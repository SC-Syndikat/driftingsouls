package net.driftingsouls.ds2.server.campaign.dialog;

import net.driftingsouls.ds2.server.DBSingleTransactionTest;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.ally.Ally;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TrustServiceTest extends DBSingleTransactionTest
{
	private TrustService trust;
	private User npc;
	private User alice;
	private User bob;
	private User carol;

	@Before
	public void setUp()
	{
		npc = new User("npc", "***", 0, "", new Cargo(), "npc@localhost");
		alice = new User("alice", "***", 0, "", new Cargo(), "alice@localhost");
		bob = new User("bob", "***", 0, "", new Cargo(), "bob@localhost");
		carol = new User("carol", "***", 0, "", new Cargo(), "carol@localhost");
		Ally ally = persist(new Ally("Bund", alice));
		alice.setAlly(ally);
		bob.setAlly(ally);
		getEM().flush();

		trust = new TrustService(getEM());
		trust.setAllianceFactor(npc.getId(), 0.5);
	}

	@Test
	public void givenActionsOfAllianceMembers_trust_shouldCountThemProportionally()
	{
		trust.record(npc.getId(), alice.getId(), 10, "Hilfe", TrustService.Kind.ACTION, null, null, null);
		trust.record(npc.getId(), bob.getId(), 4, "Hilfe", TrustService.Kind.ACTION, null, null, null);
		trust.record(npc.getId(), carol.getId(), 100, "Hilfe", TrustService.Kind.ACTION, null, null, null);

		TrustService.Trust aliceTrust = trust.trust(alice.getId(), npc.getId());

		assertEquals(10, aliceTrust.own);
		assertEquals(4, aliceTrust.alliance);
		assertEquals(12, aliceTrust.getTotal(), 0.001);
		assertEquals("Carol ist in keiner Allianz", 100, trust.trust(carol.getId(), npc.getId()).getTotal(), 0.001);
	}

	@Test
	public void givenACorrection_trust_shouldOnlyCountForThePlayer()
	{
		trust.record(npc.getId(), alice.getId(), 10, "Hilfe", TrustService.Kind.ACTION, null, null, null);

		int delta = trust.correctTo(npc.getId(), alice.getId(), 0, "Zurückgesetzt", null, npc.getId());

		assertEquals(-10, delta);
		assertEquals(0, trust.trust(alice.getId(), npc.getId()).getTotal(), 0.001);
		assertEquals("Die Korrektur wirkt nicht über den Allianzanteil", 5, trust.trust(bob.getId(), npc.getId()).getTotal(), 0.001);
	}
}
