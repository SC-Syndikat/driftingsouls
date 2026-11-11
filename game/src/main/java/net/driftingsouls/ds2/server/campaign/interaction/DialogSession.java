package net.driftingsouls.ds2.server.campaign.interaction;

/**
 * A dialog in progress. Only options of {@link #currentNodeId} are accepted, so a player cannot jump
 * to an arbitrary node by editing the URL.
 */
public class DialogSession
{
	public final int id;
	public final int userId;
	public final int shipId;
	public final int interactionId;
	public final int currentNodeId;

	public DialogSession(int id, int userId, int shipId, int interactionId, int currentNodeId)
	{
		this.id = id;
		this.userId = userId;
		this.shipId = shipId;
		this.interactionId = interactionId;
		this.currentNodeId = currentNodeId;
	}
}
