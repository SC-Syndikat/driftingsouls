package net.driftingsouls.ds2.server.tick.regular;

import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignRepository;
import net.driftingsouls.ds2.server.tick.TickController;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Counts the campaign ticks of the running campaign, which dialog conditions and interaction cooldowns
 * are based on. Does nothing in a regular tick.
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

		CampaignRepository repository = new CampaignRepository();
		Optional<Campaign> campaign = repository.findOpen().filter(Campaign::isRunning);
		if (campaign.isEmpty())
		{
			log("Keine laufende Kampagne");
			return;
		}
		repository.incrementTickCount(campaign.get().getId());
		log("Kampagnentick " + (campaign.get().getTickCount() + 1) + " der Kampagne " + campaign.get().getName());
	}
}
