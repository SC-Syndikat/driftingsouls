package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.campaign.CampaignMapService;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.campaign.CampaignText;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.ally.Ally;

import javax.persistence.EntityManager;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntPredicate;

/**
 * Choices for the selects of the campaign admin pages, as id to label. Values already stored stay
 * selectable even when they no longer belong to the campaign, so saving a form never changes them
 * silently.
 */
final class CampaignOptions
{
	private CampaignOptions()
	{
		// static only
	}

	/**
	 * @param keep Ids to list in any case, e.g. the stored value; 0 is ignored
	 * @return The NPC accounts taking part in the open campaign, by id
	 */
	static Map<Integer, String> campaignNpcs(EntityManager em, int... keep)
	{
		return users(em, withKept(participants(em, id -> id < 0), keep));
	}

	/**
	 * @param keep Ids to list in any case; 0 is ignored
	 * @return The players taking part in the open campaign, by id
	 */
	static Map<Integer, String> campaignPlayers(EntityManager em, Collection<Integer> keep)
	{
		Set<Integer> ids = participants(em, id -> id > 0);
		keep.stream().filter(id -> id != 0).forEach(ids::add);
		return users(em, ids);
	}

	/**
	 * @param keep Ids to list in any case; 0 is ignored
	 * @return Everyone taking part in the open campaign, players and NPCs, by id
	 */
	static Map<Integer, String> campaignParticipants(EntityManager em, int... keep)
	{
		return users(em, withKept(participants(em, id -> true), keep));
	}

	/**
	 * @param keep Ids to list in any case
	 * @return The alliances of the players taking part in the open campaign, by id
	 */
	static Map<Integer, String> campaignAlliances(EntityManager em, Collection<Integer> keep)
	{
		Set<Integer> ids = new TreeSet<>(keep);
		for (int id : participants(em, userId -> userId > 0))
		{
			User user = em.find(User.class, id);
			if (user != null && user.getAlly() != null)
			{
				ids.add(user.getAlly().getId());
			}
		}
		Map<Integer, String> options = new LinkedHashMap<>();
		for (int id : ids)
		{
			Ally ally = em.find(Ally.class, id);
			options.put(id, (ally == null ? "?" : CampaignText.plain(ally.getName())) + " (" + id + ")");
		}
		return options;
	}

	/**
	 * @param keep Ids to list in any case, e.g. the stored value; 0 is ignored
	 * @return The star systems of the open campaign, by id
	 */
	static Map<Integer, String> campaignSystems(EntityManager em, int... keep)
	{
		return systems(em, withKept(new TreeSet<>(new CampaignMapService(em).getOpenCampaignSystemIds()), keep));
	}

	/**
	 * @return The given star systems, by id
	 */
	static Map<Integer, String> systems(EntityManager em, Collection<Integer> ids)
	{
		Map<Integer, String> options = new LinkedHashMap<>();
		if (ids.isEmpty())
		{
			return options;
		}
		for (StarSystem system : em.createQuery("from StarSystem where id in (:ids) order by id", StarSystem.class)
				.setParameter("ids", ids)
				.getResultList())
		{
			options.put(system.getID(), systemLabel(system));
		}
		return options;
	}

	/**
	 * @return Every star system, the open campaign's first: group label to choices
	 */
	static Map<String, Map<Integer, String>> allSystemsCampaignFirst(EntityManager em)
	{
		Set<Integer> campaign = new CampaignMapService(em).getOpenCampaignSystemIds();
		Map<Integer, String> inCampaign = new LinkedHashMap<>();
		Map<Integer, String> others = new LinkedHashMap<>();
		for (StarSystem system : em.createQuery("from StarSystem order by id", StarSystem.class).getResultList())
		{
			(campaign.contains(system.getID()) ? inCampaign : others).put(system.getID(), systemLabel(system));
		}
		Map<String, Map<Integer, String>> groups = new LinkedHashMap<>();
		groups.put("Kampagnensysteme", inCampaign);
		groups.put("Weitere Systeme", others);
		return groups;
	}

	/**
	 * @return The given accounts, by id
	 */
	static Map<Integer, String> users(EntityManager em, Collection<Integer> ids)
	{
		Map<Integer, String> options = new LinkedHashMap<>();
		for (int id : new TreeSet<>(ids))
		{
			options.put(id, userLabel(em, id));
		}
		return options;
	}

	static String systemLabel(StarSystem system)
	{
		return CampaignText.plain(system.getName()) + " (" + system.getID() + ")";
	}

	static String userLabel(EntityManager em, int id)
	{
		User user = em.find(User.class, id);
		return (user == null ? "?" : CampaignText.plain(user.getName())) + " (" + id + ")";
	}

	private static Set<Integer> participants(EntityManager em, IntPredicate filter)
	{
		Set<Integer> ids = new TreeSet<>();
		CampaignService campaigns = new CampaignService(em);
		campaigns.findOpenCampaign().ifPresent(c -> campaigns.getParticipantIds(c).stream().filter(filter::test).forEach(ids::add));
		return ids;
	}

	private static Set<Integer> withKept(Set<Integer> ids, int... keep)
	{
		for (int id : keep)
		{
			if (id != 0)
			{
				ids.add(id);
			}
		}
		return ids;
	}
}
