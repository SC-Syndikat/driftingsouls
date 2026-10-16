package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.config.Offiziere;
import net.driftingsouls.ds2.server.entities.Offizier;

import java.util.List;
import java.util.Random;

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
		/** {@code slot:moduleItemId}, separated by {@code |}. */
		public final String modules;
		/** The commanding officer of each ship; {@code null} for none. */
		public final Officer officer;
		public final int alarm;

		public Entry(int id, int shipTypeId, int count, String namePattern, String cargo, String units, String modules, Officer officer, int alarm)
		{
			this.id = id;
			this.shipTypeId = shipTypeId;
			this.count = count;
			this.namePattern = namePattern;
			this.cargo = cargo;
			this.units = units;
			this.modules = modules;
			this.officer = officer;
			this.alarm = alarm;
		}
	}

	/**
	 * The officer commanding each ship of an entry.
	 */
	public static class Officer
	{
		/** {@link #special} value: every officer gets a special ability drawn at random. */
		public static final String RANDOM_SPECIAL = "ZUFALL";

		public final int nav;
		public final int ing;
		public final int waf;
		public final int sec;
		public final int com;
		/** Name of an {@link Offizier.Special}, {@link #RANDOM_SPECIAL}, or empty for none. */
		public final String special;

		/**
		 * @throws CampaignException if an ability is negative or the special ability unknown
		 */
		public Officer(int nav, int ing, int waf, int sec, int com, String special)
		{
			if (nav < 0 || ing < 0 || waf < 0 || sec < 0 || com < 0)
			{
				throw new CampaignException("Die Fähigkeiten des Offiziers dürfen nicht negativ sein.");
			}
			this.nav = nav;
			this.ing = ing;
			this.waf = waf;
			this.sec = sec;
			this.com = com;
			this.special = special == null ? "" : special;
			if (!this.special.isEmpty() && !RANDOM_SPECIAL.equals(this.special))
			{
				try
				{
					Offizier.Special.valueOf(this.special);
				}
				catch (IllegalArgumentException e)
				{
					throw new CampaignException("Unbekannte Spezialfähigkeit: " + this.special);
				}
			}
		}

		public int getAbility(Offizier.Ability ability)
		{
			switch (ability)
			{
				case NAV:
					return nav;
				case ING:
					return ing;
				case WAF:
					return waf;
				case SEC:
					return sec;
				case COM:
					return com;
				default:
					throw new IllegalArgumentException("Unbekannte Fähigkeit: " + ability);
			}
		}

		/**
		 * @return The rank these abilities earn in the game: their average divided by 125, at most
		 * {@link Offiziere#MAX_RANG}
		 */
		public int getRank()
		{
			return Math.min(Offiziere.MAX_RANG, (int) ((nav + ing + waf + sec + com) / 5.0 / 125));
		}

		/**
		 * @return The special ability for one officer; a random one, never {@link Offizier.Special#NONE},
		 * for {@link #RANDOM_SPECIAL}
		 */
		public Offizier.Special resolveSpecial(Random random)
		{
			if (special.isEmpty())
			{
				return Offizier.Special.NONE;
			}
			if (RANDOM_SPECIAL.equals(special))
			{
				Offizier.Special[] specials = Offizier.Special.values();
				return specials[1 + random.nextInt(specials.length - 1)];
			}
			return Offizier.Special.valueOf(special);
		}
	}
}
