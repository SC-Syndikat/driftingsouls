/*
 *	Drifting Souls 2
 *	Copyright (c) 2006 Christopher Jung
 *
 *	This library is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU Lesser General Public
 *	License as published by the Free Software Foundation; either
 *	version 2.1 of the License, or (at your option) any later version.
 *
 *	This library is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *	Lesser General Public License for more details.
 *
 *	You should have received a copy of the GNU Lesser General Public
 *	License along with this library; if not, write to the Free Software
 *	Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package net.driftingsouls.ds2.server.battles;

import net.driftingsouls.ds2.server.*;
import net.driftingsouls.ds2.server.comm.PM;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.User.Relation;
import net.driftingsouls.ds2.server.entities.ally.Ally;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.ConfigService;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.templates.comm;
import net.driftingsouls.ds2.server.ships.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.CacheMode;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.annotations.*;
import org.hibernate.annotations.Cache;

import javax.persistence.Entity;
import javax.persistence.*;
import javax.persistence.Table;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Repraesentiert eine Schlacht in DS.
 * @author Christopher Jung, Gregor Fuhs
 *
 */
@Entity
@Table(name="battles")
@Cache(usage=CacheConcurrencyStrategy.READ_WRITE)
@BatchSize(size=50)
@org.hibernate.annotations.Table(appliesTo = "battles", indexes = {@Index(name="battle_coords", columnNames = {"x", "y", "system"})})
@OptimisticLocking(type = OptimisticLockType.DIRTY)
public class Battle implements Locatable
{
	private static final Log log = LogFactory.getLog(Battle.class);

	@Id @GeneratedValue
	private int id;
	private int x;
	private int y;
	private int system;
	private int blockcount = 2;
	private long lastaction;
	private long lastturn;
	private int flags;

	@Transient
	private List<BattleSide> sides;

	@OneToOne()
	@JoinColumn
	@ForeignKey(name="battles_fk_schlachtlog")
	private SchlachtLog schlachtLog;

	@Transient
	private final StringBuilder logoutputbuffer = new StringBuilder();

	/**
	 * Generiert eine Stringrepraesentation eines Schiffes, welche
	 * in KS-Logs geschrieben werden kann.
	 * @param ship Das Schiff
	 * @return Die Stringrepraesentation
	 */
	public static String log_shiplink( Ship ship ) {
		ShipTypeData shiptype = ship.getTypeData();

		return "[tooltip="+shiptype.getNickname()+"]"+ship.getName()+"[/tooltip] ("+ship.getId()+")";
	}

	/**
	 * Konstruktor.
	 *
	 */
	public Battle() {

	}

	/**
	 * Konstruktor.
	 * @param location Die Position an der die Schlacht gefuehrt wird
	 */
	public Battle(Location location)
	{
		this();
		this.x = location.getX();
		this.y = location.getY();
		this.system = location.getSystem();
		this.lastaction = Common.time();
		this.lastturn = Common.time();
	}

	/**
	 * Gibt die ID der Schlacht zurueck.
	 * @return die ID
	 */
	public int getId() {
		return this.id;
	}

	/**
	 * Prueft, ob ein Spieler Kommandant der Seite der Schlacht ist.
	 * @param user Der Spieler
	 * @param side Die Seite
	 * @return <code>true</code>, falls er Kommandant ist
	 */

	public boolean isCommander( User user, int side ) {
		for(BattleSide s: sides)
		{
			if(s.getSide_id() == side && s.getCommander().getId() == user.getId())
				return true;
		}
		return false;
	}

	/**
	 * Gibt die Seite zurueck, fuer die der User kaempft
	 * @param User der Spieler
	 * @return die Schlachtseite bzw. <code>null</code>
	 */
	public BattleSide getSideForUser(User user)
	{
		for(BattleSide s : sides)
		{
			if(s.getUsers().contains(user))
			{
				return s;
			}
		}
		return null;
	}

	/**
	 * Setzt den Kommandaten einer Seite.
	 * @param side Die Seite
	 * @param user Der neue Kommandant
	 */
	public void setCommander(int side, User user) {
		for(BattleSide s : sides)
		{
			if(s.getSide_id() == side)
			{
				s.setCommander(user);
				return;
			}
		}
	}

	/**
	 * Gibt den Spieler zurueck, der auf einer Seite das Kommando uebernehmen will. Sollte
	 * kein solcher Spieler existieren, so wird <code>null</code> zurueckgegeben.
	 * @param side Die Seite
	 * @return Den Spieler oder <codeNULL</code>
	 */
	public User getTakeCommand(int side) {
		if(takeCommand.containsKey(side))
		{
			return takeCommand.get(side);
		}
		return null;
	}

	/**
	 * Setzt den Spieler, welcher auf einer Seite das Kommando uebernehmen will.
	 * @param side Die Seite
	 * @param user Der Spieler
	 */
	public void setTakeCommand(int side, User user) {
		//pruefen, ob die Seite gueltig ist
		if (commanders.keySet().contains(side))
		{
			this.takeCommand.put(side,user);
		}

	}

	private User[] getTakeCommands() {
		return (User[]) this.takeCommand.entrySet().toArray();
	}

	/**
	 * Gibt eine Liste aller User einer Seite zurueck
	 * @param side die Seite
	 * @return die Userliste bzw null
	 */
	public List<User> getUserPerSide(int side)
	{
		for(BattleSide s: sides)
		{
			if(s.getSide_id() == side)
				return s.getUsers();
		}
		return null;
	}







	/**
	 * Gibt den aktuellen Blockierungs-Zaehler fuer den Rundenwechsel zurueck.
	 * @return Der Zaehler
	 */
	public int getBlockCount()
	{
		return this.blockcount;
	}

	/**
	 * Verringert den Blockierungs-Zaehler fuer den Rundenwechsel um eins.
	 */
	public void decrementBlockCount()
	{
		this.blockcount--;
	}

	/**
	 * Gibt den Zeitpunkt des letzten Rundenwechsels in Unix-Zeit (in Sekunden) zurueck.
	 * @return Der Zeitpunkt
	 */
	public long getLetzteRunde()
	{
		return this.lastturn;
	}

	/**
	 * Gibt den Zeitpunkt der letzten Aktion in Unix-Zeit (in Sekunden) zurueck.
	 * @return Der Zeitpunkt
	 */
	public long getLetzteAktion()
	{
		return this.lastaction;
	}

	/**
	 * Gibt die X-Position der Schlacht zurueck.
	 * @return die X-Position
	 */
	public int getX() {
		return this.x;
	}

	/**
	 * Setzt die X-Position der Schlacht.
	 * @param x die X-Position
	 */
	public void setX(int x) {
		this.x = x;
	}

	/**
	 * Gibt die Y-Position der Schlacht zurueck.
	 * @return Die Y-Position
	 */
	public int getY() {
		return this.y;
	}

	/**
	 * Setzt die Y-Koordinate der Schlacht.
	 * @param y Die Y-Position
	 */
	public void setY(int y) {
		this.y = y;
	}

