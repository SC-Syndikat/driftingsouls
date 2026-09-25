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
	BOARD,
	/** Any other change to a ship or fleet: alarm, shields, batteries, crew, repair, giving it away. */
	SHIP_ACTION,
	/** Any change to a base: buildings, core, feeding, name. */
	BASE_ACTION,
	/** Acting within a battle one is already in. */
	BATTLE_ACTION,
	COLONIZE,
	/** Buying from or selling to a trading post, using a GTU depot. */
	TRADE
}
