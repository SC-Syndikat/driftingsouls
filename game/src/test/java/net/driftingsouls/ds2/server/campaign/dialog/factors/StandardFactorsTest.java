package net.driftingsouls.ds2.server.campaign.dialog.factors;

import net.driftingsouls.ds2.server.DBSingleTransactionTest;
import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignMapService;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.campaign.NpcTestUsers;
import net.driftingsouls.ds2.server.campaign.dialog.CampaignVariableService;
import net.driftingsouls.ds2.server.campaign.dialog.DialogContext;
import net.driftingsouls.ds2.server.campaign.dialog.DialogTypes;
import net.driftingsouls.ds2.server.campaign.dialog.ObjectType;
import net.driftingsouls.ds2.server.campaign.dialog.Params;
import net.driftingsouls.ds2.server.campaign.dialog.TrustService;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.cargo.ItemID;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.entities.Forschung;
import net.driftingsouls.ds2.server.entities.Offizier;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.ally.Ally;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipClasses;
import net.driftingsouls.ds2.server.ships.ShipType;
import net.driftingsouls.ds2.server.ships.ShipTypeFlag;
import net.driftingsouls.ds2.server.units.UnitCargo;
import net.driftingsouls.ds2.server.units.UnitType;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * One test per factor dialog options can depend on: each reads the player's situation as a number.
 */
public class StandardFactorsTest extends DBSingleTransactionTest
{
	private User npc;
	private User player;
	private StarSystem system;
	private ShipType type;
	private Ship npcShip;
	private Ship ship;
	private Campaign campaign;
	private double random = 50;

	@Before
	public void setUp()
	{
		// NPC first: creating it clears the persistence context
		npc = NpcTestUsers.create(getEM(), "admiral", -77);
		User gamemaster = persist(new User("gm", "***", 0, "", new Cargo(), "gm@localhost"));
		player = persist(new User("player", "***", 0, "", new Cargo(), "player@localhost"));
		system = persist(new StarSystem());
		system.setWidth(50);
		system.setHeight(50);
		system.setAccess(StarSystem.Access.NORMAL);
		type = persist(new ShipType(ShipClasses.AWACS));
		type.setCrew(10);
		type.setHull(200);
		type.setShields(100);
		type.setEps(40);
		npcShip = persist(new Ship(npc, type, system.getID(), 5, 5));
		ship = persist(new Ship(player, type, system.getID(), 5, 5));
		getEM().flush();

		CampaignService campaigns = new CampaignService(getEM());
		Campaign prepared = campaigns.prepare("Sturm über Rigel", gamemaster);
		campaigns.updateParticipants(prepared, Set.of(player.getId(), npc.getId()), gamemaster);
		new CampaignMapService(getEM()).addSystem(prepared, system.getID());
		campaigns.start(prepared);
		campaign = campaigns.findOpenCampaign().orElseThrow();
	}

	private double value(String factor, String... params)
	{
		DialogContext context = new DialogContext(getEM(), player, ship, npc, ObjectType.SHIP, npcShip.getId(), campaign, null, player,
				() -> random);
		return DialogTypes.get().factor(factor).value(context, Params.of(params));
	}

	@Test
	public void relation_shouldBeMinusOneForEnemiesAndOneForFriends()
	{
		npc.setRelation(player.getId(), User.Relation.ENEMY);
		player.setRelation(npc.getId(), User.Relation.FRIEND);

		assertEquals(-1, value("RELATION", "direction", "NPC_TO_USER"), 0);
		assertEquals(1, value("RELATION", "direction", "USER_TO_NPC"), 0);
	}

	@Test
	public void npcRank_shouldBeThePlayersRankWithTheNpc()
	{
		assertEquals(0, value("NPC_RANK"), 0);

		player.setRank(npc, 3);

		assertEquals(3, value("NPC_RANK"), 0);
	}

	@Test
	public void trust_shouldBeTheNpcsTrustInThePlayer()
	{
		new TrustService(getEM()).record(npc.getId(), player.getId(), 12, "geholfen", TrustService.Kind.ACTION, campaign.getId(), null, null);

		assertEquals(12, value("TRUST"), 0.001);
	}

