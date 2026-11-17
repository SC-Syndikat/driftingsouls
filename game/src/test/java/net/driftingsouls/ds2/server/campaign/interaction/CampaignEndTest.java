package net.driftingsouls.ds2.server.campaign.interaction;

import net.driftingsouls.ds2.server.DBSingleTransactionTest;
import net.driftingsouls.ds2.server.battles.Battle;
import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignMapService;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.campaign.NpcTestUsers;
import net.driftingsouls.ds2.server.campaign.dialog.DialogEngine;
import net.driftingsouls.ds2.server.campaign.dialog.DialogRepository;
import net.driftingsouls.ds2.server.campaign.dialog.ObjectType;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.comm.PM;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.UserFlag;
import net.driftingsouls.ds2.server.entities.WellKnownUserValue;
import net.driftingsouls.ds2.server.framework.ConfigService;
import net.driftingsouls.ds2.server.services.BattleCreationService;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipClasses;
import net.driftingsouls.ds2.server.ships.ShipType;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Ending a campaign closes what is still open in it and tells the players.
 */
public class CampaignEndTest extends DBSingleTransactionTest
{
	private User gamemaster;
	private User npc;
	private User player;
	private Ship npcShip;
	private Ship playerShip;
	private Campaign campaign;
	private CampaignService campaigns;
	private DialogService dialogs;
	private int interactionId;

	@Before
	public void setUp()
	{
		// NPC first: creating it clears the persistence context
		npc = NpcTestUsers.create(getEM(), "admiral", -77);
		gamemaster = persist(new User("gm", "***", 0, "", new Cargo(), "gm@localhost"));
		gamemaster.setFlag(UserFlag.GAMEMASTER);
		player = new User("player", "***", 0, "", new Cargo(), "player@localhost");
		StarSystem system = persist(new StarSystem());
		system.setWidth(50);
		system.setHeight(50);
		system.setAccess(StarSystem.Access.NORMAL);
		ShipType type = persist(new ShipType(ShipClasses.AWACS));
		type.setCrew(10);
		npcShip = persist(new Ship(npc, type, system.getID(), 5, 5));
		playerShip = persist(new Ship(player, type, system.getID(), 5, 5));
		getEM().flush();

		campaigns = new CampaignService(getEM());
		Campaign prepared = campaigns.prepare("Sturm über Rigel", gamemaster);
		campaigns.updateParticipants(prepared, Set.of(player.getId(), npc.getId()), gamemaster);
		new CampaignMapService(getEM()).addSystem(prepared, system.getID());
		campaigns.start(prepared);
		campaign = campaigns.findOpenCampaign().orElseThrow();

		DialogRepository repository = new DialogRepository();
		int greeting = repository.createNode("Begrüßung", "Admiral", "", "Willkommen an Bord.");
		repository.createOption(greeting, 1, "Hallo.", null, null, null, true, false);
		interactionId = new InteractionRepository().save(new Interaction(0, campaign.getId(), "Mit dem Admiral sprechen", ObjectType.SHIP,
				npcShip.getId(), greeting, npc.getId(), true, null, null, Interaction.Audience.ALL, Set.of(), Set.of()));

		dialogs = new DialogService(getEM(), new DialogEngine(), () -> 0);
	}

	@Test
	public void givenAConversationARequestAndABattle_end_shouldCloseThemAll()
	{
		dialogs.start(player, playerShip, interactionId);
		int requestId = new GmRequestService(getEM()).submit(player, playerShip, "Darf ich an Bord kommen?");
		int battleId = startBattle().getId();

		CampaignService.EndConsequences before = campaigns.findEndConsequences(campaign);
		CampaignService.EndConsequences done = campaigns.end(campaign, gamemaster);
		getEM().flush();
		getEM().clear();

		assertEquals(1, before.conversations);
		assertEquals(1, before.requests);
		assertEquals(1, before.battles);
		assertEquals(before, done);
		assertTrue(new InteractionRepository().findSessionOf(player.getId()).isEmpty());
		assertEquals(GmRequest.Status.CLOSED, new GmRequestRepository().find(requestId).orElseThrow().status);
		assertNull(getEM().find(Battle.class, battleId));
	}

	@Test
	public void givenNothingOpen_findEndConsequences_shouldFindNothing()
	{
		CampaignService.EndConsequences consequences = campaigns.findEndConsequences(campaign);

		assertEquals(0, consequences.conversations);
		assertEquals(0, consequences.requests);
		assertEquals(0, consequences.battles);
	}

	@Test
	public void end_shouldTellThePlayersWhereTheirShipsAreButNotTheNpcs()
	{
		campaigns.end(campaign, gamemaster);
		getEM().flush();

		List<PM> toPlayer = pmsTo(player);
		assertEquals(1, toPlayer.size());
		assertTrue(toPlayer.get(0).getTitle().contains("Sturm über Rigel"));
		assertTrue("Die PM nennt die Schiffe, die noch im Kampagnensystem stehen",
				toPlayer.get(0).getInhalt().contains(playerShip.getLocation().displayCoordinates(false)));
		assertTrue(pmsTo(npc).isEmpty());
	}

	private Battle startBattle()
	{
		for (User user : List.of(player, npc))
		{
			user.setFlag(UserFlag.NOOB, false);
			user.setUserValue(WellKnownUserValue.GAMEPLAY_USER_BATTLE_PM, false);
		}
		return new BattleCreationService(getEM(), new ConfigService(getEM())).create(npc, npcShip, playerShip, true);
	}

	private List<PM> pmsTo(User user)
	{
		return getEM().createQuery("from PM where empfaenger = :user and title like 'Kampagne%beendet'", PM.class)
				.setParameter("user", user)
				.getResultList();
	}
}
