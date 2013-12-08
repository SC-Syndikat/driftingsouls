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
package net.driftingsouls.ds2.server.modules;

import net.driftingsouls.ds2.server.bases.Base;
import net.driftingsouls.ds2.server.entities.GuiHelpText;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.JSONUtils;
import net.driftingsouls.ds2.server.framework.pipeline.Module;
import net.driftingsouls.ds2.server.framework.pipeline.generators.Action;
import net.driftingsouls.ds2.server.framework.pipeline.generators.ActionType;
import net.driftingsouls.ds2.server.framework.pipeline.generators.TemplateController;
import net.driftingsouls.ds2.server.framework.templates.TemplateEngine;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.List;

/**
 * Das Hauptframe von DS.
 *
 * @author Christopher Jung
 */
@Module(name = "main")
public class MainController extends TemplateController
{
	private static final String SCRIPT_FORUM = "http://forum.drifting-souls.net/phpbb3/";

	/**
	 * Konstruktor.
	 *
	 * @param context Der zu verwendende Kontext
	 */
	public MainController(Context context)
	{
		super(context);

		setDisableDebugOutput(true);
	}

	/**
	 * Persistiert die Notizen eines Benutzers.
	 *
	 * @return Die JSON-Nachricht ueber den Erfolg des speicherns
	 */
	@Action(ActionType.AJAX)
	public JSONObject speicherNotizen(String notizen)
	{
		User user = (User) getUser();
		user.setUserValue("TBLORDER/main/notizen", notizen.trim());

		return JSONUtils.success("gespeichert");
	}

	/**
	 * Prueft, ob der Spieler eine neue PM hat, welche noch nicht gelesen wurde.
	 *
	 * @throws IOException
	 */
	@Action(ActionType.AJAX)
	public void hasNewPmAjaxAct() throws IOException
	{
		User user = (User) this.getUser();
		org.hibernate.Session db = getDB();

		int pmcount = ((Number) db.createQuery("select count(*) from PM where empfaenger= :user and gelesen=0")
				.setEntity("user", user)
				.iterate().next()).intValue();
		if (pmcount > 0)
		{
			getResponse().getWriter().append("1");
		}
		else
		{
			getResponse().getWriter().append("0");
		}
	}

	/**
	 * Gibt zu einer Seite den Hilfetext zurueck.
	 *
	 * @throws IOException
	 */
	@Action(ActionType.AJAX)
	public void getHelpText(GuiHelpText page) throws IOException
	{
		if (page != null)
		{
			getResponse().getWriter().append(Common._text(page.getText()));
		}
	}

	/**
	 * Generiert das Hauptframe.
	 */
	@Override
	@Action(ActionType.DEFAULT)
	public void defaultAction()
	{
		User user = (User) getUser();
		TemplateEngine t = getTemplateEngine();
		org.hibernate.Session db = getDB();

		t.setVar("SCRIPT_FORUM", SCRIPT_FORUM);

		t.setVar(
				"user.npc", user.hasFlag(User.FLAG_ORDER_MENU),
				"user.adminSichtbar", hasPermission("admin", "sichtbar"),
				"admin.showconsole", user.getUserValue("TBLORDER/admin/show_cmdline"),
				"user.notizen", user.getUserValue("TBLORDER/main/notizen"));

		t.setBlock("_MAIN", "bases.listitem", "bases.list");

		List<?> baseList = db.createQuery("from Base where owner= :user order by system,x,y")
				.setEntity("user", user)
				.list();
		for (Object aBaseList : baseList)
		{
			Base base = (Base) aBaseList;

			t.setVar(
					"base.id", base.getId(),
					"base.name", base.getName(),
					"base.klasse", base.getKlasse(),
					"base.location", base.getLocation());

			t.parse("bases.list", "bases.listitem", true);
		}
	}
}
