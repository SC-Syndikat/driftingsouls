package net.driftingsouls.ds2.server.campaign;

/**
 * Lifecycle of a campaign. At most one campaign is not {@link #ENDED} at any time.
 */
public enum CampaignStatus
{
	/** The game master is setting up participants, star systems and objects. */
	PREPARING,
	/** Started: participants may act in campaign star systems. */
	RUNNING,
	ENDED
}
