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

import net.driftingsouls.ds2.server.WellKnownConfigValue;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.ally.Ally;
import net.driftingsouls.ds2.server.framework.ConfigService;
import net.driftingsouls.ds2.server.services.AllianzService;
import net.driftingsouls.ds2.server.services.PmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
 * TASK_ALLY_LOW_MEMBER
 * 		Eine Allianz hat weniger als 2 Mitglieder (Praesi eingerechnet) und ist daher von der Aufloesung bedroht.
 *
 * 	- data1 -> die ID der betroffenen Allianz
 *  - data2 -> unbenutzt
 *  - data3 -> unbenutzt
 *
 *  @author Christopher Jung
 */
@Service
public class HandleAllyLowMember implements TaskHandler {
	private final AllianzService allianzService;
	private final PmService pmService;
	private final TaskManager taskManager;
	private final ConfigService configService;

	@PersistenceContext
	private EntityManager em;

	@Autowired
	public HandleAllyLowMember(AllianzService allianzService, PmService pmService, TaskManager taskManager, ConfigService configService)
	{
		this.allianzService = allianzService;
		this.pmService = pmService;
		this.taskManager = taskManager;
		this.configService = configService;
	}

	@Override
	public void handleEvent(Task task, String event) {
		if( event.equals("tick_timeout") ) {
			int allyid = Integer.parseInt(task.getData1());

			Ally ally = em.find(Ally.class, allyid);
			if( ally == null ) {
				taskManager.removeTask( task.getTaskID() );
				return;
			}

			User source = em.find(User.class, configService.getValue(WellKnownConfigValue.ALLIANZAUFLOESUNG_PM_SENDER));

			pmService.sendToAlly(source, ally, "Allianzauflösung", "[Automatische Nachricht]\n\nDeine Allianz wurde mit sofortiger Wirkung aufgel&ouml;st. Der Grund ist Spielermangel. Grunds&auml;tzlich m&uuml;ssen Allianzen mindestens 2 Mitglieder haben um bestehen zu k&ouml;nnen. Da deine Allianz in der vorgegebenen Zeit dieses Ziel nicht erreichen konnte war die Aufl&ouml;sung unumg&auml;nglich.");

			allianzService.loeschen(ally);

			taskManager.removeTask( task.getTaskID() );
		}
	}

}