	/**
	 * Gibt das System zurueck, in dem die Schlacht stattfindet.
	 * @return das System
	 */
	public int getSystem() {
		return this.system;
	}

	/**
	 * Prueft, ob die Schlacht ueber das angegebene Flag verfuegt.
	 * @param flag Das Flag
	 * @return <code>true</code>, falls die Schlacht das Flag besitzt
	 */
	public boolean hasFlag( BattleFlag flag ) {
		return (this.flags & flag.getBit()) != 0;
	}

	/**
	 * Fuegt der Schlacht das angegebene Flag hinzu.
	 * @param flag Das Flag
	 */
	public void setFlag( BattleFlag flag ) {
		setFlag(flag, true);
	}

	/**
	 * Fuegt der Schlacht ein Flag hinzu oder entfernt eines.
	 * @param flag Das Flag
	 * @param status <code>true</code>, falls das Flag hinzugefuegt werden soll. Andernfalls <code>false</code>
	 */
	public void setFlag( BattleFlag flag, boolean status ) {
		if( status ) {
			this.flags |= flag.getBit();
		}
		else if( (this.flags & flag.getBit()) != 0 ) {
			this.flags ^= flag.getBit();
		}
	}



	/**
	 * Gibt das aktuell ausgewaehlte Schiff des Users zurueck.
	 * @param User der User
	 * @return Das aktuell ausgewaehlte eigene Schiff
	 */
	public BattleShip getShip(User user) {
		BattleSide s = getSideForUser(user);
		if(s != null)
		{
			return s.getActiveShipForUser(user);
		}
		return null;
	}

	/**
	 * Gibt einen User der angegebenen Seite zurueck
	 * @param side Die Seite
	 * @return Der erste gefundene User dieser Seite bzw <code>null</code>, sollte es keinen User auf dieser Seite geben
	 */
	public User getUser(int side)
	{
		for(BattleSide s : sides)
		{
			if (s.getSide_id() == side)
			{
				s.getUsers().get(0);
			}
		}
		return null;
	}

	/**
	 * Gibt den leitenden Commander einer Seite zurueck
	 * @param side die Seite
	 * @return Der aktuelle Commander (User) oder <code>null</code>, sollte die Seite nicht existieren
	 */
	public User getCommander(int side)
	{
		for(BattleSide s : sides)
		{
			if (s.getSide_id() == side)
			{
				return s.getCommander();
			}
		}
		return null;
	}

	/**
	 * Prueft, ob das aktuell ausgewaehlte Schiff des Users ein gueltiges Ziel ist
	 * (das Schiff also existiert). Bei gueltigen Zielen ist ein aufruf von {@link #getEnemyShip()}
	 * gefahrlos moeglich.
	 * @param User der ausgewaehlte Gegner
	 * @return <code>true</code>, falls das Ziel gueltig ist
	 */
	public boolean isValidTarget(User user) {
		BattleSide s = getSideForUser(user);
		if(s != null)
		{
			return s.getActiveShipForUser(user) != null;
		}
		return false;
	}

	/**
	 * Liefert das naechste Schiff des Users nach dem aktuell ausgewaehlten.
	 * @param User der User
	 * @return Das naechste passende BattleShip
	 */
	public BattleShip getNextBattleShip(User user) {

		BattleSide s = getSideForUser(user);
		if(s != null)
		{
			List<BattleShip> bs = s.getBattleShipForUser(user);
			BattleShip activeShip = s.getActiveShipForUser(user);
			int index = bs.indexOf(activeShip);
			// alle Schiffe ab dem aktiven durchlaufen
			for(int i = index + 1; i < bs.size(); i++)
			{
				BattleShip aship = bs.get(i);
				if( !aship.getShip().isLanded() &&
						!aship.hasFlag(BattleShipFlag.FLUCHT) &&
						!aship.hasFlag(BattleShipFlag.JOIN) ) {
					return aship;
				}
			}
			// nichts gefunden, dann von vorne bis zum aktiven
			for( int i= 0; i < index; i++){

				BattleShip aship = bs.get(i);
				if( !aship.getShip().isLanded() &&
						!aship.hasFlag(BattleShipFlag.FLUCHT) &&
						!aship.hasFlag(BattleShipFlag.JOIN) ) {
					return aship;
				}
			}
			// immer noch nichts gefunden, dann bleibt es beim aktuellen
			return s.getActiveShipForUser(user);
		}
		return null;
	}


	/**
	 * Liefert den Index des naechsten Zielschiffes eines Users nach dem aktuell ausgewaehlten.
	 * Bevorzugt werden Schiffe gleichen Typs
	 * @param User der User
	 * @return Der Index des naechsten passenden Schiffes
	 */
	public int getNewTargetIndex(User user) {
		// Falls das aktuelle Schiff ungueltig, dann das erstbeste in der Liste zurueckgeben
		BattleSide s = getSideForUser(user);
		if (s == null)
		{
			return -1;
		}

		if( !this.isValidTarget(user) ) {
			for( int i=0; i < s.getBattleShipForUser(user).size(); i++ ) {
				BattleShip aship = s.getBattleShipForUser(user).get(i);

				if( !aship.getShip().isLanded() &&
						!aship.hasFlag(BattleShipFlag.DESTROYED) && !aship.hasFlag(BattleShipFlag.SECONDROW) ) {
					return i;
				}
			}

			return -1;
		}

		boolean foundOld = false;

		BattleShip enemyShip = s.getBattleShipForUser(user).get(s.getBattleShipForUser(user).indexOf(s.getActiveShipForUser(user)));

		// Schiff gleichen Typs hinter dem aktuellen Schiff suchen
		List<BattleShip> enemyShips = getShips(user);
		for( int i=0; i < enemyShips.size(); i++ ) {
			BattleShip aship = enemyShips.get(i);
			if( !foundOld && (aship.getId() == enemyShip.getId()) ) {
				foundOld = true;
			}
			else if( foundOld && (aship.getShip().getType() == enemyShip.getShip().getType()) &&
					!aship.getShip().isLanded() &&
					!aship.hasFlag(BattleShipFlag.DESTROYED) && !aship.hasFlag(BattleShipFlag.SECONDROW) ) {
				return i;
			}
		}

		// Schiff gleichen Typs vor dem aktuellen Schiff suchen
		for( int i=0; i < enemyShips.size(); i++ ) {
			BattleShip aship = enemyShips.get(i);
			if( aship.getId() == enemyShip.getId() ) {
				break;
			}
			if( (aship.getShip().getType() == enemyShip.getShip().getType()) &&
					!aship.getShip().isLanded() &&
					!aship.hasFlag(BattleShipFlag.DESTROYED) && !aship.hasFlag(BattleShipFlag.SECONDROW) ) {
				return i;
			}
		}
		return -1;
	}


