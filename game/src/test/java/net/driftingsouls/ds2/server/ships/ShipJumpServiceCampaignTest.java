package net.driftingsouls.ds2.server.ships;

import net.driftingsouls.ds2.server.DBSingleTransactionTest;
import net.driftingsouls.ds2.server.Location;
import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignJumpNodeState;
import net.driftingsouls.ds2.server.campaign.CampaignMapService;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.entities.JumpNode;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.UserFlag;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

/**
 * Jumping through a campaign jump node: only participants, only while it is active, only while they still
 * have their turn.
 */
public class ShipJumpServiceCampaignTest extends DBSingleTransactionTest
{
	private User player;
	private User outsider;
	private StarSystem outside;
	private StarSystem inCampaign;
	private ShipType type;
	private JumpNode node;
	private CampaignMapService map;

	@Before
	public void setUp()
	{
		User gamemaster = persist(new User("gm", "***", 0, "", new Cargo(), "gm@localhost"));
		gamemaster.setFlag(UserFlag.GAMEMASTER);
		player = persist(new User("player", "***", 0, "", new Cargo(), "player@localhost"));
		outsider = persist(new User("outsider", "***", 0, "", new Cargo(), "outsider@localhost"));
		outside = system();
		inCampaign = system();
		type = persist(new ShipType(ShipClasses.AWACS));
		type.setCrew(10);
		type.setEps(100);
		getEM().flush();

		CampaignService campaigns = new CampaignService(getEM());
		Campaign prepared = campaigns.prepare("Test", gamemaster);
		campaigns.updateParticipants(prepared, Set.of(player.getId()), gamemaster);
		map = new CampaignMapService(getEM());
		map.addSystem(prepared, inCampaign.getID());
		node = map.createJumpNode(prepared, new Location(outside.getID(), 5, 5), new Location(inCampaign.getID(), 1, 1), "Riss", false).get(0);
		campaigns.start(prepared);
	}

	@Test
	public void givenAParticipantAndAnActiveNode_jump_shouldTakeTheShipIntoTheCampaignSystem()
	{
		Ship ship = shipAtTheNode(player);

		ShipJumpService.JumpResult result = new ShipJumpService(getEM()).jumpViaJumpNode(ship, node);

		assertTrue(result.getMessages(), result.isSuccessful());
		assertEquals(new Location(inCampaign.getID(), 1, 1), ship.getLocation());
		assertEquals(95, ship.getEnergy());
	}

	@Test
	public void givenANodeNotYetActive_jump_shouldBeRefused()
	{
		map.setJumpNodeState(node.getId(), CampaignJumpNodeState.VISIBLE);
		Ship ship = shipAtTheNode(player);

		ShipJumpService.JumpResult result = new ShipJumpService(getEM()).jumpViaJumpNode(ship, node);

		assertFalse(result.isSuccessful());
		assertTrue(result.getMessages(), result.getMessages().contains("instabil"));
		assertEquals(outside.getID(), ship.getSystem());
	}

	@Test
	public void givenSomeoneNotTakingPart_jump_shouldBeRefused()
	{
		Ship ship = shipAtTheNode(outsider);

		ShipJumpService.JumpResult result = new ShipJumpService(getEM()).jumpViaJumpNode(ship, node);

		assertFalse(result.isSuccessful());
		assertEquals(outside.getID(), ship.getSystem());
	}

	@Test
	public void givenAParticipantWhoFinishedHisTurn_jumpingOut_shouldBeRefused()
	{
		JumpNode back = map.createJumpNode(new CampaignService(getEM()).findOpenCampaign().orElseThrow(),
				new Location(inCampaign.getID(), 1, 1), new Location(outside.getID(), 5, 5), "Rückweg", false).get(0);
		map.setJumpNodeState(back.getId(), CampaignJumpNodeState.ACTIVE);
		Ship ship = persist(new Ship(player, type, inCampaign.getID(), 1, 1));
		ship.setEnergy(100);
		player.setTickReady(true);

		ShipJumpService.JumpResult result = new ShipJumpService(getEM()).jumpViaJumpNode(ship, back);

		assertFalse("Wer fertig ist, wartet auf den Kampagnentick", result.isSuccessful());
		assertEquals(inCampaign.getID(), ship.getSystem());
	}

	private Ship shipAtTheNode(User owner)
	{
		Ship ship = persist(new Ship(owner, type, outside.getID(), 5, 5));
		ship.setEnergy(100);
		getEM().flush();
		return ship;
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
