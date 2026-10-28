package net.driftingsouls.ds2.server.campaign;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ShipGroupModulesTest
{
	@Test
	public void givenAModuleString_parse_shouldReturnSlotAndItem()
	{
		assertEquals(Map.of(1, 442, 3, 17), ShipGroupModules.parse(" 1:442 | 3:17 "));
		assertTrue(ShipGroupModules.parse("").isEmpty());
		assertTrue(ShipGroupModules.parse(null).isEmpty());
	}

	@Test
	public void givenInvalidInput_parse_shouldBeRejected()
	{
		assertThrows(CampaignException.class, () -> ShipGroupModules.parse("1"));
		assertThrows(CampaignException.class, () -> ShipGroupModules.parse("x:3"));
		assertThrows("Slot 0 ist für virtuelle Module", CampaignException.class, () -> ShipGroupModules.parse("0:3"));
		assertThrows("ein Modul je Slot", CampaignException.class, () -> ShipGroupModules.parse("1:3|1:4"));
	}

	@Test
	public void givenModules_format_shouldStoreThemSortedBySlot()
	{
		Map<Integer, Integer> modules = new LinkedHashMap<>();
		modules.put(3, 17);
		modules.put(1, 442);

		assertEquals("1:442|3:17", ShipGroupModules.format(modules));
	}

	@Test
	public void givenTheSlotsOfAShipType_slots_shouldReturnNumberAndType()
	{
		Map<Integer, String> slots = ShipGroupModules.slots("1:waffe;2:antrieb:12,13;5:waffe");

		assertEquals(Map.of(1, "waffe", 2, "antrieb", 5, "waffe"), slots);
		assertTrue(ShipGroupModules.slots("").isEmpty());
		assertTrue(ShipGroupModules.slots(null).isEmpty());
	}
}
