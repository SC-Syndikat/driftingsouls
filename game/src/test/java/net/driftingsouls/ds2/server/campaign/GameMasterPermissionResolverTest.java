package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.WellKnownAdminPermission;
import net.driftingsouls.ds2.server.framework.PermissionResolver;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GameMasterPermissionResolverTest
{
	private static final PermissionResolver NOTHING = permission -> false;

	@Test
	public void gegebenEinGameMaster_hasPermission_sollteDieKampagnenrechteGewaehren()
	{
		var resolver = new GameMasterPermissionResolver(NOTHING, true);

		assertTrue(resolver.hasPermission(WellKnownAdminPermission.SICHTBAR));
		assertTrue(resolver.hasPermission(WellKnownAdminPermission.CAMPAIGN_PARTICIPANTS));
		assertTrue(resolver.hasPermission(WellKnownAdminPermission.ADD_SHIPS));
		assertTrue(resolver.hasPermission(WellKnownAdminPermission.PLAYER_LOGIN_SUPER));
	}

	@Test
	public void gegebenEinGameMaster_hasPermission_sollteDasGestaltenVonSystemenErlauben()
	{
		var resolver = new GameMasterPermissionResolver(NOTHING, true);

		assertTrue(resolver.hasPermission(WellKnownAdminPermission.EDIT_SYSTEM));
		assertTrue(resolver.hasPermission(WellKnownAdminPermission.CREATE_OBJECTS_FROM_IMAGE));
		assertTrue(resolver.hasPermission(WellKnownAdminPermission.EDIT_JUMPNODE));
		assertTrue(resolver.hasPermission(WellKnownAdminPermission.EDIT_CONFIG_FELSBROCKEN_SYSTEM));
		assertTrue(resolver.hasPermission(WellKnownAdminPermission.EDIT_CONFIG_FELSBROCKEN));
	}

	@Test
	public void gegebenEinGameMaster_hasPermission_sollteKeineAnderenAdminrechteGewaehren()
	{
		var resolver = new GameMasterPermissionResolver(NOTHING, true);

		assertFalse(resolver.hasPermission(WellKnownAdminPermission.CONSOLE));
		assertFalse(resolver.hasPermission(WellKnownAdminPermission.EDIT_USER));
		assertFalse(resolver.hasPermission(WellKnownAdminPermission.EDIT_SHIPTYPES));
		assertFalse(resolver.hasPermission(WellKnownAdminPermission.PLAYER_DELETE));
	}

	@Test
	public void gegebenKeinGameMaster_hasPermission_sollteAnDenInnerenResolverDelegieren()
	{
		assertFalse(new GameMasterPermissionResolver(NOTHING, false)
				.hasPermission(WellKnownAdminPermission.CAMPAIGN_PARTICIPANTS));
		assertTrue(new GameMasterPermissionResolver(permission -> true, false)
				.hasPermission(WellKnownAdminPermission.CONSOLE));
	}
}
