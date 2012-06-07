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
package net.driftingsouls.ds2.server.tasks;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import net.driftingsouls.ds2.server.ContextCommon;
import net.driftingsouls.ds2.server.comm.PM;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.ally.Ally;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.Configuration;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;

/**
 * TASK_ALLY_FOUND
 * 		Einer Allianz gruenden.
 * 
 * 	- data1 -> der Name der Allianz
 *  - data2 -> die Anzahl der noch fehlenden Unterstuetzungen (vgl. TASK_ALLY_FOUND_CONFIRM)
 *  - data3 -> die Spieler, die in die neu gegruendete Allianz sollen, jeweils durch ein , getrennt (Pos: 0 -> Praesident/Gruender)  
 *  @author Christopher Jung
 */
@Configurable
class HandleAllyFound implements TaskHandler {
	
	private Configuration config;
	
    /**
     * Injiziert die DS-Konfiguration.
     * @param config Die DS-Konfiguration
     */
    @Autowired
    public void setConfiguration(Configuration config) 
    {
    	this.config = config;
    }

	@Override
	public void handleEvent(Task task, String event) {	
		Context context = ContextMap.getContext();
		org.hibernate.Session db = context.getDB();
		
		if( event.equals("__conf_recv") ) {
			int confcount = Integer.parseInt(task.getData1());
			if( confcount == 1 ) {
				String allyname = task.getData2();
				
				Integer[] allymemberIds = Common.explodeToInteger(",", task.getData3());
				User[] allymember = new User[allymemberIds.length];
				for( int i=0; i < allymemberIds.length; i++ ) {
					allymember[i] = (User)db.get(User.class, allymemberIds[i]);
				}
				
				int ticks = context.get(ContextCommon.class).getTick();
				
				Ally ally = new Ally(allyname, allymember[0]);
				int allyid = (Integer)db.save(ally);
		
				Common.copyFile(config.get("ABSOLUTE_PATH")+"data/logos/ally/0.gif", config.get("ABSOLUTE_PATH")+"data/logos/ally"+allyid+".gif");
				
				for( int i=0; i < allymember.length; i++ ) {
					User source = (User)ContextMap.getContext().getDB().get(User.class, 0);
					
					PM.send( source, allymember[i].getId(), "Allianzgr&uuml;ndung", "Die Allianz "+allyname+" wurde erfolgreich gegr&uuml;ndet.\n\nHerzlichen Gl&uuml;ckwunsch!");

					allymember[i].setAlly(ally);
					allymember[i].setAllyPosten(null);
					allymember[i].addHistory(Common.getIngameTime(ticks)+": Gr&uuml;ndung der Allianz "+allyname);	
					
					// Beziehungen auf "Freund" setzen
					for( int j=0; j < allymember.length; j++ ) {
						if( allymember[j] == allymember[i] ) {
							continue;
						}
				
						allymember[j].setRelation(allymember[i].getId(), User.Relation.FRIEND);
						allymember[i].setRelation(allymember[j].getId(), User.Relation.FRIEND);
					}
				}
				
				Taskmanager.getInstance().removeTask( task.getTaskID() );
			}
			else {
				confcount--;
				Taskmanager.getInstance().modifyTask(task.getTaskID(), Integer.toString(confcount), task.getData2(), task.getData3() );	
			}
		}
		else if( event.equals("__conf_dism") ) {
			Integer[] allymember = Common.explodeToInteger(",", task.getData3());
			User source = (User)ContextMap.getContext().getDB().get(User.class, 0);
			
			PM.send( source, allymember[0], "Allianzgr&uuml;ndung", "Die Allianzgr&uuml;ndung ist fehlgeschlagen, da ein Spieler seine Unterst&uuml;tzung verweigert hat.");
			Taskmanager.getInstance().removeTask( task.getTaskID() );
			
			Task[] tasklist = Taskmanager.getInstance().getTasksByData( Taskmanager.Types.ALLY_FOUND_CONFIRM, task.getTaskID(), "*", "*" );
			for( int i=0; i < tasklist.length; i++ ) {
				Taskmanager.getInstance().removeTask( tasklist[i].getTaskID() );	
			}
			
		}
		else if( event.equals("tick_timeout") ) {
			Integer[] allymember = Common.explodeToInteger(",", task.getData3());
			
			Taskmanager.getInstance().removeTask( task.getTaskID() );
			User source = (User)ContextMap.getContext().getDB().get(User.class, 0);
			
			for( int i=0; i < allymember.length; i++ ) {
				PM.send( source, allymember[i], "Allianzgr&uuml;ndung", "Die Allianzgr&uuml;ndung ist fehlgeschlagen, da nicht alle angegebenen Spieler in der notwendigen Zeit ihre Unterst&uuml;tzung signalisiert haben.");
			}
		}
	}

}
