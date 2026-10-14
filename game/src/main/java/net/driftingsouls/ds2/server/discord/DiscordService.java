package net.driftingsouls.ds2.server.discord;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Anbindung des Ingame-Chats an den Discord-Server.
 * <p>
 * Spieler verknuepfen ihren Discord-Account per OAuth2 (Scopes <code>identify</code> und
 * <code>guilds.join</code>), ihr Passwort sieht DS dabei nie. Gelesen wird ueber einen Bot,
 * gesendet ueber einen Webhook je Kanal, der den Ingame-Namen und den Discord-Avatar des
 * Spielers traegt. Damit nicht jeder Spieler Discord einzeln abfragt, werden die Nachrichten
 * je Kanal kurz zwischengespeichert.
 * <p>
 * Welche Kanaele ein Spieler nutzen darf, prueft der Aufrufer ueber {@link DiscordKanal}.
 * Dieser Service selbst kennt keine Spielerrechte.
 */
@Service
public class DiscordService
{
	private static final Logger log = LoggerFactory.getLogger(DiscordService.class);

	private static final String API = "https://discord.com/api/v10";
	private static final String USER_AGENT = "DiscordBot (https://ds.drifting-souls.net, 1.0)";
	private static final String WEBHOOK_NAME = "Drifting Souls";
	private static final long MESSAGE_CACHE_MILLIS = 3000;
	private static final long MEMBER_CACHE_MILLIS = 5 * 60 * 1000;
	private static final long SEND_INTERVAL_MILLIS = 1500;
	private static final int MESSAGE_LIMIT = 50;
	public static final int MAX_MESSAGE_LENGTH = 2000;

	static final long RECHT_EINLADUNG_ERSTELLEN = 1L;
	static final long RECHT_ADMINISTRATOR = 1L << 3;
	static final long RECHT_KANAL_ANSEHEN = 1L << 10;
	static final long RECHT_VERLAUF_LESEN = 1L << 16;
	static final long RECHT_WEBHOOKS_VERWALTEN = 1L << 29;
	/**
	 * Die Rechte, die der Bot auf dem Server braucht. Als Zahl fuer den Einladungslink des Bots.
	 */
	public static final long BENOETIGTE_RECHTE = RECHT_EINLADUNG_ERSTELLEN | RECHT_KANAL_ANSEHEN | RECHT_VERLAUF_LESEN | RECHT_WEBHOOKS_VERWALTEN;

	private static final Map<Long, String> RECHT_NAMEN = new LinkedHashMap<>();
	static
	{
		RECHT_NAMEN.put(RECHT_KANAL_ANSEHEN, "Kanäle ansehen");
		RECHT_NAMEN.put(RECHT_VERLAUF_LESEN, "Nachrichtenverlauf lesen");
		RECHT_NAMEN.put(RECHT_WEBHOOKS_VERWALTEN, "Webhooks verwalten");
		RECHT_NAMEN.put(RECHT_EINLADUNG_ERSTELLEN, "Einladung erstellen");
	}

	private static final long FLAG_MESSAGE_CONTENT = 1L << 19;
	private static final long FLAG_MESSAGE_CONTENT_LIMITED = 1L << 18;

	private static final Pattern USER_MENTION = Pattern.compile("<@!?(\\d+)>");
	private static final Pattern ROLE_MENTION = Pattern.compile("<@&(\\d+)>");
	private static final Pattern CHANNEL_MENTION = Pattern.compile("<#(\\d+)>");
	private static final Pattern CUSTOM_EMOJI = Pattern.compile("<a?:(\\w+):\\d+>");
	private static final Pattern TIMESTAMP = Pattern.compile("<t:(-?\\d+)(?::[a-zA-Z])?>");
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Berlin"));

	private static class ChannelCache
	{
		List<DiscordChatMessage> messages = Collections.emptyList();
		long fetchedAt;
		long blockedUntil;
	}

	private static class Webhook
	{
		final String id;
		final String token;

		Webhook(String id, String token)
		{
			this.id = id;
			this.token = token;
		}
	}

	/**
	 * Die Rechte des Bots auf dem Server: Server-Rechte sowie, was fuer die Kanalrechte gebraucht wird.
	 */
	/**
	 * Ein Account aus Sicht des Discord-Servers: Server-Alias (sonst Anzeigename) und Server-Avatar
	 * (sonst Account-Avatar). <code>mitglied == false</code>, falls der Account nicht auf dem Server ist.
	 */
	private static class Mitglied
	{
		final boolean mitglied;
		final String name;
		final String avatarUrl;
		/**
		 * Die Farbe der hoechsten farbigen Rolle als <code>#rrggbb</code> oder <code>null</code>.
		 */
		final String farbe;
		final long geladen = System.currentTimeMillis();

		Mitglied(boolean mitglied, String name, String avatarUrl, String farbe)
		{
			this.mitglied = mitglied;
			this.name = name;
			this.avatarUrl = avatarUrl;
			this.farbe = farbe;
		}
	}

	/**
	 * Farbe und Position der Rollen eines Servers.
	 */
	private static class Rollen
	{
		final Map<String, int[]> farbeUndPosition = new HashMap<>();
		final long geladen = System.currentTimeMillis();
	}

