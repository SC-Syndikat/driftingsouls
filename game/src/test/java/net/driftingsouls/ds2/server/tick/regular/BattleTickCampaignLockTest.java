package net.driftingsouls.ds2.server.tick.regular;

import net.driftingsouls.ds2.server.DBTest;
import net.driftingsouls.ds2.server.battles.Battle;
import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignMapService;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.campaign.CommittedCampaignTests;
import net.driftingsouls.ds2.server.campaign.NpcTestUsers;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.UserFlag;
import net.driftingsouls.ds2.server.entities.WellKnownUserValue;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.ConfigService;
import net.driftingsouls.ds2.server.services.BattleCreationService;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipClasses;
import net.driftingsouls.ds2.server.ships.ShipType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Battles in a running campaign's star systems are advanced by a regular tick after a short lock (120 s by
 * default) instead of the regular one (1800 s); the campaign tick advances them without any lock. With
 * real battles, unlike {@link BattleTickLockTest}, which checks only the computation.
 */
public class BattleTickCampaignLockTest extends DBTest
{
	/** Committed tests share the database: each run of the set-up needs its own NPC. */
	private static int counter;
	private int campaignSystem;
	private int campaignBattle;
	private int normalBattle;

	@Before
	public void setUpBattles()
	{
		mitTransaktion(() -> {
			// NPC first: creating it clears the persistence context
			User npc = NpcTestUsers.create(getEM(), "admiral", -9302 - counter++);
			User gamemaster = persist(new User("gm", "***", 0, "", new Cargo(), "gm@localhost"));
			gamemaster.setFlag(UserFlag.GAMEMASTER);
			User player = persist(new User("player", "***", 0, "", new Cargo(), "player@localhost"));
			User other = persist(new User("other", "***", 0, "", new Cargo(), "other@localhost"));
			User rival = persist(new User("rival", "***", 0, "", new Cargo(), "rival@localhost"));
			StarSystem inCampaign = system();
			StarSystem normal = system();
			ShipType type = persist(new ShipType(ShipClasses.AWACS));
			type.setCrew(10);
			Ship npcShip = persist(new Ship(npc, type, inCampaign.getID(), 5, 5));
			Ship playerShip = persist(new Ship(player, type, inCampaign.getID(), 5, 5));
			Ship otherShip = persist(new Ship(other, type, normal.getID(), 5, 5));
			Ship rivalShip = persist(new Ship(rival, type, normal.getID(), 5, 5));
			getEM().flush();

			CampaignService campaigns = new CampaignService(getEM());
			Campaign prepared = campaigns.prepare("Test", gamemaster);
			campaigns.updateParticipants(prepared, Set.of(player.getId(), npc.getId()), gamemaster);
			new CampaignMapService(getEM()).addSystem(prepared, inCampaign.getID());
			campaigns.start(prepared);
			campaignSystem = inCampaign.getID();

			for (User user : List.of(npc, player, other, rival))
			{
				user.setFlag(UserFlag.NOOB, false);
				user.setUserValue(WellKnownUserValue.GAMEPLAY_USER_BATTLE_PM, false);
			}
			BattleCreationService battles = new BattleCreationService(getEM(), new ConfigService(getEM()));
			campaignBattle = battles.create(npc, npcShip, playerShip, true).getId();
			normalBattle = battles.create(other, otherShip, rivalShip, true).getId();
		});
	}

	@After
	public void endTheCampaign()
	{
		// Committed: later tests share the database
		mitTransaktion(() -> CommittedCampaignTests.endOpenCampaigns(getEM()));
	}

	@Test
	public void givenBattlesStartedTenMinutesAgo_aRegularTick_shouldAdvanceOnlyTheOneInTheCampaignSystem()
	{
		long tenMinutesAgo = Common.time() - 600;
		startedAt(tenMinutesAgo);

		runBattleTick(Set.of());

		assertTrue("Die Kampagnenschlacht ist nach 120 s dran", lastTurn(campaignBattle) > tenMinutesAgo);
		assertEquals("Die normale Schlacht wartet 1800 s", tenMinutesAgo, lastTurn(normalBattle));
	}

	@Test
	public void givenABattleStartedJustNow_theCampaignTick_shouldAdvanceItWithoutLock()
	{
		long justNow = Common.time() - 1;
		startedAt(justNow);

		runBattleTick(Set.of(campaignSystem));

		assertTrue("Der Kampagnentick rechnet ohne Sperre", lastTurn(campaignBattle) >= justNow);
		assertEquals("Der Kampagnentick lässt Schlachten außerhalb in Ruhe", justNow, lastTurn(normalBattle));
		assertEquals("Die Runde ist neu: Sperrzähler wieder voll", 2, blockCount(campaignBattle));
	}

	private void startedAt(long time)
	{
		mitTransaktion(() -> getEM().createNativeQuery("UPDATE battles SET lastaction = :t, lastturn = :t, blockcount = 2 WHERE id IN (:a, :b)")
				.setParameter("t", time)
				.setParameter("a", campaignBattle)
				.setParameter("b", normalBattle)
				.executeUpdate());
	}

	private void runBattleTick(Set<Integer> systems)
	{
		BattleTick tick = (BattleTick) getContext().getBean(BattleTick.class, null);
		tick.setAffectedSystems(systems);
		try
		{
			tick.execute();
		}
		finally
		{
			tick.setAffectedSystems(Set.of());
		}
	}

	private long lastTurn(int battleId)
	{
		getEM().clear();
		return getEM().find(Battle.class, battleId).getLetzteRunde();
	}

	private int blockCount(int battleId)
	{
		getEM().clear();
		return getEM().find(Battle.class, battleId).getBlockCount();
	}

	private StarSystem system()
	{
		StarSystem system = persist(new StarSystem());
		system.setWidth(50);
		system.setHeight(50);
		system.setAccess(StarSystem.Access.NORMAL);
		return system;
	}
}
