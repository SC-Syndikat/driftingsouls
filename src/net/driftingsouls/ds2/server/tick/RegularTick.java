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
package net.driftingsouls.ds2.server.tick;

import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.Configuration;

/**
 * Der normale Tick
 * @author Christopher Jung
 *
 */
public class RegularTick extends AbstractTickExecuter {

	@Override
	protected void executeTicks() {
		Common.stub();
		// TODO
		
		publishStatus("berechne Basen");
		//execTick(BaseTick.class);

		publishStatus("berechne Schiffe");
		//execTick(SchiffsTick.class);

		publishStatus("berechne Werften");
		//execTick(WerftTick.class);

		publishStatus("berechne Forschungen");
		//execTick(ForschungsTick.class);

		publishStatus("fuehre NPC-Aktionen aus");
		//execTick(NPCScriptTick.class);
		//execTick(NPCOrderTick.class);

		publishStatus("berechne Akademien");
		//execTick(AcademyTick.class);

		publishStatus("berechne GTU");
		//execTick(RTCTick.class);

		publishStatus("berechne Schlachten");
		//execTick(BattleTick.class);

		publishStatus("berechne Sonstiges");
		//execTick(RestTick.class);
	}

	@Override
	protected void prepare() {
		setName("");
		setLogPath(Configuration.getSetting("LOXPATH")+"tick/");
	}
	
	/**
	 * Hauptfunktion
	 * @param args Die Kommandozeilenargumente
	 * @throws Exception
	 */
	public static void main( String[] args ) throws Exception {
		boot(args);
		RegularTick tick = new RegularTick();
		tick.addLogTarget(TickController.STDOUT, false);
		tick.execute();
		tick.dispose();
	}
}
