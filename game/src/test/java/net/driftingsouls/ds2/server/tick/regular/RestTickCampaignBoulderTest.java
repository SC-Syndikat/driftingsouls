package net.driftingsouls.ds2.server.tick.regular;

import net.driftingsouls.ds2.server.DBTest;
import net.driftingsouls.ds2.server.campaign.NpcTestUsers;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.config.ConfigFelsbrocken;
import net.driftingsouls.ds2.server.config.ConfigFelsbrockenSystem;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.ships.ShipClasses;
import net.driftingsouls.ds2.server.ships.ShipType;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * A campaign tick restricts the boulders to the campaign's star systems. Its query compared the star
 * system entity with the system numbers, which Hibernate rejects: the boulders were never filled up
 * in a campaign tick.
 */
public class RestTickCampaignBoulderTest extends DBTest
{
	@Test
	public void givenACampaignTick_restTick_shouldFillUpTheBouldersOfItsStarSystems()
	{
		final int[] systemId = new int[1];
		final int[] typeId = new int[1];
		mitTransaktion(() -> {
			// The boulders belong to the system account
			NpcTestUsers.create(getEM(), "nobody", -1);
			StarSystem system = persist(new StarSystem());
			system.setWidth(20);
			system.setHeight(20);
			ShipType type = persist(new ShipType(ShipClasses.FELSBROCKEN));
			ConfigFelsbrockenSystem boulders = persist(new ConfigFelsbrockenSystem(system, 2));
			ConfigFelsbrocken boulder = persist(new ConfigFelsbrocken(boulders, type, 10, new Cargo()));
			boulders.getFelsbrocken().add(boulder);
			systemId[0] = system.getID();
			typeId[0] = type.getId();
		});

		RestTick tick = (RestTick) getContext().getBean(RestTick.class, null);
		tick.setAffectedSystems(Set.of(systemId[0]));
		try
		{
			tick.execute();
		}
		finally
		{
			tick.setAffectedSystems(Set.of());
		}

		long count = getEM().createQuery("select count(s) from Ship s where s.system = :system and s.shiptype.id = :type", Long.class)
				.setParameter("system", systemId[0])
				.setParameter("type", typeId[0])
				.getSingleResult();
		assertEquals(2, count);
	}
}
