package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.WellKnownAdminPermission;
import net.driftingsouls.ds2.server.WellKnownPermission;
import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.CampaignMapService;
import net.driftingsouls.ds2.server.campaign.CampaignObject;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.campaign.CampaignUserView;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static net.driftingsouls.ds2.server.modules.admin.CampaignAdminHtml.*;
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

/**
 * Prepares a campaign and maintains its participants: all eligible users on the left, participants on
 * the right, moved between the two lists.
 */
@AdminMenuEntry(category = "Kampagne", name = "Teilnehmer", permission = WellKnownAdminPermission.CAMPAIGN_PARTICIPANTS)
public class CampaignParticipants implements AdminPlugin
{
	@Override
	public void output(StringBuilder echo)
	{
		Context context = ContextMap.getContext();
		EntityManager em = context.getEM();
		User actor = (User) context.getActiveUser();
		CampaignService service = new CampaignService(em);
		String action = context.getRequest().getParameterString("cpaction");

		try
		{
			if ("prepare".equals(action))
			{
				service.prepare(context.getRequest().getParameterString("name"), actor);
				message(echo, "Kampagne angelegt. Wähle jetzt die Teilnehmer aus.");
			}
			else if ("save".equals(action))
			{
				save(echo, context, em, actor, service);
			}
		}
		catch (CampaignException e)
		{
			error(echo, e.getMessage());
		}

		Optional<Campaign> campaign = service.findOpenCampaign();
		if (campaign.isEmpty())
		{
			prepareForm(echo);
			return;
		}
		participantForm(echo, context, em, service, campaign.get());
	}

	private void save(StringBuilder echo, Context context, EntityManager em, User actor, CampaignService service)
	{
		Campaign campaign = service.findOpenCampaign()
				.orElseThrow(() -> new CampaignException("Es gibt keine Kampagne in Vorbereitung."));

		Set<Integer> desired = new HashSet<>();
		for (String value : context.getRequest().getParameterValues("participant"))
		{
			try
			{
				desired.add(Integer.parseInt(value));
			}
			catch (NumberFormatException e)
			{
				throw new CampaignException("Ungültige Spieler-ID: " + value);
			}
		}

		// Only users offered on the left may be added; a crafted request must not add hidden accounts
		Set<Integer> allowed = candidates(context, em).stream().map(User::getId).collect(Collectors.toSet());
		allowed.addAll(service.getParticipantIds(campaign));
		for (int id : desired)
		{
			if (!allowed.contains(id))
			{
				throw new CampaignException("Spieler " + id + " kann nicht ausgewählt werden.");
			}
		}

		CampaignService.ParticipantChange change = service.updateParticipants(campaign, desired, actor);
		boolean changed = !change.added.isEmpty() || !change.removed.isEmpty();
		message(echo, "Teilnehmer gespeichert: " + change.added.size() + " hinzugefügt, "
				+ change.removed.size() + " entfernt."
				+ (campaign.isRunning() && changed ? " Die Kampagne läuft, die Änderung gilt sofort." : ""));

		if (!change.removed.isEmpty())
		{
			List<CampaignObject> leftBehind = new CampaignMapService(em).findPlayerObjects(campaign).stream()
					.filter(o -> change.removed.contains(o.ownerId))
					.collect(Collectors.toList());
			if (!leftBehind.isEmpty())
			{
				echo.append("<div class='gfxbox' style='width:700px'><p class='error'>Entfernte Teilnehmer haben noch Objekte in Kampagnensystemen. ")
						.append("Sie können dort nichts mehr tun, solange die Kampagne läuft:</p>");
				CampaignSystems.objectTable(echo, leftBehind);
				echo.append("</div><br />");
			}
		}
	}

	private void prepareForm(StringBuilder echo)
	{
		echo.append("<div class='gfxbox' style='width:590px'>");
		echo.append("<p>Es wird keine Kampagne vorbereitet und keine läuft.</p>");
		echo.append("<form action='./ds' method='post'>");
		hiddenFields(echo, getClass(), "prepare");
		echo.append("Name der Kampagne: <input type='text' name='name' size='40' maxlength='255' /> ");
		echo.append("<input type='submit' value='Kampagne vorbereiten' />");
		echo.append("</form></div>");
	}

