package net.driftingsouls.ds2.server.campaign;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CampaignActionGuardTest
{
	private static final Campaign PREPARING = campaign(CampaignStatus.PREPARING);
	private static final Campaign RUNNING = campaign(CampaignStatus.RUNNING);

	@Test
	public void givenNoCampaignSystem_decide_shouldAllowEverything()
	{
		assertAllowed(RUNNING, false, false, true, CampaignAction.START_BATTLE);
		assertAllowed(PREPARING, false, false, false, CampaignAction.ENTER_SYSTEM);
	}

	@Test
	public void givenACampaignInPreparation_decide_shouldOnlyForbidEntering()
	{
		assertDenied(PREPARING, true, true, false, CampaignAction.ENTER_SYSTEM);
		assertAllowed(PREPARING, true, false, false, CampaignAction.MOVE);
	}

	@Test
	public void givenARunningCampaign_decide_shouldExcludeNonParticipants()
	{
		assertDenied(RUNNING, true, false, false, CampaignAction.ENTER_SYSTEM);
		assertDenied(RUNNING, true, false, false, CampaignAction.TRANSFER);
	}

	@Test
	public void givenARunningCampaign_decide_shouldBlockFinishedParticipants()
	{
		assertDenied(RUNNING, true, true, true, CampaignAction.MOVE);
		assertDenied(RUNNING, true, true, true, CampaignAction.START_BATTLE);
		assertAllowed(RUNNING, true, true, false, CampaignAction.START_BATTLE);
	}

	private static void assertAllowed(Campaign campaign, boolean campaignSystem, boolean participant, boolean tickReady, CampaignAction action)
	{
		assertFalse(CampaignActionGuard.decide(campaign, campaignSystem, participant, tickReady, action).isPresent());
	}

	private static void assertDenied(Campaign campaign, boolean campaignSystem, boolean participant, boolean tickReady, CampaignAction action)
	{
		assertTrue(CampaignActionGuard.decide(campaign, campaignSystem, participant, tickReady, action).isPresent());
	}

	private static Campaign campaign(CampaignStatus status)
	{
		return new Campaign(1, "Test", 1, status, 0, null, null, 0);
	}
}
