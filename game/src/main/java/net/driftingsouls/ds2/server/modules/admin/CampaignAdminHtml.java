package net.driftingsouls.ds2.server.modules.admin;

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

	static void noOpenCampaign(StringBuilder echo)
	{
		echo.append("<div class='gfxbox' style='width:590px'>Es wird keine Kampagne vorbereitet und keine läuft. ")
				.append("<a class='forschinfo' href='").append(pluginUrl(CampaignParticipants.class)).append("'>Kampagne vorbereiten</a></div>");
	}
}
