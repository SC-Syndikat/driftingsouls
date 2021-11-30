package net.driftingsouls.ds2.server.services;

import net.driftingsouls.ds2.server.ContextCommon;
import net.driftingsouls.ds2.server.WellKnownConfigValue;
import net.driftingsouls.ds2.server.battles.*;
import net.driftingsouls.ds2.server.comm.PM;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.WellKnownUserValue;
import net.driftingsouls.ds2.server.entities.ally.Ally;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.ConfigService;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipClasses;
import net.driftingsouls.ds2.server.ships.ShipTypeData;
import net.driftingsouls.ds2.server.ships.ShipTypeFlag;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.hibernate.FlushMode;
import org.hibernate.Query;
import org.hibernate.Session;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;
import java.util.*;

@Service
public class SchlachtErstellenService
{
	private static final Logger LOG = LogManager.getLogger(SchlachtErstellenService.class);

	/**
	 * Erstellt eine neue Schlacht.
	 * @param user Der Spieler, der die Schlacht beginnt
	 * @param ownShipID Die ID des Schiffes des Spielers, der angreift
	 * @param enemyShipID Die ID des angegriffenen Schiffes
	 * @return Die Schlacht, falls sie erfolgreich erstellt werden konnte. Andernfalls <code>null</code>
	 */
	public Battle erstelle( User user, int ownShipID, int enemyShipID ) {
		Context context = ContextMap.getContext();
		org.hibernate.Session db = context.getDB();

		return erstelle(user, (Ship)db.get(Ship.class, ownShipID), (Ship)db.get(Ship.class,enemyShipID), false);
	}

