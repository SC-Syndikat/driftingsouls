package net.driftingsouls.ds2.server.campaign.dialog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import static net.driftingsouls.ds2.server.campaign.CampaignDb.withDb;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.CAMPAIGN_VARIABLE;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.DIALOG_OPTION_ACTION;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.DIALOG_OPTION_CONDITION;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.DIALOG_OPTION_MODIFIER;

/**
 * Named numbers per campaign, either per player or global, for campaign progress: "has talked to the
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
	 * @return Every variable name in use: set in any campaign, or read or written by a dialog
	 */
	public SortedSet<String> knownNames()
	{
		return withDb(db -> {
			SortedSet<String> names = new TreeSet<>(db.selectDistinct(CAMPAIGN_VARIABLE.NAME).from(CAMPAIGN_VARIABLE).fetch(CAMPAIGN_VARIABLE.NAME));
			List<String> params = new ArrayList<>();
			params.addAll(db.select(DIALOG_OPTION_CONDITION.PARAMS).from(DIALOG_OPTION_CONDITION)
					.where(DIALOG_OPTION_CONDITION.TYPE.eq("VARIABLE")).fetch(DIALOG_OPTION_CONDITION.PARAMS));
			params.addAll(db.select(DIALOG_OPTION_MODIFIER.PARAMS).from(DIALOG_OPTION_MODIFIER)
					.where(DIALOG_OPTION_MODIFIER.TYPE.eq("VARIABLE")).fetch(DIALOG_OPTION_MODIFIER.PARAMS));
			params.addAll(db.select(DIALOG_OPTION_ACTION.PARAMS).from(DIALOG_OPTION_ACTION)
					.where(DIALOG_OPTION_ACTION.TYPE.in("SET_VARIABLE", "ADD_VARIABLE")).fetch(DIALOG_OPTION_ACTION.PARAMS));
			for (String json : params)
			{
				String name = Params.fromJson(json).get("name");
				if (name != null && !name.isBlank())
				{
					names.add(name);
				}
			}
			return names;
		});
	}

	public void delete(int campaignId, int userId, String name)
	{
		withDb(db -> db.deleteFrom(CAMPAIGN_VARIABLE)
				.where(CAMPAIGN_VARIABLE.CAMPAIGN_ID.eq(campaignId))
				.and(CAMPAIGN_VARIABLE.USER_ID.eq(userId))
				.and(CAMPAIGN_VARIABLE.NAME.eq(name))
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
