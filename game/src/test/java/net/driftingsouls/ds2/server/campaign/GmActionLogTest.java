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
 * Every intervention of a game master is logged with the open campaign.
 */
public class GmActionLogTest extends DBSingleTransactionTest
{
	private User npc;
	private User gamemaster;
	private User player;
	private CampaignService campaigns;
	private GmActionLog log;

	@Before
	public void setUp()
	{
		// NPC first: creating it clears the persistence context
		npc = NpcTestUsers.create(getEM(), "admiral", -77);
		gamemaster = persist(new User("gm", "***", 0, "", new Cargo(), "gm@localhost"));
		gamemaster.setFlag(UserFlag.GAMEMASTER);
		player = persist(new User("player", "***", 0, "", new Cargo(), "player@localhost"));
		campaigns = new CampaignService(getEM());
		log = new GmActionLog();
	}

	@Test
	public void givenAnOpenCampaign_record_shouldLogTheActionWithCampaignAndTick()
	{
		Campaign prepared = campaigns.prepare("Sturm über Rigel", gamemaster);
		campaigns.updateParticipants(prepared, Set.of(npc.getId(), player.getId()), gamemaster);

		log.record(GmAction.SPAWN, gamemaster.getId(), npc.getId(), null, "3 Schiffe aus „Piratenrotte“ bei 7:5/5");
		log.record(GmAction.REQUEST_ACTION, gamemaster.getId(), npc.getId(), player.getId(), "Anfrage #4: 1000 RE");

		List<GmActionLog.Entry> entries = log.find(prepared.getId(), 10);
		assertEquals(2, entries.size());
		GmActionLog.Entry newest = entries.get(0);
		assertEquals("Neueste zuerst", GmAction.REQUEST_ACTION, newest.action);
		assertEquals(Integer.valueOf(prepared.getId()), newest.campaignId);
		assertEquals(0, newest.campaignTick);
		assertEquals(gamemaster.getId(), newest.gamemasterId);
		assertEquals(Integer.valueOf(npc.getId()), newest.npcId);
		assertEquals(Integer.valueOf(player.getId()), newest.userId);
		assertEquals("Anfrage #4: 1000 RE", newest.details);
		assertNull(entries.get(1).userId);
	}

	@Test
	public void givenNoOpenCampaign_record_shouldLogWithoutCampaign()
	{
		log.record(GmAction.MASTER_LOGIN, gamemaster.getId(), npc.getId(), null, "");

		assertEquals(1, log.find(null, 10).size());
		assertNull(log.find(null, 10).get(0).campaignId);
	}
}
