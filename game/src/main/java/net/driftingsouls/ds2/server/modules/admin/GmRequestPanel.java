package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.campaign.CampaignText;
import net.driftingsouls.ds2.server.battles.Battle;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.GameMasterControl;
import net.driftingsouls.ds2.server.campaign.dialog.CampaignVariableService;
import net.driftingsouls.ds2.server.campaign.dialog.DialogAction;
import net.driftingsouls.ds2.server.campaign.dialog.DialogModel;
import net.driftingsouls.ds2.server.campaign.dialog.DialogRepository;
import net.driftingsouls.ds2.server.campaign.dialog.DialogShips;
import net.driftingsouls.ds2.server.campaign.dialog.DialogTypes;
import net.driftingsouls.ds2.server.campaign.dialog.ObjectType;
import net.driftingsouls.ds2.server.campaign.dialog.TrustService;
import net.driftingsouls.ds2.server.campaign.interaction.GmRequest;
import net.driftingsouls.ds2.server.campaign.interaction.GmRequestService;
import net.driftingsouls.ds2.server.campaign.interaction.Interaction;
import net.driftingsouls.ds2.server.campaign.interaction.InteractionRepository;
import net.driftingsouls.ds2.server.entities.Offizier;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.pipeline.Request;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipTypeData;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static net.driftingsouls.ds2.server.modules.admin.CampaignAdminHtml.esc;
import static net.driftingsouls.ds2.server.modules.admin.CampaignAdminHtml.paramFields;
import static net.driftingsouls.ds2.server.modules.admin.CampaignAdminHtml.readParams;

/**
 * A player's request as the game master works on it: who the player is and how he stands, the conversation,
 * and one form that answers, runs an action, continues the dialog and closes the request in one go. Shown in
 * the game master's request page and in the dialog box of the ship view.
 */
public final class GmRequestPanel
{
	private static final String NO_ACTION = "";

	private GmRequestPanel()
	{
		// static only
	}

	/**
	 * @param hiddenFields The fields that post the form back to the page showing the panel
	 */
	public static void render(StringBuilder echo, EntityManager em, GmRequest request, String formAction, String hiddenFields)
	{
		Optional<Interaction> interaction = Optional.ofNullable(request.interactionId).flatMap(id -> new InteractionRepository().find(id));
		playerInfo(echo, em, request, interaction);
		conversation(echo, em, request);
		if (request.isOpen())
		{
			form(echo, request, interaction, formAction, hiddenFields);
		}
	}

	/**
	 * Carries out what the game master filled in: action, dialog continuation, answer and closing, in this order.
	 *
	 * @return What was done
	 */
	public static List<String> handle(EntityManager em, Request request, User gamemaster, int requestId)
	{
		GmRequestService service = new GmRequestService(em);
		List<String> done = new ArrayList<>();
		String type = request.getParameterString("gm_action");
		if (!NO_ACTION.equals(type))
		{
			DialogAction action = DialogTypes.get().getActions().get(type);
			if (action == null)
			{
				throw new CampaignException("Unbekannte Aktion " + type + ".");
			}
			done.add("Aktion: " + service.runAction(gamemaster, requestId, type, readParams(request, action.getParams(), actionPrefix(type))));
		}
		if ("1".equals(request.getParameterString("gm_capitulate")))
		{
			done.add("Kapitulation: " + service.capitulate(gamemaster, requestId) + " Schiff(e) übergeben, Schlacht beendet");
		}
		else if ("1".equals(request.getParameterString("gm_endbattle")))
		{
			service.endBattle(gamemaster, requestId);
			done.add("Schlacht beendet");
		}
		int node = request.getParameterInt("gm_node");
		if (node != 0)
		{
			service.continueDialog(gamemaster, requestId, node);
			done.add("Gespräch bei Knoten " + node + " fortgesetzt");
		}
		String text = request.getParameterString("gm_text").trim();
		if (!text.isEmpty())
		{
			service.gamemasterReply(gamemaster, requestId, text);
			done.add("Antwort gesendet");
		}
		if ("1".equals(request.getParameterString("gm_close")))
		{
			service.setStatus(gamemaster, requestId, GmRequest.Status.CLOSED);
			done.add("Anfrage abgeschlossen");
		}
		if (done.isEmpty())
		{
			throw new CampaignException("Es war nichts ausgefüllt.");
		}
		return done;
	}

	private static String actionPrefix(String type)
	{
		return "a_" + type + "_";
	}

