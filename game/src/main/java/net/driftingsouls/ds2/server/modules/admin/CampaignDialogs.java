package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.WellKnownAdminPermission;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.campaign.dialog.Comparison;
import net.driftingsouls.ds2.server.campaign.dialog.DialogAction;
import net.driftingsouls.ds2.server.campaign.dialog.DialogFactor;
import net.driftingsouls.ds2.server.campaign.dialog.DialogModel;
import net.driftingsouls.ds2.server.campaign.dialog.DialogRepository;
import net.driftingsouls.ds2.server.campaign.dialog.DialogTypes;
import net.driftingsouls.ds2.server.campaign.dialog.ParamDef;
import net.driftingsouls.ds2.server.campaign.dialog.Params;
import net.driftingsouls.ds2.server.campaign.interaction.Interaction;
import net.driftingsouls.ds2.server.campaign.interaction.InteractionRepository;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.pipeline.Request;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.driftingsouls.ds2.server.modules.admin.CampaignAdminHtml.*;

/**
 * The dialog editor: nodes (what the NPC says), their options (the player's answers), and per option
 * the conditions, chance modifiers and actions.
 */
@AdminMenuEntry(category = "Kampagne", name = "Dialoge", permission = WellKnownAdminPermission.CAMPAIGN_DIALOGS)
public class CampaignDialogs implements AdminPlugin
{
	private final DialogRepository repository = new DialogRepository();
	private final DialogTypes types = DialogTypes.get();

	@Override
	public void output(StringBuilder echo)
	{
		CampaignAdminHtml.begin(echo);
		try
		{
			render(echo);
		}
		finally
		{
			CampaignAdminHtml.end(echo);
		}
	}

	private void render(StringBuilder echo)
	{
		Request request = ContextMap.getContext().getRequest();
		int nodeId = request.getParameterInt("node");

		try
		{
			nodeId = handle(echo, request, nodeId);
		}
		catch (CampaignException e)
		{
			error(echo, e.getMessage());
		}
		catch (RuntimeException e)
		{
			error(echo, "Fehler: " + e.getMessage());
		}

		List<DialogModel.Node> nodes = repository.findAll();
		if (nodeId != 0)
		{
			int id = nodeId;
			nodes.stream().filter(n -> n.id == id).findFirst().ifPresent(n -> nodeEditor(echo, request, n, nodes));
		}
		overview(echo, nodes);
	}

