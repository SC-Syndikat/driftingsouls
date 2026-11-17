package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.battles.Battle;
import net.driftingsouls.ds2.server.campaign.dialog.CampaignVariableService;
import net.driftingsouls.ds2.server.campaign.interaction.GmRequest;
import net.driftingsouls.ds2.server.campaign.interaction.GmRequestRepository;
import net.driftingsouls.ds2.server.campaign.interaction.InteractionRepository;
import net.driftingsouls.ds2.server.comm.PM;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.UserFlag;
import net.driftingsouls.ds2.server.framework.Common;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

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

	public Optional<Campaign> findCampaign(int campaignId)
	{
		return repository.find(campaignId);
	}

	/**
	 * @return The ended campaigns, newest first
	 */
	public List<Campaign> findEndedCampaigns()
	{
		return repository.findEnded();
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
		CampaignMapService map = new CampaignMapService(em);
		map.setAllJumpNodeStates(CampaignJumpNodeState.ACTIVE);

		String systems = map.getSystemIds(campaign).stream().sorted()
				.map(id -> {
					StarSystem system = em.find(StarSystem.class, id);
					return (system == null ? "?" : system.getName()) + " (" + id + ")";
				})
				.collect(Collectors.joining(", "));
		tellPlayers(campaign, "Kampagne „" + campaign.getName() + "“ gestartet",
				"Die Kampagne „" + campaign.getName() + "“ hat begonnen. Die Sprungpunkte in die Kampagnensysteme sind jetzt offen"
						+ (systems.isEmpty() ? "." : ": " + systems + ".")
						+ "\n\nWenn du mit deiner Runde fertig bist, melde dich in der Übersicht als bereit.");
	}

	/**
	 * Counts what ending the campaign closes.
	 */
	public EndConsequences findEndConsequences(Campaign campaign)
	{
		return new EndConsequences(new InteractionRepository().countSessions(),
				new GmRequestRepository().findForCampaign(campaign.getId(), true).size(),
				findBattles(campaign).size(), countNpcShips(campaign));
	}

	/**
	 * Ends the campaign. Dialogs in progress end, open requests to the game master are closed and battles in
	 * the campaign's star systems end as a draw; the players are told where their ships are left.
	 *
	 * @param actor Who ends it; the battle commanders hear from him
	 * @return What was closed
	 */
	public EndConsequences end(Campaign campaign, User actor)
	{
		if (!campaign.isRunning())
		{
			throw new CampaignException("Die Kampagne läuft nicht.");
		}

		// Spawned NPC ships stay; the game master clears them up himself
		int npcShips = countNpcShips(campaign);
		// Battles first: ending one needs the campaign still open
		List<Battle> battles = findBattles(campaign);
		for (Battle battle : battles)
		{
			CampaignBattles.end(em, battle, actor);
		}
		GmRequestRepository requests = new GmRequestRepository();
		List<GmRequest> openRequests = requests.findForCampaign(campaign.getId(), true);
		for (GmRequest request : openRequests)
		{
			requests.addMessage(request.id, actor.getId(), GmRequest.Kind.GAMEMASTER, "Die Kampagne ist beendet.");
			requests.setStatus(request.id, GmRequest.Status.CLOSED);
		}
		int conversations = new InteractionRepository().endAllSessions();

		tellPlayersAboutTheEnd(campaign);

		EndConsequences closed = new EndConsequences(conversations, openRequests.size(), battles.size(), npcShips);
		// While the campaign is still open, so that the entry belongs to it
		new GmActionLog().record(GmAction.CAMPAIGN_END, actor.getId(), null, null, campaign.getName() + ": " + closed.conversations
				+ " Gespräch(e), " + closed.requests + " Anfrage(n), " + closed.battles + " Schlacht(en) abgeschlossen");

		repository.markEnded(campaign.getId(), Common.time());
		new CampaignMapService(em).setAllJumpNodeStates(CampaignJumpNodeState.PREPARED);
		List<User> flagged = em.createQuery("select u from User u where u.campaign_participant = true", User.class)
				.getResultList();
		for (User user : flagged)
		{
			leaveCampaign(user);
		}
		return closed;
	}

	/**
	 * @return The ships of the participating NPCs in the campaign's star systems, which stay after its end
	 */
	private int countNpcShips(Campaign campaign)
	{
		Set<Integer> systems = new CampaignMapService(em).getSystemIds(campaign);
		Set<Integer> npcs = repository.findParticipantIds(campaign.getId()).stream().filter(id -> id < 0)
				.collect(Collectors.toSet());
		if (systems.isEmpty() || npcs.isEmpty())
		{
			return 0;
		}
		return em.createQuery("select count(*) from Ship where owner.id in (:npcs) and system in (:systems) and id > 0", Long.class)
				.setParameter("npcs", npcs)
				.setParameter("systems", systems)
				.getSingleResult().intValue();
	}

	private List<Battle> findBattles(Campaign campaign)
	{
		Set<Integer> systems = new CampaignMapService(em).getSystemIds(campaign);
		if (systems.isEmpty())
		{
			return List.of();
		}
		return em.createQuery("from Battle where system in (:systems) order by id", Battle.class)
				.setParameter("systems", systems)
				.getResultList();
	}

	private void tellPlayersAboutTheEnd(Campaign campaign)
	{
		List<CampaignObject> left = new CampaignMapService(em).findPlayerObjects(campaign);
		for (int userId : playerIds(campaign))
		{
			List<String> mine = left.stream()
					.filter(o -> o.ownerId == userId && o.type == CampaignObject.Type.SHIP)
					.map(o -> o.name + " (" + o.id + ") bei " + o.getLocation())
					.collect(Collectors.toList());
			String text = "Die Kampagne „" + campaign.getName() + "“ ist beendet. Danke fürs Mitspielen!"
					+ (mine.isEmpty() ? "" : "\n\nDiese Schiffe stehen noch in den Kampagnensystemen:\n" + String.join("\n", mine)
					+ "\n\nDie Sprungpunkte sind wieder geschlossen; der GameMaster versetzt die Schiffe oder lässt sie dort.");
			send(campaign, userId, "Kampagne „" + campaign.getName() + "“ beendet", text);
		}
	}

	/**
	 * Counts a campaign tick of the running campaign and tells the players that a new round begins.
	 */
	public void recordCampaignTick(Campaign campaign)
	{
		repository.incrementTickCount(campaign.getId());
		int tick = campaign.getTickCount() + 1;
		tellPlayers(campaign, "Kampagne „" + campaign.getName() + "“: Kampagnentick " + tick,
				"Kampagnentick " + tick + " der Kampagne „" + campaign.getName() + "“ ist gelaufen. Du bist wieder am Zug.");
	}

	/**
	 * Prepares a new campaign from an ended one: its star systems, its interactions and, if wanted, its
	 * participants. Campaign jump nodes are shared by all campaigns and need no copy.
	 */
	public Campaign copy(Campaign source, String name, User gamemaster, boolean withParticipants, boolean withVariables)
	{
		if (source.isPreparing() || source.isRunning())
		{
			throw new CampaignException("Kopieren lässt sich nur eine beendete Kampagne.");
		}
		Campaign copy = prepare(name, gamemaster);
		long now = Common.time();
		CampaignMapRepository map = new CampaignMapRepository();
		for (int systemId : map.findSystemIds(source.getId()))
		{
			map.addSystem(copy.getId(), systemId, now);
		}
		if (withParticipants)
		{
			Set<Integer> eligible = repository.findParticipantIds(source.getId()).stream()
					.map(id -> em.find(User.class, id))
					.filter(Objects::nonNull)
					.filter(CampaignService::isEligible)
					.map(User::getId)
					.collect(Collectors.toSet());
			repository.addParticipants(copy.getId(), eligible, gamemaster.getId(), now);
		}
		new InteractionRepository().copyToCampaign(source.getId(), copy.getId());
		if (withVariables)
		{
			new CampaignVariableService().copyToCampaign(source.getId(), copy.getId());
		}
		return copy;
	}

	private void tellPlayers(Campaign campaign, String title, String text)
	{
		for (int userId : playerIds(campaign))
		{
			send(campaign, userId, title, text);
		}
	}

	/**
	 * @return The participating players; NPC accounts get no messages
	 */
	private Set<Integer> playerIds(Campaign campaign)
	{
		return repository.findParticipantIds(campaign.getId()).stream().filter(id -> id > 0)
				.collect(Collectors.toCollection(TreeSet::new));
	}

	private void send(Campaign campaign, int userId, String title, String text)
	{
		// From the system account like other game messages; a database without one gets them from the game master
		User sender = em.find(User.class, -1);
		if (sender == null && campaign.getGamemasterId() != null)
		{
			sender = em.find(User.class, campaign.getGamemasterId());
		}
		if (sender != null)
		{
			PM.send(sender, userId, title, text, em);
		}
	}

	/**
	 * @return Who gets the game master's messages of the campaign: every account with the game master flag, as
	 * all of them work on the open campaign, and the one who prepared it, e.g. an admin without the flag
	 */
	public Set<Integer> gamemasterIds(Campaign campaign)
	{
		Set<Integer> ids = em.createQuery("from User where flags like :flag", User.class)
				.setParameter("flag", "%" + UserFlag.GAMEMASTER.getFlag() + "%")
				.getResultList().stream()
				.filter(user -> user.hasFlag(UserFlag.GAMEMASTER))
				.map(User::getId)
				.collect(Collectors.toCollection(TreeSet::new));
		if (campaign.getGamemasterId() != null)
		{
			ids.add(campaign.getGamemasterId());
		}
		return ids;
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

	/**
	 * What ending a campaign closes, or closed.
	 */
	public static final class EndConsequences
	{
		/** Dialogs in progress. */
		public final int conversations;
		/** Open requests to the game master. */
		public final int requests;
		/** Battles in the campaign's star systems. */
		public final int battles;
		/** Ships of the participating NPCs in the campaign's star systems; they are not closed but stay. */
		public final int npcShips;

		EndConsequences(int conversations, int requests, int battles, int npcShips)
		{
			this.conversations = conversations;
			this.requests = requests;
			this.battles = battles;
			this.npcShips = npcShips;
		}

		/**
		 * @return Whether nothing is left to close; NPC ships staying behind do not count
		 */
		public boolean isEmpty()
		{
			return conversations == 0 && requests == 0 && battles == 0;
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof EndConsequences))
			{
				return false;
			}
			EndConsequences other = (EndConsequences) o;
			return conversations == other.conversations && requests == other.requests && battles == other.battles
					&& npcShips == other.npcShips;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(conversations, requests, battles, npcShips);
		}
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
