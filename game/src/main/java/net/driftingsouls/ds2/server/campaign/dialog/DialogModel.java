package net.driftingsouls.ds2.server.campaign.dialog;

import java.util.List;

/**
 * The stored shape of a dialog: nodes, their options, and each option's conditions, chance modifiers
 * and actions.
 */
public final class DialogModel
{
	private DialogModel()
	{
		// namespace only
	}

	/**
	 * What the NPC says, followed by the player's options.
	 */
	public static class Node
	{
		public final int id;
		/** For the game master only. */
		public final String title;
		public final String speakerName;
		public final String image;
		/** BBCode. */
		public final String text;
		/** The player must answer; the dialog cannot be left here. */
		public final boolean forced;
		public final List<Option> options;

		public Node(int id, String title, String speakerName, String image, String text, boolean forced, List<Option> options)
		{
			this.forced = forced;
			this.id = id;
			this.title = title;
			this.speakerName = speakerName;
			this.image = image;
			this.text = text;
			this.options = options;
		}

		public Option option(int optionId)
		{
			return options.stream().filter(o -> o.id == optionId).findFirst().orElse(null);
		}
	}

	/**
	 * An answer the player can choose.
	 */
	public static class Option
	{
		public final int id;
		public final int nodeId;
		public final int sort;
		public final String text;
		/** {@code null} ends the dialog. */
		public final Integer nextNodeId;
		/** {@code null} ends the dialog on failure. */
		public final Integer failureNodeId;
		/** {@code null}: always succeeds. */
		public final Integer baseChance;
		public final boolean hideIfUnavailable;
		/** The player may choose it only once per campaign. */
		public final boolean oncePerUser;
		/** Percentage points after the success chance that count as partial success; {@code null}: none. */
		public final Integer partialChance;
		/** {@code null} ends the dialog on partial success. */
		public final Integer partialNodeId;
		public final List<Condition> conditions;
		public final List<Modifier> modifiers;
		public final List<ActionRef> actions;

		public Option(int id, int nodeId, int sort, String text, Integer nextNodeId, Integer failureNodeId, Integer baseChance,
					  boolean hideIfUnavailable, boolean oncePerUser, List<Condition> conditions, List<Modifier> modifiers, List<ActionRef> actions)
		{
			this(id, nodeId, sort, text, nextNodeId, failureNodeId, baseChance, null, null, hideIfUnavailable, oncePerUser, conditions, modifiers, actions);
		}

		public Option(int id, int nodeId, int sort, String text, Integer nextNodeId, Integer failureNodeId, Integer baseChance,
					  Integer partialChance, Integer partialNodeId, boolean hideIfUnavailable, boolean oncePerUser,
					  List<Condition> conditions, List<Modifier> modifiers, List<ActionRef> actions)
		{
			this.oncePerUser = oncePerUser;
			this.partialChance = partialChance;
			this.partialNodeId = partialNodeId;
			this.id = id;
			this.nodeId = nodeId;
			this.sort = sort;
			this.text = text;
			this.nextNodeId = nextNodeId;
			this.failureNodeId = failureNodeId;
			this.baseChance = baseChance;
			this.hideIfUnavailable = hideIfUnavailable;
			this.conditions = conditions;
			this.modifiers = modifiers;
			this.actions = actions;
		}
	}

	/**
	 * The option is available only if {@code factor(params) comparison threshold}.
	 */
	public static class Condition
	{
		public final int id;
		public final String type;
		public final Params params;
		public final Comparison comparison;
		public final double threshold;

		public Condition(int id, String type, Params params, Comparison comparison, double threshold)
		{
			this.id = id;
			this.type = type;
			this.params = params;
			this.comparison = comparison;
			this.threshold = threshold;
		}
	}

	/**
	 * Adds {@code factor * clamp(value, min, max)} percentage points to the option's chance.
	 */
	public static class Modifier
	{
		public final int id;
		public final String type;
		public final Params params;
		public final double factor;
		public final Double min;
		public final Double max;

		public Modifier(int id, String type, Params params, double factor, Double min, Double max)
		{
			this.id = id;
			this.type = type;
			this.params = params;
			this.factor = factor;
			this.min = min;
			this.max = max;
		}
	}

	/**
	 * When an action of an option runs.
	 */
	public enum Outcome
	{
		ALWAYS("immer"),
		SUCCESS("bei Erfolg"),
		PARTIAL("bei Teilerfolg"),
		FAILURE("bei Misserfolg");

		private final String label;

		Outcome(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}

		public boolean appliesTo(Roll roll)
		{
			switch (this)
			{
				case ALWAYS:
					return true;
				case SUCCESS:
					return roll == Roll.SUCCESS;
				case PARTIAL:
					return roll == Roll.PARTIAL;
				default:
					return roll == Roll.FAILURE;
			}
		}
	}

	/**
	 * How a choice turned out.
	 */
	public enum Roll
	{
		SUCCESS("Erfolg"),
		PARTIAL("Teilerfolg"),
		FAILURE("Misserfolg");

		private final String label;

		Roll(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	public static class ActionRef
	{
		public final int id;
		public final int sort;
		public final Outcome outcome;
		public final String type;
		public final Params params;

		public ActionRef(int id, int sort, Outcome outcome, String type, Params params)
		{
			this.id = id;
			this.sort = sort;
			this.outcome = outcome;
			this.type = type;
			this.params = params;
		}
	}
}
