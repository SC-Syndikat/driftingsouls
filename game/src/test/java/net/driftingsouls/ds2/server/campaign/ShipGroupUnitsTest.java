package net.driftingsouls.ds2.server.campaign;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ShipGroupUnitsTest
{
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
}
