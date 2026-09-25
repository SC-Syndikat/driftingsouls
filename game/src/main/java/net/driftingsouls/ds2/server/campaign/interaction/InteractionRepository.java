package net.driftingsouls.ds2.server.campaign.interaction;

import net.driftingsouls.ds2.server.campaign.dialog.ObjectType;
import net.driftingsouls.ds2.server.entities.jooq.tables.records.InteractionRecord;
import net.driftingsouls.ds2.server.framework.Common;
import org.jooq.DSLContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static net.driftingsouls.ds2.server.campaign.CampaignDb.withDb;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.DIALOG_SESSION;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.INTERACTION;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.INTERACTION_AUDIENCE;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.INTERACTION_LOG;

/**
 * Persistence for interactions, running dialog sessions and the interaction log. All jOOQ-owned.
 */
public class InteractionRepository
{
	public List<Interaction> findAll()
	{
		return withDb(db -> {
			List<Interaction> result = new ArrayList<>();
			for (InteractionRecord r : db.selectFrom(INTERACTION).orderBy(INTERACTION.ID).fetch())
			{
				result.add(toInteraction(db, r));
			}
			return result;
		});
	}

	public Optional<Interaction> find(int id)
	{
		return withDb(db -> db.selectFrom(INTERACTION).where(INTERACTION.ID.eq(id)).fetchOptional().map(r -> toInteraction(db, r)));
	}

	public List<Interaction> findActiveFor(ObjectType type, int objectId)
	{
		return withDb(db -> {
			List<Interaction> result = new ArrayList<>();
			for (InteractionRecord r : db.selectFrom(INTERACTION)
					.where(INTERACTION.OBJECT_TYPE.eq(type.name()))
					.and(INTERACTION.OBJECT_ID.eq(objectId))
					.and(INTERACTION.ACTIVE.eq((byte) 1))
					.orderBy(INTERACTION.ID)
					.fetch())
			{
				result.add(toInteraction(db, r));
			}
			return result;
		});
	}

	/**
	 * @return Ids of objects of the type that have at least one active interaction
	 */
	public Set<Integer> findObjectIdsWithActiveInteractions(ObjectType type)
	{
		return withDb(db -> new HashSet<>(db.selectDistinct(INTERACTION.OBJECT_ID)
				.from(INTERACTION)
				.where(INTERACTION.OBJECT_TYPE.eq(type.name()))
				.and(INTERACTION.ACTIVE.eq((byte) 1))
				.fetch(INTERACTION.OBJECT_ID)));
	}

	public int save(Interaction interaction)
	{
		return withDb(db -> {
			InteractionRecord record = interaction.id == 0 ? db.newRecord(INTERACTION) : db.fetchOne(INTERACTION, INTERACTION.ID.eq(interaction.id));
			record.setLabel(interaction.label);
			record.setObjectType(interaction.objectType.name());
			record.setObjectId(interaction.objectId);
			record.setStartNodeId(interaction.startNodeId);
			record.setNpcUserId(interaction.npcUserId);
			record.setActive((byte) (interaction.active ? 1 : 0));
			record.setMaxUsesPerUser(interaction.maxUsesPerUser);
			record.setCooldownTicks(interaction.cooldownTicks);
			record.setAudience(interaction.audience.name());
			record.store();

			int id = record.getId();
			db.deleteFrom(INTERACTION_AUDIENCE).where(INTERACTION_AUDIENCE.INTERACTION_ID.eq(id)).execute();
			for (int userId : interaction.audienceUserIds)
			{
				db.insertInto(INTERACTION_AUDIENCE, INTERACTION_AUDIENCE.INTERACTION_ID, INTERACTION_AUDIENCE.USER_ID).values(id, userId).execute();
			}
			for (int allianceId : interaction.audienceAllianceIds)
			{
				db.insertInto(INTERACTION_AUDIENCE, INTERACTION_AUDIENCE.INTERACTION_ID, INTERACTION_AUDIENCE.ALLIANCE_ID).values(id, allianceId).execute();
			}
			return id;
		});
	}

	public void setActive(int id, boolean active)
	{
		withDb(db -> db.update(INTERACTION).set(INTERACTION.ACTIVE, (byte) (active ? 1 : 0)).where(INTERACTION.ID.eq(id)).execute());
	}

	public void delete(int id)
	{
		withDb(db -> db.deleteFrom(INTERACTION).where(INTERACTION.ID.eq(id)).execute());
	}

	// ---------------------------------------------------------------- sessions

	public int startSession(int userId, int shipId, int interactionId, int nodeId)
	{
		return withDb(db -> {
			db.deleteFrom(DIALOG_SESSION).where(DIALOG_SESSION.USER_ID.eq(userId)).execute();
			var record = db.newRecord(DIALOG_SESSION);
			record.setUserId(userId);
			record.setShipId(shipId);
			record.setInteractionId(interactionId);
			record.setCurrentNodeId(nodeId);
			record.setStartedAt(Common.time());
			record.store();
			return record.getId();
		});
	}

	public Optional<DialogSession> findSession(int sessionId, int userId)
	{
		return withDb(db -> db.selectFrom(DIALOG_SESSION)
				.where(DIALOG_SESSION.ID.eq(sessionId))
				.and(DIALOG_SESSION.USER_ID.eq(userId))
				.fetchOptional()
				.map(r -> new DialogSession(r.getId(), r.getUserId(), r.getShipId(), r.getInteractionId(), r.getCurrentNodeId())));
	}

