package net.driftingsouls.ds2.server.discord;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.driftingsouls.ds2.server.framework.Configuration;
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
import java.util.List;
import java.util.Map;
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

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private final Gson gson = new Gson();
	private final Map<String, ChannelCache> messageCache = new ConcurrentHashMap<>();
	private final Map<String, Webhook> webhooks = new ConcurrentHashMap<>();
	private final Map<String, Long> confirmedMembers = new ConcurrentHashMap<>();
	private final Map<Integer, Long> lastSend = new ConcurrentHashMap<>();

	/**
	 * Gibt zurueck, ob der OAuth2-Login konfiguriert ist.
	 * @return <code>true</code>, falls Client-ID, Client-Secret und Redirect-URI gesetzt sind
	 */
	public boolean isLoginConfigured()
	{
		return isSet(Configuration.getDiscordClientId())
				&& isSet(Configuration.getDiscordClientSecret())
				&& isSet(Configuration.getDiscordRedirectUri());
	}

	/**
	 * Gibt zurueck, ob der Bot fuer den Chat konfiguriert ist.
	 * @return <code>true</code>, falls ein Bot-Token gesetzt ist
	 */
	public boolean isChatConfigured()
	{
		return isSet(Configuration.getDiscordBotToken());
	}

	/**
	 * Gibt die URL zurueck, auf der der Spieler die Verknuepfung bei Discord bestaetigt.
	 * @param state Zufaelliger Wert gegen CSRF, der beim Callback geprueft wird
	 * @return Die URL
	 */
	public String getAuthorizeUrl(String state)
	{
		return "https://discord.com/oauth2/authorize?response_type=code"
				+ "&client_id=" + encode(Configuration.getDiscordClientId())
				+ "&scope=" + encode("identify guilds.join")
				+ "&redirect_uri=" + encode(Configuration.getDiscordRedirectUri())
				+ "&state=" + encode(state)
				+ "&prompt=none";
	}

	/**
	 * Tauscht den Code aus dem OAuth2-Callback gegen ein Access-Token.
	 * @param code Der Code
	 * @return Das Access-Token
	 */
	public String exchangeCode(String code)
	{
		String form = "grant_type=authorization_code"
				+ "&code=" + encode(code)
				+ "&redirect_uri=" + encode(Configuration.getDiscordRedirectUri())
				+ "&client_id=" + encode(Configuration.getDiscordClientId())
				+ "&client_secret=" + encode(Configuration.getDiscordClientSecret());
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
	 * @param guildId Die ID des Discord-Servers
	 * @param user Der Account
	 * @param accessToken Das Access-Token des Accounts mit Scope <code>guilds.join</code>
	 * @return <code>true</code>, falls der Account danach Mitglied ist
	 */
	public boolean joinGuild(String guildId, DiscordUser user, String accessToken)
	{
		if (!isChatConfigured())
		{
			return false;
		}
		JsonObject body = new JsonObject();
		body.addProperty("access_token", accessToken);
		HttpResponse<String> response = send(botRequest("/guilds/" + guildId + "/members/" + user.id)
				.header("Content-Type", "application/json")
				.PUT(HttpRequest.BodyPublishers.ofString(gson.toJson(body))));
		if (response.statusCode() == 201 || response.statusCode() == 204)
		{
			confirmedMembers.put(user.id, System.currentTimeMillis());
			return true;
		}
		log.warn("Discord: Beitritt von {} zum Server fehlgeschlagen ({}): {}", user.id, response.statusCode(), response.body());
		return false;
	}

	/**
	 * Gibt die neuesten Nachrichten eines Kanals zurueck, aelteste zuerst.
	 * @param channelId Die ID des Kanals
	 * @param channelNames Bekannte Kanalnamen (ID -&gt; Name) zum Aufloesen von Kanalverweisen
	 * @return Die Nachrichten
	 */
	public List<DiscordChatMessage> getMessages(String channelId, Map<String, String> channelNames)
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

			HttpResponse<String> response = send(botRequest("/channels/" + channelId + "/messages?limit=" + MESSAGE_LIMIT).GET());
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
				result.add(toChatMessage(message, channelNames));
			}
			cache.messages = Collections.unmodifiableList(result);
			return cache.messages;
		}
	}

	/**
	 * Sendet eine Nachricht im Namen eines Spielers in einen Kanal.
	 * @param guildId Die ID des Discord-Servers
	 * @param channelId Die ID des Kanals
	 * @param userId Die ID des Spielers in DS
	 * @param discordUserId Die ID des verknuepften Discord-Accounts
	 * @param displayName Der anzuzeigende Name
	 * @param avatarUrl Die URL des anzuzeigenden Avatars
	 * @param text Der Text
	 */
	public void sendMessage(String guildId, String channelId, int userId, String discordUserId, String displayName, String avatarUrl, String text)
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

		if (!isMember(guildId, discordUserId))
		{
			throw new DiscordException("Dein Discord-Account ist kein Mitglied unseres Discord-Servers. Tritt dem Server bei und versuche es erneut.");
		}
		lastSend.put(userId, now);

		JsonObject allowedMentions = new JsonObject();
		allowedMentions.add("parse", new JsonArray());
		JsonObject body = new JsonObject();
		body.addProperty("content", content);
		body.addProperty("username", webhookUsername(displayName));
		body.addProperty("avatar_url", avatarUrl);
		body.add("allowed_mentions", allowedMentions);
		String json = gson.toJson(body);

		HttpResponse<String> response = executeWebhook(channelId, json);
		if (response.statusCode() == 404)
		{
			// Webhook wurde in Discord geloescht - neu anlegen und erneut versuchen
			webhooks.remove(channelId);
			response = executeWebhook(channelId, json);
		}
		if (response.statusCode() == 429)
		{
			throw new DiscordException("Gerade werden zu viele Nachrichten gesendet. Bitte versuche es gleich noch einmal.");
		}
		if (response.statusCode() != 200 && response.statusCode() != 204)
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
	}

	/**
	 * Vergisst die zwischengespeicherte Mitgliedschaft eines Accounts, z.B. nach dem Trennen der Verknuepfung.
	 * @param discordUserId Die ID des Accounts
	 */
	public void forgetMember(String discordUserId)
	{
		confirmedMembers.remove(discordUserId);
	}

	private boolean isMember(String guildId, String discordUserId)
	{
		Long confirmedAt = confirmedMembers.get(discordUserId);
		if (confirmedAt != null && System.currentTimeMillis() - confirmedAt < MEMBER_CACHE_MILLIS)
		{
			return true;
		}
		HttpResponse<String> response = send(botRequest("/guilds/" + guildId + "/members/" + discordUserId).GET());
		if (response.statusCode() == 200)
		{
			confirmedMembers.put(discordUserId, System.currentTimeMillis());
			return true;
		}
		confirmedMembers.remove(discordUserId);
		if (response.statusCode() == 404)
		{
			return false;
		}
		log.warn("Discord: Pruefen der Mitgliedschaft von {} fehlgeschlagen ({}): {}", discordUserId, response.statusCode(), response.body());
		throw new DiscordException("Discord ist gerade nicht erreichbar. Bitte versuche es später erneut.");
	}

	private HttpResponse<String> executeWebhook(String channelId, String json)
	{
		Webhook webhook = getWebhook(channelId);
		return send(HttpRequest.newBuilder(URI.create(API + "/webhooks/" + webhook.id + "/" + webhook.token + "?wait=true"))
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", USER_AGENT)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json)));
	}

	private synchronized Webhook getWebhook(String channelId)
	{
		Webhook webhook = webhooks.get(channelId);
		if (webhook != null)
		{
			return webhook;
		}

		HttpResponse<String> response = send(botRequest("/channels/" + channelId + "/webhooks").GET());
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
		response = send(botRequest("/channels/" + channelId + "/webhooks")
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

	private DiscordChatMessage toChatMessage(JsonObject message, Map<String, String> channelNames)
	{
		JsonObject author = message.getAsJsonObject("author");
		String authorId = string(author, "id");

		Map<String, String> mentionedUsers = new HashMap<>();
		if (message.has("mentions"))
		{
			for (JsonElement element : message.getAsJsonArray("mentions"))
			{
				JsonObject mentioned = element.getAsJsonObject();
				mentionedUsers.put(string(mentioned, "id"), displayName(mentioned));
			}
		}

		String text = formatContent(string(message, "content"), mentionedUsers, channelNames);
		if (text.isEmpty() && message.has("embeds"))
		{
			List<String> parts = new ArrayList<>();
			for (JsonElement element : message.getAsJsonArray("embeds"))
			{
				JsonObject embed = element.getAsJsonObject();
				for (String key : new String[]{"title", "description"})
				{
					String value = string(embed, key);
					if (value != null && !value.isEmpty())
					{
						parts.add(formatContent(value, mentionedUsers, channelNames));
					}
				}
			}
			text = String.join("\n", parts);
		}

		List<DiscordChatMessage.Attachment> attachments = new ArrayList<>();
		if (message.has("attachments"))
		{
			for (JsonElement element : message.getAsJsonArray("attachments"))
			{
				JsonObject attachment = element.getAsJsonObject();
				attachments.add(new DiscordChatMessage.Attachment(string(attachment, "filename"), string(attachment, "url")));
			}
		}

		return new DiscordChatMessage(
				string(message, "id"),
				displayName(author),
				DiscordUser.avatarUrl(authorId, string(author, "avatar")),
				text,
				string(message, "timestamp"),
				message.has("webhook_id") && !message.get("webhook_id").isJsonNull(),
				attachments);
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

	private HttpRequest.Builder botRequest(String path)
	{
		return HttpRequest.newBuilder(URI.create(API + path))
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", USER_AGENT)
				.header("Authorization", "Bot " + Configuration.getDiscordBotToken());
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
		JsonElement element = object.get(key);
		return element == null || element.isJsonNull() ? null : element.getAsString();
	}

	private static String encode(String value)
	{
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static boolean isSet(String value)
	{
		return value != null && !value.trim().isEmpty();
	}
}
