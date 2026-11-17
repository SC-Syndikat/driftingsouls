package net.driftingsouls.ds2.server.campaign.dialog.actions;

import net.driftingsouls.ds2.server.DBSingleTransactionTest;
import net.driftingsouls.ds2.server.Location;
import net.driftingsouls.ds2.server.battles.Battle;
import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignJumpNodeLimitService;
import net.driftingsouls.ds2.server.campaign.CampaignJumpNodeState;
import net.driftingsouls.ds2.server.campaign.CampaignMapService;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.campaign.JumpNodeLimits;
import net.driftingsouls.ds2.server.campaign.NpcTestUsers;
import net.driftingsouls.ds2.server.campaign.ShipGroupRepository;
import net.driftingsouls.ds2.server.campaign.ShipGroupTemplate;
import net.driftingsouls.ds2.server.campaign.dialog.CampaignVariableService;
import net.driftingsouls.ds2.server.campaign.dialog.DialogContext;
import net.driftingsouls.ds2.server.campaign.dialog.DialogRepository;
import net.driftingsouls.ds2.server.campaign.dialog.DialogTypes;
import net.driftingsouls.ds2.server.campaign.dialog.ObjectType;
import net.driftingsouls.ds2.server.campaign.dialog.Params;
import net.driftingsouls.ds2.server.campaign.dialog.TrustService;
import net.driftingsouls.ds2.server.campaign.interaction.Interaction;
import net.driftingsouls.ds2.server.campaign.interaction.InteractionRepository;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.cargo.ItemData;
import net.driftingsouls.ds2.server.cargo.ItemID;
import net.driftingsouls.ds2.server.comm.PM;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.entities.Forschung;
import net.driftingsouls.ds2.server.entities.JumpNode;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.UserFlag;
import net.driftingsouls.ds2.server.entities.WellKnownUserValue;
import net.driftingsouls.ds2.server.repositories.ItemRepository;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipClasses;
import net.driftingsouls.ds2.server.ships.ShipType;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * One test per action a dialog choice can trigger.
 */
public class StandardActionsTest extends DBSingleTransactionTest
{
	private User npc;
	private User player;
	private StarSystem system;
	private StarSystem outside;
	private ShipType type;
	private Ship npcShip;
	private Ship ship;
	private Campaign campaign;

