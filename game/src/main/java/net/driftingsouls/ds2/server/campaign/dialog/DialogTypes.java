package net.driftingsouls.ds2.server.campaign.dialog;

import net.driftingsouls.ds2.server.framework.AnnotationUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * All dialog factors and actions, found on the classpath by their annotations.
 */
public final class DialogTypes
{
	private static volatile DialogTypes instance;

	private final Map<String, DialogFactor> factors;
	private final Map<String, DialogAction> actions;

	DialogTypes(Map<String, DialogFactor> factors, Map<String, DialogAction> actions)
	{
		this.factors = Collections.unmodifiableMap(factors);
		this.actions = Collections.unmodifiableMap(actions);
	}

	public static DialogTypes get()
	{
		DialogTypes result = instance;
		if (result == null)
		{
			synchronized (DialogTypes.class)
			{
				if (instance == null)
				{
					instance = scan();
				}
				result = instance;
			}
		}
		return result;
	}

	private static DialogTypes scan()
	{
		Map<String, DialogFactor> factors = new LinkedHashMap<>();
		for (Class<?> cls : AnnotationUtils.INSTANCE.findClassesWithAnnotation(DialogFactor.Type.class))
		{
			factors.put(cls.getAnnotation(DialogFactor.Type.class).value(), (DialogFactor) instantiate(cls));
		}
		Map<String, DialogAction> actions = new LinkedHashMap<>();
		for (Class<?> cls : AnnotationUtils.INSTANCE.findClassesWithAnnotation(DialogAction.Type.class))
		{
			actions.put(cls.getAnnotation(DialogAction.Type.class).value(), (DialogAction) instantiate(cls));
		}
		return new DialogTypes(factors, actions);
	}

	private static Object instantiate(Class<?> cls)
	{
		try
		{
			return cls.getDeclaredConstructor().newInstance();
		}
		catch (ReflectiveOperationException e)
		{
			throw new IllegalStateException("Kann " + cls.getName() + " nicht instanziieren", e);
		}
	}

	public Map<String, DialogFactor> getFactors()
	{
		return factors;
	}

	public Map<String, DialogAction> getActions()
	{
		return actions;
	}

	public DialogFactor factor(String id)
	{
		DialogFactor factor = factors.get(id);
		if (factor == null)
		{
			throw new IllegalArgumentException("Unbekannter Dialog-Faktor: " + id);
		}
		return factor;
	}

	public DialogAction action(String id)
	{
		DialogAction action = actions.get(id);
		if (action == null)
		{
			throw new IllegalArgumentException("Unbekannte Dialog-Aktion: " + id);
		}
		return action;
	}
}
