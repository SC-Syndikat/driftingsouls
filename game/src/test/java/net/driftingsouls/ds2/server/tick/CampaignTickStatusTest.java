package net.driftingsouls.ds2.server.tick;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CampaignTickStatusTest
{
	@After
	public void tearDown()
	{
		CampaignTickStatus.reset();
	}

	@Test
	public void givenAStartedTick_running_shouldKeepTheStepAndTheStart()
	{
		CampaignTickStatus.started();
		long startedAt = CampaignTickStatus.get().getStartedAt();

		CampaignTickStatus.running("berechne Schiffe");

		CampaignTickStatus status = CampaignTickStatus.get();
		assertEquals(CampaignTickStatus.State.RUNNING, status.getState());
		assertEquals("berechne Schiffe", status.getStep());
		assertEquals(startedAt, status.getStartedAt());
	}

	@Test
	public void givenARunningTick_finished_shouldForgetTheStepAndRecordTheEnd()
	{
		CampaignTickStatus.running("berechne Schlachten");

		CampaignTickStatus.finished();

		CampaignTickStatus status = CampaignTickStatus.get();
		assertEquals(CampaignTickStatus.State.FINISHED, status.getState());
		assertNull(status.getStep());
		assertTrue(status.getFinishedAt() >= status.getStartedAt());
	}
}
