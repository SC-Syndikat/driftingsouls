package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.WellKnownAdminPermission;
import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.campaign.GameMasterRestriction;
import net.driftingsouls.ds2.server.campaign.dialog.TrustService;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.pipeline.Request;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static net.driftingsouls.ds2.server.modules.admin.CampaignAdminHtml.*;

/**
 * The players' trust towards the campaign's NPCs: overview, journal, and game master corrections.
 */
@AdminMenuEntry(category = "Kampagne", name = "Vertrauen", permission = WellKnownAdminPermission.CAMPAIGN_TRUST)
public class CampaignTrust implements AdminPlugin
{
	@Override
	public void output(StringBuilder echo)
	{
		Context context = ContextMap.getContext();
		EntityManager em = context.getEM();
		Request request = context.getRequest();
		User gamemaster = (User) context.getActiveUser();
		GameMasterRestriction restriction = GameMasterRestriction.forActiveUser();
		TrustService trust = new TrustService(em);

		Optional<Campaign> campaign = new CampaignService(em).findOpenCampaign();
		if (campaign.isEmpty())
		{
			noOpenCampaign(echo);
			return;
		}
		Set<Integer> participants = new CampaignService(em).getParticipantIds(campaign.get());
		List<User> npcs = new ArrayList<>();
		List<User> players = new ArrayList<>();
		for (int id : new TreeSet<>(participants))
		{
			User user = em.find(User.class, id);
			if (user != null)
			{
				(user.isNPC() ? npcs : players).add(user);
			}
		}

		int npcId = request.getParameterInt("npc");
		int userId = request.getParameterInt("user");
		try
		{
			switch (request.getParameterString("cpaction"))
			{
				case "adjust":
				{
					checkNpc(restriction, npcs, npcId);
					int delta = request.getParameterInt("delta");
					trust.record(npcId, userId, delta, reason(request), TrustService.Kind.GM_CORRECTION, campaign.get().getId(), null, gamemaster.getId());
					message(echo, "Vertrauen um " + delta + " angepasst.");
					break;
				}
				case "correct":
				{
					checkNpc(restriction, npcs, npcId);
					Double target = optionalDouble(request, "target");
					int delta = trust.correctTo(npcId, userId, target == null ? 0 : target, reason(request), campaign.get().getId(), gamemaster.getId());
					message(echo, "Vertrauen auf " + (target == null ? 0 : target) + " gesetzt (Korrektur " + delta + ").");
					break;
				}
				case "factor":
					checkNpc(restriction, npcs, npcId);
					trust.setAllianceFactor(npcId, optionalDouble(request, "factor"));
					message(echo, "Allianzfaktor gespeichert.");
					break;
				default:
					break;
			}
		}
		catch (CampaignException e)
		{
			error(echo, e.getMessage());
		}

		matrix(echo, trust, npcs, players);
		if (npcId != 0 && userId != 0)
		{
			journal(echo, em, trust, npcId, userId);
		}
		factors(echo, trust, npcs);
	}

	private static String reason(Request request)
	{
		String reason = request.getParameterString("reason").trim();
		if (reason.isEmpty())
		{
			throw new CampaignException("Bitte einen Grund angeben.");
		}
		return reason;
	}

	private static void checkNpc(GameMasterRestriction restriction, List<User> npcs, int npcId)
	{
		boolean participating = npcs.stream().anyMatch(n -> n.getId() == npcId);
		if (!participating || (restriction.applies() && !restriction.getMasterLoginIds().contains(npcId)))
		{
			throw new CampaignException("Dieser NPC nimmt nicht an der Kampagne teil.");
		}
	}

	private void matrix(StringBuilder echo, TrustService trust, List<User> npcs, List<User> players)
	{
		echo.append("<div class='gfxbox' style='width:900px'><h3>Vertrauen</h3>");
		if (npcs.isEmpty())
		{
			echo.append("Es nimmt kein NPC-Account an der Kampagne teil.</div><br />");
			return;
		}
		echo.append("<p class='smallfont'>Gesamt = eigene Ereignisse + Allianzfaktor × Ereignisse der Allianzmitglieder. Anklicken für Journal und Korrektur.</p>");
		echo.append("<table class='noBorderX' cellpadding='3'><tr><td class='noBorderX'>Spieler</td>");
		for (User npc : npcs)
		{
			echo.append("<td class='noBorderX'>").append(esc(npc.getPlainname())).append("</td>");
		}
		echo.append("</tr>");
		for (User player : players)
		{
			echo.append("<tr><td class='noBorderX'>").append(esc(player.getPlainname())).append(" (").append(player.getId()).append(")</td>");
			for (User npc : npcs)
			{
				TrustService.Trust t = trust.trust(player.getId(), npc.getId());
				echo.append("<td class='noBorderX'><a class='forschinfo' href='").append(pluginUrl(getClass())).append("&amp;npc=").append(npc.getId())
						.append("&amp;user=").append(player.getId()).append("'>").append(esc(format(t.getTotal()))).append("</a>")
						.append(" <span class='smallfont'>(").append(t.own).append(" + ").append(esc(format(t.getAllianceShare()))).append(")</span></td>");
			}
			echo.append("</tr>");
		}
		echo.append("</table></div><br />");
	}