	/**
	 * @return The node to show afterwards
	 */
	private int handle(StringBuilder echo, Request request, int nodeId)
	{
		int optionId = request.getParameterInt("option");
		switch (request.getParameterString("cpaction"))
		{
			case "createnode":
			{
				int id = repository.createNode(nonEmpty(request, "title", "Titel"), request.getParameterString("speaker").trim(),
						request.getParameterString("image").trim(), request.getParameterString("text"));
				repository.setNodeForced(id, "1".equals(request.getParameterString("forced")));
				message(echo, "Knoten " + id + " angelegt.");
				return id;
			}
			case "savenode":
				repository.updateNode(nodeId, nonEmpty(request, "title", "Titel"), request.getParameterString("speaker").trim(),
						request.getParameterString("image").trim(), request.getParameterString("text"));
				repository.setNodeForced(nodeId, "1".equals(request.getParameterString("forced")));
				message(echo, "Knoten gespeichert.");
				return nodeId;
			case "deletenode":
			{
				List<Integer> interactions = repository.findInteractionsStartingAt(nodeId);
				if (!interactions.isEmpty())
				{
					throw new CampaignException("Der Knoten ist Start der Interaktion(en) " + interactions + " und kann nicht gelöscht werden.");
				}
				List<Integer> options = repository.findOptionsLeadingTo(nodeId);
				if (!options.isEmpty() && !"1".equals(request.getParameterString("confirm")))
				{
					echo.append("<div class='gfxbox' style='width:590px'>Die Antworten ").append(esc(options))
							.append(" führen zu diesem Knoten und enden danach stattdessen den Dialog. ");
					actionButton(echo, getClass(), "deletenode", "Trotzdem löschen", "node", nodeId, "confirm", 1);
					echo.append("</div><br />");
					return nodeId;
				}
				repository.deleteNode(nodeId);
				message(echo, "Knoten gelöscht.");
				return 0;
			}
			case "createoption":
				repository.createOption(nodeId, request.getParameterInt("sort"), nonEmpty(request, "text", "Antworttext"),
						optionalInt(request, "next"), optionalInt(request, "failure"), chance(request), partialChance(request),
						optionalInt(request, "partial"), "1".equals(request.getParameterString("hide")),
						"1".equals(request.getParameterString("once")));
				message(echo, "Antwort angelegt.");
				return nodeId;
			case "saveoption":
				repository.updateOption(optionId, request.getParameterInt("sort"), nonEmpty(request, "text", "Antworttext"),
						optionalInt(request, "next"), optionalInt(request, "failure"), chance(request), partialChance(request),
						optionalInt(request, "partial"), "1".equals(request.getParameterString("hide")),
						"1".equals(request.getParameterString("once")));
				message(echo, "Antwort gespeichert.");
				return nodeId;
			case "deleteoption":
				repository.deleteOption(optionId);
				message(echo, "Antwort gelöscht.");
				return nodeId;
			case "addcondition":
			{
				DialogFactor factor = types.factor(request.getParameterString("ctype"));
				Double threshold = optionalDouble(request, "threshold");
				repository.addCondition(optionId, request.getParameterString("ctype"), readParams(request, factor.getParams()),
						Comparison.valueOf(request.getParameterString("comparison")), threshold == null ? 0 : threshold);
				message(echo, "Bedingung hinzugefügt.");
				return nodeId;
			}
			case "deletecondition":
				repository.deleteCondition(request.getParameterInt("item"));
				return nodeId;
			case "addmodifier":
			{
				DialogFactor factor = types.factor(request.getParameterString("ctype"));
				Double weight = optionalDouble(request, "factor");
				repository.addModifier(optionId, request.getParameterString("ctype"), readParams(request, factor.getParams()),
						weight == null ? 1 : weight, optionalDouble(request, "min"), optionalDouble(request, "max"));
				message(echo, "Modifikator hinzugefügt.");
				return nodeId;
			}
			case "deletemodifier":
				repository.deleteModifier(request.getParameterInt("item"));
				return nodeId;
			case "addaction":
			{
				DialogAction action = types.action(request.getParameterString("ctype"));
				repository.addAction(optionId, request.getParameterInt("sort"), DialogModel.Outcome.valueOf(request.getParameterString("outcome")),
						request.getParameterString("ctype"), readParams(request, action.getParams()));
				message(echo, "Aktion hinzugefügt.");
				return nodeId;
			}
			case "deleteaction":
				repository.deleteAction(request.getParameterInt("item"));
				return nodeId;
			default:
				return nodeId;
		}
	}

	private static Integer partialChance(Request request)
	{
		Integer partial = optionalInt(request, "partialchance");
		if (partial != null && (partial < 0 || partial > 100))
		{
			throw new CampaignException("Die Teilerfolg-Spanne muss zwischen 0 und 100 liegen.");
		}
		return partial;
	}

	private static Integer chance(Request request)
	{
		Integer chance = optionalInt(request, "chance");
		if (chance != null && (chance < 0 || chance > 100))
		{
			throw new CampaignException("Die Erfolgschance muss zwischen 0 und 100 liegen.");
		}
		return chance;
	}

	private static String nonEmpty(Request request, String name, String label)
	{
		String value = request.getParameterString(name).trim();
		if (value.isEmpty())
		{
			throw new CampaignException(label + " fehlt.");
		}
		return value;
	}

	// ---------------------------------------------------------------- node editor

