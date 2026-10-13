package net.driftingsouls.ds2.server.modules.schiffplugins;

import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignMapService;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.campaign.GameMasterPermissionResolver;
import net.driftingsouls.ds2.server.campaign.dialog.ObjectType;
import net.driftingsouls.ds2.server.campaign.interaction.DialogService;
import net.driftingsouls.ds2.server.campaign.interaction.Interaction;
import net.driftingsouls.ds2.server.campaign.interaction.InteractionRepository;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.BasicUser;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.pipeline.controllers.Action;
import net.driftingsouls.ds2.server.framework.pipeline.controllers.ActionType;
import net.driftingsouls.ds2.server.framework.templates.TemplateEngine;
import net.driftingsouls.ds2.server.modules.admin.CampaignInteractions;
import net.driftingsouls.ds2.server.ships.Ship;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

/**
 * Campaign dialogs in the ship view.
 *
 * <p>Ships and bases offering an interaction get a contact icon in the sensor list, as trading posts
 * do ({@link #contactLinks}). This plugin shows what the icon leads to: the choice between several
 * interactions of a contact, the dialog in progress, and the signals of the star system itself.
 */
@Component
public class CampaignDialog implements SchiffPlugin
{
	/** The id {@code SchiffController} registers this plugin under. */
	public static final String PLUGIN_ID = "campaigndialog";

	@Action(ActionType.DEFAULT)
	public String action(Parameters caller, String op, int interaction, int session, int option)
	{
		User user = (User) ContextMap.getContext().getActiveUser();
		DialogService service = new DialogService(ContextMap.getContext().getEM());
		try
		{
			switch (op)
			{
				case "start":
					service.start(user, caller.ship, interaction);
					return "";
				case "choose":
				{
					List<String> outcome = service.choose(user, caller.ship, session, option);
					StringBuilder html = new StringBuilder();
					for (String line : outcome)
					{
						html.append("<span style=\"color:#ffcc00\">").append(escapeHtml4(line)).append("</span><br />");
					}
					return html.toString();
				}
				case "quit":
					service.quit(user, session);
					return "";
				default:
					return "";
			}
		}
		catch (CampaignException e)
		{
			return "<span style=\"color:red\">" + escapeHtml4(e.getMessage()) + "</span>";
		}
	}

	@Action(ActionType.DEFAULT)
	public void output(Parameters caller, String contact)
	{
		String pluginid = caller.pluginId;
		Ship ship = caller.ship;
		TemplateEngine t = caller.t;
		User user = (User) ContextMap.getContext().getActiveUser();
		DialogService service = new DialogService(ContextMap.getContext().getEM());

		Optional<DialogService.View> view = service.currentView(user, ship);
		String offers = "";
		if (view.isEmpty())
		{
			// Without a dialog in progress: the contact the player picked in the sensor list, and the
			// signals of the star system, which has no entry of its own there
			List<DialogService.Offer> all = service.findOffers(user, ship);
			StringBuilder html = new StringBuilder();
			for (DialogService.Offer offer : all)
			{
				if (offer.type == ObjectType.STAR_SYSTEM || (offer.type + ":" + offer.objectId).equals(contact))
				{
					appendOffer(html, ship, offer);
				}
			}
			offers = html.toString();
		}
		String gamemaster = gamemasterHtml(ship);
		if (view.isEmpty() && offers.isEmpty() && gamemaster.isEmpty())
		{
			// The plugin list shares one output variable; leave nothing behind for the next plugin
			t.setVar(caller.target, "");
			return;
		}

		t.setFile("_PLUGIN_" + pluginid, "schiff.campaigndialog.html");
		t.setVar("global.pluginid", pluginid,
				"ship.id", ship.getId(),
				"campaigndialog.active", view.isPresent(),
				"campaigndialog.offers", offers,
				"campaigndialog.gamemaster", gamemaster);

		t.setBlock("_PLUGIN_" + pluginid, "campaigndialog.option.listitem", "campaigndialog.option.list");
		if (view.isPresent())
		{
			DialogService.View v = view.get();
			t.setVar("campaigndialog.session", v.sessionId,
					"campaigndialog.label", escapeHtml4(v.interactionLabel),
					"campaigndialog.speaker", escapeHtml4(v.speaker),
					"campaigndialog.image", escapeHtml4(v.image),
					"campaigndialog.text", v.textHtml);
			for (DialogService.OptionView option : v.options)
			{
				t.setVar("campaigndialog.option.id", option.id,
						"campaigndialog.option.text", escapeHtml4(option.text),
						"campaigndialog.option.available", option.available);
				t.parse("campaigndialog.option.list", "campaigndialog.option.listitem", true);
			}
		}

		t.parse(caller.target, "_PLUGIN_" + pluginid);
	}

