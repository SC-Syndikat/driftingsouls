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
package net.driftingsouls.ds2.server.tick.regular;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.driftingsouls.ds2.server.Offizier;
import net.driftingsouls.ds2.server.bases.Base;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.cargo.ResourceID;
import net.driftingsouls.ds2.server.cargo.Resources;
import net.driftingsouls.ds2.server.comm.PM;
import net.driftingsouls.ds2.server.config.Faction;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.ConfigValue;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipClasses;
import net.driftingsouls.ds2.server.ships.ShipTypeData;
import net.driftingsouls.ds2.server.ships.Ships;
import net.driftingsouls.ds2.server.tick.TickController;
import net.driftingsouls.ds2.server.units.UnitCargo;

import org.hibernate.FlushMode;

/**
 * Berechnung des Ticks fuer Schiffe.
 * @author Christopher Jung
 *
 */
public class SchiffsTick extends TickController {
	private Map<String,ResourceID> esources;
	private Map<Base,Long> savelist;
	private boolean calledByBattle=false;

	@Override
	protected void prepare() {
		getDB().setFlushMode(FlushMode.MANUAL);

		esources = new LinkedHashMap<String,ResourceID>();
		esources.put("a", Resources.ANTIMATERIE);
		esources.put("d", Resources.DEUTERIUM);
		esources.put("u", Resources.URAN);
	}

	/**
	 * Consumes food from the given cargo.
	 * @param ship Das Schiff von dem aus versorgt werden soll
	 * @param crewToFeed Die zu versorgende Crew
	 * @param scaleFactor Der Skalierungsfaktor beim Nahrungsverbrauch
	 * @return Crew that couldn't be feed.
	 */
	private int consumeFood(Ship ship, int crewToFeed, double scaleFactor) {

		int crewThatCouldBeFeed = 0;
		if( crewToFeed > ship.getNahrungCargo()*scaleFactor ) {
			crewThatCouldBeFeed = (int)(ship.getNahrungCargo()*scaleFactor);
			crewToFeed -= crewThatCouldBeFeed;
		}
		else {
			crewThatCouldBeFeed = crewToFeed;
			crewToFeed = 0;
		}
		int tmp = (int)Math.ceil(crewThatCouldBeFeed/scaleFactor);
		ship.setNahrungCargo(ship.getNahrungCargo() - tmp);
		this.log(tmp+" von "+ship.getId()+" verbraucht");

		return crewToFeed;
	}


