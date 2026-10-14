package net.driftingsouls.ds2.server.campaign.interaction;

import net.driftingsouls.ds2.server.entities.jooq.tables.records.GmRequestRecord;
import net.driftingsouls.ds2.server.framework.Common;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static net.driftingsouls.ds2.server.campaign.CampaignDb.withDb;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.GM_REQUEST;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.GM_REQUEST_MESSAGE;

/**
 * Persistence for requests to the game master and their conversations. jOOQ-owned.
 */
public class GmRequestRepository
{
	public int create(int campaignId, int userId, int shipId, Integer interactionId, Integer nodeId)
	{
		return create(campaignId, userId, shipId, interactionId, nodeId, null);
	}

	public int create(int campaignId, int userId, int shipId, Integer interactionId, Integer nodeId, Integer battleId)
	{
		long now = Common.time();
		return withDb(db -> {
			GmRequestRecord record = db.newRecord(GM_REQUEST);
			record.setCampaignId(campaignId);
			record.setUserId(userId);
			record.setShipId(shipId);
			record.setInteractionId(interactionId);
			record.setNodeId(nodeId);
			record.setBattleId(battleId);
			record.setStatus(GmRequest.Status.OPEN.name());
			record.setCreatedAt(now);
			record.setUpdatedAt(now);
			record.setUnreadByGm((byte) 1);
			record.setUnreadByUser((byte) 0);
			record.store();
			return record.getId();
		});
	}

	public Optional<GmRequest> find(int id)
	{
		return withDb(db -> db.selectFrom(GM_REQUEST).where(GM_REQUEST.ID.eq(id)).fetchOptional().map(GmRequestRepository::toRequest));
	}

	/**
	 * @return The campaign's requests, open ones first, newest activity first
	 */
	public List<GmRequest> findForCampaign(int campaignId, boolean openOnly)
	{
		return withDb(db -> db.selectFrom(GM_REQUEST)
				.where(GM_REQUEST.CAMPAIGN_ID.eq(campaignId))
				.and(openOnly ? GM_REQUEST.STATUS.eq(GmRequest.Status.OPEN.name()) : GM_REQUEST.ID.isNotNull())
				.orderBy(GM_REQUEST.STATUS.desc(), GM_REQUEST.UPDATED_AT.desc())
				.fetch()
				.stream().map(GmRequestRepository::toRequest).collect(Collectors.toList()));
	}

	/**
	 * @return The player's requests written from the ship, open ones and those with an unread answer
	 */
	public List<GmRequest> findVisibleToPlayer(int campaignId, int userId, int shipId)
	{
		return withDb(db -> db.selectFrom(GM_REQUEST)
				.where(GM_REQUEST.CAMPAIGN_ID.eq(campaignId))
				.and(GM_REQUEST.USER_ID.eq(userId))
				.and(GM_REQUEST.SHIP_ID.eq(shipId))
				.and(GM_REQUEST.STATUS.eq(GmRequest.Status.OPEN.name()).or(GM_REQUEST.UNREAD_BY_USER.eq((byte) 1)))
				.orderBy(GM_REQUEST.ID)
				.fetch()
				.stream().map(GmRequestRepository::toRequest).collect(Collectors.toList()));
	}

	/**
	 * @return Open requests about the object of one of the interactions
	 */
	public List<GmRequest> findOpenForInteractions(List<Integer> interactionIds)
	{
		if (interactionIds.isEmpty())
		{
			return List.of();
		}
		return withDb(db -> db.selectFrom(GM_REQUEST)
				.where(GM_REQUEST.INTERACTION_ID.in(interactionIds))
				.and(GM_REQUEST.STATUS.eq(GmRequest.Status.OPEN.name()))
				.orderBy(GM_REQUEST.UPDATED_AT.desc())
				.fetch()
				.stream().map(GmRequestRepository::toRequest).collect(Collectors.toList()));
	}

