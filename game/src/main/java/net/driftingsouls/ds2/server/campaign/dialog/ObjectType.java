package net.driftingsouls.ds2.server.campaign.dialog;

/**
 * What kind of object offers an interaction.
 */
public enum ObjectType
{
	SHIP("Schiff"),
	BASE("Basis / Asteroid"),
	STAR_SYSTEM("Sternensystem");

	private final String label;

	ObjectType(String label)
	{
		this.label = label;
	}

	public String getLabel()
	{
		return label;
	}
}