	/**
	 * Erstellt eine neue Schlacht bzw. gibt die aktuell laufende Schlacht auf diesem Sektor zurueck
	 * @param user Der Spieler, der die Schlacht beginnt
	 * @param ownShip Das Schiff des des Spielers, der angreift
	 * @param enemyShip Das angegriffene Schiffes
	 * @param startOwn <code>true</code>, falls eigene gelandete Schiffe starten sollen
	 * @return Die Schlacht, falls sie erfolgreich erstellt werden konnte oder bereits existierte
	 * @throws java.lang.IllegalArgumentException Falls mit den uebergebenen Parametern keine Schlacht erstellt werden kann
	 */
	public Battle erstelle(@Nonnull User user, @Nonnull Ship ownShip, @Nonnull Ship enemyShip, final boolean startOwn ) throws IllegalArgumentException {
		Context context = ContextMap.getContext();
		org.hibernate.Session db = context.getDB();

		Query battleQuery = db.createQuery("from Battle");
		List<Battle> battlelist = Common.cast(battleQuery.list());

		//Es darf nur noch eine Schlacht pro Sektor existieren, also schauen wir doch mal, ob da schon was ist
		for(Battle b : battlelist)
		{
			if(b.getLocation().sameSector(0, enemyShip.getLocation(), 0))
			{
				//wir haben im dem Sektor schon eine Schlacht, also geben wir diese nun zurueck
				return b;
			}
		}

		//scheinbar haben wir hier noch keine Schlacht, dann machen wir mal eine neue auf
		LOG.info("battle: "+user+" :: "+ownShip.getId()+" :: "+enemyShip.getId());

		Ship tmpEnemyShip = enemyShip;
		User enemyUser = tmpEnemyShip.getOwner();

		checkBattleConditions(user, enemyUser, ownShip, tmpEnemyShip);

		//
		// Schiffsliste zusammenstellen
		//
		BattleShip enemyBattleShip = null;
		BattleShip ownBattleShip = null;

		HashMap<User,Integer> commanders = new HashMap<>();

		//alle Schiffe auf dem Feld sammeln
		Query shipQuery = db.createQuery("from Ship as s inner join fetch s.owner as u " +
				"where s.id>:minid and s.x=:x and s.y=:y and " +
				"s.system=:system and s.battle is null "+
				"and locate('disable_iff',s.status)=0 and (u.vaccount=0 or u.wait4vac > 0)")
				.setInteger("minid", 0)
				.setInteger("x", ownShip.getX())
				.setInteger("y", ownShip.getY())
				.setInteger("system", ownShip.getSystem());

		List<Ship> shiplist = Common.cast(shipQuery.list());

		Set<BattleShip> ships = new HashSet<>();
		Set<BattleShip> secondRowShips = new HashSet<>();
		Map<User,List<BattleShip>> ship_map = new HashMap<>();

		for (Ship aShip : shiplist) {
			// Loot-Truemmer sollten in keine Schlacht wandern... (nicht schoen, gar nicht schoen geloest)
			if ((aShip.getOwner().getId() == -1) && (aShip.getType() == new ConfigService().getValue(WellKnownConfigValue.TRUEMMER_SHIPTYPE))) {
				continue;
			}
			User tmpUser = aShip.getOwner();

			if (tmpUser.isNoob()) {
				continue;
			}

			//gedockte + gelandete Schiffe werden ueber ihr BaseShip in die Schlacht gejoint
			//andernfalls koennen ueber Flotten Schiffe der Schlacht joinen, welche auf einem Traeger ausserhalb sind.
			if(aShip.isDocked() || aShip.isLanded()){
				continue;
			}

			BattleShip battleShip = new BattleShip(null, aShip);
			ShipTypeData shiptype = aShip.getBaseType();
			boolean ownShipFound = false;

			//dann fuegen wir das schiff nun mal in der liste hinzu
			//aber erst schauen wir, wo denn der User steht
			if(!commanders.containsKey(tmpUser))
			//wir kennen den User noch nicht, also legen wir ihn mal an
			{
				int tmp_side = 0;
				//vielleicht ist ja schon jemand aus seiner Ally dabei:
				if(tmpUser.getAlly() != null)
				{
					for(Map.Entry<User,Integer> commander: commanders.entrySet())
					{
						if(tmpUser.getAlly() == commander.getKey().getAlly())
							tmp_side = commander.getValue();
							break;
					}
				}
				if(tmp_side == 0)
				//ok, seine Ally ist noch nicht drin, dann verpassen wir ihm eine eigene Seite
				{
					for(int i: commanders.values())
					{
						if (i>tmp_side)
							tmp_side = i;
					}
				}
				commanders.put(tmpUser,tmp_side+1);
			}
			//der User ist nun Commander und wir kennen seine Seite
			int side = commanders.get(tmpUser);

			battleShip.setSide(side);
			ownShipFound = true;

			if (aShip == ownShip) {
				ownBattleShip = battleShip;
			}
			else
			{
				List<BattleShip> tmpUser_ships = ship_map.get(tmpUser);
				tmpUser_ships.add(battleShip);
				ship_map.put(tmpUser, tmpUser_ships);
			}

      if (shiptype.hasFlag(ShipTypeFlag.SECONDROW) && aShip.getEinstellungen().gotoSecondrow()) {
				secondRowShips.add(battleShip);
			}

			//sollten auf dem Schiff Schiffe gedockt / gelandet sein, joinen diese nun ebenfalls der Schlacht
			for(Ship lShip : aShip.getGedockteUndGelandeteSchiffe()){
				BattleShip blShip = new BattleShip(null, lShip);
				ShipTypeData lshiptype = lShip.getBaseType();
				List<BattleShip> tmpUser_ships = ship_map.get(tmpUser);

				//natuerlich kommt das gedockte Schiff auf die selbe Seite und in die selbe Reihe wie sein Traeger
				blShip.setSide(battleShip.getSide());
				tmpUser_ships.add(battleShip);
				ship_map.put(tmpUser, tmpUser_ships);
				if(secondRowShips.contains(battleShip)){
					secondRowShips.add(blShip);
				}
				//Geschuetze deaktivieren
				if ((lshiptype.getShipClass() == ShipClasses.GESCHUETZ) ) {
					blShip.addFlag(BattleShipFlag.DISABLE_WEAPONS);
				}
			}
		}


		//
		// Schauen wir mal ob wir was sinnvolles aus der DB gelesen haben
		// - Wenn nicht: Abbrechen
		//

		if( ownBattleShip == null ) {
			throw new IllegalArgumentException("Offenbar liegt ein Problem mit dem von Ihnen angegebenen Schiff oder Ihrem eigenen Schiff vor (wird es evtl. bereits angegriffen?).");
		}

		Battle battle = new Battle(ownShip.getLocation());
		battle.setShips(ship_map);

		addToSecondRow(battle, secondRowShips);

		if( enemyBattleShip == null && (battle.getShips(enemyUser).size() == 0) ) {
			throw new IllegalArgumentException("Offenbar liegt ein Problem mit den feindlichen Schiffen vor. Es gibt nämlich keine die angegriffen werden könnten.");
		}
		else if( enemyBattleShip == null ) {
			throw new IllegalArgumentException("Offenbar liegt ein Problem mit den feindlichen Schiffen vor. Es gibt zwar welche, jedoch fehlt das Zielschiff.");
		}

		//
		// Schlacht in die DB einfuegen
		//
		battle.setCommanders(commanders);
		battle.setFlag(BattleFlag.FIRSTROUND);
		db.save(battle);

		//
		// Schiffe in die Schlacht einfuegen
		//

		int tick = context.get(ContextCommon.class).getTick();

		// * Gegnerische Schiffe in die Schlacht einfuegen
		List<Integer> idlist = new ArrayList<>();
		List<Integer> startlist = new ArrayList<>();
		enemyShip = enemyBattleShip.getShip();
		if( enemyBattleShip.getShip().isLanded() )
		{
			enemyShip.getBaseShip().start(enemyShip);
			startlist.add(enemyBattleShip.getId());
		}
		idlist.add(enemyBattleShip.getId());

		enemyBattleShip.setBattle(battle);
		db.persist(enemyBattleShip);

		enemyShip.setBattle(battle);

		insertShipsIntoDatabase(battle, battle.getShips(enemyUser), startlist, idlist);
		if( startlist.size() > 0 ) {
			battle.logme(startlist.size() + " Jäger sind automatisch gestartet\n");
			battle.log(new SchlachtLogAktion(1, startlist.size() + " Jäger sind automatisch gestartet"));

			startlist.clear();
		}

		startlist = new ArrayList<>();
		battle.getShips(enemyUser).add(enemyBattleShip);
		battle.setShipIndex(enemyUser,battle.getShips(enemyUser).size()-1);

		// * Eigene Schiffe in die Schlacht einfuegen
		idlist.add(ownBattleShip.getId());

		ownBattleShip.setBattle(battle);
		db.persist(ownBattleShip);

		ownShip.setBattle(battle);
		if(ownBattleShip.getShip().isLanded())
		{
			//TODO: Maybe we could optimize this a little bit further with mass start?
			ownShip.getBaseShip().start(enemyShip);
			startlist.add(enemyBattleShip.getId());
		}
		ownShip.setDocked("");

		insertShipsIntoDatabase(battle, battle.getShips(user), startlist, idlist);
		if( startOwn && startlist.size() > 0 ) {
			battle.logme(startlist.size() + " Jäger sind automatisch gestartet\n");
			battle.log(new SchlachtLogAktion(0, startlist.size() + " Jäger sind automatisch gestartet"));
		}
		battle.getShips(user).add(ownBattleShip);
		battle.setFiringShip(user,ownBattleShip.getShip());

		//
		// Log erstellen
		//
		createBattleLog(db, battle, ownBattleShip, enemyBattleShip, tick);

		//
		// Beziehungen aktualisieren
		//


		// dann setzen wir doch mal die Relations fuer den Angreifer und den Angegriffenen
		// sowie die zugehoerigen Allys.
		db.setFlushMode(FlushMode.COMMIT);
		List<User> allymember = user.getAlly().getMembers();
		List<User> enemyallymember = enemyUser.getAlly().getMembers();

		for( User auser : allymember ) {
			for( User euser : enemyallymember ) {
				auser.setRelation(euser.getId(), User.Relation.ENEMY);
				euser.setRelation(auser.getId(), User.Relation.ENEMY);
			}
		}

		// PM Wegen Schlachteröffnung schicken, sofern die Spieler dies wollen.
		User niemand = (User)db.get(User.class, -1);
		String msg = "Es wurde eine Schlacht bei "+ownShip.getLocation().displayCoordinates(false)+" eröffnet.\n" +
				"Deine 2. Reihe ist ";
		String msg1 = "";
		for(User auser : commanders.keySet())
		{
			if (battle.isSecondRowStable(commanders.get(auser)))
			{
				msg1 +="stabil.";
			}
			else
			{
				msg1 += "instabil. Vorsicht!";
			}
			if(auser.getUserValue(WellKnownUserValue.GAMEPLAY_USER_BATTLE_PM))
			{
				PM.send(niemand, auser.getId(), "Schlacht eröffnet", msg+msg1);
			}
		}

		db.setFlushMode(FlushMode.AUTO);

		return battle;
	}

