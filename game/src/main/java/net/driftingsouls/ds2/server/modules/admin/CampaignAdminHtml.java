package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignText;
import net.driftingsouls.ds2.server.campaign.dialog.CampaignVariableService;
import net.driftingsouls.ds2.server.campaign.dialog.ParamChoices;
import net.driftingsouls.ds2.server.campaign.dialog.ParamDef;
import net.driftingsouls.ds2.server.campaign.dialog.Params;
import net.driftingsouls.ds2.server.cargo.ItemData;
import net.driftingsouls.ds2.server.framework.pipeline.Request;
import net.driftingsouls.ds2.server.repositories.ItemRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

/**
 * HTML building blocks shared by the campaign admin plugins. Every text passed in is escaped.
 */
public final class CampaignAdminHtml
{
	private CampaignAdminHtml()
	{
		// static only
	}

	/**
	 * Spacing for the campaign pages: the admin defaults put rows and form fields so close together
	 * that the pages are hard to read.
	 */
	private static final String STYLE = "<style>"
			+ ".campaign-admin { line-height: 1.6; }"
			+ ".campaign-admin .gfxbox { padding: 12px 16px; margin-bottom: 16px; }"
			+ ".campaign-admin h3 { margin: 0 0 10px 0; }"
			+ ".campaign-admin h4 { margin: 16px 0 6px 0; }"
			+ ".campaign-admin p { margin: 6px 0 10px 0; }"
			+ ".campaign-admin table { border-collapse: separate; border-spacing: 0 2px; }"
			+ ".campaign-admin td { padding: 4px 10px 4px 0; vertical-align: top; }"
			+ ".campaign-admin tr:nth-child(even) td { background: rgba(255,255,255,0.03); }"
			+ ".campaign-admin form { margin: 6px 0; }"
			+ ".campaign-admin input[type=text], .campaign-admin select, .campaign-admin textarea { margin: 2px 6px 2px 0; padding: 2px 4px; }"
			+ ".campaign-admin input[type=submit], .campaign-admin button { margin: 2px 4px 2px 0; padding: 2px 8px; }"
			+ ".campaign-admin ul { margin: 6px 0 10px 0; padding-left: 20px; }"
			+ ".campaign-admin li { margin: 4px 0; }"
			+ ".campaign-admin .campaign-section { border: 1px solid #555; padding: 10px 12px; margin: 10px 0; }"
			+ ".campaign-admin .campaign-hint { color: #aaaaaa; font-size: 90%; }"
			+ "</style>";

	/**
	 * Opens a campaign page; pair with {@link #end}.
	 */
	public static void begin(StringBuilder echo)
	{
		echo.append(STYLE).append("<div class='campaign-admin'>");
	}

	public static void end(StringBuilder echo)
	{
		echo.append("</div>");
	}

	static String esc(Object value)
	{
		return value == null ? "" : escapeHtml4(value.toString());
	}

	static void message(StringBuilder echo, String text)
	{
		echo.append("<div class='gfxbox' style='width:590px'>").append(esc(text)).append("</div><br />");
	}

	static void error(StringBuilder echo, String text)
	{
		echo.append("<div class='gfxbox' style='width:590px'><span class='error'>").append(esc(text)).append("</span></div><br />");
	}

	/**
	 * The hidden fields every form posting back to an admin plugin needs.
	 */
	static void hiddenFields(StringBuilder echo, Class<?> plugin, String action)
	{
		echo.append("<input type='hidden' name='module' value='admin' />");
		echo.append("<input type='hidden' name='namedplugin' value='").append(plugin.getName()).append("' />");
		echo.append("<input type='hidden' name='cpaction' value='").append(esc(action)).append("' />");
	}

	static void hidden(StringBuilder echo, String name, Object value)
	{
		echo.append("<input type='hidden' name='").append(esc(name)).append("' value='").append(esc(value)).append("' />");
	}

	static String pluginUrl(Class<?> plugin)
	{
		return "./ds?module=admin&amp;namedplugin=" + plugin.getName();
	}

	/**
	 * A one-button form posting {@code action} with the given parameters.
	 */
	static void actionButton(StringBuilder echo, Class<?> plugin, String action, String label, Object... params)
	{
		echo.append("<form action='./ds' method='post' style='display:inline'>");
		hiddenFields(echo, plugin, action);
		for (int i = 0; i + 1 < params.length; i += 2)
		{
			hidden(echo, params[i].toString(), params[i + 1]);
		}
		echo.append("<input type='submit' value='").append(esc(label)).append("' /></form> ");
	}

