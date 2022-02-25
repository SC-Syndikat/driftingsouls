/*
 *	Drifting Souls 2
 *	Copyright (c) 2008 Christopher Jung
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.ships.ShipType;
import net.driftingsouls.ds2.server.ships.ShipTypeData;
import net.driftingsouls.ds2.server.ships.ShipTypeFlag;

/**
 * Klasse fuer eine von n Seiten einer Schlacht.
 * Hier werden alle Informationen der Schlachtseite verwaltet.
 * @author Gregor Fuhs
 *
 */
public class BattleSide
{
  private int side_id;

  private User commander;
  private List<User> users;
  private int flags;
  private HashMap<User,List<BattleShip>> battleShipMap;
  private HashMap<User,ShipType> targetShipGroup;
  private List<String> battleShipGroup;
  private boolean ready;
  private HashMap<User,BattleShip> activeShipMap;
  private Battle battle;
  private User takeCommand;

  public BattleSide(int side, User commander, Battle battle)
  {
    this.side_id = side;
    this.commander = commander;
    this.battleShipMap = new HashMap<>();
    this.activeShipMap = new HashMap<>();
    addUser(commander);
    setFlag(BattleFlag.FIRSTROUND, true);
    this.ready = false;
    this.battle = battle;
    this.targetShipGroup = new HashMap<>();
  }

  public Battle getBattle() {
    return battle;
  }

  public void setBattle(Battle battle) {
    this.battle = battle;
  }

  public int getShipTypeCount(ShipType shipType) {
    int i = 0;
    for(BattleShip ship : getBattleShipList())
    {
      if(ship.getShip().getBaseType().getId() == shipType.getId())
      {
        i++;
      }
    }
    return i;
  }


	/**
	 * Ermittelt den Kampfstärke einer Seite.
	 *
	 * @return Die Kampfstärke einer Seite
	 */
	public int getBattleValue()
	{
		int battleValue = 0;
		List<BattleShip> ships = getBattleShipList();

		for(BattleShip ship: ships)
		{
			battleValue += ship.getBattleValue();
		}

		return battleValue;
	}

  public void removeUser(User user)
  {
    if (getUsers().contains(user))
    {
      this.users.remove(user);
      this.activeShipMap.remove(user);
      this.battleShipMap.remove(user);
      if(takeCommand.getId() == user.getId())
      {
        takeCommand = null;
      }
    }
  }

  public List<BattleShip> getBattleShipList()
  {
    List<BattleShip> res = new ArrayList<>();
    for(User u : getUsers())
    {
      res.addAll(getBattleShipForUser(u));
    }
    return res;
  }

  public void addShip(BattleShip ship)
  {
    if(ship == null)
    {
      return;
    }
    User user = ship.getOwner();
    if(!getUsers().contains(user))
    {
      addUser(user);
    }
    List<BattleShip> ships = getBattleShipForUser(user);
    ships.add(ship);
  }

  	/**
	 * Prueft, ob ein Spieler auf einer Seite Mitglied ist.
	 * @param user Der Spieler
	 * @return <code>true</code>, falls er Mitglied ist
	 */
	public boolean isMitglied( User user) {
		if(users.contains(user))
		{
			return true;
		}
    return false;
	}

  	/**
	 * Gibt eine Map fuer eine Seite zurueck, welche als Schluessel Schiffstypen und als zugeordnete
	 * Werte die Anzahl der vorkommenden Schiffes des Typs auf der Seite hat.
	 * @return Eine Map, welche angibt, wie haeufig ein Schiffstyp auf einer Seite vorkommt
	 */
	public Map<Integer,Integer> getShipTypeCount() {
		Map<Integer,Integer> shipTypeCount = new HashMap<>();
		for(BattleShip ship : getBattleShipList())
		{
			int shipTypeId = ship.getShip().getBaseType().getId();
      int count = shipTypeCount.containsKey(shipTypeId)? shipTypeCount.get(shipTypeId) : 0;
			shipTypeCount.put(shipTypeId, count + 1 );
		}
		return shipTypeCount;
	}

  /**
	 * Setzt die ID der aktuell ausgewaehlten Schiffsgruppe des Users.
	 * @param User der User
	 * @param enemyShipGroup Die neue Schiffsgruppen-ID
	 */
	public void setShipGroup(User user, ShipType enemyShipGroup) {
		if(users.contains(user))
			this.targetShipGroup.put(user, enemyShipGroup);
	}

