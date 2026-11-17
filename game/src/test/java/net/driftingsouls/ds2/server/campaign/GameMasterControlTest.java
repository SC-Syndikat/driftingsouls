package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.DBSingleTransactionTest;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.UserFlag;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * The game master steers the NPCs of the open campaign, already while it is being prepared.
 */
public class GameMasterControlTest extends DBSingleTransactionTest
{
	private User npc;
	private User gamemaster;
	private CampaignService campaigns;

	@Before
	public void setUp()
	{
		// NPC first: creating it clears the persistence context
		npc = NpcTestUsers.create(getEM(), "admiral", -77);
		gamemaster = persist(new User("gm", "***", 0, "", new Cargo(), "gm@localhost"));
		gamemaster.setFlag(UserFlag.GAMEMASTER);
		campaigns = new CampaignService(getEM());
	}

	@Test
	public void givenACampaignInPreparation_controllableNpcs_shouldBeItsNpcs()
	{
		Campaign prepared = campaigns.prepare("Sturm über Rigel", gamemaster);
		campaigns.updateParticipants(prepared, Set.of(npc.getId()), gamemaster);

		assertEquals(List.of(npc), GameMasterControl.controllableNpcs());
	}

	@Test
	public void givenNoOpenCampaign_controllableNpcs_shouldBeEmpty()
	{
		assertTrue(GameMasterControl.controllableNpcs().isEmpty());
	}
}
