package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.entities.jooq.tables.records.CampaignRecord;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static net.driftingsouls.ds2.server.campaign.CampaignDb.withDb;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.CAMPAIGN;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.CAMPAIGN_PARTICIPANT;

/**
 * Persistence for {@code campaign} and {@code campaign_participant}. Both tables are written by jOOQ
 * only (docs/adr/0005).
 */
public class CampaignRepository
{
	/**
	 * @return The campaign that is being prepared or is running, if any
	 */
	public Optional<Campaign> findOpen()
	{
		return withDb(db -> db.selectFrom(CAMPAIGN)
				.where(CAMPAIGN.STATUS.ne(CampaignStatus.ENDED.name()))
				.orderBy(CAMPAIGN.ID.desc())
				.limit(1)
				.fetchOptional()
				.map(CampaignRepository::toCampaign));
	}

	public Campaign create(String name, int gamemasterId, long now)
	{
		return withDb(db -> {
			CampaignRecord record = db.newRecord(CAMPAIGN);
			record.setName(name);
			record.setGamemasterId(gamemasterId);
			record.setStatus(CampaignStatus.PREPARING.name());
			record.setCreatedAt(now);
			record.setTickCount(0);
			record.store();
			return toCampaign(record);
		});
	}

	public void markStarted(int campaignId, long now)
	{
		withDb(db -> db.update(CAMPAIGN)
				.set(CAMPAIGN.STATUS, CampaignStatus.RUNNING.name())
				.set(CAMPAIGN.STARTED_AT, now)
				.where(CAMPAIGN.ID.eq(campaignId))
				.execute());
	}

	public void markEnded(int campaignId, long now)
	{
		withDb(db -> db.update(CAMPAIGN)
				.set(CAMPAIGN.STATUS, CampaignStatus.ENDED.name())
				.set(CAMPAIGN.ENDED_AT, now)
				.where(CAMPAIGN.ID.eq(campaignId))
				.execute());
	}

	public void incrementTickCount(int campaignId)
	{
		withDb(db -> db.update(CAMPAIGN)
				.set(CAMPAIGN.TICK_COUNT, CAMPAIGN.TICK_COUNT.plus(1))
				.where(CAMPAIGN.ID.eq(campaignId))
				.execute());
	}

	public Set<Integer> findParticipantIds(int campaignId)
	{
		return withDb(db -> new HashSet<>(db.select(CAMPAIGN_PARTICIPANT.USER_ID)
				.from(CAMPAIGN_PARTICIPANT)
				.where(CAMPAIGN_PARTICIPANT.CAMPAIGN_ID.eq(campaignId))
				.fetch(CAMPAIGN_PARTICIPANT.USER_ID)));
	}

	public void addParticipants(int campaignId, Set<Integer> userIds, int addedBy, long now)
	{
		if (userIds.isEmpty())
		{
			return;
		}
		withDb(db -> {
			var insert = db.insertInto(CAMPAIGN_PARTICIPANT,
					CAMPAIGN_PARTICIPANT.CAMPAIGN_ID,
					CAMPAIGN_PARTICIPANT.USER_ID,
					CAMPAIGN_PARTICIPANT.ADDED_BY,
					CAMPAIGN_PARTICIPANT.ADDED_AT);
			for (int userId : userIds)
			{
				insert = insert.values(campaignId, userId, addedBy, now);
			}
			return insert.execute();
		});
	}

	public void removeParticipants(int campaignId, Set<Integer> userIds)
	{
		if (userIds.isEmpty())
		{
			return;
		}
		withDb(db -> db.deleteFrom(CAMPAIGN_PARTICIPANT)
				.where(CAMPAIGN_PARTICIPANT.CAMPAIGN_ID.eq(campaignId))
				.and(CAMPAIGN_PARTICIPANT.USER_ID.in(userIds))
				.execute());
	}

	private static Campaign toCampaign(CampaignRecord record)
	{
		return new Campaign(
				record.getId(),
				record.getName(),
				record.getGamemasterId(),
				CampaignStatus.valueOf(record.getStatus()),
				record.getCreatedAt(),
				record.getStartedAt(),
				record.getEndedAt(),
				record.getTickCount());
	}
}