	/**
	 * Laesst eines oder mehrere Schiffe (in einer Flotte) der Schlacht beitreten.
	 * @param id Die ID des Besitzers der Schiffe
	 * @param shipid Die ID eines der Schiffe, welche beitreten sollen
	 *
	 * @return <code>true</code>, falls der Beitritt erfolgreich war
	 */
	public boolean addShip( int id, int shipid )
	{
		Context context = ContextMap.getContext();
		org.hibernate.Session db = context.getDB();

		db.flush();
		User userobj = (User)context.getDB().get(User.class, id);

		Ship shipd = (Ship)db.get(Ship.class, shipid);

		if( (shipd == null) || (shipd.getId() < 0) )
		{
			context.addError("Das angegebene Schiff existiert nicht!");
			return false;
		}
		if( shipd.getOwner().getId() != id )
		{
			context.addError("Das angegebene Schiff geh&ouml;rt nicht ihnen!");
			return false;
		}
		if( !new Location(this.system,this.x,this.y).sameSector(0, shipd.getLocation(), 0) )
		{
			context.addError("Das angegebene Schiff befindet sich nicht im selben Sektor wie die Schlacht!");
			return false;
		}
		if( shipd.getBattle() != null )
		{
			context.addError("Das angegebene Schiff befindet sich bereits in einer Schlacht!");
			return false;
		}


		if( userobj.isNoob() )
		{
			context.addError("Sie stehen unter GCP-Schutz und k&ouml;nnen daher keine Schiffe in diese Schlacht schicken!<br />Hinweis: der GCP-Schutz kann unter Optionen vorzeitig beendet werden");
			return false;
		}

		ShipTypeData shiptype = shipd.getTypeData();
		if( (shiptype.getShipClass() == ShipClasses.GESCHUETZ ) )
		{
			context.addError("<span style=\"color:red\">Gesch&uuml;tze k&ouml;nnen einer Schlacht nicht beitreten!<br />Diese m&uuml;ssen von Frachtern mitgenommen werden!</span>");
			return false;
		}

		//ok, der User darf mit dem Schiff also joinen. Dann schauen wir doch mal, ob er oder seine Ally bereits eine Seite in der Schlacht hat, sonst machen wir eine neue auf
		BattleSide battleside = getSideOrCreateNewSide(userobj);

		Map<Integer,Integer> shipcounts = new HashMap<>();

		//Userrelations werden beim join nicht neu gesetzt, erst, wenn man auf eine Seite feuert

		List<Integer> shiplist = new ArrayList<>();

		List<Ship> sid;
		// Handelt es sich um eine Flotte?
		if( shipd.getFleet() != null ) {
			sid = Common.cast(db.createQuery("from Ship as s where s.id>0 and s.fleet=:fleet and s.battle is null and s.x=:x and s.y=:y and s.system=:sys")
                    .setEntity("fleet", shipd.getFleet())
                    .setInteger("x", shipd.getX())
                    .setInteger("y", shipd.getY())
                    .setInteger("sys", shipd.getSystem())
                    .list());
		}
		else
        {
			sid = Common.cast(db.createQuery("from Ship as s where s.id>0 and s.id=:id and s.battle is null and s.x=:x and s.y=:y and s.system=:sys")
                    .setInteger("id", shipd.getId())
                    .setInteger("x", shipd.getX())
                    .setInteger("y", shipd.getY())
                    .setInteger("sys", shipd.getSystem())
                    .list());
		}

		//gehen wir mal die joinenden Schiffe durch
		for(Ship aship : sid)
		{
				if (db.get(BattleShip.class, aship.getId()) != null) {
						continue;
				}

				shiptype = aship.getTypeData();
				if (shiptype.getShipClass() == ShipClasses.GESCHUETZ) {
						continue;
				}
				//gedockte/gelandete Schiffe werden ueber ihr Baseship gejoint, damit es nicht zu Fehlern mit Flotten kommt
				if(aship.isDocked() ||aship.isLanded()){
					continue;
				}

				shiplist.add(aship.getId());
				BattleShip aBattleShip = new BattleShip(this, aship);


				if (!shiptype.hasFlag(ShipTypeFlag.INSTANT_BATTLE_ENTER)) {
						aBattleShip.addFlag(BattleShipFlag.JOIN);
				}
				// Das neue Schiff in die Liste der eigenen Schiffe eintragen
				aBattleShip.setSide(battleside.getSide_id());
				battleside.addShip(aBattleShip);

				Common.safeIntInc(shipcounts, aship.getType());

				db.persist(aBattleShip);

				aship.setBattle(this);

				//alle gedockten / gelandeten Schiffe mit in die Schlacht ziehen
				for(Ship lShip : aship.getGedockteUndGelandeteSchiffe()){

					//falls es schon im Kampf sein sollte. Kann eigentlich nicht, aber sicher ist sicher
					if (db.get(BattleShip.class, lShip.getId()) != null) {
						continue;
					}
					//ok, noch nicht in der Schlacht, dann legen wir los

					BattleShip sid2bs = new BattleShip(this, lShip);
					ShipTypeData stype = lShip.getTypeData();

					//Gechuetze blockieren
					if (stype.getShipClass() == ShipClasses.GESCHUETZ) {
							sid2bs.addFlag(BattleShipFlag.BLOCK_WEAPONS);
					}

					shiplist.add(lShip.getId());
					// Das neue Schiff in die Liste der eigenen Schiffe eintragen
					if (!shiptype.hasFlag(ShipTypeFlag.INSTANT_BATTLE_ENTER) &&
								!stype.hasFlag(ShipTypeFlag.INSTANT_BATTLE_ENTER)) {
						sid2bs.addFlag(BattleShipFlag.JOIN);
					}

					sid2bs.setSide(aBattleShip.getSide());

					battleside.addShip(sid2bs);

					Common.safeIntInc(shipcounts, lShip.getType());

					db.persist(sid2bs);

					lShip.setBattle(this);

				}
		}

		if( shiplist.size() > 1 )
		{
			int addedShips = shiplist.size()-1;
			this.log(new SchlachtLogAktion(battleside.getSide_id(), "Die " + log_shiplink(shipd) + " ist zusammen mit " + addedShips + " weiteren Schiffen der Schlacht beigetreten"));
			this.logme( "Die "+log_shiplink(shipd)+" ist zusammen mit "+addedShips+" weiteren Schiffen der Schlacht beigetreten\n\n" );
		}
		else
		{
			this.log(new SchlachtLogAktion(battleside.getSide_id(), "Die " + log_shiplink(shipd) + " ist der Schlacht beigetreten"));
			this.logme("Die "+log_shiplink(shipd)+" ist der Schlacht beigetreten\n\n");

			shipd.setBattle(this);
		}

		return true;
	}

