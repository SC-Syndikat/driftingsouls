package net.driftingsouls.ds2.server.campaign.interaction;

import net.driftingsouls.ds2.server.DBSingleTransactionTest;
import net.driftingsouls.ds2.server.Location;
import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignActionGuard;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignMapService;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.campaign.dialog.CampaignVariableService;
import net.driftingsouls.ds2.server.campaign.dialog.Comparison;
import net.driftingsouls.ds2.server.campaign.dialog.DialogEngine;
import net.driftingsouls.ds2.server.campaign.dialog.DialogModel;
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

public class DialogServiceTest extends DBSingleTransactionTest
{
	private User npc;
	private User player;
	private User outsider;
	private Ship playerShip;
	private Ship outsiderShip;
	private Campaign campaign;
	private DialogService service;
	private int greeting;
	private int accept;
	private int secret;
	private int farewell;
	private int goodbye;

	@Before
	public void setUp()
	{
		User gamemaster = new User("gm", "***", 0, "", new Cargo(), "gm@localhost");
		gamemaster.setFlag(UserFlag.GAMEMASTER);
		npc = new User("admiral", "***", 0, "", new Cargo(), "npc@localhost");
		player = new User("player", "***", 0, "", new Cargo(), "player@localhost");
		outsider = new User("outsider", "***", 0, "", new Cargo(), "outsider@localhost");
		StarSystem system = persist(new StarSystem());
		system.setWidth(50);
		system.setHeight(50);
		system.setAccess(StarSystem.Access.NORMAL);
		ShipType type = persist(new ShipType(ShipClasses.AWACS));
		type.setCrew(10);
		Ship npcShip = persist(new Ship(npc, type, system.getID(), 5, 5));
		playerShip = persist(new Ship(player, type, system.getID(), 5, 5));
		outsiderShip = persist(new Ship(outsider, type, system.getID(), 5, 5));
		getEM().flush();

		CampaignService campaigns = new CampaignService(getEM());
		Campaign prepared = campaigns.prepare("Test", gamemaster);
		campaigns.updateParticipants(prepared, Set.of(player.getId()), gamemaster);
		new CampaignMapService(getEM()).addSystem(prepared, system.getID());
		campaigns.start(prepared);
		campaign = campaigns.findOpenCampaign().orElseThrow();

		DialogRepository dialogs = new DialogRepository();
		greeting = dialogs.createNode("Begrüßung", "Admiral", "", "Willkommen an Bord.");
		farewell = dialogs.createNode("Abschied", "Admiral", "", "Viel Glück.");
		accept = dialogs.createOption(greeting, 1, "Ich helfe Ihnen.", farewell, null, null, true, false);
		dialogs.addAction(accept, 1, DialogModel.Outcome.ALWAYS, "SET_VARIABLE", Params.of("name", "hilft", "scope", "USER", "value", "1"));
		dialogs.addAction(accept, 2, DialogModel.Outcome.ALWAYS, "CHANGE_TRUST", Params.of("delta", "5", "reason", "Hilfe zugesagt"));
		secret = dialogs.createOption(greeting, 2, "Das Geheimnis?", null, null, null, true, false);
		dialogs.addCondition(secret, "VARIABLE", Params.of("name", "hilft", "scope", "USER"), Comparison.GE, 1);
		goodbye = dialogs.createOption(farewell, 1, "Auf Wiedersehen.", null, null, null, true, false);

		new InteractionRepository().save(new Interaction(0, "Mit dem Admiral sprechen", ObjectType.SHIP, npcShip.getId(), greeting, npc.getId(),
				true, 1, null, Interaction.Audience.ALL, Set.of(), Set.of()));

		service = new DialogService(getEM(), new DialogEngine(), () -> 0);
	}

	@Test
	public void gegebenEinTeilnehmerImSektor_findOffers_sollteDieInteraktionAnbieten()
	{
		List<DialogService.Offer> offers = service.findOffers(player, playerShip);

		assertEquals(1, offers.size());
		assertEquals(ObjectType.SHIP, offers.get(0).type);
		assertEquals("Mit dem Admiral sprechen", offers.get(0).interactions.get(0).label);
	}

	@Test
	public void gegebenEinNichtteilnehmer_findOffers_sollteNichtsAnbieten()
	{
		assertTrue(service.findOffers(outsider, outsiderShip).isEmpty());
	}