	/**
	 * A select over the given choices. The choice whose key reads like {@code selected} is preselected.
	 *
	 * @param emptyLabel Label of a leading choice with an empty value; {@code null} for none
	 */
	static void select(StringBuilder echo, String name, Map<?, String> options, Object selected, String emptyLabel)
	{
		String current = selected == null ? "" : selected.toString();
		echo.append("<select name='").append(esc(name)).append("'>");
		if (emptyLabel != null)
		{
			echo.append("<option value=''>").append(esc(emptyLabel)).append("</option>");
		}
		options.forEach((key, label) -> echo.append("<option value='").append(esc(key)).append("'")
				.append(key.toString().equals(current) ? " selected='selected'" : "").append(">").append(esc(label)).append("</option>"));
		echo.append("</select>");
	}

	/**
	 * A select with several choices; the request carries one value per chosen entry.
	 */
	static void multiSelect(StringBuilder echo, String name, Map<?, String> options, Collection<?> selected)
	{
		Set<String> current = new HashSet<>();
		selected.forEach(value -> current.add(value.toString()));
		echo.append("<select name='").append(esc(name)).append("' multiple='multiple' size='").append(Math.max(2, Math.min(8, options.size()))).append("'>");
		options.forEach((key, label) -> echo.append("<option value='").append(esc(key)).append("'")
				.append(current.contains(key.toString()) ? " selected='selected'" : "").append(">").append(esc(label)).append("</option>"));
		echo.append("</select> <span class='campaign-hint'>Mehrfachauswahl mit Strg</span>");
	}

	/**
	 * A select whose choices are grouped under headings; empty groups are left out.
	 */
	static void groupedSelect(StringBuilder echo, String name, Map<String, ? extends Map<?, String>> groups, Object selected)
	{
		String current = selected == null ? "" : selected.toString();
		echo.append("<select name='").append(esc(name)).append("'>");
		groups.forEach((group, options) -> {
			if (options.isEmpty())
			{
				return;
			}
			echo.append("<optgroup label='").append(esc(group)).append("'>");
			options.forEach((key, label) -> echo.append("<option value='").append(esc(key)).append("'")
					.append(key.toString().equals(current) ? " selected='selected'" : "").append(">").append(esc(label)).append("</option>"));
			echo.append("</optgroup>");
		});
		echo.append("</select>");
	}

	/**
	 * @return The integer values the request carries for a {@link #multiSelect}
	 * @throws CampaignException if one is not a number
	 */
	static Set<Integer> multiSelectIds(Request request, String name)
	{
		Set<Integer> ids = new LinkedHashSet<>();
		for (String value : request.getParameterValues(name))
		{
			if (value.isBlank())
			{
				continue;
			}
			try
			{
				ids.add(Integer.parseInt(value.trim()));
			}
			catch (NumberFormatException e)
			{
				throw new CampaignException("Ungültige Auswahl: " + value);
			}
		}
		return ids;
	}

	/** Choice of a variable select meaning "a new variable, named in the text field". */
	private static final String NEW_VARIABLE = "__new";

	/** Suffix of the select choosing whether a keep-or-all list stays unchanged, allows all or only the chosen. */
	private static final String LIST_MODE = "__mode";
	private static final String LIST_MODE_CHOSEN = "list";

	/** Suffixes of the fields making up a location. */
	private static final String LOCATION_SYSTEM = "__system";
	private static final String LOCATION_X = "__x";
	private static final String LOCATION_Y = "__y";

	/**
	 * Renders one input per parameter, named {@code p_<name>}.
	 */
	static void paramFields(StringBuilder echo, List<ParamDef> params, Params values)
	{
		paramFields(echo, params, values, "p_");
	}

