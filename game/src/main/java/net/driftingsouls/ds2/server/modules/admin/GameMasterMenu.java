package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.campaign.GameMasterPermissionResolver;
import net.driftingsouls.ds2.server.framework.BasicUser;
import net.driftingsouls.ds2.server.framework.pipeline.Request;

import java.io.Serializable;
import java.util.List;

import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

/**
 * The game master's own menu: everything for preparing a campaign on one page. A game master who is
 * not an admin always sees this instead of the admin menu, both on the game master page and around the
 * admin plugins it links to. An admin sees it after coming from the game master menu, until he goes
 * back to the admin menu.
 */
public final class GameMasterMenu
{
	/** Request parameter: 1 switches to this menu, 0 back to the admin menu. */
	public static final String PARAMETER = "gmnav";

	private GameMasterMenu()
	{
		// static only
	}

	public static class Entry
	{
		public final String label;
		public final String url;
		final String plugin;

		Entry(String label, Class<?> plugin)
		{
			this.label = label;
			this.plugin = plugin.getName();
			this.url = "./ds?module=admin&namedplugin=" + plugin.getName() + "&" + PARAMETER + "=1";
		}
	}

	/**
	 * Remembers in the session whether an admin navigates with this menu.
	 */
	public static class Navigation implements Serializable
	{
		private static final long serialVersionUID = 1L;

		boolean active;

		public Navigation()
		{
			// for the session
		}
	}

	public static final List<Entry> ENTRIES = List.of(
			new Entry("Spieler-Anfragen", CampaignRequests.class),
			new Entry("Teilnehmer", CampaignParticipants.class),
			new Entry("Systeme", CampaignSystems.class),
			new Entry("Sprungpunkte", CampaignJumpNodes.class),
			new Entry("Schiffe hinzufügen", AddShips.class),
			new Entry("Schiffe bearbeiten", EditShip.class),
			new Entry("Asteroiden / Nebel erstellen", CreateObjects.class),
			new Entry("Basen bearbeiten", EditBases.class),
			new Entry("Schiffsgruppen", CampaignShipGroups.class),
			new Entry("Dialoge", CampaignDialogs.class),
			new Entry("Interaktionen", CampaignInteractions.class),
			new Entry("Vertrauen", CampaignTrust.class),
			new Entry("Kampagnenvariablen", CampaignVariables.class),
			new Entry("Protokoll", CampaignLog.class),
			new Entry("NPC übernehmen (Masterlogin)", PlayerLoginSuper.class)
	);

	/**
	 * @return Whether the user is a game master without admin rights, who gets this menu instead of the
	 * admin menu. Under a master login the game master behind it counts.
	 */
	public static boolean appliesTo(BasicUser user)
	{
		return user != null && !user.isAdmin() && isGameMaster(user);
	}

	private static boolean isGameMaster(BasicUser user)
	{
		return GameMasterPermissionResolver.isGameMaster(user) || GameMasterPermissionResolver.isGameMaster(user.getAttachedUser());
	}

	/**
	 * Decides which menu goes around an admin plugin, and remembers an admin's switch to this menu.
	 *
	 * @param namedplugin The plugin shown; empty for the admin start page, which leaves this menu
	 * @return Whether to show this menu instead of the admin menu
	 */
	public static boolean shownFor(BasicUser user, Request request, String namedplugin)
	{
		if (appliesTo(user))
		{
			return true;
		}
		if (user == null || !(user.isAdmin() || isGameMaster(user)))
		{
			return false;
		}
		Navigation navigation = request.getFromSession(Navigation.class);
		if (navigation == null)
		{
			return false;
		}
		String parameter = request.getParameterString(PARAMETER);
		if ("1".equals(parameter))
		{
			navigation.active = true;
		}
		else if ("0".equals(parameter) || namedplugin == null || namedplugin.isEmpty())
		{
			navigation.active = false;
		}
		return navigation.active;
	}

	/**
	 * The menu as it stands in place of the admin menu's tree.
	 */
	public static void render(Appendable out, String activePlugin, boolean withAdminMenu)
	{
		try
		{
			out.append("<div class='gfxbox treemenu'><ul style='display:block'>");
			out.append("<li><a class='forschinfo' href='./gamemaster'>&laquo; GameMaster-Seite</a></li>");
			out.append("<li><p>Kampagne vorbereiten</p><ul style='display:block'>");
			for (Entry entry : ENTRIES)
			{
				boolean active = entry.plugin.equals(activePlugin);
				out.append("<li><a class='forschinfo").append(active ? " active" : "").append("' href='").append(escapeHtml4(entry.url)).append("'>")
						.append(escapeHtml4(entry.label)).append("</a></li>");
			}
			out.append("</ul></li>");
			out.append("<li><a class='forschinfo' href='./gamemaster?show=start'>Kampagne starten</a></li>");
			out.append("<li><a class='forschinfo' href='./gamemaster?show=tick'>Kampagnentick</a></li>");
			out.append("<li><a class='forschinfo' href='./gamemaster?show=end'>Kampagne beenden</a></li>");
			if (withAdminMenu)
			{
				out.append("<li><a class='forschinfo' href='./ds?module=admin&amp;").append(PARAMETER).append("=0'>Zum Admin-Menü &raquo;</a></li>");
			}
			out.append("</ul></div>");
		}
		catch (java.io.IOException e)
		{
			throw new IllegalStateException(e);
		}
	}
}
