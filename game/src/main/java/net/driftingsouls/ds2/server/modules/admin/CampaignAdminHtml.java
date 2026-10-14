package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignText;
import net.driftingsouls.ds2.server.campaign.dialog.CampaignVariableService;
import net.driftingsouls.ds2.server.campaign.dialog.ParamDef;
import net.driftingsouls.ds2.server.campaign.dialog.Params;
import net.driftingsouls.ds2.server.cargo.ItemData;
import net.driftingsouls.ds2.server.framework.pipeline.Request;
import net.driftingsouls.ds2.server.repositories.ItemRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

	/** Choice of a variable select meaning "a new variable, named in the text field". */
	private static final String NEW_VARIABLE = "__new";

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
			switch (param.type)
			{
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
			String value = request.getParameterString(prefix + param.name).trim();
			if (param.type == ParamDef.Type.VARIABLE && NEW_VARIABLE.equals(value))
			{
				value = request.getParameterString(prefix + param.name + NEW_VARIABLE).trim();
			}
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
