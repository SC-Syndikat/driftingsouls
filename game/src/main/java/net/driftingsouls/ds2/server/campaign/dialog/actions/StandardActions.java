package net.driftingsouls.ds2.server.campaign.dialog.actions;

import net.driftingsouls.ds2.server.Location;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignJumpNodeLimitService;
import net.driftingsouls.ds2.server.campaign.CampaignJumpNodeState;
import net.driftingsouls.ds2.server.campaign.CampaignMapService;
import net.driftingsouls.ds2.server.campaign.JumpNodeLimits;
import net.driftingsouls.ds2.server.campaign.ShipGroupRepository;
import net.driftingsouls.ds2.server.campaign.ShipGroupSpawner;
import net.driftingsouls.ds2.server.campaign.ShipGroupTemplate;
import net.driftingsouls.ds2.server.campaign.dialog.DialogAction;
import net.driftingsouls.ds2.server.campaign.dialog.DialogContext;
import net.driftingsouls.ds2.server.campaign.dialog.ParamDef;
import net.driftingsouls.ds2.server.campaign.dialog.Params;
import net.driftingsouls.ds2.server.campaign.dialog.TrustService;
import net.driftingsouls.ds2.server.campaign.dialog.factors.StandardFactors;
import net.driftingsouls.ds2.server.campaign.interaction.InteractionRepository;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.cargo.ItemID;
import net.driftingsouls.ds2.server.cargo.ResourceID;
import net.driftingsouls.ds2.server.cargo.Resources;
import net.driftingsouls.ds2.server.comm.PM;
import net.driftingsouls.ds2.server.entities.Forschung;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.UserMoneyTransfer;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.ConfigService;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipTypeData;
import net.driftingsouls.ds2.server.services.SchlachtErstellenService;

import java.util.List;

/**
 * The actions dialog choices can trigger. Each is one nested class, found by its annotation.
 */
public final class StandardActions
{
	private StandardActions()
	{
		// namespace only
	}

	private static final String OBJECT = "OBJECT";
	private static final String TEMPLATE = "TEMPLATE";

	// ---------------------------------------------------------------- Diplomacy

	@DialogAction.Type("SET_RELATION")
	public static class SetRelation implements DialogAction
	{
		@Override
		public String getLabel()
		{
			return "Beziehung ändern";
		}

		@Override
		public Group getGroup()
		{
			return Group.DIPLOMACY;
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(
					ParamDef.choice("direction", "Richtung", "BOTH", "beide Richtungen", "NPC_TO_USER", "NPC gegenüber Spieler", "USER_TO_NPC", "Spieler gegenüber NPC"),
					ParamDef.choice("relation", "Beziehung", "FRIEND", "Freund", "NEUTRAL", "neutral", "ENEMY", "Feind"));
		}

		@Override
		public void execute(DialogContext context, Params params)
		{
			User.Relation relation = User.Relation.valueOf(params.get("relation"));
			String direction = params.get("direction");
			if (!"USER_TO_NPC".equals(direction))
			{
				context.npc.setRelation(context.user.getId(), relation);
			}
			if (!"NPC_TO_USER".equals(direction))
			{
				context.user.setRelation(context.npc.getId(), relation);
			}
		}

		@Override
		public String describe(Params params)
		{
			return "Beziehung (" + params.get("direction") + ") → " + params.get("relation");
		}

		@Override
		public String playerMessage(DialogContext context, Params params)
		{
			String relation;
			switch (User.Relation.valueOf(params.get("relation")))
			{
				case FRIEND:
					relation = "Freund";
					break;
				case ENEMY:
					relation = "Feind";
					break;
				default:
					relation = "neutral";
			}
			return "Beziehung zu " + context.npc.getPlainname() + ": " + relation;
		}
	}

	@DialogAction.Type("SET_NPC_RANK")
	public static class SetNpcRank implements DialogAction
	{
		@Override
		public String getLabel()
		{
			return "Rang bei der NPC-Fraktion setzen";
		}

		@Override
		public Group getGroup()
		{
			return Group.DIPLOMACY;
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.integer("rank", "Rang", 1));
		}

		@Override
		public void execute(DialogContext context, Params params)
		{
			context.user.setRank(context.npc, params.getInt("rank"));
		}

		@Override
		public String describe(Params params)
		{
			return "Rang beim NPC → " + params.get("rank");
		}