  /**
	 * Gibt die ID der aktuell ausgewaehlten Schiffsgruppe des Users zurueck.
	 * @param User der User
	 * @return Die ID der aktuellen gegnerischen Schiffsgruppe
	 */
	public ShipType getShipGroup(User user) {
		if(users.contains(user))
			return targetShipGroup.get(user);
		else
			return null;
	}

  public int getShipTypeCountForUser(User user, ShipType shipType)
  {
    int i = 0;
    for(BattleShip ship : getBattleShipForUser(user))
    {
      if(ship.getShip().getBaseType().getId() == shipType.getId())
      {
        i++;
      }
    }
    return i;
  }

  public HashMap<User, BattleShip> getActiveShipMap() {
    return activeShipMap;
  }

  public void setActiveShipMap(HashMap<User, BattleShip> activeShipMap) {
    this.activeShipMap = activeShipMap;
  }

  public BattleShip getActiveShipForUser(User user)
  {
    return activeShipMap.get(user);
  }

  public void setActiveShipForUser(User user, BattleShip ship)
  {
    if(users.contains(user))
    {
      activeShipMap.put(user,ship);
    }
  }

  public boolean isReady() {
    return ready;
  }

  public void setReady(boolean ready) {
    this.ready = ready;
  }

  public int getSide_id() {
    return side_id;
  }

  public void setSide_id(int side_id) {
    this.side_id = side_id;
  }

  public User getCommander() {
    return commander;
  }

  public void setCommander(User commander) {
    this.commander = commander;
  }

  	/**
	 * Gibt den Spieler zurueck, der das Kommando uebernehmen will. Sollte
	 * kein solcher Spieler existieren, so wird <code>null</code> zurueckgegeben.
	 * @return Den Spieler oder <codeNULL</code>
	 */
	public User getTakeCommand() {
			return takeCommand;
	}
  /**
	 * Setzt den Spieler, welcher das Kommando uebernehmen will.
	 * @param user Der Spieler
	 */
	public void setTakeCommand(User user) {
		//pruefen, ob die Seite gueltig ist
		if (getUsers().contains(user))
		{
			takeCommand = user;
		}

	}

  	/**
	 * Liefert das naechste Schiff des Users nach dem aktuell ausgewaehlten.
	 * @param User der User
	 * @return Das naechste passende BattleShip
	 */
	public BattleShip getNextBattleShip(User user) {
    List<BattleShip> bs = getBattleShipForUser(user);
    BattleShip activeShip = getActiveShipForUser(user);
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
    return getActiveShipForUser(user);
	}


  public List<User> getUsers() {
    return users;
  }

  public void setUsers(List<User> users) {
    this.users = users;
  }

  public void addUser(User user)
  {
    this.users.add(user);
    this.battleShipMap.put(user, new ArrayList<BattleShip>());
    this.activeShipMap.put(user, null);
  }

  public int getFlags() {
    return flags;
  }

	/**
	 * Prueft, ob das angegebene Flag fuer die Schlachtseite gesetzt ist.
	 * @param flag Das Flag
	 * @return <code>true</code>, falls dem so ist
	 */
	public boolean hasFlag(@Nonnull BattleFlag flag)
	{
		return (this.flags & flag.getBit()) != 0;
	}

  public List<String> getBattleShipGroup() {
    return battleShipGroup;
  }

  public void setBattleShipGroup(List<String> battleShipGroup) {
    this.battleShipGroup = battleShipGroup;
  }

  public HashMap<User,List<BattleShip>> getBattleships() {
    return battleShipMap;
  }

  public void setBattleships(HashMap<User,List<BattleShip>> battleships) {
    this.battleShipMap = battleships;
  }

  public void setBattleShipsForUser(User user, List<BattleShip> shiplist)
  {
    battleShipMap.put(user, shiplist);
  }

  public List<BattleShip> getBattleShipForUser(User user)
  {
    return battleShipMap.get(user);
  }

	/**
	 * Fuegt der Schlachtseite ein Flag hinzu oder entfernt eines.
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
  * Prueft, ob eine zweite Reihe existiert
  * @return <code>true</code>, falls eine zweite Reihe exitiert
  */
  public boolean hasSecondRow() {
    List<BattleShip> shiplist = new ArrayList<>();
    for(List<BattleShip> l : battleShipMap.values())
    {
      shiplist.addAll(l);
    }

    int counter = 0;
    for (BattleShip aship : shiplist) {
        if (aship.hasFlag(BattleShipFlag.JOIN)) {
            continue;
        }

        if (aship.hasFlag(BattleShipFlag.SECONDROW)) {
            if (!aship.getShip().isDocked() && !aship.getShip().isLanded()) {
                counter ++;
            }
        }
    }
    return counter > 0;
  }

