package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.DBSingleTransactionTest;
import net.driftingsouls.ds2.server.campaign.dialog.DialogRepository;
import net.driftingsouls.ds2.server.campaign.dialog.ObjectType;
import net.driftingsouls.ds2.server.campaign.interaction.Interaction;
import net.driftingsouls.ds2.server.campaign.interaction.InteractionRepository;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.comm.PM;
import net.driftingsouls.ds2.server.config.StarSystem;
import net.driftingsouls.ds2.server.entities.User;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
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
	public void givenNoCampaign_prepare_shouldCreateACampaignInPreparation()
	{
		Campaign campaign = service.prepare("  Sturm über Rigel  ", gamemaster);

		assertTrue(campaign.isPreparing());
		assertEquals("Sturm über Rigel", campaign.getName());
		assertEquals(Integer.valueOf(gamemaster.getId()), campaign.getGamemasterId());
		assertEquals(campaign.getId(), service.findOpenCampaign().orElseThrow().getId());
	}

	@Test(expected = CampaignException.class)
	public void givenAnOpenCampaign_prepare_shouldBeRejected()
	{
		service.prepare("Erste", gamemaster);
		service.prepare("Zweite", gamemaster);
	}

	@Test
	public void givenACampaignInPreparation_updateParticipants_shouldOnlyChangeTheListAndNotTheFlag()
	{
		Campaign campaign = service.prepare("Test", gamemaster);

		CampaignService.ParticipantChange change = service.updateParticipants(campaign, Set.of(alice.getId(), bob.getId()), gamemaster);

		assertEquals(Set.of(alice.getId(), bob.getId()), change.added);
		assertEquals(Set.of(alice.getId(), bob.getId()), service.getParticipantIds(campaign));
		assertFalse(alice.isCampaignParticipant());
		assertFalse(bob.isCampaignParticipant());
	}

	@Test
	public void givenExistingParticipants_updateParticipants_shouldOnlyWriteTheDifference()
	{
		Campaign campaign = service.prepare("Test", gamemaster);
		service.updateParticipants(campaign, Set.of(alice.getId()), gamemaster);

		CampaignService.ParticipantChange change = service.updateParticipants(campaign, Set.of(bob.getId()), gamemaster);

		assertEquals(Set.of(bob.getId()), change.added);
		assertEquals(Set.of(alice.getId()), change.removed);
		assertEquals(Set.of(bob.getId()), service.getParticipantIds(campaign));
	}

	@Test
	public void givenNoParticipants_findStartBlockers_shouldBlockTheStart()
	{
		Campaign campaign = service.prepare("Test", gamemaster);

		assertFalse(service.findStartBlockers(campaign).isEmpty());
	}

	@Test
	public void givenAParticipantOnVacation_findStartBlockers_shouldBlockTheStart()
	{
		Campaign campaign = service.prepare("Test", gamemaster);
		service.updateParticipants(campaign, Set.of(alice.getId()), gamemaster);
		alice.setVacationCount(10);

		assertEquals(1, service.findStartBlockers(campaign).size());
	}

	@Test
	public void givenAPreparedCampaign_start_shouldSetTheParticipantFlags()
	{
		Campaign campaign = service.prepare("Test", gamemaster);
		service.updateParticipants(campaign, Set.of(alice.getId()), gamemaster);

		service.start(campaign);

		assertTrue(service.findOpenCampaign().orElseThrow().isRunning());
		assertTrue(alice.isCampaignParticipant());
		assertFalse(bob.isCampaignParticipant());
	}

	@Test
	public void givenARunningCampaign_updateParticipants_shouldTakeEffectImmediately()
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
	public void givenARunningCampaign_end_shouldResetAllFlags()
	{
		Campaign campaign = service.prepare("Test", gamemaster);
		service.updateParticipants(campaign, Set.of(alice.getId()), gamemaster);
		service.start(campaign);
		alice.setTickReady(true);
		getEM().flush();

		service.end(service.findOpenCampaign().orElseThrow(), gamemaster);

		assertTrue(service.findOpenCampaign().isEmpty());
		assertFalse(alice.isCampaignParticipant());
		assertFalse(alice.isTickReady());
	}

	@Test
	public void start_shouldTellEveryParticipant()
	{
		Campaign campaign = service.prepare("Sturm über Rigel", gamemaster);
		service.updateParticipants(campaign, Set.of(alice.getId()), gamemaster);

		service.start(campaign);
		getEM().flush();

		List<PM> pms = campaignPmsTo(alice);
		assertEquals(1, pms.size());
		assertTrue(pms.get(0).getTitle().contains("Sturm über Rigel"));
		assertTrue(campaignPmsTo(bob).isEmpty());
	}

	@Test
	public void recordCampaignTick_shouldCountTheTickAndTellEveryParticipant()
	{
		Campaign campaign = service.prepare("Test", gamemaster);
		service.updateParticipants(campaign, Set.of(alice.getId()), gamemaster);
		service.start(campaign);

		service.recordCampaignTick(service.findOpenCampaign().orElseThrow());
		getEM().flush();

		assertEquals(1, service.findOpenCampaign().orElseThrow().getTickCount());
		List<PM> pms = campaignPmsTo(alice);
		assertEquals("Start und Kampagnentick", 2, pms.size());
		assertTrue(pms.get(1).getInhalt().contains("Kampagnentick 1"));
	}

	@Test
	public void givenAnEndedCampaign_copy_shouldPrepareANewOneWithItsSystemsAndInteractions()
	{
		StarSystem system = persist(new StarSystem());
		getEM().flush();
		Campaign original = service.prepare("Erste", gamemaster);
		service.updateParticipants(original, Set.of(alice.getId()), gamemaster);
		new CampaignMapService(getEM()).addSystem(original, system.getID());
		int node = new DialogRepository().createNode("Gruß", "Admiral", "", "Hallo.");
		int interactionId = new InteractionRepository().save(new Interaction(0, original.getId(), "Grüßen", ObjectType.STAR_SYSTEM,
				system.getID(), node, alice.getId(), false, 1, null, Interaction.Audience.RESTRICTED, Set.of(alice.getId()), Set.of()));
		service.start(original);
		service.end(service.findCampaign(original.getId()).orElseThrow(), gamemaster);

		Campaign copy = service.copy(service.findCampaign(original.getId()).orElseThrow(), "Zweite", gamemaster, true);

		assertTrue(copy.isPreparing());
		assertEquals("Zweite", copy.getName());
		assertEquals(Set.of(system.getID()), new CampaignMapService(getEM()).getSystemIds(copy));
		assertEquals(Set.of(alice.getId()), service.getParticipantIds(copy));
		List<Interaction> copied = new InteractionRepository().findForCampaign(copy.getId());
		assertEquals(1, copied.size());
		Interaction interaction = copied.get(0);
		assertNotEquals(interactionId, interaction.id);
		assertEquals("Grüßen", interaction.label);
		assertFalse(interaction.active);
		assertEquals(Set.of(alice.getId()), interaction.audienceUserIds);
		assertEquals("Die alte Kampagne behält ihre Interaktion", 1, new InteractionRepository().findForCampaign(original.getId()).size());
	}

	@Test
	public void givenACopiedCampaign_aDialogsReferenceToAnInteraction_shouldResolveToTheRunningCampaignsCopy()
	{
		Campaign first = service.prepare("Erste", gamemaster);
		int node = new DialogRepository().createNode("Gruß", "Admiral", "", "Hallo.");
		StarSystem system = persist(new StarSystem());
		getEM().flush();
		InteractionRepository interactions = new InteractionRepository();
		int original = interactions.save(new Interaction(0, first.getId(), "Grüßen", ObjectType.STAR_SYSTEM, system.getID(), node,
				gamemaster.getId(), true, null, null, Interaction.Audience.ALL, Set.of(), Set.of()));
		service.updateParticipants(first, Set.of(alice.getId()), gamemaster);
		service.start(first);
		service.end(service.findCampaign(first.getId()).orElseThrow(), gamemaster);
		Campaign second = service.copy(service.findCampaign(first.getId()).orElseThrow(), "Zweite", gamemaster, true);
		service.start(second);
		service.end(service.findCampaign(second.getId()).orElseThrow(), gamemaster);
		Campaign third = service.copy(service.findCampaign(second.getId()).orElseThrow(), "Dritte", gamemaster, true);

		int inThird = interactions.findForCampaign(third.getId()).get(0).id;
		assertEquals("Ein Dialog verweist weiter auf die erste Interaktion", inThird, interactions.resolveForCampaign(original, third.getId()));
		assertEquals(inThird, interactions.resolveForCampaign(interactions.findForCampaign(second.getId()).get(0).id, third.getId()));
		assertEquals("In ihrer eigenen Kampagne bleibt sie, wie sie ist", original, interactions.resolveForCampaign(original, first.getId()));
	}

	@Test
	public void copy_withoutParticipants_shouldStartWithAnEmptyList()
	{
		Campaign original = service.prepare("Erste", gamemaster);
		service.updateParticipants(original, Set.of(alice.getId()), gamemaster);
		service.start(original);
		service.end(service.findCampaign(original.getId()).orElseThrow(), gamemaster);

		Campaign copy = service.copy(service.findCampaign(original.getId()).orElseThrow(), "Zweite", gamemaster, false);

		assertTrue(service.getParticipantIds(copy).isEmpty());
	}

	@Test(expected = CampaignException.class)
	public void givenAnOpenCampaign_copy_shouldBeRejected()
	{
		Campaign original = service.prepare("Erste", gamemaster);

		service.copy(original, "Zweite", gamemaster, false);
	}

	private List<PM> campaignPmsTo(User user)
	{
		return getEM().createQuery("from PM where empfaenger = :user and title like 'Kampagne%' order by id", PM.class)
				.setParameter("user", user)
				.getResultList();
	}
}
