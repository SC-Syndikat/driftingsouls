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
package net.driftingsouls.ds2.server.ships.autopilot;

import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.templates.TemplateEngine;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Repraesentiert einen Autopiloten, also eine Fluganweisung fuer NPCs, in DS.
 *
 */
public abstract class AutopilotData implements Cloneable {
	private static final Log log = LogFactory.getLog(AutopilotData.class);

  /**
	 * Gibt die Autopilotendaten via TemplateEngine aus.
	 * @param t Das TemplateEngine
	 * @param templateblock Der Name des betreffenden TemplateBlocks
	 * @param templateitem Der Name eines Items des TemplateBlocks
	 */
	public void echoAutopilotDataList( TemplateEngine t, String templateblock, String templateitem, User user ) {

		if(isEmpty())
		{
			return;
		}

		t.setVar(templateblock,"");



		for( AutoPilotDataEntry aEntry : entries ) {
			if( aEntry.getUser().getId() != user.getId() )
			{
				continue;
			}
      if(aEntry.getShip().getOwner().getId() != user.getId())
      {
        continue;
      }
		  int x = aEntry.getX();
      int y = aEntry.getY();
      int heat = aEntry.getHeat();

			t.setVar(	"schiff.autopilot.dest.x", 			x,
						"schiff.autopilot.dest.y",		y,
						"schiff.autopilot.heat",		heat );

			t.parse(templateblock,templateitem,true);
		}
	}
}