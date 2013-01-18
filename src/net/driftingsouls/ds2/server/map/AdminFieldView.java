package net.driftingsouls.ds2.server.map;

import net.driftingsouls.ds2.server.Location;
import net.driftingsouls.ds2.server.bases.Base;
import net.driftingsouls.ds2.server.entities.JumpNode;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipType;
import org.hibernate.Session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Sicht eines Administrators auf ein Feld der Sternenkarte.
 */
public class AdminFieldView implements FieldView
{
	private final Field field;
	private final User user;
	private final Session db;
	private final Location location;

	/**
	 * Legt eine neue Sicht an.
	 *
	 * @param db Ein aktives Hibernate Sessionobjekt.
	 * @param user Der Spieler fuer den die Sicht gelten soll.
	 * @param position Der gesuchte Sektor.
	 */
	public AdminFieldView(Session db, User user, Location position)
	{
		this.field = new Field(db, position);
		this.user = user;
		this.db = db;
		this.location = position;
	}

	@Override
	public List<Base> getBases()
	{
		return this.field.getBases();
	}

	@Override
	public Map<User, Map<ShipType, List<Ship>>> getShips()
	{
		Map<User, Map<ShipType, List<Ship>>> ships = new HashMap<User, Map<ShipType,List<Ship>>>();

		for (Ship viewableShip : field.getShips())
		{
			if (viewableShip.isLanded())
			{
				continue;
			}

			final ShipType type = (ShipType) db.get(ShipType.class, viewableShip.getType());
			final User owner = viewableShip.getOwner();

			if (!ships.containsKey(owner))
			{
				ships.put(owner, new HashMap<ShipType, List<Ship>>());
			}

			if (!ships.get(owner).containsKey(type))
			{
				ships.get(owner).put(type, new ArrayList<Ship>());
			}

			ships.get(owner).get(type).add(viewableShip);
		}

		return ships;
	}

	@Override
	public List<JumpNode> getJumpNodes()
	{
		return field.getNodes();
	}
}