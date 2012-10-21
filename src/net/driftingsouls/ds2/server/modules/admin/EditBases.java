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
package net.driftingsouls.ds2.server.modules.admin;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.StringUtils;

import net.driftingsouls.ds2.server.bases.AutoGTUAction;
import net.driftingsouls.ds2.server.bases.Base;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.cargo.Resources;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.modules.AdminController;

/**
 * Aktualisierungstool fuer die Werte eines Spielers.
 *
 * @author Sebastian Gift
 */
@AdminMenuEntry(category = "Asteroiden", name = "Basis editieren")
public class EditBases implements AdminPlugin
{
	@Override
	public void output(AdminController controller, String page, int action) throws IOException
	{
		Context context = ContextMap.getContext();
		Writer echo = context.getResponse().getWriter();
		org.hibernate.Session db = context.getDB();

		int baseid = context.getRequest().getParameterInt("baseid");

		// Update values?
		boolean update = context.getRequest().getParameterString("change").equals("Aktualisieren");

		echo.append("<div class='gfxbox' style='width:390px'>");
		echo.append("<form action=\"./ds\" method=\"post\">");
		echo.append("<input type=\"hidden\" name=\"page\" value=\"" + page + "\" />\n");
		echo.append("<input type=\"hidden\" name=\"act\" value=\"" + action + "\" />\n");
		echo.append("<input type=\"hidden\" name=\"module\" value=\"admin\" />\n");
		echo.append("Basis: <input type=\"text\" name=\"baseid\" value=\""+ baseid +"\" />\n");
		echo.append("<input type=\"submit\" name=\"choose\" value=\"Ok\" />");
		echo.append("</form>");
		echo.append("</div>");

		if(update && baseid != 0)
		{
			Base base = (Base)db.get(Base.class, baseid);

			base.setName(context.getRequest().getParameterString("name"));
			User newowner = (User)db.get(User.class, context.getRequest().getParameterInt("owner"));
			base.setOwner(newowner);
			base.setX(context.getRequest().getParameterInt("x"));
			base.setY(context.getRequest().getParameterInt("y"));
			base.setSystem(context.getRequest().getParameterInt("system"));
			base.setEnergy(context.getRequest().getParameterInt("energie"));
			base.setMaxEnergy(context.getRequest().getParameterInt("maxenergie"));
			base.setCargo(new Cargo(Cargo.Type.AUTO, context.getRequest().getParameterString("cargo")));
			base.setMaxCargo(context.getRequest().getParameterInt("maxcargo"));
			base.setCore(context.getRequest().getParameterInt("core"));
			base.setKlasse(context.getRequest().getParameterInt("klasse"));
			base.setWidth(context.getRequest().getParameterInt("width"));
			base.setHeight(context.getRequest().getParameterInt("height"));
			base.setMaxTiles(context.getRequest().getParameterInt("maxtiles"));
			base.setSize(context.getRequest().getParameterInt("size"));

			int size = base.getWidth()*base.getHeight();
			base.setTerrain(convertAndCapTileList(size, context.getRequest().getParameterString("terrain")));
			base.setBebauung(convertAndCapTileList(size, context.getRequest().getParameterString("bebauung")));
			base.setActive(convertAndCapTileList(size, context.getRequest().getParameterString("active")));
			base.setCoreActive(context.getRequest().getParameterString("coreactive").equals("true"));
			base.setSpawnableRess(context.getRequest().getParameterString("spawnableress"));
			base.setAvailableSpawnableRess(context.getRequest().getParameterString("availableress"));
			String[] autogtuacts = StringUtils.split(context.getRequest().getParameterString("autogtuacts"),";");
			List<AutoGTUAction> acts = new ArrayList<AutoGTUAction>();
			for( int i=0; i < autogtuacts.length; i++ )
			{
				String[] split = StringUtils.split(autogtuacts[i],":");

				acts.add(new AutoGTUAction(Resources.fromString(split[0]), Integer.parseInt(split[1]), Long.parseLong(split[2])) );
			}
			base.setAutoGTUActs(acts);

			echo.append("<p>Update abgeschlossen.</p>");
		}

		if(baseid != 0)
		{
			Base base = (Base)db.get(Base.class, baseid);

			if(base == null)
			{
				return;
			}

			echo.append("<div class='gfxbox' style='width:600px'>");
			echo.append("<form action=\"./ds\" method=\"post\">");
			echo.append("<input type=\"hidden\" name=\"page\" value=\"" + page + "\" />\n");
			echo.append("<input type=\"hidden\" name=\"act\" value=\"" + action + "\" />\n");
			echo.append("<input type=\"hidden\" name=\"module\" value=\"admin\" />\n");
			echo.append("<input type=\"hidden\" name=\"baseid\" value=\"" + baseid + "\" />\n");
			echo.append("<table width=\"100%\">");
			echo.append("<tr><td>Name: </td><td><input type=\"text\" size=\"40\" name=\"name\" value=\"" + base.getName() + "\"></td></tr>\n");
			echo.append("<tr><td>Besitzer: </td><td><input type=\"text\" size=\"40\" name=\"owner\" value=\"" + base.getOwner().getId() + "\"></td></tr>\n");
			echo.append("<tr><td>System: </td><td><input type=\"text\" size=\"40\" name=\"system\" value=\"" + base.getSystem() + "\"></td></tr>\n");
			echo.append("<tr><td>X-Koordinate: </td><td><input type=\"text\" size=\"40\" name=\"x\" value=\"" + base.getX() + "\"></td></tr>\n");
			echo.append("<tr><td>Y-Koordinate: </td><td><input type=\"text\" size=\"40\" name=\"y\" value=\"" + base.getY() + "\"></td></tr>\n");
			echo.append("<tr><td>Aktuelle Energie: </td><td><input type=\"text\" size=\"40\" name=\"energie\" value=\"" + base.getEnergy() + "\"></td></tr>\n");
			echo.append("<tr><td>Maximale Energie: </td><td><input type=\"text\" size=\"40\" name=\"maxenergie\" value=\"" + base.getMaxEnergy() + "\"></td></tr>\n");
			echo.append("<tr><td>Cargo: </td><td><input type=\"hidden\" size=\"40\" id=\"cargo\" name=\"cargo\" value=\"" + base.getCargo().toString() + "\"></td></tr>\n");
			echo.append("<tr><td>maximaler Cargo: </td><td><input type=\"text\" size=\"40\" name=\"maxcargo\" value=\"" + base.getMaxCargo() + "\"></td></tr>\n");
			echo.append("<tr><td>Core: </td><td><input type=\"text\" size=\"40\" name=\"core\" value=\"" + base.getCore() + "\"></td></tr>\n");
			echo.append("<tr><td>Klasse: </td><td><input type=\"text\" size=\"40\" name=\"klasse\" value=\"" + base.getKlasse() + "\"></td></tr>\n");
			echo.append("<tr><td>Breite: </td><td><input type=\"text\" size=\"40\" name=\"width\" value=\"" + base.getWidth() + "\"></td></tr>\n");
			echo.append("<tr><td>H&ouml;he: </td><td><input type=\"text\" size=\"40\" name=\"height\" value=\"" + base.getHeight() + "\"></td></tr>\n");
			echo.append("<tr><td>Feldergr&ouml;&szlig;e: </td><td><input type=\"text\" size=\"40\" name=\"maxtiles\" value=\"" + base.getMaxTiles() + "\"></td></tr>\n");
			echo.append("<tr><td>Gr&ouml;&szlig;e: </td><td><input type=\"text\" size=\"40\" name=\"size\" value=\"" + base.getSize() + "\"></td></tr>\n");
			echo.append("<tr><td>Terrain: </td><td><input type=\"text\" size=\"40\" name=\"terrain\" value=\"" + Common.implode("|", base.getTerrain()) + "\"></td></tr>\n");
			echo.append("<tr><td>Bebauung: </td><td><input type=\"text\" size=\"40\" name=\"bebauung\" value=\"" + Common.implode("|", base.getBebauung()) + "\"></td></tr>\n");
			echo.append("<tr><td>Active: </td><td><input type=\"text\" size=\"40\" name=\"active\" value=\"" + Common.implode("|", base.getActive()) + "\"></td></tr>\n");
			echo.append("<tr><td>Core-Aktiv: </td><td><input type=\"text\" size=\"40\" name=\"coreactive\" value=\"" + base.isCoreActive() + "\"></td></tr>\n");
			echo.append("<tr><td>Zum Spawn freigegebene Ressourcen: </td><td><input type=\"text\" size=\"40\" name=\"spawnableress\" value=\"" + base.getSpawnableRess() + "\"></td></tr>\n");
			echo.append("<tr><td>Aktuell verf&uuml;gbare Ressourcen: </td><td><input type=\"text\" size=\"40\" name=\"availableress\" value=\"" + base.getAvailableSpawnableRess() + "\"></td></tr>\n");
			echo.append("<tr><td>AutoGTUActs: </td><td><input type=\"text\" size=\"40\" name=\"autogtuacts\" value=\"" + Common.implode(";" , base.getAutoGTUActs()) + "\"></td></tr>\n");
			echo.append("<tr><td></td><td><input type=\"submit\" name=\"change\" value=\"Aktualisieren\"></td></tr>\n");
			echo.append("</table>");
			echo.append("<script type='text/javascript'>$(document).ready(function() {new CargoEditor('#cargo');});</script>");
			echo.append("</form>\n");
			echo.append("</div>");
		}
	}

	private Integer[] convertAndCapTileList(int max, String str)
	{
		Integer[] tiles = Common.explodeToInteger("|", str);
		if( tiles.length > max ) {
			Integer[] newTiles = new Integer[max];
			System.arraycopy(tiles, 0, newTiles, 0, max);
			return newTiles;
		}
		return tiles;
	}
}