	private void tickShip( org.hibernate.Session db, Ship shipd ) {
		this.log(shipd.getName()+" ("+shipd.getId()+"):");
	//	boolean recalc = false;

		ShipTypeData shiptd = shipd.getTypeData();

		Cargo shipc = shipd.getCargo();

		this.log("\tAlt: crew "+shipd.getCrew()+" e "+shipd.getEnergy() +" speicher "+shipd.getNahrungCargo());
		
		if(shipd.getNahrungCargo() < shiptd.getNahrungCargo())
		{
			this.log("\tNahrungsspeicher aufladen");
			// Vom asti aufladen (wenn noetig)
			List<?> bases = db.createQuery("from Base where owner=? and system=? and x=? and y=?")
								.setEntity(0, shipd.getOwner())
								.setInteger(1, shipd.getSystem())
								.setInteger(2, shipd.getX())
								.setInteger(3, shipd.getY())
								.list();
			
			if(bases.size() > 0)
			{
				for(Iterator<?> iter=bases.iterator();iter.hasNext();)
				{
					Base base = (Base)iter.next();
					if(savelist.containsKey(base))
					{
						Cargo basecargo = base.getCargo();
						this.log("\tBasis "+base.getId()+" gefunden");
						long needed = shiptd.getNahrungCargo() - shipd.getNahrungCargo();
						this.log("\tBenoetige "+needed+" Nahrung");
						long savenahrung = savelist.get(base);
						long feednahrung = basecargo.getResourceCount(Resources.NAHRUNG) - savenahrung + shipd.getFoodConsumption();
						if(feednahrung > needed)
						{
							this.log("\tGenug Nahrung auf Asteroid");
							basecargo.substractResource(Resources.NAHRUNG, needed);
							shipd.setNahrungCargo(shipd.getNahrungCargo()+needed);
							this.log("\tSpeicher um "+needed+" aufgefuellt");
							break;
						}
						else
						{
							needed = feednahrung;
							shipd.setNahrungCargo(shipd.getNahrungCargo()+needed);
							basecargo.substractResource(Resources.NAHRUNG, needed);
						}
						savelist.put(base, savenahrung - shipd.getFoodConsumption());
						base.setCargo(basecargo);
						this.log("\tSpeicher um "+needed+" aufgefuellt");
					}
				}
			}
			this.log("\tBasen fertig.");
		}

		//Mein Mutterschiff - relevant bei gedockten Schiffen
		Ship baseShip = shipd.getBaseShip();

		this.slog("\tCrew: ");
		//Crew die noch gefuettert werden muss
		int crewToFeed = shipd.getNettoFoodConsumption();

		//Faktor fuer den Verbrauch
		double scaleFactor = shipd.getAlertScaleFactor();

		//VersorgerCargo, Basisschiffcargo, eigener Cargo - Leerfuttern in der Reihenfolge
		while(shipd.getVersorger() != null && crewToFeed > 0)
		{
			Ship versorger = shipd.getVersorger();
			crewToFeed = consumeFood(versorger ,crewToFeed, scaleFactor);
			getContext().commit();
		}

		if(baseShip != null)
		{
			crewToFeed = consumeFood(baseShip, crewToFeed, scaleFactor);
		}
		
		crewToFeed = consumeFood(shipd, crewToFeed, scaleFactor);
		
		// Nahrungsspeicher auf Maximum beschraenken.
		if(shipd.getNahrungCargo() > shiptd.getNahrungCargo())
		{
			shipd.setNahrungCargo(shiptd.getNahrungCargo());
		}
		
		//Crew die nicht versorgt werden konnte verhungern lassen
		if(crewToFeed > 0) {
			this.log("Crew verhungert - ");
	//		recalc = true;
		}
		if(crewToFeed >= (int)Math.ceil(shipd.getUnits().getNahrung() / 10)){
			crewToFeed = crewToFeed - (int)Math.ceil(shipd.getUnits().getNahrung() / 10);
			shipd.setUnits(new UnitCargo());
			int crew = shipd.getCrew() - crewToFeed*10;
			if(crew < 0)
			{
				shipd.setCrew(0);
				this.log("Gedockte Schiffe verhungern.");
				crew = Math.abs(crew);
				List<Ship> dockedShips = shipd.getLandedShips();
				for(Iterator<Ship> iter=dockedShips.iterator();iter.hasNext();)
				{
					Ship dockShip = iter.next();
					if(crew > dockShip.getCrew())
					{
						crew -= dockShip.getCrew();
						dockShip.setCrew(0);
					}
					else
					{
						dockShip.setCrew(dockShip.getCrew()-crew);
						break;
					}
				}
			}
			else
			{
				shipd.setCrew(crew);
			}
		}
		else {
			shipd.getUnits().fleeUnits(crewToFeed*10);
			crewToFeed = 0;
		}
		
		//Damage ships which don't have enough crew
		int crew = shipd.getCrew();
		int minCrew = shiptd.getMinCrew();
		User user = shipd.getOwner();
		this.log("Crew " + crew);
		this.log("MinCrew " + minCrew);
		if(crew < minCrew && !user.hasFlag(User.FLAG_NO_HULL_DECAY))
		{
	//		recalc = true;
			this.log("Schiff hat nicht genug Crew; beschaedige Huelle.");
			ConfigValue value = (ConfigValue)db.get(ConfigValue.class, "nocrewhulldamagescale");
			double scale = Double.parseDouble(value.getValue());
			double damageFactor = (1.0 - (((double)crew) / ((double)minCrew))) / scale;
			this.log("Damage factor is: " + damageFactor);
			
			int oldArmor = shipd.getAblativeArmor();
			if(oldArmor > 0)
			{
				int damage = (int)Math.ceil(shiptd.getAblativeArmor()*damageFactor);
				int newArmor = oldArmor - damage;
				if(newArmor < 0)
				{
					this.log("Ablative Panzerung zerstoert.");
					shipd.setAblativeArmor(0);
				}
				else
				{
					this.log("Ablative Panzerung beschaedigt - neu: " + newArmor);
					shipd.setAblativeArmor(newArmor);
				}
			}
			else
			{
				int damage = (int)Math.ceil(shiptd.getHull()*damageFactor);
				int newHull = shipd.getHull() - damage;
				if(newHull > 0)
				{
					this.log("Huelle beschaedigt - neu: " + newHull);
					shipd.setHull(newHull);
				}
				else
				{
					this.log("Schiff zerstoert.");
					shipd.setStatus("destroy");
					return;
				}
			}
		}
		
		//Pay sold and maintenance
		int reCost = shipd.getBalance();
		if(reCost > 0)
		{
			this.log("Zahle Sold und Wartungskosten");
			User owner = shipd.getOwner();
			BigInteger account = owner.getKonto();
			BigInteger reCostHelp = BigInteger.valueOf(reCost);
			
			//Account is balanced
			if(account.compareTo(reCostHelp) >= 0)
			{
				this.log("Bezahlter Betrag: " + reCost);
				User nobody = (User)db.get(User.class, -1);
				nobody.transferMoneyFrom(owner.getId(), reCost);
			}
			else
			{
				BigInteger reCostHelper = BigInteger.valueOf(shiptd.getReCost());
				// Wartungskosten koennen aufgebracht werden.
				if(account.compareTo(reCostHelper) >= 0)
				{
					this.log("Konto nicht gedeckt; Besatzung meutert.");
					
					// Sammel alle Daten zusammmen
					User pirate = (User)db.get(User.class, Faction.PIRATE);
					UnitCargo unitcargo = shipd.getUnits();
					UnitCargo meuterer = unitcargo.getMeuterer(account.intValue() - shiptd.getReCost());
					
					if(meuterer.kapern(unitcargo, new UnitCargo(), new UnitCargo(), 0, 1, 1))
					{
						shipd.setOwner(pirate);
						
						PM.send(pirate, owner.getId(), "Besatzung meutert", "Die Besatzung der " + shipd.getName() + " meutert, nachdem Sie den Sold der Einheiten nicht aufbringen konnten. (" + shipd.getLocation().displayCoordinates(false) + ")");
					}
					else
					{
						PM.send(pirate, owner.getId(), "Besatzung meutert", "Die Besatzung der " + shipd.getName() + " meutert, nachdem Sie den Sold der Einheiten nicht aufbringen konnten. Die Meuterer wurden vernichtet. (" + shipd.getLocation().displayCoordinates(false) + ")");
					}
					shipd.setUnits(unitcargo);
					owner.setKonto(BigInteger.ZERO);
				}
				else
				{
					User pirate = (User)db.get(User.class, Faction.PIRATE);
					shipd.setOwner(pirate);
					owner.setKonto(BigInteger.ZERO);
					
					this.log("Konto nicht gedeckt; Schiff desertiert zum Piraten.");
					PM.send(pirate, owner.getId(), "Schiff desertiert", "Die " + shipd.getName() + " ist desertiert, nachdem Sie den Sold der Crew nicht aufbringen konnten. (" + shipd.getLocation().displayCoordinates(false) + ")");
				}
			}
		}

		//Berechnung der Energie
		this.log("\tEnergie:");
		int e = shipd.getEnergy();
		
		
		if(shiptd.getShipClass() != ShipClasses.GESCHUETZ.ordinal()) 
		{
			e -= shipd.getAlertEnergyCost();
			if( e < 0 ) {
				e = 0;
			}	
		}	

		if( e < shiptd.getEps() ) {
	//		recalc = true;
			int rm = shiptd.getRm();
			if( shiptd.getCrew() > 0 ) {
				rm = (int)(rm * shipd.getCrew() / (double)shiptd.getCrew());
			}
			int maxenergie = rm;

			// Reihenfolge muss identisch zu this.esources sein!
			int[] reactres = new int[] {shiptd.getRa(), shiptd.getRd(), shiptd.getRu()};
			int index = 0;

			for( String resshort : this.esources.keySet() ) {
				ResourceID resid = this.esources.get(resshort);

				if(reactres[index] > 0) {
					this.slog("\t * "+Cargo.getResourceName(resid)+": ");
					if( shipc.getResourceCount( resid ) > 0 ) {
						this.log(shipc.getResourceCount( resid )+" vorhanden");

						int max = (int)Math.round(rm / (double)reactres[index]);
						if( max > Math.round(maxenergie/(double)reactres[index]) ) {
							max = (int)Math.round(maxenergie/(double)reactres[index]);
						}

						int need = shiptd.getEps() - e;
						this.log("\t   maximal: "+max+" Energie bei "+reactres[index]+" Reaktorwert : "+need+" Energie frei im eps");

						int counter = 0;
						for( int k=0; k < max; k++ ) {
							if( (need > 0) && (shipc.getResourceCount( resid ) > 0) ) {
								counter++;
								if( maxenergie < reactres[index] ) {
									e += maxenergie;
									maxenergie = 0;
								} 
								else {
									e += reactres[index];
									maxenergie -= reactres[index];
								}
								shipc.substractResource( resid, 1 );
								need -= reactres[index];
								if( e > shiptd.getEps() ) {
									e = shiptd.getEps();
								}
							}
						}
						this.log("\t   verbrenne "+counter+" "+Cargo.getResourceName(resid));
					} 
					else {
						this.log(" kein "+Cargo.getResourceName(resid)+" vorhanden");
					}
				}

				index++;
			}
		}

		int[] sub = new int[] {shipd.getEngine(),shipd.getWeapons(),shipd.getComm(),shipd.getSensors()};

		// Schiff bei Bedarf und falls moeglich reparieren
		if( (shipd.getBattle() == null) && (shipd.getStatus().indexOf("lowmoney") == -1) &&
				( (shipd.getEngine() < 100) || (shipd.getWeapons() < 100) || (shipd.getComm() < 100) || (shipd.getSensors() < 100) ) &&
				(Ships.getNebula(shipd.getLocation()) != 6)  ) {

			Offizier offizier = Offizier.getOffizierByDest('s', shipd.getId());

			for( int a=0; a<=3; a++ ) {
				int old = sub[a];
				if( shipd.getCrew() == shiptd.getCrew() ) {
					sub[a] += 20;
				}
				else if( shipd.getCrew() > shiptd.getCrew()/2 ) {
					sub[a] += 15;
				}
				else if( shipd.getCrew() == shiptd.getCrew()/2 ) {
					sub[a] += 10;
				}
				else if( shipd.getCrew() < shiptd.getCrew()/2 ) {
					sub[a] += 5;
				}

				if( offizier != null ) {
					sub[a] += (int)(offizier.getAbility(Offizier.Ability.ING) / 3d );

					if( sub[a] > 40 + (int)(offizier.getAbility(Offizier.Ability.ING)/4d) ) {
						sub[a] = 40 + (int)(offizier.getAbility(Offizier.Ability.ING)/4d);
					}
				} 
				else if( sub[a] > 40 ) {
					sub[a] = 40;
				}
				if( old > sub[a] ) {
					sub[a] = old;
				}
				if( sub[a] > 100 ) {
					sub[a] = 100;
				}
			}
		}

		// Evt. Deuterium sammeln
		if( shipd.getAutoDeut() && (shiptd.getDeutFactor() != 0) && (shipd.getCrew() >= shiptd.getCrew()/2) && (e > 0) && (shipc.getMass() < shiptd.getCargo()) ) {
			this.slog("\tS. Deut: ");
	//		recalc = true;
			int nebel = Ships.getNebula(shipd.getLocation());

			if( (nebel >= 0) && (nebel <= 2) ) {
				int tmpe = e;

				int deutfactor = shiptd.getDeutFactor();
				if( nebel == 1 ) {
					deutfactor--;
				}
				else if( nebel == 2 ) {
					deutfactor++;
				}

				if( Cargo.getResourceMass( Resources.DEUTERIUM, tmpe * deutfactor ) > (shiptd.getCargo() - shipc.getMass()) ) {
					tmpe = (int)( (shiptd.getCargo()-shipc.getMass())/(deutfactor*Cargo.getResourceMass( Resources.DEUTERIUM, 1 )) );
					this.slog("[maxcargo]");
				}
				int saugdeut = tmpe * deutfactor;

				shipc.addResource( Resources.DEUTERIUM, saugdeut );
				e -= tmpe;
				this.log(tmpe+" Deuterium");
			}
			else {
				this.log("kpn");
			}
		}

		shipd.setEngine(sub[0]);
		shipd.setWeapons(sub[1]);
		shipd.setComm(sub[2]);
		shipd.setSensors(sub[3]);
		shipd.setEnergy(e);
		shipd.setWeaponHeat("");
		shipd.setCargo(shipc);
		
		this.slog("\tNeu: crew "+shipd.getCrew()+" e "+e+" speicher "+shipd.getNahrungCargo()+" status: <");
		/*
		if(recalc || shipd.getTypeData().hasFlag(ShipTypes.SF_VERSORGER))
		{
			shipd.recalculateShipStatus();
		}
		*/
		this.slog(shipd.getStatus());
		

		this.log(">");
	}

