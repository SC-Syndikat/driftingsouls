package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.UserFlag;
import net.driftingsouls.ds2.server.framework.pipeline.controllers.ValidierungException;

import java.util.Optional;
import java.util.Set;

/**
 * The single place that decides whether a user may act in a campaign star system. Callers in the
 * existing game code only ask this guard, so the rules stay in one place.
 *
 * <ul>
 *     <li>Outside campaign star systems, or while no campaign is open, everything is allowed.</li>
 *     <li>NPC accounts and game masters are never restricted: the game master steers the campaign's
 *     objects through them.</li>
 *     <li>While the campaign is prepared, nobody else may enter its star systems.</li>
 *     <li>While it runs, only participants may act there, and not after they have finished their
 *     round.</li>
 * </ul>
 */
public class CampaignActionGuard
{
	private final CampaignRepository campaigns;
	private final CampaignMapRepository map;

	public CampaignActionGuard()
	{
		this(new CampaignRepository(), new CampaignMapRepository());
	}

	CampaignActionGuard(CampaignRepository campaigns, CampaignMapRepository map)
	{
		this.campaigns = campaigns;
		this.map = map;
	}

	/**
	 * @return Why the action is forbidden, or empty if it is allowed
	 */
	public Optional<String> denial(User user, int starSystemId, CampaignAction action)
	{
		if (user == null || user.isNPC() || user.hasFlag(UserFlag.GAMEMASTER))
		{
			return Optional.empty();
		}

		Optional<Campaign> campaign = campaigns.findOpen();
		if (campaign.isEmpty())
		{
			return Optional.empty();
		}
		Set<Integer> systems = map.findSystemIds(campaign.get().getId());
		return decide(campaign.get(), systems.contains(starSystemId),
				Boolean.TRUE.equals(user.isCampaignParticipant()),
				Boolean.TRUE.equals(user.isTickReady()),
				action);
	}

	/**
	 * Checks a jump from one star system into another, both leaving and arriving, and the state of the
	 * jump node if it is a campaign jump node.
	 *
	 * @param jumpNodeId The jump node used, or {@code null} for jumps without one (Knossos, teleports)
	 * @return Why the jump is forbidden, or empty if it is allowed
	 */
	public Optional<String> jumpDenial(User user, int sourceSystemId, int targetSystemId, Integer jumpNodeId)
	{
		if (user == null || user.isNPC() || user.hasFlag(UserFlag.GAMEMASTER))
		{
			return Optional.empty();
		}
		if (jumpNodeId != null)
		{
			CampaignJumpNodeState state = map.findJumpNodeStates().get(jumpNodeId);
			if (state != null && state != CampaignJumpNodeState.ACTIVE)
			{
				return Optional.of("Der Sprungpunkt ist noch instabil und kann nicht benutzt werden.");
			}
		}
		Optional<String> leaving = denial(user, sourceSystemId, CampaignAction.JUMP);
		if (leaving.isPresent())
		{
			return leaving;
		}
		return denial(user, targetSystemId, CampaignAction.ENTER_SYSTEM);
	}

	/**
	 * @throws ValidierungException if the action is forbidden
	 */
	public void check(User user, int starSystemId, CampaignAction action)
	{
		Optional<String> denial = denial(user, starSystemId, action);
		if (denial.isPresent())
		{
			throw new ValidierungException(denial.get());
		}
	}

	static Optional<String> decide(Campaign campaign, boolean campaignSystem, boolean participant, boolean tickReady, CampaignAction action)
	{
		if (!campaignSystem)
		{
			return Optional.empty();
		}
		if (campaign.isPreparing())
		{
			return action == CampaignAction.ENTER_SYSTEM
					? Optional.of("Dieses Sternensystem wird gerade für eine Kampagne vorbereitet und kann nicht betreten werden.")
					: Optional.empty();
		}
		if (!participant)
		{
			return Optional.of("Nur Teilnehmer der Kampagne dürfen in diesem Sternensystem handeln.");
		}
		if (tickReady)
		{
			return Optional.of("Du hast deine Runde beendet. Setze sie in der Übersicht fort, um in diesem Sternensystem wieder zu handeln.");
		}
		return Optional.empty();
	}
}
