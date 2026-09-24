package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.DBSingleTransactionTest;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.entities.User;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

public class CampaignServiceTest extends DBSingleTransactionTest
{
	private CampaignService service;
	private User gamemaster;
	private User alice;
	private User bob;

	@Before
	public void setUp()
	{
		gamemaster = new User("gm", "***", 0, "", new Cargo(), "gm@localhost");
		alice = new User("alice", "***", 0, "", new Cargo(), "alice@localhost");
		bob = new User("bob", "***", 0, "", new Cargo(), "bob@localhost");
		// The campaign tables reference users by foreign key and are written by jOOQ
		getEM().flush();

		service = new CampaignService(getEM());
	}

	@Test
	public void gegebenKeineKampagne_prepare_sollteEineKampagneInVorbereitungAnlegen()
	{
		Campaign campaign = service.prepare("  Sturm über Rigel  ", gamemaster);

		assertTrue(campaign.isPreparing());
		assertEquals("Sturm über Rigel", campaign.getName());
		assertEquals(Integer.valueOf(gamemaster.getId()), campaign.getGamemasterId());
		assertEquals(campaign.getId(), service.findOpenCampaign().orElseThrow().getId());
	}

	@Test(expected = CampaignException.class)
	public void gegebenEineOffeneKampagne_prepare_sollteAbgelehntWerden()
	{
		service.prepare("Erste", gamemaster);
		service.prepare("Zweite", gamemaster);
	}

	@Test
	public void gegebenEineKampagneInVorbereitung_updateParticipants_sollteNurDieListeUndNichtDasFlagAendern()
	{
		Campaign campaign = service.prepare("Test", gamemaster);

		CampaignService.ParticipantChange change = service.updateParticipants(campaign, Set.of(alice.getId(), bob.getId()), gamemaster);

		assertEquals(Set.of(alice.getId(), bob.getId()), change.added);
		assertEquals(Set.of(alice.getId(), bob.getId()), service.getParticipantIds(campaign));
		assertFalse(alice.isCampaignParticipant());
		assertFalse(bob.isCampaignParticipant());
	}

	@Test
	public void gegebenBestehendeTeilnehmer_updateParticipants_sollteNurDieDifferenzSchreiben()
	{
		Campaign campaign = service.prepare("Test", gamemaster);
		service.updateParticipants(campaign, Set.of(alice.getId()), gamemaster);

		CampaignService.ParticipantChange change = service.updateParticipants(campaign, Set.of(bob.getId()), gamemaster);

		assertEquals(Set.of(bob.getId()), change.added);
		assertEquals(Set.of(alice.getId()), change.removed);
		assertEquals(Set.of(bob.getId()), service.getParticipantIds(campaign));
	}

	@Test
	public void gegebenKeineTeilnehmer_findStartBlockers_sollteDenStartBlockieren()
	{
		Campaign campaign = service.prepare("Test", gamemaster);

		assertFalse(service.findStartBlockers(campaign).isEmpty());
	}

	@Test
	public void gegebenEinTeilnehmerImUrlaub_findStartBlockers_sollteDenStartBlockieren()
	{
		Campaign campaign = service.prepare("Test", gamemaster);
		service.updateParticipants(campaign, Set.of(alice.getId()), gamemaster);
		alice.setVacationCount(10);

		assertEquals(1, service.findStartBlockers(campaign).size());
	}

	@Test
	public void gegebenEineVorbereiteteKampagne_start_sollteDieTeilnehmerFlagsSetzen()
	{
		Campaign campaign = service.prepare("Test", gamemaster);
		service.updateParticipants(campaign, Set.of(alice.getId()), gamemaster);

		service.start(campaign);

		assertTrue(service.findOpenCampaign().orElseThrow().isRunning());
		assertTrue(alice.isCampaignParticipant());
		assertFalse(bob.isCampaignParticipant());
	}

	@Test
	public void gegebenEineLaufendeKampagne_updateParticipants_sollteSofortWirken()
	{
		Campaign campaign = service.prepare("Test", gamemaster);
		service.updateParticipants(campaign, Set.of(alice.getId()), gamemaster);
		service.start(campaign);
		Campaign running = service.findOpenCampaign().orElseThrow();
		alice.setTickReady(true);

		service.updateParticipants(running, Set.of(bob.getId()), gamemaster);

		assertFalse(alice.isCampaignParticipant());
		assertFalse(alice.isTickReady());
		assertTrue(bob.isCampaignParticipant());
	}

	@Test
	public void gegebenEineLaufendeKampagne_end_sollteAlleFlagsZuruecksetzen()
	{
		Campaign campaign = service.prepare("Test", gamemaster);
		service.updateParticipants(campaign, Set.of(alice.getId()), gamemaster);
		service.start(campaign);
		alice.setTickReady(true);
		getEM().flush();

		service.end(service.findOpenCampaign().orElseThrow());

		assertTrue(service.findOpenCampaign().isEmpty());
		assertFalse(alice.isCampaignParticipant());
		assertFalse(alice.isTickReady());
	}
}