	private void nodeEditor(StringBuilder echo, Request request, DialogModel.Node node, List<DialogModel.Node> nodes)
	{
		echo.append("<div class='gfxbox' style='width:950px'>");
		echo.append("<h3>Knoten ").append(node.id).append(": ").append(esc(node.title)).append("</h3>");
		echo.append("<form action='./ds' method='post'>");
		hiddenFields(echo, getClass(), "savenode");
		hidden(echo, "node", node.id);
		nodeFields(echo, node.title, node.speakerName, node.image, node.text, node.forced);
		echo.append("<input type='submit' value='Knoten speichern' /></form>");
		actionButton(echo, getClass(), "deletenode", "Knoten löschen", "node", node.id);

		echo.append("<h4>Antworten</h4>");
		for (DialogModel.Option option : node.options)
		{
			optionEditor(echo, request, node, option, nodes);
		}

		echo.append("<div class='campaign-section' style='border-style:dashed'><b>Neue Antwort</b><form action='./ds' method='post'>");
		hiddenFields(echo, getClass(), "createoption");
		hidden(echo, "node", node.id);
		optionFields(echo, null, nodes, node.options.size() + 1);
		echo.append("<input type='submit' value='Antwort anlegen' /></form></div>");
		echo.append("</div><br />");
	}

	private void optionEditor(StringBuilder echo, Request request, DialogModel.Node node, DialogModel.Option option, List<DialogModel.Node> nodes)
	{
		echo.append("<div class='campaign-section'>");
		echo.append("<form action='./ds' method='post'>");
		hiddenFields(echo, getClass(), "saveoption");
		hidden(echo, "node", node.id);
		hidden(echo, "option", option.id);
		optionFields(echo, option, nodes, option.sort);
		echo.append("<input type='submit' value='Antwort speichern' /></form>");
		actionButton(echo, getClass(), "deleteoption", "Antwort löschen", "node", node.id, "option", option.id);

		echo.append("<br /><b>Bedingungen</b> (alle müssen erfüllt sein):<ul>");
		for (DialogModel.Condition condition : option.conditions)
		{
			echo.append("<li>").append(esc(describeFactor(condition.type, condition.params))).append(" ")
					.append(esc(condition.comparison.getSymbol())).append(" ").append(esc(condition.threshold)).append(" ");
			actionButton(echo, getClass(), "deletecondition", "x", "node", node.id, "item", condition.id);
			echo.append(problems(condition.type, true, condition.params)).append("</li>");
		}
		echo.append("</ul>");
		addFactorForm(echo, request, node, option, "condition");

		if (option.baseChance != null)
		{
			echo.append("<b>Modifikatoren der Erfolgschance</b> (Basis ").append(option.baseChance).append(" %):<ul>");
			for (DialogModel.Modifier modifier : option.modifiers)
			{
				echo.append("<li>").append(esc(modifier.factor)).append(" × ").append(esc(describeFactor(modifier.type, modifier.params)));
				if (modifier.min != null || modifier.max != null)
				{
					echo.append(" (begrenzt auf ").append(modifier.min == null ? "…" : esc(modifier.min)).append(" bis ")
							.append(modifier.max == null ? "…" : esc(modifier.max)).append(")");
				}
				echo.append(" ");
				actionButton(echo, getClass(), "deletemodifier", "x", "node", node.id, "item", modifier.id);
				echo.append(problems(modifier.type, true, modifier.params)).append("</li>");
			}
			echo.append("</ul>");
			addFactorForm(echo, request, node, option, "modifier");
		}

		echo.append("<b>Aktionen</b> <span class='campaign-hint'>– laufen sofort, wenn der Spieler diese Antwort wählt, noch vor dem Folgeknoten. ")
				.append("Er kann sie nicht mehr durch Abbrechen des Gesprächs verhindern.</span>");
		for (DialogModel.Outcome outcome : DialogModel.Outcome.values())
		{
			if (option.baseChance == null && outcome != DialogModel.Outcome.ALWAYS && option.actions.stream().noneMatch(a -> a.outcome == outcome))
			{
				// Without a chance roll there is no failure, and "on success" equals "always"
				continue;
			}
			if (outcome == DialogModel.Outcome.PARTIAL && option.partialChance == null && option.actions.stream().noneMatch(a -> a.outcome == outcome))
			{
				continue;
			}
			echo.append("<div style='margin-left:12px'><i>").append(esc(outcome.getLabel())).append(":</i><ul>");
			boolean any = false;
			for (DialogModel.ActionRef action : option.actions)
			{
				if (action.outcome != outcome)
				{
					continue;
				}
				any = true;
				echo.append("<li>").append(esc(describeAction(action.type, action.params))).append(" ");
				actionButton(echo, getClass(), "deleteaction", "x", "node", node.id, "item", action.id);
				echo.append(problems(action.type, false, action.params)).append("</li>");
			}
			if (!any)
			{
				echo.append("<li class='campaign-hint'>keine</li>");
			}
			echo.append("</ul></div>");
		}
		addActionForm(echo, request, node, option);
		echo.append("</div>");
	}

