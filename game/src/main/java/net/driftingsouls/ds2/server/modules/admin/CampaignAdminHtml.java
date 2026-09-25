package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.dialog.ParamDef;
import net.driftingsouls.ds2.server.campaign.dialog.Params;
import net.driftingsouls.ds2.server.framework.pipeline.Request;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

/**
 * HTML building blocks shared by the campaign admin plugins. Every text passed in is escaped.
 */
final class CampaignAdminHtml
{
	private CampaignAdminHtml()
	{
		// static only
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
	 * Renders one input per parameter, named {@code p_<name>}.
	 */
	static void paramFields(StringBuilder echo, List<ParamDef> params, Params values)
	{
		for (ParamDef param : params)
		{
			String value = values != null && values.has(param.name) ? values.get(param.name) : param.defaultValue;
			echo.append(esc(param.label)).append(": ");
			String name = "p_" + param.name;
			switch (param.type)
			{
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
		Map<String, String> values = new LinkedHashMap<>();
		List<String> errors = new ArrayList<>();
		for (ParamDef param : params)
		{
			String value = request.getParameterString("p_" + param.name).trim();
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
