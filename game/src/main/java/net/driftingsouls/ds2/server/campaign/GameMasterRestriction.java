package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.framework.BasicUser;
import net.driftingsouls.ds2.server.framework.ContextMap;

import java.util.Set;

/**
 * Keeps a game master's admin work on campaign objects: star systems of the open campaign, and NPC
 * accounts taking part in it. Admins are not restricted.
 *
 * <p>Every admin permission {@link GameMasterPermissionResolver} grants must be checked here by the
 * plugin it unlocks.
 */
public class GameMasterRestriction
{
	private final boolean applies;
	private final Set<Integer> systems;
	private final Set<Integer> npcParticipants;

	GameMasterRestriction(boolean applies, Set<Integer> systems, Set<Integer> npcParticipants)
	{
		this.applies = applies;
		this.systems = systems;
		this.npcParticipants = npcParticipants;
	}

	/**
	 * The restriction for the user of the current request.
	 */
	public static GameMasterRestriction forActiveUser()
	{
		BasicUser active = ContextMap.getContext().getActiveUser();
		// Under a master login the game master is the user behind it, not the account taken over.
		// isAdmin() already takes the user behind a master login into account.
		boolean gameMaster = active != null
				&& (GameMasterPermissionResolver.isGameMaster(active) || GameMasterPermissionResolver.isGameMaster(active.getAttachedUser()));
		if (!gameMaster || active.isAdmin())
		{
			return new GameMasterRestriction(false, Set.of(), Set.of());
		}

		CampaignRepository campaigns = new CampaignRepository();
		return campaigns.findOpen()
				.map(c -> new GameMasterRestriction(true,
						new CampaignMapRepository().findSystemIds(c.getId()),
						campaigns.findParticipantIds(c.getId())))
				.orElse(new GameMasterRestriction(true, Set.of(), Set.of()));
	}

	/**
	 * @return Whether the current user's admin work is restricted to campaign objects
	 */
	public boolean applies()
	{
		return applies;
	}

	public boolean isSystemAllowed(int starSystemId)
	{
		return !applies || systems.contains(starSystemId);
	}

	/**
	 * @return Whether objects may be given to this owner: an NPC account taking part in the campaign, or
	 * nobody (owner 0)
	 */
	public boolean isOwnerAllowed(int ownerId)
	{
		return !applies || ownerId == 0 || (ownerId < 0 && npcParticipants.contains(ownerId));
	}

	public boolean isAllowed(int starSystemId, int ownerId)
	{
		return isSystemAllowed(starSystemId) && isOwnerAllowed(ownerId);
	}

	/**
	 * @throws CampaignException if the star system or owner is outside the campaign
	 */
	public void check(int starSystemId, int ownerId)
	{
		if (!isSystemAllowed(starSystemId))
		{
			throw new CampaignException("Als GameMaster kannst du nur in Kampagnensystemen arbeiten (System " + starSystemId + ").");
		}
		if (!isOwnerAllowed(ownerId))
		{
			throw new CampaignException("Als GameMaster kannst du Objekte nur NPC-Accounts geben, die an der Kampagne teilnehmen (Besitzer " + ownerId + ").");
		}
	}

	/**
	 * @return The NPC accounts a restricted game master may take over by master login
	 */
	public Set<Integer> getMasterLoginIds()
	{
		Set<Integer> ids = new java.util.TreeSet<>();
		for (int id : npcParticipants)
		{
			if (id < 0)
			{
				ids.add(id);
			}
		}
		return ids;
	}

	/**
	 * @throws CampaignException if the account may not be taken over by master login
	 */
	public void checkMasterLogin(int userId)
	{
		if (applies && !(userId < 0 && npcParticipants.contains(userId)))
		{
			throw new CampaignException("Als GameMaster kannst du dich nur in NPC-Accounts einloggen, die an der Kampagne teilnehmen.");
		}
	}
}
