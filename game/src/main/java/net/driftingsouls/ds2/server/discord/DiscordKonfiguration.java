package net.driftingsouls.ds2.server.discord;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

/**
 * Die Einstellungen des Discord-Bots fuer den Ingame-Chat. Es gibt genau einen Datensatz,
 * der im Adminbereich unter "Discord-Bot" bearbeitet wird.
 */
@Entity
@Table(name = "discord_konfiguration")
public class DiscordKonfiguration
{
	private static final int ID = 1;

	@Id
	private int id = ID;
	@Column(nullable = false)
	private boolean aktiv;
	@Column(name = "bot_token")
	private String botToken;
	@Column(name = "client_id")
	private String clientId;
	@Column(name = "client_secret")
	private String clientSecret;
	@Column(name = "redirect_uri")
	private String redirectUri;
	@Column(name = "guild_id")
	private String guildId;
	@Column(name = "invite_url")
	private String inviteUrl;
	@Version
	private int version;

	/**
	 * Laedt die Einstellungen. Fehlt der Datensatz, wird ein nicht gespeicherter, inaktiver geliefert.
	 * @param em Der EntityManager
	 * @return Die Einstellungen
	 */
	public static DiscordKonfiguration laden(EntityManager em)
	{
		DiscordKonfiguration konfiguration = em.find(DiscordKonfiguration.class, ID);
		return konfiguration != null ? konfiguration : new DiscordKonfiguration();
	}

	/**
	 * Gibt zurueck, ob der Chat benutzt werden kann (aktiv, Bot-Token und Server gesetzt).
	 * @return <code>true</code>, falls dem so ist
	 */
	public boolean isChatEingerichtet()
	{
		return aktiv && isSetzt(botToken) && isSetzt(guildId);
	}

	/**
	 * Gibt zurueck, ob Spieler ihren Account per OAuth2 verknuepfen koennen.
	 * @return <code>true</code>, falls zusaetzlich Client-ID und Client-Secret gesetzt sind
	 */
	public boolean isLoginEingerichtet()
	{
		return isChatEingerichtet() && isSetzt(clientId) && isSetzt(clientSecret);
	}

	/**
	 * Gibt die Redirect-URI fuer den OAuth2-Login zurueck. Ist keine gesetzt, wird sie aus
	 * der URL der aktuellen Anfrage abgeleitet.
	 * @param requestUrl Die URL der aktuellen Anfrage (z.B. <code>http://localhost:8080/ds</code>)
	 * @return Die Redirect-URI
	 */
	public String getRedirectUri(String requestUrl)
	{
		if (isSetzt(redirectUri))
		{
			return redirectUri.trim();
		}
		String basis = requestUrl;
		int query = basis.indexOf('?');
		if (query != -1)
		{
			basis = basis.substring(0, query);
		}
		return basis.substring(0, basis.lastIndexOf('/') + 1) + "ds?module=options&action=discordCallback";
	}

	public static boolean isSetzt(String value)
	{
		return value != null && !value.trim().isEmpty();
	}

	public boolean isAktiv()
	{
		return aktiv;
	}

	public void setAktiv(boolean aktiv)
	{
		this.aktiv = aktiv;
	}

	public String getBotToken()
	{
		return botToken;
	}

	public void setBotToken(String botToken)
	{
		this.botToken = botToken;
	}

	public String getClientId()
	{
		return clientId;
	}

	public void setClientId(String clientId)
	{
		this.clientId = clientId;
	}

	public String getClientSecret()
	{
		return clientSecret;
	}

	public void setClientSecret(String clientSecret)
	{
		this.clientSecret = clientSecret;
	}

	public String getRedirectUri()
	{
		return redirectUri;
	}

	public void setRedirectUri(String redirectUri)
	{
		this.redirectUri = redirectUri;
	}

	public String getGuildId()
	{
		return guildId;
	}

	public void setGuildId(String guildId)
	{
		this.guildId = guildId;
	}

	public String getInviteUrl()
	{
		return inviteUrl;
	}

	public void setInviteUrl(String inviteUrl)
	{
		this.inviteUrl = inviteUrl;
	}
}
