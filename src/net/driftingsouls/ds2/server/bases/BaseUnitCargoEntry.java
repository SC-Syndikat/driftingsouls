package net.driftingsouls.ds2.server.bases;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Table;

import net.driftingsouls.ds2.server.units.UnitCargo;
import net.driftingsouls.ds2.server.units.UnitCargoEntry;

/**
 * A unit cargo entry for bases.
 * The cargo entry filters all ship related cargos from results.
 * 
 * @author Drifting-Souls Team
 */
@Entity
@Table(name="cargo_entries_units")
@DiscriminatorValue("" + UnitCargo.CARGO_ENTRY_BASE)
public class BaseUnitCargoEntry extends UnitCargoEntry 
{
	/**
	 * Default.
	 */
	public BaseUnitCargoEntry()
	{}
	
	/**
	 * @see UnitCargoEntry#UnitCargoEntry(int, int, int, long)
	 * @param type Der Typ des Eintrages
	 * @param destid Die ID des Zielobjekts
	 * @param unittype Die ID des Einheitentyps
	 * @param amount Die Menge
	 */
	public BaseUnitCargoEntry(int type, int destid, int unittype, long amount)
	{
		super(type, destid, unittype, amount);
	}
}