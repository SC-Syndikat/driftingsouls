package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.campaign.GameMasterRestriction;
import net.driftingsouls.ds2.server.config.ConfigFelsbrockenSystem;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.ContextMap;

import javax.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

/**
 * Choices for the fields of the entity editors a restricted game master uses: only what lies within the
 * campaign. The editors render the labels as they are, so text labels come escaped.
 */
final class GameMasterOptions
{
	private GameMasterOptions()
	{
		// static only
	}

	/**
	 * @return The campaign star systems, by id
	 */
	static Map<Integer, String> systemIds(GameMasterRestriction restriction)
	{
		Map<Integer, String> options = new LinkedHashMap<>();
		CampaignOptions.systems(em(), restriction.getSystems()).forEach((id, label) -> options.put(id, escapeHtml4(label)));
		return options;
	}

	/**
	 * @return The campaign star systems
	 */
	static Map<Integer, StarSystem> systems(GameMasterRestriction restriction)
	{
		Map<Integer, StarSystem> options = new LinkedHashMap<>();
		for (int id : new TreeSet<>(restriction.getSystems()))
		{
			StarSystem system = em().find(StarSystem.class, id);
			if (system != null)
			{
				options.put(id, system);
			}
		}
		return options;
	}

	/**
	 * @return The owners objects may be given to: the campaign's NPCs, and nobody (account 0) if it exists
	 */
	static Map<Integer, User> owners(GameMasterRestriction restriction)
	{
		Map<Integer, User> options = new LinkedHashMap<>();
		User nobody = em().find(User.class, 0);
		if (nobody != null)
		{
			options.put(0, nobody);
		}
		for (int id : restriction.getMasterLoginIds())
		{
			User npc = em().find(User.class, id);
			if (npc != null)
			{
				options.put(id, npc);
			}
		}
		return options;
	}

	/**
	 * @return The asteroid configurations of campaign star systems
	 */
	static Map<Long, ConfigFelsbrockenSystem> boulderSystems(GameMasterRestriction restriction)
	{
		Map<Long, ConfigFelsbrockenSystem> options = new LinkedHashMap<>();
		for (ConfigFelsbrockenSystem config : em().createQuery("from ConfigFelsbrockenSystem order by id", ConfigFelsbrockenSystem.class).getResultList())
		{
			if (restriction.isSystemAllowed(config.getSystem().getID()))
			{
				options.put((Long) em().getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(config), config);
			}
		}
		return options;
	}

	/**
	 * @return The access levels a game master may give a star system
	 */
	static Map<StarSystem.Access, StarSystem.Access> access()
	{
		Map<StarSystem.Access, StarSystem.Access> options = new LinkedHashMap<>();
		for (StarSystem.Access access : StarSystem.Access.values())
		{
			if (access != StarSystem.Access.HOMESYSTEM && access != StarSystem.Access.ADMIN)
			{
				options.put(access, access);
			}
		}
		return options;
	}

	private static EntityManager em()
	{
		return ContextMap.getContext().getEM();
	}
}
