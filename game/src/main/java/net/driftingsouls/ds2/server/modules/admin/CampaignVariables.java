package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.WellKnownAdminPermission;
import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.campaign.dialog.CampaignVariableService;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.pipeline.Request;

import javax.persistence.EntityManager;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static net.driftingsouls.ds2.server.modules.admin.CampaignAdminHtml.*;

/**
 * The campaign variables dialogs read and set: list, set and delete them, per player or global.
 */
@AdminMenuEntry(category = "Kampagne", name = "Kampagnenvariablen", permission = WellKnownAdminPermission.CAMPAIGN_VARIABLES)
public class CampaignVariables implements AdminPlugin
{
	private final CampaignVariableService variables = new CampaignVariableService();

	@Override
	public void output(StringBuilder echo)
	{
		CampaignAdminHtml.begin(echo);
		try
		{
			render(echo);
		}
		finally
		{
			CampaignAdminHtml.end(echo);
		}
	}

	private void render(StringBuilder echo)
	{
		Context context = ContextMap.getContext();
		EntityManager em = context.getEM();
		Request request = context.getRequest();

		Optional<Campaign> campaign = new CampaignService(em).findOpenCampaign();
		if (campaign.isEmpty())
		{
			noOpenCampaign(echo);
			return;
		}
		int campaignId = campaign.get().getId();
		Set<Integer> players = new TreeSet<>();
		for (int id : new CampaignService(em).getParticipantIds(campaign.get()))
		{
			User user = em.find(User.class, id);
			if (user != null && !user.isNPC())
			{
				players.add(id);
			}
		}

		try
		{
			switch (request.getParameterString("cpaction"))
			{
				case "set":
				{
					int userId = checkUser(players, request.getParameterInt("user"));
					String name = name(request);
					long value = number(request, "value");
					variables.set(campaignId, userId, name, value);
					message(echo, "Variable " + name + " (" + scope(em, userId) + ") = " + value + ".");
					break;
				}
				case "delete":
				{
					int userId = checkUser(players, request.getParameterInt("user"));
					String name = name(request);
					variables.delete(campaignId, userId, name);
					message(echo, "Variable " + name + " (" + scope(em, userId) + ") gelöscht, sie zählt jetzt als 0.");
					break;
				}
				default:
					break;
			}
		}
		catch (CampaignException e)
		{
			error(echo, e.getMessage());
		}

		list(echo, em, campaignId);
		addForm(echo, em, players);
	}

	/**
	 * Variables belong to the campaign's players, or are global; nothing else may be written.
	 */
	private static int checkUser(Set<Integer> players, int userId)
	{
		if (userId != CampaignVariableService.GLOBAL && !players.contains(userId))
		{
			throw new CampaignException("Spieler " + userId + " nimmt nicht an der Kampagne teil.");
		}
		return userId;
	}

	private static String name(Request request)
	{
		String name = request.getParameterString("name").trim();
		if (name.isEmpty() || name.length() > 64)
		{
			throw new CampaignException("Der Name muss 1 bis 64 Zeichen lang sein.");
		}
		return name;
	}

	private static long number(Request request, String parameter)
	{
		String value = request.getParameterString(parameter).trim();
		try
		{
			return Long.parseLong(value);
		}
		catch (NumberFormatException e)
		{
			throw new CampaignException("Ungültige ganze Zahl: " + value);
		}
	}

	private static String scope(EntityManager em, int userId)
	{
		if (userId == CampaignVariableService.GLOBAL)
		{
			return "global";
		}
		User user = em.find(User.class, userId);
		return (user == null ? "?" : user.getPlainname()) + " (" + userId + ")";
	}

	private void list(StringBuilder echo, EntityManager em, int campaignId)
	{
		echo.append("<div class='gfxbox' style='width:900px'><h3>Kampagnenvariablen</h3>");
		echo.append("<p class='campaign-hint'>Dialoge lesen sie mit dem Faktor „Kampagnenvariable“ und ändern sie mit den Aktionen ")
				.append("„Kampagnenvariable setzen“ und „… erhöhen / verringern“. Nicht gesetzte Variablen zählen als 0.</p>");
		Map<String, Long> values = variables.list(campaignId);
		if (values.isEmpty())
		{
			echo.append("<p>Noch keine Variablen gesetzt.</p></div><br />");
			return;
		}
		echo.append("<table class='noBorderX'><tr><td class='noBorderX'>Gültigkeit</td><td class='noBorderX'>Name</td>")
				.append("<td class='noBorderX'>Wert</td><td class='noBorderX'></td></tr>");
		for (Map.Entry<String, Long> variable : values.entrySet())
		{
			String[] userAndName = variable.getKey().split("/", 2);
			int userId = Integer.parseInt(userAndName[0]);
			String name = userAndName[1];
			echo.append("<tr><td class='noBorderX'>").append(esc(scope(em, userId)))
					.append("</td><td class='noBorderX'>").append(esc(name))
					.append("</td><td class='noBorderX'>");
			echo.append("<form action='./ds' method='post' style='display:inline'>");
			hiddenFields(echo, getClass(), "set");
			hidden(echo, "user", userId);
			hidden(echo, "name", name);
			echo.append("<input type='text' name='value' size='8' value='").append(variable.getValue()).append("' /> <input type='submit' value='setzen' /></form>");
			echo.append("</td><td class='noBorderX'>");
			actionButton(echo, getClass(), "delete", "löschen", "user", userId, "name", name);
			echo.append("</td></tr>");
		}
		echo.append("</table></div><br />");
	}

	private void addForm(StringBuilder echo, EntityManager em, Set<Integer> players)
	{
		echo.append("<div class='gfxbox' style='width:900px'><h3>Variable setzen oder ändern</h3>");
		echo.append("<form action='./ds' method='post'>");
		hiddenFields(echo, getClass(), "set");
		echo.append("Gültigkeit: <select name='user'><option value='0'>global (für die ganze Kampagne)</option>");
		for (int id : players)
		{
			echo.append("<option value='").append(id).append("'>").append(esc(scope(em, id))).append("</option>");
		}
		echo.append("</select> Name: <input type='text' name='name' size='24' maxlength='64' /> ");
		echo.append("Wert: <input type='text' name='value' size='8' value='0' /> <input type='submit' value='setzen' />");
		echo.append("</form></div>");
	}
}
