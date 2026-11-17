package net.driftingsouls.ds2.server.campaign.interaction;

import net.driftingsouls.ds2.server.DBSingleTransactionTest;
import net.driftingsouls.ds2.server.battles.Battle;
import net.driftingsouls.ds2.server.framework.ConfigService;
import net.driftingsouls.ds2.server.services.BattleCreationService;
import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignMapService;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.campaign.GameMasterPresence;
import net.driftingsouls.ds2.server.campaign.GmAction;
import net.driftingsouls.ds2.server.campaign.GmActionLog;
import net.driftingsouls.ds2.server.campaign.NpcTestUsers;
import net.driftingsouls.ds2.server.campaign.dialog.DialogEngine;
import net.driftingsouls.ds2.server.campaign.dialog.DialogRepository;
import net.driftingsouls.ds2.server.campaign.dialog.ObjectType;
import net.driftingsouls.ds2.server.campaign.dialog.Params;
import net.driftingsouls.ds2.server.campaign.dialog.TrustService;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.UserFlag;
import net.driftingsouls.ds2.server.entities.WellKnownUserValue;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipClasses;
import net.driftingsouls.ds2.server.ships.ShipType;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class GmRequestServiceTest extends DBSingleTransactionTest
{
	private User gamemaster;
	private User npc;
	private User player;
	private ShipType type;
	private Ship npcShip;
	private Ship playerShip;
	private DialogService dialogs;
	private GmRequestService requests;
	private int interactionId;
	private int farewell;

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
		type = persist(new ShipType(ShipClasses.AWACS));
		type.setCrew(10);
		npcShip = persist(new Ship(npc, type, system.getID(), 5, 5));
		playerShip = persist(new Ship(player, type, system.getID(), 5, 5));
		getEM().flush();

		CampaignService campaigns = new CampaignService(getEM());
		Campaign prepared = campaigns.prepare("Test", gamemaster);
		campaigns.updateParticipants(prepared, Set.of(player.getId(), npc.getId()), gamemaster);
		new CampaignMapService(getEM()).addSystem(prepared, system.getID());
		campaigns.start(prepared);
		Campaign campaign = campaigns.findOpenCampaign().orElseThrow();

		DialogRepository repository = new DialogRepository();
		int greeting = repository.createNode("Begrüßung", "Admiral", "", "Willkommen an Bord.");
		farewell = repository.createNode("Abschied", "Admiral", "", "Viel Glück.");
		repository.createOption(greeting, 1, "Hallo.", null, null, null, true, false);
		interactionId = new InteractionRepository().save(new Interaction(0, campaign.getId(), "Mit dem Admiral sprechen", ObjectType.SHIP, npcShip.getId(), greeting,
				npc.getId(), true, null, null, Interaction.Audience.ALL, Set.of(), Set.of()));

		dialogs = new DialogService(getEM(), new DialogEngine(), () -> 0);
		requests = new GmRequestService(getEM());
	}

	@Test
	public void givenAConversation_submit_shouldCreateARequestWithTheConversation()
	{
		dialogs.start(player, playerShip, interactionId);

		int id = requests.submit(player, playerShip, "Ich möchte dem Admiral ein Geschenk machen.");
		requests.gamemasterReply(gamemaster, id, "Was möchtest du ihm schenken?");
		requests.playerReply(player, id, "Eine Kiste Wein.");

		List<GmRequest.Message> messages = requests.messages(id);
		assertEquals(3, messages.size());
		assertEquals(GmRequest.Kind.PLAYER, messages.get(0).kind);
		assertEquals(GmRequest.Kind.GAMEMASTER, messages.get(1).kind);
		assertEquals("Eine weitere Anfrage im selben Gespräch setzt die offene fort",
				id, requests.submit(player, playerShip, "Und noch etwas."));
		assertEquals(1, requests.visibleToPlayer(player, playerShip).size());
	}

	@Test
	public void givenTwoGameMasters_submit_shouldTellBoth()
	{
		User second = persist(new User("gm2", "***", 0, "", new Cargo(), "gm2@localhost"));
		second.setFlag(UserFlag.GAMEMASTER);
		User bystander = persist(new User("other", "***", 0, "", new Cargo(), "other@localhost"));
		dialogs.start(player, playerShip, interactionId);

		requests.submit(player, playerShip, "Ich möchte dem Admiral ein Geschenk machen.");
		getEM().flush();

		assertEquals(1, pmsTo(gamemaster, "Kampagne: neue Anfrage%"));
		assertEquals(1, pmsTo(second, "Kampagne: neue Anfrage%"));
		assertEquals(0, pmsTo(bystander, "Kampagne: neue Anfrage%"));
	}

	@Test
	public void givenTwoGameMasters_aPmToTheGameMaster_shouldReachBoth()
	{
		User second = persist(new User("gm2", "***", 0, "", new Cargo(), "gm2@localhost"));
		second.setFlag(UserFlag.GAMEMASTER);
		dialogs.start(player, playerShip, interactionId);
		int id = requests.submit(player, playerShip, "Ich biete meine Hilfe an.");

		requests.runAction(gamemaster, id, "SEND_PM", Params.of("to", "GAMEMASTER", "title", "Hilfe angeboten", "text", "{spieler} hilft."));
		getEM().flush();

		assertEquals(1, pmsTo(gamemaster, "Hilfe angeboten"));
		assertEquals(1, pmsTo(second, "Hilfe angeboten"));
	}

	@Test
	public void givenOneGameMasterOnline_submit_shouldAssignTheRequestToHimAndTellOnlyHim()
	{
		User second = secondGamemaster();
		requests = new GmRequestService(getEM(), new GameMasterPresence(getEM(), id -> id == second.getId()));
		dialogs.start(player, playerShip, interactionId);

		int id = requests.submit(player, playerShip, "Ich möchte dem Admiral ein Geschenk machen.");
		getEM().flush();

		assertEquals(Integer.valueOf(second.getId()), requests.forGamemaster(id).assignedGamemasterId);
		assertEquals(1, pmsTo(second, "Kampagne: neue Anfrage%"));
		assertEquals(0, pmsTo(gamemaster, "Kampagne: neue Anfrage%"));
	}

	@Test
	public void givenTwoGameMastersOnline_submit_shouldLeaveTheRequestFreeAndTellBoth()
	{
		User second = secondGamemaster();
		requests = new GmRequestService(getEM(), new GameMasterPresence(getEM(), id -> true));
		dialogs.start(player, playerShip, interactionId);

		int id = requests.submit(player, playerShip, "Ich möchte dem Admiral ein Geschenk machen.");
		getEM().flush();

		assertNull(requests.forGamemaster(id).assignedGamemasterId);
		assertEquals(1, pmsTo(second, "Kampagne: neue Anfrage%"));
		assertEquals(1, pmsTo(gamemaster, "Kampagne: neue Anfrage%"));
	}

	@Test
	public void givenAFreeRequest_theFirstAnswer_shouldTakeIt()
	{
		dialogs.start(player, playerShip, interactionId);
		int id = requests.submit(player, playerShip, "Ich möchte dem Admiral ein Geschenk machen.");

		requests.gamemasterReply(gamemaster, id, "Was möchtest du ihm schenken?");

		assertEquals(Integer.valueOf(gamemaster.getId()), requests.forGamemaster(id).assignedGamemasterId);
	}

	@Test
	public void givenARequestOfAnotherGameMaster_heShouldAnswerOnlyAfterTakingItOver()
	{
		User second = secondGamemaster();
		dialogs.start(player, playerShip, interactionId);
		int id = requests.submit(player, playerShip, "Ich möchte dem Admiral ein Geschenk machen.");
		requests.take(gamemaster, id, false);

		assertThrows(CampaignException.class, () -> requests.gamemasterReply(second, id, "Ich mach das."));
		assertThrows(CampaignException.class, () -> requests.runAction(second, id, "CHANGE_TRUST", Params.of("delta", "5", "reason", "x")));
		assertThrows("Ohne Bestätigung übernimmt er sie nicht", CampaignException.class, () -> requests.take(second, id, false));

		requests.take(second, id, true);
		requests.gamemasterReply(second, id, "Ich mach das.");
		getEM().flush();

		assertEquals(Integer.valueOf(second.getId()), requests.forGamemaster(id).assignedGamemasterId);
		assertEquals("Der bisherige GM erfährt es", 1, pmsTo(gamemaster, "Kampagne: Anfrage #" + id + " übernommen"));
		assertThrows(CampaignException.class, () -> requests.gamemasterReply(gamemaster, id, "Doch ich."));
	}

	private User secondGamemaster()
	{
		User second = persist(new User("gm2", "***", 0, "", new Cargo(), "gm2@localhost"));
		second.setFlag(UserFlag.GAMEMASTER);
		return second;
	}

	private long pmsTo(User user, String title)
	{
		return getEM().createQuery("select count(*) from PM where empfaenger = :user and title like :title", Long.class)
				.setParameter("user", user)
				.setParameter("title", title)
				.getSingleResult();
	}

	@Test(expected = CampaignException.class)
	public void givenNoConversation_submit_shouldBeRejected()
	{
		requests.submit(player, playerShip, "Hallo?");
	}

	@Test
	public void givenARequest_theGameMasterShouldRunActionsAndContinueTheConversation()
	{
		dialogs.start(player, playerShip, interactionId);
		int id = requests.submit(player, playerShip, "Ich biete meine Hilfe an.");

		requests.runAction(gamemaster, id, "CHANGE_TRUST", Params.of("delta", "5", "reason", "Hilfe angeboten"));
		requests.continueDialog(gamemaster, id, farewell);
		requests.setStatus(gamemaster, id, GmRequest.Status.CLOSED);

		assertEquals(5, new TrustService(getEM()).trust(player.getId(), npc.getId()).getTotal(), 0.001);
		GmActionLog.Entry logged = new GmActionLog().find(null, 10).get(0);
		assertEquals("Die Aktion steht im GM-Protokoll", GmAction.REQUEST_ACTION, logged.action);
		assertEquals(gamemaster.getId(), logged.gamemasterId);
		assertEquals(Integer.valueOf(npc.getId()), logged.npcId);
		assertEquals(Integer.valueOf(player.getId()), logged.userId);
		assertTrue(logged.details.contains("#" + id));
		assertTrue("Das Gespräch steht beim Abschied", dialogs.currentView(player, playerShip).orElseThrow().textHtml.contains("Viel Gl"));
		assertThrows(CampaignException.class, () -> requests.playerReply(player, id, "Danke!"));
	}

	@Test
	public void givenAHandedOverNpcShip_shouldRemoveItsInteractions()
	{
		Ship.MESSAGE.get().setLength(0);
		assertFalse(npcShip.consign(player, false));

		assertTrue(new InteractionRepository().findFor(ObjectType.SHIP, npcShip.getId()).isEmpty());
	}

	@Test
	public void givenSeveralNpcShips_handOverShipInSector_shouldHandOverAllShipsOfTheNpcInTheSector()
	{
		Ship escort = persist(new Ship(npc, type, npcShip.getSystem(), 5, 5));
		Ship elsewhere = persist(new Ship(npc, type, npcShip.getSystem(), 9, 9));
		dialogs.start(player, playerShip, interactionId);
		int id = requests.submit(player, playerShip, "Wir übernehmen eure Flotte.");

		requests.runAction(gamemaster, id, "GIVE_SHIP", Params.of("source", "SECTOR"));

		assertEquals(player, npcShip.getOwner());
		assertEquals(player, escort.getOwner());
		assertEquals("Schiffe in anderen Sektoren bleiben beim NPC", npc, elsewhere.getOwner());
	}

	@Test
	public void givenABattleAgainstTheNpc_capitulate_shouldHandTheShipsToThePlayerAndEndTheBattle()
	{
		for (User user : List.of(player, npc))
		{
			user.setFlag(UserFlag.NOOB, false);
			user.setUserValue(WellKnownUserValue.GAMEPLAY_USER_BATTLE_PM, false);
		}
		Ship escort = persist(new Ship(npc, type, npcShip.getSystem(), 5, 5));
		Battle battle = new BattleCreationService(getEM(), new ConfigService(getEM())).create(npc, npcShip, playerShip, true);
		int battleId = battle.getId();
		int id = requests.submitFromBattle(player, playerShip, battle, "Gebt auf, dann verschonen wir euch.");

		assertEquals(2, requests.capitulate(gamemaster, id));
		getEM().flush();
		getEM().clear();

		assertNull(getEM().find(Battle.class, battleId));
		for (int shipId : List.of(npcShip.getId(), escort.getId()))
		{
			Ship ship = getEM().find(Ship.class, shipId);
			assertEquals(player.getId(), ship.getOwner().getId());
			assertNull("Das Beenden der Schlacht darf nicht überschrieben werden", ship.getBattle());
		}
	}
}
