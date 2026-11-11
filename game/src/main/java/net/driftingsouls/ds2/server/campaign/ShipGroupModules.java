package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.cargo.modules.Module;
import net.driftingsouls.ds2.server.cargo.modules.ModuleEntry;
import net.driftingsouls.ds2.server.cargo.modules.ModuleType;
import net.driftingsouls.ds2.server.config.ModuleSlots;
import net.driftingsouls.ds2.server.config.NoSuchSlotException;
import net.driftingsouls.ds2.server.config.items.Schiffsmodul;
import net.driftingsouls.ds2.server.ships.ShipTypeData;
import org.apache.commons.lang3.StringUtils;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * The modules a ship group template installs on each ship: {@code slot:itemId}, separated by {@code |}.
 */
public final class ShipGroupModules
{
	private ShipGroupModules()
	{
		// static only
	}

	/**
	 * @return Slot number to module item id, in the order given; empty for a blank string
	 * @throws CampaignException if the string is malformed, names slot 0 or a slot twice
	 */
	public static Map<Integer, Integer> parse(String modules)
	{
		Map<Integer, Integer> result = new LinkedHashMap<>();
		if (StringUtils.isBlank(modules))
		{
			return result;
		}
		for (String part : StringUtils.split(modules, '|'))
		{
			String[] slotAndItem = StringUtils.split(part.trim(), ':');
			if (slotAndItem.length != 2)
			{
				throw new CampaignException("Ungültige Modulangabe: " + part);
			}
			int slot;
			int item;
			try
			{
				slot = Integer.parseInt(slotAndItem[0].trim());
				item = Integer.parseInt(slotAndItem[1].trim());
			}
			catch (NumberFormatException e)
			{
				throw new CampaignException("Ungültige Modulangabe: " + part);
			}
			if (slot <= 0)
			{
				throw new CampaignException("Ungültiger Modul-Slot: " + slot);
			}
			if (result.put(slot, item) != null)
			{
				throw new CampaignException("Slot " + slot + " hat mehr als ein Modul.");
			}
		}
		return result;
	}

	/**
	 * @return The stored form, ordered by slot
	 */
	public static String format(Map<Integer, Integer> modules)
	{
		StringJoiner joiner = new StringJoiner("|");
		new TreeMap<>(modules).forEach((slot, item) -> joiner.add(slot + ":" + item));
		return joiner.toString();
	}

	/**
	 * @param typeModules The slots of a ship type, {@code slot:slotType[:slotData]} separated by {@code ;}
	 * @return Slot number to slot type, in the order declared
	 */
	public static Map<Integer, String> slots(String typeModules)
	{
		Map<Integer, String> slots = new LinkedHashMap<>();
		if (StringUtils.isBlank(typeModules))
		{
			return slots;
		}
		for (String slot : StringUtils.split(typeModules, ';'))
		{
			String[] parts = StringUtils.splitPreserveAllTokens(slot, ':');
			if (parts.length >= 2 && StringUtils.isNumeric(parts[0]))
			{
				slots.put(Integer.parseInt(parts[0]), parts[1]);
			}
		}
		return slots;
	}

	/**
	 * Checks that every module goes into a slot the ship type has and that fits it, as the shipyard does.
	 *
	 * @throws CampaignException naming the first module that does not fit
	 */
	public static void check(ShipTypeData type, Map<Integer, Integer> modules, EntityManager em)
	{
		Map<Integer, String> slots = slots(type.getTypeModules());
		for (Map.Entry<Integer, Integer> module : modules.entrySet())
		{
			String slotType = slots.get(module.getKey());
			if (slotType == null)
			{
				throw new CampaignException("Der Schiffstyp hat keinen Modul-Slot " + module.getKey() + ".");
			}
			Schiffsmodul item = em.find(Schiffsmodul.class, module.getValue());
			if (item == null)
			{
				throw new CampaignException("Unbekanntes Schiffsmodul: " + module.getValue());
			}
			boolean fits;
			try
			{
				fits = ModuleSlots.get().slot(slotType).isMemberIn(item.getSlots());
			}
			catch (NoSuchSlotException e)
			{
				fits = false;
			}
			if (!fits)
			{
				throw new CampaignException("Das Modul " + CampaignText.plain(item.getName()) + " passt nicht in Slot " + module.getKey() + ".");
			}
		}
	}

	/**
	 * @return The stats of a ship of the type with the modules installed, computed as installing them on a
	 * ship does
	 */
	public static ShipTypeData apply(ShipTypeData type, Map<Integer, Integer> modules)
	{
		if (modules.isEmpty())
		{
			return type;
		}
		Map<Integer, String[]> slotData = new LinkedHashMap<>();
		for (String slot : StringUtils.split(StringUtils.defaultString(type.getTypeModules()), ';'))
		{
			String[] parts = StringUtils.splitPreserveAllTokens(slot, ':');
			if (StringUtils.isNumeric(parts[0]))
			{
				slotData.put(Integer.parseInt(parts[0]), parts);
			}
		}

		List<Module> moduleObjects = new ArrayList<>();
		for (Map.Entry<Integer, Integer> module : new TreeMap<>(modules).entrySet())
		{
			Module moduleObject = new ModuleEntry(module.getKey(), ModuleType.ITEMMODULE, Integer.toString(module.getValue())).createModule();
			String[] parts = slotData.get(module.getKey());
			if (parts != null && parts.length > 2)
			{
				moduleObject.setSlotData(parts[2]);
			}
			moduleObjects.add(moduleObject);
		}

		ShipTypeData stats = type;
		for (Module moduleObject : moduleObjects)
		{
			stats = moduleObject.modifyStats(stats, moduleObjects);
		}
		return stats;
	}
}
