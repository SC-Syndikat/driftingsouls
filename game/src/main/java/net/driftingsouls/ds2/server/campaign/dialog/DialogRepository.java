package net.driftingsouls.ds2.server.campaign.dialog;

import net.driftingsouls.ds2.server.entities.jooq.tables.records.DialogNodeRecord;
import net.driftingsouls.ds2.server.entities.jooq.tables.records.DialogOptionRecord;
import org.jooq.DSLContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static net.driftingsouls.ds2.server.campaign.CampaignDb.withDb;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.DIALOG_NODE;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.DIALOG_OPTION;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.DIALOG_OPTION_ACTION;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.DIALOG_OPTION_CONDITION;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.DIALOG_OPTION_MODIFIER;
import static net.driftingsouls.ds2.server.entities.jooq.Tables.INTERACTION;

/**
 * Persistence for dialogs. All dialog tables are jOOQ-owned.
 */
public class DialogRepository
{
	/**
	 * @return All nodes with their options, ordered by id
	 */
	public List<DialogModel.Node> findAll()
	{
		return withDb(db -> {
			List<DialogModel.Node> nodes = new ArrayList<>();
			for (DialogNodeRecord record : db.selectFrom(DIALOG_NODE).orderBy(DIALOG_NODE.ID).fetch())
			{
				nodes.add(toNode(db, record));
			}
			return nodes;
		});
	}

	public Optional<DialogModel.Node> findNode(int id)
	{
		return withDb(db -> db.selectFrom(DIALOG_NODE).where(DIALOG_NODE.ID.eq(id)).fetchOptional().map(r -> toNode(db, r)));
	}

	public int createNode(String title, String speakerName, String image, String text)
	{
		return withDb(db -> {
			DialogNodeRecord record = db.newRecord(DIALOG_NODE);
			record.setTitle(title);
			record.setSpeakerName(speakerName);
			record.setImage(image);
			record.setText(text);
			record.store();
			return record.getId();
		});
	}

	public void updateNode(int id, String title, String speakerName, String image, String text)
	{
		withDb(db -> db.update(DIALOG_NODE)
				.set(DIALOG_NODE.TITLE, title)
				.set(DIALOG_NODE.SPEAKER_NAME, speakerName)
				.set(DIALOG_NODE.IMAGE, image)
				.set(DIALOG_NODE.TEXT, text)
				.where(DIALOG_NODE.ID.eq(id))
				.execute());
	}

	/**
	 * @return Interactions starting at the node; a node they start at cannot be deleted
	 */
	public List<Integer> findInteractionsStartingAt(int nodeId)
	{
		return withDb(db -> db.select(INTERACTION.ID).from(INTERACTION).where(INTERACTION.START_NODE_ID.eq(nodeId)).fetch(INTERACTION.ID));
	}

	/**
	 * @return Options of other nodes leading to the node
	 */
	public List<Integer> findOptionsLeadingTo(int nodeId)
	{
		return withDb(db -> db.select(DIALOG_OPTION.ID)
				.from(DIALOG_OPTION)
				.where(DIALOG_OPTION.NEXT_NODE_ID.eq(nodeId).or(DIALOG_OPTION.FAILURE_NODE_ID.eq(nodeId)).or(DIALOG_OPTION.PARTIAL_NODE_ID.eq(nodeId)))
				.and(DIALOG_OPTION.NODE_ID.ne(nodeId))
				.fetch(DIALOG_OPTION.ID));
	}

	public void setNodeForced(int id, boolean forced)
	{
		withDb(db -> db.update(DIALOG_NODE).set(DIALOG_NODE.FORCED, (byte) (forced ? 1 : 0)).where(DIALOG_NODE.ID.eq(id)).execute());
	}

	public void deleteNode(int id)
	{
		withDb(db -> db.deleteFrom(DIALOG_NODE).where(DIALOG_NODE.ID.eq(id)).execute());
	}

	public int createOption(int nodeId, int sort, String text, Integer nextNodeId, Integer failureNodeId, Integer baseChance, boolean hideIfUnavailable,
							boolean oncePerUser)
	{
		return createOption(nodeId, sort, text, nextNodeId, failureNodeId, baseChance, null, null, hideIfUnavailable, oncePerUser);
	}

