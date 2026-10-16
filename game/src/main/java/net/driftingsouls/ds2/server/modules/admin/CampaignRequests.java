package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.WellKnownAdminPermission;
import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.campaign.GameMasterControl;
import net.driftingsouls.ds2.server.campaign.interaction.GmRequest;
import net.driftingsouls.ds2.server.campaign.interaction.GmRequestRepository;
import net.driftingsouls.ds2.server.campaign.interaction.GmRequestService;
import net.driftingsouls.ds2.server.campaign.interaction.InteractionRepository;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.pipeline.Request;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

import static net.driftingsouls.ds2.server.modules.admin.CampaignAdminHtml.*;

/**
 * Requests players wrote from a dialog: the conversation with the player, actions run for him, and
 * continuing his dialog at a chosen node.
 */
@AdminMenuEntry(category = "Kampagne", name = "Spieler-Anfragen", permission = WellKnownAdminPermission.CAMPAIGN_REQUESTS)
public class CampaignRequests implements AdminPlugin
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
		// While steering an NPC, the game master is the one behind the session
		User gamemaster = (User) GameMasterControl.realUser();
		GmRequestService service = new GmRequestService(em);

		Optional<Campaign> campaign = new CampaignService(em).findOpenCampaign();
		if (campaign.isEmpty())
		{
			noOpenCampaign(echo);
			return;
		}

		int requestId = request.getParameterInt("request");
		try
		{
			switch (request.getParameterString("cpaction"))
			{
				case "respond":
					message(echo, String.join(" · ", GmRequestPanel.handle(em, request, gamemaster, requestId)) + ".");
					break;
				case "reopen":
					service.setStatus(gamemaster, requestId, GmRequest.Status.OPEN);
					message(echo, "Anfrage wieder geöffnet.");
					break;
				default:
					break;
			}
		}
		catch (CampaignException e)
		{
			error(echo, e.getMessage());
		}

		if (requestId != 0)
		{
			try
			{
				detail(echo, em, service.forGamemaster(requestId));
			}
			catch (CampaignException e)
			{
				error(echo, e.getMessage());
			}
		}
		list(echo, em, campaign.get(), "1".equals(request.getParameterString("all")));
	}

	private void list(StringBuilder echo, EntityManager em, Campaign campaign, boolean all)
	{
		List<GmRequest> requests = new GmRequestRepository().findForCampaign(campaign.getId(), !all);
		echo.append("<div class='gfxbox' style='width:950px'><h3>Spieler-Anfragen</h3>");
		echo.append("<p class='campaign-hint'>Spieler schreiben sie aus einem Gespräch heraus, wenn keine der Antworten passt. ")
				.append(all ? "<a class='forschinfo' href='" + pluginUrl(getClass()) + "'>nur offene anzeigen</a>"
						: "<a class='forschinfo' href='" + pluginUrl(getClass()) + "&amp;all=1'>auch erledigte anzeigen</a>").append("</p>");
		if (requests.isEmpty())
		{
			echo.append("<p>Keine Anfragen.</p></div>");
			return;
		}
		echo.append("<table class='noBorderX'><tr><td class='noBorderX'>#</td><td class='noBorderX'>Spieler</td><td class='noBorderX'>Gespräch</td>")
				.append("<td class='noBorderX'>Anliegen</td><td class='noBorderX'>Letzte Aktivität</td><td class='noBorderX'>Zustand</td></tr>");
		for (GmRequest request : requests)
		{
			User player = em.find(User.class, request.userId);
			List<GmRequest.Message> messages = new GmRequestRepository().findMessages(request.id);
			String first = messages.isEmpty() ? "" : messages.get(0).text;
			echo.append("<tr><td class='noBorderX'><a class='forschinfo' href='").append(url(request)).append("'>").append(request.id).append("</a>")
					.append("</td><td class='noBorderX'>").append(player == null ? "?" : esc(player.getPlainname())).append(" (").append(request.userId).append(")")
					.append("</td><td class='noBorderX'>").append(esc(interactionLabel(request)))
					.append("</td><td class='noBorderX'>").append(esc(first.length() > 80 ? first.substring(0, 80) + "…" : first))
					.append("</td><td class='noBorderX'>").append(esc(Common.date("d.m. H:i", request.updatedAt)))
					.append("</td><td class='noBorderX'>").append(request.unreadByGm ? "<span class='error'>neu</span> " : "")
					.append(esc(request.status.getLabel())).append("</td></tr>");
		}
		echo.append("</table></div>");
	}

	private void detail(StringBuilder echo, EntityManager em, GmRequest gmRequest)
	{
		new GmRequestRepository().markReadByGm(gmRequest.id);
		echo.append("<div class='gfxbox' style='width:950px'><h3>Anfrage ").append(gmRequest.id).append(" – ").append(esc(gmRequest.status.getLabel()))
				.append(" – ").append(esc(interactionLabel(gmRequest))).append("</h3>");
		StringBuilder hidden = new StringBuilder();
		hiddenFields(hidden, getClass(), "respond");
		hidden(hidden, "request", gmRequest.id);
		GmRequestPanel.render(echo, em, gmRequest, "./ds", hidden.toString());
		if (!gmRequest.isOpen())
		{
			actionButton(echo, getClass(), "reopen", "wieder öffnen", "request", gmRequest.id);
		}
		echo.append("<p><a class='forschinfo' href='").append(pluginUrl(CampaignLog.class)).append("&amp;user=").append(gmRequest.userId)
				.append("'>Protokoll des Spielers</a></p></div><br />");
	}

	private static String interactionLabel(GmRequest request)
	{
		if (request.interactionId == null && request.battleId != null)
		{
			return "Schlacht " + request.battleId;
		}
		return Optional.ofNullable(request.interactionId).flatMap(id -> new InteractionRepository().find(id)).map(i -> i.label)
				.orElse(request.interactionId == null ? "Schlacht (beendet)" : "?");
	}

	static String url(GmRequest request)
	{
		return pluginUrl(CampaignRequests.class) + "&amp;request=" + request.id;
	}
}
