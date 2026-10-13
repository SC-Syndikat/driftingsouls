package net.driftingsouls.ds2.server.campaign.interaction;

import net.driftingsouls.ds2.server.Location;
import net.driftingsouls.ds2.server.bases.Base;
import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignAction;
import net.driftingsouls.ds2.server.campaign.CampaignActionGuard;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignMapRepository;
import net.driftingsouls.ds2.server.campaign.CampaignRepository;
import net.driftingsouls.ds2.server.campaign.dialog.DialogContext;
import net.driftingsouls.ds2.server.campaign.dialog.DialogEngine;
import net.driftingsouls.ds2.server.campaign.dialog.DialogModel;
import net.driftingsouls.ds2.server.campaign.dialog.DialogRepository;
import net.driftingsouls.ds2.server.campaign.dialog.ObjectType;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.ships.Ship;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * What players do with interactions: see which are offered, start a dialog, choose answers.
 *
 * <p>Every choice runs in the request's transaction. If an action refuses, the whole choice is rolled
 * back, including what earlier actions of the same choice did, and only a log entry remains.
 */
public class DialogService
{
	private final EntityManager em;
	private final CampaignRepository campaigns = new CampaignRepository();
	private final CampaignMapRepository map = new CampaignMapRepository();
	private final InteractionRepository interactions = new InteractionRepository();
	private final DialogRepository dialogs = new DialogRepository();
	private final DialogEngine engine;
	private final DoubleSupplier random;

	public DialogService(EntityManager em)
	{
		this(em, new DialogEngine(), () -> ThreadLocalRandom.current().nextDouble(100));
	}

	public DialogService(EntityManager em, DialogEngine engine, DoubleSupplier random)
	{
		this.em = em;
		this.engine = engine;
		this.random = random;
	}

	/**
	 * An object near the ship with the interactions the player may start there.
	 */
	public static class Offer
	{
		public final ObjectType type;
		public final int objectId;
		public final String name;
		public final List<Interaction> interactions;

		Offer(ObjectType type, int objectId, String name, List<Interaction> interactions)
		{
			this.type = type;
			this.objectId = objectId;
			this.name = name;
			this.interactions = interactions;
		}
	}

	/**
	 * A dialog node as the player sees it.
	 */
	public static class View
	{
		public final int sessionId;
		public final String interactionLabel;
		public final String speaker;
		public final String image;
		/** Rendered from BBCode. */
		public final String textHtml;
		public final List<OptionView> options;

		View(int sessionId, String interactionLabel, String speaker, String image, String textHtml, List<OptionView> options)
		{
			this.sessionId = sessionId;
			this.interactionLabel = interactionLabel;
			this.speaker = speaker;
			this.image = image;
			this.textHtml = textHtml;
			this.options = options;
		}
	}

	public static class OptionView
	{
		public final int id;
		public final String text;
		public final boolean available;

		OptionView(int id, String text, boolean available)
		{
			this.id = id;
			this.text = text;
			this.available = available;
		}
	}

	/**
	 * Objects in the ship's sector, and the ship's star system, with interactions the player may start.
	 */
	public List<Offer> findOffers(User user, Ship ship)
	{
		List<Offer> offers = new ArrayList<>();
		Optional<Campaign> campaign = runningCampaign();
		if (campaign.isEmpty() || !map.findSystemIds(campaign.get().getId()).contains(ship.getSystem()))
		{
			return offers;
		}

		Set<Integer> shipsWithInteractions = interactions.findObjectIdsWithActiveInteractions(ObjectType.SHIP);
		for (int shipId : shipsWithInteractions)
		{
			Ship target = em.find(Ship.class, shipId);
			if (target != null && target.getId() != ship.getId() && target.getLocation().sameSector(0, ship.getLocation(), 0))
			{
				addOffer(offers, user, ship, campaign.get(), ObjectType.SHIP, shipId, Common._plaintitle(target.getName()));
			}
		}
		for (int baseId : interactions.findObjectIdsWithActiveInteractions(ObjectType.BASE))
		{
			Base base = em.find(Base.class, baseId);
			if (base != null && base.getLocation().sameSector(0, ship.getLocation(), base.getSize()))
			{
				addOffer(offers, user, ship, campaign.get(), ObjectType.BASE, baseId, Common._plaintitle(base.getName()));
			}
		}
		addOffer(offers, user, ship, campaign.get(), ObjectType.STAR_SYSTEM, ship.getSystem(), "Signale im System");
		return offers;
	}