	public int createOption(int nodeId, int sort, String text, Integer nextNodeId, Integer failureNodeId, Integer baseChance,
							Integer partialChance, Integer partialNodeId, boolean hideIfUnavailable, boolean oncePerUser)
	{
		return withDb(db -> {
			DialogOptionRecord record = db.newRecord(DIALOG_OPTION);
			applyOption(record, nodeId, sort, text, nextNodeId, failureNodeId, baseChance, hideIfUnavailable, oncePerUser);
			record.setPartialChance(partialChance);
			record.setPartialNodeId(partialNodeId);
			record.store();
			return record.getId();
		});
	}

	public void updateOption(int id, int sort, String text, Integer nextNodeId, Integer failureNodeId, Integer baseChance,
							 Integer partialChance, Integer partialNodeId, boolean hideIfUnavailable, boolean oncePerUser)
	{
		withDb(db -> db.update(DIALOG_OPTION)
				.set(DIALOG_OPTION.SORT, sort)
				.set(DIALOG_OPTION.TEXT, text)
				.set(DIALOG_OPTION.NEXT_NODE_ID, nextNodeId)
				.set(DIALOG_OPTION.FAILURE_NODE_ID, failureNodeId)
				.set(DIALOG_OPTION.BASE_CHANCE, baseChance)
				.set(DIALOG_OPTION.HIDE_IF_UNAVAILABLE, (byte) (hideIfUnavailable ? 1 : 0))
				.set(DIALOG_OPTION.ONCE_PER_USER, (byte) (oncePerUser ? 1 : 0))
				.set(DIALOG_OPTION.PARTIAL_CHANCE, partialChance)
				.set(DIALOG_OPTION.PARTIAL_NODE_ID, partialNodeId)
				.where(DIALOG_OPTION.ID.eq(id))
				.execute());
	}

	public void deleteOption(int id)
	{
		withDb(db -> db.deleteFrom(DIALOG_OPTION).where(DIALOG_OPTION.ID.eq(id)).execute());
	}

	public void addCondition(int optionId, String type, Params params, Comparison comparison, double threshold)
	{
		withDb(db -> db.insertInto(DIALOG_OPTION_CONDITION, DIALOG_OPTION_CONDITION.OPTION_ID, DIALOG_OPTION_CONDITION.TYPE,
						DIALOG_OPTION_CONDITION.PARAMS, DIALOG_OPTION_CONDITION.COMPARATOR, DIALOG_OPTION_CONDITION.THRESHOLD)
				.values(optionId, type, params.toJson(), comparison.getSymbol(), BigDecimal.valueOf(threshold))
				.execute());
	}

	public void deleteCondition(int id)
	{
		withDb(db -> db.deleteFrom(DIALOG_OPTION_CONDITION).where(DIALOG_OPTION_CONDITION.ID.eq(id)).execute());
	}

	public void addModifier(int optionId, String type, Params params, double factor, Double min, Double max)
	{
		withDb(db -> db.insertInto(DIALOG_OPTION_MODIFIER, DIALOG_OPTION_MODIFIER.OPTION_ID, DIALOG_OPTION_MODIFIER.TYPE,
						DIALOG_OPTION_MODIFIER.PARAMS, DIALOG_OPTION_MODIFIER.FACTOR, DIALOG_OPTION_MODIFIER.MIN_VALUE, DIALOG_OPTION_MODIFIER.MAX_VALUE)
				.values(optionId, type, params.toJson(), BigDecimal.valueOf(factor), toDecimal(min), toDecimal(max))
				.execute());
	}

	public void deleteModifier(int id)
	{
		withDb(db -> db.deleteFrom(DIALOG_OPTION_MODIFIER).where(DIALOG_OPTION_MODIFIER.ID.eq(id)).execute());
	}

	public void addAction(int optionId, int sort, DialogModel.Outcome outcome, String type, Params params)
	{
		withDb(db -> db.insertInto(DIALOG_OPTION_ACTION, DIALOG_OPTION_ACTION.OPTION_ID, DIALOG_OPTION_ACTION.SORT,
						DIALOG_OPTION_ACTION.OUTCOME, DIALOG_OPTION_ACTION.TYPE, DIALOG_OPTION_ACTION.PARAMS)
				.values(optionId, sort, outcome.name(), type, params.toJson())
				.execute());
	}

