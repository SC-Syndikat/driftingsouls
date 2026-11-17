package net.driftingsouls.ds2.server.campaign;

/**
 * A campaign operation was refused. The message is shown to the game master.
 */
public class CampaignException extends RuntimeException
{
	public CampaignException(String message)
	{
		super(message);
	}
}
