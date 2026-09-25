package net.driftingsouls.ds2.server.campaign.dialog;

import java.util.LinkedHashMap;
import java.util.Map;

import static net.driftingsouls.ds2.server.campaign.CampaignDb.withDb;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.CAMPAIGN_VARIABLE;

/**
 * Named numbers per campaign, either per player or global, for quest progress: "has talked to the
 * admiral", "phase 2 reached", "number of convoys escorted". Unset variables are 0.
 */
public class CampaignVariableService
{
	/** The user id under which global variables are stored. */
	public static final int GLOBAL = 0;

	public long get(int campaignId, int userId, String name)
	{
		Long value = withDb(db -> db.select(CAMPAIGN_VARIABLE.VALUE)
				.from(CAMPAIGN_VARIABLE)
				.where(CAMPAIGN_VARIABLE.CAMPAIGN_ID.eq(campaignId))
				.and(CAMPAIGN_VARIABLE.USER_ID.eq(userId))
				.and(CAMPAIGN_VARIABLE.NAME.eq(name))
				.fetchOne(CAMPAIGN_VARIABLE.VALUE));
		return value == null ? 0 : value;
	}

	public void set(int campaignId, int userId, String name, long value)
	{
		withDb(db -> db.insertInto(CAMPAIGN_VARIABLE, CAMPAIGN_VARIABLE.CAMPAIGN_ID, CAMPAIGN_VARIABLE.USER_ID,
						CAMPAIGN_VARIABLE.NAME, CAMPAIGN_VARIABLE.VALUE)
				.values(campaignId, userId, name, value)
				.onDuplicateKeyUpdate()
				.set(CAMPAIGN_VARIABLE.VALUE, value)
				.execute());
	}

	public void add(int campaignId, int userId, String name, long delta)
	{
		withDb(db -> db.insertInto(CAMPAIGN_VARIABLE, CAMPAIGN_VARIABLE.CAMPAIGN_ID, CAMPAIGN_VARIABLE.USER_ID,
						CAMPAIGN_VARIABLE.NAME, CAMPAIGN_VARIABLE.VALUE)
				.values(campaignId, userId, name, delta)
				.onDuplicateKeyUpdate()
				.set(CAMPAIGN_VARIABLE.VALUE, CAMPAIGN_VARIABLE.VALUE.plus(delta))
				.execute());
	}

	/**
	 * @return All variables of the campaign, keyed "userId/name"
	 */
	public Map<String, Long> list(int campaignId)
	{
		return withDb(db -> {
			Map<String, Long> values = new LinkedHashMap<>();
			for (var r : db.selectFrom(CAMPAIGN_VARIABLE)
					.where(CAMPAIGN_VARIABLE.CAMPAIGN_ID.eq(campaignId))
					.orderBy(CAMPAIGN_VARIABLE.USER_ID, CAMPAIGN_VARIABLE.NAME)
					.fetch())
			{
				values.put(r.getUserId() + "/" + r.getName(), r.getValue());
			}
			return values;
		});
	}
}
