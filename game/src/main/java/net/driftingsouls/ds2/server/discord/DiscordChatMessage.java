package net.driftingsouls.ds2.server.discord;

import java.util.List;

/**
 * Eine fuer die Anzeige im Ingame-Chat aufbereitete Discord-Nachricht.
 * Der Text ist reiner Text (kein HTML) und muss beim Anzeigen escaped werden.
 */
public class DiscordChatMessage
{
	public static final String ART_BILD = "bild";
	public static final String ART_VIDEO = "video";
	public static final String ART_DATEI = "datei";

	/**
	 * Ein Anhang oder ein von Discord eingebettetes Bild bzw. Video.
	 */
	public static class Attachment
	{
		/**
		 * {@link #ART_BILD} und {@link #ART_VIDEO} werden direkt angezeigt, {@link #ART_DATEI} als Link.
		 */
		public final String art;
		public final String name;
		public final String url;
		/**
		 * Die Abmessungen in Pixeln, falls Discord sie kennt, sonst 0.
		 */
		public final int breite;
		public final int hoehe;

		public Attachment(String art, String name, String url, int breite, int hoehe)
		{
			this.art = art;
			this.name = name;
			this.url = url;
			this.breite = breite;
			this.hoehe = hoehe;
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
	/**
	 * Die Rollenfarbe des Verfassers als <code>#rrggbb</code> oder <code>null</code>. Bei Nachrichten
	 * aus dem Spiel setzt sie erst der Controller, der den Verfasser kennt.
	 */
	public volatile String farbe;

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
