package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.config.StarSystem;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

public class GameMasterRestrictionTest
{
	private final GameMasterRestriction restriction = new GameMasterRestriction(true, 3, Set.of(7, 8), Set.of(-5, 42));

	@Test
	public void givenAGameMaster_shouldOnlyAllowCampaignSystems()
	{
		assertTrue(restriction.isSystemAllowed(7));
		assertFalse(restriction.isSystemAllowed(1));
	}

	@Test
	public void givenAGameMaster_shouldOnlyAllowParticipatingNpcsAndNobodyAsOwner()
	{
		assertTrue(restriction.isOwnerAllowed(-5));
		assertTrue(restriction.isOwnerAllowed(0));
		assertFalse(restriction.isOwnerAllowed(-6));
		assertFalse("Teilnehmende Spieler sind keine NPCs", restriction.isOwnerAllowed(42));
	}

	@Test
	public void givenAGameMaster_checkMasterLogin_shouldOnlyAllowParticipatingNpcs()
	{
		restriction.checkMasterLogin(-5);
		assertEquals(Set.of(-5), restriction.getMasterLoginIds());
		assertThrows(CampaignException.class, () -> restriction.checkMasterLogin(42));
		assertThrows(CampaignException.class, () -> restriction.checkMasterLogin(-6));
	}

	@Test
	public void givenNoRestriction_shouldAllowEverything()
	{
		GameMasterRestriction none = new GameMasterRestriction(false, null, Set.of(), Set.of());

		assertFalse(none.applies());
		assertTrue(none.isAllowed(1, 42));
		none.checkMasterLogin(42);
		assertTrue(none.mayCreateSystems());
		assertTrue(none.isJumpNodeAllowed(1, 2));
		none.checkSystem(1);
		none.checkSystemAccess(StarSystem.Access.HOMESYSTEM);
	}

	@Test
	public void givenAGameMaster_checkSystem_shouldOnlyAllowCampaignSystems()
	{
		restriction.checkSystem(7);
		assertThrows(CampaignException.class, () -> restriction.checkSystem(1));
	}

	@Test
	public void givenAGameMaster_isJumpNodeAllowed_shouldRequireBothEndsInTheCampaign()
	{
		assertTrue(restriction.isJumpNodeAllowed(7, 8));
		assertFalse(restriction.isJumpNodeAllowed(7, 1));
		assertFalse(restriction.isJumpNodeAllowed(1, 7));
		restriction.checkJumpNode(8, 7);
		assertThrows(CampaignException.class, () -> restriction.checkJumpNode(7, 1));
	}

	@Test
	public void givenAGameMaster_checkSystemAccess_shouldForbidHomeAndAdminSystems()
	{
		restriction.checkSystemAccess(StarSystem.Access.NORMAL);
		restriction.checkSystemAccess(StarSystem.Access.NPC);
		restriction.checkSystemAccess(StarSystem.Access.NICHT_SICHTBAR);
		assertThrows(CampaignException.class, () -> restriction.checkSystemAccess(StarSystem.Access.HOMESYSTEM));
		assertThrows(CampaignException.class, () -> restriction.checkSystemAccess(StarSystem.Access.ADMIN));
	}

	@Test
	public void givenAGameMaster_mayCreateSystems_shouldRequireAnOpenCampaign()
	{
		assertTrue(restriction.mayCreateSystems());
		assertEquals(Integer.valueOf(3), restriction.getCampaignId().orElseThrow());

		GameMasterRestriction withoutCampaign = new GameMasterRestriction(true, null, Set.of(), Set.of());
		assertFalse(withoutCampaign.mayCreateSystems());
		assertTrue(withoutCampaign.getCampaignId().isEmpty());
	}
}
