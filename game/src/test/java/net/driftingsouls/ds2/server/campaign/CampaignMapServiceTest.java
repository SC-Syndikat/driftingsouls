package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.DBSingleTransactionTest;
import net.driftingsouls.ds2.server.Location;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.entities.JumpNode;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.UserFlag;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class CampaignMapServiceTest extends DBSingleTransactionTest
{
	private CampaignService campaigns;
	private CampaignMapService map;
	private CampaignActionGuard guard;
	private User gamemaster;
	private User player;
	private User outsider;
	private StarSystem normalSystem;
	private StarSystem campaignSystem;
	private Campaign campaign;

	@Before
	public void setUp()
	{
		gamemaster = new User("gm", "***", 0, "", new Cargo(), "gm@localhost");
		gamemaster.setFlag(UserFlag.GAMEMASTER);
		player = new User("player", "***", 0, "", new Cargo(), "player@localhost");
		outsider = new User("outsider", "***", 0, "", new Cargo(), "outsider@localhost");
		normalSystem = system();
		campaignSystem = system();
		getEM().flush();

		campaigns = new CampaignService(getEM());
		map = new CampaignMapService(getEM());
		guard = new CampaignActionGuard();

		campaign = campaigns.prepare("Test", gamemaster);
		campaigns.updateParticipants(campaign, Set.of(player.getId()), gamemaster);
		map.addSystem(campaign, campaignSystem.getID());
	}

	@Test
	public void givenANewCampaignJumpNode_createJumpNode_shouldHideAndPrepareIt()
	{
		List<JumpNode> nodes = map.createJumpNode(campaign, new Location(normalSystem.getID(), 5, 5),
				new Location(campaignSystem.getID(), 10, 10), "Zugang", true);

		assertEquals(2, nodes.size());
		for (JumpNode node : nodes)
		{
			assertTrue(node.isHidden());
			assertEquals(CampaignJumpNodeState.PREPARED, map.getJumpNodeState(node.getId()));
		}
	}

	@Test(expected = CampaignException.class)
	public void givenNoCampaignSystem_createJumpNode_shouldBeRejected()
	{
		map.createJumpNode(campaign, new Location(normalSystem.getID(), 5, 5), new Location(normalSystem.getID(), 10, 10), "Zugang", false);
	}

	@Test
	public void givenACampaignInPreparation_jumpDenial_shouldOnlyLetNpcAndGmIn()
	{
		JumpNode node = entrance();
		map.setJumpNodeState(node.getId(), CampaignJumpNodeState.ACTIVE);

		assertTrue(guard.jumpDenial(player, normalSystem.getID(), campaignSystem.getID(), node.getId()).isPresent());
		assertFalse(guard.jumpDenial(gamemaster, normalSystem.getID(), campaignSystem.getID(), node.getId()).isPresent());
	}

	@Test
	public void givenARunningCampaign_jumpDenial_shouldOnlyLetParticipantsIn()
	{
		JumpNode node = entrance();
		start();

		assertFalse(guard.jumpDenial(player, normalSystem.getID(), campaignSystem.getID(), node.getId()).isPresent());
		assertTrue(guard.jumpDenial(outsider, normalSystem.getID(), campaignSystem.getID(), node.getId()).isPresent());
	}

	@Test
	public void givenAFinishedParticipant_jumpDenial_shouldForbidTheJump()
	{
		JumpNode node = entrance();
		start();
		player.setTickReady(true);

		assertTrue(guard.jumpDenial(player, normalSystem.getID(), campaignSystem.getID(), node.getId()).isPresent());
	}

	@Test
	public void givenAJumpNodeThatIsOnlyVisible_jumpDenial_shouldForbidTheJump()
	{
		JumpNode node = entrance();
		start();
		map.setJumpNodeState(node.getId(), CampaignJumpNodeState.VISIBLE);

		assertFalse(node.isHidden());
		assertTrue(guard.jumpDenial(player, normalSystem.getID(), campaignSystem.getID(), node.getId()).isPresent());
	}

	@Test
	public void givenStartAndEnd_shouldActivateAndResetTheJumpNodes()
	{
		JumpNode node = entrance();

		start();
		assertEquals(CampaignJumpNodeState.ACTIVE, map.getJumpNodeState(node.getId()));
		assertFalse(node.isHidden());

		campaigns.end(campaigns.findOpenCampaign().orElseThrow());
		assertEquals(CampaignJumpNodeState.PREPARED, map.getJumpNodeState(node.getId()));
		assertTrue(node.isHidden());
	}

	@Test
	public void givenAnExit_findExits_shouldOnlyReturnJumpNodesOutOfTheCampaignSystem()
	{
		map.createJumpNode(campaign, new Location(normalSystem.getID(), 5, 5), new Location(campaignSystem.getID(), 10, 10), "Zugang", true);

		List<JumpNode> exits = map.findExits(campaign);

		assertEquals(1, exits.size());
		assertEquals(campaignSystem.getID(), exits.get(0).getSystem());
		assertEquals(normalSystem.getID(), exits.get(0).getSystemOut());
	}

	private JumpNode entrance()
	{
		return map.createJumpNode(campaign, new Location(normalSystem.getID(), 5, 5), new Location(campaignSystem.getID(), 10, 10), "Zugang", false).get(0);
	}

	private void start()
	{
		campaigns.start(campaign);
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
