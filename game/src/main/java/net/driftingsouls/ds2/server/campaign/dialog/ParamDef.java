package net.driftingsouls.ds2.server.campaign.dialog;

import net.driftingsouls.ds2.server.ships.ShipClasses;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A parameter a dialog factor or action takes. The editor renders a field per definition and validates
 * the value against it.
 */
public class ParamDef
{
	public enum Type
	{
		TEXT,
		LONG_TEXT,
		INTEGER,
		NUMBER,
		/** One of {@link #choices}. */
		CHOICE,
		/** The name of a campaign variable: chosen from the known ones, or a new one. */
		VARIABLE,
		/** An item or good, chosen by name; stored as its id. */
		ITEM,
		/** A ship group template, chosen by name; stored as its id. */
		SHIP_GROUP,
		/** An NPC account taking part in the campaign; stored as its id. */
		CAMPAIGN_NPC,
		/** An interaction; stored as its id. */
		INTERACTION,
		/** A campaign jump node; stored as its id. */
		CAMPAIGN_JUMP_NODE,
		/** A research; stored as its id. */
		RESEARCH,
		/** A unit type; stored as its id. */
		UNIT_TYPE,
		/** An alliance; stored as its id. */
		ALLIANCE,
		/** A rank number. */
		RANK,
		/** Several ship types; stored as ids separated by commas. */
		SHIP_TYPES,
		/** Several ship classes; stored as names separated by commas. */
		SHIP_CLASSES,
		/** A position, {@code system:x/y}; the system chosen from the campaign's. */
		LOCATION
	}

	/** The types stored as the id of one thing chosen from a list. */
	public static final Set<Type> REFERENCES = EnumSet.of(Type.SHIP_GROUP, Type.CAMPAIGN_NPC, Type.INTERACTION,
			Type.CAMPAIGN_JUMP_NODE, Type.RESEARCH, Type.UNIT_TYPE, Type.ALLIANCE, Type.RANK);

	/** The types stored as several things chosen from a list. */
	public static final Set<Type> LISTS = EnumSet.of(Type.SHIP_TYPES, Type.SHIP_CLASSES);

	/** Value of a {@link #keepOrAll} list: allow all. An empty value leaves things unchanged. */
	public static final String ALL = "-";

	public final String name;
	public final String label;
	public final Type type;
	public final boolean required;
	/** Value to label, for {@link Type#CHOICE}. */
	public final Map<String, String> choices;
	public final String defaultValue;
	/** Label of the empty choice of an optional reference: what leaving it empty means. */
	public final String emptyLabel;
	/** For lists: an empty value leaves things unchanged, {@link #ALL} allows all. */
	public final boolean keepOrAll;

	private ParamDef(String name, String label, Type type, boolean required, Map<String, String> choices, String defaultValue)
	{
		this(name, label, type, required, choices, defaultValue, null, false);
	}

	private ParamDef(String name, String label, Type type, boolean required, Map<String, String> choices, String defaultValue,
			String emptyLabel, boolean keepOrAll)
	{
		this.name = name;
		this.label = label;
		this.type = type;
		this.required = required;
		this.choices = choices;
		this.defaultValue = defaultValue;
		this.emptyLabel = emptyLabel;
		this.keepOrAll = keepOrAll;
	}

	public static ParamDef item(String name, String label)
	{
		return new ParamDef(name, label, Type.ITEM, true, Map.of(), "");
	}

	public static ParamDef variable(String name, String label)
	{
		return new ParamDef(name, label, Type.VARIABLE, true, Map.of(), "");
	}

	public static ParamDef text(String name, String label)
	{
		return new ParamDef(name, label, Type.TEXT, true, Map.of(), "");
	}

	public static ParamDef optionalText(String name, String label)
	{
		return new ParamDef(name, label, Type.TEXT, false, Map.of(), "");
	}

	public static ParamDef longText(String name, String label)
	{
		return new ParamDef(name, label, Type.LONG_TEXT, true, Map.of(), "");
	}

	public static ParamDef integer(String name, String label, int defaultValue)
	{
		return new ParamDef(name, label, Type.INTEGER, true, Map.of(), Integer.toString(defaultValue));
	}

