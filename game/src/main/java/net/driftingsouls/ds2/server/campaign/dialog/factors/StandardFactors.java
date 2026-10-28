package net.driftingsouls.ds2.server.campaign.dialog.factors;

import net.driftingsouls.ds2.server.campaign.dialog.CampaignVariableService;
import net.driftingsouls.ds2.server.campaign.dialog.DialogContext;
import net.driftingsouls.ds2.server.campaign.dialog.DialogFactor;
import net.driftingsouls.ds2.server.campaign.dialog.DialogShips;
import net.driftingsouls.ds2.server.campaign.dialog.ParamChoices;
import net.driftingsouls.ds2.server.campaign.dialog.ParamDef;
import net.driftingsouls.ds2.server.campaign.dialog.Params;
import net.driftingsouls.ds2.server.cargo.ItemID;
import net.driftingsouls.ds2.server.cargo.ResourceID;
import net.driftingsouls.ds2.server.cargo.Resources;
import net.driftingsouls.ds2.server.entities.Forschung;
import net.driftingsouls.ds2.server.entities.Offizier;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipTypeData;
import net.driftingsouls.ds2.server.ships.ShipTypeFlag;
import net.driftingsouls.ds2.server.units.UnitType;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.List;

/**
 * The factors dialog options can depend on. Each is one nested class, found by its annotation.
 */
public final class StandardFactors
{
	private StandardFactors()
	{
		// namespace only
	}

	static final String NPC_TO_USER = "NPC_TO_USER";
	static final String USER_TO_NPC = "USER_TO_NPC";

	/**
	 * -1 enemy, 0 neutral, 1 friend.
	 */
	@DialogFactor.Type("RELATION")
	public static class RelationFactor implements DialogFactor
	{
		@Override
		public String getLabel()
		{
			return "Diplomatiestatus (-1 Feind, 0 neutral, 1 Freund)";
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.choice("direction", "Richtung", NPC_TO_USER, "NPC gegenüber Spieler", USER_TO_NPC, "Spieler gegenüber NPC"));
		}

		@Override
		public double value(DialogContext context, Params params)
		{
			User.Relation relation = USER_TO_NPC.equals(params.get("direction"))
					? context.user.getRelation(context.npc)
					: context.npc.getRelation(context.user);
			switch (relation)
			{
				case ENEMY:
					return -1;
				case FRIEND:
					return 1;
				default:
					return 0;
			}
		}