	/**
	 * Gibt die Seite eines Users zurueck oder legt eine neue Seite an, falls der User in keiner Seite ist
	 * @param User der User
	 * @return die ID der Seite, auf der der User kaempft
	 */
	public BattleSide getSideOrCreateNewSide(User user)
	{
		int side = -1;
		List<User> allyMembers = new ArrayList<>();
		if(user.getAlly() != null)
		{
			//Wir haben eine Ally, also fuegen wir alle AllyMember in die Userliste ein
			allyMembers = user.getAlly().getMembers();
		}
		else
		{
			//keine Ally, dann bleibt er allein in dieser Liste
			allyMembers.add(user);
		}
		for(BattleSide s : sides)
		{
			//mittels disjoint wird geprueft, ob es mindestens einen User gibt, der in beiden Listen ist
			if(!Collections.disjoint(s.getUsers(), allyMembers))
			{
				return s;
			}
		}

		//dann suchen wir mal die groesste Seitennummer raus
		int max = -1;
		for(BattleSide s : sides)
		{
			if (max < s.getSide_id())
			{
				max = s.getSide_id();
			}
		}
		//und machen eine neue Seite auf
		side = max +1;
		//dann legen wir mal die neue BattleSide an
		BattleSide neu = new BattleSide(side, user, this);
		sides.add(neu);

		return neu;
	}

	/**
	 * Laedt weitere Schlachtdaten aus der Datenbank.
	 * @param user Der aktive Spieler
	 * @param enemy Der aktive gegnerische Spieler
	 * @param ownShip Das auszuwaehlende eigene Schiff (oder <code>null</code>)
	 * @param enemyShip Das auszuwaehlende gegnerische Schiff (oder <code>null</code>)
	 * @param forcejoin Die ID einer Seite, welche als die eigene zu waehlen ist. Falls 0 wird automatisch eine gewaehlt
	 *
	 * @return <code>true</code>, falls die Schlacht erfolgreich geladen wurde
	 */
	public boolean load(User user, User enemy, Ship ownShip, Ship enemyShip, int forcejoin ) {
		Context context = ContextMap.getContext();
		org.hibernate.Session db = context.getDB();

		/*
			TODO:
				- Update der Allys falls der Commander einer Seite einer Ally beitritt (und vorher in keiner war)
				- Update der Schiffe, falls ein Spieler nicht mehr einer der beiden Seiten angehoert (und ggf auch update der Kommandanten)
		*/

		BattleSide forceSide;
		forceSide = this.getSideForUser(user);
		if( forcejoin == 0 ) {
			if( forceSide == null )
			{
						context.addError("Sie verf&uuml;gen &uuml;ber kein geeignetes Schiff im Sektor um die Schlacht zu verfolgen");
						return false;
			}
		}
		//ungueltiger Gegner
		BattleSide enemySide = null;
		for(BattleSide s : sides)
		{
			if(s.getUsers().contains(enemy))
			{
				enemySide = s;
			}
		}
		// Gegner nicht gefunden oder Gegner auf eigener Schlachtseite
		if (enemySide == null || enemySide.getUsers().contains(user))
		{
			return false;
		}

		//
		// Liste aller Schiffe in der Schlacht erstellen
		//

		List<BattleShip> ships = Common.cast(db.createQuery("from BattleShip bs inner join fetch bs.ship as s " +
                "where s.id>0 and bs.battle=:battle " +
                "order by s.shiptype.id, s.id")
                .setEntity("battle", this)
                .list());

								//ToDo... hier muss noch etwas Hirnschmalz reinfliessen
		BattleSide side = null;
		List<BattleShip> schiffe = new ArrayList<>();
		for (BattleShip ship : ships) {
			if (forceSide == null)
			{
				side = getSideOrCreateNewSide(user);
			}

			if(forceSide.getSide_id() == side.getSide_id())
			{
				schiffe.add(ship);
				if (!ship.getShip().isLanded()) {
					if (shipTypeCount.get(user).containsKey(ship.getShip().getType())) {
						Map<Integer,Integer> stc = getShipTypeCount(user);
						stc.put(ship.getShip().getType(), stc.get(ship.getShip().getType()) + 1);
						shipTypeCount.put(user,stc);
					}
					else
					{
						Map<Integer,Integer> stc = getShipTypeCount(user);
						stc.put(ship.getShip().getType(), 1);
						shipTypeCount.put(user,stc);
					}
				}
			}
		}

		if(this.ships.get(user).isEmpty() || this.ships.get(enemy).isEmpty())
		{
			return false;
		}

		//
		// aktive Schiffe heraussuchen
		//

		this.activeShip = null;

		setFiringShip(user,ownShip);
		setAttackedShip(enemy,enemyShip);

		// Falls die gewaehlten Schiffe gelandet (oder zerstoert) sind -> neue Schiffe suchen
		while( activeShip.get(enemy) < this.ships.get(enemy).size() &&
			  ( this.ships.get(enemy).get(activeShip.get(enemy)).hasFlag(BattleShipFlag.DESTROYED) ||
			 	this.ships.get(enemy).get(activeShip.get(enemy)).getShip().isLanded() ) ) {
			this.activeShip.put(enemy,this.activeShip.get(enemy));
		}

		if( activeShip.get(enemy) >= this.ships.get(enemy).size() )
		{
			activeShip.put(enemy,0);
		}

		return true;
	}


    public void setFiringShip(User user, Ship ownShip)
    {
			if(ownShip != null)
			{
				if(user.getId() != ownShip.getOwner().getId())
				{
					return;
				}
				for(BattleSide s : sides)
				{
					if(s.getUsers().contains(user))
					{
						BattleShip ship = new BattleShip(this, ownShip);
						s.setActiveShipForUser(user, ship);
					}
				}
			}
    }

	public void setFiringShip(Ship ownShip)
	{
		if(ownShip != null)
		{
			User user = ownShip.getOwner();
			for(BattleSide s : sides)
			{
				if(s.getUsers().contains(user))
				{
					BattleShip ship = new BattleShip(this, ownShip);
					s.setActiveShipForUser(user, ship);
				}
			}
		}
	}

    public void setAttackedShip(User user, Ship enemyShip)
    {
			if(enemyShip != null)
			{
				if(user.getId() != enemyShip.getOwner().getId())
				{
					return;
				}
				for(BattleSide s : sides)
				{
					if(s.getUsers().contains(user))
					{
						BattleShip ship = new BattleShip(this, enemyShip);
						s.setActiveShipForUser(user, ship);
					}
				}
			}
    }

	/**
	 * Loggt eine Nachricht fuer aktuellen Spieler.
	 * @param text Die zu loggende Nachricht
	 */
	public void logme( String text ) {
		this.logoutputbuffer.append(text);
	}

	/**
	 * Gibt die fuer den akuellen Spieler anzuzeigenden Nachrichten zurueck.
	 * @param raw Sollen die Nachrichten im Rohformat (unformatiert) zurueckgegeben werden?
	 * @return die Nachrichten
	 */
	public String getOwnLog( boolean raw ) {
		if( raw ) {
			return this.logoutputbuffer.toString();
		}
		return this.logoutputbuffer.toString().replace("\n", "<br />");
	}

	/**
	 * Fuegt einen Eintrag zum Schlachtlog hinzu.
	 * @param eintrag Der Eintrag
	 */
	public void log(SchlachtLogEintrag eintrag)
	{
		Context context = ContextMap.getContext();
		Session db = context.getDB();
		int tick = context.get(ContextCommon.class).getTick();
		eintrag.setTick(tick);

		if( this.schlachtLog == null ) {
			this.schlachtLog = new SchlachtLog(this, tick);
			db.persist(this.schlachtLog);
		}
		this.schlachtLog.add(eintrag);
		db.persist(eintrag);
	}

