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
  private List<String> battleShipGroup;
  private boolean ready;
  private HashMap<User,BattleShip> activeShipMap;
  private HashMap<User,HashMap<Integer,Integer>> shipTypeCountUserMap;
  private Battle battle;





  public BattleSide(int side, User commander, Battle battle)
  {
    this.side_id = side;
    this.commander = commander;
    this.users.add(commander);
    this.battleShipMap = new HashMap<>();
    this.activeShipMap = new HashMap<>();
    this.shipTypeCountUserMap = new HashMap<>();
    setFlag(BattleFlag.FIRSTROUND, true);
    this.battle = battle;
  }

  public Battle getBattle() {
    return battle;
  }

  public void setBattle(Battle battle) {
    this.battle = battle;
  }

  public int getShipTypeCount(int shipType) {
    int i = 0;
    for(HashMap<Integer, Integer> map : shipTypeCountUserMap.values())
    {
      i += map.get(shipType);
    }
    return i;
  }

  public int getShipTypeCountForUser(User user, int shipType)
  {
    HashMap<Integer,Integer> map = shipTypeCountUserMap.get(user);
    int i = 0;
    if(map != null)
    {
      i = map.get(shipType);
    }
    return i;
  }

  public void updateShipTypeCountForUser(User user, List<BattleShip> shiplist)
  {
    if(users.contains(user))
    {
      HashMap<Integer,Integer> map = shipTypeCountUserMap.get(user);
      for(BattleShip b : shiplist)
      {
        map.put(b.getShip().getType(),map.get(b.getShip().getType()));
      }
      shipTypeCountUserMap.put(user,map);
    }
  }

  public void setShipTypeCountUserMap(HashMap<User, HashMap<Integer, Integer>> shipTypeCountUserMap) {
    this.shipTypeCountUserMap = shipTypeCountUserMap;
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

  public List<User> getUsers() {
    return users;
  }

  public void setUsers(List<User> users) {
    this.users = users;
  }

  public void addUser(User user)
  {
    this.users.add(user);
  }

  public int getFlags() {
    return flags;
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
	 * @param side Die Seite deren zweite Reihe geprueft werden soll
	 * @param added Eine Liste zusaetzlicher Schiffe, welche sich noch nicht in der Schlacht befinden oder <code>null</code>
	 * @return <code>true</code>, falls die zweite Reihe unter den Bedingungen stabil ist
	 */
	public boolean isSecondRowStable( int side, BattleShip ... added ) {
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



}
