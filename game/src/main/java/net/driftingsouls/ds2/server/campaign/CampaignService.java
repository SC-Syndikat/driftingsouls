package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.Common;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Lifecycle of the campaign and its participant list.
 *
 * <p>The participant list is kept in {@code campaign_participant} while the campaign is prepared.
 * {@code users.campaign_participant} is the runtime flag the rest of the game reads; it is set only when
 * the campaign starts, and kept in sync with the list while it runs.
 */
public class CampaignService
{
	private final EntityManager em;
	private final CampaignRepository repository;

	public CampaignService(EntityManager em)
	{
		this(em, new CampaignRepository());
	}

	CampaignService(EntityManager em, CampaignRepository repository)
	{
		this.em = em;
		this.repository = repository;
	}

	/**
	 * @return The campaign that is being prepared or is running, if any
	 */
	public Optional<Campaign> findOpenCampaign()
	{
		return repository.findOpen();
	}

	public Campaign prepare(String name, User gamemaster)
	{
		if (repository.findOpen().isPresent())
		{
			throw new CampaignException("Es gibt bereits eine Kampagne, die vorbereitet wird oder läuft.");
		}
		String trimmed = name == null ? "" : name.trim();
		if (trimmed.isEmpty())
		{
			throw new CampaignException("Die Kampagne braucht einen Namen.");
		}
		return repository.create(trimmed, gamemaster.getId(), Common.time());
	}

	public Set<Integer> getParticipantIds(Campaign campaign)
	{
		return repository.findParticipantIds(campaign.getId());
	}

	/**
	 * Replaces the participant list with {@code desiredIds}, writing only the difference. While the
	 * campaign is running the change takes effect on the players immediately.
	 *
	 * @return The users that were added and removed
	 */
	public ParticipantChange updateParticipants(Campaign campaign, Set<Integer> desiredIds, User actor)
	{
		if (!campaign.isPreparing() && !campaign.isRunning())
		{
			throw new CampaignException("Die Kampagne ist beendet.");
		}

		Set<Integer> current = repository.findParticipantIds(campaign.getId());

		Set<Integer> added = new HashSet<>(desiredIds);
		added.removeAll(current);
		Set<Integer> removed = new HashSet<>(current);
		removed.removeAll(desiredIds);

		for (int userId : added)
		{
			User user = em.find(User.class, userId);
			if (user == null)
			{
				throw new CampaignException("Unbekannter Spieler: " + userId);
			}
			if (!isEligible(user))
			{
				throw new CampaignException("Spieler " + userId + " ist im Urlaubsmodus und kann nicht teilnehmen.");
			}
		}

		long now = Common.time();
		repository.removeParticipants(campaign.getId(), removed);
		repository.addParticipants(campaign.getId(), added, actor.getId(), now);

		if (campaign.isRunning())
		{
			for (int userId : added)
			{
				em.find(User.class, userId).setCampaignParticipant(true);
			}
			for (int userId : removed)
			{
				User user = em.find(User.class, userId);
				if (user != null)
				{
					leaveCampaign(user);
				}
			}
		}

		return new ParticipantChange(added, removed);
	}

	/**
	 * Checks whether the prepared campaign can be started.
	 *
	 * @return Problems that block the start; empty if it can start
	 */
	public List<String> findStartBlockers(Campaign campaign)
	{
		List<String> blockers = new ArrayList<>();
		if (!campaign.isPreparing())
		{
			blockers.add("Die Kampagne wird nicht vorbereitet.");
			return blockers;
		}

		Set<Integer> participants = repository.findParticipantIds(campaign.getId());
		if (participants.isEmpty())
		{
			blockers.add("Es sind keine Teilnehmer ausgewählt.");
		}
		for (int userId : participants)
		{
			User user = em.find(User.class, userId);
			if (!isEligible(user))
			{
				blockers.add(user.getPlainname() + " (" + userId + ") ist inzwischen im Urlaubsmodus.");
			}
		}
		return blockers;
	}

	public void start(Campaign campaign)
	{
		List<String> blockers = findStartBlockers(campaign);
		if (!blockers.isEmpty())
		{
			throw new CampaignException(String.join(" ", blockers));
		}

		repository.markStarted(campaign.getId(), Common.time());
		for (int userId : repository.findParticipantIds(campaign.getId()))
		{
			User user = em.find(User.class, userId);
			user.setCampaignParticipant(true);
			user.setTickReady(false);
		}
	}

	public void end(Campaign campaign)
	{
		if (!campaign.isRunning())
		{
			throw new CampaignException("Die Kampagne läuft nicht.");
		}

		repository.markEnded(campaign.getId(), Common.time());
		List<User> flagged = em.createQuery("select u from User u where u.campaign_participant = true", User.class)
				.getResultList();
		for (User user : flagged)
		{
			leaveCampaign(user);
		}
	}

	/**
	 * @return Whether the user may be chosen as participant: not in vacation mode. NPC accounts qualify.
	 */
	public static boolean isEligible(User user)
	{
		return user.isNPC() || user.getVacationCount() == 0;
	}

	private static void leaveCampaign(User user)
	{
		user.setCampaignParticipant(false);
		user.setTickReady(false);
	}

	public static class ParticipantChange
	{
		public final Set<Integer> added;
		public final Set<Integer> removed;

		ParticipantChange(Set<Integer> added, Set<Integer> removed)
		{
			this.added = added;
			this.removed = removed;
		}
	}
}
