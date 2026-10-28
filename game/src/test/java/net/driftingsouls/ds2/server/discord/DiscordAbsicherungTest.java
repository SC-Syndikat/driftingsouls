package net.driftingsouls.ds2.server.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.After;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests der Schutzmassnahmen der Discord-Anbindung: Rechte, Sichtbarkeit, Sende-Limits, Links und Konfiguration.
 */
public class DiscordAbsicherungTest
{
	private static final String GUILD = "100";
	private static final String OWNER = "1";
	private static final String SPIELER = "500";
	private static final String ROLLE = "300";
	private static final String MOD_ROLLE = "400";
	private static final long SCHREIBEN = DiscordService.RECHT_KANAL_ANSEHEN | DiscordService.RECHT_NACHRICHTEN_SENDEN;

	@After
	public void tearDown()
	{
		DiscordKonfiguration.umgebung = System::getenv;
	}

	// ---- Rechte eines Mitglieds im Kanal ----

	@Test
	public void everyoneDarfSchreiben_mitgliedsRechte_sollteSchreibenErlauben()
	{
		long rechte = DiscordService.mitgliedsRechte(OWNER, rollen(SCHREIBEN), GUILD, SPIELER, Collections.emptySet(), kanal("[]"));

		assertEquals(SCHREIBEN, rechte & SCHREIBEN);
	}

	@Test
	public void kanalFuerEveryoneStumm_mitgliedsRechte_sollteSchreibenVerbieten()
	{
		long rechte = DiscordService.mitgliedsRechte(OWNER, rollen(SCHREIBEN), GUILD, SPIELER, Collections.emptySet(),
				kanal("[" + overwrite(GUILD, 0, 0, DiscordService.RECHT_NACHRICHTEN_SENDEN) + "]"));

		assertEquals(0, rechte & DiscordService.RECHT_NACHRICHTEN_SENDEN);
	}

	@Test
	public void rolleDarfTrotzEveryoneSperre_mitgliedsRechte_sollteSchreibenErlauben()
	{
		long rechte = DiscordService.mitgliedsRechte(OWNER, rollen(SCHREIBEN), GUILD, SPIELER, Set.of(ROLLE),
				kanal("[" + overwrite(GUILD, 0, 0, SCHREIBEN) + "," + overwrite(ROLLE, 0, SCHREIBEN, 0) + "]"));

		assertEquals(SCHREIBEN, rechte & SCHREIBEN);
	}

	@Test
	public void accountGesperrtTrotzRolle_mitgliedsRechte_sollteSchreibenVerbieten()
	{
		long rechte = DiscordService.mitgliedsRechte(OWNER, rollen(SCHREIBEN), GUILD, SPIELER, Set.of(ROLLE),
				kanal("[" + overwrite(ROLLE, 0, SCHREIBEN, 0) + "," + overwrite(SPIELER, 1, 0, DiscordService.RECHT_NACHRICHTEN_SENDEN) + "]"));

		assertEquals(0, rechte & DiscordService.RECHT_NACHRICHTEN_SENDEN);
	}

	@Test
	public void eigentuemerUndAdministrator_mitgliedsRechte_sollteAllesErlauben()
	{
		Map<String, Long> rollenRechte = rollen(0);
		rollenRechte.put(MOD_ROLLE, DiscordService.RECHT_ADMINISTRATOR);
		JsonObject gesperrt = kanal("[" + overwrite(GUILD, 0, 0, SCHREIBEN) + "]");

		assertEquals(DiscordService.ALLE_RECHTE, DiscordService.mitgliedsRechte(OWNER, rollenRechte, GUILD, OWNER, Collections.emptySet(), gesperrt));
		assertEquals(DiscordService.ALLE_RECHTE, DiscordService.mitgliedsRechte(OWNER, rollenRechte, GUILD, SPIELER, Set.of(MOD_ROLLE), gesperrt));
	}

	// ---- Sichtbarkeit und Freigabe ----

	@Test
	public void kanalOhneOverwrites_isOeffentlich_sollteOeffentlichSein()
	{
		assertTrue(DiscordService.isOeffentlich(GUILD, DiscordService.RECHT_KANAL_ANSEHEN, kanal("[]")));
	}

	@Test
	public void kanalFuerEveryoneVerborgen_isOeffentlich_sollteNichtOeffentlichSein()
	{
		assertFalse(DiscordService.isOeffentlich(GUILD, DiscordService.RECHT_KANAL_ANSEHEN,
				kanal("[" + overwrite(GUILD, 0, 0, DiscordService.RECHT_KANAL_ANSEHEN) + "]")));
	}