	/**
	 * Beendet die laufende Runde und berechnet einen Rundenwechsel.
	 * @param calledByUser Wurde das Rundenende (in)direkt durch einen Spieler ausgeloesst? (<code>true</code>)
	 *
	 * @return <code>true</code>, falls die Schlacht weiterhin existiert. <code>false</code>, falls sie beendet wurde.
	 *
	 */
	public boolean endTurn( boolean calledByUser )
	{
		Context context = ContextMap.getContext();
		org.hibernate.Session db = context.getDB();


		//
		// Zuerst die Schiffe berechnen
		//
		for(BattleSide s : sides)
		{
			for( User u: s.getUsers() )
			{
				List<BattleShip> shipsSecond = new ArrayList<>();

				// Liste kopieren um Probleme beim Entfernen von Schiffen aus der Ursprungsliste zu vermeiden
				List<BattleShip> shiplist = new ArrayList<>(s.getBattleShipForUser(u));
				for (BattleShip ship : shiplist)
				{
						if (ship.hasFlag(BattleShipFlag.HIT))
						{
								ship.getShip().setAblativeArmor(ship.getAblativeArmor());
								ship.getShip().setHull(ship.getHull());
								ship.getShip().setShields(ship.getShields());
								ship.getShip().setEngine(ship.getEngine());
								ship.getShip().setWeapons(ship.getWeapons());
								ship.getShip().setComm(ship.getComm());
								ship.getShip().setSensors(ship.getSensors());
								ship.removeFlag(BattleShipFlag.HIT);
						}
						else if (ship.hasFlag(BattleShipFlag.DESTROYED))
						{
								if ( new ConfigService().getValue(WellKnownConfigValue.DESTROYABLE_SHIPS) )
								{
										//
										// Verluste verbuchen (zerstoerte/verlorene Schiffe)
										//
										User destroyer = (User) db.get(User.class, ship.getDestroyer());
										Ally destroyerAlly = destroyer.getAlly();
										if (destroyerAlly != null) {
												destroyerAlly.setDestroyedShips(destroyerAlly.getDestroyedShips() + 1);
										}
										destroyer.setDestroyedShips(destroyer.getDestroyedShips() + 1);

										Ally looserAlly = ship.getOwner().getAlly();
										if (looserAlly != null) {
												looserAlly.setLostShips(looserAlly.getLostShips() + 1);
										}
										u.setLostShips(u.getLostShips() + 1);

										ShipLost lost = new ShipLost(ship.getShip());
										lost.setDestAlly(destroyerAlly);
										lost.setDestOwner(destroyer);
										db.save(lost);

										destroyShip(ship);
										continue;
								}
								else
								{
										ship.removeFlag(BattleShipFlag.DESTROYED);
										continue; //Das Schiff kann nicht zerstoert werden
								}
						}


						if ( ship.hasFlag(BattleShipFlag.FLUCHT)) {
								ShipTypeData ashipType = ship.getTypeData();
								if (ashipType.getCost() > 0) {
										removeShip(ship, true);
								}
						}

						ship.removeFlag(BattleShipFlag.SHOT);
						ship.removeFlag(BattleShipFlag.SECONDROW_BLOCKED);

						if (ship.hasFlag(BattleShipFlag.BLOCK_WEAPONS)) {
								if (!((ship.getTypeData().getShipClass() == ShipClasses.GESCHUETZ) && ship.getShip().isDocked())) {
										ship.removeFlag(BattleShipFlag.BLOCK_WEAPONS);
								}
						}

						if (s.hasFlag(BattleFlag.DROP_SECONDROW)) {
								ship.removeFlag(BattleShipFlag.SECONDROW);
						}


						if (ship.hasFlag(BattleShipFlag.JOIN)) {
								ShipTypeData ashipType = ship.getTypeData();
								if (ashipType.hasFlag(ShipTypeFlag.SECONDROW) && ship.getShip().getEinstellungen().gotoSecondrow()) {
									shipsSecond.add(ship);
								}
								ship.removeFlag(BattleShipFlag.JOIN);
						}

						Map<String, Integer> heat = ship.getWeaponHeat();

						heat.replaceAll((n, v) -> 0);

						if (ship.hasFlag(BattleShipFlag.FLUCHTNEXT)) {
							ship.removeFlag(BattleShipFlag.FLUCHTNEXT);
							ship.addFlag(BattleShipFlag.FLUCHT);
						}

						ship.getShip().setWeaponHeat(heat);
						ship.getShip().setBattleAction(false);
				}

				for(BattleShip second : shipsSecond){
					if(s.isSecondRowStable(second)){
						second.addFlag(BattleShipFlag.SECONDROW);
					}
				}
			}
		}

		context.getDB().flush();

		//ToDo: Schlachtendebedigungen neu schreiben

		//pruefen wir zuerst mal den Trivialfall: existieren ueberhaupt noch Schiffe?
		int count = 0;
		User niemand = (User)db.get(User.class, -2);
		for(BattleSide s : sides)
		{
			count +=s.getShipCount();
		}
		//ok, alle Schiffe zerstoert, dann ist es ein unendschieden
		if(count == 0)
		{

			Map<User,Integer> winner_map = new HashMap<>();
			for(BattleSide s : sides)
			{
				for(User u : s.getUsers())
				{
					PM.send(niemand, u.getId(), "Schlacht unentschieden", "Die Schlacht bei "+this.getLocation().displayCoordinates(false)+" wurde mit einem Unentschieden beendet!");
					winner_map.put(u,0);
				}
			}
			this.endBattle(winner_map);
			sides.clear();
			if( calledByUser ) {
				try
				{
					context.getResponse().getWriter().append("Du hast die Schlacht bei <a class='forschinfo' href='./client#/map/")
						.append(this.getLocation().urlFragment()).append("'>")
						.append(this.getLocation().displayCoordinates(false));
					context.getResponse().getWriter().append(" mit einem Unentschieden beendet!");
				}
				catch( IOException e )
				{
					throw new RuntimeException(e);
				}
			}
			return false;
		}
		//Trivialfall durch... dann gehen wir jetzt mal die nicht so trivialen Faelle durch

		//erstens: Seiten ohne Schiffe haben dann wohl verloren
		for(BattleSide s : sides)
		{
			if(s.getShipCount() == 0)
			{
				List<Ally> ally_ready = new ArrayList<>();
				List<User> users = s.getUsers();
				for(User user : users)
				{
					PM.send(niemand, user.getId(), "Schlacht verloren", "Du hast die Schlacht bei "+this.getLocation().displayCoordinates(false)+" verloren!");
					if( user.getAlly() != null )
					{
						Ally ally = user.getAlly();
						if(!ally_ready.contains(ally))
						{
							ally.setLostBattles((short)(ally.getLostBattles()+1));
							ally_ready.add(ally);
						}
					}
					user.setLostBattles((short)( user.getLostBattles()+1));
					this.remove(user);
				}

				this.log(new SchlachtLogAktion(s.getSide_id(), "[Automatisch] Die Schlachtpartei "+s.getSide_id()+" wurde besiegt."));
			}
		}
		//besiegte Schlachtseiten entfernen
		sides.removeIf(o->o.getShipCount() == 0);
		//Seiten ohne Schiffe sind jetzt nicht mehr in sides, werden also auch nicht mehr in feindbeziehungen aufgefuehrt
		//Dann laden wir mal die Relations der Seiten
		Map<BattleSide,List<BattleSide>> feindbeziehungen = this.getEnemySides();
		boolean enemy_on_sight = false;
		for(Map.Entry<BattleSide,List<BattleSide>> seite : feindbeziehungen.entrySet())
		{
			if(seite.getValue() != null && !seite.getValue().isEmpty())
			{
				if(seite.getKey().getShipCount() > 0 && seite.getValue().get(0).getShipCount() > 0)
				//wir haben also noch 2 verfeindete Seiten mit Schiffen gefunden, dann ist die Schlacht noch nicht vorbei
				{
					enemy_on_sight = true;
					break;
				}
			}
		}
		if(!enemy_on_sight)
		//wir haben wohl keine Feinde mehr... dann erklaeren wir alle verbliebenen User zu gewinnern
		{
			Map<User,Integer> winner_map =new HashMap<>();
			for(BattleSide s : sides)
			{
				for(User u: s.getUsers())
				{
					winner_map.put(u,1);
				}
			}
			this.endBattle(winner_map);
			sides.clear();
			if( calledByUser ) {
				try
				{
					context.getResponse().getWriter().append("Du hast die Schlacht bei <a class='forschinfo' href='./client#/map/")
						.append(this.getLocation().urlFragment()).append("'>")
						.append(this.getLocation().displayCoordinates(false));
					if(winner_map.containsKey((User) context.getActiveUser()))
						context.getResponse().getWriter().append(" gewonnen!");
					else
						context.getResponse().getWriter().append(" verloren!");
				}
				catch( IOException e )
				{
					throw new RuntimeException(e);
				}
			}
			return false;
		}

		for(BattleSide s : sides)
		{
			s.setReady(false);
			s.setFlag(BattleFlag.BLOCK_SECONDROW, false);

			if(s.hasFlag(BattleFlag.DROP_SECONDROW))
			{
				s.setFlag(BattleFlag.DROP_SECONDROW, false);
				s.setFlag(BattleFlag.BLOCK_SECONDROW, true);
			}
		}

		this.blockcount = 2;

		this.lastturn = Common.time();

		if( this.hasFlag(BattleFlag.FIRSTROUND) ) {
			this.setFlag(BattleFlag.FIRSTROUND, false);
		}
		if(!calledByUser)
		{
			for(Map.Entry<Integer,User> tc : this.takeCommand.entrySet())
			{
				int seite = tc.getKey();
				User com = tc.getValue();

				PM.send(com, this.getCommander(seite).getId(), "Schlacht &uuml;bernommen", "Ich habe die Leitung der Schlacht bei "+this.getLocation().displayCoordinates(false)+" &uuml;bernommen.");

				this.log(new SchlachtLogAktion(seite, "[Automatisch] "+Common._titleNoFormat(com.getName())+" kommandiert nun die Truppen"));

				this.setCommander(seite, com);

				this.log(new SchlachtLogKommandantWechselt(seite, com));
				this.takeCommand.remove(seite);
			}
		}

		return true;
	}

