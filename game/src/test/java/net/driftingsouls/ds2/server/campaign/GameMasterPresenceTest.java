package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.DBSingleTransactionTest;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.UserFlag;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

/**
 * Messages to the game master go to the one game master online, or to all of them.
 */
public class GameMasterPresenceTest extends DBSingleTransactionTest
{
	private User first;
	private User second;
	private Campaign campaign;

	@Before
	public void setUp()
	{
		first = persist(new User("gm1", "***", 0, "", new Cargo(), "gm1@localhost"));
		first.setFlag(UserFlag.GAMEMASTER);
		second = persist(new User("gm2", "***", 0, "", new Cargo(), "gm2@localhost"));
		second.setFlag(UserFlag.GAMEMASTER);
		persist(new User("player", "***", 0, "", new Cargo(), "player@localhost"));
		campaign = new CampaignService(getEM()).prepare("Sturm über Rigel", first);
	}

	@Test
	public void givenOneGameMasterOnline_recipients_shouldBeOnlyHim()
	{
		GameMasterPresence presence = new GameMasterPresence(getEM(), id -> id == second.getId());

		assertEquals(Set.of(second.getId()), presence.recipients(campaign));
		assertEquals(Set.of(second.getId()), presence.online(campaign));
	}

	@Test
	public void givenTwoGameMastersOnline_recipients_shouldBeBoth()
	{
		GameMasterPresence presence = new GameMasterPresence(getEM(), id -> true);

		assertEquals(Set.of(first.getId(), second.getId()), presence.recipients(campaign));
	}

	@Test
	public void givenNoGameMasterOnline_recipients_shouldBeAllOfThem()
	{
		GameMasterPresence presence = new GameMasterPresence(getEM(), id -> false);

		assertEquals(Set.of(first.getId(), second.getId()), presence.recipients(campaign));
		assertTrue(presence.online(campaign).isEmpty());
	}
}
