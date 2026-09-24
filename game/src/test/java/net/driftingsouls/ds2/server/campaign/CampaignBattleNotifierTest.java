package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.DBSingleTransactionTest;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.UserFlag;
import net.driftingsouls.ds2.server.entities.WellKnownUserValue;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class CampaignBattleNotifierTest extends DBSingleTransactionTest
{
	private User defender;
	private StarSystem campaignSystem;
	private StarSystem normalSystem;
	private CampaignBattleNotifier notifier;

	@Before
	public void setUp()
	{
		User gamemaster = new User("gm", "***", 0, "", new Cargo(), "gm@localhost");
		gamemaster.setFlag(UserFlag.GAMEMASTER);
		defender = new User("defender", "***", 0, "", new Cargo(), "defender@localhost");
		campaignSystem = persist(new StarSystem());
		normalSystem = persist(new StarSystem());
		getEM().flush();

		CampaignService campaigns = new CampaignService(getEM());
		Campaign campaign = campaigns.prepare("Test", gamemaster);
		campaigns.updateParticipants(campaign, Set.of(defender.getId()), gamemaster);
		new CampaignMapService(getEM()).addSystem(campaign, campaignSystem.getID());
		campaigns.start(campaign);
		defender.setTickReady(true);

		notifier = new CampaignBattleNotifier(getEM());
	}

	@Test
	public void gegebenEinFertigerTeilnehmerImKampagnensystem_onAttacked_sollteDieRundeFortsetzen()
	{
		Set<User> resumed = notifier.onAttacked(List.of(defender), campaignSystem.getID());

		assertEquals(Set.of(defender), resumed);
		assertFalse(defender.isTickReady());
		assertEquals(CampaignBattleNotifier.NOTICE, defender.getUserValue(WellKnownUserValue.CAMPAIGN_ATTACK_NOTICE));
	}

	@Test
	public void gegebenEinAngriffAusserhalbDerKampagne_onAttacked_sollteNichtsAendern()
	{
		Set<User> resumed = notifier.onAttacked(List.of(defender), normalSystem.getID());

		assertTrue(resumed.isEmpty());
		assertTrue(defender.isTickReady());
	}
}