	/**
	 * Adding a condition or modifier takes two steps: choose the factor, then fill in its parameters.
	 */
	private void addFactorForm(StringBuilder echo, Request request, DialogModel.Node node, DialogModel.Option option, String kind)
	{
		String chosen = request.getParameterInt("addto") == option.id && kind.equals(request.getParameterString("addkind"))
				? request.getParameterString("ctype") : "";
		DialogFactor factor = types.getFactors().get(chosen);
		if (factor == null)
		{
			echo.append("<form action='./ds' method='get'>");
			hiddenFields(echo, getClass(), "");
			hidden(echo, "node", node.id);
			hidden(echo, "addto", option.id);
			hidden(echo, "addkind", kind);
			echo.append("condition".equals(kind) ? "Bedingung hinzufügen: " : "Modifikator hinzufügen: ");
			echo.append("<select name='ctype'>");
			types.getFactors().forEach((id, f) -> echo.append("<option value='").append(esc(id)).append("'>").append(esc(f.getLabel())).append("</option>"));
			echo.append("</select> <input type='submit' value='weiter' /></form>");
			return;
		}

		echo.append("<form action='./ds' method='post' class='campaign-section' style='border-style:dotted'>");
		hiddenFields(echo, getClass(), "condition".equals(kind) ? "addcondition" : "addmodifier");
		hidden(echo, "node", node.id);
		hidden(echo, "option", option.id);
		hidden(echo, "ctype", chosen);
		echo.append("<b>").append(esc(factor.getLabel())).append("</b><br />");
		paramFields(echo, factor.getParams(), null);
		if ("condition".equals(kind))
		{
			echo.append("Vergleich: <select name='comparison'>");
			for (Comparison comparison : Comparison.values())
			{
				echo.append("<option value='").append(comparison.name()).append("'>").append(esc(comparison.getSymbol())).append(" (")
						.append(esc(comparison.getLabel())).append(")</option>");
			}
			echo.append("</select> Schwelle: <input type='text' name='threshold' size='6' value='1' /><br />");
			echo.append("<input type='submit' value='Bedingung hinzufügen' />");
		}
		else
		{
			echo.append("Faktor (Prozentpunkte je Einheit): <input type='text' name='factor' size='6' value='1' /> ");
			echo.append("Wert begrenzen von <input type='text' name='min' size='5' /> bis <input type='text' name='max' size='5' /><br />");
			echo.append("<input type='submit' value='Modifikator hinzufügen' />");
		}
		echo.append(" <a class='forschinfo' href='").append(pluginUrl(getClass())).append("&amp;node=").append(node.id).append("'>abbrechen</a></form>");
	}

