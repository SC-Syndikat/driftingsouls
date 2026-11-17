package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.WellKnownConfigValue;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.UserFlag;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.ConfigService;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipTypeData;
import org.jooq.impl.DSL;

import javax.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

import static net.driftingsouls.ds2.server.campaign.CampaignDb.withDb;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.CAMPAIGN_JUMP_NODE;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.CAMPAIGN_JUMP_NODE_PASSAGE;

/**
 * Enforces the limits of campaign jump nodes and records every passage, which the per-player limit is
 * counted from. NPC accounts and game masters are not limited.
 */
public class CampaignJumpNodeLimitService
{
	private final EntityManager em;
	private final CampaignRepository campaigns = new CampaignRepository();

	public CampaignJumpNodeLimitService(EntityManager em)
	{
		this.em = em;
	}

	/**
	 * @return The node's limits; {@link JumpNodeLimits#NONE} for nodes that are not campaign jump nodes
	 */
	public JumpNodeLimits load(int jumpNodeId)
	{
		return withDb(db -> db.selectFrom(CAMPAIGN_JUMP_NODE)
				.where(CAMPAIGN_JUMP_NODE.JUMP_NODE_ID.eq(jumpNodeId))
				.fetchOptional()
				.map(r -> new JumpNodeLimits(
						JumpNodeLimits.parseNames(r.getAllowedShipClasses()),
						JumpNodeLimits.parseIds(r.getAllowedShipTypes()),
						r.getMaxPointsPerShip(),
						r.getMaxPointsPerPlayer(),
						JumpNodeLimits.Period.valueOf(r.getPlayerLimitPeriod()),
						JumpNodeLimits.Metric.valueOf(r.getPointsMetric())))
				.orElse(JumpNodeLimits.NONE));
	}

	public void save(int jumpNodeId, JumpNodeLimits limits)
	{
		int updated = withDb(db -> db.update(CAMPAIGN_JUMP_NODE)
				.set(CAMPAIGN_JUMP_NODE.ALLOWED_SHIP_CLASSES, String.join(",", limits.allowedShipClasses))
				.set(CAMPAIGN_JUMP_NODE.ALLOWED_SHIP_TYPES, limits.allowedShipTypes.stream().map(String::valueOf).collect(Collectors.joining(",")))
				.set(CAMPAIGN_JUMP_NODE.MAX_POINTS_PER_SHIP, limits.maxPointsPerShip)
				.set(CAMPAIGN_JUMP_NODE.MAX_POINTS_PER_PLAYER, limits.maxPointsPerPlayer)
				.set(CAMPAIGN_JUMP_NODE.PLAYER_LIMIT_PERIOD, limits.playerLimitPeriod.name())
				.set(CAMPAIGN_JUMP_NODE.POINTS_METRIC, limits.metric.name())
				.where(CAMPAIGN_JUMP_NODE.JUMP_NODE_ID.eq(jumpNodeId))
				.execute());
		if (updated == 0)
		{
			throw new CampaignException("Sprungpunkt " + jumpNodeId + " ist kein Kampagnen-Sprungpunkt.");
		}
	}

	/**
	 * Checks a jump of the given ships (fleet and everything docked or landed) through the node.
	 *
	 * @return Why the jump is refused, or empty if it is allowed
	 */
	public Optional<String> check(User user, int jumpNodeId, Collection<Ship> ships)
	{
		if (exempt(user))
		{
			return Optional.empty();
		}
		JumpNodeLimits limits = load(jumpNodeId);
		if (limits.isUnlimited())
		{
			return Optional.empty();
		}

		long total = 0;
		for (Ship ship : ships)
		{
			ShipTypeData type = ship.getTypeData();
			if (!limits.allowedShipClasses.isEmpty() && !limits.allowedShipClasses.contains(type.getShipClass().name()))
			{
				return Optional.of(ship.getName() + " (" + ship.getId() + "): Schiffe der Klasse " + type.getShipClass().getSingular()
						+ " können diesen Sprungpunkt nicht passieren.");
			}
			if (!limits.allowedShipTypes.isEmpty() && !limits.allowedShipTypes.contains(ship.getType()))
			{
				return Optional.of(ship.getName() + " (" + ship.getId() + "): Dieser Schiffstyp kann den Sprungpunkt nicht passieren.");
			}
			long points = points(type, limits.metric);
			if (limits.maxPointsPerShip != null && points > limits.maxPointsPerShip)
			{
				return Optional.of(ship.getName() + " (" + ship.getId() + ") ist zu groß für diesen Sprungpunkt (" + points + " von höchstens "
						+ limits.maxPointsPerShip + ").");
			}
			total += points;
		}

		if (limits.maxPointsPerPlayer != null)
		{
			Optional<Campaign> campaign = campaigns.findOpen();
			long used = campaign.map(c -> usedPoints(jumpNodeId, c, user.getId(), limits.playerLimitPeriod)).orElse(0L);
			if (used + total > limits.maxPointsPerPlayer)
			{
				return Optional.of("Der Sprungpunkt lässt nur noch " + Math.max(0, limits.maxPointsPerPlayer - used) + " Punkte für dich durch "
						+ limits.playerLimitPeriod.getLabel() + ", dieser Sprung bräuchte " + total + ".");
			}
		}
		return Optional.empty();
	}

