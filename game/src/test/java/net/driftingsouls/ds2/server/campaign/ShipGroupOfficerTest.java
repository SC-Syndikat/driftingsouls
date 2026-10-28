package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.entities.Offizier;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

public class ShipGroupOfficerTest
{
	@Test
	public void givenAbilities_getRank_shouldReturnTheAverageDividedBy125()
	{
		assertEquals(0, new ShipGroupTemplate.Officer(100, 100, 100, 100, 100, "").getRank());
		assertEquals(2, new ShipGroupTemplate.Officer(250, 250, 250, 250, 300, "").getRank());
	}

	@Test
	public void givenVeryHighAbilities_getRank_shouldEndAtTheHighestRank()
	{
		assertEquals(6, new ShipGroupTemplate.Officer(5000, 5000, 5000, 5000, 5000, "").getRank());
	}

	@Test
	public void givenAnAbility_getAbility_shouldReturnTheMatchingValue()
	{
		ShipGroupTemplate.Officer officer = new ShipGroupTemplate.Officer(1, 2, 3, 4, 5, "");

		assertEquals(1, officer.getAbility(Offizier.Ability.NAV));
		assertEquals(2, officer.getAbility(Offizier.Ability.ING));
		assertEquals(3, officer.getAbility(Offizier.Ability.WAF));
		assertEquals(4, officer.getAbility(Offizier.Ability.SEC));
		assertEquals(5, officer.getAbility(Offizier.Ability.COM));
	}

	@Test
	public void givenAFixedSpecialAbility_resolveSpecial_shouldReturnIt()
	{
		assertEquals(Offizier.Special.NONE, new ShipGroupTemplate.Officer(0, 0, 0, 0, 0, "").resolveSpecial(new Random(1)));
		assertEquals(Offizier.Special.WAFFENNARR, new ShipGroupTemplate.Officer(0, 0, 0, 0, 0, "WAFFENNARR").resolveSpecial(new Random(1)));
	}

	@Test
	public void givenARandomSpecialAbility_resolveSpecial_shouldNeverReturnNone()
	{
		ShipGroupTemplate.Officer officer = new ShipGroupTemplate.Officer(0, 0, 0, 0, 0, ShipGroupTemplate.Officer.RANDOM_SPECIAL);
		Random random = new Random(42);
		for (int i = 0; i < 100; i++)
		{
			assertNotEquals(Offizier.Special.NONE, officer.resolveSpecial(random));
		}
	}

	@Test
	public void givenInvalidValues_shouldBeRejected()
	{
		assertThrows(CampaignException.class, () -> new ShipGroupTemplate.Officer(-1, 0, 0, 0, 0, ""));
		assertThrows(CampaignException.class, () -> new ShipGroupTemplate.Officer(0, 0, 0, 0, 0, "GIBTSNICHT"));
	}
}
