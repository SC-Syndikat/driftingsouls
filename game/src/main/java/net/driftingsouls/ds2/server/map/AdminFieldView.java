package net.driftingsouls.ds2.server.map;

import net.driftingsouls.ds2.server.Location;
import net.driftingsouls.ds2.server.entities.Nebel;
import net.driftingsouls.ds2.server.entities.User;
import org.hibernate.Session;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.Map;

/**
 * Die Sicht eines Administrators auf ein Feld der Sternenkarte.
 */
public class AdminFieldView extends PlayerFieldView
{
	private final Field field;
	private final Session db;
	private final Location location;

	/**
	 * Legt eine neue Sicht an.
	 *
	 * @param position Der gesuchte Sektor.
	 */
	public AdminFieldView(User user, Location position, PublicStarmap starmap, EntityManager em)
	{
		this.field = new Field(db, position);
		this.db = db;
		this.location = position;
	}

	protected int getMinSize()
	{
		return 0;
	}

	@Override
	public Location getLocation(){
		return location;
	}
}
