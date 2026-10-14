package net.driftingsouls.ds2.server.discord;

import java.util.List;

/**
 * Eine fuer die Anzeige im Ingame-Chat aufbereitete Discord-Nachricht.
 * Der Text ist reiner Text (kein HTML) und muss beim Anzeigen escaped werden.
 */
public class DiscordChatMessage
{
	public static class Attachment
	{
		public final String name;
		public final String url;

		public Attachment(String name, String url)
		{
			this.name = name;
			this.url = url;
		}
	}

	public final String id;
	public final String author;
	public final String avatarUrl;
	public final String text;
	/**
	 * Der Zeitpunkt im ISO-8601-Format.
	 */
	public final String timestamp;
	/**
	 * <code>true</code>, falls die Nachricht aus dem Spiel heraus gesendet wurde.
	 */
	public final boolean ingame;
	public final List<Attachment> attachments;

	public DiscordChatMessage(String id, String author, String avatarUrl, String text, String timestamp, boolean ingame, List<Attachment> attachments)
	{
		this.id = id;
		this.author = author;
		this.avatarUrl = avatarUrl;
		this.text = text;
		this.timestamp = timestamp;
		this.ingame = ingame;
		this.attachments = attachments;
	}
}
