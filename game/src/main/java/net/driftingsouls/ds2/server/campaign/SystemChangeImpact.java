package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.config.StarSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The players' ships and bases a change to a star system affects, and why. Whoever makes the change
 * confirms it after seeing them.
 */
public class SystemChangeImpact
{
	/** Why the objects are affected, one sentence each. */
	public final List<String> reasons;
	/** The players' objects affected. */
	public final List<CampaignObject> objects;

	private SystemChangeImpact(List<String> reasons, List<CampaignObject> objects)
	{
		this.reasons = reasons;
		this.objects = objects;
	}

	/**
	 * @return Whether no player object is affected
	 */
	public boolean isEmpty()
	{
		return objects.isEmpty();
	}

	/**
	 * Shrinking moves objects beyond the new bounds onto them; other access rights change what players
	 * see of every object in the system.
	 *
	 * @param objects The players' objects in the star system
	 */
	public static SystemChangeImpact ofUpdate(int oldWidth, int oldHeight, StarSystem.Access oldAccess,
			int newWidth, int newHeight, StarSystem.Access newAccess, List<CampaignObject> objects)
	{
		List<String> reasons = new ArrayList<>();
		List<CampaignObject> affected = new ArrayList<>();
		if (newWidth < oldWidth || newHeight < oldHeight)
		{
			List<CampaignObject> outside = objects.stream()
					.filter(o -> o.x > newWidth || o.y > newHeight)
					.collect(Collectors.toList());
			if (!outside.isEmpty())
			{
				reasons.add("Das System wird auf " + newWidth + "x" + newHeight + " verkleinert: " + outside.size()
						+ " Spielerobjekt(e) liegen außerhalb und werden an den Rand verschoben.");
				affected.addAll(outside);
			}
		}
		if (oldAccess != newAccess && !objects.isEmpty())
		{
			reasons.add("Die Zugriffsrechte ändern sich von " + oldAccess + " zu " + newAccess
					+ ": das betrifft, wer die Spielerobjekte im System sieht und erreicht.");
			affected = new ArrayList<>(objects);
		}
		return new SystemChangeImpact(reasons, affected);
	}

	/**
	 * Deleting a star system destroys every ship and removes every base in it.
	 *
	 * @param objects The players' objects in the star system
	 */
	public static SystemChangeImpact ofDelete(List<CampaignObject> objects)
	{
		return new SystemChangeImpact(objects.isEmpty() ? List.of()
				: List.of("Das System wird gelöscht: alle Schiffe darin werden zerstört, alle Basen entfernt."), objects);
	}

	/**
	 * Clearing a star system for an image import removes every base in it.
	 *
	 * @param objects The players' objects in the star system
	 */
	public static SystemChangeImpact ofClearingBases(List<CampaignObject> objects)
	{
		List<CampaignObject> bases = objects.stream()
				.filter(o -> o.type == CampaignObject.Type.BASE)
				.collect(Collectors.toList());
		return new SystemChangeImpact(bases.isEmpty() ? List.of()
				: List.of("„Alte Basen/Nebel entfernen“ löscht alle Basen im System, auch die von Spielern."), bases);
	}
}
