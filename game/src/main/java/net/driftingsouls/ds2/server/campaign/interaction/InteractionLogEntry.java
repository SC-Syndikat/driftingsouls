package net.driftingsouls.ds2.server.campaign.interaction;

/**
 * One logged step of an interaction: a dialog start ({@link #optionId} {@code null}) or a choice.
 */
public class InteractionLogEntry
{
	public final Integer campaignId;
	public final int campaignTick;
	public final int userId;
	public final int shipId;
	public final int interactionId;
	public final int nodeId;
	public final Integer optionId;
	public final Integer chance;
	public final boolean success;
	public final String actionsSummary;
	public final long createdAt;

	public InteractionLogEntry(Integer campaignId, int campaignTick, int userId, int shipId, int interactionId, int nodeId, Integer optionId,
							   Integer chance, boolean success, String actionsSummary, long createdAt)
	{
		this.campaignId = campaignId;
		this.campaignTick = campaignTick;
		this.userId = userId;
		this.shipId = shipId;
		this.interactionId = interactionId;
		this.nodeId = nodeId;
		this.optionId = optionId;
		this.chance = chance;
		this.success = success;
		this.actionsSummary = actionsSummary;
		this.createdAt = createdAt;
	}

	public boolean isStart()
	{
		return optionId == null;
	}
}