	static class BotRechte
	{
		String botId;
		String botName;
		Set<String> rollen = new HashSet<>();
		Map<String, Long> rollenRechte = new HashMap<>();
		long serverRechte;
	}

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private final Gson gson = new Gson();
	private final Map<String, ChannelCache> messageCache = new ConcurrentHashMap<>();
	private final Map<String, Webhook> webhooks = new ConcurrentHashMap<>();
	private final Map<String, Mitglied> mitglieder = new ConcurrentHashMap<>();
	private final Map<String, Rollen> rollen = new ConcurrentHashMap<>();
	private final Map<Integer, Long> lastSend = new ConcurrentHashMap<>();

	/**
	 * Verwirft alle zwischengespeicherten Daten, z.B. nachdem Bot oder Server geaendert wurden.
	 */
	public void zuruecksetzen()
	{
		messageCache.clear();
		synchronized (this)
		{
			webhooks.clear();
		}
		mitglieder.clear();
		rollen.clear();
	}

	/**
	 * Gibt die URL zurueck, auf der der Spieler die Verknuepfung bei Discord bestaetigt.
	 * @param konfiguration Die Bot-Einstellungen
	 * @param redirectUri Die Redirect-URI
	 * @param state Zufaelliger Wert gegen CSRF, der beim Callback geprueft wird
	 * @return Die URL
	 */
	public String getAuthorizeUrl(DiscordKonfiguration konfiguration, String redirectUri, String state)
	{
		return "https://discord.com/oauth2/authorize?response_type=code"
				+ "&client_id=" + encode(konfiguration.getClientId().trim())
				+ "&scope=" + encode("identify guilds.join")
				+ "&redirect_uri=" + encode(redirectUri)
				+ "&state=" + encode(state)
				+ "&prompt=none";
	}