	private void participantForm(StringBuilder echo, Context context, EntityManager em, CampaignService service, Campaign campaign)
	{
		Set<Integer> participantIds = service.getParticipantIds(campaign);
		List<User> candidates = candidates(context, em);

		List<User> left = new ArrayList<>();
		for (User user : candidates)
		{
			if (!participantIds.contains(user.getId()))
			{
				left.add(user);
			}
		}
		List<User> right = participantIds.isEmpty()
				? new ArrayList<>()
				: em.createQuery("select u from User u where u.id in (:ids) order by u.id", User.class)
						.setParameter("ids", participantIds)
						.getResultList();

		echo.append("<div class='gfxbox' style='width:900px'>");
		echo.append("<h3>").append(escapeHtml4(campaign.getName())).append(" – ")
				.append(campaign.isRunning() ? "läuft" : "in Vorbereitung").append("</h3>");
		if (campaign.isRunning())
		{
			echo.append("<p class='error'>Die Kampagne läuft: Änderungen gelten beim Speichern sofort für die Spieler.</p>");
		}
		else
		{
			echo.append("<p>Freigegeben und gestartet wird die Kampagne auf der <a class='forschinfo' href='./gamemaster?show=start'>GameMaster-Seite</a>.</p>");
		}

		echo.append("<form action='./ds' method='post' id='campaignParticipantsForm'>");
		hiddenFields(echo, getClass(), "save");
		echo.append("<div style='margin-bottom:6px'>Filter: <input type='text' id='cpFilter' size='25' placeholder='Name oder ID' /> ");
		echo.append("<label><input type='checkbox' id='cpOnlineOnly' /> nur online</label></div>");

		echo.append("<table class='noBorderX'><tr>");
		echo.append("<td class='noBorderX'>Alle Spieler (").append(left.size()).append(")<br />");
		select(echo, "cpAvailable", null, CampaignUserView.of(left, em));
		echo.append("</td><td class='noBorderX' style='vertical-align:middle; text-align:center'>");
		button(echo, "cpAdd", "&rarr;", "Auswahl hinzufügen");
		button(echo, "cpAddAll", "&raquo;", "Alle sichtbaren hinzufügen");
		button(echo, "cpRemove", "&larr;", "Auswahl entfernen");
		button(echo, "cpRemoveAll", "&laquo;", "Alle sichtbaren entfernen");
		echo.append("</td><td class='noBorderX'>Teilnehmer (<span id='cpCount'>").append(right.size()).append("</span>)<br />");
		select(echo, "cpParticipants", "participant", CampaignUserView.of(right, em));
		echo.append("</td></tr></table>");
		echo.append("<p class='smallfont'>&#9679; grün = online, grau = offline. Doppelklick verschiebt einen Eintrag.</p>");
		echo.append("<input type='submit' value='Teilnehmer speichern' />");
		echo.append("</form></div>");

		script(echo);
	}

	/**
	 * @return Users offered as participants: not in vacation mode, plus all NPC accounts. Accounts with
	 * the hide flag only for users allowed to see them, as in the player list.
	 */
	private static List<User> candidates(Context context, EntityManager em)
	{
		String hideFilter = context.hasPermission(WellKnownPermission.STATISTIK_ERWEITERTE_SPIELERLISTE)
				? "" : " and locate('hide', u.flags) = 0";
		return em.createQuery("select u from User u where (u.vaccount = 0 or u.id < 0)" + hideFilter + " order by u.id", User.class)
				.getResultList();
	}

