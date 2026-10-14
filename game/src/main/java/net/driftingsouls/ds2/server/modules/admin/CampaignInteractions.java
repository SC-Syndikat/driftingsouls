package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.WellKnownAdminPermission;
import net.driftingsouls.ds2.server.bases.Base;
import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignMapService;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.campaign.GameMasterRestriction;
import net.driftingsouls.ds2.server.campaign.dialog.DialogEngine;
import net.driftingsouls.ds2.server.campaign.dialog.DialogModel;
import net.driftingsouls.ds2.server.campaign.dialog.DialogRepository;
import net.driftingsouls.ds2.server.campaign.dialog.ObjectType;
import net.driftingsouls.ds2.server.campaign.interaction.DialogService;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static net.driftingsouls.ds2.server.modules.admin.CampaignAdminHtml.*;

/**
 * Ties objects to dialogs, switches interactions on and off, and previews them as a chosen player.
 */
@AdminMenuEntry(category = "Kampagne", name = "Interaktionen", permission = WellKnownAdminPermission.CAMPAIGN_INTERACTIONS)
public class CampaignInteractions implements AdminPlugin
{
	private final InteractionRepository repository = new InteractionRepository();

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
		GameMasterRestriction restriction = GameMasterRestriction.forActiveUser();
		int interactionId = request.getParameterInt("interaction");

