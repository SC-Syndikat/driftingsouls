package net.driftingsouls.ds2.server.modules.admin.editoren;

import net.driftingsouls.ds2.server.framework.pipeline.Request;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

/**
 * What a change affects that its author should see before it is made. The change waits until the
 * author sends the same request again with {@value #PARAMETER} set.
 */
public final class Confirmation
{
	/** Request parameter: 1 confirms the change. */
	public static final String PARAMETER = "confirmed";

	private final List<String> reasons;
	private final List<String> items;
	private final Map<String, String> parameters = new LinkedHashMap<>();

	/**
	 * @param reasons Why the change needs confirmation, one sentence each
	 * @param items What the change affects, one line each
	 */
	public Confirmation(List<String> reasons, List<String> items)
	{
		this.reasons = reasons;
		this.items = items;
	}

	/**
	 * Sends this value instead of the request's own when confirming, e.g. for a file uploaded with the
	 * request that the confirmation cannot send again.
	 */
	public Confirmation withParameter(String name, String value)
	{
		this.parameters.put(name, value);
		return this;
	}

	public static boolean isConfirmed(Request request)
	{
		return "1".equals(request.getParameterString(PARAMETER));
	}

	/**
	 * Renders the reasons, the affected things and a button sending the request again, confirmed.
	 *
	 * @param buttonLabel Label of the confirming button
	 */
	public void render(StringBuilder echo, Request request, String buttonLabel)
	{
		echo.append("<div class='gfxbox' style='width:700px'><span class='error'>Bitte bestätigen:</span>");
		for (String reason : reasons)
		{
			echo.append("<p>").append(escapeHtml4(reason)).append("</p>");
		}
		echo.append("<ul>");
		for (String item : items)
		{
			echo.append("<li>").append(escapeHtml4(item)).append("</li>");
		}
		echo.append("</ul><form action='./ds' method='post'>");
		for (String name : request.getParameterMap().keySet())
		{
			if (PARAMETER.equals(name) || parameters.containsKey(name))
			{
				continue;
			}
			for (String value : request.getParameterValues(name))
			{
				hidden(echo, name, value);
			}
		}
		parameters.forEach((name, value) -> hidden(echo, name, value));
		hidden(echo, PARAMETER, "1");
		echo.append("<input type='submit' value='").append(escapeHtml4(buttonLabel)).append("' /> ")
				.append("Ohne Bestätigung wird nichts geändert.</form></div><br />");
	}

	private static void hidden(StringBuilder echo, String name, String value)
	{
		echo.append("<input type='hidden' name='").append(escapeHtml4(name)).append("' value='").append(escapeHtml4(value)).append("' />");
	}
}
