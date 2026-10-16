package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.ContextCommon;
import net.driftingsouls.ds2.server.Location;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.cargo.Resources;
import net.driftingsouls.ds2.server.entities.Offizier;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.map.TileCache;
import net.driftingsouls.ds2.server.ships.Alarmstufe;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipFleet;
import net.driftingsouls.ds2.server.ships.ShipType;
import net.driftingsouls.ds2.server.units.UnitType;
import net.driftingsouls.ds2.server.werften.ShipWerft;
import org.apache.commons.lang3.StringUtils;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;

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
				Ship ship = createShip(owner, type, location, history);
				ship.setName(StringUtils.isBlank(entry.namePattern)
						? template.name + " " + number
						: entry.namePattern.replace("{n}", Integer.toString(number)));
				ship.setCargo(cargoFor(type, entry.cargo));
				ship.setAlarm(Alarmstufe.values()[Math.max(0, Math.min(entry.alarm, Alarmstufe.values().length - 1))]);
				addUnits(ship, entry.units);
				if (entry.officerSkill != null)
				{
					addOfficer(owner, ship, entry.officerSkill);
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

	private Ship createShip(User owner, ShipType type, Location location, String history)
	{
		Ship ship = new Ship(owner, type, location.getSystem(), location.getX(), location.getY());
		ship.getHistory().addHistory(history);
		ship.setHull(type.getHull());
		ship.setEnergy(type.getEps());
		ship.setCrew(type.getCrew());
		ship.setShields(type.getShields());
		ship.setNahrungCargo(type.getNahrungCargo());
		ship.setAblativeArmor(type.getAblativeArmor());
		ship.setEngine(100);
		ship.setWeapons(100);
		ship.setComm(100);
		ship.setSensors(100);
		em.persist(ship);

		if (type.getWerft() != 0)
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
		if (StringUtils.isBlank(units))
		{
			return;
		}
		var cargo = ship.getUnits();
		for (String part : StringUtils.split(units, '|'))
		{
			String[] idAndCount = StringUtils.split(part.trim(), ':');
			if (idAndCount.length != 2)
			{
				throw new CampaignException("Ungültige Einheitenangabe: " + part);
			}
			UnitType unitType = em.find(UnitType.class, Integer.parseInt(idAndCount[0].trim()));
			if (unitType == null)
			{
				throw new CampaignException("Unbekannter Einheitentyp: " + idAndCount[0]);
			}
			cargo.addUnit(unitType, Long.parseLong(idAndCount[1].trim()));
		}
		cargo.save();
	}

	private void addOfficer(User owner, Ship ship, int skill)
	{
		Offizier officer = new Offizier(owner, "Kommandant " + ship.getName());
		for (Offizier.Ability ability : Offizier.Ability.values())
		{
			officer.setAbility(ability, skill);
		}
		officer.stationierenAuf(ship);
		em.persist(officer);
	}
}
