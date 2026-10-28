package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.config.StarSystem;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class SystemChangeImpactTest
{
	private static final CampaignObject INSIDE = new CampaignObject(CampaignObject.Type.SHIP, 1, "Innen", 7, 50, 50, 42, "Spieler");
	private static final CampaignObject RIGHT = new CampaignObject(CampaignObject.Type.BASE, 2, "Rechts", 7, 150, 10, 42, "Spieler");
	private static final CampaignObject BELOW = new CampaignObject(CampaignObject.Type.SHIP, 3, "Unten", 7, 10, 120, 43, "Andere");
	private static final List<CampaignObject> OBJECTS = List.of(INSIDE, RIGHT, BELOW);

	@Test
	public void givenNoRelevantChange_ofUpdate_shouldAffectNothing()
	{
		assertTrue(SystemChangeImpact.ofUpdate(200, 200, StarSystem.Access.NORMAL, 200, 200, StarSystem.Access.NORMAL, OBJECTS).isEmpty());
		assertTrue("Vergrößern", SystemChangeImpact.ofUpdate(200, 200, StarSystem.Access.NORMAL, 300, 250, StarSystem.Access.NORMAL, OBJECTS).isEmpty());
	}

	@Test
	public void givenAShrunkSystem_ofUpdate_shouldOnlyAffectObjectsOutside()
	{
		SystemChangeImpact impact = SystemChangeImpact.ofUpdate(200, 200, StarSystem.Access.NORMAL, 100, 100, StarSystem.Access.NORMAL, OBJECTS);

		assertEquals(List.of(RIGHT, BELOW), impact.objects);
		assertEquals(1, impact.reasons.size());
	}

	@Test
	public void givenAnObjectOnTheNewEdge_ofUpdate_shouldNotAffectIt()
	{
		SystemChangeImpact impact = SystemChangeImpact.ofUpdate(200, 200, StarSystem.Access.NORMAL, 150, 120, StarSystem.Access.NORMAL, OBJECTS);

		assertTrue(impact.isEmpty());
	}

	@Test
	public void givenChangedAccessRights_ofUpdate_shouldAffectAllObjects()
	{
		SystemChangeImpact impact = SystemChangeImpact.ofUpdate(200, 200, StarSystem.Access.NORMAL, 100, 200, StarSystem.Access.NPC, OBJECTS);

		assertEquals(OBJECTS, impact.objects);
		assertEquals("Verkleinern und Zugriffsrechte", 2, impact.reasons.size());
	}

	@Test
	public void givenNoPlayerObjects_ofUpdate_shouldAffectNothing()
	{
		assertTrue(SystemChangeImpact.ofUpdate(200, 200, StarSystem.Access.NORMAL, 10, 10, StarSystem.Access.ADMIN, List.of()).isEmpty());
	}

	@Test
	public void givenADeletedSystem_ofDelete_shouldAffectAllObjects()
	{
		assertEquals(OBJECTS, SystemChangeImpact.ofDelete(OBJECTS).objects);
		assertTrue(SystemChangeImpact.ofDelete(List.of()).isEmpty());
	}

	@Test
	public void givenRemovingAllBases_ofClearingBases_shouldOnlyAffectBases()
	{
		assertEquals(List.of(RIGHT), SystemChangeImpact.ofClearingBases(OBJECTS).objects);
		assertTrue(SystemChangeImpact.ofClearingBases(List.of(INSIDE)).isEmpty());
	}
}
