package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.Location;
import net.driftingsouls.ds2.server.WellKnownAdminPermission;
import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignJumpNodeLimitService;
import net.driftingsouls.ds2.server.campaign.JumpNodeLimits;
import net.driftingsouls.ds2.server.campaign.CampaignJumpNodeState;
import net.driftingsouls.ds2.server.campaign.CampaignMapService;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.campaign.dialog.ParamChoices;
import net.driftingsouls.ds2.server.campaign.dialog.ParamDef;
import net.driftingsouls.ds2.server.entities.JumpNode;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.pipeline.Request;
import net.driftingsouls.ds2.server.ships.ShipClasses;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static net.driftingsouls.ds2.server.modules.admin.CampaignAdminHtml.*;

/**
 * The temporary accesses into the campaign's star systems. They are prepared invisibly, can be shown
 * to players ahead of time, become usable when the campaign starts and are reset when it ends.
 */
@AdminMenuEntry(category = "Kampagne", name = "Sprungpunkte", permission = WellKnownAdminPermission.CAMPAIGN_JUMPNODES)
public class CampaignJumpNodes implements AdminPlugin
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
		Optional<Campaign> open = new CampaignService(em).findOpenCampaign();
		if (open.isEmpty())
		{
			noOpenCampaign(echo);
			return;
		}
		Campaign campaign = open.get();
		CampaignMapService mapService = new CampaignMapService(em);
		Request request = context.getRequest();

		try
		{
			switch (request.getParameterString("cpaction"))
			{
				case "create":
				{
					Location source = new Location(request.getParameterInt("sys"), request.getParameterInt("x"), request.getParameterInt("y"));
					Location target = new Location(request.getParameterInt("sysout"), request.getParameterInt("xout"), request.getParameterInt("yout"));
					String name = request.getParameterString("name").trim();
					List<JumpNode> created = mapService.createJumpNode(campaign, source, target, name.isEmpty() ? "Kampagnenzugang" : name,
							"1".equals(request.getParameterString("withreturn")));
					message(echo, created.size() + " Sprungpunkt(e) angelegt, Zustand: vorbereitet.");
					break;
				}
				case "register":
					mapService.registerJumpNode(campaign, request.getParameterInt("node"));
					message(echo, "Sprungpunkt " + request.getParameterInt("node") + " ist jetzt ein Kampagnen-Sprungpunkt (vorbereitet).");
					break;
				case "unregister":
					mapService.unregisterJumpNode(request.getParameterInt("node"));
					message(echo, "Sprungpunkt " + request.getParameterInt("node") + " ist wieder ein normaler Sprungpunkt.");
					break;
				case "savelimits":
				{
					int node = request.getParameterInt("node");
					Set<String> classes = new LinkedHashSet<>();
					for (ShipClasses shipClass : ShipClasses.values())
					{
						if ("1".equals(request.getParameterString("class_" + shipClass.name())))
						{
							classes.add(shipClass.name());
						}
					}
					Set<Integer> types = multiSelectIds(request, "types");
					new CampaignJumpNodeLimitService(em).save(node, new JumpNodeLimits(classes, types,
							optionalInt(request, "pership"), optionalInt(request, "perplayer"),
							JumpNodeLimits.Period.valueOf(request.getParameterString("period")),
							JumpNodeLimits.Metric.valueOf(request.getParameterString("metric"))));
					message(echo, "Limits von Sprungpunkt " + node + " gespeichert.");
					break;
				}
				case "state":
				{
					CampaignJumpNodeState state = CampaignJumpNodeState.valueOf(request.getParameterString("state"));
					mapService.setJumpNodeState(request.getParameterInt("node"), state);
					message(echo, "Sprungpunkt " + request.getParameterInt("node") + " ist jetzt " + state.getLabel() + ".");
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
		catch (IllegalArgumentException e)
		{
			error(echo, "Unbekannter Zustand.");
		}

		Set<Integer> systems = mapService.getSystemIds(campaign);
		Map<Integer, CampaignJumpNodeState> states = mapService.getJumpNodeStates();

		if (request.getParameterInt("limits") != 0 && states.containsKey(request.getParameterInt("limits")))
		{
			limitsForm(echo, em, campaign, request.getParameterInt("limits"));
		}
		campaignNodes(echo, em, states, campaign);
		normalNodesIntoCampaign(echo, em, systems, states);
		createForm(echo, em);
	}

	private void limitsForm(StringBuilder echo, EntityManager em, Campaign campaign, int nodeId)
	{
		CampaignJumpNodeLimitService service = new CampaignJumpNodeLimitService(em);
		JumpNodeLimits limits = service.load(nodeId);
		echo.append("<div class='gfxbox' style='width:900px'><h3>Limits von Sprungpunkt ").append(nodeId).append("</h3>");
		echo.append("<p class='campaign-hint'>Gilt für Spieler; NPCs und GameMaster sind ausgenommen. Geprüft wird der ganze Verband ")
				.append("(Flotte und gedockte Schiffe). Leere Felder bedeuten: keine Beschränkung. Dialoge können die Limits mit der Aktion ")
				.append("„Limits eines Kampagnen-Sprungpunkts ändern“ anpassen.</p>");
		echo.append("<form action='./ds' method='post'>");
		hiddenFields(echo, getClass(), "savelimits");
		hidden(echo, "node", nodeId);
		hidden(echo, "limits", nodeId);
		echo.append("<b>Erlaubte Schiffsklassen</b> (keine angekreuzt = alle):<br />");
		for (ShipClasses shipClass : ShipClasses.values())
		{
			echo.append("<label style='display:inline-block; min-width:170px'><input type='checkbox' name='class_").append(shipClass.name()).append("' value='1'")
					.append(limits.allowedShipClasses.contains(shipClass.name()) ? " checked='checked'" : "").append(" /> ")
					.append(esc(shipClass.getSingular())).append("</label>");
		}
		echo.append("<br /><br />Erlaubte Schiffstypen (keiner gewählt = alle):<br />");
		multiSelect(echo, "types", ParamChoices.forCurrentRequest().get(ParamDef.Type.SHIP_TYPES), limits.allowedShipTypes);
		echo.append("<br />");
		echo.append("Max. Punkte je Schiff: <input type='text' name='pership' size='6' value='").append(limits.maxPointsPerShip == null ? "" : limits.maxPointsPerShip).append("' /> ");
		echo.append("Max. Punkte je Spieler: <input type='text' name='perplayer' size='6' value='").append(limits.maxPointsPerPlayer == null ? "" : limits.maxPointsPerPlayer).append("' /> ");
		echo.append("<select name='period'>");
		for (JumpNodeLimits.Period period : JumpNodeLimits.Period.values())
		{
			echo.append("<option value='").append(period.name()).append("'").append(period == limits.playerLimitPeriod ? " selected='selected'" : "").append(">")
					.append(esc(period.getLabel())).append("</option>");
		}
		echo.append("</select><br />Gezählt wird: <select name='metric'>");
		for (JumpNodeLimits.Metric metric : JumpNodeLimits.Metric.values())
		{
			echo.append("<option value='").append(metric.name()).append("'").append(metric == limits.metric ? " selected='selected'" : "").append(">")
					.append(esc(metric.getLabel())).append("</option>");
		}
		echo.append("</select><br /><input type='submit' value='Limits speichern' /></form>");

		if (limits.maxPointsPerPlayer != null && campaign.isRunning())
		{
			echo.append("<h4>Verbraucht ").append(esc(limits.playerLimitPeriod.getLabel())).append("</h4><ul>");
			for (int userId : new CampaignService(em).getParticipantIds(campaign))
			{
				long used = service.usedPoints(nodeId, campaign, userId, limits.playerLimitPeriod);
				if (used > 0)
				{
					echo.append("<li>Spieler ").append(userId).append(": ").append(used).append(" von ").append(limits.maxPointsPerPlayer).append("</li>");
				}
			}
			echo.append("</ul>");
		}
		echo.append("</div>");
	}

	private void campaignNodes(StringBuilder echo, EntityManager em, Map<Integer, CampaignJumpNodeState> states, Campaign campaign)
	{
		echo.append("<div class='gfxbox' style='width:900px'>");
		echo.append("<h3>Kampagnen-Sprungpunkte</h3>");
		echo.append("<p class='smallfont'>Vorbereitet: unsichtbar und gesperrt. Sichtbar: Spieler sehen ihn, können aber noch nicht springen. ")
				.append("Aktiv: benutzbar. Beim Kampagnenstart werden alle aktiv, beim Ende wieder vorbereitet.")
				.append(campaign.isRunning() ? " <b>Die Kampagne läuft.</b>" : "").append("</p>");
		if (states.isEmpty())
		{
			echo.append("<p>Noch keine Kampagnen-Sprungpunkte.</p></div><br />");
			return;
		}
		nodeTableHeader(echo, true);
		for (JumpNode node : load(em, states.keySet()))
		{
			CampaignJumpNodeState state = states.get(node.getId());
			nodeRow(echo, node);
			echo.append("<td class='noBorderX'><b>").append(state.getLabel()).append("</b></td><td class='noBorderX'>");
			for (CampaignJumpNodeState other : CampaignJumpNodeState.values())
			{
				if (other != state)
				{
					actionButton(echo, getClass(), "state", other.getLabel(), "node", node.getId(), "state", other.name());
				}
			}
			actionButton(echo, getClass(), "unregister", "normal machen", "node", node.getId());
			echo.append("<br /><span class='campaign-hint'>Limits: ").append(esc(new CampaignJumpNodeLimitService(em).load(node.getId()).describe()))
					.append("</span> <a class='forschinfo' href='").append(pluginUrl(getClass())).append("&amp;limits=").append(node.getId()).append("'>Limits bearbeiten</a>");
			echo.append("</td></tr>");
		}
		echo.append("</table></div><br />");
	}

	private void normalNodesIntoCampaign(StringBuilder echo, EntityManager em, Set<Integer> systems, Map<Integer, CampaignJumpNodeState> states)
	{
		if (systems.isEmpty())
		{
			return;
		}
		List<JumpNode> nodes = new ArrayList<>();
		for (JumpNode node : em.createQuery("from JumpNode where system in (:systems) or systemOut in (:systems) order by id", JumpNode.class)
				.setParameter("systems", systems)
				.getResultList())
		{
			if (!states.containsKey(node.getId()) && systems.contains(node.getSystemOut()) != systems.contains(node.getSystem()))
			{
				nodes.add(node);
			}
		}
		if (nodes.isEmpty())
		{
			return;
		}
		echo.append("<div class='gfxbox' style='width:900px'>");
		echo.append("<h3>Normale Sprungpunkte in oder aus Kampagnensystemen</h3>");
		echo.append("<p class='error'>Über diese Sprungpunkte gelangt man jederzeit in Kampagnensysteme bzw. aus ihnen heraus. ")
				.append("Übernimm sie als Kampagnen-Sprungpunkte oder entferne sie, falls das nicht gewollt ist.</p>");
		nodeTableHeader(echo, false);
		for (JumpNode node : nodes)
		{
			nodeRow(echo, node);
			echo.append("<td class='noBorderX'>").append(node.isHidden() ? "versteckt" : "sichtbar").append("</td><td class='noBorderX'>");
			actionButton(echo, getClass(), "register", "als Kampagnen-Sprungpunkt übernehmen", "node", node.getId());
			echo.append("</td></tr>");
		}
		echo.append("</table></div><br />");
	}

	private void createForm(StringBuilder echo, EntityManager em)
	{
		Map<String, Map<Integer, String>> systems = CampaignOptions.allSystemsCampaignFirst(em);
		echo.append("<div class='gfxbox' style='width:900px'>");
		echo.append("<h3>Neuen Kampagnen-Sprungpunkt anlegen</h3>");
		echo.append("<form action='./ds' method='post'>");
		hiddenFields(echo, getClass(), "create");
		echo.append("Name: <input type='text' name='name' size='30' value='Kampagnenzugang' /><br />");
		echo.append("Von System ");
		groupedSelect(echo, "sys", systems, null);
		echo.append(" Position <input type='text' name='x' size='4' />/<input type='text' name='y' size='4' /><br />");
		echo.append("Nach System ");
		groupedSelect(echo, "sysout", systems, null);
		echo.append(" Position <input type='text' name='xout' size='4' />/<input type='text' name='yout' size='4' /><br />");
		echo.append("<label><input type='checkbox' name='withreturn' value='1' checked='checked' /> Rückweg ebenfalls anlegen</label><br />");
		echo.append("<input type='submit' value='Anlegen' />");
		echo.append("</form></div>");
	}

	private static List<JumpNode> load(EntityManager em, Set<Integer> ids)
	{
		return em.createQuery("from JumpNode where id in (:ids) order by id", JumpNode.class)
				.setParameter("ids", ids)
				.getResultList();
	}

	private static void nodeTableHeader(StringBuilder echo, boolean campaign)
	{
		echo.append("<table class='noBorderX' cellpadding='3'><tr><td class='noBorderX'>ID</td><td class='noBorderX'>Name</td>")
				.append("<td class='noBorderX'>Von</td><td class='noBorderX'>Nach</td><td class='noBorderX'>")
				.append(campaign ? "Zustand" : "Sichtbarkeit").append("</td><td class='noBorderX'></td></tr>");
	}

	private static void nodeRow(StringBuilder echo, JumpNode node)
	{
		echo.append("<tr><td class='noBorderX'>").append(node.getId())
				.append("</td><td class='noBorderX'>").append(esc(node.getName()))
				.append("</td><td class='noBorderX'>").append(node.getSystem()).append(":").append(node.getX()).append("/").append(node.getY())
				.append("</td><td class='noBorderX'>").append(node.getSystemOut()).append(":").append(node.getXOut()).append("/").append(node.getYOut())
				.append("</td>");
	}
}
