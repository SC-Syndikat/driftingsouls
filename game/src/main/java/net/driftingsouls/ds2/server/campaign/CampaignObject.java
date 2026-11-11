package net.driftingsouls.ds2.server.campaign;

/**
 * A ship or base inside a campaign star system, as listed in the game master's warnings.
 */
public class CampaignObject
{
	public enum Type
	{
		SHIP("Schiff"),
		BASE("Basis");

		private final String label;

		Type(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	public final Type type;
	public final int id;
	public final String name;
	public final int starSystemId;
	public final int x;
	public final int y;
	public final int ownerId;
	public final String ownerName;

	public CampaignObject(Type type, int id, String name, int starSystemId, int x, int y, int ownerId, String ownerName)
	{
		this.type = type;
		this.id = id;
		this.name = name;
		this.starSystemId = starSystemId;
		this.x = x;
		this.y = y;
		this.ownerId = ownerId;
		this.ownerName = ownerName;
	}

	public String getLocation()
	{
		return starSystemId + ":" + x + "/" + y;
	}
}
