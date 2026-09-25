package net.driftingsouls.ds2.server.modules.schiffplugins;

import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.dialog.ObjectType;
import net.driftingsouls.ds2.server.campaign.interaction.DialogService;
import net.driftingsouls.ds2.server.campaign.interaction.Interaction;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.pipeline.controllers.Action;
import net.driftingsouls.ds2.server.framework.pipeline.controllers.ActionType;
import net.driftingsouls.ds2.server.framework.templates.TemplateEngine;
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
					String outcome = service.choose(user, caller.ship, session, option);
					return outcome.isEmpty() ? "" : "<span style=\"color:#ffcc00\">" + escapeHtml4(outcome) + "</span>";
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
		if (view.isEmpty() && offers.isEmpty())
		{
			// The plugin list shares one output variable; leave nothing behind for the next plugin
			t.setVar(caller.target, "");
			return;
		}

		t.setFile("_PLUGIN_" + pluginid, "schiff.campaigndialog.html");
		t.setVar("global.pluginid", pluginid,
				"ship.id", ship.getId(),
				"campaigndialog.active", view.isPresent(),
				"campaigndialog.offers", offers);

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
					: "./ds?module=schiff&amp;ship=" + ship.getId() + "&amp;" + PLUGIN_ID + "_ops[contact]=" + key;
			links.put(key, url);
		}
		return links;
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
				+ "&amp;" + PLUGIN_ID + "_ops[op]=start&amp;" + PLUGIN_ID + "_ops[interaction]=" + interaction.id;
	}
}