	@Test
	public void officerSkill_shouldBeTheOfficersAbilityOrRankAndZeroWithoutOfficer()
	{
		// Small ships carry no officer
		type.setSize(ShipType.SMALL_SHIP_MAXSIZE + 1);
		assertEquals(0, value("OFFICER_SKILL", "ability", "NAV"), 0);

		Offizier officer = new Offizier(player, "Kirk");
		officer.setAbility(Offizier.Ability.NAV, 42);
		officer.setRang(2);
		persist(officer);
		officer.stationierenAuf(ship);

		assertEquals(42, value("OFFICER_SKILL", "ability", "NAV"), 0);
		assertEquals(2, value("OFFICER_SKILL", "ability", "RANK"), 0);
	}

	@Test
	public void shipType_shouldTellWhetherTheShipIsOfOneOfTheTypes()
	{
		assertEquals(1, value("SHIP_TYPE", "types", "999, " + type.getId()), 0);
		assertEquals(0, value("SHIP_TYPE", "types", "999"), 0);
	}

	@Test
	public void shipStatus_shouldBeThePercentageOfTheTypesMaximum()
	{
		ship.setHull(50);
		ship.setShields(100);
		ship.setCrew(5);
		ship.setEnergy(10);

		assertEquals(25, value("SHIP_STATUS", "part", "HULL"), 0.001);
		assertEquals(100, value("SHIP_STATUS", "part", "SHIELDS"), 0.001);
		assertEquals(50, value("SHIP_STATUS", "part", "CREW"), 0.001);
		assertEquals(25, value("SHIP_STATUS", "part", "ENERGY"), 0.001);
	}

	@Test
	public void shipValue_shouldReadTheShipsStateOrItsTypesValue()
	{
		ship.setHull(123);

		assertEquals(123, value("SHIP_VALUE", "property", "HULL"), 0);
		assertEquals(200, value("SHIP_VALUE", "property", "TYPE_HULL"), 0);
		assertEquals("Unbekannte Werte zählen als 0", 0, value("SHIP_VALUE", "property", "GIBTSNICHT"), 0);
	}

	@Test
	public void shipFlag_shouldTellWhetherTheShipTypeHasTheFlag()
	{
		assertEquals(0, value("SHIP_FLAG", "flag", ShipTypeFlag.TRADEPOST.name()), 0);

		type.setFlags(ShipTypeFlag.TRADEPOST.getFlag());

		assertEquals(1, value("SHIP_FLAG", "flag", ShipTypeFlag.TRADEPOST.name()), 0);
	}

	@Test
	public void random_shouldScaleTheRollIntoTheBounds()
	{
		random = 50;
		assertEquals(15, value("RANDOM", "from", "10", "to", "20"), 0.001);
		random = 0;
		assertEquals(10, value("RANDOM", "from", "10", "to", "20"), 0.001);
	}

	@Test
	public void cargo_shouldCountTheItemInTheShipsCargo()
	{
		Cargo cargo = new Cargo();
		cargo.addResource(new ItemID(16), 7);
		ship.setCargo(cargo);

		assertEquals(7, value("CARGO", "resource", "16"), 0);
		assertEquals("„i16“ meint dasselbe Item", 7, value("CARGO", "resource", "i16"), 0);
		assertEquals(0, value("CARGO", "resource", "17"), 0);
	}

	@Test
	public void units_shouldCountTheUnitTypeOnBoard()
	{
		UnitType marines = new UnitType();
		marines.setName("Marines");
		marines.setBuildCosts(new Cargo());
		marines.setPicture("none.png");
		marines.setRes(persist(research("Infanterie")));
		persist(marines);
		UnitCargo units = ship.getUnits();
		units.addUnit(marines, 4);
		ship.setUnits(units);

		assertEquals(4, value("UNITS", "unit", String.valueOf(marines.getId())), 0);
		assertEquals("Unbekannte Einheiten zählen als 0", 0, value("UNITS", "unit", "999999"), 0);
	}