	private void addOffer(List<Offer> offers, User user, Ship ship, Campaign campaign, ObjectType type, int objectId, String name)
	{
		List<Interaction> available = new ArrayList<>();
		for (Interaction interaction : interactions.findActiveFor(type, objectId))
		{
			if (unavailability(interaction, user, ship, campaign).isEmpty())
			{
				available.add(interaction);
			}
		}
		if (!available.isEmpty())
		{
			offers.add(new Offer(type, objectId, name, available));
		}
	}

	/**
	 * Starts the interaction's dialog. A dialog the player was in before is dropped.
	 */
	public void start(User user, Ship ship, int interactionId)
	{
		Interaction interaction = interactions.find(interactionId).orElseThrow(() -> new CampaignException("Diese Interaktion gibt es nicht."));
		Campaign campaign = runningCampaign().orElseThrow(() -> new CampaignException("Es läuft keine Kampagne."));
		Optional<String> reason = unavailability(interaction, user, ship, campaign);
		if (reason.isPresent())
		{
			throw new CampaignException(reason.get());
		}

		interactions.startSession(user.getId(), ship.getId(), interaction.id, interaction.startNodeId);
		interactions.log(new InteractionLogEntry(campaign.getId(), campaign.getTickCount(), user.getId(), ship.getId(), interaction.id,
				interaction.startNodeId, null, null, true, "Dialog begonnen", Common.time()));
	}

	/**
	 * @return The dialog the player is in with this ship, if any
	 */
	public Optional<View> currentView(User user, Ship ship)
	{
		Optional<DialogSession> session = currentSession(user, ship);
		if (session.isEmpty())
		{
			return Optional.empty();
		}
		Optional<Interaction> interaction = interactions.find(session.get().interactionId);
		Optional<DialogModel.Node> node = dialogs.findNode(session.get().currentNodeId);
		Optional<Campaign> campaign = runningCampaign();
		if (interaction.isEmpty() || node.isEmpty() || campaign.isEmpty())
		{
			return Optional.empty();
		}
		DialogContext context = context(user, ship, interaction.get(), campaign.get(), user);
		return Optional.of(view(session.get().id, interaction.get(), node.get(), context));
	}

	View view(int sessionId, Interaction interaction, DialogModel.Node node, DialogContext context)
	{
		List<OptionView> options = new ArrayList<>();
		for (DialogModel.Option option : node.options)
		{
			boolean available = engine.evaluate(option, context).available;
			if (available || !option.hideIfUnavailable)
			{
				options.add(new OptionView(option.id, option.text, available));
			}
		}
		return new View(sessionId, interaction.label, node.speakerName, node.image, Common._text(node.text), options);
	}

	/**
	 * Carries out the player's choice of an option of the dialog's current node.
	 *
	 * @return What the player is told about the outcome, one line each: success or failure, then the actions
	 */
	public List<String> choose(User user, Ship ship, int sessionId, int optionId)
	{
		DialogSession session = interactions.findSession(sessionId, user.getId())
				.filter(s -> s.shipId == ship.getId())
				.orElseThrow(() -> new CampaignException("Dieses Gespräch ist bereits beendet."));
		Interaction interaction = interactions.find(session.interactionId).orElseThrow(() -> new CampaignException("Diese Interaktion gibt es nicht mehr."));
		Campaign campaign = runningCampaign().orElseThrow(() -> new CampaignException("Es läuft keine Kampagne."));
		DialogModel.Node node = dialogs.findNode(session.currentNodeId).orElseThrow(() -> new CampaignException("Dieses Gespräch ist bereits beendet."));
		DialogModel.Option option = node.option(optionId);
		if (option == null)
		{
			throw new CampaignException("Diese Antwort gehört nicht zum aktuellen Gespräch.");
		}

		Optional<String> reason = stillPossible(interaction, user, ship, campaign);
		if (reason.isPresent())
		{
			interactions.endSession(session.id);
			throw new CampaignException(reason.get());
		}

		DialogContext context = context(user, ship, interaction, campaign, user);
		DialogEngine.Result result;
		try
		{
			result = engine.choose(option, context, false, null);
		}
		catch (IllegalStateException e)
		{
			throw new CampaignException(e.getMessage());
		}
		catch (RuntimeException e)
		{
			rollBackAndLog(user, ship, interaction, campaign, node, option, e);
			throw e instanceof CampaignException ? (CampaignException) e : new CampaignException("Das hat nicht geklappt: " + e.getMessage());
		}

		interactions.log(new InteractionLogEntry(campaign.getId(), campaign.getTickCount(), user.getId(), ship.getId(), interaction.id, node.id,
				option.id, result.chance, result.success, String.join("; ", result.actions), Common.time()));

		if (result.nextNodeId == null)
		{
			interactions.endSession(session.id);
		}
		else
		{
			interactions.moveSession(session.id, result.nextNodeId);
		}
		List<String> outcome = new ArrayList<>();
		if (result.chance != null)
		{
			outcome.add(result.success ? "Das hat geklappt." : "Das ist leider misslungen.");
		}
		outcome.addAll(result.playerMessages);
		return outcome;
	}

