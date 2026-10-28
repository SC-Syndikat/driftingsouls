package net.driftingsouls.ds2.server.campaign.interaction;

import net.driftingsouls.ds2.server.framework.Common;

import java.util.ArrayList;
import java.util.List;

import static net.driftingsouls.ds2.server.campaign.CampaignDb.withDb;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.CAMPAIGN_BATTLE_NOTE;

/**
 * Why a player is in a battle a dialog started, for the battle screen. The notes go with the battle.
 */
public class CampaignBattleNotes
{
	public static class Note
	{
		public final String speaker;
		public final String answer;
		/** BBCode; empty if the dialog ended with the answer. */
		public final String reply;

		Note(String speaker, String answer, String reply)
		{
			this.speaker = speaker;
			this.answer = answer;
			this.reply = reply;
		}
	}

	public void add(int battleId, int userId, String speaker, String answer, String reply)
	{
		withDb(db -> db.insertInto(CAMPAIGN_BATTLE_NOTE, CAMPAIGN_BATTLE_NOTE.BATTLE_ID, CAMPAIGN_BATTLE_NOTE.USER_ID,
						CAMPAIGN_BATTLE_NOTE.SPEAKER, CAMPAIGN_BATTLE_NOTE.ANSWER, CAMPAIGN_BATTLE_NOTE.REPLY, CAMPAIGN_BATTLE_NOTE.CREATED_AT)
				.values(battleId, userId, speaker, answer, reply, Common.time())
				.execute());
	}

	public List<Note> find(int battleId, int userId)
	{
		return withDb(db -> {
			List<Note> notes = new ArrayList<>();
			for (var r : db.selectFrom(CAMPAIGN_BATTLE_NOTE)
					.where(CAMPAIGN_BATTLE_NOTE.BATTLE_ID.eq(battleId))
					.and(CAMPAIGN_BATTLE_NOTE.USER_ID.eq(userId))
					.orderBy(CAMPAIGN_BATTLE_NOTE.ID)
					.fetch())
			{
				notes.add(new Note(r.getSpeaker(), r.getAnswer(), r.getReply()));
			}
			return notes;
		});
	}
}