	@Transient
	private boolean deleted = false;

	/**
	 * Entfernt einen User und alle seine Eintraege in der Schlacht aus der Schlacht
	 * @param User der zu entfernende User
	 */
	public void remove(User user)
	{

		if(users.containsKey(user))
		{
			this.users.remove(user);
			this.activeShip.remove(user);
			this.shipGroup.remove(user);
			this.shipTypeCount.remove(user);
		}
	}

	/**
	 * Gibt die Seiten einer Schlacht als Liste zurueck
	 * @return die Liste der Seiten
	 */
	public List<Integer> getSides()
	{
		List<Integer> sides = new ArrayList<>();
		for(Map.Entry<User,Integer> user : users.entrySet())
		{
			if(!sides.contains(user.getValue()))
			{
				sides.add(user.getValue());
			}
		}
		return sides;
	}

	/**
	 * Gibt eine Map zurueck, in der fuer jede Seite die gegnerischen Seiten aufgelistet sind
	 * @return die Relationsmap
	 */
	public Map<Integer,List<Integer>> getEnemySides()
	{
		Map<Integer,List<Integer>> result = new HashMap<>();
		for(Map.Entry<User,Integer> entry : users.entrySet())
		{
			List<Integer> enemyList = new ArrayList<>();

			User user = entry.getKey();
			int side = entry.getValue();
			if(result.containsKey(side))
			{
				enemyList = result.get(side);
			}
			for(Map.Entry<User,Integer> enemy : users.entrySet())
			{

				if(enemy.getValue() == side )
				//eigene Seite, also kein Gegner
				{
					continue;
				}

				if(enemyList.contains(enemy.getValue()))
				//die Seite ist bereits als Gegner erfasst
				{
					continue;
				}

				if(user.getRelation(enemy.getKey()) == Relation.ENEMY)
				//OK, wir haben also eine feindliche Beziehung gefunden. Dann fuegen wir den doch mal als Gegner ein
				{
					enemyList.add(enemy.getValue());
				}

			}
			//und nun noch die Gegnerliste der Seite speichern
			result.put(side,enemyList);
		}
		return result;
	}

	/**
	 * Beendet die Schlacht.
	 * @param winner_map eine Map<User,Integer> der Gewinner und Verlierer. Value 1 heisst gewonnen, Value -1 heisst verloren. Value 0 heisst unentschieden
	 */
	public void endBattle(Map<User,Integer> winner_map) {
		Context context = ContextMap.getContext();
		org.hibernate.Session db = context.getDB();

		if( deleted ) {
			log.warn("Mehrfacher Aufruf von Battle.endBattle festgestellt", new Throwable());
			return;
		}

		deleted = true;

		db.createQuery("delete from BattleShip where battle=:battle")
			.setEntity("battle", this)
			.executeUpdate();
		db.createQuery("update Ship set battle=null,battleAction=0 where id>0 and battle=:battle")
			.setEntity("battle", this)
			.executeUpdate();

		List<Ally> ally_ready = new ArrayList<>();
		for( Map.Entry<User,Integer> entry : winner_map.entrySet() ) {
			if( entry.getKey().getAlly() != null ) {
				Ally ally = entry.getKey().getAlly();
				if(!ally_ready.contains(ally))
				{
					if( entry.getValue() > 0 ) {
						ally.setWonBattles((short)(ally.getWonBattles()+1));
						ally_ready.add(ally);
					}
					else if( entry.getValue() < 0 )
					{
						ally.setLostBattles((short)(ally.getLostBattles()+1));
						ally_ready.add(ally);
					}
				}
			}
			if( entry.getValue() > 0 ) {
				 entry.getKey().setWonBattles((short)( entry.getKey().getWonBattles()+1));
			}
			else if( entry.getValue() < 0 )
			{
				 entry.getKey().setLostBattles((short)( entry.getKey().getLostBattles()+1));
			}
		}

		this.ships.clear();

		db.delete(this);
	}

