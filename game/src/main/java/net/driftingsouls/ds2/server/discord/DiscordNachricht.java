package net.driftingsouls.ds2.server.discord;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Eine aus dem Spiel gesendete Discord-Nachricht. Webhook-Nachrichten verraten nicht,
 * welcher Discord-Account sie geschrieben hat - das wird hier festgehalten.
 */
@Entity
@Table(name = "discord_nachricht")
public class DiscordNachricht
{
	@Id
	@Column(name = "message_id")
	private String messageId;
	@Column(name = "discord_user_id", nullable = false)
	private String discordUserId;
	@Column(name = "user_id", nullable = false)
	private int userId;

	protected DiscordNachricht()
	{
		// JPA
	}

	public DiscordNachricht(String messageId, String discordUserId, int userId)
	{
		this.messageId = messageId;
		this.discordUserId = discordUserId;
		this.userId = userId;
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
}