	private void addActionForm(StringBuilder echo, Request request, DialogModel.Node node, DialogModel.Option option)
	{
		String chosen = request.getParameterInt("addto") == option.id && "action".equals(request.getParameterString("addkind"))
				? request.getParameterString("ctype") : "";
		DialogAction action = types.getActions().get(chosen);
		if (action == null)
		{
			echo.append("<form action='./ds' method='get'>");
			hiddenFields(echo, getClass(), "");
			hidden(echo, "node", node.id);
			hidden(echo, "addto", option.id);
			hidden(echo, "addkind", "action");
			echo.append("Aktion hinzufügen: <select name='ctype'>");
			for (DialogAction.Group group : DialogAction.Group.values())
			{
				echo.append("<optgroup label='").append(esc(group.getLabel())).append("'>");
				types.getActions().forEach((id, a) -> {
					if (a.getGroup() == group)
					{
						echo.append("<option value='").append(esc(id)).append("'>").append(esc(a.getLabel())).append("</option>");
					}
				});
				echo.append("</optgroup>");
			}
			echo.append("</select> <input type='submit' value='weiter' /></form>");
			return;
		}

		echo.append("<form action='./ds' method='post' class='campaign-section' style='border-style:dotted'>");
		hiddenFields(echo, getClass(), "addaction");
		hidden(echo, "node", node.id);
		hidden(echo, "option", option.id);
		hidden(echo, "ctype", chosen);
		echo.append("<b>").append(esc(action.getLabel())).append("</b><br />");
		paramFields(echo, action.getParams(), null);
		echo.append("Ausführen: <select name='outcome'>");
		for (DialogModel.Outcome outcome : DialogModel.Outcome.values())
		{
			echo.append("<option value='").append(outcome.name()).append("'>").append(esc(outcome.getLabel())).append("</option>");
		}
		echo.append("</select> Reihenfolge <input type='text' name='sort' size='3' value='").append(option.actions.size() + 1).append("' /><br />");
		echo.append("<input type='submit' value='Aktion hinzufügen' /> <a class='forschinfo' href='").append(pluginUrl(getClass()))
				.append("&amp;node=").append(node.id).append("'>abbrechen</a></form>");
	}

	private static void nodeFields(StringBuilder echo, String title, String speaker, String image, String text, boolean forced)
	{
		echo.append("Titel (nur für dich): <input type='text' name='title' size='40' value='").append(esc(title)).append("' /><br />");
		echo.append("Sprecher: <input type='text' name='speaker' size='30' value='").append(esc(speaker)).append("' /> ");
		echo.append("Bild (Pfad/URL): <input type='text' name='image' size='40' value='").append(esc(image)).append("' /><br />");
		echo.append("Text (BBCode):<br /><textarea name='text' rows='5' cols='100'>").append(esc(text)).append("</textarea><br />");
		echo.append("<span class='campaign-hint'>Bedingter Text: [wenn name &gt;= 2]…[sonst]…[/wenn], kampagnenweit mit global:name.</span><br />");
		echo.append("<label><input type='checkbox' name='forced' value='1'").append(forced ? " checked='checked'" : "")
				.append(" /> Spieler muss antworten (kein „Gespräch beenden“, kein anderes Gespräch, z.B. bei einem Überfall)</label><br />");
	}

	private static void optionFields(StringBuilder echo, DialogModel.Option option, List<DialogModel.Node> nodes, int sort)
	{
		echo.append("Text: <input type='text' name='text' size='70' value='").append(option == null ? "" : esc(option.text)).append("' /> ");
		echo.append("Reihenfolge <input type='text' name='sort' size='3' value='").append(sort).append("' /><br />");
		echo.append("Weiter zu: ");
		nodeSelect(echo, "next", option == null ? null : option.nextNodeId, nodes);
		echo.append(" Erfolgschance in % (leer = immer Erfolg): <input type='text' name='chance' size='4' value='")
				.append(option == null || option.baseChance == null ? "" : option.baseChance).append("' /> bei Misserfolg weiter zu: ");
		nodeSelect(echo, "failure", option == null ? null : option.failureNodeId, nodes);
		echo.append("<br />Teilerfolg-Spanne in % (leer = kein Teilerfolg): <input type='text' name='partialchance' size='4' value='")
				.append(option == null || option.partialChance == null ? "" : option.partialChance).append("' /> bei Teilerfolg weiter zu: ");
		nodeSelect(echo, "partial", option == null ? null : option.partialNodeId, nodes);
		echo.append("<br /><span class='campaign-hint'>Der Wurf (0–99) entscheidet: unter der Erfolgschance Erfolg, in den folgenden ")
				.append("Prozentpunkten der Teilerfolg-Spanne Teilerfolg, sonst Misserfolg. Beispiel: Chance 30 %, Spanne 40 % → 30 % Erfolg, ")
				.append("40 % Teilerfolg, 30 % Misserfolg.</span>");
		echo.append("<br /><span class='campaign-hint'>Ein Folgeknoten bei Misserfolg ist optional. Soll ein Misserfolg z.B. sofort eine Schlacht ")
				.append("auslösen, lege dafür eine Aktion „bei Misserfolg“ an: Sie läuft direkt bei der Auswahl, der Spieler kann sie nicht durch Abbrechen abwehren.</span>");
		echo.append("<br /><label><input type='checkbox' name='hide' value='1'")
				.append(option == null || option.hideIfUnavailable ? " checked='checked'" : "")
				.append(" /> verstecken, wenn Bedingungen nicht erfüllt (sonst ausgegraut)</label><br />");
		echo.append("<label><input type='checkbox' name='once' value='1'")
				.append(option != null && option.oncePerUser ? " checked='checked'" : "")
				.append(" /> nur einmal wählbar (je Spieler und Kampagne; danach versteckt bzw. ausgegraut)</label><br />");
	}

