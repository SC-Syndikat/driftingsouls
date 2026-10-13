package net.driftingsouls.ds2.server.campaign.dialog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

/**
 * Something a dialog choice makes happen: a reward, a changed relation, a battle, reinforcements.
 *
 * <p>New behaviour is added as a new implementation; there is no scripting language. Implementations are
 * found by their {@link Type} annotation and need a no-argument constructor.
 */
public interface DialogAction
{
	/**
	 * Marks an action implementation. {@link #value()} is the id stored in the database.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	@interface Type
	{
		String value();
	}

	/**
	 * Groups actions in the editor.
	 */
	enum Group
	{
		DIPLOMACY("Diplomatie"),
		REWARD("Belohnung"),
		BATTLE("Schlacht"),
		SCRIPT("Script");

		private final String label;

		Group(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	String getLabel();

	Group getGroup();

	List<ParamDef> getParams();

	/**
	 * Performs the action. Throws a {@link net.driftingsouls.ds2.server.campaign.CampaignException} to
	 * refuse; the whole choice is then rolled back and the message shown to the player.
	 */
	void execute(DialogContext context, Params params);

	/**
	 * @return What the action does with these parameters, for the editor, the preview and the log
	 */
	String describe(Params params);

	/**
	 * @return What the player is told after the action ran, or {@code null} to keep it hidden, e.g. for
	 * campaign variables or reinforcements that should come as a surprise
	 */
	default String playerMessage(DialogContext context, Params params)
	{
		return null;
	}
}
