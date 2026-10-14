package net.driftingsouls.ds2.server.discord;

import java.util.ArrayList;
import java.util.List;

/**
 * Ergebnis der Verbindungspruefung des Discord-Bots fuer den Adminbereich.
 */
public class DiscordPruefung
{
	public static class Punkt
	{
		public final boolean ok;
		public final String text;

		Punkt(boolean ok, String text)
		{
			this.ok = ok;
			this.text = text;
		}
	}

	public final List<Punkt> punkte = new ArrayList<>();

	void ok(String text)
	{
		punkte.add(new Punkt(true, text));
	}

	void fehler(String text)
	{
		punkte.add(new Punkt(false, text));
	}
}
