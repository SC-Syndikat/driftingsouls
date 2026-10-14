package net.driftingsouls.ds2.server.discord;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class DiscordServiceTest
{
	@Test
	public void kanalliste_parse_sollteGueltigeEintraegeInReihenfolgeLiefern()
	{
		// run
		List<DiscordChannel> channels = DiscordChannel.parse(" 123:Allgemein ; kaputt:X;456; 789:Handel: und Wandel;;");

		// assert
		assertEquals(3, channels.size());
		assertEquals("123", channels.get(0).id);
		assertEquals("Allgemein", channels.get(0).name);
		assertEquals("456", channels.get(1).id);
		assertEquals("456", channels.get(1).name);
		assertEquals("789", channels.get(2).id);
		assertEquals("Handel: und Wandel", channels.get(2).name);
	}

	@Test
	public void leereKanalliste_parse_sollteLeereListeLiefern()
	{
		assertEquals(0, DiscordChannel.parse("").size());
		assertEquals(0, DiscordChannel.parse(null).size());
	}

	@Test
	public void discordMarkup_formatContent_sollteLesbarenTextLiefern()
	{
		// setup
		Map<String, String> users = Collections.singletonMap("42", "Bob");
		Map<String, String> channels = Collections.singletonMap("7", "handel");

		// run
		String text = DiscordService.formatContent("Hi <@42> und <@!43>, siehe <#7> und <#8> <@&99> <:ds:12345> <a:tanz:6789>", users, channels);

		// assert
		assertEquals("Hi @Bob und @unbekannt, siehe #handel und #kanal @Rolle :ds: :tanz:", text);
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
}
