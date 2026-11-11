package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.framework.BasicUser;
import net.driftingsouls.ds2.server.framework.ContextMap;

import java.util.Optional;
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
	private final Integer campaignId;
	private final Set<Integer> systems;
	private final Set<Integer> npcParticipants;

	GameMasterRestriction(boolean applies, Integer campaignId, Set<Integer> systems, Set<Integer> npcParticipants)
	{
		this.applies = applies;
		this.campaignId = campaignId;
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
			return new GameMasterRestriction(false, null, Set.of(), Set.of());
		}

		CampaignRepository campaigns = new CampaignRepository();
		return campaigns.findOpen()
				.map(c -> new GameMasterRestriction(true, c.getId(),
						new CampaignMapRepository().findSystemIds(c.getId()),
						campaigns.findParticipantIds(c.getId())))
				.orElse(new GameMasterRestriction(true, null, Set.of(), Set.of()));
	}

	/**
	 * @return Whether the current user's admin work is restricted to campaign objects
	 */
	public boolean applies()
	{
		return applies;
	}

	/**
	 * @return The star systems a restricted game master may work in
	 */
	public Set<Integer> getSystems()
	{
		return systems;
	}

	/**
	 * @return The open campaign a restricted game master works on, if there is one. Empty for an
	 * unrestricted user.
	 */
	public Optional<Integer> getCampaignId()
	{
		return Optional.ofNullable(campaignId);
	}

	public boolean isSystemAllowed(int starSystemId)
	{
		return !applies || systems.contains(starSystemId);
	}

	/**
	 * @return Whether new star systems may be created. A restricted game master needs an open campaign
	 * to put them into.
	 */
	public boolean mayCreateSystems()
	{
		return !applies || campaignId != null;
	}

	/**
	 * @return Whether a jump node between these star systems may be worked on: both ends must lie in the
	 * campaign
	 */
	public boolean isJumpNodeAllowed(int starSystemId, int exitStarSystemId)
	{
		return isSystemAllowed(starSystemId) && isSystemAllowed(exitStarSystemId);
	}

	/**
	 * @throws CampaignException if the star system is outside the campaign
	 */
	public void checkSystem(int starSystemId)
	{
		if (!isSystemAllowed(starSystemId))
		{
			throw new CampaignException("Als GameMaster kannst du nur in Kampagnensystemen arbeiten (System " + starSystemId + ").");
		}
	}

	/**
	 * @throws CampaignException if one end of the jump node is outside the campaign
	 */
	public void checkJumpNode(int starSystemId, int exitStarSystemId)
	{
		checkSystem(starSystemId);
		checkSystem(exitStarSystemId);
	}

	/**
	 * @throws CampaignException if a restricted game master may not give a star system this access level:
	 * home systems spare ships their food and RE, admin systems are hidden from the campaign's players
	 */
	public void checkSystemAccess(StarSystem.Access access)
	{
		if (applies && (access == StarSystem.Access.HOMESYSTEM || access == StarSystem.Access.ADMIN))
		{
			throw new CampaignException("Als GameMaster kannst du einem System nicht die Zugriffsrechte " + access + " geben.");
		}
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
		checkSystem(starSystemId);
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