	/**
	 * Prueft, ob ein Spieler Mitglied der Schlacht ist.
	 * @param user Der Spieler
	 * @return <code>true</code>, falls er Mitglied ist
	 */
	public boolean isMitglied( User user ) {
		return users.containsKey(user);
	}

	/**
	 * Prueft, ob ein Spieler auf einer Seite Mitglied ist.
	 * @param user Der Spieler
	 * @param side Die Seite oder <code>-1</code>, falls die Seite egal ist
	 * @return <code>true</code>, falls er Mitglied ist
	 */
	public boolean isMitglied( User user, int side ) {
		if(!users.containsKey(user))
		{
			return false;
		}
		return ( users.get(user) == side);
	}

	/**
	 * Gibt die ID der aktuell ausgewaehlten Schiffsgruppe des Users zurueck.
	 * @param User der User
	 * @return Die ID der aktuellen gegnerischen Schiffsgruppe
	 */
	public String getShipGroup(User user) {
		if(users.containsKey(user))
			return shipGroup.get(user);
		else
			return "";
	}

	/**
	 * Setzt die ID der aktuell ausgewaehlten Schiffsgruppe des Users.
	 * @param User der User
	 * @param enemyShipGroup Die neue Schiffsgruppen-ID
	 */
	public void setShipGroup(User user, String enemyShipGroup) {
		if(users.containsKey(user))
			this.shipGroup.put(user, enemyShipGroup);
	}

	/**
	 * Gibt die ID der Seite des Users zurueck.
	 * @param User der User
	 * @return Die ID der gegnerischen Seite
	 */
	public int getSide(User user) {
		if(!users.containsKey(user))
			return -1;
		return users.get(user);
	}

	/**
	 * Gibt die Liste der Schiffe des Users zurueck.
	 * @param User der User
	 * @return Die Liste der gegnerischen Schiffe
	 */
	public List<BattleShip> getShips(User user) {
		if(!users.containsKey(user))
			return null;
		else
			return ships.get(user);
	}

	/**
	 * Gibt die Liste der Schiffe einer Seite zurueck.
	 * @param side die Seite
	 * @return Die Liste der gegnerischen Schiffe
	 */
	public List<BattleShip> getShips(int side) {
		List<BattleShip> ships = new ArrayList<>();
		for(User u : getUserPerSide(side))
		{
			ships.addAll(getShips(u));
		}
		if(ships.size() == 0)
			return null;
		else
			return ships;
	}

	/**
	 * Gibt das Schiff des Users mit dem angegebenen Index (nicht ID!) zurueck.
	 * @param index Der Index
	 * @return Das Schiff
	 */
	public BattleShip getShip(User user, int index ) {
		if(!users.containsKey(user))
			return null;
		else
			return ships.get(user).get(index);
	}


	/**
	 * Gibt das eigene Schiff mit der angegebenen ID oder null zurueck, falls nicht gefunden.
	 * @param shipID Die Schiffs-ID
	 * @return Das Schiff
	 */
	public BattleShip getShipByID( int shipID ) {
		if(shipID <= 0) {
			return null;
		}
		BattleShip ship = null;

		for (User u : users.keySet())
		{
			ship = ships.get(u).stream()
										.filter(ownShip -> ownShip.getId() == shipID)
										.findAny()
										.orElse(null);
			if ( ship != null)
			{
				return 	ship;
			}
		}
		return null;
	}


	/**
	 * Prueft, ob eine Seite mit ihren Aktionen in der Runde fertig ist.
	 * @param side Die Seite
	 * @return <code>true</code>, falls sie mit ihren Aktionen in der Runde fertig ist
	 */
	public boolean isReady(int side) {
		if(ready.containsKey(side))
			return this.ready.get(side);
		else
			return false;
	}

	/**
	 * Setzt den "fertig"-Status einer Seite fuer die aktuelle Runde.
	 * @param side Die Seite
	 * @param ready Der Status
	 */
	public void setReady(int side, boolean ready) {
		if(this.ready.containsKey(side))
		{
			this.ready.put(side,ready);
		}
	}

	/**
	 * Setzt die Commander-Seite-Map der Schlacht
	 * @param die Map
	 */
	public void setusers(Map<User,Integer> users)
	{
		this.users = users;
	}

	/**
	 * setzt die User-Schiffsliste-Map
	 * @param ships die Map
	 */
	public void setShips(Map<User,List<BattleShip>> ships)
	{
		this.ships = ships;
	}

	/**
	 * Gibt eine Map fuer eine Seite zurueck, welche als Schluessel Schiffstypen und als zugeordnete
	 * Werte die Anzahl der vorkommenden Schiffes des Typs auf der Seite hat.
	 * @param side Die Seite
	 * @return Eine Map, welche angibt, wie haeufig ein Schiffstyp auf einer Seite vorkommt
	 */
	public Map<Integer,Integer> getShipTypeCount( User user ) {
		Map<Integer,Integer> shipTypeCount = new HashMap<>();
		for(int shipTypeId : this.shipTypeCount.get(user).keySet())
		{
			int count = shipTypeCount.containsKey(shipTypeId) ? shipTypeCount.get(shipTypeId) : 0;
			shipTypeCount.put(shipTypeId, count + this.shipTypeCount.get(user).get(shipTypeId));
		}
		return shipTypeCount;
	}

	/**
	 * Gibt eine Map fuer eine Seite zurueck, welche als Schluessel Schiffstypen und als zugeordnete
	 * Werte die Anzahl der vorkommenden Schiffes des Typs auf der Seite hat.
	 * @param side Die Seite
	 * @return Eine Map, welche angibt, wie haeufig ein Schiffstyp auf einer Seite vorkommt
	 */
	public Map<Integer,Integer> getShipTypeCount( int side ) {
		Map<Integer,Integer> shipTypeCount = new HashMap<>();
		for(User u: getUserPerSide(side))
		{
			for(int shipTypeId : this.shipTypeCount.get(u).keySet())
			{
				int count = shipTypeCount.containsKey(shipTypeId) ? shipTypeCount.get(shipTypeId) : 0;
				shipTypeCount.put(shipTypeId, count + this.shipTypeCount.get(u).get(shipTypeId));
			}
		}
		return shipTypeCount;
	}

