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
}
