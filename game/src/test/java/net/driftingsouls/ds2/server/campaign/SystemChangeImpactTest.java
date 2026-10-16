package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.config.StarSystem;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class SystemChangeImpactTest
{
	private static final CampaignObject INNEN = new CampaignObject(CampaignObject.Type.SHIP, 1, "Innen", 7, 50, 50, 42, "Spieler");
	private static final CampaignObject RECHTS = new CampaignObject(CampaignObject.Type.BASE, 2, "Rechts", 7, 150, 10, 42, "Spieler");
	private static final CampaignObject UNTEN = new CampaignObject(CampaignObject.Type.SHIP, 3, "Unten", 7, 10, 120, 43, "Andere");
	private static final List<CampaignObject> OBJEKTE = List.of(INNEN, RECHTS, UNTEN);

	@Test
	public void gegebenKeineRelevanteAenderung_ofUpdate_sollteNichtsBetreffen()
	{
		assertTrue(SystemChangeImpact.ofUpdate(200, 200, StarSystem.Access.NORMAL, 200, 200, StarSystem.Access.NORMAL, OBJEKTE).isEmpty());
		assertTrue("Vergrößern", SystemChangeImpact.ofUpdate(200, 200, StarSystem.Access.NORMAL, 300, 250, StarSystem.Access.NORMAL, OBJEKTE).isEmpty());
	}

	@Test
	public void gegebenEinVerkleinertesSystem_ofUpdate_sollteNurObjekteAusserhalbBetreffen()
	{
		SystemChangeImpact impact = SystemChangeImpact.ofUpdate(200, 200, StarSystem.Access.NORMAL, 100, 100, StarSystem.Access.NORMAL, OBJEKTE);

		assertEquals(List.of(RECHTS, UNTEN), impact.objects);
		assertEquals(1, impact.reasons.size());
	}

	@Test
	public void gegebenEinObjektAufDemNeuenRand_ofUpdate_sollteEsNichtBetreffen()
	{
		SystemChangeImpact impact = SystemChangeImpact.ofUpdate(200, 200, StarSystem.Access.NORMAL, 150, 120, StarSystem.Access.NORMAL, OBJEKTE);

		assertTrue(impact.isEmpty());
	}

	@Test
	public void gegebenGeaenderteZugriffsrechte_ofUpdate_sollteAlleObjekteBetreffen()
	{
		SystemChangeImpact impact = SystemChangeImpact.ofUpdate(200, 200, StarSystem.Access.NORMAL, 100, 200, StarSystem.Access.NPC, OBJEKTE);

		assertEquals(OBJEKTE, impact.objects);
		assertEquals("Verkleinern und Zugriffsrechte", 2, impact.reasons.size());
	}

	@Test
	public void gegebenKeineSpielerobjekte_ofUpdate_sollteNichtsBetreffen()
	{
		assertTrue(SystemChangeImpact.ofUpdate(200, 200, StarSystem.Access.NORMAL, 10, 10, StarSystem.Access.ADMIN, List.of()).isEmpty());
	}

	@Test
	public void gegebenEinGeloeschtesSystem_ofDelete_sollteAlleObjekteBetreffen()
	{
		assertEquals(OBJEKTE, SystemChangeImpact.ofDelete(OBJEKTE).objects);
		assertTrue(SystemChangeImpact.ofDelete(List.of()).isEmpty());
	}

	@Test
	public void gegebenDasEntfernenAllerBasen_ofClearingBases_sollteNurBasenBetreffen()
	{
		assertEquals(List.of(RECHTS), SystemChangeImpact.ofClearingBases(OBJEKTE).objects);
		assertTrue(SystemChangeImpact.ofClearingBases(List.of(INNEN)).isEmpty());
	}
}
