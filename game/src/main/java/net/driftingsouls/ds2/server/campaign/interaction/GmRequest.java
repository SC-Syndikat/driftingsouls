package net.driftingsouls.ds2.server.campaign.interaction;

/**
 * A player's request to the game master, written from a dialog when none of the answers fits.
 */
public class GmRequest
{
	public enum Status
	{
		OPEN("offen"),
		CLOSED("erledigt");

		private final String label;

		Status(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	/**
	 * Who wrote a message of the conversation.
	 */
	public enum Kind
	{
		PLAYER,
		GAMEMASTER,
		/** What an action the game master ran did for the player. */
		ACTION
	}

	public static class Message
	{
		public final int id;
		public final Integer authorId;
		public final Kind kind;
		public final String text;
		public final long createdAt;

		public Message(int id, Integer authorId, Kind kind, String text, long createdAt)
		{
			this.id = id;
			this.authorId = authorId;
			this.kind = kind;
			this.text = text;
			this.createdAt = createdAt;
		}
	}

	public final int id;
	public final int campaignId;
	public final int userId;
	public final int shipId;
	/** {@code null} if the interaction was deleted since. */
	public final Integer interactionId;
	public final Integer nodeId;
	/** The battle the request was written from; {@code null} for requests from a dialog or once the battle ended. */
	public final Integer battleId;
	public final Status status;
	public final long createdAt;
	public final long updatedAt;
	public final boolean unreadByGm;
	public final boolean unreadByUser;

	public GmRequest(int id, int campaignId, int userId, int shipId, Integer interactionId, Integer nodeId, Integer battleId, Status status,
					 long createdAt, long updatedAt, boolean unreadByGm, boolean unreadByUser)
	{
		this.battleId = battleId;
		this.id = id;
		this.campaignId = campaignId;
		this.userId = userId;
		this.shipId = shipId;
		this.interactionId = interactionId;
		this.nodeId = nodeId;
		this.status = status;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.unreadByGm = unreadByGm;
		this.unreadByUser = unreadByUser;
	}

	public boolean isOpen()
	{
		return status == Status.OPEN;
	}
}
