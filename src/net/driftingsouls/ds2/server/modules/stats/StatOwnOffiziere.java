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
package net.driftingsouls.ds2.server.modules.stats;

import net.driftingsouls.ds2.server.Offizier;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.User;
import net.driftingsouls.ds2.server.framework.db.Database;
import net.driftingsouls.ds2.server.framework.db.SQLQuery;
import net.driftingsouls.ds2.server.modules.StatsController;

/**
 * Zeigt die eigenen Offiziere und deren Aufenthaltsort
 * @author Christopher Jung
 *
 */
public class StatOwnOffiziere implements Statistic {

	public void show(StatsController contr, int size) {
		Context context = ContextMap.getContext();
		User user = context.getActiveUser();
		Database db = context.getDatabase();

		StringBuffer echo = context.getResponse().getContent();
	
		SQLQuery offi = db.query("SELECT id,name,rang,ing,nav,waf,sec,com,spec,dest ",
					"FROM offiziere ",
					"WHERE userid=",user.getID()," ",
					"ORDER BY ing+nav+waf+sec+com DESC");
					
		if( offi.numRows() == 0 ) {
			echo.append("<div align=\"center\">Sie verf&uuml;gen &uuml;ber keine Offiziere</div>\n");
			
			return;	
		}
					
		echo.append("<table class=\"noBorderX\" cellspacing=\"2\" cellpadding=\"3\">\n");
		echo.append("<tr><td class=\"noBorderX\" align=\"left\" colspan=\"2\">Offizier</td><td class=\"noBorderX\">Auf</td><td class=\"noBorderX\">Technik</td><td class=\"noBorderX\">Navigation</td><td class=\"noBorderX\">Waffen</td><td class=\"noBorderX\">Sicherheit</td><td class=\"noBorderX\">Kommando</td><td class=\"noBorderX\">Spezial</td></tr>\n");
		
		while( offi.next() ) {
			Offizier offizier = new Offizier(offi.getRow());
		   	echo.append("<tr>\n");
			echo.append("<td class=\"noBorderX\"><img src=\""+offizier.getPicture()+"\" alt=\"Rang "+offizier.getRang()+"\" /> <a class=\"forschinfo\" href=\""+Common.buildUrl(context, "default", "module", "choff", "off", offizier.getID())+"\">"+Common._title(offizier.getName())+"</a> ("+offizier.getID()+")</td>\n");
			echo.append("<td class=\"noBorderX\">&nbsp;</td>\n");
	
			String[] dest = offizier.getDest();
	
			if( dest[0].equals("s") ) {
				String shipname = db.first("SELECT name FROM ships WHERE id>0 AND id=",dest[1]).getString("name");
				echo.append("<td class=\"noBorderX\"><a class=\"forschinfo\" href=\""+Common.buildUrl(context, "default", "module", "schiff", "ship", dest[1])+"\">"+shipname+"</a></td>\n");
			}
			else {
				String basename = db.first("SELECT name FROM bases WHERE id=",dest[1]).getString("name");
				echo.append("<td class=\"noBorderX\"><a class=\"forschinfo\" href=\""+Common.buildUrl(context, "default", "module", "base", "col", dest[1])+"\">"+basename+"</a> "+(dest[0].equals("t") ? "(A)" : "")+"</td>\n");
			}
			
			echo.append("<td class=\"noBorderX\">"+offizier.getAbility(Offizier.Ability.ING)+"</td>\n");
			echo.append("<td class=\"noBorderX\">"+offizier.getAbility(Offizier.Ability.NAV)+"</td>\n");
			echo.append("<td class=\"noBorderX\">"+offizier.getAbility(Offizier.Ability.WAF)+"</td>\n");
			echo.append("<td class=\"noBorderX\">"+offizier.getAbility(Offizier.Ability.SEC)+"</td>\n");
			echo.append("<td class=\"noBorderX\">"+offizier.getAbility(Offizier.Ability.COM)+"</td>\n");
			echo.append("<td class=\"noBorderX\">"+offizier.getSpecial().getName()+"</td>\n");
			echo.append("</tr>\n");
		}
		offi.free();
	
		echo.append("</table><div><br /><br /></div>\n");
	}

}
