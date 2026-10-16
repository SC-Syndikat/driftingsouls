package net.driftingsouls.ds2.server.modules.admin;

import com.google.gson.Gson;
import net.driftingsouls.ds2.server.Location;
import net.driftingsouls.ds2.server.WellKnownAdminPermission;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignText;
import net.driftingsouls.ds2.server.campaign.GameMasterRestriction;
import net.driftingsouls.ds2.server.campaign.ShipGroupRepository;
import net.driftingsouls.ds2.server.campaign.ShipGroupSpawner;
import net.driftingsouls.ds2.server.campaign.ShipGroupTemplate;
import net.driftingsouls.ds2.server.campaign.ShipGroupUnits;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.cargo.ResourceEntry;
import net.driftingsouls.ds2.server.config.Offiziere;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.entities.Offizier;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.pipeline.Request;
import net.driftingsouls.ds2.server.ships.Alarmstufe;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipType;
import net.driftingsouls.ds2.server.units.UnitType;
import org.apache.commons.lang3.StringUtils;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
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
		int editEntry = request.getParameterInt("editentry");

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
					repository.addEntry(templateId, readEntry(em, request, 0));
					message(echo, "Schiffe zur Vorlage hinzugefügt.");
					break;
				case "updateentry":
					repository.updateEntry(templateId, readEntry(em, request, request.getParameterInt("entry")));
					editEntry = 0;
					message(echo, "Eintrag gespeichert.");
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
			int entryToEdit = editEntry;
			repository.find(templateId).ifPresent(t -> templateDetails(echo, em, t, entryToEdit));
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

	/**
	 * Reads the entry form, checking cargo and units against the ship type.
	 */
	private static ShipGroupTemplate.Entry readEntry(EntityManager em, Request request, int entryId)
	{
		int shipTypeId = request.getParameterInt("shiptype");
		ShipType type = em.find(ShipType.class, shipTypeId);
		if (type == null)
		{
			throw new CampaignException("Unbekannter Schiffstyp.");
		}
		int count = Math.max(1, request.getParameterInt("count"));

		String cargo = request.getParameterString("cargo").trim();
		try
		{
			new Cargo(Cargo.Type.AUTO, cargo);
		}
		catch (RuntimeException e)
		{
			throw new CampaignException("Ungültiger Cargo: " + cargo);
		}

		Map<Integer, Long> units = ShipGroupUnits.parse(request.getParameterString("units"));
		ShipGroupUnits.check(units, unitSizes(em), type.getUnitSpace(), type.getMaxUnitSize());

		ShipGroupTemplate.Officer officer = null;
		if ("1".equals(request.getParameterString("officer")))
		{
			officer = new ShipGroupTemplate.Officer(
					request.getParameterInt("officer_nav"),
					request.getParameterInt("officer_ing"),
					request.getParameterInt("officer_waf"),
					request.getParameterInt("officer_sec"),
					request.getParameterInt("officer_com"),
					request.getParameterString("officer_special"));
		}

		return new ShipGroupTemplate.Entry(entryId, shipTypeId, count,
				request.getParameterString("namepattern").trim(),
				cargo,
				ShipGroupUnits.format(units),
				officer,
				request.getParameterInt("alarm"));
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

	private void templateDetails(StringBuilder echo, EntityManager em, ShipGroupTemplate template, int editEntry)
	{
		Map<Integer, ShipType> shipTypes = shipTypes(em);
		List<UnitType> unitTypes = em.createQuery("from UnitType order by name", UnitType.class).getResultList();

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
		ShipGroupTemplate.Entry edited = null;
		for (ShipGroupTemplate.Entry entry : template.entries)
		{
			if (entry.id == editEntry)
			{
				edited = entry;
			}
			ShipType type = shipTypes.get(entry.shipTypeId);
			echo.append("<tr><td class='noBorderX'>").append(esc(type == null ? "?" : CampaignText.plain(type.getNickname()))).append(" (").append(entry.shipTypeId).append(")")
					.append("</td><td class='noBorderX'>").append(entry.count)
					.append("</td><td class='noBorderX'>").append(esc(entry.namePattern))
					.append("</td><td class='noBorderX'>").append(esc(describeCargo(entry.cargo)))
					.append("</td><td class='noBorderX'>").append(esc(describeUnits(entry.units, unitTypes)))
					.append("</td><td class='noBorderX'>").append(esc(describeOfficer(entry.officer)))
					.append("</td><td class='noBorderX'>").append(esc(alarm(entry.alarm).name()))
					.append("</td><td class='noBorderX'>");
			echo.append("<a class='forschinfo' href='").append(pluginUrl(getClass())).append("&amp;template=").append(template.id)
					.append("&amp;editentry=").append(entry.id).append("#sgEntryForm'>bearbeiten</a> ");
			actionButton(echo, getClass(), "deleteentry", "entfernen", "template", template.id, "entry", entry.id);
			echo.append("</td></tr>");
		}
		echo.append("</table>");

		entryForm(echo, template, edited, shipTypes, unitTypes);
		spawnForm(echo, em, template);
		echo.append("</div><br />");
	}

	private void entryForm(StringBuilder echo, ShipGroupTemplate template, ShipGroupTemplate.Entry entry, Map<Integer, ShipType> shipTypes, List<UnitType> unitTypes)
	{
		echo.append("<h4>").append(entry == null ? "Schiffe hinzufügen" : "Eintrag bearbeiten").append("</h4>");
		echo.append("<form action='./ds' method='post' id='sgEntryForm'>");
		hiddenFields(echo, getClass(), entry == null ? "addentry" : "updateentry");
		hidden(echo, "template", template.id);
		if (entry != null)
		{
			hidden(echo, "entry", entry.id);
		}

		Map<Integer, String> typeOptions = new LinkedHashMap<>();
		shipTypes.forEach((id, type) -> typeOptions.put(id, CampaignText.plain(type.getNickname()) + " (" + id + ")"));
		echo.append("<div class='campaign-section'>Typ ");
		select(echo, "shiptype", typeOptions, entry == null ? null : entry.shipTypeId, null);
		echo.append(" <span class='campaign-hint sgShipTypeInfo'></span><br />");
		echo.append("Anzahl <input type='text' name='count' size='3' value='").append(entry == null ? 1 : entry.count).append("' /> ");
		echo.append("Name <input type='text' name='namepattern' size='20' placeholder='Wache {n}' value='").append(entry == null ? "" : esc(entry.namePattern)).append("' /> ");
		echo.append("<span class='campaign-hint'>{n} wird durch die laufende Nummer ersetzt</span><br />");
		echo.append("Alarm ");
		Map<Integer, String> alarms = new LinkedHashMap<>();
		for (Alarmstufe alarm : Alarmstufe.values())
		{
			alarms.put(alarm.ordinal(), alarm.name());
		}
		select(echo, "alarm", alarms, entry == null ? 0 : entry.alarm, null);
		echo.append("</div>");

		echo.append("<div class='campaign-section'><b>Cargo</b> <span class='campaign-hint'>leer = nur Treibstoff für zehn Flüge</span>");
		echo.append("<input type='hidden' name='cargo' id='sgCargo' value='").append(entry == null ? "" : esc(entry.cargo)).append("' /></div>");

		echo.append("<div class='campaign-section'><b>Einheiten</b> <span class='campaign-hint'>je Schiff</span>");
		echo.append("<input type='hidden' name='units' id='sgUnits' value='").append(entry == null ? "" : esc(entry.units)).append("' /></div>");

		officerFields(echo, entry == null ? null : entry.officer);

		echo.append("<input type='submit' value='").append(entry == null ? "Hinzufügen" : "Speichern").append("' />");
		if (entry != null)
		{
			echo.append(" <a class='forschinfo' href='").append(pluginUrl(getClass())).append("&amp;template=").append(template.id).append("'>abbrechen</a>");
		}
		echo.append("</form>");

		Gson gson = new Gson();
		Map<Integer, Map<String, Object>> typeData = new TreeMap<>();
		shipTypes.forEach((id, type) -> typeData.put(id, Map.of("cargo", type.getCargo(), "unitSpace", type.getUnitSpace(), "maxUnitSize", type.getMaxUnitSize())));
		List<Map<String, Object>> unitData = new ArrayList<>();
		for (UnitType unitType : unitTypes)
		{
			unitData.add(Map.of("id", unitType.getId(), "name", unitType.getName(), "size", unitType.getSize(), "picture", StringUtils.defaultString(unitType.getPicture())));
		}
		echo.append("<script type='text/javascript'>var sgShipTypes=").append(gson.toJson(typeData))
				.append(";var sgUnitTypes=").append(gson.toJson(unitData))
				.append(";var sgMaxRank=").append(Offiziere.MAX_RANG).append(";</script>");
		echo.append("<script src='./data/javascript/modules/admin.shipgroups.js' type='text/javascript'></script>");
	}

	private static void officerFields(StringBuilder echo, ShipGroupTemplate.Officer officer)
	{
		echo.append("<div class='campaign-section'><label><input type='checkbox' name='officer' value='1'")
				.append(officer != null ? " checked='checked'" : "").append(" /> <b>mit Offizier</b></label>")
				.append(" <span class='campaign-hint'>nur Schiffe ab Größe ").append(ShipType.SMALL_SHIP_MAXSIZE + 1)
				.append(" und Rettungskapseln haben einen Offizier</span>");
		echo.append("<div class='sgOfficerFields'>");
		for (Offizier.Ability ability : Offizier.Ability.values())
		{
			String name = ability.name().toLowerCase();
			echo.append(ability.name()).append(" <input type='text' name='officer_").append(name).append("' size='4' value='")
					.append(officer == null ? 0 : officer.getAbility(ability)).append("' /> ");
		}
		echo.append(" Rang <b class='sgOfficerRank'></b><br />");
		echo.append("Zufällig zwischen <input type='text' name='officer_min' size='4' value='0' /> und ")
				.append("<input type='text' name='officer_max' size='4' value='500' /> ")
				.append("<button type='button' class='sgOfficerRandom'>Zufällig</button><br />");
		Map<String, String> specials = new LinkedHashMap<>();
		specials.put("", "keine");
		specials.put(ShipGroupTemplate.Officer.RANDOM_SPECIAL, "zufällig, für jeden Offizier neu");
		for (Offizier.Special special : Offizier.Special.values())
		{
			if (special != Offizier.Special.NONE)
			{
				specials.put(special.name(), CampaignText.plain(special.getName()));
			}
		}
		echo.append("Spezialfähigkeit ");
		select(echo, "officer_special", specials, officer == null ? "" : officer.special, null);
		echo.append("</div></div>");
	}

	private void spawnForm(StringBuilder echo, EntityManager em, ShipGroupTemplate template)
	{
		echo.append("<h4>Jetzt erstellen</h4>");
		Map<Integer, String> owners = CampaignOptions.campaignNpcs(em);
		Map<Integer, String> systems = CampaignOptions.campaignSystems(em);
		if (owners.isEmpty() || systems.isEmpty())
		{
			echo.append("<p class='campaign-hint'>Zum Erstellen braucht die offene Kampagne teilnehmende NPCs und Kampagnensysteme.</p>");
			return;
		}
		echo.append("<form action='./ds' method='post'>");
		hiddenFields(echo, getClass(), "spawn");
		hidden(echo, "template", template.id);
		echo.append("Besitzer ");
		select(echo, "owner", owners, null, null);
		echo.append(" System ");
		select(echo, "system", systems, null, null);
		echo.append(" Position <input type='text' name='x' size='4' />/<input type='text' name='y' size='4' /> ")
				.append("<input type='submit' value='Erstellen' /></form>");
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

	private static String describeCargo(String cargo)
	{
		if (StringUtils.isBlank(cargo))
		{
			return "nur Treibstoff";
		}
		try
		{
			StringJoiner joiner = new StringJoiner(", ");
			for (ResourceEntry resource : new Cargo(Cargo.Type.AUTO, cargo).getResourceList())
			{
				joiner.add(resource.getCount1() + "× " + CampaignText.plain(resource.getName()));
			}
			return joiner.toString();
		}
		catch (RuntimeException e)
		{
			return cargo;
		}
	}

	private static String describeUnits(String units, List<UnitType> unitTypes)
	{
		if (StringUtils.isBlank(units))
		{
			return "-";
		}
		try
		{
			StringJoiner joiner = new StringJoiner(", ");
			ShipGroupUnits.parse(units).forEach((id, count) -> joiner.add(count + "× " + unitTypes.stream()
					.filter(t -> t.getId() == id)
					.map(t -> CampaignText.plain(t.getName()))
					.findFirst()
					.orElse("Typ " + id)));
			return joiner.toString();
		}
		catch (CampaignException e)
		{
			return units;
		}
	}

	private static String describeOfficer(ShipGroupTemplate.Officer officer)
	{
		if (officer == null)
		{
			return "-";
		}
		StringJoiner abilities = new StringJoiner("/");
		for (Offizier.Ability ability : Offizier.Ability.values())
		{
			abilities.add(Integer.toString(officer.getAbility(ability)));
		}
		String special = officer.special.isEmpty() ? ""
				: ShipGroupTemplate.Officer.RANDOM_SPECIAL.equals(officer.special) ? ", Spezial zufällig"
				: ", " + CampaignText.plain(Offizier.Special.valueOf(officer.special).getName());
		return abilities + " (Rang " + officer.getRank() + special + ")";
	}

	private static Alarmstufe alarm(int ordinal)
	{
		return Alarmstufe.values()[Math.max(0, Math.min(ordinal, Alarmstufe.values().length - 1))];
	}

	private static Map<Integer, ShipType> shipTypes(EntityManager em)
	{
		Map<Integer, ShipType> types = new TreeMap<>();
		for (ShipType type : em.createQuery("from ShipType order by id", ShipType.class).getResultList())
		{
			types.put(type.getId(), type);
		}
		return types;
	}

	private static Map<Integer, Integer> unitSizes(EntityManager em)
	{
		Map<Integer, Integer> sizes = new TreeMap<>();
		for (UnitType unitType : em.createQuery("from UnitType", UnitType.class).getResultList())
		{
			sizes.put(unitType.getId(), unitType.getSize());
		}
		return sizes;
	}
}
