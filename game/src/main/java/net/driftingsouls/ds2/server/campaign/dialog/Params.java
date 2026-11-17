package net.driftingsouls.ds2.server.campaign.dialog;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The parameter values of one configured factor or action, stored as JSON.
 */
public class Params
{
	private static final Gson GSON = new Gson();
	private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, String>>() {}.getType();

	private final Map<String, String> values;

	public Params(Map<String, String> values)
	{
		this.values = values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
	}

	public static Params of(String... namesAndValues)
	{
		Map<String, String> values = new LinkedHashMap<>();
		for (int i = 0; i + 1 < namesAndValues.length; i += 2)
		{
			values.put(namesAndValues[i], namesAndValues[i + 1]);
		}
		return new Params(values);
	}

	public static Params fromJson(String json)
	{
		if (json == null || json.isBlank())
		{
			return new Params(null);
		}
		Map<String, String> values = GSON.fromJson(json, MAP_TYPE);
		return new Params(values);
	}

	public String toJson()
	{
		return GSON.toJson(values);
	}

	public String get(String name)
	{
		String value = values.get(name);
		return value == null ? "" : value.trim();
	}

	public boolean has(String name)
	{
		return !get(name).isEmpty();
	}

	public int getInt(String name)
	{
		return has(name) ? Integer.parseInt(get(name)) : 0;
	}

	public long getLong(String name)
	{
		return has(name) ? Long.parseLong(get(name)) : 0;
	}

	public Map<String, String> asMap()
	{
		return Collections.unmodifiableMap(values);
	}
}
