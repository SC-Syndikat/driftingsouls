package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.WellKnownAdminPermission;
import net.driftingsouls.ds2.server.campaign.interaction.DialogService;
import net.driftingsouls.ds2.server.campaign.interaction.InteractionLogEntry;
import net.driftingsouls.ds2.server.campaign.interaction.InteractionRepository;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.pipeline.Request;

import javax.persistence.EntityManager;
import java.util.Map;

import static net.driftingsouls.ds2.server.modules.admin.CampaignAdminHtml.*;

/**
 * What players did with interactions.
 */
@AdminMenuEntry(category = "Kampagne", name = "Protokoll", permission = WellKnownAdminPermission.CAMPAIGN_LOG)
public class CampaignLog implements AdminPlugin
{
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
		Integer userId = optionalInt(request, "user");
		Integer interactionId = optionalInt(request, "interaction");
		Map<Integer, String> labels = new DialogService(em).interactionLabels();

		echo.append("<div class='gfxbox' style='width:950px'><h3>Protokoll der Interaktionen</h3>");
		echo.append("<form action='./ds' method='get'>");
		hiddenFields(echo, getClass(), "");
		echo.append("Spieler ");
		select(echo, "user", CampaignOptions.campaignParticipants(em, userId == null ? 0 : userId), userId, "alle");
		echo.append(" ");
		echo.append("Interaktion <select name='interaction'><option value=''>alle</option>");
		labels.forEach((id, label) -> echo.append("<option value='").append(id).append("'").append(id.equals(interactionId) ? " selected='selected'" : "")
				.append(">").append(id).append(": ").append(esc(label)).append("</option>"));
		echo.append("</select> <input type='submit' value='filtern' /></form><br />");

		echo.append("<table class='noBorderX' cellpadding='2'><tr><td class='noBorderX'>Zeit</td><td class='noBorderX'>Tick</td>")
				.append("<td class='noBorderX'>Spieler</td><td class='noBorderX'>Interaktion</td><td class='noBorderX'>Knoten</td>")
				.append("<td class='noBorderX'>Antwort</td><td class='noBorderX'>Chance</td><td class='noBorderX'>Ergebnis</td><td class='noBorderX'>Aktionen</td></tr>");
		for (InteractionLogEntry entry : new InteractionRepository().findLog(userId, interactionId, 300))
		{
			User user = em.find(User.class, entry.userId);
			echo.append("<tr><td class='noBorderX'>").append(esc(Common.date("d.m. H:i", entry.createdAt)))
					.append("</td><td class='noBorderX'>").append(entry.campaignTick)
					.append("</td><td class='noBorderX'>").append(user == null ? "?" : esc(user.getPlainname())).append(" (").append(entry.userId).append(")")
					.append("</td><td class='noBorderX'>").append(esc(labels.getOrDefault(entry.interactionId, "#" + entry.interactionId)))
					.append("</td><td class='noBorderX'>").append(entry.nodeId)
					.append("</td><td class='noBorderX'>").append(entry.isStart() ? "Start" : entry.optionId)
					.append("</td><td class='noBorderX'>").append(entry.chance == null ? "" : entry.chance + " %")
					.append("</td><td class='noBorderX'>").append(entry.isStart() ? "" : entry.success ? "Erfolg" : "<span class='error'>Misserfolg</span>")
					.append("</td><td class='noBorderX'>").append(esc(entry.actionsSummary)).append("</td></tr>");
		}
		echo.append("</table><br /><a class='forschinfo' href='").append(pluginUrl(CampaignVariables.class))
				.append("'>Kampagnenvariablen ansehen und bearbeiten</a></div><br />");
	}
}
