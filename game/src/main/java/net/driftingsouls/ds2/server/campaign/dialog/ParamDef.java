package net.driftingsouls.ds2.server.campaign.dialog;

import java.util.LinkedHashMap;
import java.util.Map;

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
		VARIABLE
	}

	public final String name;
	public final String label;
	public final Type type;
	public final boolean required;
	/** Value to label, for {@link Type#CHOICE}. */
	public final Map<String, String> choices;
	public final String defaultValue;

	private ParamDef(String name, String label, Type type, boolean required, Map<String, String> choices, String defaultValue)
	{
		this.name = name;
		this.label = label;
		this.type = type;
		this.required = required;
		this.choices = choices;
		this.defaultValue = defaultValue;
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
	 * @return Why the value is invalid, or {@code null} if it is valid
	 */
	public String validate(String value)
	{
		String v = value == null ? "" : value.trim();
		if (v.isEmpty())
		{
			return required ? label + " fehlt." : null;
		}
		switch (type)
		{
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
			case VARIABLE:
				return v.length() <= 64 && v.matches("[\\w.-]+") ? null : label + ": nur Buchstaben, Ziffern, _ . - (höchstens 64 Zeichen).";
			default:
				return null;
		}
	}
}
