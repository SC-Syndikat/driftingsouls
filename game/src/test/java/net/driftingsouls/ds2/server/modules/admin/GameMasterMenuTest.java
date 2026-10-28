package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.campaign.GameMasterPermissionResolver;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GameMasterMenuTest
{
	@Test
	public void everyEntry_shouldOpenAPluginTheGameMasterMayUse() throws ClassNotFoundException
	{
		var resolver = new GameMasterPermissionResolver(permission -> false, true);

		for (GameMasterMenu.Entry entry : GameMasterMenu.ENTRIES)
		{
			AdminMenuEntry menuEntry = Class.forName(entry.plugin).getAnnotation(AdminMenuEntry.class);
			assertNotNull(entry.label, menuEntry);
			assertTrue(entry.label, resolver.hasPermission(menuEntry.permission()));
		}
	}
}
