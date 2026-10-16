package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.campaign.CampaignText;
import net.driftingsouls.ds2.server.Location;
import net.driftingsouls.ds2.server.WellKnownAdminPermission;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.GameMasterRestriction;
import net.driftingsouls.ds2.server.campaign.ShipGroupRepository;
import net.driftingsouls.ds2.server.campaign.ShipGroupSpawner;
import net.driftingsouls.ds2.server.campaign.ShipGroupTemplate;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.pipeline.Request;
import net.driftingsouls.ds2.server.ships.Alarmstufe;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipType;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static net.driftingsouls.ds2.server.modules.admin.CampaignAdminHtml.*;

/**
 * Reusable NPC fleets: defined once, spawned by the game master or by dialog actions.
 */
@AdminMenuEntry(category = "Kampagne", name = "Schiffsgruppen", permission = WellKnownAdminPermission.CAMPAIGN_SHIPGROUPS)
public class CampaignShipGroups implements AdminPlugin
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
		ShipGroupRepository repository = new ShipGroupRepository();
		int templateId = request.getParameterInt("template");

		try
		{
			switch (request.getParameterString("cpaction"))
			{
				case "create":
					templateId = repository.create(requireName(request), request.getParameterString("description"), "1".equals(request.getParameterString("fleet")));
					message(echo, "Vorlage angelegt.");
					break;
				case "update":
					repository.update(templateId, requireName(request), request.getParameterString("description"), "1".equals(request.getParameterString("fleet")));
					message(echo, "Vorlage gespeichert.");
					break;
				case "delete":
					repository.delete(templateId);
					templateId = 0;
					message(echo, "Vorlage gelöscht.");
					break;
				case "addentry":
					addEntry(em, request, repository, templateId);
					message(echo, "Schiffe zur Vorlage hinzugefügt.");
					break;
				case "deleteentry":
					repository.deleteEntry(templateId, request.getParameterInt("entry"));
					message(echo, "Eintrag entfernt.");
					break;
				case "spawn":
					spawn(echo, em, request, repository, templateId);
					break;
				default:
					break;
			}
		}
		catch (CampaignException e)
		{
			error(echo, e.getMessage());
		}
		catch (RuntimeException e)
		{
			error(echo, "Fehler: " + e.getMessage());
		}

		if (templateId != 0)
		{
			repository.find(templateId).ifPresent(t -> templateDetails(echo, em, t));
		}
		templateList(echo, repository.findAll());
	}

	private static String requireName(Request request)
	{
		String name = request.getParameterString("name").trim();
		if (name.isEmpty())
		{
			throw new CampaignException("Die Vorlage braucht einen Namen.");
		}
		return name;
	}

	private static void addEntry(EntityManager em, Request request, ShipGroupRepository repository, int templateId)
	{
		int shipTypeId = request.getParameterInt("shiptype");
		if (em.find(ShipType.class, shipTypeId) == null)
		{
			throw new CampaignException("Unbekannter Schiffstyp.");
		}
		int count = Math.max(1, request.getParameterInt("count"));
		String officer = request.getParameterString("officer").trim();
		repository.addEntry(templateId, new ShipGroupTemplate.Entry(0, shipTypeId, count,
				request.getParameterString("namepattern").trim(),
				request.getParameterString("cargo").trim(),
				request.getParameterString("units").trim(),
				officer.isEmpty() ? null : Integer.parseInt(officer),
				request.getParameterInt("alarm")));
	}

	private static void spawn(StringBuilder echo, EntityManager em, Request request, ShipGroupRepository repository, int templateId)
	{
		ShipGroupTemplate template = repository.find(templateId).orElseThrow(() -> new CampaignException("Unbekannte Vorlage."));
		int ownerId = request.getParameterInt("owner");
		Location location = new Location(request.getParameterInt("system"), request.getParameterInt("x"), request.getParameterInt("y"));
		GameMasterRestriction.forActiveUser().check(location.getSystem(), ownerId);

		User owner = em.find(User.class, ownerId);
		if (owner == null)
		{
			throw new CampaignException("Unbekannter Besitzer: " + ownerId);
		}
		StarSystem system = em.find(StarSystem.class, location.getSystem());
		if (system == null || location.getX() < 1 || location.getY() < 1 || location.getX() > system.getWidth() || location.getY() > system.getHeight())
		{
			throw new CampaignException("Die Position liegt nicht in einem Sternensystem.");
		}

		List<Ship> ships = new ShipGroupSpawner(em).spawn(template, owner, location);
		echo.append("<div class='gfxbox' style='width:590px'>").append(ships.size()).append(" Schiff(e) bei ")
				.append(location.displayCoordinates(false)).append(" erstellt: ");
		for (Ship ship : ships)
		{
			echo.append("<a class='forschinfo' href='./ds?module=schiff&amp;ship=").append(ship.getId()).append("'>")
					.append(esc(ship.getName())).append(" (").append(ship.getId()).append(")</a> ");
		}
		echo.append("</div><br />");
	}

	private void templateDetails(StringBuilder echo, EntityManager em, ShipGroupTemplate template)
	{
		Map<Integer, String> shipTypes = shipTypes(em);

		echo.append("<div class='gfxbox' style='width:900px'>");
		echo.append("<h3>Vorlage ").append(esc(template.name)).append("</h3>");
		echo.append("<form action='./ds' method='post'>");
		hiddenFields(echo, getClass(), "update");
		hidden(echo, "template", template.id);
		echo.append("Name: <input type='text' name='name' size='30' value='").append(esc(template.name)).append("' /> ");
		echo.append("<label><input type='checkbox' name='fleet' value='1'").append(template.createFleet ? " checked='checked'" : "").append(" /> als Flotte</label><br />");
		echo.append("Beschreibung:<br /><textarea name='description' rows='3' cols='80'>").append(esc(template.description)).append("</textarea><br />");
		echo.append("<input type='submit' value='Speichern' /></form>");
		actionButton(echo, getClass(), "delete", "Vorlage löschen", "template", template.id);

		echo.append("<h4>Schiffe</h4><table class='noBorderX' cellpadding='3'><tr><td class='noBorderX'>Typ</td><td class='noBorderX'>Anzahl</td>")
				.append("<td class='noBorderX'>Name</td><td class='noBorderX'>Cargo</td><td class='noBorderX'>Einheiten</td>")
				.append("<td class='noBorderX'>Offizier</td><td class='noBorderX'>Alarm</td><td class='noBorderX'></td></tr>");
		for (ShipGroupTemplate.Entry entry : template.entries)
		{
			echo.append("<tr><td class='noBorderX'>").append(esc(shipTypes.getOrDefault(entry.shipTypeId, "?"))).append(" (").append(entry.shipTypeId).append(")")
					.append("</td><td class='noBorderX'>").append(entry.count)
					.append("</td><td class='noBorderX'>").append(esc(entry.namePattern))
					.append("</td><td class='noBorderX'>").append(esc(entry.cargo))
					.append("</td><td class='noBorderX'>").append(esc(entry.units))
					.append("</td><td class='noBorderX'>").append(entry.officerSkill == null ? "-" : entry.officerSkill.toString())
					.append("</td><td class='noBorderX'>").append(esc(Alarmstufe.values()[Math.min(entry.alarm, Alarmstufe.values().length - 1)].name()))
					.append("</td><td class='noBorderX'>");
			actionButton(echo, getClass(), "deleteentry", "entfernen", "template", template.id, "entry", entry.id);
			echo.append("</td></tr>");
		}
		echo.append("</table>");

		echo.append("<form action='./ds' method='post'>");
		hiddenFields(echo, getClass(), "addentry");
		hidden(echo, "template", template.id);
		echo.append("Typ <select name='shiptype'>");
		shipTypes.forEach((id, name) -> echo.append("<option value='").append(id).append("'>").append(esc(name)).append(" (").append(id).append(")</option>"));
		echo.append("</select> Anzahl <input type='text' name='count' size='3' value='1' /> ");
		echo.append("Name <input type='text' name='namepattern' size='20' placeholder='Wache {n}' /><br />");
		echo.append("Cargo <input type='text' name='cargo' size='40' placeholder='leer = nur Treibstoff' /> ");
		echo.append("Einheiten <input type='text' name='units' size='15' placeholder='typ:anzahl|typ:anzahl' /><br />");
		echo.append("Offizier-Fähigkeiten <input type='text' name='officer' size='4' placeholder='leer = keiner' /> ");
		echo.append("Alarm <select name='alarm'>");
		for (Alarmstufe alarm : Alarmstufe.values())
		{
			echo.append("<option value='").append(alarm.ordinal()).append("'>").append(esc(alarm.name())).append("</option>");
		}
		echo.append("</select> <input type='submit' value='Hinzufügen' /></form>");

		echo.append("<h4>Jetzt erstellen</h4><form action='./ds' method='post'>");
		hiddenFields(echo, getClass(), "spawn");
		hidden(echo, "template", template.id);
		echo.append("Besitzer (NPC-ID) <input type='text' name='owner' size='6' /> bei System <input type='text' name='system' size='4' /> ")
				.append("Position <input type='text' name='x' size='4' />/<input type='text' name='y' size='4' /> ")
				.append("<input type='submit' value='Erstellen' /></form>");
		echo.append("</div><br />");
	}

	private void templateList(StringBuilder echo, List<ShipGroupTemplate> templates)
	{
		echo.append("<div class='gfxbox' style='width:900px'><h3>Schiffsgruppen-Vorlagen</h3><ul>");
		for (ShipGroupTemplate template : templates)
		{
			echo.append("<li><a class='forschinfo' href='").append(pluginUrl(getClass())).append("&amp;template=").append(template.id).append("'>")
					.append(esc(template.name)).append("</a> – ").append(template.getShipCount()).append(" Schiff(e)</li>");
		}
		echo.append("</ul><form action='./ds' method='post'>");
		hiddenFields(echo, getClass(), "create");
		echo.append("Neue Vorlage: <input type='text' name='name' size='30' /> ");
		echo.append("<label><input type='checkbox' name='fleet' value='1' checked='checked' /> als Flotte</label> ");
		echo.append("<input type='hidden' name='description' value='' /><input type='submit' value='Anlegen' /></form></div>");
	}

	private static Map<Integer, String> shipTypes(EntityManager em)
	{
		Map<Integer, String> types = new TreeMap<>();
		for (ShipType type : em.createQuery("from ShipType order by id", ShipType.class).getResultList())
		{
			types.put(type.getId(), CampaignText.plain(type.getNickname()));
		}
		return types;
	}
}
