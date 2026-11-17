package net.driftingsouls.ds2.server.campaign;

import javax.persistence.EntityManager;

/**
 * For tests that commit (DBTest): all test classes of a build share one database, so what such a test leaves
 * behind meets the tests after it. An open campaign would make every later {@code prepare} fail.
 */
public final class CommittedCampaignTests
{
	private CommittedCampaignTests()
	{
		// static only
	}

	/**
	 * Ends every open campaign and its battles, as ending it in the game would. Call in a transaction.
	 */
	public static void endOpenCampaigns(EntityManager em)
	{
		em.createNativeQuery("DELETE FROM battles_ships").executeUpdate();
		em.createNativeQuery("UPDATE ships SET battle = NULL, battleAction = 0 WHERE battle IS NOT NULL").executeUpdate();
		em.createNativeQuery("DELETE FROM battles").executeUpdate();
		em.createNativeQuery("DELETE FROM dialog_session").executeUpdate();
		em.createNativeQuery("UPDATE campaign SET status = 'ENDED', ended_at = 0 WHERE status <> 'ENDED'").executeUpdate();
		em.createNativeQuery("UPDATE users SET campaign_participant = 0, tick_ready = 0").executeUpdate();
	}
}