	private static void playerInfo(StringBuilder echo, EntityManager em, GmRequest request, Optional<Interaction> interaction)
	{
		User player = em.find(User.class, request.userId);
		Ship ship = em.find(Ship.class, request.shipId);
		echo.append("<div class='campaign-section' style='line-height:1.5'><b>")
				.append(player == null ? "?" : esc(player.getPlainname())).append(" (").append(request.userId).append(")</b>");
		if (ship == null)
		{
			echo.append(" – das Schiff der Anfrage existiert nicht mehr</div>");
			return;
		}
		ShipTypeData type = ship.getTypeData();
		echo.append(" mit ").append(esc(CampaignText.plain(ship.getName()))).append(" (").append(ship.getId()).append(", ")
				.append(esc(CampaignText.plain(type.getNickname()))).append(") bei ").append(esc(ship.getLocation().displayCoordinates(false))).append("<br />");
		echo.append("Hülle ").append(percent(ship.getHull(), type.getHull())).append(" · Schilde ").append(percent(ship.getShields(), type.getShields()))
				.append(" · Crew ").append(ship.getCrew()).append("/").append(type.getCrew())
				.append(" · Energie ").append(ship.getEnergy()).append("/").append(type.getEps()).append("<br />");

		Offizier officer = ship.getOffizier();
		if (officer == null)
		{
			echo.append("Kein Offizier an Bord<br />");
		}
		else
		{
			echo.append("Offizier ").append(esc(officer.getName())).append(", Rang ").append(officer.getRang()).append(": ");
			for (Offizier.Ability ability : Offizier.Ability.values())
			{
				echo.append(esc(ability.name())).append(" ").append(officer.getAbility(ability)).append(" ");
			}
			echo.append("<br />");
		}

		List<Ship> sector = em.createQuery("from Ship where owner = :owner and system = :system and x = :x and y = :y and id > 0", Ship.class)
				.setParameter("owner", player)
				.setParameter("system", ship.getSystem())
				.setParameter("x", ship.getX())
				.setParameter("y", ship.getY())
				.getResultList();
		echo.append("Im Sektor: ").append(sector.size()).append(" Schiff(e), Kampfkraft ").append(DialogShips.strength(em, sector)).append("<br />");

		if (interaction.isPresent() && player != null)
		{
			User npc = em.find(User.class, interaction.get().npcUserId);
			if (npc != null)
			{
				TrustService.Trust trust = new TrustService(em).trust(player.getId(), npc.getId());
				echo.append("Vertrauen von ").append(esc(npc.getPlainname())).append(": ").append(Math.round(trust.getTotal()))
						.append(" (eigenes ").append(trust.own).append(", Allianz ").append(Math.round(trust.getAllianceShare())).append(")")
						.append(" · Beziehung ").append(esc(npc.getRelation(player).name())).append("<br />");
			}
		}
		Map<String, Long> variables = new CampaignVariableService().list(request.campaignId);
		String own = variables.entrySet().stream()
				.filter(e -> e.getKey().startsWith(request.userId + "/"))
				.map(e -> e.getKey().substring(e.getKey().indexOf('/') + 1) + " = " + e.getValue())
				.collect(Collectors.joining(", "));
		echo.append("Kampagnenvariablen: ").append(own.isEmpty() ? "keine" : esc(own)).append("<br />");
		battleInfo(echo, em, request);
		sectorLinks(echo, ship, interaction);
		echo.append("</div>");
	}

	/**
	 * To the place of the request: the NPC ship he talked to, seen as its NPC, and the sector on the star map.
	 */
	private static void sectorLinks(StringBuilder echo, Ship playerShip, Optional<Interaction> interaction)
	{
		echo.append("<a class='forschinfo' href='./starmap?system=").append(playerShip.getSystem()).append("&amp;x=").append(playerShip.getX())
				.append("&amp;y=").append(playerShip.getY()).append("'>Sektor auf der Sternenkarte</a>");
		Set<Integer> npcs = GameMasterControl.controllableNpcs().stream().map(User::getId).collect(Collectors.toSet());
		if (interaction.isPresent() && interaction.get().objectType == ObjectType.SHIP && npcs.contains(interaction.get().npcUserId))
		{
			echo.append(" · <a class='forschinfo' href='").append(esc(GameMasterMenu.controlUrl(interaction.get().npcUserId, "ship:" + interaction.get().objectId)))
					.append("'>zum Schiff des Gesprächspartners (als NPC steuern)</a>");
		}
		else if (interaction.isPresent() && interaction.get().objectType == ObjectType.BASE && npcs.contains(interaction.get().npcUserId))
		{
			echo.append(" · <a class='forschinfo' href='").append(esc(GameMasterMenu.controlUrl(interaction.get().npcUserId, "base:" + interaction.get().objectId)))
					.append("'>zur Basis des Gesprächspartners (als NPC steuern)</a>");
		}
	}