		@Override
		public String describe(Params params)
		{
			return USER_TO_NPC.equals(params.get("direction")) ? "Beziehung Spieler→NPC" : "Beziehung NPC→Spieler";
		}
	}

	@DialogFactor.Type("NPC_RANK")
	public static class NpcRankFactor implements DialogFactor
	{
		@Override
		public String getLabel()
		{
			return "Rang bei der NPC-Fraktion";
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of();
		}

		@Override
		public double value(DialogContext context, Params params)
		{
			return context.user.getRank(context.npc).getRank();
		}

		@Override
		public String describe(Params params)
		{
			return "Rang beim NPC";
		}
	}

	@DialogFactor.Type("TRUST")
	public static class TrustFactor implements DialogFactor
	{
		@Override
		public String getLabel()
		{
			return "Vertrauen des NPC (inkl. Allianzanteil)";
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of();
		}

		@Override
		public double value(DialogContext context, Params params)
		{
			return context.trust.trust(context.user.getId(), context.npc.getId()).getTotal();
		}

		@Override
		public String describe(Params params)
		{
			return "Vertrauen";
		}
	}

	@DialogFactor.Type("OFFICER_SKILL")
	public static class OfficerSkillFactor implements DialogFactor
	{
		@Override
		public String getLabel()
		{
			return "Fähigkeit oder Rang des Offiziers auf dem Schiff (0 ohne Offizier)";
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.choice("ability", "Fähigkeit", "NAV", "Navigation", "ING", "Technik", "WAF", "Waffen", "SEC", "Sicherheit", "COM", "Kommando", "RANK", "Rang"));
		}

		@Override
		public double value(DialogContext context, Params params)
		{
			Offizier officer = context.ship.getOffizier();
			if (officer == null)
			{
				return 0;
			}
			return "RANK".equals(params.get("ability")) ? officer.getRang() : officer.getAbility(Offizier.Ability.valueOf(params.get("ability")));
		}

		@Override
		public String describe(Params params)
		{
			return "Offizier " + params.get("ability");
		}
	}

	/**
	 * 1 if the ship is of one of the listed types, otherwise 0.
	 */
	@DialogFactor.Type("SHIP_TYPE")
	public static class ShipTypeFactor implements DialogFactor
	{
		@Override
		public String getLabel()
		{
			return "Schiffstyp ist einer von (1 ja, 0 nein)";
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.list(ParamDef.Type.SHIP_TYPES, "types", "Schiffstypen", false));
		}

		@Override
		public double value(DialogContext context, Params params)
		{
			int type = context.ship.getType();
			return Arrays.stream(params.get("types").split(","))
					.map(String::trim)
					.filter(s -> !s.isEmpty())
					.anyMatch(s -> Integer.parseInt(s) == type) ? 1 : 0;
		}

		@Override
		public String describe(Params params)
		{
			return "Schiffstyp in [" + ParamChoices.describe(ParamDef.Type.SHIP_TYPES, params.get("types")) + "]";
		}
	}

	/**
	 * Percentage of the ship type's maximum.
	 */
	@DialogFactor.Type("SHIP_STATUS")
	public static class ShipStatusFactor implements DialogFactor
	{
		@Override
		public String getLabel()
		{
			return "Zustand des Schiffs in Prozent";
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.choice("part", "Wert", "HULL", "Hülle", "SHIELDS", "Schilde", "CREW", "Crew", "ENERGY", "Energie"));
		}

		@Override
		public double value(DialogContext context, Params params)
		{
			Ship ship = context.ship;
			ShipTypeData type = ship.getTypeData();
			switch (params.get("part"))
			{
				case "SHIELDS":
					return percent(ship.getShields(), type.getShields());
				case "CREW":
					return percent(ship.getCrew(), type.getCrew());
				case "ENERGY":
					return percent(ship.getEnergy(), type.getEps());
				default:
					return percent(ship.getHull(), type.getHull());
			}
		}

		private static double percent(long current, long max)
		{
			return max <= 0 ? 100 : 100.0 * current / max;
		}

		@Override
		public String describe(Params params)
		{
			return params.get("part") + " %";
		}
	}

	/**
	 * Any number of the player's ship: its current state or a value of its ship type.
	 */
	@DialogFactor.Type("SHIP_VALUE")
	public static class ShipValueFactor implements DialogFactor
	{
		private static final Map<String, Property> PROPERTIES = new LinkedHashMap<>();

		private static class Property
		{
			final String label;
			final ToDoubleFunction<Ship> value;

			Property(String label, ToDoubleFunction<Ship> value)
			{
				this.label = label;
				this.value = value;
			}
		}

		private static void property(String key, String label, ToDoubleFunction<Ship> value)
		{
			PROPERTIES.put(key, new Property(label, value));
		}

		static
		{
			// Current state
			property("HULL", "Hülle (aktuell)", Ship::getHull);
			property("SHIELDS", "Schilde (aktuell)", Ship::getShields);
			property("ABLATIVE_ARMOR", "Ablative Panzerung (aktuell)", Ship::getAblativeArmor);
			property("CREW", "Crew (aktuell)", Ship::getCrew);
			property("ENERGY", "Energie (aktuell)", Ship::getEnergy);
			property("HEAT", "Antriebsüberhitzung", Ship::getHeat);
			property("ENGINE", "Antrieb in %", Ship::getEngine);
			property("WEAPONS", "Waffen in %", Ship::getWeapons);
			property("COMM", "Kommunikation in %", Ship::getComm);
			property("SENSORS", "Sensoren in %", Ship::getSensors);
			property("SCAN_RANGE", "Sensorreichweite (aktuell, mit Schäden)", Ship::getEffectiveScanRange);
			property("CARGO_USED", "Belegter Frachtraum", ship -> ship.getCargo().getMass());
			property("FOOD", "Nahrungsvorrat", Ship::getNahrungCargo);
			property("UNITS", "Einheiten an Bord (Masse)", ship -> ship.getUnits() == null ? 0 : ship.getUnits().getMass());
			// Ship type
			property("TYPE_SIZE", "Größe (GP)", ship -> ship.getTypeData().getSize());
			property("TYPE_HULL", "Hülle (maximal)", ship -> ship.getTypeData().getHull());
			property("TYPE_SHIELDS", "Schilde (maximal)", ship -> ship.getTypeData().getShields());
			property("TYPE_ARMOR", "Panzerung", ship -> ship.getTypeData().getPanzerung());
			property("TYPE_ABLATIVE_ARMOR", "Ablative Panzerung (maximal)", ship -> ship.getTypeData().getAblativeArmor());
			property("TYPE_CREW", "Crew (maximal)", ship -> ship.getTypeData().getCrew());
			property("TYPE_MIN_CREW", "Mindestcrew", ship -> ship.getTypeData().getMinCrew());
			property("TYPE_EPS", "Energiespeicher", ship -> ship.getTypeData().getEps());
			property("TYPE_REACTOR", "Reaktorleistung (Uran + Deuterium + Antimaterie)",
					ship -> ship.getTypeData().getRu() + ship.getTypeData().getRd() + ship.getTypeData().getRa());
			property("TYPE_REACTOR_MAX", "Maximale Reaktorleistung", ship -> ship.getTypeData().getRm());
			property("TYPE_COST", "Flugkosten je Feld", ship -> ship.getTypeData().getCost());
			property("TYPE_HEAT", "Maximale Überhitzung", ship -> ship.getTypeData().getHeat());
			property("TYPE_SENSOR_RANGE", "Sensorreichweite (Typ)", ship -> ship.getTypeData().getSensorRange());
			property("TYPE_TORPEDO_DEF", "Torpedoabwehr", ship -> ship.getTypeData().getTorpedoDef());
			property("TYPE_CARGO", "Frachtraum", ship -> ship.getTypeData().getCargo());
			property("TYPE_FOOD_CARGO", "Nahrungsspeicher", ship -> ship.getTypeData().getNahrungCargo());
			property("TYPE_HYDRO", "Nahrungsproduktion", ship -> ship.getTypeData().getHydro());
			property("TYPE_DEUT_FACTOR", "Deuterium-Sammelfaktor", ship -> ship.getTypeData().getDeutFactor());
			property("TYPE_JDOCKS", "Jägerdocks", ship -> ship.getTypeData().getJDocks());
			property("TYPE_ADOCKS", "Externe Docks", ship -> ship.getTypeData().getADocks());
			property("TYPE_UNIT_SPACE", "Einheitenplatz", ship -> ship.getTypeData().getUnitSpace());
			property("TYPE_MAX_UNIT_SIZE", "Maximale Einheitengröße", ship -> ship.getTypeData().getMaxUnitSize());
			property("TYPE_WERFT", "Werftslots", ship -> ship.getTypeData().getWerft());
			property("TYPE_RE_COST", "Unterhalt (RE)", ship -> ship.getTypeData().getReCost());
		}

		@Override
		public String getLabel()
		{
			return "Schiffswert (Zustand oder Typwert)";
		}

		@Override
		public List<ParamDef> getParams()
		{
			String[] choices = PROPERTIES.entrySet().stream()
					.flatMap(e -> java.util.stream.Stream.of(e.getKey(), e.getValue().label))
					.toArray(String[]::new);
			return List.of(ParamDef.choice("property", "Wert", choices));
		}

		@Override
		public double value(DialogContext context, Params params)
		{
			Property property = PROPERTIES.get(params.get("property"));
			return property == null ? 0 : property.value.applyAsDouble(context.ship);
		}

		@Override
		public String describe(Params params)
		{
			Property property = PROPERTIES.get(params.get("property"));
			return property == null ? params.get("property") : property.label;
		}
	}

	/**
	 * 1 if the ship type has the flag, e.g. AWACS sensors, otherwise 0.
	 */
	@DialogFactor.Type("SHIP_FLAG")
	public static class ShipFlagFactor implements DialogFactor
	{
		@Override
		public String getLabel()
		{
			return "Schiffstyp hat Eigenschaft (1 = ja, 0 = nein)";
		}

		@Override
		public List<ParamDef> getParams()
		{
			String[] choices = Arrays.stream(ShipTypeFlag.values())
					.flatMap(flag -> java.util.stream.Stream.of(flag.name(), flag.getLabel()))
					.toArray(String[]::new);
			return List.of(ParamDef.choice("flag", "Eigenschaft", choices));
		}

		@Override
		public double value(DialogContext context, Params params)
		{
			return context.ship.getTypeData().hasFlag(ShipTypeFlag.valueOf(params.get("flag"))) ? 1 : 0;
		}

		@Override
		public String describe(Params params)
		{
			try
			{
				return "Eigenschaft " + ShipTypeFlag.valueOf(params.get("flag")).getLabel();
			}
			catch (IllegalArgumentException e)
			{
				return "Eigenschaft " + params.get("flag");
			}
		}
	}

	/**
	 * A random number between the bounds, drawn anew for every evaluation; adds luck beyond the roll.
	 */
	@DialogFactor.Type("RANDOM")
	public static class RandomFactor implements DialogFactor
	{
		@Override
		public String getLabel()
		{
			return "Zufallszahl";
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.integer("from", "von", 0), ParamDef.integer("to", "bis", 10));
		}

		@Override
		public double value(DialogContext context, Params params)
		{
			int from = params.getInt("from");
			int to = params.getInt("to");
			return from + (to - from) * context.random.getAsDouble() / 100.0;
		}

		@Override
		public String describe(Params params)
		{
			return "Zufall " + params.get("from") + "–" + params.get("to");
		}
	}

	@DialogFactor.Type("CARGO")
	public static class CargoFactor implements DialogFactor
	{
		@Override
		public String getLabel()
		{
			return "Menge einer Ware / eines Items im Schiffscargo";
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.item("resource", "Ware / Item"));
		}

		@Override
		public double value(DialogContext context, Params params)
		{
			return context.ship.getCargo().getResourceCount(resource(params.get("resource")));
		}

		@Override
		public String describe(Params params)
		{
			return "Cargo " + params.get("resource");
		}
	}

	@DialogFactor.Type("UNITS")
	public static class UnitsFactor implements DialogFactor
	{
		@Override
		public String getLabel()
		{
			return "Anzahl einer Einheit auf dem Schiff";
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.reference(ParamDef.Type.UNIT_TYPE, "unit", "Einheitentyp"));
		}

		@Override
		public double value(DialogContext context, Params params)
		{
			UnitType unit = context.em.find(UnitType.class, params.getInt("unit"));
			return unit == null ? 0 : context.ship.getUnits().getUnitCount(unit);
		}

		@Override
		public String describe(Params params)
		{
			return "Einheiten " + ParamChoices.describe(ParamDef.Type.UNIT_TYPE, params.get("unit"));
		}
	}

	/**
	 * The battle value of all military ships of the player in the sector, as battles compute it.
	 */
	@DialogFactor.Type("FLEET_STRENGTH")
	public static class FleetStrengthFactor implements DialogFactor
	{
		@Override
		public String getLabel()
		{
			return "Flottenstärke des Spielers im Sektor (Schlachtwert)";
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of();
		}

		@Override
		public double value(DialogContext context, Params params)
		{
			return DialogShips.strength(context.em, DialogShips.of(context, context.user, DialogShips.Scope.SECTOR));
		}

		@Override
		public String describe(Params params)
		{
			return "Flottenstärke";
		}
	}

	/**
	 * The player's battle strength in the sector divided by the speaking NPC's: 3 means three times as strong.
	 * Without NPC ships in the sector the player counts as overwhelmingly stronger.
	 */
	@DialogFactor.Type("STRENGTH_RATIO")
	public static class StrengthRatioFactor implements DialogFactor
	{
		private static final double NO_OPPONENT = 1000;

		@Override
		public String getLabel()
		{
			return "Kampfkraft-Verhältnis Spieler / NPC im Sektor (3 = dreimal so stark)";
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of();
		}

		@Override
		public double value(DialogContext context, Params params)
		{
			long npc = DialogShips.strength(context.em, DialogShips.of(context, context.npc, DialogShips.Scope.SECTOR));
			long player = DialogShips.strength(context.em, DialogShips.of(context, context.user, DialogShips.Scope.SECTOR));
			return npc == 0 ? NO_OPPONENT : (double) player / npc;
		}

		@Override
		public String describe(Params params)
		{
			return "Kampfkraft-Verhältnis";
		}
	}

	@DialogFactor.Type("CAMPAIGN_TICKS")
	public static class CampaignTicksFactor implements DialogFactor
	{
		@Override
		public String getLabel()
		{
			return "Kampagnenticks seit Kampagnenstart";
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of();
		}

		@Override
		public double value(DialogContext context, Params params)
		{
			return context.campaignTicks();
		}

		@Override
		public String describe(Params params)
		{
			return "Kampagnenticks";
		}
	}

	@DialogFactor.Type("VARIABLE")
	public static class VariableFactor implements DialogFactor
	{
		@Override
		public String getLabel()
		{
			return "Kampagnenvariable (nicht gesetzt = 0)";
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.variable("name", "Kampagnenvariable"), scope());
		}

		@Override
		public double value(DialogContext context, Params params)
		{
			if (context.campaign == null)
			{
				return 0;
			}
			return context.variables.get(context.campaign.getId(), scopeUser(context, params), params.get("name"));
		}

		@Override
		public String describe(Params params)
		{
			return ("GLOBAL".equals(params.get("scope")) ? "globale " : "") + "Variable " + params.get("name");
		}
	}

	/**
	 * 1 if the player has the research, otherwise 0.
	 */
	@DialogFactor.Type("RESEARCH")
	public static class ResearchFactor implements DialogFactor
	{
		@Override
		public String getLabel()
		{
			return "Forschung erforscht (1 ja, 0 nein)";
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.reference(ParamDef.Type.RESEARCH, "research", "Forschung"));
		}

		@Override
		public double value(DialogContext context, Params params)
		{
			Forschung research = context.em.find(Forschung.class, params.getInt("research"));
			return research != null && context.user.hasResearched(research) ? 1 : 0;
		}

		@Override
		public String describe(Params params)
		{
			return "Forschung " + ParamChoices.describe(ParamDef.Type.RESEARCH, params.get("research"));
		}
	}

	/**
	 * 1 if the player is in an alliance (or in the given one), otherwise 0.
	 */
	@DialogFactor.Type("ALLIANCE")
	public static class AllianceFactor implements DialogFactor
	{
		@Override
		public String getLabel()
		{
			return "Mitglied einer Allianz (1 ja, 0 nein)";
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.optionalReference(ParamDef.Type.ALLIANCE, "alliance", "Allianz", "beliebige"));
		}

		@Override
		public double value(DialogContext context, Params params)
		{
			if (context.user.getAlly() == null)
			{
				return 0;
			}
			return !params.has("alliance") || context.user.getAlly().getId() == params.getInt("alliance") ? 1 : 0;
		}

		@Override
		public String describe(Params params)
		{
			return params.has("alliance") ? "Allianz " + ParamChoices.describe(ParamDef.Type.ALLIANCE, params.get("alliance")) : "in einer Allianz";
		}
	}

	/**
	 * Reads an item as a game master writes it: "16", "i16" or the full form "i16|0|0".
	 *
	 * @throws net.driftingsouls.ds2.server.campaign.CampaignException if it is no item
	 */
	public static ResourceID resource(String value)
	{
		String v = value == null ? "" : value.trim();
		if (v.matches("i?\\d+"))
		{
			return new ItemID(Integer.parseInt(v.startsWith("i") ? v.substring(1) : v));
		}
		ResourceID id = Resources.fromString(v);
		if (id == null)
		{
			throw new net.driftingsouls.ds2.server.campaign.CampaignException("Unbekannte Ressource: " + value);
		}
		return id;
	}

	static ParamDef scope()
	{
		return ParamDef.choice("scope", "Gültigkeit", "USER", "pro Spieler", "GLOBAL", "für die ganze Kampagne");
	}

	/**
	 * @return The user id a variable with these parameters is stored under
	 */
	public static int scopeUser(DialogContext context, Params params)
	{
		return "GLOBAL".equals(params.get("scope")) ? CampaignVariableService.GLOBAL : context.user.getId();
	}
}
