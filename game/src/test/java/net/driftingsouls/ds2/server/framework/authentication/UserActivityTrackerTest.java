package net.driftingsouls.ds2.server.framework.authentication;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class UserActivityTrackerTest
{
	private final AtomicLong now = new AtomicLong(1_000_000);
	private final UserActivityTracker tracker = new UserActivityTracker(now::get);

	@Test
	public void gegebenKeineAktivitaet_isOnline_sollteFalseLiefern()
	{
		assertFalse(tracker.isOnline(1, 300));
		assertTrue(tracker.secondsSinceLastActivity(1).isEmpty());
	}

	@Test
	public void gegebenAktivitaetInnerhalbDerSchwelle_isOnline_sollteTrueLiefern()
	{
		tracker.recordActivity(1);
		now.addAndGet(300_000);

		assertTrue(tracker.isOnline(1, 300));
		assertEquals(300, tracker.secondsSinceLastActivity(1).getAsLong());
	}

	@Test
	public void gegebenAktivitaetAusserhalbDerSchwelle_isOnline_sollteFalseLiefern()
	{
		tracker.recordActivity(1);
		now.addAndGet(301_000);

		assertFalse(tracker.isOnline(1, 300));
	}
}
