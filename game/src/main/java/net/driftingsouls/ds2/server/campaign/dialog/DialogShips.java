package net.driftingsouls.ds2.server.campaign.dialog;

import net.driftingsouls.ds2.server.WellKnownConfigValue;
import net.driftingsouls.ds2.server.campaign.CampaignMapRepository;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.ConfigService;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipTypeData;

import javax.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Ships around a dialog, and their battle strength, for factors and actions.
 */
public final class DialogShips
{
	/** Where to look for ships. */
	public enum Scope
	{
		SHIP("nur das Gesprächsschiff des Spielers"),
		SECTOR("im Sektor des Spielers"),
		CAMPAIGN("in allen Kampagnensystemen");

		private final String label;

		Scope(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	private DialogShips()
	{
		// static only
	}

	/**
	 * @return The owner's ships in the scope; for {@link Scope#SHIP} the player's ship of the dialog
	 */
	public static List<Ship> of(DialogContext context, User owner, Scope scope)
	{
		switch (scope)
		{
			case SHIP:
				return owner == context.user ? List.of(context.ship) : List.of();
			case SECTOR:
				return context.em.createQuery("from Ship where owner = :owner and system = :system and x = :x and y = :y and id > 0", Ship.class)
						.setParameter("owner", owner)
						.setParameter("system", context.ship.getSystem())
						.setParameter("x", context.ship.getX())
						.setParameter("y", context.ship.getY())
						.getResultList();
			default:
				Set<Integer> systems = context.campaign == null ? Set.of() : new CampaignMapRepository().findSystemIds(context.campaign.getId());
				if (systems.isEmpty())
				{
					return List.of();
				}
				return context.em.createQuery("from Ship where owner = :owner and system in (:systems) and id > 0", Ship.class)
						.setParameter("owner", owner)
						.setParameter("systems", systems)
						.getResultList();
		}
	}

	/**
	 * Battle strength as the game counts it for battle value: size and fighter docks of military ships.
	 */
	public static long strength(EntityManager em, Collection<Ship> ships)
	{
		ConfigService config = new ConfigService(em);
		int sizeModifier = config.getValue(WellKnownConfigValue.BATTLE_VALUE_SIZE_MODIFIER);
		int dockModifier = config.getValue(WellKnownConfigValue.BATTLE_VALUE_DOCK_MODIFIER);
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

	/**
	 * @return A ship of the speaking NPC in the player's sector, preferring one not in battle
	 */
	public static Optional<Ship> npcShipInSector(DialogContext context)
	{
		List<Ship> ships = of(context, context.npc, Scope.SECTOR);
		return ships.stream().filter(s -> s.getBattle() == null).findFirst().or(() -> ships.stream().findFirst());
	}
}
