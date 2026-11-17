package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.entities.jooq.tables.records.GmActionLogRecord;
import net.driftingsouls.ds2.server.framework.Common;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static net.driftingsouls.ds2.server.campaign.CampaignDb.withDb;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.GM_ACTION_LOG;

/**
 * Who of the game masters did what in a campaign, so that interventions stay traceable. jOOQ-owned.
 */
public class GmActionLog
{
	/**
	 * One logged action.
	 */
	public static final class Entry
	{
		public final Integer campaignId;
		public final int campaignTick;
		public final int gamemasterId;
		public final Integer npcId;
		public final Integer userId;
		public final GmAction action;
		public final String details;
		public final long createdAt;

		Entry(Integer campaignId, int campaignTick, int gamemasterId, Integer npcId, Integer userId, GmAction action, String details,
			  long createdAt)
		{
			this.campaignId = campaignId;
			this.campaignTick = campaignTick;
			this.gamemasterId = gamemasterId;
			this.npcId = npcId;
			this.userId = userId;
			this.action = action;
			this.details = details;
			this.createdAt = createdAt;
		}
	}

	/**
	 * Logs an action for the open campaign, or without a campaign when none is open.
	 *
	 * @param gamemasterId The game master (or admin) behind the action, also while he steers an NPC
	 * @param npcId The NPC acted for, if any
	 * @param userId The player concerned, if any
	 */
	public void record(GmAction action, int gamemasterId, Integer npcId, Integer userId, String details)
	{
		Optional<Campaign> campaign = new CampaignRepository().findOpen();
		withDb(db -> {
			GmActionLogRecord record = db.newRecord(GM_ACTION_LOG);
			record.setCampaignId(campaign.map(Campaign::getId).orElse(null));
			record.setCampaignTick(campaign.map(Campaign::getTickCount).orElse(0));
			record.setGamemasterId(gamemasterId);
			record.setNpcUserId(npcId);
			record.setUserId(userId);
			record.setAction(action.name());
			record.setDetails(details == null ? "" : details);
			record.setCreatedAt(Common.time());
			record.store();
			return null;
		});
	}

	/**
	 * @param campaignId The campaign, or {@code null} for all entries
	 * @return The newest entries first
	 */
	public List<Entry> find(Integer campaignId, int limit)
	{
		return withDb(db -> db.selectFrom(GM_ACTION_LOG)
				.where(campaignId == null ? GM_ACTION_LOG.ID.isNotNull() : GM_ACTION_LOG.CAMPAIGN_ID.eq(campaignId))
				.orderBy(GM_ACTION_LOG.ID.desc())
				.limit(limit)
				.fetch()
				.stream()
				.map(r -> new Entry(r.getCampaignId(), r.getCampaignTick(), r.getGamemasterId(), r.getNpcUserId(), r.getUserId(),
						GmAction.valueOf(r.getAction()), r.getDetails(), r.getCreatedAt()))
				.collect(Collectors.toList()));
	}
}