		try
		{
			switch (request.getParameterString("cpaction"))
			{
				case "save":
					interactionId = save(em, request, restriction, interactionId);
					message(echo, "Interaktion gespeichert.");
					break;
				case "toggle":
				{
					Interaction interaction = find(interactionId);
					checkAllowed(em, restriction, interaction.objectType, interaction.objectId, interaction.npcUserId);
					repository.setActive(interactionId, !interaction.active);
					message(echo, "Interaktion " + (interaction.active ? "deaktiviert." : "aktiviert."));
					break;
				}
				case "delete":
				{
					Interaction interaction = find(interactionId);
					checkAllowed(em, restriction, interaction.objectType, interaction.objectId, interaction.npcUserId);
					repository.delete(interactionId);
					interactionId = 0;
					message(echo, "Interaktion gelöscht.");
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

		List<DialogModel.Node> nodes = new DialogRepository().findAll();
		if (request.getParameterInt("preview") != 0)
		{
			try
			{
				preview(echo, em, request, find(request.getParameterInt("preview")));
			}
			catch (CampaignException e)
			{
				error(echo, e.getMessage());
			}
		}
		else if (interactionId != 0 || "new".equals(request.getParameterString("show")))
		{
			Optional<Interaction> interaction = interactionId != 0 ? repository.find(interactionId) : Optional.empty();
			form(echo, em, request, interaction.orElse(null), nodes);
		}
		// From a ship or base: only its interactions, and "new" preset to it
		ObjectType filterType = request.getParameterString("objecttype").isEmpty() ? null : parseType(request.getParameterString("objecttype"));
		list(echo, em, nodes, filterType, request.getParameterInt("objectid"));
	}

	private Interaction find(int id)
	{
		return repository.find(id).orElseThrow(() -> new CampaignException("Unbekannte Interaktion " + id + "."));
	}

	private int save(EntityManager em, Request request, GameMasterRestriction restriction, int id)
	{
		String label = request.getParameterString("label").trim();
		if (label.isEmpty())
		{
			throw new CampaignException("Die Interaktion braucht eine Beschriftung.");
		}
		ObjectType type = ObjectType.valueOf(request.getParameterString("objecttype"));
		int objectId = request.getParameterInt("objectid_" + type.name());
		if (objectId == 0)
		{
			objectId = request.getParameterInt("objectid");
		}
		int npcId = request.getParameterInt("npc");
		int startNode = request.getParameterInt("startnode");
		if (new DialogRepository().findNode(startNode).isEmpty())
		{
			throw new CampaignException("Bitte einen Startknoten wählen.");
		}
		if (em.find(User.class, npcId) == null)
		{
			throw new CampaignException("Unbekannter NPC " + npcId + ".");
		}
		checkAllowed(em, restriction, type, objectId, npcId);

		Interaction.Audience audience = Interaction.Audience.valueOf(request.getParameterString("audience"));
		return repository.save(new Interaction(id, label, type, objectId, startNode, npcId, "1".equals(request.getParameterString("active")),
				optionalInt(request, "maxuses"), optionalInt(request, "cooldown"), audience,
				ids(request.getParameterString("audienceusers")), ids(request.getParameterString("audiencealliances"))));
	}

	/**
	 * The object must exist; a game master may only use objects in campaign star systems, spoken for by
	 * a participating NPC.
	 */
	private static void checkAllowed(EntityManager em, GameMasterRestriction restriction, ObjectType type, int objectId, int npcId)
	{
		int system;
		switch (type)
		{
			case SHIP:
			{
				Ship ship = em.find(Ship.class, objectId);
				if (ship == null)
				{
					throw new CampaignException("Unbekanntes Schiff " + objectId + ".");
				}
				if (!ship.getOwner().isNPC())
				{
					throw new CampaignException("Interaktionen gibt es nur an NPC-Schiffen, NPC-Basen und Sternensystemen.");
				}
				system = ship.getSystem();
				break;
			}
			case BASE:
			{
				Base base = em.find(Base.class, objectId);
				if (base == null)
				{
					throw new CampaignException("Unbekannte Basis " + objectId + ".");
				}
				if (!base.getOwner().isNPC())
				{
					throw new CampaignException("Interaktionen gibt es nur an NPC-Schiffen, NPC-Basen und Sternensystemen.");
				}
				system = base.getSystem();
				break;
			}
			default:
				system = objectId;
		}
		if (!restriction.isSystemAllowed(system))
		{
			throw new CampaignException("Als GameMaster kannst du nur Objekte in Kampagnensystemen verwenden.");
		}
		if (restriction.applies() && !restriction.getMasterLoginIds().contains(npcId))
		{
			throw new CampaignException("Als GameMaster kannst du nur teilnehmende NPC-Accounts sprechen lassen.");
		}
	}

	private static Set<Integer> ids(String csv)
	{
		return Arrays.stream(csv.split("[,;\\s]+"))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.map(s -> {
					try
					{
						return Integer.parseInt(s);
					}
					catch (NumberFormatException e)
					{
						throw new CampaignException("Ungültige ID: " + s);
					}
				})
				.collect(Collectors.toSet());
	}

	private void form(StringBuilder echo, EntityManager em, Request request, Interaction interaction, List<DialogModel.Node> nodes)
	{
		// Preset from a link, e.g. "add interaction" in the ship view
		ObjectType presetType = interaction != null ? interaction.objectType : parseType(request.getParameterString("objecttype"));
		int presetId = interaction != null ? interaction.objectId : request.getParameterInt("objectid");

		echo.append("<div class='gfxbox' style='width:900px'><h3>")
				.append(interaction == null ? "Neue Interaktion" : "Interaktion " + interaction.id).append("</h3>");
		echo.append("<form action='./ds' method='post'>");
		hiddenFields(echo, getClass(), "save");
		hidden(echo, "interaction", interaction == null ? 0 : interaction.id);
		echo.append("Beschriftung (was der Spieler anklickt): <input type='text' name='label' size='40' value='")
				.append(interaction == null ? "" : esc(interaction.label)).append("' /><br />");
		echo.append("Objekt: <select name='objecttype' id='cpObjectType'>");
		for (ObjectType type : ObjectType.values())
		{
			echo.append("<option value='").append(type.name()).append("'").append(presetType == type ? " selected='selected'" : "")
					.append(">").append(esc(type.getLabel())).append("</option>");
		}
		echo.append("</select> ");
		objectSelects(echo, em, presetType, presetId);
		echo.append("<br />");
		echo.append("Startknoten: <select name='startnode'>");
		for (DialogModel.Node node : nodes)
		{
			echo.append("<option value='").append(node.id).append("'").append(interaction != null && interaction.startNodeId == node.id ? " selected='selected'" : "")
					.append(">").append(node.id).append(": ").append(esc(node.title)).append("</option>");
		}
		echo.append("</select> ");
		npcSelect(echo, em, interaction == null ? 0 : interaction.npcUserId);
		echo.append("<br />");
		echo.append("<label><input type='checkbox' name='active' value='1'").append(interaction == null || interaction.active ? " checked='checked'" : "")
				.append(" /> aktiv</label> ");
		echo.append("Max. Nutzungen pro Spieler (leer = beliebig): <input type='text' name='maxuses' size='4' value='")
				.append(interaction == null || interaction.maxUsesPerUser == null ? "" : interaction.maxUsesPerUser).append("' /> ");
		echo.append("Abstand in Kampagnenticks (leer = keiner): <input type='text' name='cooldown' size='4' value='")
				.append(interaction == null || interaction.cooldownTicks == null ? "" : interaction.cooldownTicks).append("' /><br />");
		echo.append("Für: <select name='audience'>");
		for (Interaction.Audience audience : Interaction.Audience.values())
		{
			echo.append("<option value='").append(audience.name()).append("'").append(interaction != null && interaction.audience == audience ? " selected='selected'" : "")
					.append(">").append(esc(audience.getLabel())).append("</option>");
		}
		echo.append("</select> Spieler-IDs: <input type='text' name='audienceusers' size='20' value='")
				.append(interaction == null ? "" : esc(join(interaction.audienceUserIds))).append("' /> Allianz-IDs: <input type='text' name='audiencealliances' size='12' value='")
				.append(interaction == null ? "" : esc(join(interaction.audienceAllianceIds))).append("' /><br />");
		echo.append("<input type='submit' value='Speichern' /></form></div><br />");
	}

	private static ObjectType parseType(String value)
	{
		try
		{
			return ObjectType.valueOf(value);
		}
		catch (IllegalArgumentException e)
		{
			return ObjectType.SHIP;
		}
	}

	/**
	 * One select per object type, listing only objects in campaign star systems with name and
	 * position; a small script shows the one of the chosen type. Without an open campaign, a plain id
	 * field remains.
	 */
	private static void objectSelects(StringBuilder echo, EntityManager em, ObjectType presetType, int presetId)
	{
		Set<Integer> systems = new CampaignMapService(em).getOpenCampaignSystemIds();
		if (systems.isEmpty())
		{
			echo.append("ID (Schiff, Basis oder System): <input type='text' name='objectid' size='8' value='").append(presetId == 0 ? "" : presetId).append("' />");
			echo.append(" <span class='campaign-hint'>Ohne Kampagnensysteme gibt es keine Auswahlliste.</span>");
			return;
		}

		// Only the ships of the speaking NPCs; listing every player ship in the campaign systems would run
		// into thousands of entries. The stored ship stays selectable.
		Set<Integer> owners = new TreeSet<>();
		CampaignService campaigns = new CampaignService(em);
		campaigns.findOpenCampaign().ifPresent(c -> campaigns.getParticipantIds(c).stream().filter(id -> id < 0).forEach(owners::add));
		Map<Integer, String> ships = new LinkedHashMap<>();
		for (Ship ship : em.createQuery("from Ship where system in (:systems) and id > 0 and (owner.id in (:owners) or id = :preset) order by system, x, y, id", Ship.class)
				.setParameter("systems", systems)
				.setParameter("owners", owners.isEmpty() ? Set.of(0) : owners)
				.setParameter("preset", presetType == ObjectType.SHIP ? presetId : 0)
				.getResultList())
		{
			ships.put(ship.getId(), Common._plaintitle(ship.getName()) + " (" + ship.getId() + ") – " + ship.getLocation().displayCoordinates(false)
					+ " – " + Common._plaintitle(ship.getOwner().getName()));
		}
		Map<Integer, String> bases = new LinkedHashMap<>();
		for (Base base : em.createQuery("from Base where system in (:systems) and owner.id < 0 order by system, x, y, id", Base.class)
				.setParameter("systems", systems).getResultList())
		{
			bases.put(base.getId(), Common._plaintitle(base.getName()) + " (" + base.getId() + ") – " + base.getLocation().displayCoordinates(false)
					+ " – " + Common._plaintitle(base.getOwner().getName()));
		}
		Map<Integer, String> starSystems = new LinkedHashMap<>();
		for (StarSystem system : em.createQuery("from StarSystem where id in (:systems) order by id", StarSystem.class)
				.setParameter("systems", systems).getResultList())
		{
			starSystems.put(system.getID(), system.getName() + " (" + system.getID() + ")");
		}

		objectSelect(echo, ObjectType.SHIP, ships, presetType, presetId);
		objectSelect(echo, ObjectType.BASE, bases, presetType, presetId);
		objectSelect(echo, ObjectType.STAR_SYSTEM, starSystems, presetType, presetId);
		echo.append("<script type='text/javascript'>(function(){")
				.append("var type=document.getElementById('cpObjectType');")
				.append("function show(){['SHIP','BASE','STAR_SYSTEM'].forEach(function(t){document.getElementById('cpObject_'+t).style.display=(type.value===t?'':'none');});}")
				.append("type.onchange=show;show();})();</script>");
	}

	private static void objectSelect(StringBuilder echo, ObjectType type, Map<Integer, String> options, ObjectType presetType, int presetId)
	{
		echo.append("<select name='objectid_").append(type.name()).append("' id='cpObject_").append(type.name()).append("' style='max-width:600px'>");
		if (options.isEmpty())
		{
			echo.append("<option value='0'>– keine in Kampagnensystemen –</option>");
		}
		options.forEach((id, label) -> echo.append("<option value='").append(id).append("'")
				.append(type == presetType && id == presetId ? " selected='selected'" : "").append(">").append(esc(label)).append("</option>"));
		echo.append("</select>");
	}

	/**
	 * The NPC accounts taking part in the open campaign; the stored NPC stays selectable even if it
	 * no longer takes part.
	 */
	private static void npcSelect(StringBuilder echo, EntityManager em, int selected)
	{
		Set<Integer> ids = new TreeSet<>();
		CampaignService campaigns = new CampaignService(em);
		campaigns.findOpenCampaign().ifPresent(c -> campaigns.getParticipantIds(c).stream().filter(id -> id < 0).forEach(ids::add));
		if (selected != 0)
		{
			ids.add(selected);
		}
		echo.append("Sprechender NPC: <select name='npc'>");
		if (ids.isEmpty())
		{
			echo.append("<option value='0'>– kein NPC nimmt an der Kampagne teil –</option>");
		}
		for (int id : ids)
		{
			User npc = em.find(User.class, id);
			echo.append("<option value='").append(id).append("'").append(id == selected ? " selected='selected'" : "").append(">")
					.append(npc == null ? "?" : esc(Common._plaintitle(npc.getName()))).append(" (").append(id).append(")</option>");
		}
		echo.append("</select>");
	}

	private static String join(Set<Integer> ids)
	{
		return ids.stream().sorted().map(String::valueOf).collect(Collectors.joining(", "));
	}

	/**
	 * @return The link to the interactions of an object, with "new" preset to it
	 */
	public static String objectUrl(ObjectType type, int objectId)
	{
		return pluginUrl(CampaignInteractions.class) + "&amp;objecttype=" + type.name() + "&amp;objectid=" + objectId;
	}

	private void list(StringBuilder echo, EntityManager em, List<DialogModel.Node> nodes, ObjectType filterType, int filterId)
	{
		echo.append("<div class='gfxbox' style='width:900px'><h3>Interaktionen</h3>");
		String preset = filterType == null ? "" : "&amp;objecttype=" + filterType.name() + "&amp;objectid=" + filterId;
		if (filterType != null)
		{
			echo.append("<p>Nur Interaktionen von ").append(esc(filterType.getLabel())).append(" ").append(filterId)
					.append(". <a class='forschinfo' href='").append(pluginUrl(getClass())).append("'>alle anzeigen</a></p>");
		}
		echo.append("<a class='forschinfo' href='").append(pluginUrl(getClass())).append("&amp;show=new").append(preset).append("'>+ neue Interaktion</a><br /><br />");
		echo.append("<table class='noBorderX' cellpadding='3'><tr><td class='noBorderX'>ID</td><td class='noBorderX'>Beschriftung</td>")
				.append("<td class='noBorderX'>Objekt</td><td class='noBorderX'>Start</td><td class='noBorderX'>NPC</td><td class='noBorderX'>Limits</td>")
				.append("<td class='noBorderX'>Zustand</td><td class='noBorderX'></td></tr>");
		for (Interaction interaction : filterType == null ? repository.findAll() : repository.findFor(filterType, filterId))
		{
			User npc = em.find(User.class, interaction.npcUserId);
			String startTitle = nodes.stream().filter(n -> n.id == interaction.startNodeId).map(n -> n.title).findFirst().orElse("?");
			echo.append("<tr><td class='noBorderX'>").append(interaction.id)
					.append("</td><td class='noBorderX'><a class='forschinfo' href='").append(pluginUrl(getClass())).append("&amp;interaction=").append(interaction.id)
					.append("'>").append(esc(interaction.label)).append("</a>")
					.append("</td><td class='noBorderX'>").append(esc(interaction.objectType.getLabel())).append(" ").append(interaction.objectId)
					.append("</td><td class='noBorderX'>").append(interaction.startNodeId).append(": ").append(esc(startTitle))
					.append("</td><td class='noBorderX'>").append(npc == null ? "?" : esc(npc.getPlainname())).append(" (").append(interaction.npcUserId).append(")")
					.append("</td><td class='noBorderX'>").append(interaction.maxUsesPerUser == null ? "∞" : interaction.maxUsesPerUser + "×")
					.append(interaction.cooldownTicks == null ? "" : ", alle " + interaction.cooldownTicks + " Ticks")
					.append(interaction.audience == Interaction.Audience.RESTRICTED ? ", eingeschränkt" : "")
					.append("</td><td class='noBorderX'>").append(interaction.active ? "aktiv" : "<span class='error'>inaktiv</span>")
					.append("</td><td class='noBorderX'>");
			actionButton(echo, getClass(), "toggle", interaction.active ? "deaktivieren" : "aktivieren", "interaction", interaction.id);
			echo.append("<a class='forschinfo' href='").append(pluginUrl(getClass())).append("&amp;preview=").append(interaction.id).append("'>Vorschau</a> ");
			actionButton(echo, getClass(), "delete", "löschen", "interaction", interaction.id);
			echo.append("</td></tr>");
		}
		echo.append("</table></div>");
	}

	// ---------------------------------------------------------------- preview

	private void preview(StringBuilder echo, EntityManager em, Request request, Interaction interaction)
	{
		User gamemaster = (User) ContextMap.getContext().getActiveUser();
		Optional<Campaign> campaign = new CampaignService(em).findOpenCampaign();
		Set<Integer> participants = campaign.map(c -> new CampaignService(em).getParticipantIds(c)).orElse(Set.of());

		echo.append("<div class='gfxbox' style='width:900px'><h3>Vorschau: ").append(esc(interaction.label)).append("</h3>");
		echo.append("<p class='smallfont'>Bedingungen und Chancen werden für den gewählten Spieler und sein Schiff ausgewertet. ")
				.append("Aktionen werden nur beschrieben, nichts wird verändert.</p>");

		int playerId = request.getParameterInt("pplayer");
		int shipId = request.getParameterInt("pship");
		echo.append("<form action='./ds' method='get'>");
		hiddenFields(echo, getClass(), "");
		hidden(echo, "preview", interaction.id);
		echo.append("Als Spieler: <select name='pplayer'>");
		for (int id : participants)
		{
			User user = em.find(User.class, id);
			if (user != null && !user.isNPC())
			{
				echo.append("<option value='").append(id).append("'").append(id == playerId ? " selected='selected'" : "").append(">")
						.append(esc(user.getPlainname())).append(" (").append(id).append(")</option>");
			}
		}
		echo.append("</select> ");
		User player = playerId != 0 ? em.find(User.class, playerId) : null;
		if (player != null)
		{
			echo.append("mit Schiff: <select name='pship'>");
			for (Ship ship : em.createQuery("from Ship where owner = :owner and id > 0 order by id", Ship.class).setParameter("owner", player).getResultList())
			{
				echo.append("<option value='").append(ship.getId()).append("'").append(ship.getId() == shipId ? " selected='selected'" : "").append(">")
						.append(esc(Common._plaintitle(ship.getName()))).append(" (").append(ship.getId()).append(") ").append(ship.getLocation().displayCoordinates(false))
						.append("</option>");
			}
			echo.append("</select> ");
		}
		echo.append("<input type='submit' value='anzeigen' /></form>");

		Ship ship = shipId != 0 ? em.find(Ship.class, shipId) : null;
		if (player == null || ship == null || ship.getOwner() != player)
		{
			echo.append("</div><br />");
			return;
		}

		DialogService service = new DialogService(em);
		int nodeId = request.getParameterInt("pnode") != 0 ? request.getParameterInt("pnode") : interaction.startNodeId;

		if ("previewchoose".equals(request.getParameterString("cpaction")))
		{
			String forced = request.getParameterString("forced");
			DialogModel.Roll forcedOutcome = "success".equals(forced) ? DialogModel.Roll.SUCCESS
					: "partial".equals(forced) ? DialogModel.Roll.PARTIAL
					: "failure".equals(forced) ? DialogModel.Roll.FAILURE : null;
			DialogEngine.Result result = service.previewChoose(interaction, player, ship, nodeId, request.getParameterInt("poption"), forcedOutcome, gamemaster);
			echo.append("<div style='border:1px solid #aa8800; padding:6px'>Ergebnis: <b>").append(result.roll.getLabel()).append("</b>")
					.append(result.chance == null ? "" : " (Chance " + result.chance + " %)").append("<br />Aktionen: ")
					.append(result.actions.isEmpty() ? "keine" : esc(String.join("; ", result.actions)))
					.append("<br />").append(result.nextNodeId == null ? "Das Gespräch endet." : "Weiter mit Knoten " + result.nextNodeId + ".").append("</div>");
			if (result.nextNodeId == null)
			{
				echo.append("<a class='forschinfo' href='").append(previewUrl(interaction, playerId, shipId, interaction.startNodeId)).append("'>von vorn</a></div><br />");
				return;
			}
			nodeId = result.nextNodeId;
		}

		DialogService.PreviewView view = service.preview(interaction, player, ship, nodeId, gamemaster);
		if (view.unavailability.isPresent())
		{
			echo.append("<p class='error'>Hinweis: Im echten Spiel könnte der Spieler das Gespräch gerade nicht führen: ")
					.append(esc(view.unavailability.get())).append("</p>");
		}
		echo.append("<table><tr><td valign='top'>");
		if (!view.node.image.isEmpty())
		{
			echo.append("<img src='").append(esc(view.node.image)).append("' alt='' />");
		}
		echo.append("</td><td valign='top'><b>").append(esc(view.node.speakerName)).append("</b> <span class='smallfont'>(Knoten ")
				.append(view.node.id).append(")</span><br /><br />").append(view.textHtml).append("<br /><br />");
		for (DialogEngine.Evaluation evaluation : view.evaluations)
		{
			DialogModel.Option option = evaluation.option;
			echo.append("<div style='margin-bottom:6px'>");
			echo.append(evaluation.available ? "<b>" : "<span style='color:#888888'>").append(esc(option.text)).append(evaluation.available ? "</b>" : "</span>");
			if (!evaluation.available)
			{
				echo.append(" <span class='error'>nicht verfügbar").append(option.hideIfUnavailable ? ", für den Spieler unsichtbar" : "").append(": ")
						.append(esc(String.join("; ", evaluation.unmetConditions))).append("</span>");
			}
			if (evaluation.chance != null)
			{
				echo.append("<br /><span class='smallfont'>Chance ").append(evaluation.chance).append(" %")
						.append(option.partialChance == null ? "" : ", Teilerfolg weitere " + option.partialChance + " %").append(": ")
						.append(esc(String.join(" | ", evaluation.chanceExplanation))).append("</span>");
			}
			if (evaluation.available)
			{
				echo.append("<br />");
				previewButton(echo, interaction, playerId, shipId, nodeId, option.id, "", "wählen (würfeln)");
				if (evaluation.chance != null)
				{
					previewButton(echo, interaction, playerId, shipId, nodeId, option.id, "success", "Erfolg erzwingen");
					if (option.partialChance != null)
					{
						previewButton(echo, interaction, playerId, shipId, nodeId, option.id, "partial", "Teilerfolg erzwingen");
					}
					previewButton(echo, interaction, playerId, shipId, nodeId, option.id, "failure", "Misserfolg erzwingen");
				}
			}
			echo.append("</div>");
		}
		echo.append("</td></tr></table>");
		echo.append("<a class='forschinfo' href='").append(previewUrl(interaction, playerId, shipId, interaction.startNodeId)).append("'>von vorn</a>");
		echo.append("</div><br />");
	}

	private String previewUrl(Interaction interaction, int playerId, int shipId, int nodeId)
	{
		return pluginUrl(getClass()) + "&amp;preview=" + interaction.id + "&amp;pplayer=" + playerId + "&amp;pship=" + shipId + "&amp;pnode=" + nodeId;
	}

	private void previewButton(StringBuilder echo, Interaction interaction, int playerId, int shipId, int nodeId, int optionId, String forced, String label)
	{
		actionButton(echo, getClass(), "previewchoose", label, "preview", interaction.id, "pplayer", playerId, "pship", shipId,
				"pnode", nodeId, "poption", optionId, "forced", forced);
	}
}
