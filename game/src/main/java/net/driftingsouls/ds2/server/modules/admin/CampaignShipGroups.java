package net.driftingsouls.ds2.server.modules.admin;

import com.google.gson.Gson;
import net.driftingsouls.ds2.server.Location;
import net.driftingsouls.ds2.server.WellKnownAdminPermission;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignText;
import net.driftingsouls.ds2.server.campaign.GameMasterRestriction;
import net.driftingsouls.ds2.server.campaign.ShipGroupLimits;
import net.driftingsouls.ds2.server.campaign.ShipGroupModules;
import net.driftingsouls.ds2.server.campaign.ShipGroupRepository;
import net.driftingsouls.ds2.server.campaign.ShipGroupSpawner;
import net.driftingsouls.ds2.server.campaign.ShipGroupTemplate;
import net.driftingsouls.ds2.server.campaign.ShipGroupUnits;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.cargo.ItemCargoEntry;
import net.driftingsouls.ds2.server.cargo.ItemData;
import net.driftingsouls.ds2.server.cargo.ResourceID;
import net.driftingsouls.ds2.server.cargo.ResourceEntry;
import net.driftingsouls.ds2.server.config.ModuleSlot;
import net.driftingsouls.ds2.server.config.ModuleSlots;
import net.driftingsouls.ds2.server.config.NoSuchSlotException;
import net.driftingsouls.ds2.server.config.Offiziere;
import net.driftingsouls.ds2.server.config.items.Item;
import net.driftingsouls.ds2.server.config.items.Schiffsmodul;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.entities.Offizier;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.pipeline.Request;
import net.driftingsouls.ds2.server.ships.Alarmstufe;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipType;
import net.driftingsouls.ds2.server.ships.ShipTypeData;
import net.driftingsouls.ds2.server.units.UnitType;
import org.apache.commons.lang3.StringUtils;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.TreeSet;

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
				{
					EntryInput input = readEntry(em, request, 0);
					repository.addEntry(templateId, input.entry);
					message(echo, "Schiffe zur Vorlage hinzugefügt.");
					warnings(echo, input.warnings);
					break;
				}
				case "updateentry":
				{
					EntryInput input = readEntry(em, request, request.getParameterInt("entry"));
					repository.updateEntry(templateId, input.entry);
					editEntry = 0;
					message(echo, "Eintrag gespeichert.");
					warnings(echo, input.warnings);
					break;
				}
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
	 * An entry read from the form, and what had to be taken out of it to fit the ship.
	 */
	private static final class EntryInput
	{
		final ShipGroupTemplate.Entry entry;
		final List<String> warnings;

		EntryInput(ShipGroupTemplate.Entry entry, List<String> warnings)
		{
			this.entry = entry;
			this.warnings = warnings;
		}
	}

	private static void warnings(StringBuilder echo, List<String> warnings)
	{
		if (warnings.isEmpty())
		{
			return;
		}
		echo.append("<div class='gfxbox' style='width:700px'><span class='error'>Der Schiffstyp hat nicht genug Platz, gespeichert wurde:</span><ul>");
		for (String warning : warnings)
		{
			echo.append("<li>").append(esc(warning)).append("</li>");
		}
		echo.append("</ul></div><br />");
	}

	/**
	 * Reads the entry form. Modules have to fit their slots; cargo and units that do not fit the ship,
	 * modules included, are cut from the last entry on.
	 */
	private static EntryInput readEntry(EntityManager em, Request request, int entryId)
	{
		int shipTypeId = request.getParameterInt("shiptype");
		ShipType type = em.find(ShipType.class, shipTypeId);
		if (type == null)
		{
			throw new CampaignException("Unbekannter Schiffstyp.");
		}
		int count = Math.max(1, request.getParameterInt("count"));

		Map<Integer, Integer> modules = new LinkedHashMap<>();
		for (int slot : ShipGroupModules.slots(type.getTypeModules()).keySet())
		{
			int item = request.getParameterInt("module_" + slot);
			if (item != 0)
			{
				modules.put(slot, item);
			}
		}
		ShipGroupModules.check(type, modules, em);
		ShipTypeData stats = ShipGroupModules.apply(type, modules);

		List<String> warnings = new ArrayList<>();
		String cargo = fitCargo(request.getParameterString("cargo").trim(), stats.getCargo(), warnings);
		String units = fitUnits(em, ShipGroupUnits.parse(request.getParameterString("units")), stats, warnings);

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

		return new EntryInput(new ShipGroupTemplate.Entry(entryId, shipTypeId, count,
				request.getParameterString("namepattern").trim(),
				cargo,
				units,
				ShipGroupModules.format(modules),
				officer,
				request.getParameterInt("alarm")), warnings);
	}

	/**
	 * @return The cargo, cut to the ship's cargo space from the last item on
	 */
	private static String fitCargo(String cargoString, long space, List<String> warnings)
	{
		Cargo cargo;
		try
		{
			cargo = new Cargo(Cargo.Type.AUTO, cargoString);
		}
		catch (RuntimeException e)
		{
			throw new CampaignException("Ungültiger Cargo: " + cargoString);
		}
		List<ShipGroupLimits.Load<ResourceID>> loads = new ArrayList<>();
		for (ItemCargoEntry<Item> item : cargo.getItems())
		{
			ItemData data = item.getItem();
			loads.add(new ShipGroupLimits.Load<>(item.getResourceID(), data == null ? "Item " + item.getItemID() : CampaignText.plain(data.getName()),
					item.getCount(), data == null ? 0 : data.getCargo()));
		}
		ShipGroupLimits.Result<ResourceID> result = ShipGroupLimits.trim(loads, space, "Cargo");
		if (!result.isChanged())
		{
			return cargoString;
		}
		warnings.addAll(result.warnings);
		Cargo fitted = new Cargo();
		result.loads.forEach(load -> fitted.addResource(load.key, load.count));
		return fitted.save();
	}

	/**
	 * @return The units, without those too large for the ship and cut to its unit space from the last
	 * entry on
	 */
	private static String fitUnits(EntityManager em, Map<Integer, Long> units, ShipTypeData stats, List<String> warnings)
	{
		List<ShipGroupLimits.Load<Integer>> loads = new ArrayList<>();
		for (Map.Entry<Integer, Long> unit : units.entrySet())
		{
			UnitType unitType = em.find(UnitType.class, unit.getKey());
			if (unitType == null)
			{
				throw new CampaignException("Unbekannter Einheitentyp: " + unit.getKey());
			}
			String name = CampaignText.plain(unitType.getName());
			if (stats.getMaxUnitSize() != -1 && unitType.getSize() > stats.getMaxUnitSize())
			{
				warnings.add("Einheiten: " + name + " (" + unit.getValue() + ") entfernt, zu groß für den Schiffstyp.");
				continue;
			}
			loads.add(new ShipGroupLimits.Load<>(unit.getKey(), name, unit.getValue(), unitType.getSize()));
		}
		ShipGroupLimits.Result<Integer> result = ShipGroupLimits.trim(loads, stats.getUnitSpace(), "Einheiten");
		warnings.addAll(result.warnings);
		Map<Integer, Long> fitted = new LinkedHashMap<>();
		result.loads.forEach(load -> fitted.put(load.key, load.count));
		return ShipGroupUnits.format(fitted);
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
				.append("<td class='noBorderX'>Module</td><td class='noBorderX'>Offizier</td><td class='noBorderX'>Alarm</td><td class='noBorderX'></td></tr>");
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
					.append("</td><td class='noBorderX'>").append(esc(describeModules(em, entry.modules)))
					.append("</td><td class='noBorderX'>").append(esc(describeOfficer(entry.officer)))
					.append("</td><td class='noBorderX'>").append(esc(alarm(entry.alarm).name()))
					.append("</td><td class='noBorderX'>");
			echo.append("<a class='forschinfo' href='").append(pluginUrl(getClass())).append("&amp;template=").append(template.id)
					.append("&amp;editentry=").append(entry.id).append("#sgEntryForm'>bearbeiten</a> ");
			actionButton(echo, getClass(), "deleteentry", "entfernen", "template", template.id, "entry", entry.id);
			echo.append("</td></tr>");
		}
		echo.append("</table>");

		entryForm(echo, em, template, edited, shipTypes, unitTypes);
		spawnForm(echo, em, template);
		echo.append("</div><br />");
	}

	private void entryForm(StringBuilder echo, EntityManager em, ShipGroupTemplate template, ShipGroupTemplate.Entry entry, Map<Integer, ShipType> shipTypes, List<UnitType> unitTypes)
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
		echo.append("<span class='campaign-hint'>Die Anzeigen rechnen ohne Module. Beim Speichern zählt der Platz mit Modulen; was nicht passt, ")
				.append("wird vom letzten Eintrag an gekürzt.</span><br />");
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

		echo.append("<div class='campaign-section'><b>Module</b> <span class='campaign-hint'>je Schiff, passend zum Slot</span>")
				.append("<div class='sgModules'></div></div>");

		officerFields(echo, entry == null ? null : entry.officer);

		echo.append("<input type='submit' value='").append(entry == null ? "Hinzufügen" : "Speichern").append("' />");
		if (entry != null)
		{
			echo.append(" <a class='forschinfo' href='").append(pluginUrl(getClass())).append("&amp;template=").append(template.id).append("'>abbrechen</a>");
		}
		echo.append("</form>");

		Gson gson = new Gson();
		Map<Integer, Map<String, Object>> typeData = new TreeMap<>();
		shipTypes.forEach((id, type) -> typeData.put(id, new LinkedHashMap<>(Map.of("cargo", type.getCargo(), "unitSpace", type.getUnitSpace(), "maxUnitSize", type.getMaxUnitSize()))));
		List<Map<String, Object>> unitData = new ArrayList<>();
		for (UnitType unitType : unitTypes)
		{
			unitData.add(Map.of("id", unitType.getId(), "name", unitType.getName(), "size", unitType.getSize(), "picture", StringUtils.defaultString(unitType.getPicture())));
		}
		moduleData(echo, gson, em, shipTypes, typeData, entry);
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

	/**
	 * Adds each ship type's module slots to its data, and writes the slot types with the modules fitting
	 * them, the module names and the entry's modules for the form script.
	 */
	private static void moduleData(StringBuilder echo, Gson gson, EntityManager em, Map<Integer, ShipType> shipTypes,
			Map<Integer, Map<String, Object>> typeData, ShipGroupTemplate.Entry entry)
	{
		List<Schiffsmodul> moduleItems = em.createQuery("from Schiffsmodul order by id", Schiffsmodul.class).getResultList();
		Set<String> slotTypes = new TreeSet<>();
		shipTypes.forEach((id, type) -> {
			List<Object[]> slots = new ArrayList<>();
			ShipGroupModules.slots(type.getTypeModules()).forEach((slot, slotType) -> {
				slots.add(new Object[] {slot, slotType});
				slotTypes.add(slotType);
			});
			typeData.get(id).put("slots", slots);
		});

		Map<String, Map<String, Object>> slotData = new TreeMap<>();
		for (String slotType : slotTypes)
		{
			String name = slotType;
			List<Integer> fitting = new ArrayList<>();
			try
			{
				ModuleSlot slot = ModuleSlots.get().slot(slotType);
				name = CampaignText.plain(slot.getName());
				for (Schiffsmodul item : moduleItems)
				{
					if (slot.isMemberIn(item.getSlots()))
					{
						fitting.add(item.getID());
					}
				}
			}
			catch (NoSuchSlotException e)
			{
				// An unknown slot type takes no module
			}
			slotData.put(slotType, Map.of("name", name, "modules", fitting));
		}
		Map<Integer, String> names = new TreeMap<>();
		for (Schiffsmodul item : moduleItems)
		{
			names.put(item.getID(), CampaignText.plain(item.getName()) + " (" + item.getID() + ")");
		}
		echo.append("<script type='text/javascript'>var sgSlotTypes=").append(gson.toJson(slotData))
				.append(";var sgModuleNames=").append(gson.toJson(names))
				.append(";var sgCurrentModules=").append(gson.toJson(entry == null ? Map.of() : ShipGroupModules.parse(entry.modules)))
				.append(";</script>");
	}

	private static String describeModules(EntityManager em, String modules)
	{
		if (StringUtils.isBlank(modules))
		{
			return "-";
		}
		StringJoiner joiner = new StringJoiner(", ");
		ShipGroupModules.parse(modules).forEach((slot, item) -> {
			Schiffsmodul module = em.find(Schiffsmodul.class, item);
			joiner.add(slot + ": " + (module == null ? "Modul " + item : CampaignText.plain(module.getName())));
		});
		return joiner.toString();
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
}