	private void tickUser(org.hibernate.Session db, User auser, String battle) {
		//List<Integer> idlist = new ArrayList<Integer>();

		//long prevnahrung = this.usercargo.getResourceCount(Resources.NAHRUNG);
		
		savelist = new HashMap<Base,Long>();

		// Schiffe berechnen
		List<?> ships = db.createQuery(
				"from Ship as s left join fetch s.modules" +
				" where s.id>0 and s.owner=? " +
				" and system!=0 and "+battle +
				"order by s.shiptype.versorger DESC, " +
				" s.modules.versorger DESC, s.shiptype.jDocks DESC," +
				"s.modules.jDocks DESC,s.shiptype ASC")
				.setEntity(0, auser)
				.list();

		List<?> bases = db.createQuery("from Base where owner=?")
									.setEntity(0,auser)
									.list();
		
		for( Iterator<?> baseiter=bases.iterator();baseiter.hasNext();)
		{
			Base base = (Base)baseiter.next();
			savelist.put(base, base.getSaveNahrung());
		}
		
		this.log(auser.getId()+": Es sind "+ships.size()+" Schiffe zu berechnen ("+battle+")");

		for( Iterator<?> iter=ships.iterator(); iter.hasNext(); ) {
			Ship ship = (Ship)iter.next();
			//idlist.add(ship.getId());

			try {         
				this.tickShip( db, ship );
			}
			catch( RuntimeException e ) {
				this.log("ship "+ship.getId()+" failed: "+e);
				e.printStackTrace();
				Common.mailThrowable(e, "SchiffsTick Exception", "ship: "+ship.getId());

				throw e;
			}
		}

		// Nahrung verarbeiten
		// Vorerst auskommentiert bis entsprechende Optimierungen es brauchen
		/*if( Configuration.getIntSetting("DISABLE_FOOD_CONSUMPTION") == 0 ) {
			Long crewcount ;
			String idListStr = "";
			if( idlist.size() > 0 ) {
				idListStr = " AND id NOT IN ("+Common.implode(",",idlist)+") ";	
			}
			crewcount = (Long)db.createQuery("select sum(s.crew) " +
					"from Ship as s " +
					"where s.id>0 and s.system!=0 and s.owner=? " +
					"AND "+battle+idListStr)
					.setEntity(0, auser)
					.iterate().next();

			if( crewcount == null ) {
				crewcount = (long)0;
			}

			this.log("# base+: "+(prevnahrung-usernstat));
			this.log("# Verbrauche "+crewcount+" Nahrung");
			this.log("# "+(prevnahrung-this.usercargo.getResourceCount(Resources.NAHRUNG))+" bereits verbucht");
			if( crewcount <= this.usercargo.getResourceCount(Resources.NAHRUNG) ) {
				this.usercargo.substractResource(Resources.NAHRUNG, crewcount);
			}
			else {
				// Nicht genug Nahrung fuer alle -> verhungern
				crewcount -= this.usercargo.getResourceCount(Resources.NAHRUNG);
				this.usercargo.setResource(Resources.NAHRUNG, 0);
				List shiplist = db.createQuery("from Ship as s " +
						"where s.id>0 and s.system!=0 and s.owner=? " +
						"and s.crew>0 and "+battle)
						.setEntity(0, auser)
						.list();
				for( Iterator iter=shiplist.iterator(); iter.hasNext(); ) {
					Ship s = (Ship)iter.next();

					if( s.getCrew() < crewcount ) {
						s.setCrew(0);
						s.recalculateShipStatus();
						this.log(s.getId()+" verhungert");
					}
					else {
						s.setCrew((int)(s.getCrew()-crewcount));
						s.recalculateShipStatus();
						this.log(s.getId()+" "+crewcount+" Crew verhungert");
						break;
					}
				}
			}
		}*/

	}