	/**
	 * @return The user's dialog in progress; a user is in at most one
	 */
	public Optional<DialogSession> findSessionOf(int userId)
	{
		return withDb(db -> db.selectFrom(DIALOG_SESSION)
				.where(DIALOG_SESSION.USER_ID.eq(userId))
				.orderBy(DIALOG_SESSION.ID.desc())
				.limit(1)
				.fetchOptional()
				.map(r -> new DialogSession(r.getId(), r.getUserId(), r.getShipId(), r.getInteractionId(), r.getCurrentNodeId())));
	}

	public void moveSession(int sessionId, int nodeId)
	{
		withDb(db -> db.update(DIALOG_SESSION).set(DIALOG_SESSION.CURRENT_NODE_ID, nodeId).where(DIALOG_SESSION.ID.eq(sessionId)).execute());
	}

	public void endSession(int sessionId)
	{
		withDb(db -> db.deleteFrom(DIALOG_SESSION).where(DIALOG_SESSION.ID.eq(sessionId)).execute());
	}

	// ---------------------------------------------------------------- log

	public void log(InteractionLogEntry entry)
	{
		withDb(db -> db.insertInto(INTERACTION_LOG, INTERACTION_LOG.CAMPAIGN_ID, INTERACTION_LOG.CAMPAIGN_TICK, INTERACTION_LOG.USER_ID,
						INTERACTION_LOG.SHIP_ID, INTERACTION_LOG.INTERACTION_ID, INTERACTION_LOG.NODE_ID, INTERACTION_LOG.OPTION_ID,
						INTERACTION_LOG.CHANCE, INTERACTION_LOG.SUCCESS, INTERACTION_LOG.ACTIONS_SUMMARY, INTERACTION_LOG.CREATED_AT)
				.values(entry.campaignId, entry.campaignTick, entry.userId, entry.shipId, entry.interactionId, entry.nodeId, entry.optionId,
						entry.chance, (byte) (entry.success ? 1 : 0), entry.actionsSummary, entry.createdAt)
				.execute());
	}

	/**
	 * @return How often the user started the interaction (logged dialog starts)
	 */
	public int countUses(int interactionId, int userId)
	{
		return withDb(db -> db.fetchCount(INTERACTION_LOG, INTERACTION_LOG.INTERACTION_ID.eq(interactionId)
				.and(INTERACTION_LOG.USER_ID.eq(userId))
				.and(INTERACTION_LOG.OPTION_ID.isNull())));
	}

	/**
	 * @return The campaign tick of the user's last start of the interaction, or {@code null}
	 */
	public Integer lastUseTick(int interactionId, int userId)
	{
		return withDb(db -> db.select(INTERACTION_LOG.CAMPAIGN_TICK)
				.from(INTERACTION_LOG)
				.where(INTERACTION_LOG.INTERACTION_ID.eq(interactionId))
				.and(INTERACTION_LOG.USER_ID.eq(userId))
				.and(INTERACTION_LOG.OPTION_ID.isNull())
				.orderBy(INTERACTION_LOG.ID.desc())
				.limit(1)
				.fetchOne(INTERACTION_LOG.CAMPAIGN_TICK));
	}

	public List<InteractionLogEntry> findLog(Integer userId, Integer interactionId, int limit)
	{
		return withDb(db -> {
			org.jooq.Condition condition = org.jooq.impl.DSL.trueCondition();
			if (userId != null)
			{
				condition = condition.and(INTERACTION_LOG.USER_ID.eq(userId));
			}
			if (interactionId != null)
			{
				condition = condition.and(INTERACTION_LOG.INTERACTION_ID.eq(interactionId));
			}
			List<InteractionLogEntry> entries = new ArrayList<>();
			for (var r : db.selectFrom(INTERACTION_LOG).where(condition).orderBy(INTERACTION_LOG.ID.desc()).limit(limit).fetch())
			{
				entries.add(new InteractionLogEntry(r.getCampaignId(), r.getCampaignTick(), r.getUserId(), r.getShipId(), r.getInteractionId(),
						r.getNodeId(), r.getOptionId(), r.getChance(), r.getSuccess() != 0, r.getActionsSummary(), r.getCreatedAt()));
			}
			return entries;
		});
	}

	private static Interaction toInteraction(DSLContext db, InteractionRecord r)
	{
		Set<Integer> users = new HashSet<>();
		Set<Integer> alliances = new HashSet<>();
		for (var a : db.selectFrom(INTERACTION_AUDIENCE).where(INTERACTION_AUDIENCE.INTERACTION_ID.eq(r.getId())).fetch())
		{
			if (a.getUserId() != null)
			{
				users.add(a.getUserId());
			}
			if (a.getAllianceId() != null)
			{
				alliances.add(a.getAllianceId());
			}
		}
		return new Interaction(r.getId(), r.getLabel(), ObjectType.valueOf(r.getObjectType()), r.getObjectId(), r.getStartNodeId(),
				r.getNpcUserId(), r.getActive() != 0, r.getMaxUsesPerUser(), r.getCooldownTicks(),
				Interaction.Audience.valueOf(r.getAudience()), users, alliances);
	}
}
