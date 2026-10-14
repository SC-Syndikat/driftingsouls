package net.driftingsouls.ds2.server.discord;

import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.UserFlag;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DiscordKanalTest
{
	private static class TestUser extends User
	{
		TestUser(int id, int accesslevel, UserFlag flag)
		{
			setId(id);
			setAccesslevel(accesslevel);
			setFlag(flag, true);
		}
	}

	private final User spieler = new TestUser(10, 0, UserFlag.HIDE);
	private final User npc = new TestUser(-10, 0, UserFlag.HIDE);
	private final User gamemaster = new TestUser(11, 0, UserFlag.GAMEMASTER);
	private final User admin = new TestUser(12, 30, UserFlag.HIDE);

	@Test
	public void gesperrterKanal_sollteFuerNiemandenLesbarSein()
	{
		DiscordKanal kanal = kanal(DiscordZugriff.GESPERRT, DiscordZugriff.GESPERRT);

		assertFalse(kanal.darfLesen(spieler));
		assertFalse(kanal.darfLesen(npc));
		assertFalse(kanal.darfLesen(gamemaster));
		assertFalse(kanal.darfLesen(admin));
	}

	@Test
	public void npcKanal_sollteNurFuerNpcsUndAdminsLesbarSein()
	{
		DiscordKanal kanal = kanal(DiscordZugriff.NPC, DiscordZugriff.NPC);

		assertFalse(kanal.darfLesen(spieler));
		assertTrue(kanal.darfLesen(npc));
		assertFalse(kanal.darfLesen(gamemaster));
		assertTrue(kanal.darfLesen(admin));
	}

	@Test
	public void gamemasterKanal_sollteNurFuerGamemasterUndAdminsLesbarSein()
	{
		DiscordKanal kanal = kanal(DiscordZugriff.GAMEMASTER, DiscordZugriff.GAMEMASTER);

		assertFalse(kanal.darfLesen(spieler));
		assertFalse(kanal.darfLesen(npc));
		assertTrue(kanal.darfLesen(gamemaster));
		assertTrue(kanal.darfLesen(admin));
	}

	@Test
	public void adminKanal_sollteNurFuerAdminsLesbarSein()
	{
		DiscordKanal kanal = kanal(DiscordZugriff.ADMIN, DiscordZugriff.ADMIN);

		assertFalse(kanal.darfLesen(spieler));
		assertFalse(kanal.darfLesen(npc));
		assertFalse(kanal.darfLesen(gamemaster));
		assertTrue(kanal.darfLesen(admin));
	}

	@Test
	public void ankuendigungskanal_sollteFuerAlleLesbarAberNurFuerAdminsSchreibbarSein()
	{
		DiscordKanal kanal = kanal(DiscordZugriff.ALLE, DiscordZugriff.ADMIN);

		assertTrue(kanal.darfLesen(spieler));
		assertFalse(kanal.darfSchreiben(spieler));
		assertTrue(kanal.darfSchreiben(admin));
	}

	@Test
	public void schreibrechtOhneLeserecht_sollteKeinSchreibenErlauben()
	{
		DiscordKanal kanal = kanal(DiscordZugriff.ADMIN, DiscordZugriff.ALLE);

		assertFalse(kanal.darfSchreiben(spieler));
		assertTrue(kanal.darfSchreiben(admin));
	}

	private static DiscordKanal kanal(DiscordZugriff lesen, DiscordZugriff schreiben)
	{
		DiscordKanal kanal = new DiscordKanal("1", "2");
		kanal.setLesen(lesen);
		kanal.setSchreiben(schreiben);
		return kanal;
	}
}