	/**
	 * Renders one input per parameter, named {@code <prefix><name>}; several forms can share a page.
	 */
	public static void paramFields(StringBuilder echo, List<ParamDef> params, Params values, String prefix)
	{
		SortedSet<String> variables = null;
		for (ParamDef param : params)
		{
			String value = values != null && values.has(param.name) ? values.get(param.name) : param.defaultValue;
			echo.append(esc(param.label)).append(": ");
			String name = prefix + param.name;
			if (ParamDef.REFERENCES.contains(param.type))
			{
				referenceField(echo, param, name, value);
				echo.append("<br />");
				continue;
			}
			switch (param.type)
			{
				case SHIP_TYPES:
				case SHIP_CLASSES:
					listField(echo, param, name, value);
					break;
				case LOCATION:
					locationField(echo, param, name, value);
					break;
				case ITEM:
				{
					// Stored as the id; older values may carry the cargo form "i16|0|0"
					String selected = value == null ? "" : value.replaceAll("^i?(\\d+).*$", "$1");
					echo.append("<select name='").append(esc(name)).append("'>");
					ItemRepository.getInstance().getItemsData().values().stream()
							.sorted(Comparator.comparing((ItemData item) -> CampaignText.plain(item.getName()).toLowerCase()))
							.forEach(item -> echo.append("<option value='").append(item.getId()).append("'")
									.append(String.valueOf(item.getId()).equals(selected) ? " selected='selected'" : "").append(">")
									.append(esc(CampaignText.plain(item.getName()))).append(" (").append(item.getId()).append(")</option>"));
					echo.append("</select>");
					break;
				}
				case VARIABLE:
				{
					if (variables == null)
					{
						variables = new CampaignVariableService().knownNames();
					}
					echo.append("<select name='").append(esc(name)).append("'>");
					for (String variable : variables)
					{
						echo.append("<option value='").append(esc(variable)).append("'").append(variable.equals(value) ? " selected='selected'" : "")
								.append(">").append(esc(variable)).append("</option>");
					}
					echo.append("<option value='").append(NEW_VARIABLE).append("'").append(variables.isEmpty() ? " selected='selected'" : "")
							.append(">neue Variable …</option></select> <input type='text' name='").append(esc(name)).append(NEW_VARIABLE)
							.append("' size='20' placeholder='Name der neuen Variable' />");
					break;
				}
				case CHOICE:
					echo.append("<select name='").append(esc(name)).append("'>");
					param.choices.forEach((v, label) -> echo.append("<option value='").append(esc(v)).append("'")
							.append(v.equals(value) ? " selected='selected'" : "").append(">").append(esc(label)).append("</option>"));
					echo.append("</select>");
					break;
				case LONG_TEXT:
					echo.append("<br /><textarea name='").append(esc(name)).append("' rows='3' cols='60'>").append(esc(value)).append("</textarea>");
					break;
				default:
					echo.append("<input type='text' name='").append(esc(name)).append("' size='")
							.append(param.type == ParamDef.Type.TEXT ? 30 : 8).append("' value='").append(esc(value)).append("' />");
			}
			echo.append("<br />");
		}
	}

	private static void referenceField(StringBuilder echo, ParamDef param, String name, String value)
	{
		ParamChoices choices = ParamChoices.forCurrentRequest();
		Map<String, String> options = new LinkedHashMap<>(choices.get(param.type));
		String current = value == null ? "" : value.trim();
		if (!current.isEmpty() && !options.containsKey(current))
		{
			options.put(current, "unbekannt (" + current + ")");
		}
		String emptyLabel = param.required
				? (options.isEmpty() ? "– nichts zur Auswahl –" : null)
				: (param.emptyLabel != null ? param.emptyLabel : "– keine Angabe –");
		select(echo, name, options, current, emptyLabel);
	}

	private static void listField(StringBuilder echo, ParamDef param, String name, String value)
	{
		String current = value == null ? "" : value.trim();
		if (param.keepOrAll)
		{
			Map<String, String> modes = new LinkedHashMap<>();
			modes.put("", "unverändert");
			modes.put(ParamDef.ALL, "alle erlauben");
			modes.put(LIST_MODE_CHOSEN, "nur die gewählten");
			select(echo, name + LIST_MODE, modes, current.isEmpty() || ParamDef.ALL.equals(current) ? current : LIST_MODE_CHOSEN, null);
			echo.append("<br />");
		}
		Set<String> chosen = new LinkedHashSet<>();
		if (!current.isEmpty() && !ParamDef.ALL.equals(current))
		{
			for (String entry : current.split(","))
			{
				String trimmed = entry.trim();
				chosen.add(param.type == ParamDef.Type.SHIP_CLASSES ? trimmed.toUpperCase() : trimmed);
			}
		}
		Map<String, String> options = new LinkedHashMap<>(ParamChoices.forCurrentRequest().get(param.type));
		for (String entry : chosen)
		{
			options.putIfAbsent(entry, "unbekannt (" + entry + ")");
		}
		echo.append("<select name='").append(esc(name)).append("' multiple='multiple' size='6'>");
		options.forEach((key, label) -> echo.append("<option value='").append(esc(key)).append("'")
				.append(chosen.contains(key) ? " selected='selected'" : "").append(">").append(esc(label)).append("</option>"));
		echo.append("</select> <span class='campaign-hint'>Mehrfachauswahl mit Strg</span>");
	}