	/**
	 * For a request from a battle: where it is and who fights, with a link to watch it as the NPC.
	 */
	private static void battleInfo(StringBuilder echo, EntityManager em, GmRequest request)
	{
		if (request.battleId == null)
		{
			return;
		}
		Battle battle = em.find(Battle.class, request.battleId);
		if (battle == null)
		{
			echo.append("Die Schlacht dieser Anfrage ist zu Ende.<br />");
			return;
		}
		echo.append("Schlacht ").append(battle.getId()).append(" bei ").append(esc(battle.getLocation().displayCoordinates(false))).append(": ");
		Set<Integer> npcs = GameMasterControl.controllableNpcs().stream().map(User::getId).collect(Collectors.toSet());
		for (int side = 0; side < 2; side++)
		{
			User commander = battle.getCommander(side);
			echo.append(side == 0 ? "" : " gegen ").append(commander == null ? "?" : esc(commander.getPlainname()));
		}
		for (int side = 0; side < 2; side++)
		{
			User commander = battle.getCommander(side);
			if (commander != null && npcs.contains(commander.getId()))
			{
				echo.append(" · <a class='forschinfo' href='").append(esc(GameMasterMenu.controlUrl(commander.getId(), "battle:" + battle.getId())))
						.append("'>Schlacht als ").append(esc(commander.getPlainname())).append(" ansehen</a>");
				break;
			}
		}
		echo.append("<br />");
	}

	private static String percent(long current, long max)
	{
		return max <= 0 ? "-" : Math.round(100.0 * current / max) + " %";
	}

	private static void conversation(StringBuilder echo, EntityManager em, GmRequest request)
	{
		echo.append("<div class='campaign-section'>");
		for (GmRequest.Message message : new GmRequestService(em).messages(request.id))
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
	}

	private static void form(StringBuilder echo, GmRequest request, Optional<Interaction> interaction, String formAction, String hiddenFields)
	{
		String id = "gmr" + request.id;
		echo.append("<form action='").append(esc(formAction)).append("' method='post'>").append(hiddenFields);
		echo.append("Antwort an den Spieler:<br /><textarea name='gm_text' rows='3' cols='90'></textarea><br />");

		echo.append("Aktion: <select name='gm_action' id='").append(id).append("_action'><option value=''>keine</option>");
		Map<String, DialogAction> actions = DialogTypes.get().getActions();
		for (DialogAction.Group group : DialogAction.Group.values())
		{
			echo.append("<optgroup label='").append(esc(group.getLabel())).append("'>");
			actions.forEach((type, action) -> {
				if (action.getGroup() == group)
				{
					echo.append("<option value='").append(esc(type)).append("'>").append(esc(action.getLabel())).append("</option>");
				}
			});
			echo.append("</optgroup>");
		}
		echo.append("</select>");
		actions.forEach((type, action) -> {
			echo.append("<div class='").append(id).append("_params' data-action='").append(esc(type))
					.append("' style='display:none; margin:4px 0 4px 20px'>");
			paramFields(echo, action.getParams(), null, actionPrefix(type));
			echo.append("</div>");
		});
		echo.append("<br />");

		if (interaction.isPresent())
		{
			echo.append("Gespräch fortsetzen bei: <select name='gm_node'><option value='0'>nicht fortsetzen</option>");
			for (DialogModel.Node node : new DialogRepository().findAll())
			{
				echo.append("<option value='").append(node.id).append("'>").append(node.id).append(": ").append(esc(node.title)).append("</option>");
			}
			echo.append("</select><br />");
		}
		if (request.battleId != null)
		{
			echo.append("<label><input type='checkbox' name='gm_endbattle' value='1' /> Schlacht beenden (unentschieden)</label><br />");
			echo.append("<label><input type='checkbox' name='gm_capitulate' value='1' /> Gegenseite kapituliert: alle ihre Schiffe an den Spieler übergeben und die Schlacht beenden</label><br />");
		}
		echo.append("<label><input type='checkbox' name='gm_close' value='1' /> Anfrage danach abschließen</label><br />");
		echo.append("<input type='submit' value='Ausführen' /> <span class='campaign-hint'>Aktion, Fortsetzung, Antwort und Abschluss laufen mit einem Klick.</span>");
		echo.append("</form>");
		// Only the parameters of the chosen action are shown; the others are not posted
		echo.append("<script type='text/javascript'>(function(){var s=document.getElementById('").append(id).append("_action');")
				.append("function show(){var blocks=document.getElementsByClassName('").append(id).append("_params');")
				.append("for(var i=0;i<blocks.length;i++){var on=blocks[i].getAttribute('data-action')===s.value;blocks[i].style.display=on?'':'none';")
				.append("var f=blocks[i].querySelectorAll('input,select,textarea');for(var j=0;j<f.length;j++){f[j].disabled=!on;}}}")
				.append("s.onchange=show;show();})();</script>");
	}
}
