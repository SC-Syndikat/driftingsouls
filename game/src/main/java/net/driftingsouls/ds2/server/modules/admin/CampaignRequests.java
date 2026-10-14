package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.WellKnownAdminPermission;
import net.driftingsouls.ds2.server.bases.Base;
import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.campaign.dialog.DialogAction;
import net.driftingsouls.ds2.server.campaign.dialog.DialogModel;
import net.driftingsouls.ds2.server.campaign.dialog.DialogRepository;
import net.driftingsouls.ds2.server.campaign.dialog.DialogTypes;
import net.driftingsouls.ds2.server.campaign.interaction.GmRequest;
import net.driftingsouls.ds2.server.campaign.interaction.GmRequestRepository;
import net.driftingsouls.ds2.server.campaign.interaction.GmRequestService;
import net.driftingsouls.ds2.server.campaign.interaction.Interaction;
import net.driftingsouls.ds2.server.campaign.interaction.InteractionRepository;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.pipeline.Request;
import net.driftingsouls.ds2.server.ships.Ship;

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
		User gamemaster = (User) context.getActiveUser();
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
				case "reply":
					service.gamemasterReply(gamemaster, requestId, request.getParameterString("text"));
					message(echo, "Antwort gesendet; der Spieler erhält zusätzlich eine PM.");
					break;
				case "runaction":
				{
					String type = request.getParameterString("ctype");
					DialogAction action = DialogTypes.get().action(type);
					String done = service.runAction(gamemaster, requestId, type, readParams(request, action.getParams()));
					message(echo, "Ausgeführt: " + done);
					break;
				}
				case "continue":
					service.continueDialog(gamemaster, requestId, request.getParameterInt("node"));
					message(echo, "Das Gespräch des Spielers steht jetzt bei Knoten " + request.getParameterInt("node") + ".");
					break;
				case "close":
					service.setStatus(gamemaster, requestId, GmRequest.Status.CLOSED);
					message(echo, "Anfrage abgeschlossen.");
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
				detail(echo, em, request, service.forGamemaster(requestId));
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

	private void detail(StringBuilder echo, EntityManager em, Request request, GmRequest gmRequest)
	{
		new GmRequestRepository().markReadByGm(gmRequest.id);
		User player = em.find(User.class, gmRequest.userId);
		Ship ship = em.find(Ship.class, gmRequest.shipId);
		Optional<Interaction> interaction = Optional.ofNullable(gmRequest.interactionId).flatMap(id -> new InteractionRepository().find(id));

		echo.append("<div class='gfxbox' style='width:950px'><h3>Anfrage ").append(gmRequest.id).append(" – ").append(esc(gmRequest.status.getLabel())).append("</h3>");
		echo.append("<table class='noBorderX'>");
		row(echo, "Spieler", player == null ? "?" : esc(player.getPlainname()) + " (" + gmRequest.userId + ")");
		row(echo, "Schiff des Spielers", ship == null ? "existiert nicht mehr"
				: esc(Common._plaintitle(ship.getName())) + " (" + ship.getId() + ") bei " + esc(ship.getLocation().displayCoordinates(false)));
		row(echo, "Gespräch", interaction.map(i -> esc(i.label) + " – " + esc(objectDescription(em, i))).orElse("Interaktion gelöscht"));
		new DialogRepository().findNode(gmRequest.nodeId == null ? 0 : gmRequest.nodeId)
				.ifPresent(node -> row(echo, "Beim Knoten", node.id + ": " + esc(node.title)));
		echo.append("</table>");
		if (interaction.isPresent())
		{
			Interaction i = interaction.get();
			echo.append("<p><a class='forschinfo' href='").append(CampaignInteractions.objectUrl(i.objectType, i.objectId)).append("'>Zum Objekt (Interaktionen)</a> · ")
					.append("<a class='forschinfo' href='").append(pluginUrl(CampaignInteractions.class)).append("&amp;preview=").append(i.id)
					.append("&amp;pplayer=").append(gmRequest.userId).append("&amp;pship=").append(gmRequest.shipId).append("'>Vorschau als dieser Spieler</a> · ")
					.append("<a class='forschinfo' href='").append(pluginUrl(CampaignLog.class)).append("&amp;user=").append(gmRequest.userId)
					.append("'>Protokoll des Spielers</a></p>");
		}

		echo.append("<h4>Unterhaltung</h4><div class='campaign-section'>");
		for (GmRequest.Message message : new GmRequestService(em).messages(gmRequest.id))
		{
			String who;
			switch (message.kind)
			{
				case PLAYER:
					who = "<span style='color:#aaaaaa'>Spieler</span>";
					break;
				case GAMEMASTER:
					who = "<span style='color:#66ccff'>GameMaster</span>";
					break;
				default:
					who = "<span style='color:#ffcc00'>→ für den Spieler</span>";
			}
			echo.append("<span class='campaign-hint'>").append(esc(Common.date("d.m. H:i", message.createdAt))).append("</span> ").append(who).append(": ")
					.append(esc(message.text).replace("\n", "<br />")).append("<br />");
		}
		echo.append("</div>");

		if (!gmRequest.isOpen())
		{
			actionButton(echo, getClass(), "reopen", "wieder öffnen", "request", gmRequest.id);
			echo.append("</div><br />");
			return;
		}

		echo.append("<form action='./ds' method='post'>");
		hiddenFields(echo, getClass(), "reply");
		hidden(echo, "request", gmRequest.id);
		echo.append("<textarea name='text' rows='3' cols='100'></textarea><br /><input type='submit' value='Antworten' /></form>");

		echo.append("<h4>Aktion für den Spieler ausführen</h4>");
		actionForm(echo, request, gmRequest);

		if (interaction.isPresent())
		{
			echo.append("<h4>Gespräch fortsetzen</h4><form action='./ds' method='post'>");
			hiddenFields(echo, getClass(), "continue");
			hidden(echo, "request", gmRequest.id);
			echo.append("bei Knoten <select name='node'>");
			for (DialogModel.Node node : new DialogRepository().findAll())
			{
				echo.append("<option value='").append(node.id).append("'").append(Integer.valueOf(node.id).equals(gmRequest.nodeId) ? " selected='selected'" : "")
						.append(">").append(node.id).append(": ").append(esc(node.title)).append("</option>");
			}
			echo.append("</select> <input type='submit' value='fortsetzen' /> <span class='campaign-hint'>Der Spieler sieht beim Schiff ")
					.append(gmRequest.shipId).append(" den gewählten Knoten mit seinen Antworten.</span></form>");
		}

		echo.append("<br />");
		actionButton(echo, getClass(), "close", "Anfrage abschließen", "request", gmRequest.id);
		echo.append("</div><br />");
	}

	/**
	 * First the action type, then its parameters, as in the dialog editor.
	 */
	private void actionForm(StringBuilder echo, Request request, GmRequest gmRequest)
	{
		DialogTypes types = DialogTypes.get();
		DialogAction chosen = types.getActions().get(request.getParameterString("ctype"));
		if (chosen == null || !request.getParameterString("cpaction").isEmpty())
		{
			echo.append("<form action='./ds' method='get'>");
			hiddenFields(echo, getClass(), "");
			hidden(echo, "request", gmRequest.id);
			echo.append("<select name='ctype'>");
			for (DialogAction.Group group : DialogAction.Group.values())
			{
				echo.append("<optgroup label='").append(esc(group.getLabel())).append("'>");
				types.getActions().forEach((id, a) -> {
					if (a.getGroup() == group)
					{
						echo.append("<option value='").append(esc(id)).append("'>").append(esc(a.getLabel())).append("</option>");
					}
				});
				echo.append("</optgroup>");
			}
			echo.append("</select> <input type='submit' value='weiter' /></form>");
			return;
		}
		echo.append("<form action='./ds' method='post' class='campaign-section' style='border-style:dotted'>");
		hiddenFields(echo, getClass(), "runaction");
		hidden(echo, "request", gmRequest.id);
		hidden(echo, "ctype", request.getParameterString("ctype"));
		echo.append("<b>").append(esc(chosen.getLabel())).append("</b><br />");
		paramFields(echo, chosen.getParams(), null);
		echo.append("<input type='submit' value='jetzt ausführen' /> <a class='forschinfo' href='").append(url(gmRequest)).append("'>abbrechen</a>")
				.append("<br /><span class='campaign-hint'>Läuft sofort für den Spieler und sein Schiff, wie eine Dialogantwort; ")
				.append("sichtbare Folgen erscheinen in der Unterhaltung.</span></form>");
	}

	private static void row(StringBuilder echo, String label, String value)
	{
		echo.append("<tr><td class='noBorderX'>").append(esc(label)).append("</td><td class='noBorderX'>").append(value).append("</td></tr>");
	}

	private static String interactionLabel(GmRequest request)
	{
		return Optional.ofNullable(request.interactionId).flatMap(id -> new InteractionRepository().find(id)).map(i -> i.label).orElse("?");
	}

	private static String objectDescription(EntityManager em, Interaction interaction)
	{
		switch (interaction.objectType)
		{
			case SHIP:
			{
				Ship ship = em.find(Ship.class, interaction.objectId);
				return ship == null ? "Schiff " + interaction.objectId + " (existiert nicht mehr)"
						: Common._plaintitle(ship.getName()) + " (" + ship.getId() + ") bei " + ship.getLocation().displayCoordinates(false);
			}
			case BASE:
			{
				Base base = em.find(Base.class, interaction.objectId);
				return base == null ? "Basis " + interaction.objectId + " (existiert nicht mehr)"
						: Common._plaintitle(base.getName()) + " (" + base.getId() + ") bei " + base.getLocation().displayCoordinates(false);
			}
			default:
			{
				StarSystem system = em.find(StarSystem.class, interaction.objectId);
				return "System " + (system == null ? "" : system.getName() + " ") + "(" + interaction.objectId + ")";
			}
		}
	}

	static String url(GmRequest request)
	{
		return pluginUrl(CampaignRequests.class) + "&amp;request=" + request.id;
	}
}
