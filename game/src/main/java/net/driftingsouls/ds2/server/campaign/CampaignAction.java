package net.driftingsouls.ds2.server.campaign;

/**
 * Something a user does in a star system that campaign rules may forbid.
 */
public enum CampaignAction
{
	/** Arriving in the system, by any kind of jump or teleport. */
	ENTER_SYSTEM,
	MOVE,
	JUMP,
	START_BATTLE,
	JOIN_BATTLE,
	INTERACT,
	TRANSFER,
	DOCK,
	LAND,
	BOARD
}