	private static void select(StringBuilder echo, String id, String name, List<CampaignUserView> users)
	{
		echo.append("<select multiple size='20' style='width:360px' id='").append(id).append("'");
		if (name != null)
		{
			echo.append(" name='").append(name).append("'");
		}
		echo.append(">");
		for (CampaignUserView user : users)
		{
			echo.append("<option value='").append(user.id).append("'")
					.append(" data-online='").append(user.online).append("'")
					.append(" data-search='").append(escapeHtml4((user.plainName + " " + user.id).toLowerCase())).append("'")
					.append(" title='").append(escapeHtml4(user.lastSeen)).append("'")
					.append(user.online ? " style='color:#33dd33'" : "")
					.append(">");
			echo.append("&#9679; ").append(escapeHtml4(user.plainName)).append(" (").append(user.id).append(")");
			if (!user.allianceName.isEmpty())
			{
				echo.append(" [").append(escapeHtml4(user.allianceName)).append("]");
			}
			if (user.npc)
			{
				echo.append(" · NPC");
			}
			if (user.gamemaster)
			{
				echo.append(" · GM");
			}
			if (!user.eligible)
			{
				echo.append(" · Urlaub");
			}
			echo.append(" · inaktiv ").append(user.inactivity);
			echo.append("</option>");
		}
		echo.append("</select>");
	}

	private static void button(StringBuilder echo, String id, String label, String title)
	{
		echo.append("<button type='button' id='").append(id).append("' title='").append(title)
				.append("' style='display:block; width:40px; margin:4px auto'>").append(label).append("</button>");
	}

	private static void script(StringBuilder echo)
	{
		echo.append("<script type='text/javascript'>\n")
				.append("(function() {\n")
				.append("  var available = document.getElementById('cpAvailable');\n")
				.append("  var participants = document.getElementById('cpParticipants');\n")
				.append("  var filter = document.getElementById('cpFilter');\n")
				.append("  var onlineOnly = document.getElementById('cpOnlineOnly');\n")
				.append("  function visible(option) { return option.style.display !== 'none'; }\n")
				.append("  function move(from, to, all) {\n")
				.append("    var options = Array.prototype.slice.call(from.options);\n")
				.append("    options.forEach(function(o) { if (visible(o) && (all || o.selected)) { o.selected = false; to.appendChild(o); } });\n")
				.append("    sort(to); update();\n")
				.append("  }\n")
				.append("  function sort(list) {\n")
				.append("    var options = Array.prototype.slice.call(list.options);\n")
				.append("    options.sort(function(a, b) { return parseInt(a.value, 10) - parseInt(b.value, 10); });\n")
				.append("    options.forEach(function(o) { list.appendChild(o); });\n")
				.append("  }\n")
				.append("  function applyFilter() {\n")
				.append("    var text = filter.value.toLowerCase();\n")
				.append("    [available, participants].forEach(function(list) {\n")
				.append("      Array.prototype.forEach.call(list.options, function(o) {\n")
				.append("        var show = o.getAttribute('data-search').indexOf(text) >= 0\n")
				.append("          && (!onlineOnly.checked || o.getAttribute('data-online') === 'true');\n")
				.append("        o.style.display = show ? '' : 'none';\n")
				.append("        if (!show) { o.selected = false; }\n")
				.append("      });\n")
				.append("    });\n")
				.append("  }\n")
				.append("  function update() { document.getElementById('cpCount').textContent = participants.options.length; }\n")
				.append("  document.getElementById('cpAdd').onclick = function() { move(available, participants, false); };\n")
				.append("  document.getElementById('cpAddAll').onclick = function() { move(available, participants, true); };\n")
				.append("  document.getElementById('cpRemove').onclick = function() { move(participants, available, false); };\n")
				.append("  document.getElementById('cpRemoveAll').onclick = function() { move(participants, available, true); };\n")
				.append("  available.ondblclick = function() { move(available, participants, false); };\n")
				.append("  participants.ondblclick = function() { move(participants, available, false); };\n")
				.append("  filter.oninput = applyFilter;\n")
				.append("  onlineOnly.onchange = applyFilter;\n")
				.append("  // Submit every participant, including those the filter currently hides\n")
				.append("  document.getElementById('campaignParticipantsForm').onsubmit = function() {\n")
				.append("    Array.prototype.forEach.call(participants.options, function(o) { o.selected = true; });\n")
				.append("  };\n")
				.append("})();\n")
				.append("</script>");
	}
}
