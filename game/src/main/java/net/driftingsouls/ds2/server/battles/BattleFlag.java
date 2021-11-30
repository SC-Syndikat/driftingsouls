package net.driftingsouls.ds2.server.battles;

/**
 * Die Flags einer Schlacht.
 */
public enum BattleFlag
{
	/**
	 * Erste Runde.
	 */
	FIRSTROUND(1),
	/**
	 * Seitenflag: Entfernt die zweite Reihe der Seite.
	 */
	DROP_SECONDROW(2),
	/**
	 * Seitenflag: Blockiert die zweite Reihe der Seite.
	 */
	BLOCK_SECONDROW(4);

	private final int bit;

	BattleFlag(final int bit)
	{
		this.bit = bit;
	}

	/**
	 * Gibt den Bitwert dieses Flags zurueck.
	 * @return Der Bitwert
	 */
	public int getBit()
	{
		return this.bit;
	}
}
