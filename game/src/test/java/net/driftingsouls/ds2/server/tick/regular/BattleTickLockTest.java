package net.driftingsouls.ds2.server.tick.regular;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BattleTickLockTest
{
	@Test
	public void givenARegularTickInANormalSystem_lockSeconds_shouldReturnTheNormalLock()
	{
		assertEquals(1800, BattleTick.lockSeconds(false, false, 1800, 120));
	}

	@Test
	public void givenARegularTickInACampaignSystem_lockSeconds_shouldReturnTheShortLock()
	{
		assertEquals(120, BattleTick.lockSeconds(false, true, 1800, 120));
	}

	@Test
	public void givenACampaignTick_lockSeconds_shouldReturnNoLock()
	{
		assertEquals(0, BattleTick.lockSeconds(true, true, 1800, 120));
		assertEquals(0, BattleTick.lockSeconds(true, false, 1800, 120));
	}
}
