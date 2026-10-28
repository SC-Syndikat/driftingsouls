package net.driftingsouls.ds2.server.discord;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Eine aus dem Spiel gesendete Discord-Nachricht. Webhook-Nachrichten verraten nicht,
 * welcher Discord-Account sie geschrieben hat - das wird hier festgehalten. Daran erkennt
 * der Chat auch, welche Webhook-Nachrichten wirklich aus dem Spiel kommen.
 * <p>
 * Der Chat zeigt nur die neuesten Nachrichten eines Kanals, die Eintraege werden daher nach
 * {@link #AUFBEWAHRUNG_TAGE} Tagen geloescht, ebenso beim Trennen der Verknuepfung und beim
 * Loeschen des Spielers.
 */
@Entity
@Table(name = "discord_nachricht")
public class DiscordNachricht
{
	public static final int AUFBEWAHRUNG_TAGE = 30;

	@Id
	@Column(name = "message_id")
	private String messageId;
	@Column(name = "discord_user_id", nullable = false)
	private String discordUserId;
	@Column(name = "user_id", nullable = false)
	private int userId;
	@Temporal(TemporalType.TIMESTAMP)
	@Column(nullable = false)
	private Date gesendet;

	protected DiscordNachricht()
	{
		// JPA
	}

	public DiscordNachricht(String messageId, String discordUserId, int userId)
	{
		this.messageId = messageId;
		this.discordUserId = discordUserId;
		this.userId = userId;
		this.gesendet = new Date();
	}

	/**
	 * Gibt zu den Nachrichten-IDs die Discord-Accounts der Verfasser zurueck, soweit bekannt.
	 * @param em Der EntityManager
	 * @param messageIds Die IDs der Nachrichten
	 * @return Nachrichten-ID -&gt; Discord-Account-ID
	 */
	public static Map<String, String> verfasser(EntityManager em, Collection<String> messageIds)
	{
		Map<String, String> result = new HashMap<>();
		if (messageIds.isEmpty())
		{
			return result;
		}
		for (DiscordNachricht nachricht : em.createQuery("from DiscordNachricht where messageId in (:ids)", DiscordNachricht.class)
				.setParameter("ids", messageIds)
				.getResultList())
		{
			result.put(nachricht.messageId, nachricht.discordUserId);
		}
		return result;
	}

	/**
	 * Loescht die Eintraege eines Spielers, z.B. beim Trennen der Verknuepfung.
	 * @param em Der EntityManager
	 * @param userId Die ID des Spielers in DS
	 */
	public static void loeschenFuer(EntityManager em, int userId)
	{
		em.createQuery("delete from DiscordNachricht where userId=:user")
				.setParameter("user", userId)
				.executeUpdate();
	}

	/**
	 * Loescht die Eintraege, die aelter als {@link #AUFBEWAHRUNG_TAGE} Tage sind.
	 * @param em Der EntityManager
	 */
	public static void loeschenAbgelaufene(EntityManager em)
	{
		em.createQuery("delete from DiscordNachricht where gesendet<:grenze")
				.setParameter("grenze", new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(AUFBEWAHRUNG_TAGE)))
				.executeUpdate();
	}
}
