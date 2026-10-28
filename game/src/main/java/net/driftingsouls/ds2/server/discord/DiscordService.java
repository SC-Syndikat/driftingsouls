package net.driftingsouls.ds2.server.discord;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vdurmont.emoji.Emoji;
import com.vdurmont.emoji.EmojiManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Anbindung des Ingame-Chats an den Discord-Server.
 * <p>
 * Spieler verknuepfen ihren Discord-Account per OAuth2 (Scopes <code>identify</code> und
 * <code>guilds.join</code>), ihr Passwort sieht DS dabei nie. Gelesen wird ueber einen Bot,
 * gesendet ueber einen Webhook je Kanal, der den Server-Alias und den Server-Avatar des
 * Spielers traegt - wie bei einer direkt in Discord geschriebenen Nachricht. Vor jedem Senden
 * wird wie in Discord geprueft, ob der Account im Kanal schreiben darf (Kanalrechte, Timeout,
 * Regelbestaetigung, Slowmode). Damit nicht jeder Spieler Discord einzeln abfragt, werden die
 * Nachrichten je Kanal kurz zwischengespeichert.
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
	private static final Duration LESE_TIMEOUT = Duration.ofSeconds(5);
	private static final Duration SCHREIB_TIMEOUT = Duration.ofSeconds(10);
	private static final long MESSAGE_CACHE_MILLIS = 3000;
	private static final long MEMBER_CACHE_MILLIS = 5 * 60 * 1000;
	private static final long SERVER_CACHE_MILLIS = 60 * 1000;
	/**
	 * So lange wird nach einer fehlgeschlagenen Anfrage nicht erneut gefragt.
	 */
	private static final long FEHLER_CACHE_MILLIS = 30 * 1000;
	/**
	 * Hoechstens so viele noch unbekannte Verfasser werden je Abruf eines Kanals nachgeladen, die
	 * uebrigen bei den naechsten Abrufen. So bleibt ein Abruf auch bei leerem Cache kurz.
	 */
	private static final int MAX_NEUE_MITGLIEDER_PRO_ABRUF = 5;
	private static final int MESSAGE_LIMIT = 50;
	public static final int MAX_MESSAGE_LENGTH = 2000;

	static final long SEND_INTERVAL_MILLIS = 2000;
	static final long SEND_FENSTER_MILLIS = 60 * 1000;
	/**
	 * Nachrichten je Discord-Account und Minute.
	 */
	static final int MAX_NACHRICHTEN_PRO_ACCOUNT = 10;
	/**
	 * Nachrichten je Kanal und Minute ueber alle Spieler. Discord erlaubt einem Webhook etwa 30 je Minute.
	 */
	static final int MAX_NACHRICHTEN_PRO_KANAL = 25;

	/**
	 * Discord sperrt eine IP, die in 10 Minuten 10.000 ungueltige Anfragen (401, 403, 429) stellt.
	 * Lange vorher stellt DS die Anfragen fuer den Rest des Zeitraums ein.
	 */
	private static final long UNGUELTIGE_FENSTER_MILLIS = 10 * 60 * 1000;
	private static final int MAX_UNGUELTIGE_ANFRAGEN = 2500;

	static final long RECHT_EINLADUNG_ERSTELLEN = 1L;
	static final long RECHT_ADMINISTRATOR = 1L << 3;
	static final long RECHT_KANAELE_VERWALTEN = 1L << 4;
	static final long RECHT_KANAL_ANSEHEN = 1L << 10;
	static final long RECHT_NACHRICHTEN_SENDEN = 1L << 11;
	static final long RECHT_NACHRICHTEN_VERWALTEN = 1L << 13;
	static final long RECHT_VERLAUF_LESEN = 1L << 16;
	static final long RECHT_WEBHOOKS_VERWALTEN = 1L << 29;
	static final long ALLE_RECHTE = -1L;
	/**
	 * Die Rechte, die der Bot auf dem ganzen Server braucht. Als Zahl fuer den Einladungslink des Bots.
	 * Webhooks verwalten darf er nur in den angebundenen Kanaelen, siehe {@link #KANAL_RECHTE}.
	 */
	public static final long BENOETIGTE_RECHTE = RECHT_EINLADUNG_ERSTELLEN | RECHT_KANAL_ANSEHEN | RECHT_VERLAUF_LESEN;
	/**
	 * Die Rechte, die der Bot in jedem angebundenen Kanal braucht.
	 */
	public static final long KANAL_RECHTE = RECHT_KANAL_ANSEHEN | RECHT_VERLAUF_LESEN | RECHT_WEBHOOKS_VERWALTEN;
	/**
	 * Die Rechte, die bestimmen, wer einen Kanal mitlesen kann. Nur sie gehen in die {@link #rechteSignatur} ein.
	 */
	static final long SICHTBARKEITS_RECHTE = RECHT_KANAL_ANSEHEN | RECHT_VERLAUF_LESEN;

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

	private static final String NICHT_ERREICHBAR = "Discord ist gerade nicht erreichbar. Bitte versuche es später erneut.";

	private static final Pattern SNOWFLAKE = Pattern.compile("\\d{1,25}");
	private static final Pattern USER_MENTION = Pattern.compile("<@!?(\\d+)>");
	private static final Pattern ROLE_MENTION = Pattern.compile("<@&(\\d+)>");
	private static final Pattern CHANNEL_MENTION = Pattern.compile("<#(\\d+)>");
	/**
	 * Eine Kurzform wie <code>:thumbsup:</code>, aber nicht der Name in einem Server-Emoji <code>&lt;:name:id&gt;</code>.
	 */
	private static final Pattern KURZFORM = Pattern.compile("(?<![<\\w]):([A-Za-z0-9_+\\-]{2,40}):");
	private static final Pattern TIMESTAMP = Pattern.compile("<t:(-?\\d+)(?::[a-zA-Z])?>");
	/**
	 * Ein maskierter Link <code>[Text](URL)</code>, auch mit <code>&lt;URL&gt;</code> und Titel.
	 */
	private static final Pattern MASKIERTER_LINK = Pattern.compile("\\[([^\\]\\n]*)\\]\\(\\s*<?([^\\s)>]+)>?(?:\\s+\"[^\"]*\")?\\s*\\)");
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Berlin"));

	private static class ChannelCache
	{
		final ReentrantLock lock = new ReentrantLock();
		volatile List<DiscordChatMessage> messages = Collections.emptyList();
		volatile long fetchedAt;
		volatile long blockedUntil;
		/**
		 * Die Fehlermeldung, die bis <code>blockedUntil</code> geliefert wird, oder <code>null</code>.
		 */
		volatile String fehler;

		List<DiscordChatMessage> ergebnis()
		{
			String meldung = fehler;
			if (meldung != null && System.currentTimeMillis() < blockedUntil)
			{
				throw new DiscordException(meldung);
			}
			return messages;
		}
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
	 * Ein Account aus Sicht des Discord-Servers: Server-Alias (sonst Anzeigename) und Server-Avatar
	 * (sonst Account-Avatar). <code>mitglied == false</code>, falls der Account nicht auf dem Server ist.
	 */
	private static class Mitglied
	{
		static Mitglied fehlgeschlagen()
		{
			Mitglied mitglied = new Mitglied(false, null, null, null, Collections.emptySet(), false, 0);
			mitglied.fehlgeschlagen = true;
			return mitglied;
		}

		final boolean mitglied;
		final String name;
		final String avatarUrl;
		/**
		 * Die Farbe der hoechsten farbigen Rolle als <code>#rrggbb</code> oder <code>null</code>.
		 */
		final String farbe;
		final Set<String> rollen;
		/**
		 * <code>true</code>, falls der Account die Regeln des Servers noch nicht bestaetigt hat.
		 */
		final boolean wartend;
		/**
		 * Bis wann der Account stummgeschaltet ist (Timeout), in Millisekunden.
		 */
		final long stummBis;
		final long geladen = System.currentTimeMillis();
		/**
		 * <code>true</code>, falls Discord nicht sauber geantwortet hat. Dann wird eine Weile nicht erneut gefragt.
		 */
		boolean fehlgeschlagen;

		Mitglied(boolean mitglied, String name, String avatarUrl, String farbe, Set<String> rollen, boolean wartend, long stummBis)
		{
			this.mitglied = mitglied;
			this.name = name;
			this.avatarUrl = avatarUrl;
			this.farbe = farbe;
			this.rollen = rollen;
			this.wartend = wartend;
			this.stummBis = stummBis;
		}

		boolean isAktuell(long now)
		{
			return now - geladen < (fehlgeschlagen ? FEHLER_CACHE_MILLIS : MEMBER_CACHE_MILLIS);
		}
	}

	/**
	 * Was zu einem Server gebraucht wird: Eigentuemer, Rollen und eigene Emojis.
	 * Ist <code>fehler</code> gesetzt, konnte Discord den Server nicht liefern.
	 */
	private static class ServerDaten
	{
		String ownerId;
		final Map<String, Long> rollenRechte = new HashMap<>();
		final Map<String, int[]> farbeUndPosition = new HashMap<>();
		/**
		 * Die eigenen Emojis als Name -&gt; Markup.
		 */
		final Map<String, String> emojis = new HashMap<>();
		String fehler;
		final long geladen = System.currentTimeMillis();

		boolean isAktuell(long now)
		{
			return now - geladen < (fehler != null ? FEHLER_CACHE_MILLIS : SERVER_CACHE_MILLIS);
		}
	}

	/**
	 * Die Kanaele eines Servers samt Rechten und Slowmode.
	 */
	private static class KanalListe
	{
		final Map<String, JsonObject> kanaele = new HashMap<>();
		String fehler;
		final long geladen = System.currentTimeMillis();

		boolean isAktuell(long now)
		{
			return now - geladen < (fehler != null ? FEHLER_CACHE_MILLIS : SERVER_CACHE_MILLIS);
		}
	}

	static class BotRechte
	{
		String botId;
		String botName;
		Set<String> rollen = new HashSet<>();
		Map<String, Long> rollenRechte = new HashMap<>();
		long serverRechte;
		boolean administrator;
	}

	/**
	 * Begrenzt, wie oft gesendet wird: je Discord-Account, je Kanal und nach dem Slowmode des Kanals.
	 * Pruefen und Vermerken geschehen gemeinsam, parallele Anfragen koennen die Grenzen also nicht umgehen.
	 */
	static class SendeLimits
	{
		private static final long MAX_SLOWMODE_MILLIS = 6 * 60 * 60 * 1000;

		private final Map<String, Deque<Long>> proAccount = new HashMap<>();
		private final Map<String, Deque<Long>> proKanal = new HashMap<>();
		private final Map<String, Long> letzteImKanal = new HashMap<>();

		/**
		 * Prueft, ob der Account jetzt in den Kanal senden darf, und vermerkt die Nachricht.
		 * @param discordUserId Der Account
		 * @param channelId Der Kanal
		 * @param slowmodeSekunden Der Slowmode, der fuer den Account gilt, oder 0
		 * @param now Der aktuelle Zeitpunkt in Millisekunden
		 * @throws DiscordException Falls er jetzt nicht senden darf
		 */
		synchronized void pruefeUndVermerke(String discordUserId, String channelId, int slowmodeSekunden, long now)
		{
			Deque<Long> account = aktuelle(proAccount, discordUserId, now);
			if (!account.isEmpty() && now - account.peekLast() < SEND_INTERVAL_MILLIS)
			{
				throw new DiscordException("Bitte warte einen Moment, bevor du die nächste Nachricht sendest.");
			}
			if (account.size() >= MAX_NACHRICHTEN_PRO_ACCOUNT)
			{
				throw new DiscordException("Du hast in der letzten Minute zu viele Nachrichten gesendet. Bitte warte einen Moment.");
			}
			Deque<Long> kanal = aktuelle(proKanal, channelId, now);
			if (kanal.size() >= MAX_NACHRICHTEN_PRO_KANAL)
			{
				throw new DiscordException("In diesem Kanal wird gerade sehr viel geschrieben. Bitte versuche es gleich noch einmal.");
			}
			String slowmodeKey = channelId + ":" + discordUserId;
			Long letzte = letzteImKanal.get(slowmodeKey);
			if (slowmodeSekunden > 0 && letzte != null && now - letzte < slowmodeSekunden * 1000L)
			{
				long rest = (slowmodeSekunden * 1000L - (now - letzte) + 999) / 1000;
				throw new DiscordException("In diesem Kanal gilt ein Slowmode von " + slowmodeSekunden + " Sekunden. Bitte warte noch " + rest + " Sekunden.");
			}

			account.addLast(now);
			kanal.addLast(now);
			if (letzteImKanal.size() > 10000)
			{
				letzteImKanal.values().removeIf(zeit -> now - zeit > MAX_SLOWMODE_MILLIS);
			}
			letzteImKanal.put(slowmodeKey, now);
		}

		private static Deque<Long> aktuelle(Map<String, Deque<Long>> zeiten, String key, long now)
		{
			Deque<Long> result = zeiten.computeIfAbsent(key, k -> new ArrayDeque<>());
			while (!result.isEmpty() && now - result.peekFirst() >= SEND_FENSTER_MILLIS)
			{
				result.removeFirst();
			}
			return result;
		}
	}

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private final Gson gson = new Gson();
	private final Map<String, ChannelCache> messageCache = new ConcurrentHashMap<>();
	private final Map<String, Webhook> webhooks = new ConcurrentHashMap<>();
	private final Map<String, Object> webhookSperren = new ConcurrentHashMap<>();
	private final Map<String, Mitglied> mitglieder = new ConcurrentHashMap<>();
	private final Map<String, ServerDaten> server = new ConcurrentHashMap<>();
	private final Map<String, KanalListe> kanalListen = new ConcurrentHashMap<>();
	private final SendeLimits sendeLimits = new SendeLimits();

	private final Object ungueltigeSperre = new Object();
	private long ungueltigeSeit;
	private int ungueltige;
	private volatile long gesperrtBis;

	/**
	 * Verwirft alle zwischengespeicherten Daten, z.B. nachdem Bot oder Server geaendert wurden.
	 */
	public void zuruecksetzen()
	{
		messageCache.clear();
		webhooks.clear();
		mitglieder.clear();
		server.clear();
		kanalListen.clear();
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
				.timeout(SCHREIB_TIMEOUT)
				.header("User-Agent", USER_AGENT)
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(form)));
		String accessToken = response.statusCode() == 200 ? string(JsonParser.parseString(response.body()).getAsJsonObject(), "access_token") : null;
		if (accessToken == null)
		{
			log.warn("Discord-OAuth2: Token-Austausch fehlgeschlagen ({})", response.statusCode());
			throw new DiscordException("Die Anmeldung bei Discord ist fehlgeschlagen. Bitte versuche es erneut.");
		}
		return accessToken;
	}

	/**
	 * Laedt den Discord-Account, dem das Access-Token gehoert.
	 * @param accessToken Das Access-Token
	 * @return Der Account
	 */
	public DiscordUser fetchUser(String accessToken)
	{
		HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(API + "/users/@me"))
				.timeout(LESE_TIMEOUT)
				.header("User-Agent", USER_AGENT)
				.header("Authorization", "Bearer " + accessToken)
				.GET());
		if (response.statusCode() != 200)
		{
			log.warn("Discord-OAuth2: Laden des Accounts fehlgeschlagen ({}): {}", response.statusCode(), response.body());
			throw new DiscordException("Dein Discord-Account konnte nicht geladen werden. Bitte versuche es erneut.");
		}
		JsonObject user = JsonParser.parseString(response.body()).getAsJsonObject();
		String id = string(user, "id");
		if (id == null || !SNOWFLAKE.matcher(id).matches())
		{
			throw new DiscordException("Dein Discord-Account konnte nicht geladen werden. Bitte versuche es erneut.");
		}
		return new DiscordUser(id, displayName(user), string(user, "avatar"));
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
		HttpResponse<String> response = send(botRequest(konfiguration, "/guilds/" + konfiguration.getGuildId() + "/members/" + user.id, SCHREIB_TIMEOUT)
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
	 * <p>
	 * Laedt gerade eine andere Anfrage den Kanal neu, wird nicht gewartet, sondern der
	 * bisherige Stand geliefert. Schlaegt das Laden fehl, wird eine Weile nicht erneut gefragt.
	 * @param konfiguration Die Bot-Einstellungen
	 * @param channelId Die ID des Kanals
	 * @param channelNames Bekannte Kanalnamen (ID -&gt; Name) zum Aufloesen von Kanalverweisen
	 * @return Die Nachrichten
	 */
	public List<DiscordChatMessage> getMessages(DiscordKonfiguration konfiguration, String channelId, Map<String, String> channelNames)
	{
		ChannelCache cache = messageCache.computeIfAbsent(channelId, k -> new ChannelCache());
		if (!isNeuZuLaden(cache) || !cache.lock.tryLock())
		{
			return cache.ergebnis();
		}
		try
		{
			if (!isNeuZuLaden(cache))
			{
				return cache.ergebnis();
			}
			long now = System.currentTimeMillis();
			cache.fetchedAt = now;

			HttpResponse<String> response;
			try
			{
				response = send(botRequest(konfiguration, "/channels/" + channelId + "/messages?limit=" + MESSAGE_LIMIT, LESE_TIMEOUT).GET());
			}
			catch (DiscordException e)
			{
				sperre(cache, now, e.getMessage());
				throw e;
			}
			if (response.statusCode() == 429)
			{
				cache.blockedUntil = now + retryAfterMillis(response);
				return cache.messages;
			}
			if (response.statusCode() != 200)
			{
				log.warn("Discord: Laden der Nachrichten aus Kanal {} fehlgeschlagen ({}): {}", channelId, response.statusCode(), response.body());
				sperre(cache, now, "Die Nachrichten konnten nicht von Discord geladen werden.");
				return cache.ergebnis();
			}

			JsonArray messages = JsonParser.parseString(response.body()).getAsJsonArray();
			List<DiscordChatMessage> result = new ArrayList<>();
			int[] neueMitglieder = {MAX_NEUE_MITGLIEDER_PRO_ABRUF};
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
				result.add(toChatMessage(konfiguration, message, channelNames, neueMitglieder));
			}
			cache.messages = Collections.unmodifiableList(result);
			cache.fehler = null;
			return cache.messages;
		}
		finally
		{
			cache.lock.unlock();
		}
	}

	private static boolean isNeuZuLaden(ChannelCache cache)
	{
		long now = System.currentTimeMillis();
		return now - cache.fetchedAt >= MESSAGE_CACHE_MILLIS && now >= cache.blockedUntil;
	}

	private static void sperre(ChannelCache cache, long now, String fehler)
	{
		cache.fehler = fehler;
		cache.blockedUntil = now + FEHLER_CACHE_MILLIS;
	}

	/**
	 * Sendet eine Nachricht im Namen eines Spielers in einen Kanal.
	 * Der Aufrufer muss vorher pruefen, dass der Spieler im Spiel in den Kanal schreiben darf.
	 * Hier wird zusaetzlich geprueft, ob der Discord-Account dort schreiben duerfte: Er muss
	 * Mitglied sein, die Regeln des Servers bestaetigt haben, darf nicht stummgeschaltet sein,
	 * braucht im Kanal die Rechte zum Ansehen und Schreiben und unterliegt dem Slowmode.
	 * @param konfiguration Die Bot-Einstellungen
	 * @param channelId Die ID des Kanals
	 * @param discordUserId Die ID des verknuepften Discord-Accounts. Name und Avatar der Nachricht
	 * sind sein Server-Alias und Server-Avatar, wie bei einer direkt in Discord geschriebenen Nachricht.
	 * @param text Der Text
	 * @return Die ID der gesendeten Nachricht
	 */
	public String sendMessage(DiscordKonfiguration konfiguration, String channelId, String discordUserId, String text)
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
		content = bereiteZumSendenVor(konfiguration, entschaerfeLinks(content));
		if (content.length() > MAX_MESSAGE_LENGTH)
		{
			throw new DiscordException("Die Nachricht ist zu lang (maximal " + MAX_MESSAGE_LENGTH + " Zeichen).");
		}

		// Immer frisch laden: Ein Bann oder Timeout soll sofort wirken
		Mitglied mitglied = ladeMitglied(konfiguration, discordUserId, true);
		if (!mitglied.mitglied)
		{
			throw new DiscordException("Dein Discord-Account ist kein Mitglied unseres Discord-Servers. Tritt dem Server bei und versuche es erneut.");
		}
		if (mitglied.wartend)
		{
			throw new DiscordException("Bestätige zuerst in Discord die Regeln unseres Discord-Servers.");
		}
		long now = System.currentTimeMillis();
		if (mitglied.stummBis > now)
		{
			throw new DiscordException("Du bist auf unserem Discord-Server bis " + TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(mitglied.stummBis)) + " stummgeschaltet.");
		}

		ServerDaten serverDaten = ladeServer(konfiguration);
		if (serverDaten.fehler != null)
		{
			throw new DiscordException(serverDaten.fehler);
		}
		JsonObject channel = ladeKanal(konfiguration, channelId);
		long rechte = mitgliedsRechte(serverDaten.ownerId, serverDaten.rollenRechte, konfiguration.getGuildId(), discordUserId, mitglied.rollen, channel);
		if ((rechte & (RECHT_KANAL_ANSEHEN | RECHT_NACHRICHTEN_SENDEN)) != (RECHT_KANAL_ANSEHEN | RECHT_NACHRICHTEN_SENDEN))
		{
			throw new DiscordException("Dein Discord-Account darf in diesem Kanal nicht schreiben.");
		}
		boolean ohneSlowmode = (rechte & (RECHT_NACHRICHTEN_VERWALTEN | RECHT_KANAELE_VERWALTEN)) != 0;
		int slowmode = ohneSlowmode || !channel.has("rate_limit_per_user") ? 0 : channel.get("rate_limit_per_user").getAsInt();
		sendeLimits.pruefeUndVermerke(discordUserId, channelId, slowmode, now);

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
			cache.fetchedAt = 0;
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
	 * Gibt die aktuelle {@link #rechteSignatur} eines Kanals zurueck (zwischengespeichert).
	 * @param konfiguration Die Bot-Einstellungen
	 * @param channelId Die ID des Kanals
	 * @return Die Signatur
	 * @throws DiscordException Falls Discord nicht antwortet oder es den Kanal nicht mehr gibt
	 */
	public String getRechteSignatur(DiscordKonfiguration konfiguration, String channelId)
	{
		ServerDaten serverDaten = ladeServer(konfiguration);
		if (serverDaten.fehler != null)
		{
			throw new DiscordException(serverDaten.fehler);
		}
		JsonObject channel = ladeKanal(konfiguration, channelId);
		return rechteSignatur(konfiguration.getGuildId(), serverDaten.rollenRechte.getOrDefault(konfiguration.getGuildId(), 0L), channel);
	}

	/**
	 * Laedt die Textkanaele des Servers samt fehlender Rechte des Bots je Kanal.
	 * @param konfiguration Die Bot-Einstellungen
	 * @return Die Kanaele in der Reihenfolge von Discord
	 */
	public List<DiscordGuildKanal> ladeKanaele(DiscordKonfiguration konfiguration)
	{
		BotRechte rechte = ladeBotRechte(konfiguration);

		HttpResponse<String> response = send(botRequest(konfiguration, "/guilds/" + konfiguration.getGuildId() + "/channels", LESE_TIMEOUT).GET());
		if (response.statusCode() != 200)
		{
			log.warn("Discord: Laden der Kanaele fehlgeschlagen ({}): {}", response.statusCode(), response.body());
			throw new DiscordException("Die Kanäle konnten nicht geladen werden (HTTP " + response.statusCode() + ").");
		}

		JsonArray channels = JsonParser.parseString(response.body()).getAsJsonArray();
		// Gleich als aktuellen Stand fuer die Pruefungen beim Lesen und Senden uebernehmen
		kanalListen.put(konfiguration.getGuildId(), kanalListe(channels));

		Map<String, JsonObject> kategorien = new HashMap<>();
		for (JsonElement element : channels)
		{
			JsonObject channel = element.getAsJsonObject();
			if (channel.get("type").getAsInt() == 4)
			{
				kategorien.put(string(channel, "id"), channel);
			}
		}

		long everyone = rechte.rollenRechte.getOrDefault(konfiguration.getGuildId(), 0L);
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
					fehlendeKanalRechte(rechte, konfiguration.getGuildId(), channel),
					isOeffentlich(konfiguration.getGuildId(), everyone, channel),
					rechteSignatur(konfiguration.getGuildId(), everyone, channel)));
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
		if (!DiscordKonfiguration.isSetzt(konfiguration.getRedirectUri()))
		{
			pruefung.fehler("Die Redirect-URI ist nicht gesetzt - ohne sie können Spieler ihren Account nicht verknüpfen.");
		}

		try
		{
			HttpResponse<String> response = send(botRequest(konfiguration, "/applications/@me", LESE_TIMEOUT).GET());
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

			response = send(botRequest(konfiguration, "/guilds/" + konfiguration.getGuildId(), LESE_TIMEOUT).GET());
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
			if (rechte.administrator)
			{
				pruefung.fehler("Der Bot ist Administrator. Gib ihm nur die benötigten Rechte - wer das Bot-Token hat, kann sonst den ganzen Server verwalten.");
			}
			else if ((rechte.serverRechte & RECHT_WEBHOOKS_VERWALTEN) != 0)
			{
				pruefung.fehler("Der Bot darf auf dem ganzen Server Webhooks verwalten. Erlaube das besser nur in den angebundenen Kanälen.");
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

		HttpResponse<String> response = send(botRequest(konfiguration, "/users/@me", LESE_TIMEOUT).GET());
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
		response = send(botRequest(konfiguration, "/guilds/" + guildId + "/members/" + rechte.botId, LESE_TIMEOUT).GET());
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

		response = send(botRequest(konfiguration, "/guilds/" + guildId + "/roles", LESE_TIMEOUT).GET());
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
		rechte.administrator = (serverRechte & RECHT_ADMINISTRATOR) != 0;
		rechte.serverRechte = rechte.administrator ? ALLE_RECHTE : serverRechte;
		return rechte;
	}

	/**
	 * Berechnet die fehlenden Rechte des Bots in einem Kanal.
	 */
	static String fehlendeKanalRechte(BotRechte rechte, String guildId, JsonObject channel)
	{
		long kanalRechte = kanalRechte(rechte.serverRechte, guildId, rechte.rollen, rechte.botId, channel);
		List<String> fehlend = fehlendeRechte(kanalRechte, KANAL_RECHTE);
		return fehlend.isEmpty() ? null : "Bot fehlt: " + String.join(", ", fehlend);
	}

	/**
	 * Berechnet die Rechte eines Mitglieds in einem Kanal nach dem Verfahren von Discord:
	 * Der Eigentuemer und Administratoren duerfen alles, sonst gelten die Rechte von @everyone und
	 * den Rollen des Mitglieds, angepasst durch die Overwrites des Kanals.
	 */
	static long mitgliedsRechte(String ownerId, Map<String, Long> rollenRechte, String guildId, String userId, Set<String> rollen, JsonObject channel)
	{
		if (userId.equals(ownerId))
		{
			return ALLE_RECHTE;
		}
		long basis = rollenRechte.getOrDefault(guildId, 0L);
		for (String rolle : rollen)
		{
			basis |= rollenRechte.getOrDefault(rolle, 0L);
		}
		if ((basis & RECHT_ADMINISTRATOR) != 0)
		{
			return ALLE_RECHTE;
		}
		return kanalRechte(basis, guildId, rollen, userId, channel);
	}

	/**
	 * Wendet die Overwrites eines Kanals auf Server-Rechte an: erst die fuer @everyone,
	 * dann die der Rollen, zuletzt die fuer den Account selbst.
	 */
	private static long kanalRechte(long serverRechte, String guildId, Set<String> rollen, String userId, JsonObject channel)
	{
		if (serverRechte == ALLE_RECHTE || !channel.has("permission_overwrites"))
		{
			return serverRechte;
		}
		long rollenErlaubt = 0;
		long rollenVerboten = 0;
		long everyoneErlaubt = 0;
		long everyoneVerboten = 0;
		long accountErlaubt = 0;
		long accountVerboten = 0;
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
			else if (overwrite.get("type").getAsInt() == 0 && rollen.contains(id))
			{
				rollenErlaubt |= erlaubt;
				rollenVerboten |= verboten;
			}
			else if (overwrite.get("type").getAsInt() == 1 && id.equals(userId))
			{
				accountErlaubt = erlaubt;
				accountVerboten = verboten;
			}
		}
		long kanalRechte = serverRechte;
		kanalRechte = (kanalRechte & ~everyoneVerboten) | everyoneErlaubt;
		kanalRechte = (kanalRechte & ~rollenVerboten) | rollenErlaubt;
		kanalRechte = (kanalRechte & ~accountVerboten) | accountErlaubt;
		return kanalRechte;
	}

	/**
	 * Gibt zurueck, ob @everyone den Kanal sehen darf.
	 */
	static boolean isOeffentlich(String guildId, long everyoneRechte, JsonObject channel)
	{
		long rechte = (everyoneRechte & RECHT_ADMINISTRATOR) != 0 ? ALLE_RECHTE : everyoneRechte;
		return (kanalRechte(rechte, guildId, Collections.emptySet(), null, channel) & RECHT_KANAL_ANSEHEN) != 0;
	}

	/**
	 * Fasst zusammen, wer einen Kanal in Discord mitlesen kann: die Sichtbarkeitsrechte von
	 * @everyone und alle Overwrites des Kanals, die diese Rechte beruehren. Aendert sich die
	 * Signatur, wurde der Kanal in Discord fuer andere geoeffnet oder geschlossen.
	 * @param guildId Die ID des Servers
	 * @param everyoneRechte Die Server-Rechte von @everyone
	 * @param channel Der Kanal, wie Discord ihn liefert
	 * @return Die Signatur als Hex-String (64 Zeichen)
	 */
	static String rechteSignatur(String guildId, long everyoneRechte, JsonObject channel)
	{
		List<String> teile = new ArrayList<>();
		if (channel.has("permission_overwrites"))
		{
			for (JsonElement element : channel.getAsJsonArray("permission_overwrites"))
			{
				JsonObject overwrite = element.getAsJsonObject();
				long erlaubt = Long.parseLong(string(overwrite, "allow")) & SICHTBARKEITS_RECHTE;
				long verboten = Long.parseLong(string(overwrite, "deny")) & SICHTBARKEITS_RECHTE;
				if (erlaubt != 0 || verboten != 0)
				{
					teile.add(overwrite.get("type").getAsInt() + ":" + string(overwrite, "id") + ":" + erlaubt + ":" + verboten);
				}
			}
		}
		Collections.sort(teile);
		String text = guildId + "|" + (everyoneRechte & (SICHTBARKEITS_RECHTE | RECHT_ADMINISTRATOR)) + "|" + String.join(";", teile);
		try
		{
			StringBuilder hex = new StringBuilder();
			for (byte b : MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)))
			{
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException(e);
		}
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
	 * Laedt einen Account als Mitglied des Servers.
	 * @param frisch <code>true</code>, um den Zwischenspeicher zu umgehen
	 * @throws DiscordException Falls Discord nicht sauber antwortet
	 */
	private Mitglied ladeMitglied(DiscordKonfiguration konfiguration, String discordUserId, boolean frisch)
	{
		if (discordUserId == null || !SNOWFLAKE.matcher(discordUserId).matches())
		{
			return new Mitglied(false, null, null, null, Collections.emptySet(), false, 0);
		}
		String key = memberKey(konfiguration, discordUserId);
		Mitglied mitglied = mitglieder.get(key);
		if (!frisch && mitglied != null && mitglied.isAktuell(System.currentTimeMillis()))
		{
			if (mitglied.fehlgeschlagen)
			{
				throw new DiscordException(NICHT_ERREICHBAR);
			}
			return mitglied;
		}

		HttpResponse<String> response;
		try
		{
			response = send(botRequest(konfiguration, "/guilds/" + konfiguration.getGuildId() + "/members/" + discordUserId, LESE_TIMEOUT).GET());
		}
		catch (DiscordException e)
		{
			mitglieder.put(key, Mitglied.fehlgeschlagen());
			throw e;
		}
		if (response.statusCode() == 200)
		{
			JsonObject member = JsonParser.parseString(response.body()).getAsJsonObject();
			JsonObject user = member.getAsJsonObject("user");
			String nick = string(member, "nick");
			String serverAvatar = string(member, "avatar");
			Set<String> rollenIds = new HashSet<>();
			for (JsonElement rolle : member.getAsJsonArray("roles"))
			{
				rollenIds.add(rolle.getAsString());
			}
			boolean wartend = member.has("pending") && !member.get("pending").isJsonNull() && member.get("pending").getAsBoolean();
			mitglied = new Mitglied(true,
					nick != null && !nick.isEmpty() ? nick : displayName(user),
					DiscordUser.isAvatarHash(serverAvatar)
							? "https://cdn.discordapp.com/guilds/" + konfiguration.getGuildId() + "/users/" + discordUserId + "/avatars/" + serverAvatar + ".png?size=64"
							: DiscordUser.avatarUrl(discordUserId, string(user, "avatar")),
					rollenFarbe(konfiguration, rollenIds),
					rollenIds,
					wartend,
					zeitpunkt(string(member, "communication_disabled_until")));
		}
		else if (response.statusCode() == 404)
		{
			mitglied = new Mitglied(false, null, null, null, Collections.emptySet(), false, 0);
		}
		else
		{
			log.warn("Discord: Laden des Mitglieds {} fehlgeschlagen ({}): {}", discordUserId, response.statusCode(), response.body());
			mitglieder.put(key, Mitglied.fehlgeschlagen());
			throw new DiscordException(NICHT_ERREICHBAR);
		}
		mitglieder.put(key, mitglied);
		return mitglied;
	}

	private static long zeitpunkt(String iso)
	{
		if (iso == null || iso.isEmpty())
		{
			return 0;
		}
		try
		{
			return OffsetDateTime.parse(iso).toInstant().toEpochMilli();
		}
		catch (DateTimeParseException e)
		{
			// Lieber als stummgeschaltet behandeln als einen Timeout zu uebergehen
			return Long.MAX_VALUE;
		}
	}

	/**
	 * Laedt Eigentuemer, Rollen und Emojis des Servers (zwischengespeichert).
	 * Schlaegt das fehl, ist <code>fehler</code> gesetzt und es wird eine Weile nicht erneut gefragt.
	 */
	private ServerDaten ladeServer(DiscordKonfiguration konfiguration)
	{
		ServerDaten geladen = server.get(konfiguration.getGuildId());
		if (geladen != null && geladen.isAktuell(System.currentTimeMillis()))
		{
			return geladen;
		}
		geladen = new ServerDaten();
		try
		{
			HttpResponse<String> response = send(botRequest(konfiguration, "/guilds/" + konfiguration.getGuildId(), LESE_TIMEOUT).GET());
			if (response.statusCode() != 200)
			{
				log.warn("Discord: Laden des Servers fehlgeschlagen ({}): {}", response.statusCode(), response.body());
				geladen.fehler = NICHT_ERREICHBAR;
			}
			else
			{
				JsonObject guild = JsonParser.parseString(response.body()).getAsJsonObject();
				geladen.ownerId = string(guild, "owner_id");
				for (JsonElement element : guild.getAsJsonArray("roles"))
				{
					JsonObject rolle = element.getAsJsonObject();
					geladen.rollenRechte.put(string(rolle, "id"), Long.parseLong(string(rolle, "permissions")));
					geladen.farbeUndPosition.put(string(rolle, "id"), new int[]{rolle.get("color").getAsInt(), rolle.get("position").getAsInt()});
				}
				if (guild.has("emojis"))
				{
					for (JsonElement element : guild.getAsJsonArray("emojis"))
					{
						JsonObject emoji = element.getAsJsonObject();
						boolean animiert = emoji.has("animated") && emoji.get("animated").getAsBoolean();
						geladen.emojis.put(string(emoji, "name"), "<" + (animiert ? "a" : "") + ":" + string(emoji, "name") + ":" + string(emoji, "id") + ">");
					}
				}
			}
		}
		catch (DiscordException e)
		{
			geladen.fehler = e.getMessage();
		}
		server.put(konfiguration.getGuildId(), geladen);
		return geladen;
	}

	/**
	 * Laedt einen Kanal des Servers samt Rechten und Slowmode (zwischengespeichert).
	 * @throws DiscordException Falls Discord nicht antwortet oder es den Kanal nicht gibt
	 */
	private JsonObject ladeKanal(DiscordKonfiguration konfiguration, String channelId)
	{
		KanalListe liste = kanalListen.get(konfiguration.getGuildId());
		if (liste == null || !liste.isAktuell(System.currentTimeMillis()))
		{
			liste = new KanalListe();
			try
			{
				HttpResponse<String> response = send(botRequest(konfiguration, "/guilds/" + konfiguration.getGuildId() + "/channels", LESE_TIMEOUT).GET());
				if (response.statusCode() == 200)
				{
					liste = kanalListe(JsonParser.parseString(response.body()).getAsJsonArray());
				}
				else
				{
					log.warn("Discord: Laden der Kanaele fehlgeschlagen ({}): {}", response.statusCode(), response.body());
					liste.fehler = NICHT_ERREICHBAR;
				}
			}
			catch (DiscordException e)
			{
				liste.fehler = e.getMessage();
			}
			kanalListen.put(konfiguration.getGuildId(), liste);
		}
		if (liste.fehler != null)
		{
			throw new DiscordException(liste.fehler);
		}
		JsonObject channel = liste.kanaele.get(channelId);
		if (channel == null)
		{
			throw new DiscordException("Diesen Kanal gibt es auf Discord nicht mehr.");
		}
		return channel;
	}

	private static KanalListe kanalListe(JsonArray channels)
	{
		KanalListe liste = new KanalListe();
		for (JsonElement element : channels)
		{
			JsonObject channel = element.getAsJsonObject();
			liste.kanaele.put(string(channel, "id"), channel);
		}
		return liste;
	}

	/**
	 * Wie in Discord: Die Farbe der hoechsten Rolle, die eine Farbe hat. Ist der Server
	 * gerade nicht zu laden, bleiben die Namen eben ungefaerbt.
	 */
	private String rollenFarbe(DiscordKonfiguration konfiguration, Set<String> rollenIds)
	{
		ServerDaten serverDaten = ladeServer(konfiguration);
		int farbe = 0;
		int position = -1;
		for (String rolle : rollenIds)
		{
			int[] daten = serverDaten.farbeUndPosition.get(rolle);
			if (daten != null && daten[0] != 0 && daten[1] > position)
			{
				farbe = daten[0];
				position = daten[1];
			}
		}
		return position == -1 ? null : String.format("#%06x", farbe);
	}

	/**
	 * Gibt die Rollenfarben von Accounts zurueck. Wie beim Laden der Nachrichten werden je Aufruf
	 * nur wenige noch unbekannte Accounts nachgeladen, die uebrigen bleiben vorerst ungefaerbt.
	 * @param konfiguration Die Bot-Einstellungen
	 * @param discordUserIds Die IDs der Accounts
	 * @return ID -&gt; Farbe als <code>#rrggbb</code>, fuer Accounts ohne Farbe oder ohne Antwort von Discord fehlt der Eintrag
	 */
	public Map<String, String> getRollenFarben(DiscordKonfiguration konfiguration, Collection<String> discordUserIds)
	{
		Map<String, String> farben = new HashMap<>();
		int[] neueMitglieder = {MAX_NEUE_MITGLIEDER_PRO_ABRUF};
		for (String discordUserId : new HashSet<>(discordUserIds))
		{
			Mitglied mitglied = ladeMitgliedOderNull(konfiguration, discordUserId, neueMitglieder);
			if (mitglied != null && mitglied.farbe != null)
			{
				farben.put(discordUserId, mitglied.farbe);
			}
		}
		return farben;
	}

	/**
	 * Wie {@link #ladeMitglied}, wirft aber nicht: Beim Anzeigen von Nachrichten reicht notfalls der Accountname.
	 * @param neueMitglieder Wie viele noch nicht zwischengespeicherte Accounts noch geladen werden duerfen. Wird heruntergezaehlt.
	 */
	private Mitglied ladeMitgliedOderNull(DiscordKonfiguration konfiguration, String discordUserId, int[] neueMitglieder)
	{
		Mitglied vorhanden = mitglieder.get(memberKey(konfiguration, discordUserId));
		boolean zwischengespeichert = vorhanden != null && vorhanden.isAktuell(System.currentTimeMillis());
		if (!zwischengespeichert)
		{
			if (neueMitglieder[0] <= 0)
			{
				return null;
			}
			neueMitglieder[0]--;
		}
		try
		{
			Mitglied mitglied = ladeMitglied(konfiguration, discordUserId, false);
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
				.timeout(SCHREIB_TIMEOUT)
				.header("User-Agent", USER_AGENT)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json)));
	}

	private Webhook getWebhook(DiscordKonfiguration konfiguration, String channelId)
	{
		Webhook webhook = webhooks.get(channelId);
		if (webhook != null)
		{
			return webhook;
		}

		// Je Kanal nur einmal anlegen, ohne andere Kanaele aufzuhalten
		synchronized (webhookSperren.computeIfAbsent(channelId, k -> new Object()))
		{
			webhook = webhooks.get(channelId);
			if (webhook != null)
			{
				return webhook;
			}

			HttpResponse<String> response = send(botRequest(konfiguration, "/channels/" + channelId + "/webhooks", LESE_TIMEOUT).GET());
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
			response = send(botRequest(konfiguration, "/channels/" + channelId + "/webhooks", SCHREIB_TIMEOUT)
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
	}

	private DiscordChatMessage toChatMessage(DiscordKonfiguration konfiguration, JsonObject message, Map<String, String> channelNames, int[] neueMitglieder)
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
			Mitglied mitglied = ladeMitgliedOderNull(konfiguration, authorId, neueMitglieder);
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

		return new DiscordChatMessage(
				string(message, "id"),
				autorName,
				autorAvatar,
				text,
				string(message, "timestamp"),
				webhook,
				attachments,
				autorFarbe);
	}

	/**
	 * Uebernimmt ein Medium nur, wenn es von Discord selbst ausgeliefert wird. So laedt der Browser
	 * des Spielers nie etwas von fremden Seiten, die in eine Nachricht eingebettet wurden.
	 */
	private static DiscordChatMessage.Attachment medium(String art, String name, JsonObject quelle)
	{
		if (quelle == null)
		{
			return new DiscordChatMessage.Attachment(art, name, null, 0, 0);
		}
		JsonElement breite = quelle.get("width");
		JsonElement hoehe = quelle.get("height");
		// proxy_url liefert Discord ueber das eigene CDN aus, auch fuer Bilder fremder Seiten
		String url = string(quelle, "proxy_url");
		if (!isDiscordCdn(url))
		{
			url = isDiscordCdn(string(quelle, "url")) ? string(quelle, "url") : null;
		}
		return new DiscordChatMessage.Attachment(art, name, url,
				breite != null && !breite.isJsonNull() ? breite.getAsInt() : 0,
				hoehe != null && !hoehe.isJsonNull() ? hoehe.getAsInt() : 0);
	}

	/**
	 * Prueft, ob eine URL per HTTPS auf das CDN von Discord zeigt
	 * (<code>cdn.discordapp.com</code>, <code>media.discordapp.net</code>, <code>images-ext-*.discordapp.net</code>).
	 * @param url Die URL
	 * @return <code>true</code>, falls dem so ist
	 */
	static boolean isDiscordCdn(String url)
	{
		if (url == null)
		{
			return false;
		}
		try
		{
			URI uri = new URI(url);
			String host = uri.getHost();
			return "https".equals(uri.getScheme())
					&& uri.getUserInfo() == null
					&& uri.getPort() == -1
					&& host != null
					&& (host.equals("cdn.discordapp.com") || host.endsWith(".discordapp.net"));
		}
		catch (URISyntaxException e)
		{
			return false;
		}
	}

	/**
	 * Ersetzt die Discord-Markup-Verweise (Erwaehnungen, Kanaele, Zeitstempel) durch lesbaren Text
	 * und Emoji-Kurzformen wie <code>:thumbsup:</code> durch das Emoji. Server-Emojis
	 * (<code>&lt;:name:id&gt;</code>) bleiben stehen, die zeigt der Chat als Bild an.
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
		text = replace(TIMESTAMP, text, m -> TIMESTAMP_FORMAT.format(Instant.ofEpochSecond(Long.parseLong(m.group(1)))));
		return ersetzeKurzformen(text, Collections.emptyMap());
	}

	/**
	 * Macht aus maskierten Links <code>[Text](URL)</code> sichtbare Links <code>Text (URL)</code>.
	 * Ueber den Webhook liesse sich sonst hinter harmlosem Text ein beliebiges Ziel verstecken.
	 * @param text Der Text
	 * @return Der Text ohne maskierte Links
	 */
	static String entschaerfeLinks(String text)
	{
		String result = text;
		while (true)
		{
			// Jede Ersetzung entfernt eine eckige Klammer, die Schleife endet also
			String neu = replace(MASKIERTER_LINK, result, m -> m.group(1) + " (" + m.group(2) + ")");
			if (neu.equals(result))
			{
				return result;
			}
			result = neu;
		}
	}

	/**
	 * Bereitet den Text einer Nachricht aus dem Spiel fuer Discord vor. Der Discord-Client wandelt
	 * Kurzformen beim Tippen selbst um - fuer per Webhook gesendeten Text uebernimmt das DS:
	 * Namen von Server-Emojis werden zu <code>&lt;:name:id&gt;</code>, alle anderen bekannten Kurzformen zum Emoji.
	 * @param konfiguration Die Bot-Einstellungen
	 * @param text Der eingegebene Text
	 * @return Der Text fuer Discord
	 */
	public String bereiteZumSendenVor(DiscordKonfiguration konfiguration, String text)
	{
		if (text == null || !KURZFORM.matcher(text).find())
		{
			return text;
		}
		// Ist der Server gerade nicht zu laden, werden eben nur die Unicode-Kurzformen umgewandelt
		return ersetzeKurzformen(text, ladeServer(konfiguration).emojis);
	}

	/**
	 * Ersetzt <code>:name:</code> durch das Server-Emoji gleichen Namens oder das Unicode-Emoji.
	 * Unbekannte Namen bleiben stehen, ebenso der Name innerhalb von <code>&lt;:name:id&gt;</code>.
	 */
	static String ersetzeKurzformen(String text, Map<String, String> serverEmojis)
	{
		return replace(KURZFORM, text, m -> {
			String name = m.group(1);
			String serverEmoji = serverEmojis.get(name);
			if (serverEmoji != null)
			{
				return serverEmoji;
			}
			Emoji emoji = EmojiManager.getForAlias(name);
			return emoji != null ? emoji.getUnicode() : m.group();
		});
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

	private HttpRequest.Builder botRequest(DiscordKonfiguration konfiguration, String path, Duration timeout)
	{
		return HttpRequest.newBuilder(URI.create(API + path))
				.timeout(timeout)
				.header("User-Agent", USER_AGENT)
				.header("Authorization", "Bot " + konfiguration.getBotToken().trim());
	}

	/**
	 * Stellt eine Anfrage an Discord. Waehrend Discord global bremst oder zu viele ungueltige
	 * Anfragen gestellt wurden, wird gar nicht erst gefragt.
	 */
	private HttpResponse<String> send(HttpRequest.Builder request)
	{
		if (System.currentTimeMillis() < gesperrtBis)
		{
			throw new DiscordException("Discord nimmt gerade keine Anfragen an. Bitte versuche es später erneut.");
		}
		HttpResponse<String> response;
		try
		{
			response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			log.warn("Discord ist nicht erreichbar: {}", e.toString());
			throw new DiscordException(NICHT_ERREICHBAR, e);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new DiscordException("Die Anfrage an Discord wurde abgebrochen.", e);
		}
		int status = response.statusCode();
		if (status == 401 || status == 403 || status == 429)
		{
			vermerkeUngueltig(response);
		}
		return response;
	}

	private void vermerkeUngueltig(HttpResponse<String> response)
	{
		long now = System.currentTimeMillis();
		if (response.statusCode() == 429 && (response.headers().firstValue("X-RateLimit-Global").map("true"::equalsIgnoreCase).orElse(false)
				|| response.headers().firstValue("X-RateLimit-Scope").map("global"::equals).orElse(false)))
		{
			gesperrtBis = Math.max(gesperrtBis, now + retryAfterMillis(response));
		}
		synchronized (ungueltigeSperre)
		{
			if (now - ungueltigeSeit >= UNGUELTIGE_FENSTER_MILLIS)
			{
				ungueltigeSeit = now;
				ungueltige = 0;
			}
			ungueltige++;
			if (ungueltige == MAX_UNGUELTIGE_ANFRAGEN)
			{
				log.error("Discord: {} ungueltige Anfragen in 10 Minuten - Anfragen werden bis zum Ende des Zeitraums eingestellt", ungueltige);
				gesperrtBis = Math.max(gesperrtBis, ungueltigeSeit + UNGUELTIGE_FENSTER_MILLIS);
			}
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
