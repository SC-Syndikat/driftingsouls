package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.WellKnownAdminPermission;
import net.driftingsouls.ds2.server.bases.Base;
import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.campaign.GameMasterRestriction;
import net.driftingsouls.ds2.server.campaign.dialog.DialogEngine;
import net.driftingsouls.ds2.server.campaign.dialog.DialogModel;
import net.driftingsouls.ds2.server.campaign.dialog.DialogRepository;
import net.driftingsouls.ds2.server.campaign.dialog.ObjectType;
import net.driftingsouls.ds2.server.campaign.interaction.DialogService;
import net.driftingsouls.ds2.server.campaign.interaction.Interaction;
import net.driftingsouls.ds2.server.campaign.interaction.InteractionRepository;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.pipeline.Request;
import net.driftingsouls.ds2.server.ships.Ship;

import javax.persistence.EntityManager;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
			form(echo, interaction.orElse(null), nodes);
		}
		list(echo, em, nodes);
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
		int objectId = request.getParameterInt("objectid");
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

	private void form(StringBuilder echo, Interaction interaction, List<DialogModel.Node> nodes)
	{
		echo.append("<div class='gfxbox' style='width:900px'><h3>")
				.append(interaction == null ? "Neue Interaktion" : "Interaktion " + interaction.id).append("</h3>");
		echo.append("<form action='./ds' method='post'>");
		hiddenFields(echo, getClass(), "save");
		hidden(echo, "interaction", interaction == null ? 0 : interaction.id);
		echo.append("Beschriftung (was der Spieler anklickt): <input type='text' name='label' size='40' value='")
				.append(interaction == null ? "" : esc(interaction.label)).append("' /><br />");
		echo.append("Objekt: <select name='objecttype'>");
		for (ObjectType type : ObjectType.values())
		{
			echo.append("<option value='").append(type.name()).append("'").append(interaction != null && interaction.objectType == type ? " selected='selected'" : "")
					.append(">").append(esc(type.getLabel())).append("</option>");
		}
		echo.append("</select> ID (Schiff, Basis oder System): <input type='text' name='objectid' size='8' value='")
				.append(interaction == null ? "" : interaction.objectId).append("' /><br />");
		echo.append("Startknoten: <select name='startnode'>");
		for (DialogModel.Node node : nodes)
		{
			echo.append("<option value='").append(node.id).append("'").append(interaction != null && interaction.startNodeId == node.id ? " selected='selected'" : "")
					.append(">").append(node.id).append(": ").append(esc(node.title)).append("</option>");
		}
		echo.append("</select> ");
		echo.append("Sprechender NPC (User-ID): <input type='text' name='npc' size='6' value='").append(interaction == null ? "" : interaction.npcUserId).append("' /><br />");
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

	private static String join(Set<Integer> ids)
	{
		return ids.stream().sorted().map(String::valueOf).collect(Collectors.joining(", "));
	}

	private void list(StringBuilder echo, EntityManager em, List<DialogModel.Node> nodes)
	{
		echo.append("<div class='gfxbox' style='width:900px'><h3>Interaktionen</h3>");
		echo.append("<a class='forschinfo' href='").append(pluginUrl(getClass())).append("&amp;show=new'>+ neue Interaktion</a><br /><br />");
		echo.append("<table class='noBorderX' cellpadding='3'><tr><td class='noBorderX'>ID</td><td class='noBorderX'>Beschriftung</td>")
				.append("<td class='noBorderX'>Objekt</td><td class='noBorderX'>Start</td><td class='noBorderX'>NPC</td><td class='noBorderX'>Limits</td>")
				.append("<td class='noBorderX'>Zustand</td><td class='noBorderX'></td></tr>");
		for (Interaction interaction : repository.findAll())
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
			Boolean forcedOutcome = "success".equals(forced) ? Boolean.TRUE : "failure".equals(forced) ? Boolean.FALSE : null;
			DialogEngine.Result result = service.previewChoose(interaction, player, ship, nodeId, request.getParameterInt("poption"), forcedOutcome, gamemaster);
			echo.append("<div style='border:1px solid #aa8800; padding:6px'>Ergebnis: <b>").append(result.success ? "Erfolg" : "Misserfolg").append("</b>")
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
				echo.append("<br /><span class='smallfont'>Chance ").append(evaluation.chance).append(" %: ")
						.append(esc(String.join(" | ", evaluation.chanceExplanation))).append("</span>");
			}
			if (evaluation.available)
			{
				echo.append("<br />");
				previewButton(echo, interaction, playerId, shipId, nodeId, option.id, "", "wählen (würfeln)");
				if (evaluation.chance != null)
				{
					previewButton(echo, interaction, playerId, shipId, nodeId, option.id, "success", "Erfolg erzwingen");
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
