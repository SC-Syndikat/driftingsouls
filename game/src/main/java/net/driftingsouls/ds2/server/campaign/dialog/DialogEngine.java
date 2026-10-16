package net.driftingsouls.ds2.server.campaign.dialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Evaluates dialog options and carries out a choice.
 *
 * <pre>
 * available = every condition holds: factor(params) comparison threshold
 * chance    = clamp(baseChance + sum(modifier.factor * clamp(value, min, max)), 0, 100)
 * success   = roll &lt; chance, with roll uniform in [0, 100); options without base chance always succeed
 * partial   = chance &lt;= roll &lt; chance + partialChance
 * </pre>
 */
public class DialogEngine
{
	private final DialogTypes types;

	public DialogEngine()
	{
		this(DialogTypes.get());
	}

	public DialogEngine(DialogTypes types)
	{
		this.types = types;
	}

	/**
	 * How an option stands for the player right now.
	 */
	public static class Evaluation
	{
		public final DialogModel.Option option;
		public final boolean available;
		/** Descriptions of the conditions that do not hold. */
		public final List<String> unmetConditions;
		/** {@code null} if the option always succeeds. */
		public final Integer chance;
		/** How each modifier contributed to the chance. */
		public final List<String> chanceExplanation;

		Evaluation(DialogModel.Option option, boolean available, List<String> unmetConditions, Integer chance, List<String> chanceExplanation)
		{
			this.option = option;
			this.available = available;
			this.unmetConditions = unmetConditions;
			this.chance = chance;
			this.chanceExplanation = chanceExplanation;
		}
	}

	/**
	 * What a choice led to.
	 */
	public static class Result
	{
		/** Only a full success; see {@link #roll}. */
		public final boolean success;
		public final DialogModel.Roll roll;
		/** {@code null} if the option always succeeds. */
		public final Integer chance;
		/** {@code null} ends the dialog. */
		public final Integer nextNodeId;
		/** What each action did, or in a preview would do. */
		public final List<String> actions;
		/** What the player is told about the actions; hidden actions are left out. */
		public final List<String> playerMessages;

		Result(DialogModel.Roll roll, Integer chance, Integer nextNodeId, List<String> actions, List<String> playerMessages)
		{
			this.roll = roll;
			this.success = roll == DialogModel.Roll.SUCCESS;
			this.chance = chance;
			this.nextNodeId = nextNodeId;
			this.actions = actions;
			this.playerMessages = playerMessages;
		}
	}

	public Evaluation evaluate(DialogModel.Option option, DialogContext context)
	{
		List<String> unmet = new ArrayList<>();
		if (option.oncePerUser && context.hasChosen(option.id))
		{
			unmet.add("nur einmal wählbar, bereits gewählt");
		}
		for (DialogModel.Condition condition : option.conditions)
		{
			DialogFactor factor = types.factor(condition.type);
			double value = factor.value(context, condition.params);
			if (!condition.comparison.test(value, condition.threshold))
			{
				unmet.add(factor.describe(condition.params) + " " + condition.comparison.getSymbol() + " "
						+ format(condition.threshold) + " (ist " + format(value) + ")");
			}
		}

		Integer chance = null;
		List<String> explanation = new ArrayList<>();
		if (option.baseChance != null)
		{
			double total = option.baseChance;
			explanation.add("Basis " + option.baseChance + " %");
			for (DialogModel.Modifier modifier : option.modifiers)
			{
				DialogFactor factor = types.factor(modifier.type);
				double value = clamp(factor.value(context, modifier.params), modifier.min, modifier.max);
				double contribution = modifier.factor * value;
				total += contribution;
				explanation.add(factor.describe(modifier.params) + " = " + format(value) + " × " + format(modifier.factor)
						+ " → " + (contribution >= 0 ? "+" : "") + format(contribution) + " %");
			}
			chance = (int) Math.round(Math.max(0, Math.min(100, total)));
		}
		return new Evaluation(option, unmet.isEmpty(), unmet, chance, explanation);
	}

	/**
	 * Carries out the choice of an option: rolls for success and runs the actions for that outcome.
	 *
	 * @param preview Only describe the actions, do not run them
	 * @param forcedOutcome The outcome to take instead of rolling; for previews
	 * @throws IllegalStateException if the option is not available
	 */
	public Result choose(DialogModel.Option option, DialogContext context, boolean preview, DialogModel.Roll forcedOutcome)
	{
		Evaluation evaluation = evaluate(option, context);
		if (!evaluation.available)
		{
			throw new IllegalStateException("Diese Antwort steht dir nicht zur Verfügung.");
		}

		DialogModel.Roll roll;
		if (forcedOutcome != null)
		{
			roll = forcedOutcome;
		}
		else if (evaluation.chance == null)
		{
			roll = DialogModel.Roll.SUCCESS;
		}
		else
		{
			double dice = context.random.getAsDouble();
			int partial = option.partialChance == null ? 0 : option.partialChance;
			roll = dice < evaluation.chance ? DialogModel.Roll.SUCCESS
					: dice < evaluation.chance + partial ? DialogModel.Roll.PARTIAL
					: DialogModel.Roll.FAILURE;
		}

		List<String> done = new ArrayList<>();
		List<String> messages = new ArrayList<>();
		for (DialogModel.ActionRef ref : option.actions)
		{
			if (!ref.outcome.appliesTo(roll))
			{
				continue;
			}
			DialogAction action = types.action(ref.type);
			if (!preview)
			{
				action.execute(context, ref.params);
			}
			done.add(action.describe(ref.params));
			String message = action.playerMessage(context, ref.params);
			if (message != null)
			{
				messages.add(message);
			}
		}

		Integer next;
		switch (roll)
		{
			case SUCCESS:
				next = option.nextNodeId;
				break;
			case PARTIAL:
				next = option.partialNodeId;
				break;
			default:
				next = option.failureNodeId;
		}
		return new Result(roll, evaluation.chance, next, done, messages);
	}

	private static double clamp(double value, Double min, Double max)
	{
		double result = value;
		if (min != null)
		{
			result = Math.max(min, result);
		}
		if (max != null)
		{
			result = Math.min(max, result);
		}
		return result;
	}

	static String format(double value)
	{
		if (value == Math.rint(value))
		{
			return Long.toString((long) value);
		}
		return String.format(Locale.GERMANY, "%.2f", value);
	}
}