	private void createBattleLog(Session db, Battle battle, BattleShip ownBattleShip, BattleShip enemyBattleShip, int tick)
	{
		SchlachtLog log = new SchlachtLog(battle, tick);
		db.persist(log);
		battle.setSchlachtLog(log);

		battle.log(new SchlachtLogKommandantWechselt(0, ownBattleShip.getOwner()));
		battle.log(new SchlachtLogKommandantWechselt(1, enemyBattleShip.getOwner()));
	}

	private void insertShipsIntoDatabase(Battle battle, List<BattleShip> ships, List<Integer> startlist, List<Integer> idlist)
	{
		if( ships.isEmpty() ) {
			return;
		}

		Context context = ContextMap.getContext();
		org.hibernate.Session db = context.getDB();
		for (BattleShip ship : ships) {
			Ship baseShip = ship.getShip().getBaseShip();
			if (baseShip != null && ship.getShip().isLanded() && baseShip.getEinstellungen().startFighters() && ship.getShip().getTypeData().getShipClass() == ShipClasses.JAEGER ) {
				ship.getShip().setDocked("");
				startlist.add(ship.getId());
			}
			idlist.add(ship.getId());
			ship.getShip().setBattle(battle);
			ship.setBattle(battle);
			db.persist(ship);
		}
	}