	/**
	 * Tauscht den Code aus dem OAuth2-Callback gegen ein Access-Token.
	 * @param konfiguration Die Bot-Einstellungen
	 * @param redirectUri Die Redirect-URI, mit der der Login gestartet wurde
	 * @param code Der Code
	 * @return Das Access-Token
	 */
	public String exchangeCode(DiscordKonfiguration konfiguration, String redirectUri, String code)
	{
		String form = "grant_type=authorization_code"
				+ "&code=" + encode(code)
				+ "&redirect_uri=" + encode(redirectUri)
				+ "&client_id=" + encode(konfiguration.getClientId().trim())
				+ "&client_secret=" + encode(konfiguration.getClientSecret().trim());
		HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(API + "/oauth2/token"))
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", USER_AGENT)
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(form)));
		if (response.statusCode() != 200)
		{
			log.warn("Discord-OAuth2: Token-Austausch fehlgeschlagen ({}): {}", response.statusCode(), response.body());
			throw new DiscordException("Die Anmeldung bei Discord ist fehlgeschlagen. Bitte versuche es erneut.");
		}
		return JsonParser.parseString(response.body()).getAsJsonObject().get("access_token").getAsString();
	}

	/**
	 * Laedt den Discord-Account, dem das Access-Token gehoert.
	 * @param accessToken Das Access-Token
	 * @return Der Account
	 */
	public DiscordUser fetchUser(String accessToken)
	{
		HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(API + "/users/@me"))
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", USER_AGENT)
				.header("Authorization", "Bearer " + accessToken)
				.GET());
		if (response.statusCode() != 200)
		{
			log.warn("Discord-OAuth2: Laden des Accounts fehlgeschlagen ({}): {}", response.statusCode(), response.body());
			throw new DiscordException("Dein Discord-Account konnte nicht geladen werden. Bitte versuche es erneut.");
		}
		JsonObject user = JsonParser.parseString(response.body()).getAsJsonObject();
		return new DiscordUser(string(user, "id"), displayName(user), string(user, "avatar"));
	}

	/**
	 * Fuegt den Account dem Discord-Server hinzu, falls er noch kein Mitglied ist.
	 * Dafuer braucht der Bot das Recht, Einladungen zu erstellen.
	 * @param konfiguration Die Bot-Einstellungen
	 * @param user Der Account
	 * @param accessToken Das Access-Token des Accounts mit Scope <code>guilds.join</code>
	 * @return <code>true</code>, falls der Account danach Mitglied ist
	 */
	public boolean joinGuild(DiscordKonfiguration konfiguration, DiscordUser user, String accessToken)
	{
		JsonObject body = new JsonObject();
		body.addProperty("access_token", accessToken);
		HttpResponse<String> response = send(botRequest(konfiguration, "/guilds/" + konfiguration.getGuildId() + "/members/" + user.id)
				.header("Content-Type", "application/json")
				.PUT(HttpRequest.BodyPublishers.ofString(gson.toJson(body))));
		if (response.statusCode() == 201 || response.statusCode() == 204)
		{
			// Beim naechsten Zugriff mit Server-Alias und -Avatar neu laden
			mitglieder.remove(memberKey(konfiguration, user.id));
			return true;
		}
		log.warn("Discord: Beitritt von {} zum Server fehlgeschlagen ({}): {}", user.id, response.statusCode(), response.body());
		return false;
	}

	/**
	 * Gibt die neuesten Nachrichten eines Kanals zurueck, aelteste zuerst.
	 * Der Aufrufer muss vorher pruefen, dass der Spieler den Kanal lesen darf.
	 * @param konfiguration Die Bot-Einstellungen
	 * @param channelId Die ID des Kanals
	 * @param channelNames Bekannte Kanalnamen (ID -&gt; Name) zum Aufloesen von Kanalverweisen
	 * @return Die Nachrichten
	 */
	public List<DiscordChatMessage> getMessages(DiscordKonfiguration konfiguration, String channelId, Map<String, String> channelNames)
	{
		ChannelCache cache = messageCache.computeIfAbsent(channelId, k -> new ChannelCache());
		synchronized (cache)
		{
			long now = System.currentTimeMillis();
			if (now - cache.fetchedAt < MESSAGE_CACHE_MILLIS || now < cache.blockedUntil)
			{
				return cache.messages;
			}
			cache.fetchedAt = now;

			HttpResponse<String> response = send(botRequest(konfiguration, "/channels/" + channelId + "/messages?limit=" + MESSAGE_LIMIT).GET());
			if (response.statusCode() == 429)
			{
				cache.blockedUntil = now + retryAfterMillis(response);
				return cache.messages;
			}
			if (response.statusCode() != 200)
			{
				log.warn("Discord: Laden der Nachrichten aus Kanal {} fehlgeschlagen ({}): {}", channelId, response.statusCode(), response.body());
				throw new DiscordException("Die Nachrichten konnten nicht von Discord geladen werden.");
			}

			JsonArray messages = JsonParser.parseString(response.body()).getAsJsonArray();
			List<DiscordChatMessage> result = new ArrayList<>();
			// Discord liefert die neueste Nachricht zuerst
			for (int i = messages.size() - 1; i >= 0; i--)
			{
				JsonObject message = messages.get(i).getAsJsonObject();
				int type = message.get("type").getAsInt();
				// 0 = normale Nachricht, 19 = Antwort. Systemmeldungen (Beitritte, Pins, ...) auslassen.
				if (type != 0 && type != 19)
				{
					continue;
				}
				result.add(toChatMessage(konfiguration, message, channelNames));
			}
			cache.messages = Collections.unmodifiableList(result);
			return cache.messages;
		}
	}

	/**
	 * Sendet eine Nachricht im Namen eines Spielers in einen Kanal.
	 * Der Aufrufer muss vorher pruefen, dass der Spieler in den Kanal schreiben darf.
	 * @param konfiguration Die Bot-Einstellungen
	 * @param channelId Die ID des Kanals
	 * @param userId Die ID des Spielers in DS
	 * @param discordUserId Die ID des verknuepften Discord-Accounts. Name und Avatar der Nachricht
	 * sind sein Server-Alias und Server-Avatar, wie bei einer direkt in Discord geschriebenen Nachricht.
	 * @param text Der Text
	 * @return Die ID der gesendeten Nachricht
	 */
	public String sendMessage(DiscordKonfiguration konfiguration, String channelId, int userId, String discordUserId, String text)
	{
		String content = text == null ? "" : text.trim();
		if (content.isEmpty())
		{
			throw new DiscordException("Die Nachricht ist leer.");
		}
		if (content.length() > MAX_MESSAGE_LENGTH)
		{
			throw new DiscordException("Die Nachricht ist zu lang (maximal " + MAX_MESSAGE_LENGTH + " Zeichen).");
		}

		long now = System.currentTimeMillis();
		Long previous = lastSend.get(userId);
		if (previous != null && now - previous < SEND_INTERVAL_MILLIS)
		{
			throw new DiscordException("Bitte warte einen Moment, bevor du die nächste Nachricht sendest.");
		}

		Mitglied mitglied = ladeMitglied(konfiguration, discordUserId);
		if (!mitglied.mitglied)
		{
			throw new DiscordException("Dein Discord-Account ist kein Mitglied unseres Discord-Servers. Tritt dem Server bei und versuche es erneut.");
		}
		lastSend.put(userId, now);

		JsonObject allowedMentions = new JsonObject();
		allowedMentions.add("parse", new JsonArray());
		JsonObject body = new JsonObject();
		body.addProperty("content", content);
		body.addProperty("username", webhookUsername(mitglied.name));
		body.addProperty("avatar_url", mitglied.avatarUrl);
		body.add("allowed_mentions", allowedMentions);
		String json = gson.toJson(body);

		HttpResponse<String> response = executeWebhook(konfiguration, channelId, json);
		if (response.statusCode() == 404)
		{
			// Webhook wurde in Discord geloescht - neu anlegen und erneut versuchen
			webhooks.remove(channelId);
			response = executeWebhook(konfiguration, channelId, json);
		}
		if (response.statusCode() == 429)
		{
			throw new DiscordException("Gerade werden zu viele Nachrichten gesendet. Bitte versuche es gleich noch einmal.");
		}
		if (response.statusCode() != 200)
		{
			log.warn("Discord: Senden in Kanal {} fehlgeschlagen ({}): {}", channelId, response.statusCode(), response.body());
			throw new DiscordException("Die Nachricht konnte nicht an Discord gesendet werden.");
		}

		ChannelCache cache = messageCache.get(channelId);
		if (cache != null)
		{
			synchronized (cache)
			{
				cache.fetchedAt = 0;
			}
		}
		// Dank wait=true liefert Discord die angelegte Nachricht zurueck
		return string(JsonParser.parseString(response.body()).getAsJsonObject(), "id");
	}

	/**
	 * Vergisst die zwischengespeicherte Mitgliedschaft eines Accounts, z.B. nach dem Trennen der Verknuepfung.
	 * @param discordUserId Die ID des Accounts
	 */
	public void forgetMember(String discordUserId)
	{
		mitglieder.keySet().removeIf(key -> key.endsWith(":" + discordUserId));
	}

	/**
	 * Laedt die Textkanaele des Servers samt fehlender Rechte des Bots je Kanal.
	 * @param konfiguration Die Bot-Einstellungen
	 * @return Die Kanaele in der Reihenfolge von Discord
	 */
	public List<DiscordGuildKanal> ladeKanaele(DiscordKonfiguration konfiguration)
	{
		BotRechte rechte = ladeBotRechte(konfiguration);

		HttpResponse<String> response = send(botRequest(konfiguration, "/guilds/" + konfiguration.getGuildId() + "/channels").GET());
		if (response.statusCode() != 200)
		{
			log.warn("Discord: Laden der Kanaele fehlgeschlagen ({}): {}", response.statusCode(), response.body());
			throw new DiscordException("Die Kanäle konnten nicht geladen werden (HTTP " + response.statusCode() + ").");
		}

		JsonArray channels = JsonParser.parseString(response.body()).getAsJsonArray();
		Map<String, JsonObject> kategorien = new HashMap<>();
		for (JsonElement element : channels)
		{
			JsonObject channel = element.getAsJsonObject();
			if (channel.get("type").getAsInt() == 4)
			{
				kategorien.put(string(channel, "id"), channel);
			}
		}

		List<DiscordGuildKanal> result = new ArrayList<>();
		for (JsonElement element : channels)
		{
			JsonObject channel = element.getAsJsonObject();
			int type = channel.get("type").getAsInt();
			// 0 = Textkanal, 5 = Ankuendigungskanal
			if (type != 0 && type != 5)
			{
				continue;
			}
			JsonObject kategorie = kategorien.get(string(channel, "parent_id"));
			int kategoriePosition = kategorie != null ? kategorie.get("position").getAsInt() + 1 : 0;
			int position = kategoriePosition * 1000 + channel.get("position").getAsInt();

			result.add(new DiscordGuildKanal(
					string(channel, "id"),
					string(channel, "name"),
					kategorie != null ? string(kategorie, "name") : null,
					position,
					fehlendeKanalRechte(rechte, konfiguration.getGuildId(), channel)));
		}
		result.sort((a, b) -> Integer.compare(a.position, b.position));
		return result;
	}

	/**
	 * Prueft Bot-Token, Server, Message Content Intent und die Rechte des Bots auf dem Server.
	 * @param konfiguration Die Bot-Einstellungen
	 * @return Das Ergebnis
	 */
	public DiscordPruefung pruefen(DiscordKonfiguration konfiguration)
	{
		DiscordPruefung pruefung = new DiscordPruefung();
		if (!DiscordKonfiguration.isSetzt(konfiguration.getBotToken()) || !DiscordKonfiguration.isSetzt(konfiguration.getGuildId()))
		{
			pruefung.fehler("Bot-Token und Server-ID müssen gesetzt sein.");
			return pruefung;
		}

		try
		{
			HttpResponse<String> response = send(botRequest(konfiguration, "/applications/@me").GET());
			if (response.statusCode() == 401)
			{
				pruefung.fehler("Das Bot-Token ist ungültig.");
				return pruefung;
			}
			if (response.statusCode() == 200)
			{
				JsonObject anwendung = JsonParser.parseString(response.body()).getAsJsonObject();
				long flags = anwendung.has("flags") ? anwendung.get("flags").getAsLong() : 0;
				String clientId = konfiguration.getClientId() != null ? konfiguration.getClientId().trim() : "";
				if (!clientId.isEmpty() && !clientId.equals(string(anwendung, "id")))
				{
					pruefung.fehler("Die Client-ID passt nicht zur Anwendung des Bots (" + string(anwendung, "id") + ").");
				}
				if ((flags & (FLAG_MESSAGE_CONTENT | FLAG_MESSAGE_CONTENT_LIMITED)) != 0)
				{
					pruefung.ok("Message Content Intent ist aktiviert.");
				}
				else
				{
					pruefung.fehler("Message Content Intent ist nicht aktiviert - Nachrichten kommen ohne Text an (Developer Portal → Bot).");
				}
			}

			BotRechte rechte = ladeBotRechte(konfiguration);
			pruefung.ok("Angemeldet als Bot \"" + rechte.botName + "\".");

			response = send(botRequest(konfiguration, "/guilds/" + konfiguration.getGuildId()).GET());
			if (response.statusCode() == 200)
			{
				pruefung.ok("Server \"" + string(JsonParser.parseString(response.body()).getAsJsonObject(), "name") + "\" gefunden.");
			}

			List<String> fehlend = fehlendeRechte(rechte.serverRechte, BENOETIGTE_RECHTE);
			if (fehlend.isEmpty())
			{
				pruefung.ok("Der Bot hat auf dem Server alle benötigten Rechte.");
			}
			else
			{
				pruefung.fehler("Dem Bot fehlen auf dem Server die Rechte: " + String.join(", ", fehlend) + ".");
			}
		}
		catch (DiscordException e)
		{
			pruefung.fehler(e.getMessage());
		}
		return pruefung;
	}

	private BotRechte ladeBotRechte(DiscordKonfiguration konfiguration)
	{
		BotRechte rechte = new BotRechte();

		HttpResponse<String> response = send(botRequest(konfiguration, "/users/@me").GET());
		if (response.statusCode() == 401)
		{
			throw new DiscordException("Das Bot-Token ist ungültig.");
		}
		if (response.statusCode() != 200)
		{
			throw new DiscordException("Der Bot konnte nicht geladen werden (HTTP " + response.statusCode() + ").");
		}
		JsonObject bot = JsonParser.parseString(response.body()).getAsJsonObject();
		rechte.botId = string(bot, "id");
		rechte.botName = string(bot, "username");

		String guildId = konfiguration.getGuildId();
		response = send(botRequest(konfiguration, "/guilds/" + guildId + "/members/" + rechte.botId).GET());
		if (response.statusCode() == 403 || response.statusCode() == 404)
		{
			throw new DiscordException("Der Bot \"" + rechte.botName + "\" ist nicht auf dem Server " + guildId + ". Lade ihn zuerst über den Einladungslink ein.");
		}
		if (response.statusCode() != 200)
		{
			throw new DiscordException("Die Mitgliedschaft des Bots konnte nicht geladen werden (HTTP " + response.statusCode() + ").");
		}
		for (JsonElement rolle : JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("roles"))
		{
			rechte.rollen.add(rolle.getAsString());
		}

		response = send(botRequest(konfiguration, "/guilds/" + guildId + "/roles").GET());
		if (response.statusCode() != 200)
		{
			throw new DiscordException("Die Rollen des Servers konnten nicht geladen werden (HTTP " + response.statusCode() + ").");
		}
		for (JsonElement element : JsonParser.parseString(response.body()).getAsJsonArray())
		{
			JsonObject rolle = element.getAsJsonObject();
			rechte.rollenRechte.put(string(rolle, "id"), Long.parseLong(string(rolle, "permissions")));
		}

		// Die @everyone-Rolle hat die ID des Servers
		long serverRechte = rechte.rollenRechte.getOrDefault(guildId, 0L);
		for (String rolle : rechte.rollen)
		{
			serverRechte |= rechte.rollenRechte.getOrDefault(rolle, 0L);
		}
		rechte.serverRechte = (serverRechte & RECHT_ADMINISTRATOR) != 0 ? -1L : serverRechte;
		return rechte;
	}

	/**
	 * Berechnet die Rechte des Bots in einem Kanal nach dem Verfahren von Discord:
	 * Server-Rechte, dann Overwrites fuer @everyone, die Rollen des Bots und den Bot selbst.
	 */
	static String fehlendeKanalRechte(BotRechte rechte, String guildId, JsonObject channel)
	{
		long kanalRechte = rechte.serverRechte;
		if (kanalRechte != -1L && channel.has("permission_overwrites"))
		{
			long rollenErlaubt = 0;
			long rollenVerboten = 0;
			long everyoneErlaubt = 0;
			long everyoneVerboten = 0;
			long botErlaubt = 0;
			long botVerboten = 0;
			for (JsonElement element : channel.getAsJsonArray("permission_overwrites"))
			{
				JsonObject overwrite = element.getAsJsonObject();
				String id = string(overwrite, "id");
				long erlaubt = Long.parseLong(string(overwrite, "allow"));
				long verboten = Long.parseLong(string(overwrite, "deny"));
				if (id.equals(guildId))
				{
					everyoneErlaubt = erlaubt;
					everyoneVerboten = verboten;
				}
				else if (overwrite.get("type").getAsInt() == 0 && rechte.rollen.contains(id))
				{
					rollenErlaubt |= erlaubt;
					rollenVerboten |= verboten;
				}
				else if (overwrite.get("type").getAsInt() == 1 && id.equals(rechte.botId))
				{
					botErlaubt = erlaubt;
					botVerboten = verboten;
				}
			}
			kanalRechte = (kanalRechte & ~everyoneVerboten) | everyoneErlaubt;
			kanalRechte = (kanalRechte & ~rollenVerboten) | rollenErlaubt;
			kanalRechte = (kanalRechte & ~botVerboten) | botErlaubt;
		}

		List<String> fehlend = fehlendeRechte(kanalRechte, RECHT_KANAL_ANSEHEN | RECHT_VERLAUF_LESEN | RECHT_WEBHOOKS_VERWALTEN);
		return fehlend.isEmpty() ? null : "Bot fehlt: " + String.join(", ", fehlend);
	}

	private static List<String> fehlendeRechte(long vorhanden, long benoetigt)
	{
		List<String> fehlend = new ArrayList<>();
		for (Map.Entry<Long, String> recht : RECHT_NAMEN.entrySet())
		{
			if ((benoetigt & recht.getKey()) != 0 && (vorhanden & recht.getKey()) == 0)
			{
				fehlend.add(recht.getValue());
			}
		}
		return fehlend;
	}

	/**
	 * Laedt einen Account als Mitglied des Servers (zwischengespeichert).
	 * @throws DiscordException Falls Discord nicht sauber antwortet
	 */
	private Mitglied ladeMitglied(DiscordKonfiguration konfiguration, String discordUserId)
	{
		String key = memberKey(konfiguration, discordUserId);
		Mitglied mitglied = mitglieder.get(key);
		if (mitglied != null && System.currentTimeMillis() - mitglied.geladen < MEMBER_CACHE_MILLIS)
		{
			return mitglied;
		}
		HttpResponse<String> response = send(botRequest(konfiguration, "/guilds/" + konfiguration.getGuildId() + "/members/" + discordUserId).GET());
		if (response.statusCode() == 200)
		{
			JsonObject member = JsonParser.parseString(response.body()).getAsJsonObject();
			JsonObject user = member.getAsJsonObject("user");
			String nick = string(member, "nick");
			String serverAvatar = string(member, "avatar");
			List<String> rollenIds = new ArrayList<>();
			for (JsonElement rolle : member.getAsJsonArray("roles"))
			{
				rollenIds.add(rolle.getAsString());
			}
			mitglied = new Mitglied(true,
					nick != null && !nick.isEmpty() ? nick : displayName(user),
					serverAvatar != null
							? "https://cdn.discordapp.com/guilds/" + konfiguration.getGuildId() + "/users/" + discordUserId + "/avatars/" + serverAvatar + ".png?size=64"
							: DiscordUser.avatarUrl(discordUserId, string(user, "avatar")),
					rollenFarbe(ladeRollen(konfiguration), rollenIds));
		}
		else if (response.statusCode() == 404)
		{
			mitglied = new Mitglied(false, null, null, null);
		}
		else
		{
			log.warn("Discord: Laden des Mitglieds {} fehlgeschlagen ({}): {}", discordUserId, response.statusCode(), response.body());
			throw new DiscordException("Discord ist gerade nicht erreichbar. Bitte versuche es später erneut.");
		}
		mitglieder.put(key, mitglied);
		return mitglied;
	}

	/**
	 * Laedt Farbe und Position der Rollen des Servers (zwischengespeichert). Schlaegt das fehl,
	 * bleiben die Namen eben ungefaerbt.
	 */
	private Rollen ladeRollen(DiscordKonfiguration konfiguration)
	{
		Rollen geladen = rollen.get(konfiguration.getGuildId());
		if (geladen != null && System.currentTimeMillis() - geladen.geladen < MEMBER_CACHE_MILLIS)
		{
			return geladen;
		}
		geladen = new Rollen();
		HttpResponse<String> response = send(botRequest(konfiguration, "/guilds/" + konfiguration.getGuildId() + "/roles").GET());
		if (response.statusCode() != 200)
		{
			log.warn("Discord: Laden der Rollen fehlgeschlagen ({}): {}", response.statusCode(), response.body());
			return geladen;
		}
		for (JsonElement element : JsonParser.parseString(response.body()).getAsJsonArray())
		{
			JsonObject rolle = element.getAsJsonObject();
			geladen.farbeUndPosition.put(string(rolle, "id"), new int[]{rolle.get("color").getAsInt(), rolle.get("position").getAsInt()});
		}
		rollen.put(konfiguration.getGuildId(), geladen);
		return geladen;
	}

	/**
	 * Wie in Discord: Die Farbe der hoechsten Rolle, die eine Farbe hat.
	 */
	private static String rollenFarbe(Rollen rollen, List<String> rollenIds)
	{
		int farbe = 0;
		int position = -1;
		for (String rolle : rollenIds)
		{
			int[] daten = rollen.farbeUndPosition.get(rolle);
			if (daten != null && daten[0] != 0 && daten[1] > position)
			{
				farbe = daten[0];
				position = daten[1];
			}
		}
		return position == -1 ? null : String.format("#%06x", farbe);
	}

	/**
	 * Gibt die Rollenfarbe eines Accounts zurueck.
	 * @param konfiguration Die Bot-Einstellungen
	 * @param discordUserId Die ID des Accounts
	 * @return Die Farbe als <code>#rrggbb</code> oder <code>null</code>, falls er keine hat oder Discord nicht antwortet
	 */
	public String getRollenFarbe(DiscordKonfiguration konfiguration, String discordUserId)
	{
		Mitglied mitglied = ladeMitgliedOderNull(konfiguration, discordUserId);
		return mitglied != null ? mitglied.farbe : null;
	}

	/**
	 * Wie {@link #ladeMitglied}, wirft aber nicht: Beim Anzeigen von Nachrichten reicht notfalls der Accountname.
	 */
	private Mitglied ladeMitgliedOderNull(DiscordKonfiguration konfiguration, String discordUserId)
	{
		try
		{
			Mitglied mitglied = ladeMitglied(konfiguration, discordUserId);
			return mitglied.mitglied ? mitglied : null;
		}
		catch (DiscordException e)
		{
			return null;
		}
	}

	private static String memberKey(DiscordKonfiguration konfiguration, String discordUserId)
	{
		return konfiguration.getGuildId() + ":" + discordUserId;
	}

	private HttpResponse<String> executeWebhook(DiscordKonfiguration konfiguration, String channelId, String json)
	{
		Webhook webhook = getWebhook(konfiguration, channelId);
		return send(HttpRequest.newBuilder(URI.create(API + "/webhooks/" + webhook.id + "/" + webhook.token + "?wait=true"))
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", USER_AGENT)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json)));
	}

	private synchronized Webhook getWebhook(DiscordKonfiguration konfiguration, String channelId)
	{
		Webhook webhook = webhooks.get(channelId);
		if (webhook != null)
		{
			return webhook;
		}

		HttpResponse<String> response = send(botRequest(konfiguration, "/channels/" + channelId + "/webhooks").GET());
		if (response.statusCode() != 200)
		{
			log.warn("Discord: Laden der Webhooks von Kanal {} fehlgeschlagen ({}): {}", channelId, response.statusCode(), response.body());
			throw new DiscordException("Der Bot darf in diesem Kanal keine Webhooks verwalten.");
		}
		for (JsonElement element : JsonParser.parseString(response.body()).getAsJsonArray())
		{
			JsonObject existing = element.getAsJsonObject();
			if (WEBHOOK_NAME.equals(string(existing, "name")) && string(existing, "token") != null)
			{
				webhook = new Webhook(string(existing, "id"), string(existing, "token"));
				webhooks.put(channelId, webhook);
				return webhook;
			}
		}

		JsonObject body = new JsonObject();
		body.addProperty("name", WEBHOOK_NAME);
		response = send(botRequest(konfiguration, "/channels/" + channelId + "/webhooks")
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body))));
		if (response.statusCode() != 200)
		{
			log.warn("Discord: Anlegen des Webhooks in Kanal {} fehlgeschlagen ({}): {}", channelId, response.statusCode(), response.body());
			throw new DiscordException("Der Bot darf in diesem Kanal keine Webhooks anlegen.");
		}
		JsonObject created = JsonParser.parseString(response.body()).getAsJsonObject();
		webhook = new Webhook(string(created, "id"), string(created, "token"));
		webhooks.put(channelId, webhook);
		return webhook;
	}

	private DiscordChatMessage toChatMessage(DiscordKonfiguration konfiguration, JsonObject message, Map<String, String> channelNames)
	{
		JsonObject author = message.getAsJsonObject("author");
		String authorId = string(author, "id");
		boolean webhook = message.has("webhook_id") && !message.get("webhook_id").isJsonNull();

		// Nachrichten von Webhooks (auch aus dem Spiel) tragen ihren Namen schon selbst.
		// Bei echten Accounts zeigt Discord den Server-Alias an, das REST-API liefert ihn aber nicht mit.
		String autorName = displayName(author);
		String autorAvatar = DiscordUser.avatarUrl(authorId, string(author, "avatar"));
		String autorFarbe = null;
		if (!webhook)
		{
			Mitglied mitglied = ladeMitgliedOderNull(konfiguration, authorId);
			if (mitglied != null)
			{
				autorName = mitglied.name;
				autorAvatar = mitglied.avatarUrl;
				autorFarbe = mitglied.farbe;
			}
		}

		Map<String, String> mentionedUsers = new HashMap<>();
		if (message.has("mentions"))
		{
			for (JsonElement element : message.getAsJsonArray("mentions"))
			{
				JsonObject mentioned = element.getAsJsonObject();
				String nick = string(mentioned.getAsJsonObject("member"), "nick");
				mentionedUsers.put(string(mentioned, "id"), nick != null && !nick.isEmpty() ? nick : displayName(mentioned));
			}
		}

		String text = formatContent(string(message, "content"), mentionedUsers, channelNames);
		List<DiscordChatMessage.Attachment> attachments = new ArrayList<>();
		if (message.has("embeds"))
		{
			List<String> parts = new ArrayList<>();
			for (JsonElement element : message.getAsJsonArray("embeds"))
			{
				JsonObject embed = element.getAsJsonObject();
				String typ = string(embed, "type");
				if ("image".equals(typ))
				{
					// Eine gepostete Bild-URL: Discord zeigt dafuer das Bild an
					attachments.add(medium(DiscordChatMessage.ART_BILD, "", embed.getAsJsonObject("thumbnail")));
					continue;
				}
				if ("gifv".equals(typ))
				{
					// Animierte GIFs (z.B. von Tenor) liefert Discord als Video
					attachments.add(medium(DiscordChatMessage.ART_VIDEO, "", embed.getAsJsonObject("video")));
					continue;
				}
				if (text.isEmpty())
				{
					for (String key : new String[]{"title", "description"})
					{
						String value = string(embed, key);
						if (value != null && !value.isEmpty())
						{
							parts.add(formatContent(value, mentionedUsers, channelNames));
						}
					}
				}
				if (embed.has("image"))
				{
					attachments.add(medium(DiscordChatMessage.ART_BILD, "", embed.getAsJsonObject("image")));
				}
			}
			if (text.isEmpty())
			{
				text = String.join("\n", parts);
			}
		}

		if (message.has("attachments"))
		{
			for (JsonElement element : message.getAsJsonArray("attachments"))
			{
				JsonObject attachment = element.getAsJsonObject();
				String contentType = string(attachment, "content_type");
				String art = DiscordChatMessage.ART_DATEI;
				if (contentType != null && contentType.startsWith("image/"))
				{
					art = DiscordChatMessage.ART_BILD;
				}
				else if (contentType != null && contentType.startsWith("video/"))
				{
					art = DiscordChatMessage.ART_VIDEO;
				}
				attachments.add(medium(art, string(attachment, "filename"), attachment));
			}
		}
		attachments.removeIf(attachment -> attachment.url == null);

		DiscordChatMessage result = new DiscordChatMessage(
				string(message, "id"),
				autorName,
				autorAvatar,
				text,
				string(message, "timestamp"),
				webhook,
				attachments);
		result.farbe = autorFarbe;
		return result;
	}

	private static DiscordChatMessage.Attachment medium(String art, String name, JsonObject quelle)
	{
		if (quelle == null)
		{
			return new DiscordChatMessage.Attachment(art, name, null, 0, 0);
		}
		JsonElement breite = quelle.get("width");
		JsonElement hoehe = quelle.get("height");
		// proxy_url liefert Discord ueber das eigene CDN aus, auch fuer Bilder fremder Seiten
		String url = string(quelle, "proxy_url") != null ? string(quelle, "proxy_url") : string(quelle, "url");
		return new DiscordChatMessage.Attachment(art, name, url,
				breite != null && !breite.isJsonNull() ? breite.getAsInt() : 0,
				hoehe != null && !hoehe.isJsonNull() ? hoehe.getAsInt() : 0);
	}

	/**
	 * Ersetzt die Discord-Markup-Verweise (Erwaehnungen, Kanaele, Emojis, Zeitstempel) durch lesbaren Text.
	 */
	static String formatContent(String content, Map<String, String> users, Map<String, String> channels)
	{
		if (content == null)
		{
			return "";
		}
		String text = replace(USER_MENTION, content, m -> "@" + users.getOrDefault(m.group(1), "unbekannt"));
		text = replace(ROLE_MENTION, text, m -> "@Rolle");
		text = replace(CHANNEL_MENTION, text, m -> "#" + channels.getOrDefault(m.group(1), "kanal"));
		text = replace(CUSTOM_EMOJI, text, m -> ":" + m.group(1) + ":");
		text = replace(TIMESTAMP, text, m -> TIMESTAMP_FORMAT.format(Instant.ofEpochSecond(Long.parseLong(m.group(1)))));
		return text;
	}

	/**
	 * Macht aus dem Spielernamen einen fuer Webhooks zulaessigen Namen.
	 * Discord lehnt Namen mit "discord" oder "clyde" ab und begrenzt die Laenge auf 80 Zeichen.
	 */
	static String webhookUsername(String name)
	{
		String result = name == null ? "" : name.replaceAll("(?i)discord|clyde", "").replaceAll("[@#:`]", "").trim();
		if (result.length() > 80)
		{
			result = result.substring(0, 80).trim();
		}
		if (result.isEmpty() || result.equalsIgnoreCase("everyone") || result.equalsIgnoreCase("here"))
		{
			result = "Spieler";
		}
		return result;
	}

	private static String replace(Pattern pattern, String text, Function<Matcher, String> replacement)
	{
		Matcher matcher = pattern.matcher(text);
		StringBuilder result = new StringBuilder();
		while (matcher.find())
		{
			matcher.appendReplacement(result, Matcher.quoteReplacement(replacement.apply(matcher)));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	private HttpRequest.Builder botRequest(DiscordKonfiguration konfiguration, String path)
	{
		return HttpRequest.newBuilder(URI.create(API + path))
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", USER_AGENT)
				.header("Authorization", "Bot " + konfiguration.getBotToken().trim());
	}

	private HttpResponse<String> send(HttpRequest.Builder request)
	{
		try
		{
			return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			log.warn("Discord ist nicht erreichbar", e);
			throw new DiscordException("Discord ist gerade nicht erreichbar. Bitte versuche es später erneut.", e);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new DiscordException("Die Anfrage an Discord wurde abgebrochen.", e);
		}
	}

	private static long retryAfterMillis(HttpResponse<String> response)
	{
		try
		{
			return (long) (Double.parseDouble(response.headers().firstValue("Retry-After").orElse("5")) * 1000);
		}
		catch (NumberFormatException e)
		{
			return 5000;
		}
	}

	private static String displayName(JsonObject user)
	{
		String globalName = string(user, "global_name");
		return globalName != null && !globalName.isEmpty() ? globalName : string(user, "username");
	}

	private static String string(JsonObject object, String key)
	{
		if (object == null)
		{
			return null;
		}
		JsonElement element = object.get(key);
		return element == null || element.isJsonNull() ? null : element.getAsString();
	}

	private static String encode(String value)
	{
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
