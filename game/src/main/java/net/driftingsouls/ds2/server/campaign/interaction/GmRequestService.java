package net.driftingsouls.ds2.server.campaign.interaction;

import net.driftingsouls.ds2.server.battles.Battle;
import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignBattles;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignMapRepository;
import net.driftingsouls.ds2.server.campaign.CampaignRepository;
import net.driftingsouls.ds2.server.campaign.dialog.DialogAction;
import net.driftingsouls.ds2.server.campaign.dialog.DialogContext;
import net.driftingsouls.ds2.server.campaign.dialog.DialogRepository;
import net.driftingsouls.ds2.server.campaign.dialog.DialogTypes;
import net.driftingsouls.ds2.server.campaign.dialog.ObjectType;
import net.driftingsouls.ds2.server.campaign.dialog.Params;
import net.driftingsouls.ds2.server.campaign.dialog.actions.StandardActions;
import net.driftingsouls.ds2.server.comm.PM;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.ships.Ship;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Requests to the game master: a player writes one from a dialog when none of the answers fits, the game
 * master answers, runs actions for the player or continues the dialog, and closes the request.
 */
public class GmRequestService
{
	static final int MAX_TEXT = 2000;

	private final EntityManager em;
	private final GmRequestRepository requests = new GmRequestRepository();
	private final InteractionRepository interactions = new InteractionRepository();
	private final CampaignRepository campaigns = new CampaignRepository();

	public GmRequestService(EntityManager em)
	{
		this.em = em;
	}

	// ---------------------------------------------------------------- player

	/**
	 * Writes a request from the dialog the player is in with the ship. A further request about the same
	 * interaction continues the open one.
	 *
	 * @return The request's id
	 */
	public int submit(User user, Ship ship, String text)
	{
		String message = checkText(text);
		Campaign campaign = runningCampaign();
		DialogSession session = interactions.findSessionOf(user.getId())
				.filter(s -> s.shipId == ship.getId())
				.orElseThrow(() -> new CampaignException("Anfragen an den GameMaster sind nur aus einem laufenden Gespräch möglich."));

		Optional<GmRequest> open = requests.findVisibleToPlayer(campaign.getId(), user.getId(), ship.getId()).stream()
				.filter(r -> r.isOpen() && Integer.valueOf(session.interactionId).equals(r.interactionId))
				.findFirst();
		int id = open.map(r -> r.id).orElseGet(() -> requests.create(campaign.getId(), user.getId(), ship.getId(), session.interactionId,
				session.currentNodeId));
		requests.addMessage(id, user.getId(), GmRequest.Kind.PLAYER, message);

		Integer gamemaster = campaign.getGamemasterId();
		if (gamemaster != null && open.isEmpty())
		{
			String label = interactions.find(session.interactionId).map(i -> i.label).orElse("?");
			PM.send(user, gamemaster, "Kampagne: neue Anfrage von " + user.getPlainname(),
					"Anfrage #" + id + " zu „" + label + "“ (Schiff " + ship.getId() + "):\n\n" + message
							+ "\n\nDu findest sie auf der GameMaster-Seite unter „Spieler-Anfragen“.", em);
		}
		return id;
	}

	/**
	 * Writes a request from a battle in a campaign star system, e.g. to negotiate terms. A further request
	 * from the same battle continues the open one.
	 *
	 * @return The request's id
	 */
	public int submitFromBattle(User user, Ship ship, Battle battle, String text)
	{
		String message = checkText(text);
		Campaign campaign = runningCampaign();
		if (battle == null || ship == null || ship.getOwner() != user || ship.getBattle() != battle
				|| !new CampaignMapRepository().findSystemIds(campaign.getId()).contains(battle.getSystem()))
		{
			throw new CampaignException("Anfragen an den GameMaster gibt es nur aus Schlachten in Kampagnensystemen.");
		}
		Optional<GmRequest> open = requests.findVisibleInBattle(user.getId(), battle.getId()).stream().filter(GmRequest::isOpen).findFirst();
		int id = open.map(r -> r.id).orElseGet(() -> requests.create(campaign.getId(), user.getId(), ship.getId(), null, null, battle.getId()));
		requests.addMessage(id, user.getId(), GmRequest.Kind.PLAYER, message);

		Integer gamemaster = campaign.getGamemasterId();
		if (gamemaster != null && open.isEmpty())
		{
			PM.send(user, gamemaster, "Kampagne: Anfrage aus einer Schlacht von " + user.getPlainname(),
					"Anfrage #" + id + " aus der Schlacht bei " + battle.getLocation().displayCoordinates(false) + ":\n\n" + message
							+ "\n\nDu findest sie auf der GameMaster-Seite unter „Spieler-Anfragen“.", em);
		}
		return id;
	}