	@Test
	public void gegebenEinGespraech_sollteBedingungenAktionenUndLimitsBeachten()
	{
		int interactionId = service.findOffers(player, playerShip).get(0).interactions.get(0).id;
		service.start(player, playerShip, interactionId);

		DialogService.View first = service.currentView(player, playerShip).orElseThrow();
		assertEquals("Admiral", first.speaker);
		assertEquals("Die Bedingung der zweiten Antwort ist noch nicht erfüllt", 1, first.options.size());
		assertEquals(accept, first.options.get(0).id);

		service.choose(player, playerShip, first.sessionId, accept);

		assertEquals(1, new CampaignVariableService().get(campaign.getId(), player.getId(), "hilft"));
		assertEquals(5, new TrustService(getEM()).trust(player.getId(), npc.getId()).getTotal(), 0.001);

		DialogService.View second = service.currentView(player, playerShip).orElseThrow();
		assertEquals(goodbye, second.options.get(0).id);
		service.choose(player, playerShip, second.sessionId, goodbye);

		assertTrue(service.currentView(player, playerShip).isEmpty());
		assertTrue("Die Interaktion darf nur einmal genutzt werden", service.findOffers(player, playerShip).isEmpty());
	}

	@Test
	public void gegebenEineNurEinmalWaehlbareAntwort_choose_sollteSieDanachNichtMehrAnbieten()
	{
		DialogRepository dialogs = new DialogRepository();
		int compliment = dialogs.createOption(greeting, 3, "Schönes Schiff!", greeting, null, null, true, true);
		dialogs.addAction(compliment, 1, DialogModel.Outcome.ALWAYS, "CHANGE_TRUST", Params.of("delta", "1", "reason", "Kompliment"));
		int interactionId = service.findOffers(player, playerShip).get(0).interactions.get(0).id;
		service.start(player, playerShip, interactionId);

		DialogService.View first = service.currentView(player, playerShip).orElseThrow();
		assertTrue(first.options.stream().anyMatch(o -> o.id == compliment));
		assertEquals("Der Spieler erfährt, was die Aktionen bewirkt haben",
				List.of("Das Vertrauen von " + npc.getPlainname() + " in dich wurde gestärkt."), service.choose(player, playerShip, first.sessionId, compliment));

		DialogService.View again = service.currentView(player, playerShip).orElseThrow();
		assertTrue("Die Antwort ist bereits gewählt", again.options.stream().noneMatch(o -> o.id == compliment));
		assertThrows(CampaignException.class, () -> service.choose(player, playerShip, again.sessionId, compliment));
		assertEquals(1, new TrustService(getEM()).trust(player.getId(), npc.getId()).getTotal(), 0.001);
	}

	@Test
	public void gegebenEinLaufendesGespraech_sollteDieSchiffeImSektorBinden()
	{
		int interactionId = service.findOffers(player, playerShip).get(0).interactions.get(0).id;
		CampaignActionGuard guard = new CampaignActionGuard();
		assertTrue(guard.dialogDenial(player, playerShip.getLocation()).isEmpty());

		service.start(player, playerShip, interactionId);
		assertTrue("Im Gesprächssektor darf nichts abfliegen", guard.dialogDenial(player, playerShip.getLocation()).isPresent());
		assertTrue("Andere Sektoren sind frei", guard.dialogDenial(player, new Location(playerShip.getSystem(), 40, 40)).isEmpty());

		service.quit(player, service.currentView(player, playerShip).orElseThrow().sessionId);
		assertTrue(guard.dialogDenial(player, playerShip.getLocation()).isEmpty());
	}

	@Test
	public void gegebenEinPflichtknoten_quit_sollteAbgelehntWerden()
	{
		new DialogRepository().setNodeForced(greeting, true);
		int interactionId = service.findOffers(player, playerShip).get(0).interactions.get(0).id;
		service.start(player, playerShip, interactionId);
		DialogService.View view = service.currentView(player, playerShip).orElseThrow();

		assertFalse(view.canQuit);
		assertThrows(CampaignException.class, () -> service.quit(player, view.sessionId));
		assertTrue(service.currentView(player, playerShip).isPresent());
	}

	@Test
	public void gegebenEineAntwortEinesAnderenKnotens_choose_sollteAbgelehntWerden()
	{
		int interactionId = service.findOffers(player, playerShip).get(0).interactions.get(0).id;
		service.start(player, playerShip, interactionId);
		DialogService.View first = service.currentView(player, playerShip).orElseThrow();

		assertThrows(CampaignException.class, () -> service.choose(player, playerShip, first.sessionId, goodbye));
	}

	@Test
	public void gegebenEinFertigerTeilnehmer_findOffers_sollteNichtsAnbieten()
	{
		player.setTickReady(true);

		assertTrue(service.findOffers(player, playerShip).isEmpty());
		assertTrue(secret > 0);
	}
}