  /**
  * Prueft, ob eine erste Reihe existiert
  * @param side Die Seite deren zweite Reihe geprueft werden soll
  * @return <code>true</code>, falls eine zweite Reihe exitiert
  */
  public boolean hasFrontRow( int side) {

    List<BattleShip> shiplist = new ArrayList<>();
    for(List<BattleShip> l : battleShipMap.values())
    {
      shiplist.addAll(l);
    }

    int counter = 0;
    for (BattleShip aship : shiplist) {
        if (!(aship.hasFlag(BattleShipFlag.JOIN) || aship.hasFlag(BattleShipFlag.FLUCHT) || aship.hasFlag(BattleShipFlag.SECONDROW))) {
            counter ++;
        }
    }
    return counter > 0;
  }

  /**
  * Prueft, ob fiehende / beitretende Schiffe existiert
  * @param side Die Seite deren zweite Reihe geprueft werden soll
  * @return <code>true</code>, falls eine zweite Reihe exitiert
  */
  public boolean hasJoinFluchtRow( int side) {
    List<BattleShip> shiplist = new ArrayList<>();
    for(List<BattleShip> l : battleShipMap.values())
    {
      shiplist.addAll(l);
    }

    int counter = 0;
    for (BattleShip aship : shiplist) {
        if (aship.hasFlag(BattleShipFlag.JOIN) || aship.hasFlag(BattleShipFlag.FLUCHT)) {
            counter ++;
        }
    }
    return counter > 0;
  }
  	/**
	 * Prueft, ob die zweite Reihe stabil ist. Beruecksichtigt wird auf Wunsch
	 * auch eine Liste von Schiffen, welche der Schlacht noch nicht begetreten sind
	 * (unter der Annahme, dass gemaess Flags die Schiffe in der ersten bzw zweiten
	 * Reihe landen wuerden).
	 * @param added Eine Liste zusaetzlicher Schiffe, welche sich noch nicht in der Schlacht befinden oder <code>null</code>
	 * @return <code>true</code>, falls die zweite Reihe unter den Bedingungen stabil ist
	 */
	public boolean isSecondRowStable( BattleShip ... added ) {
		List<BattleShip> shiplist = new ArrayList<>();
    for(List<BattleShip> l : battleShipMap.values())
    {
      shiplist.addAll(l);
    }

		double owncaps = 0;
		double secondrowcaps = 0;
    for (BattleShip aship : shiplist) {
        if (aship.hasFlag(BattleShipFlag.JOIN)) {
            continue;
        }
        ShipTypeData type = aship.getTypeData();

        double size = type.getSize();
        if (aship.hasFlag(BattleShipFlag.SECONDROW)) {
            if (!aship.getShip().isDocked() && !aship.getShip().isLanded()) {
                secondrowcaps += size;
            }
        }
        else
        {
            if (size > ShipType.SMALL_SHIP_MAXSIZE) {
                double countedSize = size;
                if (type.getCrew() > 0) {
                    countedSize *= (aship.getCrew() / ((double) type.getCrew()));
                }
                owncaps += countedSize;
            }
        }
    }

		if( added != null ) {
      for (BattleShip aship : added) {
          ShipTypeData type = aship.getTypeData();

          if (!type.hasFlag(ShipTypeFlag.SECONDROW)) {
              continue;
          }

          if (!aship.getShip().isDocked() && !aship.getShip().isLanded()) {
              secondrowcaps += type.getSize();
          }
      }
		}

    return Double.valueOf(secondrowcaps).intValue() == 0 || Double.valueOf(owncaps).intValue() >= Double.valueOf(secondrowcaps).intValue() * 2;

  }

  	/**
	 * Gibt die Anzahl der Schiffe eines Users zurueck.
	 * @return die Anzahl
	 */
	public int getShipCount(User user) {
    if(getUsers().contains(user))
    {
      return getBattleships().get(user).size();
    }
		return 0;
	}

  /**
   * Gibt die Anzahl der Schiffe der Schlachtseite zurueck
   * @return die Anzahl
   */
  public int getShipCount()
  {
    int count = 0;
    for(User u : getUsers())
    {
      count += getShipCount(u);
    }
    return count;
  }



}
