package net.driftingsouls.ds2.server.tick.regular;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BattleTickLockTest
{
	@Test
	public void gegebenEinRegulaererTickImNormalenSystem_lockSeconds_sollteDieNormaleSperreLiefern()
	{
		assertEquals(1800, BattleTick.lockSeconds(false, false, 1800, 120));
	}

	@Test
	public void gegebenEinRegulaererTickImKampagnensystem_lockSeconds_sollteDieKurzeSperreLiefern()
	{
		assertEquals(120, BattleTick.lockSeconds(false, true, 1800, 120));
	}

	@Test
	public void gegebenEinKampagnentick_lockSeconds_sollteKeineSperreLiefern()
	{
		assertEquals(0, BattleTick.lockSeconds(true, true, 1800, 120));
		assertEquals(0, BattleTick.lockSeconds(true, false, 1800, 120));
	}
}
