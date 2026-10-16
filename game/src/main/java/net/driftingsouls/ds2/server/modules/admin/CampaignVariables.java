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
import java.util.SortedSet;
import java.util.TreeSet;

import static net.driftingsouls.ds2.server.modules.admin.CampaignAdminHtml.*;

/**
 * The campaign variables dialogs read and set: list, set and delete them, per player or global.
 */
@AdminMenuEntry(category = "Kampagne", name = "Kampagnenvariablen", permission = WellKnownAdminPermission.CAMPAIGN_VARIABLES)
public class CampaignVariables implements AdminPlugin
{
	private static final String NEW = "__new";

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
		if (NEW.equals(name))
		{
			name = request.getParameterString("newname").trim();
		}
		if (name.isEmpty() || name.length() > 64 || !name.matches("[\\w.-]+"))
		{
			throw new CampaignException("Der Name muss 1 bis 64 Zeichen lang sein: Buchstaben, Ziffern, _ . -");
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
		echo.append("<div class='gfxbox' style='width:950px'><h3>Kampagnenvariablen</h3>");
		echo.append("<p class='campaign-hint'>Dialoge lesen sie mit dem Faktor „Kampagnenvariable“ und ändern sie mit den Aktionen ")
				.append("„Kampagnenvariable setzen“ und „… erhöhen / verringern“. Nicht gesetzte Variablen zählen als 0. ")
				.append("Spaltenköpfe sortieren, die Felder darunter filtern.</p>");
		Map<String, Long> values = variables.list(campaignId);
		echo.append("<table class='noBorderX' id='cpVariables'><thead><tr>")
				.append("<td class='noBorderX' style='cursor:pointer' data-col='0'>Gültigkeit ⇅</td>")
				.append("<td class='noBorderX' style='cursor:pointer' data-col='1'>Name ⇅</td>")
				.append("<td class='noBorderX' style='cursor:pointer' data-col='2' data-numeric='1'>Wert ⇅</td>")
				.append("<td class='noBorderX'></td></tr><tr>")
				.append("<td class='noBorderX'><input type='text' size='18' data-filter='0' placeholder='Filter' /></td>")
				.append("<td class='noBorderX'><input type='text' size='18' data-filter='1' placeholder='Filter' /></td>")
				.append("<td class='noBorderX'><input type='text' size='8' data-filter='2' placeholder='Filter' /></td>")
				.append("<td class='noBorderX'></td></tr></thead><tbody>");
		for (Map.Entry<String, Long> variable : values.entrySet())
		{
			String[] userAndName = variable.getKey().split("/", 2);
			int userId = Integer.parseInt(userAndName[0]);
			String name = userAndName[1];
			echo.append("<tr><td class='noBorderX'>").append(esc(scope(em, userId)))
					.append("</td><td class='noBorderX'>").append(esc(name))
					.append("</td><td class='noBorderX' data-value='").append(variable.getValue()).append("'>");
			echo.append("<form action='./ds' method='post' style='display:inline'>");
			hiddenFields(echo, getClass(), "set");
			hidden(echo, "user", userId);
			hidden(echo, "name", name);
			echo.append("<input type='text' name='value' size='8' value='").append(variable.getValue()).append("' /> <input type='submit' value='setzen' /></form>");
			echo.append("</td><td class='noBorderX'>");
			actionButton(echo, getClass(), "delete", "löschen", "user", userId, "name", name);
			echo.append("</td></tr>");
		}
		echo.append("</tbody></table>");
		if (values.isEmpty())
		{
			echo.append("<p>Noch keine Variablen gesetzt.</p>");
		}
		echo.append("</div><br />");
		// Sorting and filtering happen in the page; the table is small
		echo.append("<script type='text/javascript'>(function(){var t=document.getElementById('cpVariables');if(!t)return;var b=t.tBodies[0];")
				.append("function cell(r,c){var td=r.cells[c];return td.hasAttribute('data-value')?td.getAttribute('data-value'):td.textContent.trim();}")
				.append("var dir={};t.querySelectorAll('[data-col]').forEach(function(h){h.onclick=function(){var c=+h.getAttribute('data-col');")
				.append("var num=h.hasAttribute('data-numeric');dir[c]=!dir[c];var rows=Array.prototype.slice.call(b.rows);")
				.append("rows.sort(function(x,y){var a=cell(x,c),z=cell(y,c);var r=num?(a-z):a.localeCompare(z,'de');return dir[c]?r:-r;});")
				.append("rows.forEach(function(r){b.appendChild(r);});};});")
				.append("var f=t.querySelectorAll('[data-filter]');function filter(){Array.prototype.forEach.call(b.rows,function(r){var show=true;")
				.append("f.forEach(function(i){var v=i.value.trim().toLowerCase();if(v&&cell(r,+i.getAttribute('data-filter')).toLowerCase().indexOf(v)<0)show=false;});")
				.append("r.style.display=show?'':'none';});}f.forEach(function(i){i.oninput=filter;});})();</script>");
	}

	private void addForm(StringBuilder echo, EntityManager em, Set<Integer> players)
	{
		echo.append("<div class='gfxbox' style='width:950px'><h3>Variable setzen</h3>");
		echo.append("<form action='./ds' method='post'>");
		hiddenFields(echo, getClass(), "set");
		echo.append("Gültigkeit: <select name='user'><option value='0'>global (für die ganze Kampagne)</option>");
		for (int id : players)
		{
			echo.append("<option value='").append(id).append("'>").append(esc(scope(em, id))).append("</option>");
		}
		echo.append("</select> Variable: <select name='name'>");
		SortedSet<String> known = variables.knownNames();
		for (String name : known)
		{
			echo.append("<option value='").append(esc(name)).append("'>").append(esc(name)).append("</option>");
		}
		echo.append("<option value='").append(NEW).append("'").append(known.isEmpty() ? " selected='selected'" : "").append(">neue Variable …</option></select> ")
				.append("<input type='text' name='newname' size='20' maxlength='64' placeholder='Name der neuen Variable' /> ");
		echo.append("Wert: <input type='text' name='value' size='8' value='0' /> <input type='submit' value='setzen' />");
		echo.append("</form><p class='campaign-hint'>Die Auswahl enthält alle Variablen, die gesetzt sind oder von einem Dialog gelesen oder geschrieben werden.</p></div>");
	}
}
