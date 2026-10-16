package net.driftingsouls.ds2.server.campaign;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

public class GameMasterRestrictionTest
{
	private final GameMasterRestriction restriction = new GameMasterRestriction(true, Set.of(7), Set.of(-5, 42));

	@Test
	public void gegebenEinGameMaster_sollteNurKampagnensystemeErlauben()
	{
		assertTrue(restriction.isSystemAllowed(7));
		assertFalse(restriction.isSystemAllowed(1));
	}

	@Test
	public void gegebenEinGameMaster_sollteNurTeilnehmendeNpcsUndNiemandenAlsBesitzerErlauben()
	{
		assertTrue(restriction.isOwnerAllowed(-5));
		assertTrue(restriction.isOwnerAllowed(0));
		assertFalse(restriction.isOwnerAllowed(-6));
		assertFalse("Teilnehmende Spieler sind keine NPCs", restriction.isOwnerAllowed(42));
	}

	@Test
	public void gegebenEinGameMaster_checkMasterLogin_sollteNurTeilnehmendeNpcsErlauben()
	{
		restriction.checkMasterLogin(-5);
		assertEquals(Set.of(-5), restriction.getMasterLoginIds());
		assertThrows(CampaignException.class, () -> restriction.checkMasterLogin(42));
		assertThrows(CampaignException.class, () -> restriction.checkMasterLogin(-6));
	}

	@Test
	public void gegebenKeineEinschraenkung_sollteAllesErlauben()
	{
		GameMasterRestriction none = new GameMasterRestriction(false, Set.of(), Set.of());

		assertFalse(none.applies());
		assertTrue(none.isAllowed(1, 42));
		none.checkMasterLogin(42);
	}
}