	/**
	 * Leaves the dialog the player is in.
	 */
	public void quit(User user, int sessionId)
	{
		interactions.findSession(sessionId, user.getId()).ifPresent(s -> interactions.endSession(s.id));
	}

	private void rollBackAndLog(User user, Ship ship, Interaction interaction, Campaign campaign, DialogModel.Node node, DialogModel.Option option, RuntimeException e)
	{
		// Undo whatever the earlier actions of this choice changed; Hibernate and jOOQ share the transaction
		if (em.getTransaction().isActive())
		{
			em.getTransaction().rollback();
		}
		em.clear();
		em.getTransaction().begin();
		interactions.log(new InteractionLogEntry(campaign.getId(), campaign.getTickCount(), user.getId(), ship.getId(), interaction.id, node.id,
				option.id, null, false, InteractionRepository.ABORTED_PREFIX + e.getMessage(), Common.time()));
	}

	/**
	 * @return Why the player cannot start the interaction now, or empty if they can
	 */
	public Optional<String> unavailability(Interaction interaction, User user, Ship ship, Campaign campaign)
	{
		Optional<String> reason = stillPossible(interaction, user, ship, campaign);
		if (reason.isPresent())
		{
			return reason;
		}
		if (interaction.maxUsesPerUser != null && interactions.countUses(interaction.id, user.getId()) >= interaction.maxUsesPerUser)
		{
			return Optional.of("Das hast du bereits getan.");
		}
		if (interaction.cooldownTicks != null)
		{
			Integer last = interactions.lastUseTick(interaction.id, user.getId());
			if (last != null && campaign.getTickCount() - last < interaction.cooldownTicks)
			{
				int remaining = interaction.cooldownTicks - (campaign.getTickCount() - last);
				return Optional.of("Das geht erst in " + remaining + " Kampagnentick(s) wieder.");
			}
		}
		return Optional.empty();
	}

	/**
	 * The checks that must hold for every step of a dialog, not only its start.
	 */
	private Optional<String> stillPossible(Interaction interaction, User user, Ship ship, Campaign campaign)
	{
		if (!interaction.active)
		{
			return Optional.of("Dieses Gespräch ist gerade nicht möglich.");
		}
		Optional<Location> location = objectLocation(interaction);
		if (location.isEmpty() || !map.findSystemIds(campaign.getId()).contains(location.get().getSystem()))
		{
			return Optional.of("Dieses Gespräch ist gerade nicht möglich.");
		}
		boolean near = interaction.objectType == ObjectType.STAR_SYSTEM
				? ship.getSystem() == location.get().getSystem()
				: location.get().sameSector(0, ship.getLocation(), 0);
		if (!near)
		{
			return Optional.of("Dein Schiff ist nicht mehr in Reichweite.");
		}
		Optional<String> denial = new CampaignActionGuard().denial(user, ship.getSystem(), CampaignAction.INTERACT);
		if (denial.isPresent())
		{
			return denial;
		}
		Integer allianceId = user.getAlly() != null ? user.getAlly().getId() : null;
		if (!interaction.isForAudience(user.getId(), allianceId))
		{
			return Optional.of("Dieses Gespräch ist nicht für dich bestimmt.");
		}
		return Optional.empty();
	}

	private Optional<Location> objectLocation(Interaction interaction)
	{
		switch (interaction.objectType)
		{
			case SHIP:
			{
				Ship target = em.find(Ship.class, interaction.objectId);
				return target == null ? Optional.empty() : Optional.of(target.getLocation());
			}
			case BASE:
			{
				Base base = em.find(Base.class, interaction.objectId);
				return base == null ? Optional.empty() : Optional.of(base.getLocation());
			}
			default:
				return Optional.of(new Location(interaction.objectId, 0, 0));
		}
	}

