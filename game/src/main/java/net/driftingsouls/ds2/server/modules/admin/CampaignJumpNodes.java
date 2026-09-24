package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.Location;
import net.driftingsouls.ds2.server.WellKnownAdminPermission;
import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignJumpNodeState;
import net.driftingsouls.ds2.server.campaign.CampaignMapService;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.entities.JumpNode;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.pipeline.Request;

import javax.persistence.EntityManager;
import java.util.ArrayList;
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

		campaignNodes(echo, em, states, campaign);
		normalNodesIntoCampaign(echo, em, systems, states);
		createForm(echo);
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

	private void createForm(StringBuilder echo)
	{
		echo.append("<div class='gfxbox' style='width:900px'>");
		echo.append("<h3>Neuen Kampagnen-Sprungpunkt anlegen</h3>");
		echo.append("<form action='./ds' method='post'>");
		hiddenFields(echo, getClass(), "create");
		echo.append("Name: <input type='text' name='name' size='30' value='Kampagnenzugang' /><br />");
		echo.append("Von System <input type='text' name='sys' size='4' /> Position <input type='text' name='x' size='4' />/<input type='text' name='y' size='4' /><br />");
		echo.append("Nach System <input type='text' name='sysout' size='4' /> Position <input type='text' name='xout' size='4' />/<input type='text' name='yout' size='4' /><br />");
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
