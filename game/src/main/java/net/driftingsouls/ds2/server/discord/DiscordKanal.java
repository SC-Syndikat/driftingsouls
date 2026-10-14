package net.driftingsouls.ds2.server.discord;

import net.driftingsouls.ds2.server.entities.User;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Ein mit dem Discord-Server synchronisierter Kanal und wer ihn im Ingame-Chat nutzen darf.
 * Neu synchronisierte Kanaele sind gesperrt, bis ein Admin sie freigibt.
 */
@Entity
@Table(name = "discord_kanal")
public class DiscordKanal
{
	@Id
	@GeneratedValue
	private int id;
	@Column(name = "guild_id", nullable = false)
	private String guildId;
	@Column(name = "channel_id", nullable = false)
	private String channelId;
	@Column(nullable = false)
	private String name;
	private String kategorie;
	@Column(nullable = false)
	private int position;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DiscordZugriff lesen = DiscordZugriff.GESPERRT;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DiscordZugriff schreiben = DiscordZugriff.GESPERRT;
	@Column(nullable = false)
	private boolean standard;
	@Column(name = "bot_hinweis")
	private String botHinweis;
	@Version
	private int version;

	protected DiscordKanal()
	{
		// JPA
	}

	public DiscordKanal(String guildId, String channelId)
	{
		this.guildId = guildId;
		this.channelId = channelId;
	}

	/**
	 * Gibt alle Kanaele eines Discord-Servers in der Reihenfolge von Discord zurueck.
	 * @param em Der EntityManager
	 * @param guildId Die ID des Discord-Servers
	 * @return Die Kanaele
	 */
	public static List<DiscordKanal> alle(EntityManager em, String guildId)
	{
		return em.createQuery("from DiscordKanal where guildId=:guild order by position, name", DiscordKanal.class)
				.setParameter("guild", guildId != null ? guildId : "")
				.getResultList();
	}

	/**
	 * Gibt die Kanaele zurueck, die der Spieler im Ingame-Chat lesen darf. Ist der Chat nicht
	 * eingerichtet, ist die Liste leer. Alle Zugriffe auf Kanaele muessen hierueber laufen.
	 * @param em Der EntityManager
	 * @param konfiguration Die Bot-Einstellungen
	 * @param user Der Spieler
	 * @return Die lesbaren Kanaele
	 */
	public static List<DiscordKanal> lesbare(EntityManager em, DiscordKonfiguration konfiguration, User user)
	{
		if (!konfiguration.isChatEingerichtet())
		{
			return List.of();
		}
		return alle(em, konfiguration.getGuildId()).stream()
				.filter(kanal -> kanal.darfLesen(user))
				.collect(Collectors.toList());
	}

	public boolean darfLesen(User user)
	{
		return lesen.erlaubt(user);
	}

	/**
	 * Schreiben setzt immer auch das Leserecht voraus.
	 * @param user Der Spieler
	 * @return <code>true</code>, falls der Spieler in diesen Kanal schreiben darf
	 */
	public boolean darfSchreiben(User user)
	{
		return darfLesen(user) && schreiben.erlaubt(user);
	}

	public int getId()
	{
		return id;
	}

	public String getGuildId()
	{
		return guildId;
	}

	public String getChannelId()
	{
		return channelId;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public String getKategorie()
	{
		return kategorie;
	}

	public void setKategorie(String kategorie)
	{
		this.kategorie = kategorie;
	}

	public int getPosition()
	{
		return position;
	}

	public void setPosition(int position)
	{
		this.position = position;
	}

	public DiscordZugriff getLesen()
	{
		return lesen;
	}

	public void setLesen(DiscordZugriff lesen)
	{
		this.lesen = lesen;
	}

	public DiscordZugriff getSchreiben()
	{
		return schreiben;
	}

	public void setSchreiben(DiscordZugriff schreiben)
	{
		this.schreiben = schreiben;
	}

	public boolean isStandard()
	{
		return standard;
	}

	public void setStandard(boolean standard)
	{
		this.standard = standard;
	}

	/**
	 * Gibt die beim letzten Synchronisieren festgestellten fehlenden Rechte des Bots zurueck.
	 * @return Der Hinweis oder <code>null</code>, falls alles passt
	 */
	public String getBotHinweis()
	{
		return botHinweis;
	}

	public void setBotHinweis(String botHinweis)
	{
		this.botHinweis = botHinweis;
	}
}
