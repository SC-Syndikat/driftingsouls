package net.driftingsouls.ds2.server.config;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModuleSlotTest
{
	@Test
	public void aModuleWithoutSlotsFitsNoSlot()
	{
		ModuleSlot slot = new ModuleSlot("weapon", "Waffe", null);

		assertFalse(slot.isMemberIn(Collections.<String>emptyList()));
	}

	@Test
	public void aSlotIsMemberOfItsOwnTypeAndOfItsParents()
	{
		ModuleSlot parent = new ModuleSlot("weapon", "Waffe", null);
		ModuleSlot slot = new ModuleSlot("heavy_weapon", "Schwere Waffe", parent);

		assertTrue(slot.isMemberIn(List.of("heavy_weapon")));
		assertTrue(slot.isMemberIn(List.of("cargo", "weapon")));
		assertFalse(parent.isMemberIn(List.of("heavy_weapon")));
	}
}