	private static void locationField(StringBuilder echo, ParamDef param, String name, String value)
	{
		String current = value == null ? "" : value.trim();
		String system = "";
		String x = "";
		String y = "";
		if (current.matches("[0-9]+:[0-9]+/[0-9]+"))
		{
			system = current.substring(0, current.indexOf(':'));
			x = current.substring(current.indexOf(':') + 1, current.indexOf('/'));
			y = current.substring(current.indexOf('/') + 1);
		}
		Map<String, String> systems = new LinkedHashMap<>(ParamChoices.forCurrentRequest().getCampaignSystems());
		if (!system.isEmpty())
		{
			systems.putIfAbsent(system, "System " + system);
		}
		echo.append("System ");
		select(echo, name + LOCATION_SYSTEM, systems, system, param.required ? null : "– keine Angabe –");
		echo.append(" Position <input type='text' name='").append(esc(name + LOCATION_X)).append("' size='4' value='").append(esc(x)).append("' />/")
				.append("<input type='text' name='").append(esc(name + LOCATION_Y)).append("' size='4' value='").append(esc(y)).append("' />");
	}

	/**
	 * Reads the parameters rendered by {@link #paramFields} and validates them.
	 *
	 * @throws CampaignException listing every invalid parameter
	 */
	static Params readParams(Request request, List<ParamDef> params)
	{
		return readParams(request, params, "p_");
	}

	/**
	 * Reads the parameters rendered by {@link #paramFields(StringBuilder, List, Params, String)}.
	 */
	public static Params readParams(Request request, List<ParamDef> params, String prefix)
	{
		Map<String, String> values = new LinkedHashMap<>();
		List<String> errors = new ArrayList<>();
		for (ParamDef param : params)
		{
			String value = readValue(request, param, prefix + param.name);
			String error = param.validate(value);
			if (error != null)
			{
				errors.add(error);
			}
			values.put(param.name, value);
		}
		if (!errors.isEmpty())
		{
			throw new CampaignException(String.join(" ", errors));
		}
		return new Params(values);
	}

	private static String readValue(Request request, ParamDef param, String name)
	{
		String value = request.getParameterString(name).trim();
		switch (param.type)
		{
			case VARIABLE:
				return NEW_VARIABLE.equals(value) ? request.getParameterString(name + NEW_VARIABLE).trim() : value;
			case SHIP_TYPES:
			case SHIP_CLASSES:
			{
				String chosen = String.join(",", Arrays.asList(request.getParameterValues(name)));
				if (!param.keepOrAll)
				{
					return chosen;
				}
				String mode = request.getParameterString(name + LIST_MODE);
				if (!LIST_MODE_CHOSEN.equals(mode))
				{
					return ParamDef.ALL.equals(mode) ? ParamDef.ALL : "";
				}
				if (chosen.isEmpty())
				{
					throw new CampaignException(param.label + ": bitte mindestens einen Eintrag wählen oder „alle erlauben“.");
				}
				return chosen;
			}
			case LOCATION:
			{
				String system = request.getParameterString(name + LOCATION_SYSTEM).trim();
				if (system.isEmpty())
				{
					return "";
				}
				return system + ":" + request.getParameterString(name + LOCATION_X).trim() + "/" + request.getParameterString(name + LOCATION_Y).trim();
			}
			default:
				return value;
		}
	}

	/**
	 * @return The request parameter as integer, or {@code null} if it is empty
	 */
	static Integer optionalInt(Request request, String name)
	{
		String value = request.getParameterString(name).trim();
		if (value.isEmpty())
		{
			return null;
		}
		try
		{
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e)
		{
			throw new CampaignException("Ungültige Zahl: " + value);
		}
	}

	static Double optionalDouble(Request request, String name)
	{
		String value = request.getParameterString(name).trim().replace(',', '.');
		if (value.isEmpty())
		{
			return null;
		}
		try
		{
			return Double.parseDouble(value);
		}
		catch (NumberFormatException e)
		{
			throw new CampaignException("Ungültige Zahl: " + value);
		}
	}

	static void noOpenCampaign(StringBuilder echo)
	{
		echo.append("<div class='gfxbox' style='width:590px'>Es wird keine Kampagne vorbereitet und keine läuft. ")
				.append("<a class='forschinfo' href='").append(pluginUrl(CampaignParticipants.class)).append("'>Kampagne vorbereiten</a></div>");
	}
}