	/**
	 * @return Whether the player may write to the game master from the battle: a participant fighting in a
	 * campaign star system while the campaign runs
	 */
	public static boolean battleRequestsPossible(User user, Battle battle)
	{
		if (user == null || user.isNPC() || battle == null)
		{
			return false;
		}
		Optional<Campaign> campaign = new CampaignRepository().findOpen().filter(Campaign::isRunning);
		return campaign.isPresent() && new CampaignMapRepository().findSystemIds(campaign.get().getId()).contains(battle.getSystem());
	}

	/**
	 * @return The player's requests from the battle that are open or have an unread answer; viewing them marks
	 * them read
	 */
	public List<GmRequest> visibleInBattle(User user, Battle battle)
	{
		List<GmRequest> visible = requests.findVisibleInBattle(user.getId(), battle.getId());
		visible.stream().filter(r -> r.unreadByUser).forEach(r -> requests.markReadByUser(r.id));
		return visible;
	}

	/**
	 * Ends the battle of a request as a draw.
	 */
	public void endBattle(User gamemaster, int requestId)
	{
		GmRequest request = forGamemaster(requestId);
		Battle battle = request.battleId == null ? null : em.find(Battle.class, request.battleId);
		if (battle == null)
		{
			throw new CampaignException("Die Schlacht dieser Anfrage ist bereits zu Ende.");
		}
		CampaignBattles.end(em, battle, gamemaster);
		requests.addMessage(request.id, gamemaster.getId(), GmRequest.Kind.ACTION, "Der GameMaster hat die Schlacht beendet.");
	}

	/**
	 * The other side of the request's battle gives up: the ships of campaign NPCs fighting there go to the
	 * player and the battle ends. Ships that defected to another NPC during the battle count as long as that
	 * NPC takes part in the campaign.
	 *
	 * @return How many ships the player got
	 */
	public int capitulate(User gamemaster, int requestId)
	{
		GmRequest request = forGamemaster(requestId);
		Campaign campaign = runningCampaign();
		Battle battle = request.battleId == null ? null : em.find(Battle.class, request.battleId);
		User player = em.find(User.class, request.userId);
		if (battle == null || player == null)
		{
			throw new CampaignException("Die Schlacht dieser Anfrage ist bereits zu Ende.");
		}
		int side = battle.getSchlachtMitglied(player);
		if (side < 0)
		{
			throw new CampaignException("Der Spieler kämpft nicht mehr in dieser Schlacht.");
		}
		List<Ship> enemies = em.createQuery("select bs.ship from BattleShip bs where bs.battle = :battle and bs.side != :side and bs.ship.id > 0", Ship.class)
				.setParameter("battle", battle)
				.setParameter("side", side)
				.getResultList();
		Set<Integer> participants = campaigns.findParticipantIds(campaign.getId());
		List<String> foreign = enemies.stream().map(Ship::getOwner).filter(o -> !o.isNPC() || !participants.contains(o.getId()))
				.map(o -> o.getPlainname() + " (" + o.getId() + ")").distinct().collect(Collectors.toList());
		if (!foreign.isEmpty())
		{
			throw new CampaignException("Auf der Gegenseite kämpfen Schiffe, die keinem NPC der Kampagne gehören: " + String.join(", ", foreign) + ".");
		}
		// Docked and landed ships go along with their carrier
		List<Ship> carriersInBattle = enemies.stream().filter(s -> s.getDocked().isEmpty()).collect(Collectors.toList());
		if (carriersInBattle.isEmpty())
		{
			throw new CampaignException("Die Gegenseite hat keine Schiffe mehr.");
		}
		// Before the battle ends, so that it goes on if the ships may not be handed over
		StandardActions.checkConsignable(carriersInBattle, player);
		List<Integer> carrierIds = carriersInBattle.stream().map(Ship::getId).collect(Collectors.toList());

		CampaignBattles.end(em, battle, gamemaster);
		// Ending the battle clears it from the ships in the database only; loaded ships would write it back
		List<Ship> carriers = new ArrayList<>();
		for (int id : carrierIds)
		{
			Ship ship = em.find(Ship.class, id);
			em.refresh(ship);
			carriers.add(ship);
		}
		StandardActions.consignAll(carriers, player);

		int count = enemies.size();
		requests.addMessage(request.id, gamemaster.getId(), GmRequest.Kind.ACTION,
				"Die Gegenseite hat kapituliert: " + count + " Schiff(e) gehören jetzt dir, die Schlacht ist zu Ende.");
		return count;
	}