	@Test
	public void kanalWirdPrivat_rechteSignatur_sollteSichAendern()
	{
		String vorher = DiscordService.rechteSignatur(GUILD, DiscordService.RECHT_KANAL_ANSEHEN, kanal("[]"));
		String nachher = DiscordService.rechteSignatur(GUILD, DiscordService.RECHT_KANAL_ANSEHEN,
				kanal("[" + overwrite(GUILD, 0, 0, DiscordService.RECHT_KANAL_ANSEHEN) + "]"));

		assertNotEquals(vorher, nachher);
		assertEquals(64, vorher.length());
	}

	@Test
	public void rolleDarfMitlesen_rechteSignatur_sollteSichAendern()
	{
		String vorher = DiscordService.rechteSignatur(GUILD, 0, kanal("[]"));
		String nachher = DiscordService.rechteSignatur(GUILD, 0, kanal("[" + overwrite(ROLLE, 0, DiscordService.RECHT_VERLAUF_LESEN, 0) + "]"));

		assertNotEquals(vorher, nachher);
	}

	@Test
	public void nurSchreibrechtGeaendertOderReihenfolgeAnders_rechteSignatur_sollteGleichBleiben()
	{
		String a = overwrite(ROLLE, 0, DiscordService.RECHT_KANAL_ANSEHEN, 0);
		String b = overwrite(MOD_ROLLE, 0, 0, DiscordService.RECHT_KANAL_ANSEHEN);
		String vorher = DiscordService.rechteSignatur(GUILD, DiscordService.RECHT_KANAL_ANSEHEN, kanal("[" + a + "," + b + "]"));
		String nachher = DiscordService.rechteSignatur(GUILD, DiscordService.RECHT_KANAL_ANSEHEN | DiscordService.RECHT_NACHRICHTEN_SENDEN,
				kanal("[" + b + "," + a + "," + overwrite(SPIELER, 1, 0, DiscordService.RECHT_NACHRICHTEN_SENDEN) + "]"));

		assertEquals(vorher, nachher);
	}

	@Test
	public void freigabe_isFreigabeAktuell_sollteNurFuerDieFreigegebeneSichtbarkeitGelten()
	{
		DiscordKanal kanal = new DiscordKanal(GUILD, "7");
		kanal.setDiscordSignatur("a");
		assertFalse(kanal.isFreigabeAktuell());

		kanal.freigeben();
		assertTrue(kanal.isFreigabeAktuell());

		kanal.setDiscordSignatur("b");
		assertFalse(kanal.isFreigabeAktuell());
	}

	// ---- Senden ----

	@Test
	public void maskierterLink_entschaerfeLinks_sollteDasZielZeigen()
	{
		assertEquals("Hier (https://evil.example/x) klicken", DiscordService.entschaerfeLinks("[Hier](https://evil.example/x) klicken"));
		assertEquals("Hier (https://evil.example)", DiscordService.entschaerfeLinks("[Hier](<https://evil.example> \"Titel\")"));
		assertEquals("Normaler Text [ohne] Link (kein)", DiscordService.entschaerfeLinks("Normaler Text [ohne] Link (kein)"));
	}

	@Test
	public void verschachtelterLink_entschaerfeLinks_sollteKeinenMaskiertenLinkUebriglassen()
	{
		String text = DiscordService.entschaerfeLinks("[[a](https://x.example)](https://y.example)");

		assertFalse(text, text.matches("(?s).*\\[[^\\]]*\\]\\([^)]*\\).*"));
	}

	@Test
	public void sendeLimits_pruefeUndVermerke_sollteZuSchnellesSendenAblehnen()
	{
		DiscordService.SendeLimits limits = new DiscordService.SendeLimits();
		limits.pruefeUndVermerke(SPIELER, "7", 0, 10_000);

		erwarteAblehnung(() -> limits.pruefeUndVermerke(SPIELER, "8", 0, 10_000 + DiscordService.SEND_INTERVAL_MILLIS - 1));
		limits.pruefeUndVermerke(SPIELER, "8", 0, 10_000 + DiscordService.SEND_INTERVAL_MILLIS);
	}

	@Test
	public void sendeLimits_pruefeUndVermerke_sollteNachrichtenJeAccountUndMinuteBegrenzen()
	{
		DiscordService.SendeLimits limits = new DiscordService.SendeLimits();
		long zeit = 0;
		for (int i = 0; i < DiscordService.MAX_NACHRICHTEN_PRO_ACCOUNT; i++)
		{
			limits.pruefeUndVermerke(SPIELER, "7", 0, zeit);
			zeit += DiscordService.SEND_INTERVAL_MILLIS;
		}
		long letzte = zeit;
		erwarteAblehnung(() -> limits.pruefeUndVermerke(SPIELER, "7", 0, letzte));

		// Ein anderer Account darf weiter schreiben, nach einer Minute auch der erste wieder
		limits.pruefeUndVermerke("501", "7", 0, letzte);
		limits.pruefeUndVermerke(SPIELER, "7", 0, DiscordService.SEND_FENSTER_MILLIS);
	}

