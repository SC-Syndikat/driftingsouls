package net.driftingsouls.ds2.server.campaign;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ShipGroupUnitsTest
{
	private static final Map<Integer, Integer> SIZES = Map.of(1, 2, 2, 5);

	@Test
	public void gegebenEinEinheitenString_parse_sollteTypUndAnzahlLiefern()
	{
		Map<Integer, Long> units = ShipGroupUnits.parse(" 1:10 | 2:3 ");

		assertEquals(Map.of(1, 10L, 2, 3L), units);
		assertTrue(ShipGroupUnits.parse("").isEmpty());
		assertTrue(ShipGroupUnits.parse(null).isEmpty());
	}

	@Test
	public void gegebenUngueltigeAngaben_parse_sollteAbgelehntWerden()
	{
		assertThrows(CampaignException.class, () -> ShipGroupUnits.parse("1"));
		assertThrows(CampaignException.class, () -> ShipGroupUnits.parse("a:3"));
		assertThrows(CampaignException.class, () -> ShipGroupUnits.parse("1:-2"));
	}

	@Test
	public void gegebenEinheiten_format_sollteDasGespeicherteFormatLiefernUndLeereAuslassen()
	{
		Map<Integer, Long> units = new LinkedHashMap<>();
		units.put(1, 10L);
		units.put(3, 0L);
		units.put(2, 3L);

		assertEquals("1:10|2:3", ShipGroupUnits.format(units));
	}

	@Test
	public void gegebenEinheiten_mass_sollteAnzahlMalGroesseSummieren()
	{
		assertEquals(10 * 2 + 3 * 5, ShipGroupUnits.mass(Map.of(1, 10L, 2, 3L), SIZES));
	}

	@Test
	public void gegebenGenugPlatz_check_sollteDieEinheitenAkzeptieren()
	{
		ShipGroupUnits.check(Map.of(1, 10L, 2, 3L), SIZES, 35, 5);
		ShipGroupUnits.check(Map.of(2, 1L), SIZES, 5, -1);
		ShipGroupUnits.check(Map.of(), SIZES, 0, 0);
	}

	@Test
	public void gegebenZuWenigPlatz_check_sollteAbgelehntWerden()
	{
		assertThrows(CampaignException.class, () -> ShipGroupUnits.check(Map.of(1, 10L, 2, 3L), SIZES, 34, -1));
	}

	@Test
	public void gegebenEineZuGrosseEinheit_check_sollteAbgelehntWerden()
	{
		assertThrows(CampaignException.class, () -> ShipGroupUnits.check(Map.of(2, 1L), SIZES, 100, 4));
	}

	@Test
	public void gegebenEinUnbekannterEinheitentyp_check_sollteAbgelehntWerden()
	{
		assertThrows(CampaignException.class, () -> ShipGroupUnits.check(Map.of(9, 1L), SIZES, 100, -1));
	}
}
