package net.driftingsouls.ds2.server.campaign.interaction;

import net.driftingsouls.ds2.server.DBSingleTransactionTest;
import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignMapService;
import net.driftingsouls.ds2.server.campaign.CampaignService;
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
		ShipType type = persist(new ShipType(ShipClasses.AWACS));
		type.setCrew(10);
		npcShip = persist(new Ship(npc, type, system.getID(), 5, 5));
		playerShip = persist(new Ship(player, type, system.getID(), 5, 5));
		getEM().flush();

		CampaignService campaigns = new CampaignService(getEM());
		Campaign prepared = campaigns.prepare("Test", gamemaster);
		campaigns.updateParticipants(prepared, Set.of(player.getId()), gamemaster);
		new CampaignMapService(getEM()).addSystem(prepared, system.getID());
		campaigns.start(prepared);

		DialogRepository repository = new DialogRepository();
		int greeting = repository.createNode("Begrüßung", "Admiral", "", "Willkommen an Bord.");
		farewell = repository.createNode("Abschied", "Admiral", "", "Viel Glück.");
		repository.createOption(greeting, 1, "Hallo.", null, null, null, true, false);
		interactionId = new InteractionRepository().save(new Interaction(0, "Mit dem Admiral sprechen", ObjectType.SHIP, npcShip.getId(), greeting,
				npc.getId(), true, null, null, Interaction.Audience.ALL, Set.of(), Set.of()));

		dialogs = new DialogService(getEM(), new DialogEngine(), () -> 0);
		requests = new GmRequestService(getEM());
	}

	@Test
	public void gegebenEinGespraech_submit_sollteEineAnfrageMitUnterhaltungAnlegen()
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

	@Test(expected = CampaignException.class)
	public void gegebenKeinGespraech_submit_sollteAbgelehntWerden()
	{
		requests.submit(player, playerShip, "Hallo?");
	}

	@Test
	public void gegebenEineAnfrage_sollteDerGameMasterAktionenAusfuehrenUndDasGespraechFortsetzen()
	{
		dialogs.start(player, playerShip, interactionId);
		int id = requests.submit(player, playerShip, "Ich biete meine Hilfe an.");

		requests.runAction(gamemaster, id, "CHANGE_TRUST", Params.of("delta", "5", "reason", "Hilfe angeboten"));
		requests.continueDialog(gamemaster, id, farewell);
		requests.setStatus(gamemaster, id, GmRequest.Status.CLOSED);

		assertEquals(5, new TrustService(getEM()).trust(player.getId(), npc.getId()).getTotal(), 0.001);
		assertTrue("Das Gespräch steht beim Abschied", dialogs.currentView(player, playerShip).orElseThrow().textHtml.contains("Viel Gl"));
		assertThrows(CampaignException.class, () -> requests.playerReply(player, id, "Danke!"));
	}

	@Test
	public void gegebenEinUebergebenesNpcSchiff_sollteSeineInteraktionenEntfernen()
	{
		Ship.MESSAGE.get().setLength(0);
		assertFalse(npcShip.consign(player, false));

		assertTrue(new InteractionRepository().findFor(ObjectType.SHIP, npcShip.getId()).isEmpty());
	}
}