	/**
	 * Records a passage through a campaign jump node. Does nothing for other nodes or exempt users.
	 */
	public void record(User user, int jumpNodeId, Collection<Ship> ships)
	{
		Optional<Campaign> campaign = campaigns.findOpen();
		if (exempt(user) || campaign.isEmpty() || !isCampaignJumpNode(jumpNodeId))
		{
			return;
		}
		JumpNodeLimits limits = load(jumpNodeId);
		long points = ships.stream().mapToLong(s -> points(s.getTypeData(), limits.metric)).sum();
		withDb(db -> db.insertInto(CAMPAIGN_JUMP_NODE_PASSAGE, CAMPAIGN_JUMP_NODE_PASSAGE.JUMP_NODE_ID, CAMPAIGN_JUMP_NODE_PASSAGE.CAMPAIGN_ID,
						CAMPAIGN_JUMP_NODE_PASSAGE.CAMPAIGN_TICK, CAMPAIGN_JUMP_NODE_PASSAGE.USER_ID, CAMPAIGN_JUMP_NODE_PASSAGE.SHIP_COUNT,
						CAMPAIGN_JUMP_NODE_PASSAGE.POINTS, CAMPAIGN_JUMP_NODE_PASSAGE.CREATED_AT)
				.values(jumpNodeId, campaign.get().getId(), campaign.get().getTickCount(), user.getId(), ships.size(), (int) points, Common.time())
				.execute());
	}

	/**
	 * @return The points the user has already taken through the node in the period
	 */
	public long usedPoints(int jumpNodeId, Campaign campaign, int userId, JumpNodeLimits.Period period)
	{
		BigDecimal sum = withDb(db -> {
			var condition = CAMPAIGN_JUMP_NODE_PASSAGE.JUMP_NODE_ID.eq(jumpNodeId)
					.and(CAMPAIGN_JUMP_NODE_PASSAGE.CAMPAIGN_ID.eq(campaign.getId()))
					.and(CAMPAIGN_JUMP_NODE_PASSAGE.USER_ID.eq(userId));
			if (period == JumpNodeLimits.Period.PER_TICK)
			{
				condition = condition.and(CAMPAIGN_JUMP_NODE_PASSAGE.CAMPAIGN_TICK.eq(campaign.getTickCount()));
			}
			return db.select(DSL.sum(CAMPAIGN_JUMP_NODE_PASSAGE.POINTS)).from(CAMPAIGN_JUMP_NODE_PASSAGE).where(condition).fetchOne(0, BigDecimal.class);
		});
		return sum == null ? 0 : sum.longValue();
	}

	long points(ShipTypeData type, JumpNodeLimits.Metric metric)
	{
		if (metric == JumpNodeLimits.Metric.SIZE)
		{
			return type.getSize();
		}
		if (!type.isMilitary())
		{
			return 0;
		}
		ConfigService config = new ConfigService(em);
		int sizeModifier = config.getValue(WellKnownConfigValue.BATTLE_VALUE_SIZE_MODIFIER);
		int dockModifier = config.getValue(WellKnownConfigValue.BATTLE_VALUE_DOCK_MODIFIER);
		return (long) type.getSize() * sizeModifier + (long) type.getJDocks() * dockModifier;
	}

	private boolean isCampaignJumpNode(int jumpNodeId)
	{
		return withDb(db -> db.fetchExists(CAMPAIGN_JUMP_NODE, CAMPAIGN_JUMP_NODE.JUMP_NODE_ID.eq(jumpNodeId)));
	}

	private static boolean exempt(User user)
	{
		return user == null || user.isNPC() || user.hasFlag(UserFlag.GAMEMASTER);
	}
}