	public void deleteAction(int id)
	{
		withDb(db -> db.deleteFrom(DIALOG_OPTION_ACTION).where(DIALOG_OPTION_ACTION.ID.eq(id)).execute());
	}

	private static DialogModel.Node toNode(DSLContext db, DialogNodeRecord record)
	{
		List<DialogModel.Option> options = new ArrayList<>();
		for (DialogOptionRecord o : db.selectFrom(DIALOG_OPTION)
				.where(DIALOG_OPTION.NODE_ID.eq(record.getId()))
				.orderBy(DIALOG_OPTION.SORT, DIALOG_OPTION.ID)
				.fetch())
		{
			options.add(toOption(db, o));
		}
		return new DialogModel.Node(record.getId(), record.getTitle(), record.getSpeakerName(), record.getImage(), record.getText(), record.getForced() != 0, options);
	}

	private static DialogModel.Option toOption(DSLContext db, DialogOptionRecord o)
	{
		List<DialogModel.Condition> conditions = new ArrayList<>();
		for (var c : db.selectFrom(DIALOG_OPTION_CONDITION).where(DIALOG_OPTION_CONDITION.OPTION_ID.eq(o.getId())).orderBy(DIALOG_OPTION_CONDITION.ID).fetch())
		{
			conditions.add(new DialogModel.Condition(c.getId(), c.getType(), Params.fromJson(c.getParams()),
					Comparison.fromSymbol(c.getComparator()), c.getThreshold().doubleValue()));
		}
		List<DialogModel.Modifier> modifiers = new ArrayList<>();
		for (var m : db.selectFrom(DIALOG_OPTION_MODIFIER).where(DIALOG_OPTION_MODIFIER.OPTION_ID.eq(o.getId())).orderBy(DIALOG_OPTION_MODIFIER.ID).fetch())
		{
			modifiers.add(new DialogModel.Modifier(m.getId(), m.getType(), Params.fromJson(m.getParams()), m.getFactor().doubleValue(),
					fromDecimal(m.getMinValue()), fromDecimal(m.getMaxValue())));
		}
		List<DialogModel.ActionRef> actions = new ArrayList<>();
		for (var a : db.selectFrom(DIALOG_OPTION_ACTION).where(DIALOG_OPTION_ACTION.OPTION_ID.eq(o.getId()))
				.orderBy(DIALOG_OPTION_ACTION.SORT, DIALOG_OPTION_ACTION.ID).fetch())
		{
			actions.add(new DialogModel.ActionRef(a.getId(), a.getSort(), DialogModel.Outcome.valueOf(a.getOutcome()), a.getType(),
					Params.fromJson(a.getParams())));
		}
		return new DialogModel.Option(o.getId(), o.getNodeId(), o.getSort(), o.getText(), o.getNextNodeId(), o.getFailureNodeId(),
				o.getBaseChance(), o.getPartialChance(), o.getPartialNodeId(), o.getHideIfUnavailable() != 0, o.getOncePerUser() != 0, conditions, modifiers, actions);
	}

	private static void applyOption(DialogOptionRecord record, int nodeId, int sort, String text, Integer nextNodeId, Integer failureNodeId,
									Integer baseChance, boolean hideIfUnavailable, boolean oncePerUser)
	{
		record.setOncePerUser((byte) (oncePerUser ? 1 : 0));
		record.setNodeId(nodeId);
		record.setSort(sort);
		record.setText(text);
		record.setNextNodeId(nextNodeId);
		record.setFailureNodeId(failureNodeId);
		record.setBaseChance(baseChance);
		record.setHideIfUnavailable((byte) (hideIfUnavailable ? 1 : 0));
	}

	private static BigDecimal toDecimal(Double value)
	{
		return value == null ? null : BigDecimal.valueOf(value);
	}

	private static Double fromDecimal(BigDecimal value)
	{
		return value == null ? null : value.doubleValue();
	}
}
