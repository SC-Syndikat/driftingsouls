package net.driftingsouls.ds2.server.campaign.dialog;

import net.driftingsouls.ds2.server.WellKnownConfigValue;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.ConfigService;
import org.jooq.impl.DSL;

import javax.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static net.driftingsouls.ds2.server.campaign.CampaignDb.withDb;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.NPC_TRUST_EVENT;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.NPC_TRUST_SETTING;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.USERS;

/**
 * A player's trust towards an NPC, kept as a journal across campaigns.
 *
 * <pre>
 * trust(user, npc) = sum of the user's own events
 *                  + allianceFactor(npc) * sum of ACTION events of the user's current alliance members
 * </pre>
 *
 * Game master corrections only count for the player they were made for.
 */
public class TrustService
{
	public enum Kind
	{
		ACTION,
		GM_CORRECTION
	}

	/**
	 * A player's trust split into its parts.
	 */
	public static class Trust
	{
		public final long own;
		public final long alliance;
		public final double allianceFactor;

		Trust(long own, long alliance, double allianceFactor)
		{
			this.own = own;
			this.alliance = alliance;
			this.allianceFactor = allianceFactor;
		}

		/**
		 * @return The alliance's events weighted by the factor
		 */
		public double getAllianceShare()
		{
			return alliance * allianceFactor;
		}

		public double getTotal()
		{
			return own + getAllianceShare();
		}
	}

	/**
	 * One entry of the journal.
	 */
	public static class Event
	{
		public final int id;
		public final int npcId;
		public final int userId;
		public final int delta;
		public final String reason;
		public final Kind kind;
		public final Integer campaignId;
		public final Integer createdBy;
		public final long createdAt;

		Event(int id, int npcId, int userId, int delta, String reason, Kind kind, Integer campaignId, Integer createdBy, long createdAt)
		{
			this.id = id;
			this.npcId = npcId;
			this.userId = userId;
			this.delta = delta;
			this.reason = reason;
			this.kind = kind;
			this.campaignId = campaignId;
			this.createdBy = createdBy;
			this.createdAt = createdAt;
		}
	}

	private final EntityManager em;

	public TrustService(EntityManager em)
	{
		this.em = em;
	}

	public Trust trust(int userId, int npcId)
	{
		return withDb(db -> {
			long own = sum(db.select(DSL.sum(NPC_TRUST_EVENT.DELTA))
					.from(NPC_TRUST_EVENT)
					.where(NPC_TRUST_EVENT.NPC_USER_ID.eq(npcId))
					.and(NPC_TRUST_EVENT.USER_ID.eq(userId))
					.fetchOne(0, BigDecimal.class));

			Integer allianceId = db.select(USERS.ALLY).from(USERS).where(USERS.ID.eq(userId)).fetchOne(USERS.ALLY);
			long alliance = 0;
			if (allianceId != null && allianceId != 0)
			{
				alliance = sum(db.select(DSL.sum(NPC_TRUST_EVENT.DELTA))
						.from(NPC_TRUST_EVENT)
						.join(USERS).on(NPC_TRUST_EVENT.USER_ID.eq(USERS.ID))
						.where(NPC_TRUST_EVENT.NPC_USER_ID.eq(npcId))
						.and(NPC_TRUST_EVENT.KIND.eq(Kind.ACTION.name()))
						.and(USERS.ALLY.eq(allianceId))
						.and(USERS.ID.ne(userId))
						.fetchOne(0, BigDecimal.class));
			}
			return new Trust(own, alliance, allianceFactor(npcId));
		});
	}

	public void record(int npcId, int userId, int delta, String reason, Kind kind, Integer campaignId, Integer interactionId, Integer createdBy)
	{
		withDb(db -> db.insertInto(NPC_TRUST_EVENT,
						NPC_TRUST_EVENT.NPC_USER_ID, NPC_TRUST_EVENT.USER_ID, NPC_TRUST_EVENT.DELTA, NPC_TRUST_EVENT.REASON,
						NPC_TRUST_EVENT.KIND, NPC_TRUST_EVENT.CAMPAIGN_ID, NPC_TRUST_EVENT.INTERACTION_ID,
						NPC_TRUST_EVENT.CREATED_BY, NPC_TRUST_EVENT.CREATED_AT)
				.values(npcId, userId, delta, reason, kind.name(), campaignId, interactionId, createdBy, Common.time())
				.execute());
	}

	/**
	 * Records the correction that brings the player's total trust to {@code target}.
	 *
	 * @return The delta recorded
	 */
	public int correctTo(int npcId, int userId, double target, String reason, Integer campaignId, int gamemasterId)
	{
		int delta = (int) Math.round(target - trust(userId, npcId).getTotal());
		if (delta != 0)
		{
			record(npcId, userId, delta, reason, Kind.GM_CORRECTION, campaignId, null, gamemasterId);
		}
		return delta;
	}

	public List<Event> journal(int npcId, int userId)
	{
		return withDb(db -> {
			List<Event> events = new ArrayList<>();
			for (var r : db.selectFrom(NPC_TRUST_EVENT)
					.where(NPC_TRUST_EVENT.NPC_USER_ID.eq(npcId))
					.and(NPC_TRUST_EVENT.USER_ID.eq(userId))
					.orderBy(NPC_TRUST_EVENT.ID.desc())
					.fetch())
			{
				events.add(new Event(r.getId(), r.getNpcUserId(), r.getUserId(), r.getDelta(), r.getReason(),
						Kind.valueOf(r.getKind()), r.getCampaignId(), r.getCreatedBy(), r.getCreatedAt()));
			}
			return events;
		});
	}

	public double allianceFactor(int npcId)
	{
		BigDecimal own = withDb(db -> db.select(NPC_TRUST_SETTING.ALLIANCE_FACTOR)
				.from(NPC_TRUST_SETTING)
				.where(NPC_TRUST_SETTING.NPC_USER_ID.eq(npcId))
				.fetchOne(NPC_TRUST_SETTING.ALLIANCE_FACTOR));
		return own != null ? own.doubleValue() : new ConfigService(em).getValue(WellKnownConfigValue.NPC_TRUST_ALLIANCE_FACTOR);
	}

	/**
	 * @param factor The NPC's own factor, or {@code null} to use the global default again
	 */
	public void setAllianceFactor(int npcId, Double factor)
	{
		withDb(db -> {
			if (factor == null)
			{
				return db.deleteFrom(NPC_TRUST_SETTING).where(NPC_TRUST_SETTING.NPC_USER_ID.eq(npcId)).execute();
			}
			BigDecimal value = BigDecimal.valueOf(factor);
			return db.insertInto(NPC_TRUST_SETTING, NPC_TRUST_SETTING.NPC_USER_ID, NPC_TRUST_SETTING.ALLIANCE_FACTOR)
					.values(npcId, value)
					.onDuplicateKeyUpdate()
					.set(NPC_TRUST_SETTING.ALLIANCE_FACTOR, value)
					.execute();
		});
	}

	private static long sum(BigDecimal value)
	{
		return value == null ? 0 : value.longValue();
	}
}