	private void checkBattleConditions(User user, User enemyUser, Ship ownShip, Ship enemyShip) throws IllegalArgumentException
	{
		// Kann der Spieler ueberhaupt angreifen (Noob-Schutz?)
		if (user.isNoob())
		{
			throw new IllegalArgumentException("Sie stehen unter GCP-Schutz (Neulingsschutz) und k&ouml;nnen daher keinen Gegner angreifen!<br />Hinweis: Der GCP-Schutz kann in den Optionen vorzeitig beendet werden");
		}

		if ((ownShip == null) || (ownShip.getId() < 0) || (ownShip.getOwner() != user))
		{
			throw new IllegalArgumentException("Das angreifende Schiff existiert nicht oder untersteht nicht Ihrem Kommando!");
		}

		if ((enemyShip == null) || (enemyShip.getId() < 0))
		{
			throw new IllegalArgumentException("Das angegebene Zielschiff existiert nicht!");
		}

		if (!ownShip.getLocation().sameSector(0, enemyShip.getLocation(), 0))
		{
			throw new IllegalArgumentException("Die beiden Schiffe befinden sich nicht im selben Sektor");
		}

		//
		// Kann der Spieler angegriffen werden (NOOB-Schutz?/Vac-Mode?)
		//

		if (enemyUser.isNoob())
		{
			throw new IllegalArgumentException("Der Gegner steht unter GCP-Schutz (Neulingsschutz) und kann daher nicht angegriffen werden!");
		}

		if (enemyUser.getVacationCount() != 0 && enemyUser.getWait4VacationCount() == 0)
		{
			throw new IllegalArgumentException("Der Gegner befindet sich im Vacation-Modus und kann daher nicht angegriffen werden!");
		}

		//
		// IFF-Stoersender?
		//
		boolean disable_iff = enemyShip.getStatus().contains("disable_iff");
		if (disable_iff)
		{
			throw new IllegalArgumentException("Dieses Schiff kann nicht angegriffen werden (egal wieviel Du mit der URL rumspielst!)");
		}

	}

	/**
	 * Adds ships which have the second row flag to the second row.
	 * If there is no first row no ship will be added to the second row.
	 *
	 * @param battle Die momentan in der Erstellung befindliche Schlacht
	 * @param secondRowShips Ships to add.
	 */
	private void addToSecondRow(Battle battle, Set<BattleShip> secondRowShips)
	{
		Context context = ContextMap.getContext();
		org.hibernate.Session db = context.getDB();
		db.setFlushMode(FlushMode.COMMIT);

		Map<Ship,BattleShip> battleShipMap = new HashMap<>();
		for(User u:battle.getCommanders().keySet())
		{
			for(BattleShip ship : battle.getShips(u))
			{
				battleShipMap.put(ship.getShip(), ship);
			}
		}

		for(BattleShip ship: secondRowShips)
		{
			if( ship.getShip().getEinstellungen().gotoSecondrow() && ship.getShip().getTypeData().hasFlag(ShipTypeFlag.SECONDROW) )
			{

				ship.addFlag(BattleShipFlag.SECONDROW);
				if(ship.getTypeData().getJDocks() > 0)
				{
					List<Ship> landedShips = ship.getShip().getLandedShips();
					for(Ship landedShip: landedShips)
					{
						if(!landedShip.getTypeData().hasFlag(ShipTypeFlag.SECONDROW))
						{
							continue;
						}

						BattleShip aship = battleShipMap.get(landedShip);
						if(aship != null)
						{
							aship.addFlag(BattleShipFlag.SECONDROW);
						}
					}
				}
				if( ship.getTypeData().getADocks() == 0)
				{
					List<Ship> dockedShips = ship.getShip().getDockedShips();
					for(Ship dockedShip: dockedShips)
					{

						BattleShip aship = battleShipMap.get(dockedShip);
						if(aship != null)
						{
							aship.addFlag(BattleShipFlag.SECONDROW);
						}
					}
				}
			}
		}
		db.setFlushMode(FlushMode.AUTO);
	}

}