	@Test
	public void sendeLimits_pruefeUndVermerke_sollteNachrichtenJeKanalBegrenzen()
	{
		DiscordService.SendeLimits limits = new DiscordService.SendeLimits();
		for (int i = 0; i < DiscordService.MAX_NACHRICHTEN_PRO_KANAL; i++)
		{
			limits.pruefeUndVermerke("account" + i, "7", 0, 1000);
		}

		erwarteAblehnung(() -> limits.pruefeUndVermerke("nochEiner", "7", 0, 1000));
		limits.pruefeUndVermerke("nochEiner", "8", 0, 1000);
	}

	@Test
	public void slowmode_pruefeUndVermerke_sollteIhnJeAccountUndKanalDurchsetzen()
	{
		DiscordService.SendeLimits limits = new DiscordService.SendeLimits();
		limits.pruefeUndVermerke(SPIELER, "7", 30, 0);

		erwarteAblehnung(() -> limits.pruefeUndVermerke(SPIELER, "7", 30, 29_999));
		limits.pruefeUndVermerke(SPIELER, "8", 30, 29_999);
		limits.pruefeUndVermerke(SPIELER, "7", 30, 60_000);
	}

	@Test
	public void abgelehnteNachricht_pruefeUndVermerke_sollteNichtMitzaehlen()
	{
		DiscordService.SendeLimits limits = new DiscordService.SendeLimits();
		limits.pruefeUndVermerke(SPIELER, "7", 600, 0);
		erwarteAblehnung(() -> limits.pruefeUndVermerke(SPIELER, "7", 600, 5_000));

		// Der abgelehnte Versuch hat das Intervall nicht neu gestartet
		limits.pruefeUndVermerke(SPIELER, "8", 0, DiscordService.SEND_INTERVAL_MILLIS);
	}

	// ---- Medien und Avatare ----

	@Test
	public void discordCdn_isDiscordCdn_sollteNurDasCdnVonDiscordAkzeptieren()
	{
		assertTrue(DiscordService.isDiscordCdn("https://cdn.discordapp.com/attachments/1/2/bild.png?ex=1"));
		assertTrue(DiscordService.isDiscordCdn("https://media.discordapp.net/attachments/1/2/bild.png"));
		assertTrue(DiscordService.isDiscordCdn("https://images-ext-1.discordapp.net/external/abc/https/example.com/x.png"));

		assertFalse(DiscordService.isDiscordCdn(null));
		assertFalse(DiscordService.isDiscordCdn("http://cdn.discordapp.com/x.png"));
		assertFalse(DiscordService.isDiscordCdn("https://example.com/x.png"));
		assertFalse(DiscordService.isDiscordCdn("https://cdn.discordapp.com.evil.example/x.png"));
		assertFalse(DiscordService.isDiscordCdn("https://cdn.discordapp.com@evil.example/x.png"));
		assertFalse(DiscordService.isDiscordCdn("https://cdn.discordapp.com:8443/x.png"));
		assertFalse(DiscordService.isDiscordCdn("javascript:alert(1)"));
	}

	@Test
	public void ungueltigerAvatarHash_avatarUrl_sollteDenStandardavatarLiefern()
	{
		assertTrue(DiscordUser.avatarUrl("123", "\"><script>").startsWith("https://cdn.discordapp.com/embed/avatars/"));
		assertTrue(DiscordUser.avatarUrl("../x", "0123456789abcdef0123456789abcdef").startsWith("https://cdn.discordapp.com/embed/avatars/"));
	}

	// ---- Konfiguration ----

	@Test
	public void einladungslink_isGueltigeEinladung_sollteNurDiscordEinladungenAkzeptieren()
	{
		assertTrue(DiscordKonfiguration.isGueltigeEinladung("https://discord.gg/cpxxAGy"));
		assertTrue(DiscordKonfiguration.isGueltigeEinladung("https://discord.com/invite/cpxxAGy"));

		assertFalse(DiscordKonfiguration.isGueltigeEinladung(null));
		assertFalse(DiscordKonfiguration.isGueltigeEinladung("javascript:alert(document.cookie)"));
		assertFalse(DiscordKonfiguration.isGueltigeEinladung("https://evil.example/discord.gg/x"));
		assertFalse(DiscordKonfiguration.isGueltigeEinladung("https://discord.gg/x\" onclick=\"alert(1)"));
		assertFalse(DiscordKonfiguration.isGueltigeEinladung("http://discord.gg/cpxxAGy"));
	}

