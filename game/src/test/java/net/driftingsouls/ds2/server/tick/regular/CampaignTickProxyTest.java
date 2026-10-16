package net.driftingsouls.ds2.server.tick.regular;

import net.driftingsouls.ds2.server.DBTest;
import net.driftingsouls.ds2.server.tick.TickController;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertTrue;

/**
 * Ticks are thread-scoped Spring proxies. The campaign tick's star systems used to be written to the
 * proxy's field, which the instance doing the work never saw: every part of a campaign tick ran as a
 * regular tick, so the campaign tick was never counted.
 */
public class CampaignTickProxyTest extends DBTest
{
	@Test
	public void gegebenDieSpringBean_setAffectedSystems_sollteDenTickAlsKampagnentickMarkieren()
	{
		TickController tick = getContext().getBean(CampaignTick.class, null);

		tick.setAffectedSystems(Set.of(621));

		assertTrue(tick.isCampaignTick());
	}
}