	private void journal(StringBuilder echo, EntityManager em, TrustService trust, int npcId, int userId)
	{
		User npc = em.find(User.class, npcId);
		User player = em.find(User.class, userId);
		if (npc == null || player == null)
		{
			return;
		}
		TrustService.Trust t = trust.trust(userId, npcId);
		echo.append("<div class='gfxbox' style='width:900px'><h3>").append(esc(player.getPlainname())).append(" → ").append(esc(npc.getPlainname())).append("</h3>");
		echo.append("Eigen ").append(t.own).append(", Allianzmitglieder ").append(t.alliance).append(" × ").append(esc(format(t.allianceFactor)))
				.append(" = ").append(esc(format(t.getAllianceShare()))).append(", <b>gesamt ").append(esc(format(t.getTotal()))).append("</b><br /><br />");

		echo.append("<form action='./ds' method='post' style='display:inline'>");
		hiddenFields(echo, getClass(), "adjust");
		hidden(echo, "npc", npcId);
		hidden(echo, "user", userId);
		echo.append("Anpassen um <input type='text' name='delta' size='5' /> Grund <input type='text' name='reason' size='30' /> <input type='submit' value='anpassen' /></form><br />");
		echo.append("<form action='./ds' method='post' style='display:inline'>");
		hiddenFields(echo, getClass(), "correct");
		hidden(echo, "npc", npcId);
		hidden(echo, "user", userId);
		echo.append("Gesamt setzen auf <input type='text' name='target' size='5' value='0' /> Grund <input type='text' name='reason' size='30' value='Zurückgesetzt' /> ")
				.append("<input type='submit' value='setzen / zurücksetzen' /></form>");
		echo.append("<p class='smallfont'>Korrekturen wirken nur für diesen Spieler, nicht über den Allianzanteil für seine Allianzmitglieder.</p>");

		echo.append("<table class='noBorderX' cellpadding='2'><tr><td class='noBorderX'>Zeit</td><td class='noBorderX'>Änderung</td>")
				.append("<td class='noBorderX'>Art</td><td class='noBorderX'>Grund</td><td class='noBorderX'>durch</td></tr>");
		for (TrustService.Event event : trust.journal(npcId, userId))
		{
			echo.append("<tr><td class='noBorderX'>").append(esc(Common.date("d.m.Y H:i", event.createdAt))).append("</td><td class='noBorderX'>")
					.append(event.delta >= 0 ? "+" : "").append(event.delta).append("</td><td class='noBorderX'>")
					.append(event.kind == TrustService.Kind.GM_CORRECTION ? "Korrektur" : "Aktion").append("</td><td class='noBorderX'>")
					.append(esc(event.reason)).append("</td><td class='noBorderX'>").append(event.createdBy == null ? "" : event.createdBy).append("</td></tr>");
		}
		echo.append("</table></div><br />");
	}

	private void factors(StringBuilder echo, TrustService trust, List<User> npcs)
	{
		if (npcs.isEmpty())
		{
			return;
		}
		echo.append("<div class='gfxbox' style='width:900px'><h3>Allianzfaktor je NPC</h3>");
		for (User npc : npcs)
		{
			echo.append("<form action='./ds' method='post'>");
			hiddenFields(echo, getClass(), "factor");
			hidden(echo, "npc", npc.getId());
			echo.append(esc(npc.getPlainname())).append(": <input type='text' name='factor' size='5' value='").append(esc(format(trust.allianceFactor(npc.getId()))))
					.append("' /> <input type='submit' value='speichern' /> <span class='smallfont'>(leer = globaler Standard)</span></form>");
		}
		echo.append("</div>");
	}

	private static String format(double value)
	{
		return value == Math.rint(value) ? Long.toString((long) value) : String.format(java.util.Locale.GERMANY, "%.2f", value);
	}
}