	@Override
	protected void tick() {
		org.hibernate.Session db = getDB();

		String userlist = "";
		String battle = "";

		// Wurden wir von einer Schlacht aufgerufen? (Parameter '--battle $schlachtid')
		int battleid = getContext().getRequest().getParameterInt("battle");
		if( battleid != 0 ) {
			this.calledByBattle = true;
			battle = "s.battle="+battleid;

			List<Integer> userIdList = new ArrayList<Integer>();
			List<?> users = db.createQuery("select distinct owner from Ship where battle=?")
			.setInteger(0, battleid)
			.list();
			for( Iterator<?> iter=users.iterator(); iter.hasNext(); ) {
				User auser = (User)iter.next();
				userIdList.add(auser.getId());
			}

			if( userIdList.size() > 0 ) {
				userlist = " and id in ("+Common.implode(",",userIdList)+") ";
			}
		}
		else {
			this.calledByBattle = false;
			battle = "s.battle is null ";
		}

		int index = 0;

		// Ueberhitzung
		if( !this.calledByBattle ) {
			db.createQuery("update Ship set heat=heat-(case when heat>=70 then 70 else heat end) " +
			"where heat>0 and owner in (from User where vaccount=0 or wait4vac>0) and id>0 and system!=0 and battle is null")
			.executeUpdate();
		}

		Iterator<?> useriter = db.createQuery("select id from User " +
				"where id!=0 and (vaccount=0 or wait4vac>0) "+userlist+" order by id asc")
				.list().iterator();
		for(; useriter.hasNext(); ) {
			int auserId = (Integer)useriter.next();

			User auser = (User)db.get(User.class, auserId);

			for( int retry=0; retry < 3; retry++ ) {
				try {
					
					this.tickUser(db, auser, battle);
	
					if( !calledByBattle ) {
						getDB().flush();
						getContext().commit();
					}
					
					break;
				}
				catch( RuntimeException e ) {
					if( calledByBattle ) {
						throw e;
					}
					
					getContext().rollback();
					db.clear();
	
					this.log("#"+retry+" User "+auser.getId()+" failed: "+e);
					e.printStackTrace();
					Common.mailThrowable(e, "ShipTick #"+retry+" Exception", "User: "+auser.getId()+"\nBattle: "+battle);
	
					auser = (User)db.get(User.class, auser.getId());
				}
				finally {
					if( !calledByBattle ) {
						db.evict(auser);
					}
				}
			}

			if( !calledByBattle && (index++ % 5 == 0) ) {
				getDB().flush();
				getDB().clear();
			}
		}

		getDB().setFlushMode(FlushMode.AUTO);

		db.createQuery("update Ship set crew=0 where id>0 and crew<0").executeUpdate();

		if( this.calledByBattle ) {
			return;
		}			

		/*
			Schiffe mit destroy-tag im status-Feld entfernen
		 */
		this.log("");
		this.log("Zerstoere Schiffe mit 'destroy'-status");

		List<?> ships = db.createQuery("from Ship where id>0 and locate('destroy',status)!=0").list();
		for( Iterator<?> iter=ships.iterator(); iter.hasNext(); ) {
			Ship aship = (Ship)iter.next();

			this.log("\tEntferne "+aship.getId());
			aship.destroy();
		}

		/*
		 * Schadensnebel
		 */
		this.log("");
		this.log("Behandle Schadensnebel");
		ships = db.createQuery("select s from Ship as s, Nebel as n " +
		"where s.system=n.loc.system and s.x=n.loc.x and s.y=n.loc.y and n.type=6 and (s.owner.vaccount=0 or s.owner.wait4vac>0) and s.docked not like 'l %'").list();
		for( Iterator<?> iter=ships.iterator(); iter.hasNext(); ) {
			Ship ship = (Ship)iter.next();

			this.log("* "+ship.getId());
			int[] sub = new int[] {ship.getEngine(),ship.getWeapons(),ship.getComm(),ship.getSensors()};

			for( int i=0; i < sub.length; i++ ) {
				sub[i] -= 10;
				if( sub[i] < 0 ) {
					sub[i] = 0;
				}
			}

			int hull = ship.getHull();
			if( hull > 1 ) {
				hull -= (int)(hull*0.05d);
				if( hull < 1 ) {
					hull = 1;
				}
			}

			ship.setEngine(sub[0]);
			ship.setWeapons(sub[1]);
			ship.setComm(sub[2]);
			ship.setSensors(sub[3]);
			ship.setHull(hull);
		}
	}
}