	/**
	 * Links for the contact icon in the sensor list, keyed {@code SHIP:<id>} or {@code BASE:<id>}. A
	 * contact with one interaction starts it directly; with several the link opens the choice.
	 */
	public static Map<String, String> contactLinks(User user, Ship ship)
	{
		Map<String, String> links = new HashMap<>();
		for (DialogService.Offer offer : new DialogService(ContextMap.getContext().getEM()).findOffers(user, ship))
		{
			if (offer.type == ObjectType.STAR_SYSTEM)
			{
				continue;
			}
			String key = offer.type + ":" + offer.objectId;
			String url = offer.interactions.size() == 1
					? startUrl(ship, offer.interactions.get(0))
					: "./ds?module=schiff&amp;ship=" + ship.getId() + "&amp;" + PLUGIN_ID + "_ops%5Bcontact%5D=" + key;
			links.put(key, url);
		}
		return links;
	}

	/**
	 * For a game master or admin: the interactions of the ship shown, and a way to add one. Empty for
	 * everyone else.
	 */
	private static String gamemasterHtml(Ship ship)
	{
		BasicUser viewer = ContextMap.getContext().getActiveUser();
		boolean gamemaster = viewer != null && (viewer.isAdmin()
				|| GameMasterPermissionResolver.isGameMaster(viewer)
				|| GameMasterPermissionResolver.isGameMaster(viewer.getAttachedUser()));
		if (!gamemaster)
		{
			return "";
		}
		StringBuilder html = new StringBuilder("<span class=\"smallfont\">GameMaster – Interaktionen dieses Schiffs: ");
		List<Interaction> interactions = new InteractionRepository().findFor(ObjectType.SHIP, ship.getId());
		if (interactions.isEmpty())
		{
			html.append("keine");
		}
		for (Interaction interaction : interactions)
		{
			html.append("<a class=\"forschinfo\" href=\"./ds?module=admin&amp;namedplugin=").append(CampaignInteractions.class.getName())
					.append("&amp;interaction=").append(interaction.id).append("\">").append(escapeHtml4(interaction.label)).append("</a>")
					.append(interaction.active ? "" : " (inaktiv)").append(", ");
		}
		html.append(" <a class=\"forschinfo\" href=\"").append(CampaignInteractions.objectUrl(ObjectType.SHIP, ship.getId()))
				.append("\">+ Interaktion hinzufügen / verwalten</a></span><br />");
		if (!interactions.isEmpty() && !inCampaignSystem(ship))
		{
			html.append("<span class=\"smallfont\" style=\"color:orange\">Das Schiff steht außerhalb der Kampagnensysteme. ")
					.append("Spieler sehen seine Interaktionen erst, wenn es in einem Kampagnensystem steht.</span><br />");
		}
		html.append("<br />");
		return html.toString();
	}

	private static boolean inCampaignSystem(Ship ship)
	{
		var em = ContextMap.getContext().getEM();
		Optional<Campaign> campaign = new CampaignService(em).findOpenCampaign();
		return campaign.isPresent() && new CampaignMapService(em).getSystemIds(campaign.get()).contains(ship.getSystem());
	}

	private static void appendOffer(StringBuilder html, Ship ship, DialogService.Offer offer)
	{
		html.append("<b>").append(escapeHtml4(offer.name)).append("</b> <span class=\"smallfont\">(")
				.append(escapeHtml4(offer.type.getLabel())).append(")</span>: ");
		for (Interaction interaction : offer.interactions)
		{
			html.append("<a class=\"forschinfo\" href=\"").append(startUrl(ship, interaction)).append("\">")
					.append(escapeHtml4(interaction.label)).append("</a> ");
		}
		html.append("<br />");
	}

	private static String startUrl(Ship ship, Interaction interaction)
	{
		return "./ds?module=schiff&amp;ship=" + ship.getId() + "&amp;action=plugin&amp;plugin=" + PLUGIN_ID
				+ "&amp;" + PLUGIN_ID + "_ops%5Bop%5D=start&amp;" + PLUGIN_ID + "_ops%5Binteraction%5D=" + interaction.id;
	}
}
