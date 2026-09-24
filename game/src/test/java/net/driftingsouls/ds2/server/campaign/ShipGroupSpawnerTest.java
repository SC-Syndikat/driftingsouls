package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.DBSingleTransactionTest;
import net.driftingsouls.ds2.server.Location;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipClasses;
import net.driftingsouls.ds2.server.ships.ShipType;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ShipGroupSpawnerTest extends DBSingleTransactionTest
{
	@Test
	public void gegebenEineVorlage_spawn_sollteAlleSchiffeAlsFlotteAmOrtErstellen()
	{
		User owner = new User("npc", "***", 0, "", new Cargo(), "npc@localhost");
		StarSystem system = persist(new StarSystem());
		system.setWidth(50);
		system.setHeight(50);
		ShipType type = persist(new ShipType(ShipClasses.JAEGER));
		type.setCrew(5);
		getEM().flush();

		ShipGroupTemplate template = new ShipGroupTemplate(1, "Patrouille", "", true, List.of(
				new ShipGroupTemplate.Entry(1, type.getId(), 2, "Wache {n}", "", "", null, 0)));

		Location location = new Location(system.getID(), 3, 4);
		List<Ship> ships = new ShipGroupSpawner(getEM()).spawn(template, owner, location);

		assertEquals(2, ships.size());
		assertEquals("Wache 1", ships.get(0).getName());
		assertEquals("Wache 2", ships.get(1).getName());
		for (Ship ship : ships)
		{
			assertEquals(location, ship.getLocation());
			assertEquals(owner, ship.getOwner());
			assertNotNull(ship.getFleet());
		}
		assertSame(ships.get(0).getFleet(), ships.get(1).getFleet());
	}

	@Test(expected = CampaignException.class)
	public void gegebenEinUnbekannterSchiffstyp_spawn_sollteAbgelehntWerden()
	{
		User owner = new User("npc", "***", 0, "", new Cargo(), "npc@localhost");
		ShipGroupTemplate template = new ShipGroupTemplate(1, "Kaputt", "", false, List.of(
				new ShipGroupTemplate.Entry(1, -12345, 1, "", "", "", null, 0)));

		new ShipGroupSpawner(getEM()).spawn(template, owner, new Location(1, 1, 1));
	}
}
