package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.Location;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.entities.JumpNode;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.map.TileCache;
import net.driftingsouls.ds2.server.repositories.JumpNodeRepository;
import net.driftingsouls.ds2.server.repositories.StarsystemRepository;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The campaign's star systems and the temporary jump nodes leading into them.
 *
 * <p>Campaign membership and jump node states are jOOQ-owned tables. The star system's visibility and
 * the jump node's {@code hidden} flag live on Hibernate-owned tables and are written through the
 * entities.
 */
public class CampaignMapService
{
	private final EntityManager em;
	private final CampaignRepository campaigns;
	private final CampaignMapRepository map;

	public CampaignMapService(EntityManager em)
	{
		this.em = em;
		this.campaigns = new CampaignRepository();
		this.map = new CampaignMapRepository();
	}

	public Set<Integer> getSystemIds(Campaign campaign)
	{
		return map.findSystemIds(campaign.getId());
	}

	/**
	 * Star systems of the campaign that is being prepared or runs; empty if there is none.
	 */
	public Set<Integer> getOpenCampaignSystemIds()
	{
		return campaigns.findOpen().map(c -> map.findSystemIds(c.getId())).orElse(Set.of());
	}

	/**
	 * Star systems of a running campaign; empty while none runs.
	 */
	public Set<Integer> getRunningCampaignSystemIds()
	{
		return campaigns.findOpen()
				.filter(Campaign::isRunning)
				.map(c -> map.findSystemIds(c.getId()))
				.orElse(Set.of());
	}

	/**
	 * Ships and bases of non-participating players in the campaign's star systems.
	 */
	public List<CampaignObject> findForeignObjects(Campaign campaign)
	{
		return map.findForeignObjects(map.findSystemIds(campaign.getId()), campaigns.findParticipantIds(campaign.getId()));
	}

	/**
	 * Ships and bases of non-participating players in one star system.
	 */
	public List<CampaignObject> findForeignObjects(Campaign campaign, int starSystemId)
	{
		return map.findForeignObjects(Set.of(starSystemId), campaigns.findParticipantIds(campaign.getId()));
	}

	/**
	 * Ships and bases of any player in the campaign's star systems, e.g. those left behind when it ends.
	 */
	public List<CampaignObject> findPlayerObjects(Campaign campaign)
	{
		return map.findPlayerObjects(map.findSystemIds(campaign.getId()));
	}

	public void addSystem(Campaign campaign, int starSystemId)
	{
		requireOpen(campaign);
		if (em.find(StarSystem.class, starSystemId) == null)
		{
			throw new CampaignException("Unbekanntes Sternensystem: " + starSystemId);
		}
		map.addSystem(campaign.getId(), starSystemId, Common.time());
	}

	public void removeSystem(Campaign campaign, int starSystemId)
	{
		requireOpen(campaign);
		map.removeSystem(campaign.getId(), starSystemId);
	}

	public void setSystemVisible(int starSystemId, boolean visible)
	{
		StarSystem system = em.find(StarSystem.class, starSystemId);
		if (system == null)
		{
			throw new CampaignException("Unbekanntes Sternensystem: " + starSystemId);
		}
		system.setStarmapVisible(visible);
		StarsystemRepository.getInstance().clearStarsystemData();
		TileCache.forSystem(starSystemId).resetCache();
	}

	public Map<Integer, CampaignJumpNodeState> getJumpNodeStates()
	{
		return map.findJumpNodeStates();
	}

	/**
	 * @return The state of the jump node if it is a campaign jump node, otherwise {@code null}
	 */
	public CampaignJumpNodeState getJumpNodeState(int jumpNodeId)
	{
		return map.findJumpNodeStates().get(jumpNodeId);
	}

	/**
	 * Creates a new campaign jump node in state {@link CampaignJumpNodeState#PREPARED}, and optionally its
	 * counterpart leading back.
	 *
	 * @return The created nodes
	 */
	public List<JumpNode> createJumpNode(Campaign campaign, Location source, Location target, String name, boolean withReturn)
	{
		requireOpen(campaign);
		requireLocation(source);
		requireLocation(target);
		Set<Integer> systems = map.findSystemIds(campaign.getId());
		if (!systems.contains(source.getSystem()) && !systems.contains(target.getSystem()))
		{
			throw new CampaignException("Mindestens eine Seite des Sprungpunkts muss in einem Kampagnensystem liegen.");
		}

		List<JumpNode> created = new ArrayList<>();
		created.add(persistJumpNode(source, target, name));
		if (withReturn)
		{
			created.add(persistJumpNode(target, source, name));
		}
		return created;
	}