	public static ParamDef optionalInteger(String name, String label)
	{
		return new ParamDef(name, label, Type.INTEGER, false, Map.of(), "");
	}

	public static ParamDef choice(String name, String label, String... valuesAndLabels)
	{
		Map<String, String> choices = new LinkedHashMap<>();
		for (int i = 0; i + 1 < valuesAndLabels.length; i += 2)
		{
			choices.put(valuesAndLabels[i], valuesAndLabels[i + 1]);
		}
		return new ParamDef(name, label, Type.CHOICE, true, choices, valuesAndLabels.length > 0 ? valuesAndLabels[0] : "");
	}

	/**
	 * @param type One of {@link #REFERENCES}
	 */
	public static ParamDef reference(Type type, String name, String label)
	{
		requireIn(type, REFERENCES);
		return new ParamDef(name, label, type, true, Map.of(), "");
	}

	/**
	 * @param type One of {@link #REFERENCES}
	 * @param emptyLabel What leaving the choice empty means
	 */
	public static ParamDef optionalReference(Type type, String name, String label, String emptyLabel)
	{
		requireIn(type, REFERENCES);
		return new ParamDef(name, label, type, false, Map.of(), "", emptyLabel, false);
	}

	/**
	 * @param type One of {@link #LISTS}
	 * @param keepOrAll Whether an empty value leaves things unchanged and {@link #ALL} allows all; otherwise
	 * at least one entry is required
	 */
	public static ParamDef list(Type type, String name, String label, boolean keepOrAll)
	{
		requireIn(type, LISTS);
		return new ParamDef(name, label, type, !keepOrAll, Map.of(), "", null, keepOrAll);
	}

	public static ParamDef location(String name, String label, boolean required)
	{
		return new ParamDef(name, label, Type.LOCATION, required, Map.of(), "");
	}

	private static void requireIn(Type type, Set<Type> types)
	{
		if (!types.contains(type))
		{
			throw new IllegalArgumentException("Unpassender Parametertyp: " + type);
		}
	}

	/**
	 * @return Why the value is invalid, or {@code null} if it is valid
	 */
	public String validate(String value)
	{
		String v = value == null ? "" : value.trim();
		if (v.isEmpty())
		{
			return required ? label + " fehlt." : null;
		}
		if (REFERENCES.contains(type))
		{
			return v.matches("-?\\d+") ? null : label + ": bitte aus der Liste wählen.";
		}
		switch (type)
		{
			case SHIP_TYPES:
				if (keepOrAll && ALL.equals(v))
				{
					return null;
				}
				return v.matches("\\d+(\\s*,\\s*\\d+)*") ? null : label + ": bitte Schiffstypen wählen.";
			case SHIP_CLASSES:
				if (keepOrAll && ALL.equals(v))
				{
					return null;
				}
				for (String shipClass : v.split("\\s*,\\s*"))
				{
					try
					{
						ShipClasses.valueOf(shipClass.toUpperCase());
					}
					catch (IllegalArgumentException e)
					{
						return label + ": unbekannte Schiffsklasse " + shipClass + ".";
					}
				}
				return null;
			case LOCATION:
				return v.matches("\\d+:\\d+/\\d+") ? null : label + ": bitte System und Position angeben.";
			case INTEGER:
				try
				{
					Long.parseLong(v);
				}
				catch (NumberFormatException e)
				{
					return label + " muss eine ganze Zahl sein.";
				}
				return null;
			case NUMBER:
				try
				{
					Double.parseDouble(v);
				}
				catch (NumberFormatException e)
				{
					return label + " muss eine Zahl sein.";
				}
				return null;
			case CHOICE:
				return choices.containsKey(v) ? null : label + " hat einen ungültigen Wert.";
			case ITEM:
				return v.matches("i?\\d+(\\|\\d+\\|\\d+)?") ? null : label + ": bitte ein Item wählen.";
			case VARIABLE:
				return v.length() <= 64 && v.matches("[\\w.-]+") ? null : label + ": nur Buchstaben, Ziffern, _ . - (höchstens 64 Zeichen).";
			default:
				return null;
		}
	}
}