	/**
	 * @return The player's requests from the battle that are open or have an unread answer
	 */
	public List<GmRequest> findVisibleInBattle(int userId, int battleId)
	{
		return withDb(db -> db.selectFrom(GM_REQUEST)
				.where(GM_REQUEST.USER_ID.eq(userId))
				.and(GM_REQUEST.BATTLE_ID.eq(battleId))
				.and(GM_REQUEST.STATUS.eq(GmRequest.Status.OPEN.name()).or(GM_REQUEST.UNREAD_BY_USER.eq((byte) 1)))
				.orderBy(GM_REQUEST.ID)
				.fetch()
				.stream().map(GmRequestRepository::toRequest).collect(Collectors.toList()));
	}

	public int countOpenUnread(int campaignId)
	{
		return withDb(db -> db.fetchCount(GM_REQUEST, GM_REQUEST.CAMPAIGN_ID.eq(campaignId)
				.and(GM_REQUEST.STATUS.eq(GmRequest.Status.OPEN.name()))
				.and(GM_REQUEST.UNREAD_BY_GM.eq((byte) 1))));
	}

	/**
	 * Adds a message and marks the request unread for the other side.
	 */
	public void addMessage(int requestId, Integer authorId, GmRequest.Kind kind, String text)
	{
		long now = Common.time();
		boolean fromPlayer = kind == GmRequest.Kind.PLAYER;
		withDb(db -> {
			db.insertInto(GM_REQUEST_MESSAGE, GM_REQUEST_MESSAGE.REQUEST_ID, GM_REQUEST_MESSAGE.AUTHOR_ID, GM_REQUEST_MESSAGE.KIND,
							GM_REQUEST_MESSAGE.TEXT, GM_REQUEST_MESSAGE.CREATED_AT)
					.values(requestId, authorId, kind.name(), text, now)
					.execute();
			return db.update(GM_REQUEST)
					.set(GM_REQUEST.UPDATED_AT, now)
					.set(fromPlayer ? GM_REQUEST.UNREAD_BY_GM : GM_REQUEST.UNREAD_BY_USER, (byte) 1)
					.where(GM_REQUEST.ID.eq(requestId))
					.execute();
		});
	}

	public List<GmRequest.Message> findMessages(int requestId)
	{
		return withDb(db -> db.selectFrom(GM_REQUEST_MESSAGE)
				.where(GM_REQUEST_MESSAGE.REQUEST_ID.eq(requestId))
				.orderBy(GM_REQUEST_MESSAGE.ID)
				.fetch()
				.stream()
				.map(r -> new GmRequest.Message(r.getId(), r.getAuthorId(), GmRequest.Kind.valueOf(r.getKind()), r.getText(), r.getCreatedAt()))
				.collect(Collectors.toList()));
	}

	public void markReadByGm(int requestId)
	{
		withDb(db -> db.update(GM_REQUEST).set(GM_REQUEST.UNREAD_BY_GM, (byte) 0).where(GM_REQUEST.ID.eq(requestId)).execute());
	}

	public void markReadByUser(int requestId)
	{
		withDb(db -> db.update(GM_REQUEST).set(GM_REQUEST.UNREAD_BY_USER, (byte) 0).where(GM_REQUEST.ID.eq(requestId)).execute());
	}

	public void setStatus(int requestId, GmRequest.Status status)
	{
		withDb(db -> db.update(GM_REQUEST)
				.set(GM_REQUEST.STATUS, status.name())
				.set(GM_REQUEST.UPDATED_AT, Common.time())
				.where(GM_REQUEST.ID.eq(requestId))
				.execute());
	}

	private static GmRequest toRequest(GmRequestRecord r)
	{
		return new GmRequest(r.getId(), r.getCampaignId(), r.getUserId(), r.getShipId(), r.getInteractionId(), r.getNodeId(), r.getBattleId(),
				GmRequest.Status.valueOf(r.getStatus()), r.getCreatedAt(), r.getUpdatedAt(), r.getUnreadByGm() != 0, r.getUnreadByUser() != 0);
	}
}
