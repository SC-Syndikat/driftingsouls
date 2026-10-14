package net.driftingsouls.ds2.server.discord;

/**
 * Ein ueber OAuth2 verknuepfter Discord-Account.
 */
public class DiscordUser
{
	public final String id;
	public final String displayName;
	public final String avatar;

	public DiscordUser(String id, String displayName, String avatar)
	{
		this.id = id;
		this.displayName = displayName;
		this.avatar = avatar;
	}

	/**
	 * Gibt die URL des Avatars eines Discord-Accounts zurueck. Ohne eigenen Avatar
	 * wird einer der Standardavatare von Discord verwendet.
	 * @param userId Die ID des Accounts (oder Webhooks)
	 * @param avatar Der Hash des Avatars oder <code>null</code>
	 * @return Die URL
	 */
	public static String avatarUrl(String userId, String avatar)
	{
		if (avatar != null && !avatar.isEmpty())
		{
			return "https://cdn.discordapp.com/avatars/" + userId + "/" + avatar + ".png?size=64";
		}
		long index;
		try
		{
			index = (Long.parseUnsignedLong(userId) >>> 22) % 6;
		}
		catch (NumberFormatException e)
		{
			index = 0;
		}
		return "https://cdn.discordapp.com/embed/avatars/" + index + ".png";
	}
}