	private static void nodeSelect(StringBuilder echo, String name, Integer selected, List<DialogModel.Node> nodes)
	{
		echo.append("<select name='").append(name).append("'><option value=''>– Dialog endet –</option>");
		for (DialogModel.Node node : nodes)
		{
			echo.append("<option value='").append(node.id).append("'").append(Integer.valueOf(node.id).equals(selected) ? " selected='selected'" : "")
					.append(">").append(node.id).append(": ").append(esc(node.title)).append("</option>");
		}
		echo.append("</select>");
	}

	// ---------------------------------------------------------------- overview and checks

	private void overview(StringBuilder echo, List<DialogModel.Node> nodes)
	{
		List<Interaction> interactions = new InteractionRepository().findAll();
		Set<Integer> starts = new HashSet<>();
		interactions.forEach(i -> starts.add(i.startNodeId));
		Set<Integer> referenced = new HashSet<>();
		Map<Integer, DialogModel.Node> byId = new HashMap<>();
		for (DialogModel.Node node : nodes)
		{
			byId.put(node.id, node);
			for (DialogModel.Option option : node.options)
			{
				if (option.nextNodeId != null)
				{
					referenced.add(option.nextNodeId);
				}
				if (option.failureNodeId != null)
				{
					referenced.add(option.failureNodeId);
				}
				if (option.partialNodeId != null)
				{
					referenced.add(option.partialNodeId);
				}
			}
		}

		echo.append("<div class='gfxbox' style='width:950px'><h3>Dialogknoten</h3>");
		echo.append("<table class='noBorderX' cellpadding='3'><tr><td class='noBorderX'>ID</td><td class='noBorderX'>Titel</td>")
				.append("<td class='noBorderX'>Sprecher</td><td class='noBorderX'>Antworten</td><td class='noBorderX'>Hinweise</td></tr>");
		for (DialogModel.Node node : nodes)
		{
			List<String> hints = new ArrayList<>();
			if (starts.contains(node.id))
			{
				hints.add("Start einer Interaktion");
			}
			else if (!referenced.contains(node.id))
			{
				hints.add("<span class='error'>nicht erreichbar</span>");
			}
			if (node.options.isEmpty())
			{
				hints.add("keine Antworten (Gespräch bleibt stehen, bis der Spieler es beendet)");
			}
			for (DialogModel.Option option : node.options)
			{
				String p = optionProblems(option);
				if (!p.isEmpty())
				{
					hints.add("<span class='error'>Antwort " + option.id + ": ungültige Einstellungen</span>");
				}
			}
			echo.append("<tr><td class='noBorderX'>").append(node.id).append("</td><td class='noBorderX'><a class='forschinfo' href='")
					.append(pluginUrl(getClass())).append("&amp;node=").append(node.id).append("'>").append(esc(node.title)).append("</a></td>")
					.append("<td class='noBorderX'>").append(esc(node.speakerName)).append("</td><td class='noBorderX'>").append(node.options.size())
					.append("</td><td class='noBorderX'>").append(String.join(", ", hints)).append("</td></tr>");
		}
		echo.append("</table>");

		echo.append("<h4>Struktur</h4><div class='smallfont' style='font-family:monospace'>");
		Set<Integer> shown = new HashSet<>();
		for (DialogModel.Node node : nodes)
		{
			if (starts.contains(node.id) || !referenced.contains(node.id))
			{
				tree(echo, node, byId, shown, 0);
			}
		}
		echo.append("</div>");

		echo.append("<h4>Neuer Knoten</h4><form action='./ds' method='post'>");
		hiddenFields(echo, getClass(), "createnode");
		nodeFields(echo, "", "", "", "", false);
		echo.append("<input type='submit' value='Knoten anlegen' /></form></div>");
	}

