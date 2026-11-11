package net.driftingsouls.ds2.server.campaign;

import org.jooq.Condition;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.driftingsouls.ds2.server.campaign.CampaignDb.withDb;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.BASES;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.CAMPAIGN_JUMP_NODE;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.CAMPAIGN_STAR_SYSTEM;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.SHIPS;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.USERS;

/**
 * Persistence for the campaign's star systems and jump nodes ({@code campaign_star_system},
 * {@code campaign_jump_node}, both jOOQ-owned), and read-only queries on the objects inside them.
 */
public class CampaignMapRepository
{
	public Set<Integer> findSystemIds(int campaignId)
	{
		return withDb(db -> new HashSet<>(db.select(CAMPAIGN_STAR_SYSTEM.STAR_SYSTEM_ID)
				.from(CAMPAIGN_STAR_SYSTEM)
				.where(CAMPAIGN_STAR_SYSTEM.CAMPAIGN_ID.eq(campaignId))
				.fetch(CAMPAIGN_STAR_SYSTEM.STAR_SYSTEM_ID)));
	}

	public void addSystem(int campaignId, int starSystemId, long now)
	{
		withDb(db -> db.insertInto(CAMPAIGN_STAR_SYSTEM,
						CAMPAIGN_STAR_SYSTEM.CAMPAIGN_ID, CAMPAIGN_STAR_SYSTEM.STAR_SYSTEM_ID, CAMPAIGN_STAR_SYSTEM.ADDED_AT)
				.values(campaignId, starSystemId, now)
				.onDuplicateKeyIgnore()
				.execute());
	}

	public void removeSystem(int campaignId, int starSystemId)
	{
		withDb(db -> db.deleteFrom(CAMPAIGN_STAR_SYSTEM)
				.where(CAMPAIGN_STAR_SYSTEM.CAMPAIGN_ID.eq(campaignId))
				.and(CAMPAIGN_STAR_SYSTEM.STAR_SYSTEM_ID.eq(starSystemId))
				.execute());
	}

	public Map<Integer, CampaignJumpNodeState> findJumpNodeStates()
	{
		return withDb(db -> {
			Map<Integer, CampaignJumpNodeState> states = new HashMap<>();
			for (var record : db.selectFrom(CAMPAIGN_JUMP_NODE).fetch())
			{
				states.put(record.getJumpNodeId(), CampaignJumpNodeState.valueOf(record.getState()));
			}
			return states;
		});
	}

	public void saveJumpNodeState(int jumpNodeId, CampaignJumpNodeState state)
	{
		withDb(db -> db.insertInto(CAMPAIGN_JUMP_NODE, CAMPAIGN_JUMP_NODE.JUMP_NODE_ID, CAMPAIGN_JUMP_NODE.STATE)
				.values(jumpNodeId, state.name())
				.onDuplicateKeyUpdate()
				.set(CAMPAIGN_JUMP_NODE.STATE, state.name())
				.execute());
	}

	public void removeJumpNode(int jumpNodeId)
	{
		withDb(db -> db.deleteFrom(CAMPAIGN_JUMP_NODE)
				.where(CAMPAIGN_JUMP_NODE.JUMP_NODE_ID.eq(jumpNodeId))
				.execute());
	}

	/**
	 * Ships and bases in the given star systems whose owner is a player (not an NPC), not among
	 * {@code participantIds} and not a game master.
	 */
	public List<CampaignObject> findForeignObjects(Collection<Integer> starSystemIds, Collection<Integer> participantIds)
	{
		return findPlayerObjects(starSystemIds, USERS.ID.notIn(participantIds.isEmpty() ? Set.of(0) : participantIds)
				.and(USERS.FLAGS.notContains("gamemaster")));
	}

	/**
	 * Ships and bases in the given star systems owned by players (not NPCs).
	 */
	public List<CampaignObject> findPlayerObjects(Collection<Integer> starSystemIds)
	{
		return findPlayerObjects(starSystemIds, DSL.trueCondition());
	}

	private List<CampaignObject> findPlayerObjects(Collection<Integer> starSystemIds, Condition ownerCondition)
	{
		if (starSystemIds.isEmpty())
		{
			return new ArrayList<>();
		}
		return withDb(db -> {
			List<CampaignObject> result = new ArrayList<>();
			db.select(SHIPS.ID, SHIPS.NAME, SHIPS.STAR_SYSTEM, SHIPS.X, SHIPS.Y, USERS.ID, USERS.PLAINNAME)
					.from(SHIPS)
					.join(USERS).on(SHIPS.OWNER.eq(USERS.ID))
					.where(SHIPS.STAR_SYSTEM.in(starSystemIds))
					.and(SHIPS.ID.gt(0))
					.and(USERS.ID.gt(0))
					.and(ownerCondition)
					.orderBy(USERS.ID, SHIPS.ID)
					.forEach(r -> result.add(new CampaignObject(CampaignObject.Type.SHIP, r.value1(), r.value2(),
							r.value3(), r.value4(), r.value5(), r.value6(), r.value7())));
			db.select(BASES.ID, BASES.NAME, BASES.STAR_SYSTEM, BASES.X, BASES.Y, USERS.ID, USERS.PLAINNAME)
					.from(BASES)
					.join(USERS).on(BASES.OWNER.eq(USERS.ID))
					.where(BASES.STAR_SYSTEM.in(starSystemIds))
					.and(USERS.ID.gt(0))
					.and(ownerCondition)
					.orderBy(USERS.ID, BASES.ID)
					.forEach(r -> result.add(new CampaignObject(CampaignObject.Type.BASE, r.value1(), r.value2(),
							r.value3(), r.value4(), r.value5(), r.value6(), r.value7())));
			return result;
		});
	}
}
