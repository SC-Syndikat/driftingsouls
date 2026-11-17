package net.driftingsouls.ds2.server.tick.regular;

import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignRepository;
import net.driftingsouls.ds2.server.campaign.CampaignService;
import net.driftingsouls.ds2.server.tick.TickController;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Counts the campaign ticks of the running campaign, which dialog conditions and interaction cooldowns
 * are based on, and tells the participating players. Does nothing in a regular tick.
 */
@Service
@Scope(value = "thread", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class CampaignTick extends TickController
{
	@Override
	protected void prepare()
	{
		// EMPTY
	}

	@Override
	protected void tick()
	{
		if (!isCampaignTick())
		{
			return;
		}

		Optional<Campaign> campaign = new CampaignRepository().findOpen().filter(Campaign::isRunning);
		if (campaign.isEmpty())
		{
			log("Keine laufende Kampagne");
			return;
		}
		// Count and messages together: the messages are written by Hibernate, the count by jOOQ on the same connection
		var transaction = getEM().getTransaction();
		transaction.begin();
		try
		{
			new CampaignService(getEM()).recordCampaignTick(campaign.get());
			transaction.commit();
		}
		catch (RuntimeException e)
		{
			transaction.rollback();
			throw e;
		}
		log("Kampagnentick " + (campaign.get().getTickCount() + 1) + " der Kampagne " + campaign.get().getName());
	}
}
