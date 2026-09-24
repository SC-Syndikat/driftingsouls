package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.WellKnownAdminPermission;
import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignMapService;
import net.driftingsouls.ds2.server.campaign.CampaignObject;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static net.driftingsouls.ds2.server.modules.admin.CampaignAdminHtml.*;

/**
 * Marks star systems as part of the campaign and switches their visibility on the star map.
 */
@AdminMenuEntry(category = "Kampagne", name = "Systeme", permission = WellKnownAdminPermission.CAMPAIGN_SYSTEMS)
public class CampaignSystems implements AdminPlugin
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

		String action = context.getRequest().getParameterString("cpaction");
		int systemId = context.getRequest().getParameterInt("system");
		boolean confirmed = "1".equals(context.getRequest().getParameterString("confirm"));
		try
		{
			switch (action)
			{
				case "add":
				{
					List<CampaignObject> foreign = mapService.findForeignObjects(campaign, systemId);
					if (!foreign.isEmpty() && !confirmed)
					{
						confirmAdd(echo, systemId, foreign);
						return;
					}
					mapService.addSystem(campaign, systemId);
					message(echo, "System " + systemId + " gehört jetzt zur Kampagne.");
					break;
				}
				case "remove":
					mapService.removeSystem(campaign, systemId);
					message(echo, "System " + systemId + " gehört nicht mehr zur Kampagne.");
					break;
				case "show":
				case "hide":
					mapService.setSystemVisible(systemId, "show".equals(action));
					message(echo, "System " + systemId + " ist jetzt " + ("show".equals(action) ? "sichtbar." : "versteckt."));
					break;
				default:
					break;
			}
		}
		catch (CampaignException e)
		{
			error(echo, e.getMessage());
		}

		systemList(echo, em, campaign, mapService);
	}

	private void confirmAdd(StringBuilder echo, int systemId, List<CampaignObject> foreign)
	{
		echo.append("<div class='gfxbox' style='width:700px'>");
		echo.append("<p class='error'>In System ").append(systemId).append(" befinden sich Objekte von Spielern, die nicht an der Kampagne teilnehmen. ")
				.append("Sie können dort nichts mehr tun, sobald die Kampagne läuft. Bereite das System entsprechend vor.</p>");
		objectTable(echo, foreign);
		actionButton(echo, getClass(), "add", "Trotzdem hinzufügen", "system", systemId, "confirm", 1);
		echo.append("<a class='forschinfo' href='").append(pluginUrl(getClass())).append("'>Abbrechen</a>");
		echo.append("</div>");
	}

	private void systemList(StringBuilder echo, EntityManager em, Campaign campaign, CampaignMapService mapService)
	{
		Set<Integer> campaignSystems = mapService.getSystemIds(campaign);
		List<CampaignObject> foreign = mapService.findForeignObjects(campaign);
		List<StarSystem> systems = em.createQuery("from StarSystem order by id", StarSystem.class).getResultList();

		echo.append("<div class='gfxbox' style='width:900px'>");
		echo.append("<h3>Systeme der Kampagne ").append(esc(campaign.getName())).append("</h3>");
		echo.append("<table class='noBorderX' cellpadding='3'><tr>")
				.append("<td class='noBorderX'>ID</td><td class='noBorderX'>Name</td><td class='noBorderX'>Zugriff</td>")
				.append("<td class='noBorderX'>Sternenkarte</td><td class='noBorderX'>Schlachten</td>")
				.append("<td class='noBorderX'>Kampagne</td><td class='noBorderX'>Hinweise</td><td class='noBorderX'></td></tr>");
		for (StarSystem system : systems)
		{
			boolean inCampaign = campaignSystems.contains(system.getID());
			long foreignCount = foreign.stream().filter(o -> o.starSystemId == system.getID()).count();

			echo.append("<tr><td class='noBorderX'>").append(system.getID()).append("</td>");
			echo.append("<td class='noBorderX'>").append(esc(system.getName())).append("</td>");
			echo.append("<td class='noBorderX'>").append(esc(system.getAccess())).append("</td>");
			echo.append("<td class='noBorderX'>").append(system.isStarmapVisible() ? "sichtbar" : "versteckt").append("</td>");
			echo.append("<td class='noBorderX'>").append(system.isBattleAllowed() ? "erlaubt" : "verboten").append("</td>");
			echo.append("<td class='noBorderX'>").append(inCampaign ? "<b>ja</b>" : "nein").append("</td>");

			echo.append("<td class='noBorderX'>");
			if (inCampaign)
			{
				if (system.getAccess() != StarSystem.Access.NORMAL)
				{
					echo.append("<span class='error'>Zugriff ist nicht NORMAL</span><br />");
				}
				if (!system.isBattleAllowed())
				{
					echo.append("<span class='error'>Schlachten sind verboten</span><br />");
				}
				if (foreignCount > 0)
				{
					echo.append("<span class='error'>").append(foreignCount).append(" fremde Objekte</span>");
				}
			}
			echo.append("</td><td class='noBorderX'>");
			if (inCampaign)
			{
				actionButton(echo, getClass(), "remove", "Entfernen", "system", system.getID());
			}
			else
			{
				actionButton(echo, getClass(), "add", "Hinzufügen", "system", system.getID());
			}
			if (system.isStarmapVisible())
			{
				actionButton(echo, getClass(), "hide", "Verstecken", "system", system.getID());
			}
			else
			{
				actionButton(echo, getClass(), "show", "Sichtbar machen", "system", system.getID());
			}
			echo.append("</td></tr>");
		}
		echo.append("</table>");

		if (!foreign.isEmpty())
		{
			echo.append("<h4>Objekte fremder Spieler in Kampagnensystemen</h4>");
			objectTable(echo, foreign);
		}
		echo.append("</div>");
	}

	static void objectTable(StringBuilder echo, List<CampaignObject> objects)
	{
		echo.append("<table class='noBorderX' cellpadding='2'><tr><td class='noBorderX'>Art</td><td class='noBorderX'>ID</td>")
				.append("<td class='noBorderX'>Name</td><td class='noBorderX'>Position</td><td class='noBorderX'>Besitzer</td></tr>");
		for (CampaignObject object : objects)
		{
			echo.append("<tr><td class='noBorderX'>").append(object.type.getLabel())
					.append("</td><td class='noBorderX'>").append(object.id)
					.append("</td><td class='noBorderX'>").append(esc(object.name))
					.append("</td><td class='noBorderX'>").append(object.getLocation())
					.append("</td><td class='noBorderX'>").append(esc(object.ownerName)).append(" (").append(object.ownerId).append(")</td></tr>");
		}
		echo.append("</table>");
	}
}
