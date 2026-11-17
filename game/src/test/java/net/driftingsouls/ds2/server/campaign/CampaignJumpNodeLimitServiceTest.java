package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.DBSingleTransactionTest;
import net.driftingsouls.ds2.server.Location;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.entities.JumpNode;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.UserFlag;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipClasses;
import net.driftingsouls.ds2.server.ships.ShipType;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class CampaignJumpNodeLimitServiceTest extends DBSingleTransactionTest
{
	private User gamemaster;
	private User player;
	private Ship fighter;
	private Ship cruiser;
	private int nodeId;
	private CampaignJumpNodeLimitService limits;

	@Before
	public void setUp()
	{
		gamemaster = new User("gm", "***", 0, "", new Cargo(), "gm@localhost");
		gamemaster.setFlag(UserFlag.GAMEMASTER);
		player = new User("player", "***", 0, "", new Cargo(), "player@localhost");
		StarSystem outside = system();
		StarSystem inside = system();
		ShipType fighterType = shipType(ShipClasses.JAEGER, 2);
		ShipType cruiserType = shipType(ShipClasses.KREUZER, 8);
		fighter = persist(new Ship(player, fighterType, outside.getID(), 5, 5));
		cruiser = persist(new Ship(player, cruiserType, outside.getID(), 5, 5));
		getEM().flush();

		CampaignService campaigns = new CampaignService(getEM());
		CampaignMapService map = new CampaignMapService(getEM());
		Campaign campaign = campaigns.prepare("Test", gamemaster);
		campaigns.updateParticipants(campaign, Set.of(player.getId()), gamemaster);
		map.addSystem(campaign, inside.getID());
		List<JumpNode> nodes = map.createJumpNode(campaign, new Location(outside.getID(), 5, 5), new Location(inside.getID(), 1, 1), "Zugang", false);
		nodeId = nodes.get(0).getId();
		campaigns.start(campaign);

		limits = new CampaignJumpNodeLimitService(getEM());
	}

	@Test
	public void givenNoLimits_check_shouldAllowEverything()
	{
		assertFalse(limits.check(player, nodeId, List.of(fighter, cruiser)).isPresent());
	}

	@Test
	public void givenAllowedClasses_check_shouldRejectOtherClasses()
	{
		limits.save(nodeId, JumpNodeLimits.NONE.withAllowedShipClasses(Set.of(ShipClasses.JAEGER.name())));

		assertFalse(limits.check(player, nodeId, List.of(fighter)).isPresent());
		assertTrue(limits.check(player, nodeId, List.of(fighter, cruiser)).isPresent());
	}

	@Test
	public void givenMaxPointsPerShip_check_shouldRejectShipsThatAreTooLarge()
	{
		limits.save(nodeId, JumpNodeLimits.NONE.withMaxPointsPerShip(5));

		assertFalse(limits.check(player, nodeId, List.of(fighter)).isPresent());
		assertTrue(limits.check(player, nodeId, List.of(cruiser)).isPresent());
	}

	@Test
	public void givenAPlayerQuota_check_shouldCountPreviousPassages()
	{
		limits.save(nodeId, JumpNodeLimits.NONE.withMaxPointsPerPlayer(10));

		assertFalse(limits.check(player, nodeId, List.of(cruiser)).isPresent());
		limits.record(player, nodeId, List.of(cruiser));
		assertFalse("8 + 2 = 10 passt noch", limits.check(player, nodeId, List.of(fighter)).isPresent());
		limits.record(player, nodeId, List.of(fighter));
		assertTrue("Kontingent erschöpft", limits.check(player, nodeId, List.of(fighter)).isPresent());
	}

	@Test
	public void givenAQuotaPerTick_check_shouldOnlyCountTheCurrentTick()
	{
		limits.save(nodeId, JumpNodeLimits.NONE.withMaxPointsPerPlayer(8).withPeriod(JumpNodeLimits.Period.PER_TICK));
		limits.record(player, nodeId, List.of(cruiser));
		assertTrue(limits.check(player, nodeId, List.of(fighter)).isPresent());

		new CampaignRepository().incrementTickCount(new CampaignRepository().findOpen().orElseThrow().getId());

		assertFalse("Neuer Kampagnentick, neues Kontingent", limits.check(player, nodeId, List.of(cruiser)).isPresent());
	}

	@Test
	public void givenAGameMaster_check_shouldNotRestrict()
	{
		limits.save(nodeId, JumpNodeLimits.NONE.withMaxPointsPerShip(1));

		assertFalse(limits.check(gamemaster, nodeId, List.of(cruiser)).isPresent());
	}

	private StarSystem system()
	{
		StarSystem system = persist(new StarSystem());
		system.setWidth(50);
		system.setHeight(50);
		system.setAccess(StarSystem.Access.NORMAL);
		return system;
	}

	private ShipType shipType(ShipClasses shipClass, int size)
	{
		ShipType type = persist(new ShipType(shipClass));
		type.setSize(size);
		type.setCrew(5);
		return type;
	}
}