	private Optional<DialogSession> currentSession(User user, Ship ship)
	{
		return interactions.findSessionOf(user.getId()).filter(s -> s.shipId == ship.getId());
	}

	DialogContext context(User user, Ship ship, Interaction interaction, Campaign campaign, User actor)
	{
		User npc = em.find(User.class, interaction.npcUserId);
		if (npc == null)
		{
			throw new CampaignException("Der Gesprächspartner existiert nicht mehr.");
		}
		return new DialogContext(em, user, ship, npc, interaction.objectType, interaction.objectId, campaign, interaction.id, actor, random);
	}

	private Optional<Campaign> runningCampaign()
	{
		return campaigns.findOpen().filter(Campaign::isRunning);
	}

	// ---------------------------------------------------------------- preview for the game master

	/**
	 * A node as the game master sees it in the preview: every option with its evaluation for the chosen
	 * player and ship.
	 */
	public static class PreviewView
	{
		public final DialogModel.Node node;
		public final String textHtml;
		public final List<DialogEngine.Evaluation> evaluations;
		/** Why the player could not start the interaction right now; empty if they could. */
		public final Optional<String> unavailability;

		PreviewView(DialogModel.Node node, String textHtml, List<DialogEngine.Evaluation> evaluations, Optional<String> unavailability)
		{
			this.node = node;
			this.textHtml = textHtml;
			this.evaluations = evaluations;
			this.unavailability = unavailability;
		}
	}

	/**
	 * Evaluates a node of the interaction's dialog for a player and ship, without changing anything.
	 */
	public PreviewView preview(Interaction interaction, User player, Ship ship, int nodeId, User gamemaster)
	{
		DialogModel.Node node = dialogs.findNode(nodeId).orElseThrow(() -> new CampaignException("Unbekannter Dialogknoten " + nodeId + "."));
		Campaign campaign = campaigns.findOpen().orElse(null);
		DialogContext context = previewContext(interaction, player, ship, campaign, gamemaster);
		List<DialogEngine.Evaluation> evaluations = new ArrayList<>();
		for (DialogModel.Option option : node.options)
		{
			evaluations.add(engine.evaluate(option, context));
		}
		Optional<String> unavailability = campaign != null && campaign.isRunning()
				? unavailability(interaction, player, ship, campaign)
				: Optional.of("Die Kampagne läuft noch nicht.");
		return new PreviewView(node, Common._text(node.text), evaluations, unavailability);
	}

	/**
	 * Plays an option in the preview: rolls (or takes the forced outcome) and describes the actions
	 * instead of running them.
	 */
	public DialogEngine.Result previewChoose(Interaction interaction, User player, Ship ship, int nodeId, int optionId, Boolean forcedOutcome, User gamemaster)
	{
		DialogModel.Node node = dialogs.findNode(nodeId).orElseThrow(() -> new CampaignException("Unbekannter Dialogknoten " + nodeId + "."));
		DialogModel.Option option = node.option(optionId);
		if (option == null)
		{
			throw new CampaignException("Die Antwort gehört nicht zu diesem Knoten.");
		}
		DialogContext context = previewContext(interaction, player, ship, campaigns.findOpen().orElse(null), gamemaster);
		try
		{
			return engine.choose(option, context, true, forcedOutcome);
		}
		catch (IllegalStateException e)
		{
			throw new CampaignException(e.getMessage());
		}
	}

	private DialogContext previewContext(Interaction interaction, User player, Ship ship, Campaign campaign, User gamemaster)
	{
		User npc = em.find(User.class, interaction.npcUserId);
		if (npc == null)
		{
			throw new CampaignException("Der Gesprächspartner existiert nicht mehr.");
		}
		return new DialogContext(em, player, ship, npc, interaction.objectType, interaction.objectId, campaign, interaction.id, gamemaster, random);
	}

	/**
	 * @return The labels of all interactions, keyed by id; for editors and the log
	 */
	public Map<Integer, String> interactionLabels()
	{
		Map<Integer, String> labels = new LinkedHashMap<>();
		for (Interaction interaction : interactions.findAll())
		{
			labels.put(interaction.id, interaction.label);
		}
		return labels;
	}
}
