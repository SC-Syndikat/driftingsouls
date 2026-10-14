package net.driftingsouls.ds2.server.discord;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein im Ingame-Chat waehlbarer Discord-Kanal.
 */
public class DiscordChannel
{
	public final String id;
	public final String name;

	public DiscordChannel(String id, String name)
	{
		this.id = id;
		this.name = name;
	}

	/**
	 * Liest die Kanalliste aus dem Konfigurationswert ein.
	 * Format: <code>KanalID:Name;KanalID:Name</code>. Ungueltige Eintraege werden ignoriert.
	 * @param config Der Konfigurationswert
	 * @return Die Kanaele in der konfigurierten Reihenfolge
	 */
	public static List<DiscordChannel> parse(String config)
	{
		List<DiscordChannel> result = new ArrayList<>();
		if (config == null)
		{
			return result;
		}
		for (String entry : config.split(";"))
		{
			String trimmed = entry.trim();
			int separator = trimmed.indexOf(':');
			String id = (separator == -1 ? trimmed : trimmed.substring(0, separator)).trim();
			if (!id.matches("\\d+"))
			{
				continue;
			}
			String name = separator == -1 ? "" : trimmed.substring(separator + 1).trim();
			result.add(new DiscordChannel(id, name.isEmpty() ? id : name));
		}
		return result;
	}
}
