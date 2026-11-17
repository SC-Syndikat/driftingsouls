package net.driftingsouls.ds2.server.campaign.dialog;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text of a dialog node that depends on campaign variables, written into the BBCode:
 *
 * <pre>
 * [wenn notruf_teile &gt;= 2]...shown if the player's variable is at least 2...[sonst]...otherwise...[/wenn]
 * [wenn global:phase == 1]...a campaign-wide variable...[/wenn]
 * </pre>
 *
 * Blocks cannot be nested. Unset variables count as 0, outside a campaign every variable is 0.
 */
public final class DialogText
{
	private static final String GLOBAL_PREFIX = "global:";
	private static final Pattern BLOCK = Pattern.compile(
			"\\[wenn\\s+([\\w:]+)\\s*(>=|<=|==|!=|>|<)\\s*(-?\\d+)\\s*\\](.*?)(?:\\[sonst\\](.*?))?\\[/wenn\\]",
			Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

	private DialogText()
	{
		// static only
	}

	/**
	 * @return The text with every block replaced by the part that applies to the player
	 */
	public static String resolve(String text, DialogContext context)
	{
		if (text == null || !text.contains("[") )
		{
			return text;
		}
		Matcher matcher = BLOCK.matcher(text);
		StringBuilder result = new StringBuilder();
		while (matcher.find())
		{
			long value = variable(matcher.group(1), context);
			boolean holds = Comparison.fromSymbol(matcher.group(2)).test(value, Long.parseLong(matcher.group(3)));
			String part = holds ? matcher.group(4) : matcher.group(5);
			matcher.appendReplacement(result, Matcher.quoteReplacement(part == null ? "" : part));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	private static long variable(String name, DialogContext context)
	{
		if (context == null || context.campaign == null)
		{
			return 0;
		}
		if (name.toLowerCase().startsWith(GLOBAL_PREFIX))
		{
			return context.variables.get(context.campaign.getId(), CampaignVariableService.GLOBAL, name.substring(GLOBAL_PREFIX.length()));
		}
		return context.variables.get(context.campaign.getId(), context.user.getId(), name);
	}
}