	/**
	 * Gibt zurueck, wie oft ein Schiffstyp auf einer Seite vorkommt.
	 * @param side Die ID der Seite
	 * @param shiptype Die ID des Schifftyps
	 * @return Die Anzahl der vorkommenden Schiffe des Schifftyps
	 */
	public int getShipTypeCount( int side, int shiptype ) {
		Map<Integer,Integer> shipTypeCount = new HashMap<>();
		shipTypeCount = getShipTypeCount(side);
		if(shipTypeCount == null)
			return 0;
		Integer count = shipTypeCount.get(shiptype);
		if( count == null ) {
			return 0;
		}
		return count;
	}

	@Override
	public Location getLocation() {
		return new Location(this.system, this.x, this.y);
	}

	/**
	 * Ermittelt den Kampfstärke einer Seite.
	 *
	 * @param side Die Seite, deren Kampfstärke abgefragt werden soll
	 * @return Die Kampfstärke einer Seite
	 */
	public int getBattleValue(int side)
	{
		int battleValue = 0;
		List<BattleShip> ships = getShips(side);

		for(BattleShip ship: ships)
		{
			battleValue += ship.getBattleValue();
		}

		return battleValue;
	}

	/**
	 * Zerstoert ein Schiff und alle an ihm gedockten Schiff.
	 * @param ship Das zu zerstoerende Schiff
	 */
	private void destroyShip( BattleShip ship ) {
		Context context = ContextMap.getContext();
		org.hibernate.Session db = context.getDB();

		if( !ship.getShip().isDestroyed() )
		{
			db.delete(ship);
			ship.getShip().destroy();
		}

		User user = ship.getOwner();

		List<BattleShip> shiplist = ships.get(user);

		for( int i=0; i < shiplist.size(); i++ ) {
			BattleShip aship = shiplist.get(i);

			if( aship == ship || aship.getBaseShip() == ship ) {
				shiplist.remove(i);
			}
		}

		if( this.activeShip.get(user) >= shiplist.size())  {
			this.activeShip.put(user,0);
		}
	}

	/**
	 * Entfernt ein Schiff aus einer Schlacht und platziert es falls gewuenscht in einem zufaelligen Sektor
	 * um die Schlacht herum. Evt gedockte Schiffe werden mitentfernt und im selben Sektor platziert.
	 * @param ship Das fliehende Schiff
	 * @param relocate Soll ein zufaelliger Sektor um die Schlacht herum gewaehlt werden? (<code>true</code>)
	 */
	public void removeShip( BattleShip ship, boolean relocate ) {
		Context context = ContextMap.getContext();
		org.hibernate.Session db = context.getDB();

		User user = ship.getOwner();
		Location loc = ship.getShip().getLocation();

		if(relocate && !ship.getShip().isLanded() && !ship.getShip().isDocked()) {
			StarSystem sys = (StarSystem)db.get(StarSystem.class, this.system);
			int maxRetries = 100;

			while( ((loc.getX() == this.x) && (loc.getY() == this.y)) ||
					(loc.getX() < 1) || (loc.getY() < 1) ||
					(loc.getX() > sys.getWidth()) ||
					(loc.getY() > sys.getHeight()) ) {
				loc = loc.setX(this.x + ThreadLocalRandom.current().nextInt(3) - 1);
				loc = loc.setY(this.y + ThreadLocalRandom.current().nextInt(3) - 1);

				maxRetries--;
				if( maxRetries == 0 ) {
					break;
				}
			}
		}

		if( ship.getShip().getBattle() == null ) {
			// Es kann vorkommen, dass das Schiff bereits entfernt wurde (wegen einer dock-Beziehung)
			return;
		}

		// Falls das Schiff an einem anderen Schiff gedockt ist, dann das
		// Elternschiff fliehen lassen. Dieses kuemmert sich dann um die
		// gedockten Schiffe
		if( ship.getShip().isDocked() || ship.getShip().isLanded() ) {
			int masterid = ship.getShip().getBaseShip().getId();

			List<BattleShip> shiplist = this.ships.get(user);
			for (BattleShip aship : shiplist) {
					if (aship.getId() == masterid) {
							removeShip(aship, relocate);
							return;
					}
			}
		}

		long dockcount = ship.getShip().getAnzahlGedockterUndGelandeterSchiffe();

		ship.getShip().setBattle(null);
		ship.getShip().setX(loc.getX());
		ship.getShip().setY(loc.getY());

		db.delete(ship);
		ship.getShip().recalculateShipStatus();

		//
		// Feststellen in welcher der beiden Listen sich das Schiff befindet und dieses daraus entfernen
		//

		boolean found = false;
		List<BattleShip> shiplist = this.ships.get(user);

		for( int i=0; i < shiplist.size(); i++ ) {
			BattleShip aship = shiplist.get(i);

			if( aship == ship ) {
				shiplist.remove(i);
				i--; // Arraypositionen haben sich nach dem Entfernen veraendert. Daher Index aktuallisieren

				found = true;
			}
			// Evt ist das Schiff an das gerade fliehende gedockt
			// In diesem Fall muss es ebenfalls entfernt werden
			else if(dockcount > 0 && aship.getShip().getBaseShip() != null && aship.getShip().getBaseShip().getId() == ship.getId() ) {
				shiplist.remove(i);
				i--; // Arraypositionen haben sich nach dem Entfernen veraendert. Daher Index aktuallisieren

				dockcount--;

				aship.getShip().setBattle(null);
				aship.getShip().setBattleAction(false);
				aship.getShip().setX(loc.getX());
				aship.getShip().setY(loc.getY());

				db.delete(aship);
				aship.getShip().recalculateShipStatus();
			}

			if( found && (dockcount == 0) ) {
				break;
			}
		}

		if( this.activeShip.get(user) >= shiplist.size()) {
			this.activeShip.put(user,0);
		}
	}

	/**
	 *
	 * @param user Der Spieler von dem die Nahrungsbalance berechnet werden soll.
	 * @return Gibt die Nahrungsbalance der Schlacht zurueck.
	 */
	public int getNahrungsBalance(User user)
	{
		int balance = 0;

		org.hibernate.Session db = ContextMap.getContext().getDB();
		ScrollableResults ships = db.createQuery("from Ship where owner=:owner and id>0 and battle=:battle")
			.setParameter("owner", user)
			.setParameter("battle", this)
			.setCacheMode(CacheMode.IGNORE)
			.scroll(ScrollMode.FORWARD_ONLY);

		while(ships.next())
		{
			Ship ship = (Ship)ships.get(0);
			balance -= ship.getNahrungsBalance();
            db.evict(ship);
		}

		return balance;
	}


	/**
	 * Gibt das Log zu dieser Schlacht zurueck.
	 * @return Das Log
	 */
	public SchlachtLog getSchlachtLog()
	{
		return schlachtLog;
	}

	/**
	 * Setzt das Log zu dieser Schlacht.
	 * @param schlachtLog Das Log
	 */
	public void setSchlachtLog(SchlachtLog schlachtLog)
	{
		this.schlachtLog = schlachtLog;
	}

	/**
	 * Gibt eine Map<User,Integer> der User und der Seite, fuer die sie kaempfen, zurueck
	 * @return die Map
	 */
	public Map<User,Integer> getusers()
	{
		return this.users;
	}
}
