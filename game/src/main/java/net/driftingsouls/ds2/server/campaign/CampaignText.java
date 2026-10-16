package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.framework.Common;
import org.apache.commons.text.StringEscapeUtils;

/**
 * Names of players, ships and items carry BBCode and HTML entities. The campaign pages show them as plain
 * text, escaped exactly once where they are written into HTML.
 */
public final class CampaignText
{
	private CampaignText()
	{
		// static only
	}

	/**
	 * @return The text without BBCode and with entities decoded, e.g. "«NTB» Lady Azula" for a coloured name
	 */
	public static String plain(String bbcode)
	{
		if (bbcode == null)
		{
			return "";
		}
		return StringEscapeUtils.unescapeHtml4(Common._title(bbcode).replaceAll("<[^>]*>", "")).trim();
	}
}
