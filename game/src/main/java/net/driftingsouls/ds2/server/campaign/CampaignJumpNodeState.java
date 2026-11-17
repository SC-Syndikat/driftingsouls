package net.driftingsouls.ds2.server.campaign;

/**
 * State of a temporary access into a campaign star system.
 */
public enum CampaignJumpNodeState
{
	/** Invisible to players and not usable. The state outside a running campaign. */
	PREPARED("vorbereitet", true),
	/** Visible to players but not yet usable. */
	VISIBLE("sichtbar", false),
	/** Visible and usable. Set for every node when the campaign starts. */
	ACTIVE("aktiv", false);

	private final String label;
	private final boolean hidden;

	CampaignJumpNodeState(String label, boolean hidden)
	{
		this.label = label;
		this.hidden = hidden;
	}

	public String getLabel()
	{
		return label;
	}

	/**
	 * @return The value {@code JumpNode.hidden} must have in this state
	 */
	public boolean isHidden()
	{
		return hidden;
	}
}
