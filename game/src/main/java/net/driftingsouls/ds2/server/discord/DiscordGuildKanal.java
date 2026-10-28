package net.driftingsouls.ds2.server.discord;

/**
 * Ein Textkanal, wie ihn Discord beim Synchronisieren liefert.
 */
public class DiscordGuildKanal
{
	public final String id;
	public final String name;
	public final String kategorie;
	/**
	 * Sortierschluessel ueber Kategorie und Position innerhalb der Kategorie.
	 */
	public final int position;
	/**
	 * Fehlende Rechte des Bots in diesem Kanal oder <code>null</code>, falls alles passt.
	 */
	public final String hinweis;
	/**
	 * <code>true</code>, falls @everyone den Kanal sehen darf.
	 */
	public final boolean oeffentlich;
	/**
	 * Wer den Kanal mitlesen kann, siehe {@link DiscordService#rechteSignatur}.
	 */
	public final String signatur;

	public DiscordGuildKanal(String id, String name, String kategorie, int position, String hinweis, boolean oeffentlich, String signatur)
	{
		this.id = id;
		this.name = name;
		this.kategorie = kategorie;
		this.position = position;
		this.hinweis = hinweis;
		this.oeffentlich = oeffentlich;
		this.signatur = signatur;
	}
}