	@Test
	public void fleetStrength_shouldCountTheMilitaryShipsOfThePlayerInTheSector()
	{
		assertEquals("Ein Schiff ohne Waffen zählt nicht", 0, value("FLEET_STRENGTH"), 0);

		ShipType cruiser = militaryType();
		persist(new Ship(player, cruiser, system.getID(), 5, 5));
		persist(new Ship(player, cruiser, system.getID(), 9, 9));
		getEM().flush();

		assertTrue("Der Kreuzer im Sektor zählt, der außerhalb nicht", value("FLEET_STRENGTH") > 0);
		double one = value("FLEET_STRENGTH");
		persist(new Ship(player, cruiser, system.getID(), 5, 5));
		getEM().flush();
		assertEquals(2 * one, value("FLEET_STRENGTH"), 0.001);
	}

	@Test
	public void strengthRatio_shouldDivideThePlayersStrengthByTheNpcs()
	{
		ShipType cruiser = militaryType();
		persist(new Ship(player, cruiser, system.getID(), 5, 5));
		persist(new Ship(player, cruiser, system.getID(), 5, 5));
		getEM().flush();
		assertEquals("Ohne NPC-Kampfkraft gilt der Spieler als weit überlegen", 1000, value("STRENGTH_RATIO"), 0.001);

		persist(new Ship(npc, cruiser, system.getID(), 5, 5));
		getEM().flush();

		assertEquals(2, value("STRENGTH_RATIO"), 0.001);
	}

	@Test
	public void campaignTicks_shouldBeTheCampaignsTickCount()
	{
		assertEquals(0, value("CAMPAIGN_TICKS"), 0);

		new CampaignService(getEM()).recordCampaignTick(campaign);
		campaign = new CampaignService(getEM()).findOpenCampaign().orElseThrow();

		assertEquals(1, value("CAMPAIGN_TICKS"), 0);
	}

	@Test
	public void variable_shouldReadThePlayersOrTheGlobalValue()
	{
		CampaignVariableService variables = new CampaignVariableService();
		variables.set(campaign.getId(), player.getId(), "konvois", 3);
		variables.set(campaign.getId(), CampaignVariableService.GLOBAL, "phase", 2);

		assertEquals(3, value("VARIABLE", "name", "konvois", "scope", "USER"), 0);
		assertEquals(2, value("VARIABLE", "name", "phase", "scope", "GLOBAL"), 0);
		assertEquals("Nicht gesetzt ist 0", 0, value("VARIABLE", "name", "phase", "scope", "USER"), 0);
	}

	@Test
	public void research_shouldTellWhetherThePlayerHasResearchedIt()
	{
		Forschung warp = persist(research("Warpantrieb"));
		getEM().flush();
		assertEquals(0, value("RESEARCH", "research", String.valueOf(warp.getID())), 0);

		player.addResearch(warp);

		assertEquals(1, value("RESEARCH", "research", String.valueOf(warp.getID())), 0);
	}

	@Test
	public void alliance_shouldTellWhetherThePlayerIsInAnyOrTheGivenAlliance()
	{
		assertEquals(0, value("ALLIANCE"), 0);

		Ally ally = persist(new Ally("Bund", player));
		player.setAlly(ally);

		assertEquals(1, value("ALLIANCE"), 0);
		assertEquals(1, value("ALLIANCE", "alliance", String.valueOf(ally.getId())), 0);
		assertEquals(0, value("ALLIANCE", "alliance", String.valueOf(ally.getId() + 1)), 0);
	}

	private ShipType militaryType()
	{
		ShipType cruiser = persist(new ShipType(ShipClasses.KREUZER));
		cruiser.setSize(10);
		cruiser.setWeapons(Map.of("laser", 1));
		return cruiser;
	}

	private static Forschung research(String name)
	{
		Forschung research = new Forschung();
		research.setName(name);
		research.setCosts(new Cargo());
		research.setDescription("");
		return research;
	}
}
