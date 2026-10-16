package net.driftingsouls.ds2.server.campaign.dialog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

/**
 * Something about the player's situation that a dialog option can depend on: an officer's skill, the
 * relation to the NPC, cargo, fleet strength and so on.
 *
 * <p>A factor yields a number. As a condition it is compared with a threshold; as a modifier it raises or
 * lowers the option's chance of success. Yes/no factors yield 1 or 0.
 *
 * <p>Implementations are found by their {@link Type} annotation and need a no-argument constructor.
 */
public interface DialogFactor
{
	/**
	 * Marks a factor implementation. {@link #value()} is the id stored in the database.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	@interface Type
	{
		String value();
	}

	String getLabel();

	List<ParamDef> getParams();

	double value(DialogContext context, Params params);

	/**
	 * @return A short description of the factor with these parameters, for the editor and the preview
	 */
	String describe(Params params);
}
