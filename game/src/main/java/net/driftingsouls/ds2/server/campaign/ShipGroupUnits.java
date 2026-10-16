package net.driftingsouls.ds2.server.campaign;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * The units a ship group template puts on each ship: {@code unitTypeId:count}, separated by {@code |}.
 */
public final class ShipGroupUnits
{
	private ShipGroupUnits()
	{
		// static only
	}

	/**
	 * @return Unit type id to count, in the order given; empty for a blank string
	 * @throws CampaignException if the string is malformed
	 */
	public static Map<Integer, Long> parse(String units)
	{
		Map<Integer, Long> result = new LinkedHashMap<>();
		if (StringUtils.isBlank(units))
		{
			return result;
		}
		for (String part : StringUtils.split(units, '|'))
		{
			String[] idAndCount = StringUtils.split(part.trim(), ':');
			if (idAndCount.length != 2)
			{
				throw new CampaignException("Ungültige Einheitenangabe: " + part);
			}
			try
			{
				int id = Integer.parseInt(idAndCount[0].trim());
				long count = Long.parseLong(idAndCount[1].trim());
				if (count < 0)
				{
					throw new CampaignException("Ungültige Einheitenanzahl: " + part);
				}
				result.merge(id, count, Long::sum);
			}
			catch (NumberFormatException e)
			{
				throw new CampaignException("Ungültige Einheitenangabe: " + part);
			}
		}
		return result;
	}

	/**
	 * @return The stored form, leaving out unit types without units
	 */
	public static String format(Map<Integer, Long> units)
	{
		StringJoiner joiner = new StringJoiner("|");
		units.forEach((id, count) -> {
			if (count > 0)
			{
				joiner.add(id + ":" + count);
			}
		});
		return joiner.toString();
	}

	/**
	 * @param sizes Size of each unit type, by id
	 * @return The unit space the units take on one ship
	 */
	public static long mass(Map<Integer, Long> units, Map<Integer, Integer> sizes)
	{
		long mass = 0;
		for (Map.Entry<Integer, Long> unit : units.entrySet())
		{
			mass += unit.getValue() * sizes.getOrDefault(unit.getKey(), 0);
		}
		return mass;
	}

	/**
	 * Checks that the units fit on one ship of a type, by the rules of unit transfers.
	 *
	 * @param sizes Size of each unit type, by id
	 * @param unitSpace The unit space of the ship type
	 * @param maxUnitSize The largest unit size the ship type takes; -1 for any
	 * @throws CampaignException if a unit type is unknown or too large, or the units need too much space
	 */
	public static void check(Map<Integer, Long> units, Map<Integer, Integer> sizes, int unitSpace, int maxUnitSize)
	{
		for (Map.Entry<Integer, Long> unit : units.entrySet())
		{
			Integer size = sizes.get(unit.getKey());
			if (size == null)
			{
				throw new CampaignException("Unbekannter Einheitentyp: " + unit.getKey());
			}
			if (unit.getValue() > 0 && maxUnitSize != -1 && size > maxUnitSize)
			{
				throw new CampaignException("Einheitentyp " + unit.getKey() + " ist zu groß für diesen Schiffstyp (Größe " + size
						+ ", höchstens " + maxUnitSize + ").");
			}
		}
		long mass = mass(units, sizes);
		if (mass > unitSpace)
		{
			throw new CampaignException("Die Einheiten brauchen " + mass + " Platz, der Schiffstyp hat nur " + unitSpace + ".");
		}
	}
}
