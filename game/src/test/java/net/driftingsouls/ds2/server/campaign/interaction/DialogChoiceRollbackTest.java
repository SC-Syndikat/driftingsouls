package net.driftingsouls.ds2.server.campaign.interaction;

import net.driftingsouls.ds2.server.DBTest;
import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignMapService;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.campaign.NpcTestUsers;
import net.driftingsouls.ds2.server.campaign.dialog.CampaignVariableService;
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
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * All actions of a dialog choice happen in one transaction: if one fails, what the earlier ones changed is
 * undone, in Hibernate entities as in jOOQ tables, and the abort is logged. Runs with real commits, unlike the
 * single-transaction tests, because the rollback itself is under test.
 */
public class DialogChoiceRollbackTest extends DBTest
{
	private int npcId;
	private int playerId;
	private int shipId;
	private int campaignId;
	private int interactionId;
	private int greeting;
	private int optionId;

	@Test
	public void givenAChoiceWhoseLastActionFails_choose_shouldUndoTheEarlierActionsAndLogTheAbort()
	{
		mitTransaktion(this::setUpCampaignAndDialog);

		String[] message = new String[1];
		mitTransaktion(() -> {
			User player = getEM().find(User.class, playerId);
			Ship ship = getEM().find(Ship.class, shipId);
			int sessionId = new InteractionRepository().findSessionOf(playerId).orElseThrow().id;
			try
			{
				new DialogService(getEM(), new DialogEngine(), () -> 0).choose(player, ship, sessionId, optionId);
				fail("Die letzte Aktion hätte scheitern müssen");
			}
			catch (CampaignException e)
			{
				message[0] = e.getMessage();
			}
		});

		mitTransaktion(() -> {
			User npc = getEM().find(User.class, npcId);
			User player = getEM().find(User.class, playerId);
			assertTrue(message[0], message[0].contains("Forschung"));
			assertEquals("jOOQ: die Variable ist zurückgenommen", 0, new CampaignVariableService().get(campaignId, playerId, "hilft"));
			assertEquals("jOOQ: das Vertrauen ist zurückgenommen", 0,
					new TrustService(getEM()).trust(playerId, npcId).getTotal(), 0.001);
			assertEquals("Hibernate: die Beziehung ist zurückgenommen", User.Relation.NEUTRAL, npc.getRelation(player));
			assertEquals("Das Gespräch steht noch an derselben Stelle", greeting,
					new InteractionRepository().findSessionOf(playerId).orElseThrow().currentNodeId);
			// Besides the start of the dialog, logged when it began
			List<InteractionLogEntry> log = new InteractionRepository().findLog(playerId, interactionId, 10).stream()
					.filter(entry -> !entry.isStart()).collect(java.util.stream.Collectors.toList());
			assertEquals(1, log.size());
			assertFalse(log.get(0).success);
			assertTrue(log.get(0).actionsSummary.startsWith(InteractionRepository.ABORTED_PREFIX));
		});
	}

	private void setUpCampaignAndDialog()
	{
		// NPC first: creating it clears the persistence context
		User npc = NpcTestUsers.create(getEM(), "admiral", -77);
		npcId = npc.getId();
		User gamemaster = persist(new User("gm", "***", 0, "", new Cargo(), "gm@localhost"));
		gamemaster.setFlag(UserFlag.GAMEMASTER);
		User player = persist(new User("player", "***", 0, "", new Cargo(), "player@localhost"));
		playerId = player.getId();
		StarSystem system = persist(new StarSystem());
		system.setWidth(50);
		system.setHeight(50);
		system.setAccess(StarSystem.Access.NORMAL);
		ShipType type = persist(new ShipType(ShipClasses.AWACS));
		type.setCrew(10);
		Ship npcShip = persist(new Ship(npc, type, system.getID(), 5, 5));
		Ship ship = persist(new Ship(player, type, system.getID(), 5, 5));
		shipId = ship.getId();
		getEM().flush();

		CampaignService campaigns = new CampaignService(getEM());
		Campaign prepared = campaigns.prepare("Test", gamemaster);
		campaigns.updateParticipants(prepared, Set.of(playerId, npcId), gamemaster);
		new CampaignMapService(getEM()).addSystem(prepared, system.getID());
		campaigns.start(prepared);
		campaignId = campaigns.findOpenCampaign().orElseThrow().getId();

		DialogRepository dialogs = new DialogRepository();
		greeting = dialogs.createNode("Begrüßung", "Admiral", "", "Willkommen an Bord.");
		int farewell = dialogs.createNode("Abschied", "Admiral", "", "Viel Glück.");
		optionId = dialogs.createOption(greeting, 1, "Ich helfe Ihnen.", farewell, null, null, true, false);
		// jOOQ, Hibernate, jOOQ, then an action that fails
		dialogs.addAction(optionId, 1, DialogModel.Outcome.ALWAYS, "SET_VARIABLE", Params.of("name", "hilft", "scope", "USER", "value", "1"));
		dialogs.addAction(optionId, 2, DialogModel.Outcome.ALWAYS, "SET_RELATION", Params.of("direction", "BOTH", "relation", "ENEMY"));
		dialogs.addAction(optionId, 3, DialogModel.Outcome.ALWAYS, "CHANGE_TRUST", Params.of("delta", "5", "reason", "Hilfe zugesagt"));
		dialogs.addAction(optionId, 4, DialogModel.Outcome.ALWAYS, "UNLOCK_RESEARCH", Params.of("research", "999999"));
		interactionId = new InteractionRepository().save(new Interaction(0, campaignId, "Mit dem Admiral sprechen", ObjectType.SHIP,
				npcShip.getId(), greeting, npcId, true, null, null, Interaction.Audience.ALL, Set.of(), Set.of()));

		new DialogService(getEM(), new DialogEngine(), () -> 0).start(player, ship, interactionId);
	}
}
