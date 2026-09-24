package net.driftingsouls.ds2.server.campaign;

import java.util.List;

/**
 * A reusable NPC fleet: which ships, how many, with what cargo, units and officer.
 */
public class ShipGroupTemplate
{
	public final int id;
	public final String name;
	public final String description;
	public final boolean createFleet;
	public final List<Entry> entries;

	public ShipGroupTemplate(int id, String name, String description, boolean createFleet, List<Entry> entries)
	{
		this.id = id;
		this.name = name;
		this.description = description;
		this.createFleet = createFleet;
		this.entries = entries;
	}

	public int getShipCount()
	{
		return entries.stream().mapToInt(e -> e.count).sum();
	}

	/**
	 * One kind of ship in the template.
	 */
	public static class Entry
	{
		public final int id;
		public final int shipTypeId;
		public final int count;
		/** {@code {n}} is replaced by the running number of the ship. */
		public final String namePattern;
		/** Cargo string as the ship editor shows it; empty for default fuel only. */
		public final String cargo;
		/** {@code unitTypeId:count}, separated by {@code |}. */
		public final String units;
		/** All abilities of the officer; {@code null} for no officer. */
		public final Integer officerSkill;
		public final int alarm;

		public Entry(int id, int shipTypeId, int count, String namePattern, String cargo, String units, Integer officerSkill, int alarm)
		{
			this.id = id;
			this.shipTypeId = shipTypeId;
			this.count = count;
			this.namePattern = namePattern;
			this.cargo = cargo;
			this.units = units;
			this.officerSkill = officerSkill;
			this.alarm = alarm;
		}
	}
}