	/**
	 * The player answers in the conversation of an open request of theirs.
	 */
	public void playerReply(User user, int requestId, String text)
	{
		String message = checkText(text);
		GmRequest request = requests.find(requestId)
				.filter(r -> r.userId == user.getId())
				.orElseThrow(() -> new CampaignException("Diese Anfrage gibt es nicht."));
		if (!request.isOpen())
		{
			throw new CampaignException("Diese Anfrage ist bereits erledigt.");
		}
		requests.addMessage(requestId, user.getId(), GmRequest.Kind.PLAYER, message);
	}

	/**
	 * The player is done with an open request of theirs.
	 */
	public void playerClose(User user, int requestId)
	{
		GmRequest request = requests.find(requestId)
				.filter(r -> r.userId == user.getId())
				.orElseThrow(() -> new CampaignException("Diese Anfrage gibt es nicht."));
		if (!request.isOpen())
		{
			return;
		}
		requests.setStatus(request.id, GmRequest.Status.CLOSED);
		requests.addMessage(request.id, user.getId(), GmRequest.Kind.PLAYER, "(hat die Anfrage abgeschlossen)");
		requests.markReadByUser(request.id);
	}

	/**
	 * @return The player's requests from the ship that are open or have an unread answer; viewing them marks
	 * them read
	 */
	public List<GmRequest> visibleToPlayer(User user, Ship ship)
	{
		Optional<Campaign> campaign = campaigns.findOpen();
		if (campaign.isEmpty())
		{
			return List.of();
		}
		List<GmRequest> visible = requests.findVisibleToPlayer(campaign.get().getId(), user.getId(), ship.getId());
		visible.stream().filter(r -> r.unreadByUser).forEach(r -> requests.markReadByUser(r.id));
		return visible;
	}

	public List<GmRequest.Message> messages(int requestId)
	{
		return requests.findMessages(requestId);
	}

	// ---------------------------------------------------------------- game master

	/**
	 * @return The request, if it belongs to the open campaign
	 */
	public GmRequest forGamemaster(int requestId)
	{
		Campaign campaign = campaigns.findOpen().orElseThrow(() -> new CampaignException("Es läuft keine Kampagne."));
		return requests.find(requestId)
				.filter(r -> r.campaignId == campaign.getId())
				.orElseThrow(() -> new CampaignException("Diese Anfrage gibt es nicht."));
	}

	public void gamemasterReply(User gamemaster, int requestId, String text)
	{
		String message = checkText(text);
		GmRequest request = forGamemaster(requestId);
		requests.addMessage(request.id, gamemaster.getId(), GmRequest.Kind.GAMEMASTER, message);
		PM.send(gamemaster, request.userId, "Kampagne: Antwort des GameMasters",
				message + "\n\nDie ganze Unterhaltung findest du in der Ansicht deines Schiffs " + request.shipId + ".", em);
	}

	/**
	 * Runs a dialog action for the request's player and ship, as if a dialog answer had triggered it.
	 *
	 * @return What the action did
	 */
	public String runAction(User gamemaster, int requestId, String type, Params params)
	{
		GmRequest request = forGamemaster(requestId);
		DialogContext context = context(request, gamemaster);
		DialogAction action = DialogTypes.get().action(type);
		action.execute(context, params);

		String description = action.describe(params);
		String playerMessage = action.playerMessage(context, params);
		if (playerMessage != null)
		{
			requests.addMessage(request.id, gamemaster.getId(), GmRequest.Kind.ACTION, playerMessage);
		}
		return description;
	}

