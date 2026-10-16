package net.driftingsouls.ds2.server.campaign;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ShipGroupLimitsTest
{
	private static ShipGroupLimits.Load<String> load(String key, long count, long size)
	{
		return new ShipGroupLimits.Load<>(key, key, count, size);
	}

	private static List<Long> counts(ShipGroupLimits.Result<String> result)
	{
		return result.loads.stream().map(l -> l.count).collect(java.util.stream.Collectors.toList());
	}

	@Test
	public void gegebenGenugPlatz_trim_sollteNichtsAendern()
	{
		ShipGroupLimits.Result<String> result = ShipGroupLimits.trim(List.of(load("a", 10, 2), load("b", 5, 1)), 25, "Cargo");

		assertEquals(List.of(10L, 5L), counts(result));
		assertTrue(result.warnings.isEmpty());
		assertFalse(result.isChanged());
	}

	@Test
	public void gegebenEineUeberbelegung_trim_sollteDenLetztenEintragReduzieren()
	{
		ShipGroupLimits.Result<String> result = ShipGroupLimits.trim(List.of(load("a", 10, 2), load("b", 10, 3)), 26, "Cargo");

		assertEquals("20 + 2*3 = 26", List.of(10L, 2L), counts(result));
		assertEquals(List.of("Cargo: b von 10 auf 2 reduziert."), result.warnings);
		assertTrue(result.isChanged());
	}

	@Test
	public void gegebenEineGrosseUeberbelegung_trim_sollteVonHintenEntfernenUndReduzieren()
	{
		ShipGroupLimits.Result<String> result = ShipGroupLimits.trim(List.of(load("a", 10, 2), load("b", 4, 5), load("c", 3, 10)), 12, "Einheiten");

		assertEquals(List.of(6L), counts(result));
		assertEquals(List.of("Einheiten: c (3) entfernt.", "Einheiten: b (4) entfernt.", "Einheiten: a von 10 auf 6 reduziert."),
				result.warnings);
	}

	@Test
	public void gegebenEintraegeOhneGroesse_trim_sollteSieBehalten()
	{
		ShipGroupLimits.Result<String> result = ShipGroupLimits.trim(List.of(load("a", 5, 4), load("frei", 100, 0)), 8, "Cargo");

		assertEquals(List.of(2L, 100L), counts(result));
	}

	@Test
	public void gegebenKeinPlatz_trim_sollteAllesEntfernen()
	{
		ShipGroupLimits.Result<String> result = ShipGroupLimits.trim(List.of(load("a", 5, 4)), 0, "Einheiten");

		assertTrue(result.loads.isEmpty());
		assertEquals(1, result.warnings.size());
	}
}
