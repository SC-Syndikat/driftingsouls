package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.ContextCommon;
import net.driftingsouls.ds2.server.Location;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.cargo.Resources;
import net.driftingsouls.ds2.server.cargo.modules.ModuleType;
import net.driftingsouls.ds2.server.entities.Offizier;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.map.TileCache;
import net.driftingsouls.ds2.server.ships.AlertLevel;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipClasses;
import net.driftingsouls.ds2.server.ships.ShipFleet;
import net.driftingsouls.ds2.server.ships.ShipType;
import net.driftingsouls.ds2.server.ships.ShipTypeData;
import net.driftingsouls.ds2.server.units.UnitType;
import net.driftingsouls.ds2.server.shipyards.ShipWerft;
import org.apache.commons.lang3.StringUtils;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Creates the ships of a {@link ShipGroupTemplate} for an owner at a location.
 */
public class ShipGroupSpawner
{
	private final EntityManager em;

	public ShipGroupSpawner(EntityManager em)
	{
		this.em = em;
	}

	/**
	 * @return The ships created, in template order
	 * @throws CampaignException if the template refers to something that does not exist
	 */
	public List<Ship> spawn(ShipGroupTemplate template, User owner, Location location)
	{
		List<Ship> ships = new ArrayList<>();
		ShipFleet fleet = null;
		if (template.createFleet && template.getShipCount() > 1)
		{
			fleet = new ShipFleet(template.name);
			em.persist(fleet);
		}

		String history = "Indienststellung am " + Common.getIngameTime(ContextMap.getContext().get(ContextCommon.class).getTick())
				+ " [hide]Vorlage " + template.name + "[/hide]";
		int number = 0;
		for (ShipGroupTemplate.Entry entry : template.entries)
		{
			ShipType type = em.find(ShipType.class, entry.shipTypeId);
			if (type == null)
			{
				throw new CampaignException("Unbekannter Schiffstyp " + entry.shipTypeId + " in Vorlage " + template.name + ".");
			}
			for (int i = 0; i < entry.count; i++)
			{
				number++;
				Ship ship = createShip(owner, type, location, history, ShipGroupModules.parse(entry.modules));
				ship.setName(StringUtils.isBlank(entry.namePattern)
						? template.name + " " + number
						: entry.namePattern.replace("{n}", Integer.toString(number)));
				ship.setCargo(cargoFor(type, entry.cargo));
				ship.setAlarm(AlertLevel.values()[Math.max(0, Math.min(entry.alarm, AlertLevel.values().length - 1))]);
				addUnits(ship, entry.units);
				if (entry.officer != null && mayCarryOfficer(type))
				{
					addOfficer(owner, ship, entry.officer);
				}
				if (fleet != null)
				{
					fleet.addShip(ship);
				}
				ship.recalculateShipStatus();
				ships.add(ship);
			}
		}
		TileCache.forSystem(location.getSystem()).resetCache();
		return ships;
	}

	private Ship createShip(User owner, ShipType type, Location location, String history, Map<Integer, Integer> modules)
	{
		Ship ship = new Ship(owner, type, location.getSystem(), location.getX(), location.getY());
		ship.getHistory().addHistory(history);
		ship.setEngine(100);
		ship.setWeapons(100);
		ship.setComm(100);
		ship.setSensors(100);
		em.persist(ship);

		// Modules first: they change what the ship holds and how strong it is
		ShipGroupModules.check(type, modules, em);
		modules.forEach((slot, item) -> ship.addModule(slot, ModuleType.ITEMMODULE, Integer.toString(item)));

		ShipTypeData stats = ship.getTypeData();
		ship.setHull(stats.getHull());
		ship.setEnergy(stats.getEps());
		ship.setCrew(stats.getCrew());
		ship.setShields(stats.getShields());
		ship.setNahrungCargo(stats.getNahrungCargo());
		ship.setAblativeArmor(stats.getAblativeArmor());

		if (stats.getWerft() != 0)
		{
			em.persist(new ShipWerft(ship));
		}
		return ship;
	}

	private static Cargo cargoFor(ShipType type, String cargoString)
	{
		Cargo cargo = StringUtils.isBlank(cargoString) ? new Cargo() : new Cargo(Cargo.Type.AUTO, cargoString);
		if (StringUtils.isBlank(cargoString))
		{
			cargo.addResource(Resources.DEUTERIUM, type.getRd() * 10L);
			cargo.addResource(Resources.URAN, type.getRu() * 10L);
			cargo.addResource(Resources.ANTIMATERIE, type.getRa() * 10L);
		}
		return cargo;
	}

	private void addUnits(Ship ship, String units)
	{
		Map<Integer, Long> parsed = ShipGroupUnits.parse(units);
		if (parsed.isEmpty())
		{
			return;
		}
		var cargo = ship.getUnits();
		for (Map.Entry<Integer, Long> unit : parsed.entrySet())
		{
			UnitType unitType = em.find(UnitType.class, unit.getKey());
			if (unitType == null)
			{
				throw new CampaignException("Unbekannter Einheitentyp: " + unit.getKey());
			}
			cargo.addUnit(unitType, unit.getValue());
		}
		cargo.save();
	}

	/**
	 * @return Whether ships of the type have a commanding officer: small ships have none, except escape pods
	 */
	static boolean mayCarryOfficer(ShipType type)
	{
		return type.getSize() > ShipType.SMALL_SHIP_MAXSIZE || type.getShipClass() == ShipClasses.RETTUNGSKAPSEL;
	}

	private void addOfficer(User owner, Ship ship, ShipGroupTemplate.Officer template)
	{
		Offizier officer = new Offizier(owner, "Kommandant " + ship.getName());
		for (Offizier.Ability ability : Offizier.Ability.values())
		{
			officer.setAbility(ability, template.getAbility(ability));
		}
		officer.setRang(template.getRank());
		officer.setSpecial(template.resolveSpecial(ThreadLocalRandom.current()));
		officer.stationierenAuf(ship);
		em.persist(officer);
	}
}
