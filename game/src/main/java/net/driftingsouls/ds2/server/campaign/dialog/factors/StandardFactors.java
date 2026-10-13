package net.driftingsouls.ds2.server.campaign.dialog.factors;

import net.driftingsouls.ds2.server.WellKnownConfigValue;
import net.driftingsouls.ds2.server.campaign.dialog.CampaignVariableService;
import net.driftingsouls.ds2.server.campaign.dialog.DialogContext;
import net.driftingsouls.ds2.server.campaign.dialog.DialogFactor;
import net.driftingsouls.ds2.server.campaign.dialog.ParamDef;
import net.driftingsouls.ds2.server.campaign.dialog.Params;
import net.driftingsouls.ds2.server.cargo.ItemID;
import net.driftingsouls.ds2.server.cargo.ResourceID;
import net.driftingsouls.ds2.server.cargo.Resources;
import net.driftingsouls.ds2.server.entities.Forschung;
import net.driftingsouls.ds2.server.entities.Offizier;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.ConfigService;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipTypeData;
import net.driftingsouls.ds2.server.units.UnitType;

import java.util.Arrays;
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
			return "Fähigkeit des Offiziers auf dem Schiff (0 ohne Offizier)";
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of(ParamDef.choice("ability", "Fähigkeit", "NAV", "Navigation", "ING", "Technik", "WAF", "Waffen", "SEC", "Sicherheit", "COM", "Kommando"));
		}

		@Override
		public double value(DialogContext context, Params params)
		{
			Offizier officer = context.ship.getOffizier();
			return officer == null ? 0 : officer.getAbility(Offizier.Ability.valueOf(params.get("ability")));
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
			return List.of(ParamDef.text("types", "Schiffstyp-IDs, durch Komma getrennt"));
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
			return "Schiffstyp in [" + params.get("types") + "]";
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
			return List.of(ParamDef.text("resource", "Item-ID (z.B. 16 für Nahrung, 17 für Deuterium)"));
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
			return List.of(ParamDef.integer("unit", "Einheitentyp-ID", 1));
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
			return "Einheiten " + params.get("unit");
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
			ConfigService config = new ConfigService(context.em);
			int sizeModifier = config.getValue(WellKnownConfigValue.BATTLE_VALUE_SIZE_MODIFIER);
			int dockModifier = config.getValue(WellKnownConfigValue.BATTLE_VALUE_DOCK_MODIFIER);

			List<Ship> ships = context.em.createQuery("from Ship where owner = :owner and system = :system and x = :x and y = :y and id > 0", Ship.class)
					.setParameter("owner", context.user)
					.setParameter("system", context.ship.getSystem())
					.setParameter("x", context.ship.getX())
					.setParameter("y", context.ship.getY())
					.getResultList();
			long strength = 0;
			for (Ship ship : ships)
			{
				ShipTypeData type = ship.getTypeData();
				if (type.isMilitary())
				{
					strength += (long) type.getSize() * sizeModifier + (long) type.getJDocks() * dockModifier;
				}
			}
			return strength;
		}

		@Override
		public String describe(Params params)
		{
			return "Flottenstärke";
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
			return List.of(ParamDef.text("name", "Name"), scope());
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
			return List.of(ParamDef.integer("research", "Forschungs-ID", 1));
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
			return "Forschung " + params.get("research");
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
			return List.of(ParamDef.optionalInteger("alliance", "Allianz-ID (leer = beliebige)"));
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
			return params.has("alliance") ? "Allianz " + params.get("alliance") : "in einer Allianz";
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
