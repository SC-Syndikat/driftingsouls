package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.campaign.CampaignMapService;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.campaign.CampaignText;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.entities.User;

import javax.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
		Set<Integer> ids = new TreeSet<>();
		CampaignService campaigns = new CampaignService(em);
		campaigns.findOpenCampaign().ifPresent(c -> campaigns.getParticipantIds(c).stream().filter(id -> id < 0).forEach(ids::add));
		for (int id : keep)
		{
			if (id != 0)
			{
				ids.add(id);
			}
		}
		Map<Integer, String> options = new LinkedHashMap<>();
		for (int id : ids)
		{
			options.put(id, userLabel(em, id));
		}
		return options;
	}

	/**
	 * @param keep Ids to list in any case, e.g. the stored value; 0 is ignored
	 * @return The star systems of the open campaign, by id
	 */
	static Map<Integer, String> campaignSystems(EntityManager em, int... keep)
	{
		Set<Integer> ids = new TreeSet<>(new CampaignMapService(em).getOpenCampaignSystemIds());
		for (int id : keep)
		{
			if (id != 0)
			{
				ids.add(id);
			}
		}
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

	static String systemLabel(StarSystem system)
	{
		return CampaignText.plain(system.getName()) + " (" + system.getID() + ")";
	}

	static String userLabel(EntityManager em, int id)
	{
		User user = em.find(User.class, id);
		return (user == null ? "?" : CampaignText.plain(user.getName())) + " (" + id + ")";
	}
}
