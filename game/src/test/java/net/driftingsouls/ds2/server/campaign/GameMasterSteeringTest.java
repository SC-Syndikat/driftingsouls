package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.DBSingleTransactionTest;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.UserFlag;
import net.driftingsouls.ds2.server.framework.BasicContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

/**
 * How a game master acts for the campaign's NPCs, with the open campaign in the database: steering an NPC
 * keeps it in the campaign star systems, and a master login is possible only into participating NPCs.
 */
public class GameMasterSteeringTest extends DBSingleTransactionTest
{
	private User npc;
	private User otherNpc;
	private User gamemaster;
	private User player;
	private StarSystem inCampaign;
	private StarSystem outside;

	@Before
	public void setUp()
	{
		// NPCs first: creating one clears the persistence context
		otherNpc = NpcTestUsers.create(getEM(), "fremd", -78);
		npc = NpcTestUsers.create(getEM(), "admiral", -77);
		gamemaster = persist(new User("gm", "***", 0, "", new Cargo(), "gm@localhost"));
		gamemaster.setFlag(UserFlag.GAMEMASTER);
		player = persist(new User("player", "***", 0, "", new Cargo(), "player@localhost"));
		inCampaign = persist(new StarSystem());
		outside = persist(new StarSystem());
		getEM().flush();

		CampaignService campaigns = new CampaignService(getEM());
		Campaign prepared = campaigns.prepare("Test", gamemaster);
		campaigns.updateParticipants(prepared, Set.of(npc.getId(), player.getId()), gamemaster);
		new CampaignMapService(getEM()).addSystem(prepared, inCampaign.getID());
		campaigns.start(prepared);
	}

	@After
	public void leaveTheSession()
	{
		context().setActiveUser(null);
	}

	@Test
	public void givenTheGameMasterSteersAnNpc_itsActions_shouldStayInCampaignSystems()
	{
		steer(npc);
		CampaignActionGuard guard = new CampaignActionGuard();

		assertTrue(GameMasterControl.isControlling());
		assertFalse(guard.controlDenial(npc, inCampaign.getID()).isPresent());
		assertTrue(guard.controlDenial(npc, outside.getID()).isPresent());
		assertTrue("Auch kein Sprung hinaus", guard.jumpDenial(npc, inCampaign.getID(), outside.getID(), null).isPresent());
		assertTrue(guard.denial(npc, outside.getID(), CampaignAction.MOVE).isPresent());
	}

	@Test
	public void givenAMasterLoginInsteadOfSteering_theNpc_shouldNotBeRestricted()
	{
		steer(npc);
		context().getRequest().getFromSession(GameMasterControl.State.class).active = false;

		assertFalse(GameMasterControl.isControlling());
		assertFalse(new CampaignActionGuard().controlDenial(npc, outside.getID()).isPresent());
	}

	@Test
	public void givenAGameMaster_masterLogin_shouldOnlyReachTheParticipatingNpcs()
	{
		context().setActiveUser(gamemaster);

		GameMasterRestriction restriction = GameMasterRestriction.forActiveUser();

		assertTrue(restriction.applies());
		assertEquals(Set.of(npc.getId()), restriction.getMasterLoginIds());
		restriction.checkMasterLogin(npc.getId());
		assertThrows("Ein NPC, der nicht teilnimmt", CampaignException.class, () -> restriction.checkMasterLogin(otherNpc.getId()));
		assertThrows("Ein Spieler, auch wenn er teilnimmt", CampaignException.class, () -> restriction.checkMasterLogin(player.getId()));
	}

	@Test
	public void givenTheCampaignHasEnded_masterLogin_shouldReachNoNpc()
	{
		CampaignService campaigns = new CampaignService(getEM());
		campaigns.end(campaigns.findOpenCampaign().orElseThrow(), gamemaster);
		context().setActiveUser(gamemaster);

		GameMasterRestriction restriction = GameMasterRestriction.forActiveUser();

		assertTrue(restriction.getMasterLoginIds().isEmpty());
		assertThrows(CampaignException.class, () -> restriction.checkMasterLogin(npc.getId()));
	}

	/**
	 * As the switch on the ship overview does: the NPC is the active user, the game master attached behind it.
	 */
	private void steer(User target)
	{
		target.attachToUser(gamemaster);
		context().setActiveUser(target);
		context().getRequest().getFromSession(GameMasterControl.State.class).active = true;
	}

	private BasicContext context()
	{
		return (BasicContext) getContext();
	}
}
