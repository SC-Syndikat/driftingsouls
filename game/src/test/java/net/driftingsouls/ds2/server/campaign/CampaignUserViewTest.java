package net.driftingsouls.ds2.server.campaign;

import org.junit.Test;

import java.util.OptionalLong;

import static org.junit.Assert.assertEquals;

public class CampaignUserViewTest
{
	@Test
	public void describeLastSeen_sollteDieZeitSeitDerLetztenAktivitaetBeschreiben()
	{
		assertEquals("online", CampaignUserView.describeLastSeen(OptionalLong.of(10), true));
		assertEquals("seit dem Serverstart nicht gesehen", CampaignUserView.describeLastSeen(OptionalLong.empty(), false));
		assertEquals("zuletzt aktiv vor 12 Min.", CampaignUserView.describeLastSeen(OptionalLong.of(12 * 60 + 5), false));
		assertEquals("zuletzt aktiv vor 5 Std.", CampaignUserView.describeLastSeen(OptionalLong.of(5 * 3600), false));
		assertEquals("zuletzt aktiv vor 3 Tagen", CampaignUserView.describeLastSeen(OptionalLong.of(3 * 86400), false));
	}
}