	/**
	 * Turns an existing jump node into a campaign jump node, in state {@link CampaignJumpNodeState#PREPARED}.
	 */
	public void registerJumpNode(Campaign campaign, int jumpNodeId)
	{
		requireOpen(campaign);
		JumpNode node = findJumpNode(jumpNodeId);
		Set<Integer> systems = map.findSystemIds(campaign.getId());
		if (!systems.contains(node.getSystem()) && !systems.contains(node.getSystemOut()))
		{
			throw new CampaignException("Mindestens eine Seite des Sprungpunkts muss in einem Kampagnensystem liegen.");
		}
		setJumpNodeState(node, CampaignJumpNodeState.PREPARED);
	}

	/**
	 * Makes a campaign jump node a normal one again. It keeps its current visibility.
	 */
	public void unregisterJumpNode(int jumpNodeId)
	{
		map.removeJumpNode(jumpNodeId);
	}

	public void setJumpNodeState(int jumpNodeId, CampaignJumpNodeState state)
	{
		if (map.findJumpNodeStates().get(jumpNodeId) == null)
		{
			throw new CampaignException("Sprungpunkt " + jumpNodeId + " ist kein Kampagnen-Sprungpunkt.");
		}
		setJumpNodeState(findJumpNode(jumpNodeId), state);
	}

	/**
	 * Sets every campaign jump node to the given state: ACTIVE when a campaign starts, PREPARED when it
	 * ends.
	 */
	public void setAllJumpNodeStates(CampaignJumpNodeState state)
	{
		for (int jumpNodeId : map.findJumpNodeStates().keySet())
		{
			JumpNode node = em.find(JumpNode.class, jumpNodeId);
			if (node != null)
			{
				setJumpNodeState(node, state);
			}
		}
	}

	/**
	 * Campaign jump nodes leading out of a campaign star system into a normal one. Their far end is where
	 * ships left behind are moved to when the campaign ends.
	 */
	public List<JumpNode> findExits(Campaign campaign)
	{
		Set<Integer> systems = map.findSystemIds(campaign.getId());
		Set<Integer> campaignNodes = map.findJumpNodeStates().keySet();
		if (systems.isEmpty() || campaignNodes.isEmpty())
		{
			return new ArrayList<>();
		}
		List<JumpNode> exits = new ArrayList<>();
		for (JumpNode node : em.createQuery("from JumpNode where id in (:ids) order by id", JumpNode.class)
				.setParameter("ids", campaignNodes)
				.getResultList())
		{
			if (systems.contains(node.getSystem()) && !systems.contains(node.getSystemOut()))
			{
				exits.add(node);
			}
		}
		return exits;
	}

	/**
	 * Moves a player's ship, with whatever is docked or landed on it, to the given location. Used to get
	 * ships out of campaign star systems when the campaign ends.
	 */
	public void relocateShip(int shipId, Location target)
	{
		requireLocation(target);
		var ship = em.find(net.driftingsouls.ds2.server.ships.Ship.class, shipId);
		if (ship == null)
		{
			throw new CampaignException("Unbekanntes Schiff: " + shipId);
		}
		if (ship.getBattle() != null)
		{
			throw new CampaignException("Schiff " + shipId + " ist in einer Schlacht und kann nicht versetzt werden.");
		}
		ship.getGedockteUndGelandeteSchiffe().forEach(docked -> docked.setLocation(target));
		ship.setLocation(target);
		TileCache.forSystem(target.getSystem()).resetCache();
	}

	private void setJumpNodeState(JumpNode node, CampaignJumpNodeState state)
	{
		map.saveJumpNodeState(node.getId(), state);
		node.setHidden(state.isHidden());
		JumpNodeRepository.getInstance().clearSystem(node.getSystem());
		TileCache.forSystem(node.getSystem()).resetCache();
	}

	private JumpNode persistJumpNode(Location source, Location target, String name)
	{
		JumpNode node = new JumpNode(source, target, name);
		node.setHidden(true);
		em.persist(node);
		// The state row references the node, and is written by jOOQ
		em.flush();
		setJumpNodeState(node, CampaignJumpNodeState.PREPARED);
		return node;
	}

	private JumpNode findJumpNode(int jumpNodeId)
	{
		JumpNode node = em.find(JumpNode.class, jumpNodeId);
		if (node == null)
		{
			throw new CampaignException("Unbekannter Sprungpunkt: " + jumpNodeId);
		}
		return node;
	}

	private void requireLocation(Location location)
	{
		StarSystem system = em.find(StarSystem.class, location.getSystem());
		if (system == null)
		{
			throw new CampaignException("Unbekanntes Sternensystem: " + location.getSystem());
		}
		if (location.getX() < 1 || location.getY() < 1 || location.getX() > system.getWidth() || location.getY() > system.getHeight())
		{
			throw new CampaignException("Die Position " + location.displayCoordinates(false) + " liegt nicht im System.");
		}
	}

	private static void requireOpen(Campaign campaign)
	{
		if (!campaign.isPreparing() && !campaign.isRunning())
		{
			throw new CampaignException("Die Kampagne ist beendet.");
		}
	}
}
