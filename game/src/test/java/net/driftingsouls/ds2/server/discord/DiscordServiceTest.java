package net.driftingsouls.ds2.server.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DiscordServiceTest
{
	private static final String GUILD = "100";
	private static final String BOT = "200";
	private static final String BOT_ROLLE = "300";
	private static final String ANDERE_ROLLE = "400";

	@Test
	public void discordMarkup_formatContent_sollteLesbarenTextLiefern()
	{
		// setup
		Map<String, String> users = Collections.singletonMap("42", "Bob");
		Map<String, String> channels = Collections.singletonMap("7", "handel");

		// run
		String text = DiscordService.formatContent("Hi <@42> und <@!43>, siehe <#7> und <#8> <@&99> <:ds:12345> <a:tanz:6789>", users, channels);

		// assert: Server-Emojis bleiben stehen, die zeigt der Chat als Bild an
		assertEquals("Hi @Bob und @unbekannt, siehe #handel und #kanal @Rolle <:ds:12345> <a:tanz:6789>", text);
	}

	@Test
	public void kurzformen_formatContent_sollteSieInEmojisUmwandeln()
	{
		String text = DiscordService.formatContent("Super :thumbsup: :heart: :gibtsnicht: um 10:30:45", Collections.emptyMap(), Collections.emptyMap());

		assertEquals("Super 👍 ❤ :gibtsnicht: um 10:30:45", text);
	}

	@Test
	public void serverEmojiMitUnicodeNamen_formatContent_sollteEsNichtZerstoeren()
	{
		String text = DiscordService.formatContent("<:heart:123> <a:smile:456>", Collections.emptyMap(), Collections.emptyMap());

		assertEquals("<:heart:123> <a:smile:456>", text);
	}

	@Test
	public void kurzformenBeimSenden_ersetzeKurzformen_sollteServerEmojisBevorzugen()
	{
		Map<String, String> serverEmojis = Collections.singletonMap("heart", "<:heart:999>");

		String text = DiscordService.ersetzeKurzformen(":heart: :thumbsup: :small_green_triangle:", serverEmojis);

		assertEquals("<:heart:999> 👍 :small_green_triangle:", text);
	}

	@Test
	public void sonderzeichenImText_formatContent_sollteSieUnveraendertLassen()
	{
		String text = DiscordService.formatContent("Preis: 5$ \\o/ <b>", Collections.emptyMap(), Collections.emptyMap());

		assertEquals("Preis: 5$ \\o/ <b>", text);
	}

	@Test
	public void spielername_webhookUsername_sollteVerboteneTeileEntfernen()
	{
		assertEquals("Max", DiscordService.webhookUsername("Max@Discord"));
		assertEquals("Spieler", DiscordService.webhookUsername("clyde"));
		assertEquals("Spieler", DiscordService.webhookUsername("everyone"));
		assertEquals("Spieler", DiscordService.webhookUsername(null));
		assertEquals(80, DiscordService.webhookUsername("x".repeat(100)).length());
	}

	@Test
	public void avatar_avatarUrl_sollteCdnUrlLiefern()
	{
		assertEquals("https://cdn.discordapp.com/avatars/123/abc.png?size=64", DiscordUser.avatarUrl("123", "abc"));
		assertEquals("https://cdn.discordapp.com/embed/avatars/" + ((471610776126226432L >>> 22) % 6) + ".png", DiscordUser.avatarUrl("471610776126226432", ""));
	}

	@Test
	public void alleRechteAufDemServer_fehlendeKanalRechte_sollteNichtsMelden()
	{
		DiscordService.BotRechte rechte = botRechte(DiscordService.BENOETIGTE_RECHTE);

		assertNull(DiscordService.fehlendeKanalRechte(rechte, GUILD, kanal("[]")));
	}

	@Test
	public void kanalFuerEveryoneGesperrt_fehlendeKanalRechte_sollteFehlendesAnsehenMelden()
	{
		DiscordService.BotRechte rechte = botRechte(DiscordService.BENOETIGTE_RECHTE);

		String hinweis = DiscordService.fehlendeKanalRechte(rechte, GUILD,
				kanal("[{\"id\":\"" + GUILD + "\",\"type\":0,\"allow\":\"0\",\"deny\":\"" + DiscordService.RECHT_KANAL_ANSEHEN + "\"}]"));

		assertEquals("Bot fehlt: Kanäle ansehen", hinweis);
	}

	@Test
	public void kanalFuerEveryoneGesperrtAberFuerBotRolleErlaubt_fehlendeKanalRechte_sollteNichtsMelden()
	{
		DiscordService.BotRechte rechte = botRechte(DiscordService.BENOETIGTE_RECHTE);

		String hinweis = DiscordService.fehlendeKanalRechte(rechte, GUILD, kanal("["
				+ "{\"id\":\"" + GUILD + "\",\"type\":0,\"allow\":\"0\",\"deny\":\"" + DiscordService.RECHT_KANAL_ANSEHEN + "\"},"
				+ "{\"id\":\"" + BOT_ROLLE + "\",\"type\":0,\"allow\":\"" + DiscordService.RECHT_KANAL_ANSEHEN + "\",\"deny\":\"0\"}]"));

		assertNull(hinweis);
	}

	@Test
	public void kanalNurFuerAndereRolle_fehlendeKanalRechte_sollteFehlendesAnsehenMelden()
	{
		DiscordService.BotRechte rechte = botRechte(DiscordService.BENOETIGTE_RECHTE);

		String hinweis = DiscordService.fehlendeKanalRechte(rechte, GUILD, kanal("["
				+ "{\"id\":\"" + GUILD + "\",\"type\":0,\"allow\":\"0\",\"deny\":\"" + DiscordService.RECHT_KANAL_ANSEHEN + "\"},"
				+ "{\"id\":\"" + ANDERE_ROLLE + "\",\"type\":0,\"allow\":\"" + DiscordService.RECHT_KANAL_ANSEHEN + "\",\"deny\":\"0\"}]"));

		assertEquals("Bot fehlt: Kanäle ansehen", hinweis);
	}

	@Test
	public void webhooksFuerBotVerboten_fehlendeKanalRechte_sollteWebhooksMelden()
	{
		DiscordService.BotRechte rechte = botRechte(DiscordService.BENOETIGTE_RECHTE);

		String hinweis = DiscordService.fehlendeKanalRechte(rechte, GUILD,
				kanal("[{\"id\":\"" + BOT + "\",\"type\":1,\"allow\":\"0\",\"deny\":\"" + DiscordService.RECHT_WEBHOOKS_VERWALTEN + "\"}]"));

		assertEquals("Bot fehlt: Webhooks verwalten", hinweis);
	}

	@Test
	public void botIstAdministrator_fehlendeKanalRechte_sollteOverwritesIgnorieren()
	{
		DiscordService.BotRechte rechte = botRechte(-1L);

		assertNull(DiscordService.fehlendeKanalRechte(rechte, GUILD,
				kanal("[{\"id\":\"" + GUILD + "\",\"type\":0,\"allow\":\"0\",\"deny\":\"" + DiscordService.RECHT_KANAL_ANSEHEN + "\"}]")));
	}

	@Test
	public void keineRedirectUri_getRedirectUri_sollteSieAusDerAnfrageAbleiten()
	{
		DiscordKonfiguration konfiguration = new DiscordKonfiguration();

		assertEquals("http://localhost:8080/ds?module=options&action=discordCallback",
				konfiguration.getRedirectUri("http://localhost:8080/ds"));

		konfiguration.setRedirectUri(" https://ds.example/ds?module=options&action=discordCallback ");
		assertEquals("https://ds.example/ds?module=options&action=discordCallback",
				konfiguration.getRedirectUri("http://localhost:8080/ds"));
	}

	private static DiscordService.BotRechte botRechte(long serverRechte)
	{
		DiscordService.BotRechte rechte = new DiscordService.BotRechte();
		rechte.botId = BOT;
		rechte.rollen.add(BOT_ROLLE);
		rechte.serverRechte = serverRechte;
		return rechte;
	}

	private static JsonObject kanal(String overwrites)
	{
		return JsonParser.parseString("{\"id\":\"1\",\"type\":0,\"permission_overwrites\":" + overwrites + "}").getAsJsonObject();
	}
}