	private void tree(StringBuilder echo, DialogModel.Node node, Map<Integer, DialogModel.Node> byId, Set<Integer> shown, int depth)
	{
		String indent = "&nbsp;&nbsp;&nbsp;&nbsp;".repeat(depth);
		echo.append(indent).append("<b>#").append(node.id).append(" ").append(esc(node.title)).append("</b>");
		if (!shown.add(node.id))
		{
			echo.append(" (siehe oben)<br />");
			return;
		}
		echo.append("<br />");
		for (DialogModel.Option option : node.options)
		{
			echo.append(indent).append("&nbsp;&nbsp;↳ „").append(esc(option.text)).append("“");
			if (option.baseChance != null)
			{
				echo.append(" [").append(option.baseChance).append(" %]");
			}
			if (!option.conditions.isEmpty())
			{
				echo.append(" [").append(option.conditions.size()).append(" Bed.]");
			}
			echo.append(option.nextNodeId == null ? " → Ende" : "").append("<br />");
			if (option.nextNodeId != null && byId.containsKey(option.nextNodeId))
			{
				tree(echo, byId.get(option.nextNodeId), byId, shown, depth + 2);
			}
			if (option.baseChance != null && option.partialChance != null)
			{
				echo.append(indent).append("&nbsp;&nbsp;&nbsp;&nbsp;◐ Teilerfolg [").append(option.partialChance).append(" %]")
						.append(option.partialNodeId == null ? " → Ende" : "").append("<br />");
				if (option.partialNodeId != null && byId.containsKey(option.partialNodeId))
				{
					tree(echo, byId.get(option.partialNodeId), byId, shown, depth + 3);
				}
			}
			if (option.baseChance != null)
			{
				echo.append(indent).append("&nbsp;&nbsp;&nbsp;&nbsp;✗ Misserfolg").append(option.failureNodeId == null ? " → Ende" : "").append("<br />");
				if (option.failureNodeId != null && byId.containsKey(option.failureNodeId))
				{
					tree(echo, byId.get(option.failureNodeId), byId, shown, depth + 3);
				}
			}
		}
	}

	private String optionProblems(DialogModel.Option option)
	{
		StringBuilder problems = new StringBuilder();
		option.conditions.forEach(c -> problems.append(problems(c.type, true, c.params)));
		option.modifiers.forEach(m -> problems.append(problems(m.type, true, m.params)));
		option.actions.forEach(a -> problems.append(problems(a.type, false, a.params)));
		return problems.toString();
	}

	/**
	 * @return HTML naming what is wrong with a stored factor or action, or an empty string
	 */
	private String problems(String type, boolean factor, Params params)
	{
		List<ParamDef> defs;
		if (factor)
		{
			DialogFactor f = types.getFactors().get(type);
			if (f == null)
			{
				return " <span class='error'>unbekannter Faktor " + esc(type) + "</span>";
			}
			defs = f.getParams();
		}
		else
		{
			DialogAction a = types.getActions().get(type);
			if (a == null)
			{
				return " <span class='error'>unbekannte Aktion " + esc(type) + "</span>";
			}
			defs = a.getParams();
		}
		List<String> errors = new ArrayList<>();
		for (ParamDef def : defs)
		{
			String error = def.validate(params.get(def.name));
			if (error != null)
			{
				errors.add(error);
			}
		}
		return errors.isEmpty() ? "" : " <span class='error'>" + esc(String.join(" ", errors)) + "</span>";
	}

	private String describeFactor(String type, Params params)
	{
		DialogFactor factor = types.getFactors().get(type);
		return factor == null ? type : factor.describe(params);
	}

	private String describeAction(String type, Params params)
	{
		DialogAction action = types.getActions().get(type);
		return action == null ? type : action.describe(params);
	}
}
