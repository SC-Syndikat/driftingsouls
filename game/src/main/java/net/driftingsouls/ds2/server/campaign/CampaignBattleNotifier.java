package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.battles.Battle;
import net.driftingsouls.ds2.server.battles.BattleShip;
import net.driftingsouls.ds2.server.comm.PM;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.WellKnownUserValue;

import javax.persistence.EntityManager;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Lets campaign participants who had finished their round react when they are attacked: their round is
 * resumed and they are told why.
 */
public class CampaignBattleNotifier
{
	static final String NOTICE = "Du wurdest angegriffen, deine Runde wurde fortgesetzt. Du kannst jetzt reagieren.";

	private final EntityManager em;
	private final CampaignRepository campaigns;
	private final CampaignMapRepository map;

	public CampaignBattleNotifier(EntityManager em)
	{
		this.em = em;
		this.campaigns = new CampaignRepository();
		this.map = new CampaignMapRepository();
	}

	/**
	 * @param defenders The users on the attacked side
	 * @param starSystemId Where the battle takes place
	 * @return The users whose round was resumed
	 */
	public Set<User> onAttacked(Collection<User> defenders, int starSystemId)
	{
		Set<User> resumed = new LinkedHashSet<>();
		Optional<Campaign> campaign = campaigns.findOpen();
		if (campaign.isEmpty() || !campaign.get().isRunning() || !map.findSystemIds(campaign.get().getId()).contains(starSystemId))
		{
			return resumed;
		}

		User nobody = em.find(User.class, -1);
		for (User defender : defenders)
		{
			if (Boolean.TRUE.equals(defender.isCampaignParticipant()) && Boolean.TRUE.equals(defender.isTickReady()))
			{
				defender.setTickReady(false);
				defender.setUserValue(WellKnownUserValue.CAMPAIGN_ATTACK_NOTICE, NOTICE);
				if (nobody != null)
				{
					PM.send(nobody, defender.getId(), "Kampagne: Du wurdest angegriffen", NOTICE, em);
				}
				resumed.add(defender);
			}
		}
		return resumed;
	}

	/**
	 * For a user joining an existing battle: everyone on the other side counts as attacked.
	 */
	public Set<User> onJoined(Battle battle)
	{
		Set<User> defenders = new LinkedHashSet<>();
		for (BattleShip ship : battle.getEnemyShips())
		{
			defenders.add(ship.getOwner());
		}
		return onAttacked(defenders, battle.getSystem());
	}
}
