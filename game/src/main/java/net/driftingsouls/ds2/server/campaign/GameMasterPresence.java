package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.WellKnownConfigValue;
import net.driftingsouls.ds2.server.framework.ConfigService;
import net.driftingsouls.ds2.server.framework.authentication.UserActivityTracker;

import javax.persistence.EntityManager;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;

/**
 * Which of the campaign's game masters are online, and so who gets what is meant for "the" game master:
 * the one game master online, or all of them when none or several are.
 */
public class GameMasterPresence
{
	private final EntityManager em;
	private final IntPredicate online;

	/**
	 * Online means a request within the configured threshold, as in the participant status.
	 */
	public GameMasterPresence(EntityManager em)
	{
		this(em, onlineByActivity(em));
	}

	public GameMasterPresence(EntityManager em, IntPredicate online)
	{
		this.em = em;
		this.online = online;
	}

	private static IntPredicate onlineByActivity(EntityManager em)
	{
		int threshold = new ConfigService(em).getValue(WellKnownConfigValue.ONLINE_THRESHOLD_SECONDS);
		UserActivityTracker tracker = UserActivityTracker.getInstance();
		return id -> tracker.isOnline(id, threshold);
	}

	/**
	 * @return The campaign's game masters who are online
	 */
	public Set<Integer> online(Campaign campaign)
	{
		return new CampaignService(em).gamemasterIds(campaign).stream()
				.filter(online::test)
				.collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * @return The one game master online, or all game masters when none or several are online
	 */
	public Set<Integer> recipients(Campaign campaign)
	{
		Set<Integer> present = online(campaign);
		return present.size() == 1 ? present : new CampaignService(em).gamemasterIds(campaign);
	}
}