	@Before
	public void setUp()
	{
		// NPC first: creating it clears the persistence context
		npc = NpcTestUsers.create(getEM(), "admiral", -77);
		User gamemaster = persist(new User("gm", "***", 0, "", new Cargo(), "gm@localhost"));
		gamemaster.setFlag(UserFlag.GAMEMASTER);
		player = persist(new User("player", "***", 0, "", new Cargo(), "player@localhost"));
		system = persist(new StarSystem());
		system.setWidth(50);
		system.setHeight(50);
		system.setAccess(StarSystem.Access.NORMAL);
		outside = persist(new StarSystem());
		outside.setWidth(50);
		outside.setHeight(50);
		outside.setAccess(StarSystem.Access.NORMAL);
		type = persist(new ShipType(ShipClasses.AWACS));
		type.setCrew(10);
		type.setHull(200);
		type.setShields(100);
		type.setCargo(100);
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

	private void run(String action, String... params)
	{
		DialogContext context = new DialogContext(getEM(), player, ship, npc, ObjectType.SHIP, npcShip.getId(), campaign, null, player,
				() -> 0);
		DialogTypes.get().action(action).execute(context, Params.of(params));
	}

	// ---------------------------------------------------------------- Diplomacy

	@Test
	public void setRelation_shouldSetTheChosenDirections()
	{
		run("SET_RELATION", "direction", "NPC_TO_USER", "relation", "ENEMY");

		assertEquals(User.Relation.ENEMY, npc.getRelation(player));
		assertEquals(User.Relation.NEUTRAL, player.getRelation(npc));

		run("SET_RELATION", "direction", "BOTH", "relation", "FRIEND");

		assertEquals(User.Relation.FRIEND, npc.getRelation(player));
		assertEquals(User.Relation.FRIEND, player.getRelation(npc));
	}

	@Test
	public void setNpcRank_shouldGiveThePlayerTheRank()
	{
		run("SET_NPC_RANK", "rank", "4");

		assertEquals(4, player.getRank(npc).getRank());
	}

	@Test
	public void changeTrust_shouldRecordTheChange()
	{
		run("CHANGE_TRUST", "delta", "-6", "reason", "gelogen");

		assertEquals(-6, new TrustService(getEM()).trust(player.getId(), npc.getId()).getTotal(), 0.001);
	}

	// ---------------------------------------------------------------- Rewards

	@Test
	public void giveResource_shouldAddToTheCargoOrTakeItFromTheConversationShip()
	{
		run("GIVE_RESOURCE", "resource", "16", "count", "5", "from", "NEW");
		assertEquals(5, ship.getCargo().getResourceCount(new ItemID(16)));

		Cargo loot = new Cargo();
		loot.addResource(new ItemID(16), 3);
		npcShip.setCargo(loot);
		run("GIVE_RESOURCE", "resource", "16", "count", "3", "from", "OBJECT");

		assertEquals(8, ship.getCargo().getResourceCount(new ItemID(16)));
		assertEquals(0, npcShip.getCargo().getResourceCount(new ItemID(16)));
		assertThrows("Das Gesprächsschiff hat nichts mehr", CampaignException.class,
				() -> run("GIVE_RESOURCE", "resource", "16", "count", "1", "from", "OBJECT"));
	}

	@Test
	public void giveResource_shouldRefuseWhenTheCargoIsFull()
	{
		// An item with mass; the item cache is filled directly, as the test transaction is never committed
		int heavy = 99901;
		ItemRepository.getInstance().putItemData(new ItemData(heavy, "None", 1, "", "", "Fass", "", "COMMON", 0, (byte) 0, (byte) 0));

		run("GIVE_RESOURCE", "resource", String.valueOf(heavy), "count", "100", "from", "NEW");
		assertThrows(CampaignException.class, () -> run("GIVE_RESOURCE", "resource", String.valueOf(heavy), "count", "1", "from", "NEW"));
		assertEquals(100, ship.getCargo().getResourceCount(new ItemID(heavy)));
	}

	@Test
	public void takeResource_shouldRemoveFromTheCargoAndHandItToTheNpcShip()
	{
		Cargo cargo = new Cargo();
		cargo.addResource(new ItemID(16), 5);
		ship.setCargo(cargo);

		run("TAKE_RESOURCE", "resource", "16", "count", "2", "to", "GONE");
		run("TAKE_RESOURCE", "resource", "16", "count", "2", "to", "NPC_SHIP");

		assertEquals(1, ship.getCargo().getResourceCount(new ItemID(16)));
		assertEquals(2, npcShip.getCargo().getResourceCount(new ItemID(16)));
		assertThrows(CampaignException.class, () -> run("TAKE_RESOURCE", "resource", "16", "count", "2", "to", "GONE"));
	}

	@Test
	public void giveCredits_shouldPayFromTheNpcOrCreateTheMoney()
	{
		npc.setKonto(BigInteger.valueOf(1000));
		player.setKonto(BigInteger.ZERO);

		run("GIVE_CREDITS", "amount", "600", "source", "NPC");
		assertEquals(600, player.getKonto().longValue());
		assertEquals(400, npc.getKonto().longValue());

		assertThrows("Das NPC-Konto reicht nicht", CampaignException.class, () -> run("GIVE_CREDITS", "amount", "600", "source", "NPC"));

		run("GIVE_CREDITS", "amount", "600", "source", "NONE");
		assertEquals(1200, player.getKonto().longValue());
		assertEquals(400, npc.getKonto().longValue());
	}

	@Test
	public void unlockItem_shouldMakeTheItemKnown()
	{
		run("UNLOCK_ITEM", "item", "4711");

		assertTrue(player.isKnownItem(4711));
	}

	@Test
	public void unlockResearch_shouldGiveThePlayerTheResearch()
	{
		Forschung warp = new Forschung();
		warp.setName("Warpantrieb");
		warp.setCosts(new Cargo());
		warp.setDescription("");
		persist(warp);
		getEM().flush();

		run("UNLOCK_RESEARCH", "research", String.valueOf(warp.getID()));

		assertTrue(player.hasResearched(warp));
		assertThrows(CampaignException.class, () -> run("UNLOCK_RESEARCH", "research", "999999"));
	}

	@Test
	public void giveShip_shouldHandOverTheConversationShip()
	{
		run("GIVE_SHIP", "source", "OBJECT");

		assertEquals(player, npcShip.getOwner());
	}

	// ---------------------------------------------------------------- Battle

	@Test
	public void startBattle_shouldLetTheConversationShipAttack()
	{
		for (User user : List.of(player, npc))
		{
			user.setFlag(UserFlag.NOOB, false);
			user.setUserValue(WellKnownUserValue.GAMEPLAY_USER_BATTLE_PM, false);
		}

		run("START_BATTLE", "source", "OBJECT");

		Battle battle = ship.getBattle();
		assertNotNull(battle);
		assertSame(battle, npcShip.getBattle());
	}

	// ---------------------------------------------------------------- Script

	@Test
	public void spawnShipGroup_shouldCreateTheShipsAndCopyTheInteractionOntoTheFirst()
	{
		int template = template("Piratenrotte", 2);
		int node = new DialogRepository().createNode("Gruß", "Pirat", "", "Was willst du?");
		int interaction = new InteractionRepository().save(new Interaction(0, campaign.getId(), "Mit dem Piraten sprechen", ObjectType.SHIP,
				npcShip.getId(), node, npc.getId(), true, null, null, Interaction.Audience.ALL, Set.of(), Set.of()));

		run("SPAWN_SHIP_GROUP", "template", String.valueOf(template), "location", system.getID() + ":20/20",
				"interaction", String.valueOf(interaction));

		List<Ship> spawned = shipsOf(npc, 20, 20);
		assertEquals(2, spawned.size());
		List<Interaction> copies = new InteractionRepository().findFor(ObjectType.SHIP, spawned.get(0).getId());
		assertEquals(1, copies.size());
		assertEquals("Nur für den Spieler, der es ausgelöst hat", Set.of(player.getId()), copies.get(0).audienceUserIds);
	}

	@Test
	public void removeNpcShips_shouldDestroyTheNpcsShipsInTheSector()
	{
		Ship elsewhere = persist(new Ship(npc, type, system.getID(), 9, 9));
		getEM().flush();

		run("REMOVE_NPC_SHIPS");
		getEM().flush();

		assertTrue(shipsOf(npc, 5, 5).isEmpty());
		assertEquals("Außerhalb des Sektors bleibt es", npc, getEM().find(Ship.class, elsewhere.getId()).getOwner());
	}

	@Test
	public void repairShip_shouldRestoreHullShieldsAndSubsystems()
	{
		ship.setHull(10);
		ship.setShields(0);
		ship.setEngine(20);
		ship.setWeapons(30);

		run("REPAIR_SHIP");

		assertEquals(200, ship.getHull());
		assertEquals(100, ship.getShields());
		assertEquals(100, ship.getEngine());
		assertEquals(100, ship.getWeapons());
	}

	@Test
	public void setAndAddVariable_shouldChangeThePlayersOrTheGlobalValue()
	{
		CampaignVariableService variables = new CampaignVariableService();

		run("SET_VARIABLE", "name", "konvois", "scope", "USER", "value", "3");
		run("ADD_VARIABLE", "name", "konvois", "scope", "USER", "delta", "2");
		run("ADD_VARIABLE", "name", "phase", "scope", "GLOBAL", "delta", "-1");

		assertEquals(5, variables.get(campaign.getId(), player.getId(), "konvois"));
		assertEquals(-1, variables.get(campaign.getId(), CampaignVariableService.GLOBAL, "phase"));
	}

	@Test
	public void setInteractionActive_shouldSwitchTheInteraction()
	{
		int node = new DialogRepository().createNode("Gruß", "Admiral", "", "Hallo.");
		InteractionRepository interactions = new InteractionRepository();
		int interaction = interactions.save(new Interaction(0, campaign.getId(), "Grüßen", ObjectType.SHIP, npcShip.getId(), node, npc.getId(),
				false, null, null, Interaction.Audience.ALL, Set.of(), Set.of()));

		run("SET_INTERACTION_ACTIVE", "interaction", String.valueOf(interaction), "active", "1");

		assertTrue(interactions.find(interaction).orElseThrow().active);
		assertThrows(CampaignException.class, () -> run("SET_INTERACTION_ACTIVE", "interaction", "999999", "active", "1"));
	}

	@Test
	public void setJumpNodeState_shouldChangeTheCampaignJumpNode()
	{
		JumpNode node = jumpNode();

		run("SET_JUMP_NODE_STATE", "node", String.valueOf(node.getId()), "state", "VISIBLE");

		assertEquals(CampaignJumpNodeState.VISIBLE, new CampaignMapService(getEM()).getJumpNodeState(node.getId()));
	}

	@Test
	public void setJumpNodeLimits_shouldChangeOnlyTheGivenLimits()
	{
		JumpNode node = jumpNode();

		run("SET_JUMP_NODE_LIMITS", "node", String.valueOf(node.getId()), "classes", "KREUZER", "perShip", "30", "period", "PER_TICK",
				"metric", "KEEP");
		run("SET_JUMP_NODE_LIMITS", "node", String.valueOf(node.getId()), "perPlayer", "100", "period", "KEEP", "metric", "KEEP");

		JumpNodeLimits limits = new CampaignJumpNodeLimitService(getEM()).load(node.getId());
		assertEquals(Set.of("KREUZER"), limits.allowedShipClasses);
		assertEquals(Integer.valueOf(30), limits.maxPointsPerShip);
		assertEquals(Integer.valueOf(100), limits.maxPointsPerPlayer);
		assertEquals(JumpNodeLimits.Period.PER_TICK, limits.playerLimitPeriod);

		run("SET_JUMP_NODE_LIMITS", "node", String.valueOf(node.getId()), "perShip", "-", "period", "KEEP", "metric", "KEEP");
		assertNull("„-“ hebt das Limit auf", new CampaignJumpNodeLimitService(getEM()).load(node.getId()).maxPointsPerShip);
	}

	@Test
	public void sendPm_shouldWriteToThePlayerInTheNpcsName()
	{
		run("SEND_PM", "to", "PLAYER", "title", "Warnung", "text", "Hüte dich, {spieler}.");
		getEM().flush();

		List<PM> pms = getEM().createQuery("from PM where empfaenger = :user and title = 'Warnung'", PM.class)
				.setParameter("user", player)
				.getResultList();
		assertEquals(1, pms.size());
		assertEquals(npc, pms.get(0).getSender());
		assertEquals("Hüte dich, " + player.getPlainname() + ".", pms.get(0).getInhalt());
	}

	private int template(String name, int count)
	{
		ShipGroupRepository repository = new ShipGroupRepository();
		int id = repository.create(name, "", false);
		repository.addEntry(id, new ShipGroupTemplate.Entry(0, type.getId(), count, "Pirat {n}", "", "", "", null, 0));
		return id;
	}

	private JumpNode jumpNode()
	{
		return new CampaignMapService(getEM()).createJumpNode(campaign, new Location(outside.getID(), 5, 5), new Location(system.getID(), 1, 1),
				"Zugang", false).get(0);
	}

	private List<Ship> shipsOf(User owner, int x, int y)
	{
		return getEM().createQuery("from Ship where owner = :owner and system = :system and x = :x and y = :y and id > 0 order by id", Ship.class)
				.setParameter("owner", owner)
				.setParameter("system", system.getID())
				.setParameter("x", x)
				.setParameter("y", y)
				.getResultList();
	}
}
