package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.DBSingleTransactionTest;
import net.driftingsouls.ds2.server.Location;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.config.ModuleSlot;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.config.items.Schiffsmodul;
import net.driftingsouls.ds2.server.entities.Offizier;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.ships.SchiffstypModifikation;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipClasses;
import net.driftingsouls.ds2.server.ships.ShipType;
import org.junit.Test;

import java.util.List;
import java.util.Set;

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
				new ShipGroupTemplate.Entry(1, type.getId(), 2, "Wache {n}", "", "", "", null, 0)));

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

	@Test
	public void gegebenEinOffizier_spawn_sollteFaehigkeitenRangUndSpezialfaehigkeitSetzen()
	{
		User owner = new User("npc", "***", 0, "", new Cargo(), "npc@localhost");
		StarSystem system = persist(new StarSystem());
		ShipType type = persist(new ShipType(ShipClasses.KREUZER));
		type.setCrew(5);
		type.setSize(ShipType.SMALL_SHIP_MAXSIZE + 1);
		getEM().flush();

		ShipGroupTemplate template = new ShipGroupTemplate(1, "Kommando", "", false, List.of(
				new ShipGroupTemplate.Entry(1, type.getId(), 1, "", "", "", "",
						new ShipGroupTemplate.Officer(100, 200, 300, 400, 500, "WAFFENNARR"), 0)));

		Ship ship = new ShipGroupSpawner(getEM()).spawn(template, owner, new Location(system.getID(), 1, 1)).get(0);

		Offizier officer = ship.getOffizier();
		assertNotNull(officer);
		assertEquals(100, officer.getAbility(Offizier.Ability.NAV));
		assertEquals(500, officer.getAbility(Offizier.Ability.COM));
		assertEquals(2, officer.getRang());
		assertEquals(Offizier.Special.WAFFENNARR, officer.getSpecial());
	}

	@Test
	public void gegebenEinKleinesSchiff_spawn_sollteKeinenOffizierAnlegen()
	{
		User owner = new User("npc", "***", 0, "", new Cargo(), "npc@localhost");
		StarSystem system = persist(new StarSystem());
		ShipType type = persist(new ShipType(ShipClasses.JAEGER));
		type.setCrew(1);
		type.setSize(ShipType.SMALL_SHIP_MAXSIZE);
		getEM().flush();

		ShipGroupTemplate template = new ShipGroupTemplate(1, "Staffel", "", false, List.of(
				new ShipGroupTemplate.Entry(1, type.getId(), 1, "", "", "", "",
						new ShipGroupTemplate.Officer(100, 100, 100, 100, 100, ""), 0)));

		Ship ship = new ShipGroupSpawner(getEM()).spawn(template, owner, new Location(system.getID(), 1, 1)).get(0);

		assertTrue(ship.getOffiziere().isEmpty());
	}

	@Test
	public void gegebenEinModul_spawn_sollteEsEinbauenUndDieWerteDesSchiffsAnpassen()
	{
		User owner = new User("npc", "***", 0, "", new Cargo(), "npc@localhost");
		StarSystem system = persist(new StarSystem());
		persist(new ModuleSlot("lager", "Lagerslot", null));
		SchiffstypModifikation mods = new SchiffstypModifikation();
		mods.setProduces(new Cargo());
		persist(mods);
		mods.setCargo(500);
		mods.setHull(50);
		Schiffsmodul module = new Schiffsmodul(0, "Frachtmodul");
		module.setSlots(Set.of("lager"));
		module.setMods(mods);
		persist(module);
		ShipType type = persist(new ShipType(ShipClasses.KREUZER));
		type.setCrew(5);
		type.setCargo(100);
		type.setHull(200);
		type.setModules("1:lager");
		getEM().flush();

		ShipGroupTemplate template = new ShipGroupTemplate(1, "Frachter", "", false, List.of(
				new ShipGroupTemplate.Entry(1, type.getId(), 1, "", "", "", "1:" + module.getID(), null, 0)));

		Ship ship = new ShipGroupSpawner(getEM()).spawn(template, owner, new Location(system.getID(), 1, 1)).get(0);

		assertEquals(1, ship.getModules().length);
		assertEquals(600, ship.getTypeData().getCargo());
		assertEquals("Hülle nach den Werten mit Modul", 250, ship.getHull());
		assertEquals("Grenzen beim Speichern wie beim Einbau", 600, ShipGroupModules.apply(type, java.util.Map.of(1, module.getID())).getCargo());
	}

	@Test(expected = CampaignException.class)
	public void gegebenEinUnpassendesModul_spawn_sollteAbgelehntWerden()
	{
		User owner = new User("npc", "***", 0, "", new Cargo(), "npc@localhost");
		StarSystem system = persist(new StarSystem());
		persist(new ModuleSlot("lager", "Lagerslot", null));
		persist(new ModuleSlot("waffe", "Waffenslot", null));
		Schiffsmodul module = new Schiffsmodul(0, "Kanone");
		module.setSlots(Set.of("waffe"));
		persist(module);
		ShipType type = persist(new ShipType(ShipClasses.KREUZER));
		type.setModules("1:lager");
		getEM().flush();

		ShipGroupTemplate template = new ShipGroupTemplate(1, "Falsch", "", false, List.of(
				new ShipGroupTemplate.Entry(1, type.getId(), 1, "", "", "", "1:" + module.getID(), null, 0)));

		new ShipGroupSpawner(getEM()).spawn(template, owner, new Location(system.getID(), 1, 1));
	}

	@Test(expected = CampaignException.class)
	public void gegebenEinUnbekannterSchiffstyp_spawn_sollteAbgelehntWerden()
	{
		User owner = new User("npc", "***", 0, "", new Cargo(), "npc@localhost");
		ShipGroupTemplate template = new ShipGroupTemplate(1, "Kaputt", "", false, List.of(
				new ShipGroupTemplate.Entry(1, -12345, 1, "", "", "", "", null, 0)));

		new ShipGroupSpawner(getEM()).spawn(template, owner, new Location(1, 1, 1));
	}
}
