package net.driftingsouls.ds2.server.modules.schiffplugins;

import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.interaction.DialogService;
import net.driftingsouls.ds2.server.campaign.interaction.Interaction;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.pipeline.controllers.Action;
import net.driftingsouls.ds2.server.framework.pipeline.controllers.ActionType;
import net.driftingsouls.ds2.server.framework.templates.TemplateEngine;
import net.driftingsouls.ds2.server.ships.Ship;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

/**
 * Campaign dialogs in the ship view: the contacts in the sector and star system that offer an
 * interaction, and the dialog the player is in.
 */
@Component
public class CampaignDialog implements SchiffPlugin
{
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
	public void output(Parameters caller)
	{
		String pluginid = caller.pluginId;
		Ship ship = caller.ship;
		TemplateEngine t = caller.t;
		User user = (User) ContextMap.getContext().getActiveUser();
		DialogService service = new DialogService(ContextMap.getContext().getEM());

		Optional<DialogService.View> view = service.currentView(user, ship);
		List<DialogService.Offer> offers = view.isPresent() ? List.of() : service.findOffers(user, ship);
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
				"campaigndialog.offers", offersHtml(pluginid, ship, offers));

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

	private static String offersHtml(String pluginid, Ship ship, List<DialogService.Offer> offers)
	{
		StringBuilder html = new StringBuilder();
		for (DialogService.Offer offer : offers)
		{
			html.append("<b>").append(escapeHtml4(offer.name)).append("</b> <span class=\"smallfont\">(")
					.append(escapeHtml4(offer.type.getLabel())).append(")</span>: ");
			for (Interaction interaction : offer.interactions)
			{
				html.append("<a class=\"forschinfo\" href=\"./ds?module=schiff&amp;ship=").append(ship.getId())
						.append("&amp;action=plugin&amp;plugin=").append(pluginid)
						.append("&amp;").append(pluginid).append("_ops[op]=start")
						.append("&amp;").append(pluginid).append("_ops[interaction]=").append(interaction.id)
						.append("\">").append(escapeHtml4(interaction.label)).append("</a> ");
			}
			html.append("<br />");
		}
		return html.toString();
	}
}