	@Test
	public void ungueltigerEinladungslinkInDerDatenbank_getSichereInviteUrl_sollteNullLiefern()
	{
		DiscordKonfiguration konfiguration = new DiscordKonfiguration();
		konfiguration.setInviteUrl("javascript:alert(1)");
		assertNull(konfiguration.getSichereInviteUrl());

		konfiguration.setInviteUrl("https://discord.gg/cpxxAGy");
		assertEquals("https://discord.gg/cpxxAGy", konfiguration.getSichereInviteUrl());
	}

	@Test
	public void ohneRedirectUri_isLoginEingerichtet_sollteFalseLiefern()
	{
		DiscordKonfiguration konfiguration = eingerichtet();
		konfiguration.setRedirectUri(null);
		assertFalse(konfiguration.isLoginEingerichtet());

		konfiguration.setRedirectUri(" https://ds.example/ds?module=options&action=discordCallback ");
		assertTrue(konfiguration.isLoginEingerichtet());
		assertEquals("https://ds.example/ds?module=options&action=discordCallback", konfiguration.getRedirectUri());
	}

	@Test
	public void anfrageUrl_vorschlagRedirectUri_sollteDenCallbackVorschlagen()
	{
		assertEquals("http://localhost:8080/ds?module=options&action=discordCallback",
				DiscordKonfiguration.vorschlagRedirectUri("http://localhost:8080/ds?module=admin"));
	}

	@Test
	public void geheimnisseInUmgebungsvariablen_getBotTokenUndClientSecret_solltenVorrangHaben()
	{
		Map<String, String> umgebung = new HashMap<>();
		umgebung.put(DiscordKonfiguration.ENV_BOT_TOKEN, " env-token ");
		DiscordKonfiguration.umgebung = umgebung::get;
		DiscordKonfiguration konfiguration = new DiscordKonfiguration();
		konfiguration.setBotToken("db-token");
		konfiguration.setClientSecret("db-secret");

		assertEquals("env-token", konfiguration.getBotToken());
		assertTrue(konfiguration.isBotTokenAusUmgebung());
		assertTrue(konfiguration.isBotTokenGespeichert());
		assertEquals("db-secret", konfiguration.getClientSecret());
		assertFalse(konfiguration.isClientSecretAusUmgebung());
	}

	@Test
	public void verknuepfung_isVerknuepfungAktuell_sollteNachAblaufNichtMehrGelten()
	{
		long jetzt = System.currentTimeMillis();
		assertTrue(DiscordUser.isVerknuepfungAktuell(jetzt - TimeUnit.DAYS.toMillis(DiscordUser.VERKNUEPFUNG_GUELTIG_TAGE - 1)));
		assertFalse(DiscordUser.isVerknuepfungAktuell(jetzt - TimeUnit.DAYS.toMillis(DiscordUser.VERKNUEPFUNG_GUELTIG_TAGE + 1)));
		assertFalse(DiscordUser.isVerknuepfungAktuell(0L));
		assertFalse(DiscordUser.isVerknuepfungAktuell(null));
	}

	private static DiscordKonfiguration eingerichtet()
	{
		DiscordKonfiguration.umgebung = name -> null;
		DiscordKonfiguration konfiguration = new DiscordKonfiguration();
		konfiguration.setAktiv(true);
		konfiguration.setBotToken("token");
		konfiguration.setGuildId(GUILD);
		konfiguration.setClientId("42");
		konfiguration.setClientSecret("secret");
		return konfiguration;
	}

	private static void erwarteAblehnung(Runnable aufruf)
	{
		try
		{
			aufruf.run();
			fail("DiscordException erwartet");
		}
		catch (DiscordException e)
		{
			// erwartet
		}
	}

	private static Map<String, Long> rollen(long everyone)
	{
		Map<String, Long> rollen = new HashMap<>();
		rollen.put(GUILD, everyone);
		rollen.put(ROLLE, 0L);
		return rollen;
	}

	private static String overwrite(String id, int typ, long erlaubt, long verboten)
	{
		return "{\"id\":\"" + id + "\",\"type\":" + typ + ",\"allow\":\"" + erlaubt + "\",\"deny\":\"" + verboten + "\"}";
	}

	private static JsonObject kanal(String overwrites)
	{
		return JsonParser.parseString("{\"id\":\"7\",\"type\":0,\"permission_overwrites\":" + overwrites + "}").getAsJsonObject();
	}
}
