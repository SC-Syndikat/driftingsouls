package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.battles.Battle;
import net.driftingsouls.ds2.server.comm.PM;
import net.driftingsouls.ds2.server.entities.User;

import javax.persistence.EntityManager;
import java.util.Optional;

/**
 * Battles in campaign star systems as the game master handles them.
 */
public final class CampaignBattles
{
	private CampaignBattles()
	{
		// static only
	}

	/**
	 * Ends the battle as a draw, as admins can with "battle end", and tells both commanders.
	 *
	 * @return Where the battle was
	 * @throws CampaignException if the battle is not in a campaign star system
	 */
	public static String end(EntityManager em, Battle battle, User gamemaster)
	{
		Optional<Campaign> campaign = new CampaignRepository().findOpen();
		if (battle == null || campaign.isEmpty() || !new CampaignMapRepository().findSystemIds(campaign.get().getId()).contains(battle.getSystem()))
		{
			throw new CampaignException("Du kannst nur Schlachten in Kampagnensystemen beenden.");
		}
		String location = battle.getLocation().displayCoordinates(false);
		for (int side = 0; side < 2; side++)
		{
			User commander = battle.getCommander(side);
			if (commander != null)
			{
				PM.send(gamemaster, commander.getId(), "Schlacht beendet", "Die Schlacht bei " + location + " wurde durch den GameMaster beendet.", em);
			}
		}
		battle.load(battle.getCommander(0), null, null, 0, em);
		battle.endBattle(0, 0);
		return location;
	}
}
