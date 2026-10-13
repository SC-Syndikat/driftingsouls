package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.campaign.GameMasterPermissionResolver;
import net.driftingsouls.ds2.server.framework.BasicUser;

import java.util.List;

import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

/**
 * The game master's own menu: everything for preparing a campaign on one page. A game master who is
 * not an admin sees this instead of the admin menu, both on the game master page and around the admin
 * plugins it links to.
 */
public final class GameMasterMenu
{
	private GameMasterMenu()
	{
		// static only
	}

	public static class Entry
	{
		public final String label;
		public final String url;

		Entry(String label, Class<?> plugin)
		{
			this.label = label;
			this.url = "./ds?module=admin&namedplugin=" + plugin.getName();
		}
	}

	public static final List<Entry> ENTRIES = List.of(
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
		return user != null && !user.isAdmin()
				&& (GameMasterPermissionResolver.isGameMaster(user) || GameMasterPermissionResolver.isGameMaster(user.getAttachedUser()));
	}

	/**
	 * The menu as it stands in place of the admin menu's tree.
	 */
	public static void render(Appendable out, String activePlugin)
	{
		try
		{
			out.append("<div class='gfxbox treemenu'><ul style='display:block'>");
			out.append("<li><a class='forschinfo' href='./gamemaster'>&laquo; GameMaster-Seite</a></li>");
			out.append("<li><p>Kampagne vorbereiten</p><ul style='display:block'>");
			for (Entry entry : ENTRIES)
			{
				boolean active = entry.url.endsWith("=" + activePlugin);
				out.append("<li><a class='forschinfo").append(active ? " active" : "").append("' href='").append(escapeHtml4(entry.url)).append("'>")
						.append(escapeHtml4(entry.label)).append("</a></li>");
			}
			out.append("</ul></li>");
			out.append("<li><a class='forschinfo' href='./gamemaster?show=start'>Kampagne starten</a></li>");
			out.append("<li><a class='forschinfo' href='./gamemaster?show=tick'>Kampagnentick</a></li>");
			out.append("<li><a class='forschinfo' href='./gamemaster?show=end'>Kampagne beenden</a></li>");
			out.append("</ul></div>");
		}
		catch (java.io.IOException e)
		{
			throw new IllegalStateException(e);
		}
	}
}