	/**
	 * Puts the player's dialog at the node, starting it again if it had ended.
	 */
	public void continueDialog(User gamemaster, int requestId, int nodeId)
	{
		GmRequest request = forGamemaster(requestId);
		if (request.interactionId == null)
		{
			throw new CampaignException("Die Interaktion dieser Anfrage gibt es nicht mehr.");
		}
		if (new DialogRepository().findNode(nodeId).isEmpty())
		{
			throw new CampaignException("Unbekannter Dialogknoten " + nodeId + ".");
		}
		Optional<DialogSession> session = interactions.findSessionOf(request.userId).filter(s -> s.shipId == request.shipId);
		if (session.isPresent() && session.get().interactionId == request.interactionId)
		{
			interactions.moveSession(session.get().id, nodeId);
		}
		else
		{
			session.ifPresent(s -> interactions.endSession(s.id));
			interactions.startSession(request.userId, request.shipId, request.interactionId, nodeId);
		}
		requests.addMessage(request.id, gamemaster.getId(), GmRequest.Kind.ACTION, "Das Gespräch geht weiter.");
	}

	public void setStatus(User gamemaster, int requestId, GmRequest.Status status)
	{
		GmRequest request = forGamemaster(requestId);
		requests.setStatus(request.id, status);
		requests.addMessage(request.id, gamemaster.getId(), GmRequest.Kind.ACTION,
				status == GmRequest.Status.CLOSED ? "Der GameMaster hat die Anfrage abgeschlossen." : "Der GameMaster hat die Anfrage wieder geöffnet.");
	}

	private DialogContext context(GmRequest request, User gamemaster)
	{
		Campaign campaign = runningCampaign();
		if (request.interactionId == null && request.battleId != null)
		{
			return battleContext(request, campaign, gamemaster);
		}
		Interaction interaction = Optional.ofNullable(request.interactionId).flatMap(interactions::find)
				.orElseThrow(() -> new CampaignException("Die Interaktion dieser Anfrage gibt es nicht mehr."));
		User player = em.find(User.class, request.userId);
		Ship ship = em.find(Ship.class, request.shipId);
		if (player == null || ship == null || ship.getOwner() != player)
		{
			throw new CampaignException("Das Schiff des Spielers gibt es nicht mehr oder es gehört ihm nicht mehr.");
		}
		return new DialogService(em).context(player, ship, interaction, campaign, gamemaster);
	}

	/**
	 * Actions for a request from a battle concern the NPC commanding the other side.
	 */
	private DialogContext battleContext(GmRequest request, Campaign campaign, User gamemaster)
	{
		Battle battle = em.find(Battle.class, request.battleId);
		User player = em.find(User.class, request.userId);
		Ship ship = em.find(Ship.class, request.shipId);
		if (battle == null || player == null || ship == null)
		{
			throw new CampaignException("Die Schlacht dieser Anfrage ist bereits zu Ende.");
		}
		int side = battle.getSchlachtMitglied(player);
		User npc = side < 0 ? null : battle.getCommander(1 - side);
		if (npc == null || !npc.isNPC())
		{
			throw new CampaignException("Aktionen gibt es nur, wenn ein NPC die Gegenseite führt.");
		}
		// A battle has no conversation ship; the request's ship is the player's own
		return new DialogContext(em, player, ship, npc, ObjectType.STAR_SYSTEM, battle.getSystem(), campaign, null, gamemaster,
				() -> ThreadLocalRandom.current().nextDouble(100));
	}

	private Campaign runningCampaign()
	{
		return campaigns.findOpen().filter(Campaign::isRunning).orElseThrow(() -> new CampaignException("Es läuft keine Kampagne."));
	}

	private static String checkText(String text)
	{
		String trimmed = text == null ? "" : text.trim();
		if (trimmed.isEmpty())
		{
			throw new CampaignException("Bitte schreibe dein Anliegen auf.");
		}
		if (trimmed.length() > MAX_TEXT)
		{
			throw new CampaignException("Bitte fasse dich kürzer (höchstens " + MAX_TEXT + " Zeichen).");
		}
		return trimmed;
	}
}