		@Override
		public String playerMessage(DialogContext context, Params params)
		{
			return "Neuer Rang bei " + context.npc.getPlainname() + ": " + params.get("rank");
		}
	}

	@DialogAction.Type("CHANGE_TRUST")
	public static class ChangeTrust implements DialogAction
	{
		@Override
		public String getLabel()
		{
			return "Vertrauen ändern";
		}

		@Override
		public Group getGroup()
		{
			return Group.DIPLOMACY;
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.integer("delta", "Änderung (negativ = Misstrauen)", 1), ParamDef.optionalText("reason", "Grund"));
		}

		@Override
		public void execute(DialogContext context, Params params)
		{
			String reason = params.has("reason") ? params.get("reason") : "Dialog";
			context.trust.record(context.npc.getId(), context.user.getId(), params.getInt("delta"), reason, TrustService.Kind.ACTION,
					context.campaign != null ? context.campaign.getId() : null, context.interactionId,
					context.actor != null ? context.actor.getId() : null);
		}

		@Override
		public String describe(Params params)
		{
			int delta = params.getInt("delta");
			return "Vertrauen " + (delta >= 0 ? "+" : "") + delta;
		}

		@Override
		public String playerMessage(DialogContext context, Params params)
		{
			int delta = params.getInt("delta");
			return "Vertrauen von " + context.npc.getPlainname() + " " + (delta >= 0 ? "+" : "") + delta;
		}
	}

	// ---------------------------------------------------------------- Rewards

	@DialogAction.Type("GIVE_RESOURCE")
	public static class GiveResource implements DialogAction
	{
		@Override
		public String getLabel()
		{
			return "Ware / Item ins Schiffscargo";
		}

		@Override
		public Group getGroup()
		{
			return Group.REWARD;
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.text("resource", "Item-ID (z.B. 16 für Nahrung, 17 für Deuterium)"), ParamDef.integer("count", "Menge", 1));
		}

		@Override
		public void execute(DialogContext context, Params params)
		{
			ResourceID resource = StandardFactors.resource(params.get("resource"));
			Ship ship = context.ship;
			Cargo cargo = new Cargo(ship.getCargo());
			cargo.addResource(resource, params.getLong("count"));
			if (cargo.getMass() > ship.getTypeData().getCargo())
			{
				throw new CampaignException("Im Frachtraum deines Schiffes ist nicht genug Platz.");
			}
			ship.setCargo(cargo);
		}

		@Override
		public String describe(Params params)
		{
			return params.get("count") + "× " + params.get("resource") + " ins Cargo";
		}

		@Override
		public String playerMessage(DialogContext context, Params params)
		{
			return "Erhalten: " + Common.ln(params.getLong("count")) + "× " + resourceName(params);
		}
	}

	@DialogAction.Type("TAKE_RESOURCE")
	public static class TakeResource implements DialogAction
	{
		@Override
		public String getLabel()
		{
			return "Ware / Item aus dem Schiffscargo abnehmen";
		}

		@Override
		public Group getGroup()
		{
			return Group.REWARD;
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.text("resource", "Item-ID (z.B. 16 für Nahrung, 17 für Deuterium)"), ParamDef.integer("count", "Menge", 1));
		}

		@Override
		public void execute(DialogContext context, Params params)
		{
			ResourceID resource = StandardFactors.resource(params.get("resource"));
			long count = params.getLong("count");
			Ship ship = context.ship;
			Cargo cargo = new Cargo(ship.getCargo());
			if (cargo.getResourceCount(resource) < count)
			{
				throw new CampaignException("Dein Schiff hat nicht genug davon an Bord.");
			}
			cargo.substractResource(resource, count);
			ship.setCargo(cargo);
		}

		@Override
		public String describe(Params params)
		{
			return params.get("count") + "× " + params.get("resource") + " aus dem Cargo abgeben";
		}

		@Override
		public String playerMessage(DialogContext context, Params params)
		{
			return "Abgegeben: " + Common.ln(params.getLong("count")) + "× " + resourceName(params);
		}
	}

	@DialogAction.Type("GIVE_CREDITS")
	public static class GiveCredits implements DialogAction
	{
		@Override
		public String getLabel()
		{
			return "RE (Credits) überweisen";
		}

		@Override
		public Group getGroup()
		{
			return Group.REWARD;
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.integer("amount", "Betrag", 1000),
					ParamDef.choice("source", "Quelle", "NPC", "vom Konto des NPC", "NONE", "neu geschaffen"));
		}

		@Override
		public void execute(DialogContext context, Params params)
		{
			long amount = params.getLong("amount");
			boolean fromNpc = "NPC".equals(params.get("source"));
			if (fromNpc && context.npc.getKonto().longValue() < amount)
			{
				throw new CampaignException(context.npc.getPlainname() + " kann diesen Betrag gerade nicht zahlen.");
			}
			context.user.transferMoneyFrom(context.npc.getId(), amount, "Kampagne: " + context.npc.getPlainname(), !fromNpc,
					UserMoneyTransfer.Transfer.AUTO);
		}

		@Override
		public String describe(Params params)
		{
			return params.get("amount") + " RE";
		}

		@Override
		public String playerMessage(DialogContext context, Params params)
		{
			return "Erhalten: " + Common.ln(params.getLong("amount")) + " RE";
		}
	}

	@DialogAction.Type("UNLOCK_ITEM")
	public static class UnlockItem implements DialogAction
	{
		@Override
		public String getLabel()
		{
			return "Item / Ware freischalten";
		}

		@Override
		public Group getGroup()
		{
			return Group.REWARD;
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.integer("item", "Item-ID", 1));
		}

		@Override
		public void execute(DialogContext context, Params params)
		{
			context.user.addKnownItem(params.getInt("item"));
		}

		@Override
		public String describe(Params params)
		{
			return "Item " + params.get("item") + " freigeschaltet";
		}

		@Override
		public String playerMessage(DialogContext context, Params params)
		{
			return "Freigeschaltet: " + Cargo.getResourceName(new ItemID(params.getInt("item")));
		}
	}

	@DialogAction.Type("UNLOCK_RESEARCH")
	public static class UnlockResearch implements DialogAction
	{
		@Override
		public String getLabel()
		{
			return "Forschung freischalten";
		}

		@Override
		public Group getGroup()
		{
			return Group.REWARD;
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.integer("research", "Forschungs-ID", 1));
		}

		@Override
		public void execute(DialogContext context, Params params)
		{
			Forschung research = context.em.find(Forschung.class, params.getInt("research"));
			if (research == null)
			{
				throw new CampaignException("Unbekannte Forschung " + params.get("research") + ".");
			}
			if (!context.user.hasResearched(research))
			{
				context.user.addResearch(research);
			}
		}

		@Override
		public String describe(Params params)
		{
			return "Forschung " + params.get("research") + " freigeschaltet";
		}

		@Override
		public String playerMessage(DialogContext context, Params params)
		{
			Forschung research = context.em.find(Forschung.class, params.getInt("research"));
			return "Forschung erhalten: " + (research == null ? params.get("research") : Common._plaintitle(research.getName()));
		}
	}

	@DialogAction.Type("GIVE_SHIP")
	public static class GiveShip implements DialogAction
	{
		@Override
		public String getLabel()
		{
			return "Schiff übergeben";
		}

		@Override
		public Group getGroup()
		{
			return Group.REWARD;
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.choice("source", "Schiff", OBJECT, "das Gesprächsschiff", TEMPLATE, "neu aus einer Schiffsgruppen-Vorlage"),
					ParamDef.optionalInteger("template", "Vorlagen-ID (bei Vorlage)"));
		}

		@Override
		public void execute(DialogContext context, Params params)
		{
			if (TEMPLATE.equals(params.get("source")))
			{
				new ShipGroupSpawner(context.em).spawn(template(params), context.user, context.ship.getLocation());
				return;
			}
			Ship ship = context.requireObjectShip();
			Ship.MESSAGE.get().setLength(0);
			if (ship.consign(context.user, false))
			{
				throw new CampaignException("Das Schiff kann nicht übergeben werden: " + Ship.MESSAGE.getMessage());
			}
		}

		@Override
		public String describe(Params params)
		{
			return TEMPLATE.equals(params.get("source")) ? "Schiffe aus Vorlage " + params.get("template") + " übergeben" : "Gesprächsschiff übergeben";
		}

		@Override
		public String playerMessage(DialogContext context, Params params)
		{
			return TEMPLATE.equals(params.get("source")) ? "Dir wurden neue Schiffe übergeben." : "Das Schiff gehört jetzt dir.";
		}
	}

	// ---------------------------------------------------------------- Battle

	@DialogAction.Type("START_BATTLE")
	public static class StartBattle implements DialogAction
	{
		@Override
		public String getLabel()
		{
			return "Schlacht beginnen (der NPC greift an)";
		}

		@Override
		public Group getGroup()
		{
			return Group.BATTLE;
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.choice("source", "Angreifer", OBJECT, "das Gesprächsschiff", TEMPLATE, "neu aus einer Schiffsgruppen-Vorlage"),
					ParamDef.optionalInteger("template", "Vorlagen-ID (bei Vorlage)"));
		}

		@Override
		public void execute(DialogContext context, Params params)
		{
			Ship attacker;
			if (TEMPLATE.equals(params.get("source")))
			{
				List<Ship> ships = new ShipGroupSpawner(context.em).spawn(template(params), context.npc, context.ship.getLocation());
				if (ships.isEmpty())
				{
					throw new CampaignException("Die Vorlage enthält keine Schiffe.");
				}
				attacker = ships.get(0);
			}
			else
			{
				attacker = context.requireObjectShip();
			}
			try
			{
				new SchlachtErstellenService(context.em, new ConfigService(context.em))
						.erstelle(attacker.getOwner(), attacker, context.ship, true);
			}
			catch (IllegalArgumentException e)
			{
				throw new CampaignException(e.getMessage());
			}
		}

		@Override
		public String describe(Params params)
		{
			return TEMPLATE.equals(params.get("source")) ? "Schlacht gegen Vorlage " + params.get("template") : "Schlacht gegen das Gesprächsschiff";
		}

		@Override
		public String playerMessage(DialogContext context, Params params)
		{
			return "Dein Schiff wird angegriffen – eine Schlacht beginnt!";
		}
	}

	// ---------------------------------------------------------------- Script

	@DialogAction.Type("SPAWN_SHIP_GROUP")
	public static class SpawnShipGroup implements DialogAction
	{
		@Override
		public String getLabel()
		{
			return "Schiffsgruppe erscheinen lassen (Verstärkung)";
		}

		@Override
		public Group getGroup()
		{
			return Group.SCRIPT;
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.integer("template", "Vorlagen-ID", 1),
					ParamDef.optionalInteger("owner", "Besitzer (leer = der sprechende NPC)"),
					ParamDef.optionalText("location", "Ort system:x/y (leer = Sektor des Spielers)"));
		}

		@Override
		public void execute(DialogContext context, Params params)
		{
			User owner = context.npc;
			if (params.has("owner"))
			{
				owner = context.em.find(User.class, params.getInt("owner"));
				if (owner == null)
				{
					throw new CampaignException("Unbekannter Besitzer " + params.get("owner") + ".");
				}
			}
			Location location = params.has("location") ? Location.fromString(params.get("location")) : context.ship.getLocation();
			new ShipGroupSpawner(context.em).spawn(template(params), owner, location);
		}

		@Override
		public String describe(Params params)
		{
			return "Vorlage " + params.get("template") + " erscheint" + (params.has("location") ? " bei " + params.get("location") : " im Sektor");
		}
	}

	@DialogAction.Type("REPAIR_SHIP")
	public static class RepairShip implements DialogAction
	{
		@Override
		public String getLabel()
		{
			return "Schiff des Spielers reparieren";
		}

		@Override
		public Group getGroup()
		{
			return Group.SCRIPT;
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of();
		}

		@Override
		public void execute(DialogContext context, Params params)
		{
			Ship ship = context.ship;
			ShipTypeData type = ship.getTypeData();
			ship.setHull(type.getHull());
			ship.setShields(type.getShields());
			ship.setAblativeArmor(type.getAblativeArmor());
			ship.setEngine(100);
			ship.setWeapons(100);
			ship.setComm(100);
			ship.setSensors(100);
			ship.recalculateShipStatus();
		}

		@Override
		public String describe(Params params)
		{
			return "Schiff repariert";
		}

		@Override
		public String playerMessage(DialogContext context, Params params)
		{
			return "Dein Schiff wurde repariert.";
		}
	}

	@DialogAction.Type("SET_VARIABLE")
	public static class SetVariable implements DialogAction
	{
		@Override
		public String getLabel()
		{
			return "Kampagnenvariable setzen";
		}

		@Override
		public Group getGroup()
		{
			return Group.SCRIPT;
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.text("name", "Name"), scope(), ParamDef.integer("value", "Wert", 1));
		}

		@Override
		public void execute(DialogContext context, Params params)
		{
			context.variables.set(context.requireCampaign().getId(), StandardFactors.scopeUser(context, params), params.get("name"), params.getLong("value"));
		}

		@Override
		public String describe(Params params)
		{
			return "Variable " + params.get("name") + " = " + params.get("value");
		}
	}

	@DialogAction.Type("ADD_VARIABLE")
	public static class AddVariable implements DialogAction
	{
		@Override
		public String getLabel()
		{
			return "Kampagnenvariable erhöhen / verringern";
		}

		@Override
		public Group getGroup()
		{
			return Group.SCRIPT;
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.text("name", "Name"), scope(), ParamDef.integer("delta", "Änderung", 1));
		}

		@Override
		public void execute(DialogContext context, Params params)
		{
			context.variables.add(context.requireCampaign().getId(), StandardFactors.scopeUser(context, params), params.get("name"), params.getLong("delta"));
		}

		@Override
		public String describe(Params params)
		{
			return "Variable " + params.get("name") + " += " + params.get("delta");
		}
	}

	@DialogAction.Type("SET_INTERACTION_ACTIVE")
	public static class SetInteractionActive implements DialogAction
	{
		@Override
		public String getLabel()
		{
			return "Interaktion (de)aktivieren (Kampagnenphasen)";
		}

		@Override
		public Group getGroup()
		{
			return Group.SCRIPT;
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.integer("interaction", "Interaktions-ID", 1),
					ParamDef.choice("active", "Zustand", "1", "aktiv", "0", "inaktiv"));
		}

		@Override
		public void execute(DialogContext context, Params params)
		{
			InteractionRepository repository = new InteractionRepository();
			if (repository.find(params.getInt("interaction")).isEmpty())
			{
				throw new CampaignException("Unbekannte Interaktion " + params.get("interaction") + ".");
			}
			repository.setActive(params.getInt("interaction"), "1".equals(params.get("active")));
		}

		@Override
		public String describe(Params params)
		{
			return "Interaktion " + params.get("interaction") + ("1".equals(params.get("active")) ? " aktiviert" : " deaktiviert");
		}
	}

	@DialogAction.Type("SET_JUMP_NODE_STATE")
	public static class SetJumpNodeState implements DialogAction
	{
		@Override
		public String getLabel()
		{
			return "Kampagnen-Sprungpunkt (de)aktivieren";
		}

		@Override
		public Group getGroup()
		{
			return Group.SCRIPT;
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.integer("node", "Sprungpunkt-ID", 1),
					ParamDef.choice("state", "Zustand", "ACTIVE", "aktiv", "VISIBLE", "sichtbar, gesperrt", "PREPARED", "unsichtbar, gesperrt"));
		}

		@Override
		public void execute(DialogContext context, Params params)
		{
			new CampaignMapService(context.em).setJumpNodeState(params.getInt("node"), CampaignJumpNodeState.valueOf(params.get("state")));
		}

		@Override
		public String describe(Params params)
		{
			return "Sprungpunkt " + params.get("node") + " → " + CampaignJumpNodeState.valueOf(params.get("state")).getLabel();
		}
	}

	/**
	 * Changes the limits of a campaign jump node. Empty parameters leave a limit as it is, "-" removes it.
	 */
	@DialogAction.Type("SET_JUMP_NODE_LIMITS")
	public static class SetJumpNodeLimits implements DialogAction
	{
		private static final String KEEP = "KEEP";

		@Override
		public String getLabel()
		{
			return "Limits eines Kampagnen-Sprungpunkts ändern";
		}

		@Override
		public Group getGroup()
		{
			return Group.SCRIPT;
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.integer("node", "Sprungpunkt-ID", 1),
					ParamDef.optionalText("classes", "Erlaubte Schiffsklassen (z.B. JAEGER,KORVETTE; leer = unverändert, - = alle)"),
					ParamDef.optionalText("types", "Erlaubte Schiffstyp-IDs (leer = unverändert, - = alle)"),
					ParamDef.optionalText("perShip", "Max. GP je Schiff (leer = unverändert, - = unbegrenzt)"),
					ParamDef.optionalText("perPlayer", "Max. GP je Spieler (leer = unverändert, - = unbegrenzt)"),
					ParamDef.choice("period", "Spieler-Limit gilt", KEEP, "unverändert", "TOTAL", "für die ganze Kampagne", "PER_TICK", "pro Kampagnentick"),
					ParamDef.choice("metric", "Gezählt wird", KEEP, "unverändert", "SIZE", "Größenpunkte (GP)", "BATTLE_VALUE", "Schlachtwert"));
		}

		@Override
		public void execute(DialogContext context, Params params)
		{
			CampaignJumpNodeLimitService service = new CampaignJumpNodeLimitService(context.em);
			int node = params.getInt("node");
			JumpNodeLimits limits = service.load(node);
			if (params.has("classes"))
			{
				limits = limits.withAllowedShipClasses("-".equals(params.get("classes")) ? java.util.Set.of() : names(params.get("classes")));
			}
			if (params.has("types"))
			{
				limits = limits.withAllowedShipTypes("-".equals(params.get("types")) ? java.util.Set.of() : ids(params.get("types")));
			}
			if (params.has("perShip"))
			{
				limits = limits.withMaxPointsPerShip(number(params.get("perShip")));
			}
			if (params.has("perPlayer"))
			{
				limits = limits.withMaxPointsPerPlayer(number(params.get("perPlayer")));
			}
			if (!KEEP.equals(params.get("period")))
			{
				limits = limits.withPeriod(JumpNodeLimits.Period.valueOf(params.get("period")));
			}
			if (!KEEP.equals(params.get("metric")))
			{
				limits = limits.withMetric(JumpNodeLimits.Metric.valueOf(params.get("metric")));
			}
			service.save(node, limits);
		}

		private static java.util.Set<String> names(String csv)
		{
			java.util.Set<String> names = new java.util.LinkedHashSet<>();
			for (String name : csv.split("[,;\\s]+"))
			{
				if (!name.isBlank())
				{
					names.add(net.driftingsouls.ds2.server.ships.ShipClasses.valueOf(name.trim().toUpperCase()).name());
				}
			}
			return names;
		}

		private static java.util.Set<Integer> ids(String csv)
		{
			java.util.Set<Integer> ids = new java.util.LinkedHashSet<>();
			for (String id : csv.split("[,;\\s]+"))
			{
				if (!id.isBlank())
				{
					ids.add(Integer.parseInt(id.trim()));
				}
			}
			return ids;
		}

		private static Integer number(String value)
		{
			return "-".equals(value) ? null : Integer.valueOf(value.trim());
		}

		@Override
		public String describe(Params params)
		{
			StringBuilder text = new StringBuilder("Sprungpunkt " + params.get("node") + ":");
			for (String name : List.of("classes", "types", "perShip", "perPlayer"))
			{
				if (params.has(name))
				{
					text.append(" ").append(name).append("=").append(params.get(name));
				}
			}
			if (!KEEP.equals(params.get("period")))
			{
				text.append(" Zeitraum=").append(params.get("period"));
			}
			if (!KEEP.equals(params.get("metric")))
			{
				text.append(" Punkte=").append(params.get("metric"));
			}
			return text.toString();
		}
	}

	@DialogAction.Type("SEND_PM")
	public static class SendPm implements DialogAction
	{
		@Override
		public String getLabel()
		{
			return "Nachricht (PM) senden";
		}

		@Override
		public Group getGroup()
		{
			return Group.SCRIPT;
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.choice("to", "An", "PLAYER", "den Spieler", "GAMEMASTER", "den GameMaster"),
					ParamDef.text("title", "Betreff"), ParamDef.longText("text", "Text ({spieler} = Name des Spielers)"));
		}

		@Override
		public void execute(DialogContext context, Params params)
		{
			int to = context.user.getId();
			if ("GAMEMASTER".equals(params.get("to")))
			{
				Integer gamemaster = context.requireCampaign().getGamemasterId();
				if (gamemaster == null)
				{
					return;
				}
				to = gamemaster;
			}
			String text = params.get("text").replace("{spieler}", context.user.getPlainname());
			PM.send(context.npc, to, params.get("title"), text, context.em);
		}

		@Override
		public String describe(Params params)
		{
			return "PM an " + ("GAMEMASTER".equals(params.get("to")) ? "den GameMaster" : "den Spieler") + ": " + params.get("title");
		}
	}

	private static ParamDef scope()
	{
		return ParamDef.choice("scope", "Gültigkeit", "USER", "pro Spieler", "GLOBAL", "für die ganze Kampagne");
	}

	private static String resourceName(Params params)
	{
		return Common._plaintitle(Cargo.getResourceName(StandardFactors.resource(params.get("resource"))));
	}

	private static ShipGroupTemplate template(Params params)
	{
		return new ShipGroupRepository().find(params.getInt("template"))
				.orElseThrow(() -> new CampaignException("Unbekannte Schiffsgruppen-Vorlage " + params.get("template") + "."));
	}
}
