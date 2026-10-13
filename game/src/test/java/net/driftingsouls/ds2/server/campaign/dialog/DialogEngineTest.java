package net.driftingsouls.ds2.server.campaign.dialog;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class DialogEngineTest
{
	/** Returns the "value" parameter. */
	private static class ConstantFactor implements DialogFactor
	{
		@Override
		public String getLabel()
		{
			return "konstant";
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of();
		}

		@Override
		public double value(DialogContext context, Params params)
		{
			return Double.parseDouble(params.get("value"));
		}

		@Override
		public String describe(Params params)
		{
			return "konstant";
		}
	}

	private final List<String> executed = new ArrayList<>();

	private class RecordingAction implements DialogAction
	{
		@Override
		public String getLabel()
		{
			return "merken";
		}

		@Override
		public Group getGroup()
		{
			return Group.SCRIPT;
		}

		@Override
		public List<ParamDef> getParams()
		{
			return List.of();
		}

		@Override
		public void execute(DialogContext context, Params params)
		{
			executed.add(params.get("name"));
		}

		@Override
		public String describe(Params params)
		{
			return params.get("name");
		}
	}

	private final DialogEngine engine = new DialogEngine(new DialogTypes(
			Map.of("CONST", new ConstantFactor()),
			Map.of("RECORD", new RecordingAction())));

	@Test
	public void gegebenEineErfuellteBedingung_evaluate_sollteDieOptionAnbieten()
	{
		DialogModel.Option option = option(null, List.of(condition(80, Comparison.GE, 50)), List.of(), List.of());

		DialogEngine.Evaluation evaluation = engine.evaluate(option, context(0));

		assertTrue(evaluation.available);
		assertNull(evaluation.chance);
	}

	@Test
	public void gegebenEineUnerfuellteBedingung_evaluate_sollteDieOptionSperren()
	{
		DialogModel.Option option = option(null, List.of(condition(20, Comparison.GE, 50)), List.of(), List.of());

		DialogEngine.Evaluation evaluation = engine.evaluate(option, context(0));

		assertFalse(evaluation.available);
		assertEquals(1, evaluation.unmetConditions.size());
	}

	@Test
	public void gegebenModifikatoren_evaluate_sollteDieChanceBerechnenUndBegrenzen()
	{
		// 30 + 0.5 * clamp(100, max 60) = 30 + 30 = 60
		DialogModel.Option option = option(30, List.of(), List.of(modifier(100, 0.5, null, 60.0)), List.of());
		assertEquals(Integer.valueOf(60), engine.evaluate(option, context(0)).chance);

		// 90 + 1 * 50 = 140, capped at 100
		DialogModel.Option capped = option(90, List.of(), List.of(modifier(50, 1, null, null)), List.of());
		assertEquals(Integer.valueOf(100), engine.evaluate(capped, context(0)).chance);
	}

	@Test
	public void gegebenEinWurfUnterDerChance_choose_sollteErfolgreichSeinUndErfolgsaktionenAusfuehren()
	{
		DialogModel.Option option = option(50, List.of(), List.of(), List.of(
				action(DialogModel.Outcome.SUCCESS, "gewonnen"),
				action(DialogModel.Outcome.FAILURE, "verloren"),
				action(DialogModel.Outcome.ALWAYS, "immer")));

		DialogEngine.Result result = engine.choose(option, context(49.9), false, null);

		assertTrue(result.success);
		assertEquals(Integer.valueOf(2), result.nextNodeId);
		assertEquals(List.of("gewonnen", "immer"), executed);
	}

	@Test
	public void gegebenEinWurfUeberDerChance_choose_sollteMisslingenUndZumFehlschlagKnotenFuehren()
	{
		DialogModel.Option option = option(50, List.of(), List.of(), List.of(
				action(DialogModel.Outcome.SUCCESS, "gewonnen"),
				action(DialogModel.Outcome.FAILURE, "verloren")));

		DialogEngine.Result result = engine.choose(option, context(50), false, null);

		assertFalse(result.success);
		assertEquals(Integer.valueOf(3), result.nextNodeId);
		assertEquals(List.of("verloren"), executed);
	}

	@Test
	public void gegebenEineVorschau_choose_sollteAktionenNurBeschreiben()
	{
		DialogModel.Option option = option(null, List.of(), List.of(), List.of(action(DialogModel.Outcome.ALWAYS, "belohnung")));

		DialogEngine.Result result = engine.choose(option, context(0), true, null);

		assertEquals(List.of("belohnung"), result.actions);
		assertTrue(executed.isEmpty());
	}

	@Test
	public void gegebenEinErzwungenesErgebnis_choose_sollteNichtWuerfeln()
	{
		DialogModel.Option option = option(100, List.of(), List.of(), List.of());

		assertFalse(engine.choose(option, context(0), true, false).success);
	}

	@Test(expected = IllegalStateException.class)
	public void gegebenEineGesperrteOption_choose_sollteAbgelehntWerden()
	{
		DialogModel.Option option = option(null, List.of(condition(0, Comparison.GT, 0)), List.of(), List.of());

		engine.choose(option, context(0), false, null);
	}

	private static DialogContext context(double roll)
	{
		return new DialogContext(null, null, null, null, ObjectType.SHIP, 1, null, null, null, () -> roll);
	}

	private static DialogModel.Option option(Integer baseChance, List<DialogModel.Condition> conditions, List<DialogModel.Modifier> modifiers,
											 List<DialogModel.ActionRef> actions)
	{
		return new DialogModel.Option(1, 1, 0, "Antwort", 2, 3, baseChance, true, false, conditions, modifiers, actions);
	}

	private static DialogModel.Condition condition(double value, Comparison comparison, double threshold)
	{
		return new DialogModel.Condition(1, "CONST", Params.of("value", Double.toString(value)), comparison, threshold);
	}

	private static DialogModel.Modifier modifier(double value, double factor, Double min, Double max)
	{
		return new DialogModel.Modifier(1, "CONST", Params.of("value", Double.toString(value)), factor, min, max);
	}

	private static DialogModel.ActionRef action(DialogModel.Outcome outcome, String name)
	{
		return new DialogModel.ActionRef(1, 0, outcome, "RECORD", Params.of("name", name));
	}
}
