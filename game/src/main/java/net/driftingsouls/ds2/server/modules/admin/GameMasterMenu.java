package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignRepository;
import net.driftingsouls.ds2.server.campaign.GameMasterControl;
import net.driftingsouls.ds2.server.campaign.GameMasterPermissionResolver;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.BasicUser;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.pipeline.Request;
import net.driftingsouls.ds2.server.tick.CampaignTickStatus;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

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
	 * The switch between the game master's own account and the running campaign's NPCs, for the ship and base
	 * overviews; empty for everyone else.
	 *
	 * @param to Where to land after switching: {@code ships} or {@code bases}
	 */
	public static String controlToggle(String to)
	{
		BasicUser real = GameMasterControl.realUser();
		if (!GameMasterControl.mayControl(real))
		{
			return "";
		}
		List<User> npcs = GameMasterControl.controllableNpcs();
		if (npcs.isEmpty())
		{
			return "";
		}
		int active = ContextMap.getContext().getActiveUser().getId();
		StringBuilder html = new StringBuilder("<div class='gfxbox' style='margin-bottom:6px'><span class='smallfont'>GameMaster – steuern als: ");
		toggleLink(html, real.getId(), Common._title(real.getName()) + " (du)", active, to);
		for (User npc : npcs)
		{
			html.append(" · ");
			toggleLink(html, npc.getId(), escapeHtml4(npc.getPlainname()) + " (" + npc.getId() + ")", active, to);
		}
		html.append(GameMasterControl.isControlling() ? " – nur Schiffe und Basen in Kampagnensystemen" : "").append("</span></div>");
		return html.toString();
	}

	private static void toggleLink(StringBuilder html, int userId, String label, int active, String to)
	{
		if (userId == active)
		{
			html.append("<b>").append(label).append("</b>");
			return;
		}
		html.append("<a class='forschinfo' href='").append(escapeHtml4(controlUrl(userId, to))).append("'>").append(label).append("</a>");
	}

	/**
	 * @param to {@code ships}, {@code bases}, {@code ship:<id>}, {@code base:<id>} or {@code battle:<id>}
	 * @return The link that switches to the user and lands there
	 */
	public static String controlUrl(int userId, String to)
	{
		return "./gamemaster?action=control&user=" + userId + "&to=" + to;
	}

	private static void menuLink(Appendable out, String label, String url, boolean enabled) throws java.io.IOException
	{
		out.append("<li><img src='data/interface/admin/editor.png' /><a class='").append(enabled ? "forschinfo" : "error").append("' href='")
				.append(enabled ? url : "#").append("'>").append(label).append("</a></li>");
	}

	/**
	 * The running campaign's tick count and how far the last campaign tick has got, as menu entries.
	 * @return The entries, empty without a running campaign
	 */
	public static String tickInfo(Optional<Campaign> campaign)
	{
		if (campaign.isEmpty() || !campaign.get().isRunning())
		{
			return "";
		}
		StringBuilder html = new StringBuilder();
		html.append("<li class='smallfont'>Kampagnentick ").append(campaign.get().getTickCount()).append("</li>");
		CampaignTickStatus status = CampaignTickStatus.get();
		switch (status.getState())
		{
			case STARTED:
				html.append("<li class='smallfont' style='color:orange'>Tick gestartet um ").append(Common.date("H:i:s", status.getStartedAt()))
						.append(", wartet auf den Scheduler</li>");
				break;
			case RUNNING:
				html.append("<li class='smallfont' style='color:orange'>Tick läuft seit ").append(Common.date("H:i:s", status.getStartedAt()));
				if (status.getStep() != null)
				{
					html.append(": ").append(escapeHtml4(status.getStep()));
				}
				html.append("</li>");
				break;
			case FINISHED:
				html.append("<li class='smallfont' style='color:#33dd33'>Tick beendet um ").append(Common.date("H:i:s", status.getFinishedAt()))
						.append(" (Dauer ").append(status.getFinishedAt() - status.getStartedAt()).append(" s)</li>");
				break;
			default:
				break;
		}
		return html.toString();
	}

	/**
	 * The menu as it stands in place of the admin menu's tree.
	 */
	public static void render(Appendable out, String activePlugin, boolean withAdminMenu)
	{
		try
		{
			// The same menu as on the game master page, expanded
			Optional<Campaign> campaign = new CampaignRepository().findOpen();
			boolean running = campaign.isPresent() && campaign.get().isRunning();
			boolean preparing = campaign.isPresent() && campaign.get().isPreparing();
			out.append("<div class='gfxbox treemenu'><ul style='display:block'>");
			out.append("<li class='smallfont'><a class='forschinfo' href='./gamemaster'>")
					.append(campaign.map(c -> escapeHtml4(c.getName()) + (running ? " (läuft)" : " (in Vorbereitung)")).orElse("GameMaster-Seite"))
					.append("</a></li>");
			out.append(tickInfo(campaign));
			out.append("<li><img src='data/interface/admin/editor.png' /><span>Kampagne vorbereiten</span>")
					.append("<ul style='display:block; margin-left:14px; line-height:1.6'>");
			for (Entry entry : ENTRIES)
			{
				boolean active = entry.plugin.equals(activePlugin);
				out.append("<li><a class='forschinfo").append(active ? " active" : "").append("' href='").append(escapeHtml4(entry.url)).append("'>")
						.append(escapeHtml4(entry.label)).append("</a></li>");
			}
			out.append("</ul></li>");
			menuLink(out, "Kampagne starten", "./gamemaster?show=start", preparing);
			menuLink(out, "Kampagnentick", "./gamemaster?show=tick", running);
			menuLink(out, "Kampagne beenden", "./gamemaster?show=end", running);
			if (withAdminMenu)
			{
				out.append("<li><img src='data/interface/admin/editor.png' /><a class='forschinfo' href='./ds?module=admin&amp;")
						.append(PARAMETER).append("=0'>Zum Admin-Menü &raquo;</a></li>");
			}
			out.append("</ul></div>");
		}
		catch (java.io.IOException e)
		{
			throw new IllegalStateException(e);
		}
	}
}
