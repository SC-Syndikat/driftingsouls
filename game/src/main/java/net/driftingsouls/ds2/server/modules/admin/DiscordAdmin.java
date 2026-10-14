package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.WellKnownAdminPermission;
import net.driftingsouls.ds2.server.discord.DiscordException;
import net.driftingsouls.ds2.server.discord.DiscordGuildKanal;
import net.driftingsouls.ds2.server.discord.DiscordKanal;
import net.driftingsouls.ds2.server.discord.DiscordKonfiguration;
import net.driftingsouls.ds2.server.discord.DiscordPruefung;
import net.driftingsouls.ds2.server.discord.DiscordService;
import net.driftingsouls.ds2.server.discord.DiscordZugriff;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.pipeline.Request;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Einstellungen des Discord-Bots fuer den Ingame-Chat: Zugangsdaten, Server und
 * die Rechte je Kanal.
 */
@AdminMenuEntry(category = "Sonstiges", name = "Discord-Bot", permission = WellKnownAdminPermission.DISCORD)
public class DiscordAdmin implements AdminPlugin
{
	@Autowired
	private DiscordService discordService;

	private final List<String> meldungen = new ArrayList<>();
	private final List<String> fehler = new ArrayList<>();

	@Override
	public void output(StringBuilder echo)
	{
		Context context = ContextMap.getContext();
		Request request = context.getRequest();
		EntityManager em = context.getEM();
		DiscordKonfiguration konfiguration = DiscordKonfiguration.laden(em);

		DiscordPruefung pruefung = null;
		try
		{
			switch (request.getParameterString("aktion"))
			{
				case "speichern":
					einstellungenSpeichern(em, request, konfiguration);
					break;
				case "pruefen":
					pruefung = discordService.pruefen(konfiguration);
					break;
				case "synchronisieren":
					synchronisieren(em, konfiguration);
					break;
				case "kanaele":
					kanaeleSpeichern(em, request, konfiguration);
					break;
				default:
					break;
			}
		}
		catch (DiscordException e)
		{
			fehler.add(e.getMessage());
		}

		echo.append("<div class='gfxbox' style='width:900px'>");
		echo.append("<h1>Discord-Bot</h1>");
		ausgabeMeldungen(echo);
		ausgabeEinstellungen(echo, request, konfiguration);
		ausgabePruefung(echo, pruefung);
		ausgabeKanaele(echo, em, konfiguration);
		echo.append("</div>");
	}

	private void einstellungenSpeichern(EntityManager em, Request request, DiscordKonfiguration konfiguration)
	{
		String alteGuildId = konfiguration.getGuildId();
		String altesToken = konfiguration.getBotToken();

		konfiguration.setAktiv("1".equals(request.getParameterString("aktiv")));

		String guildId = request.getParameterString("guild_id").trim();
		if (!guildId.isEmpty() && !guildId.matches("\\d+"))
		{
			fehler.add("Die Server-ID muss eine Zahl sein.");
		}
		else
		{
			konfiguration.setGuildId(leerZuNull(guildId));
		}

		String clientId = request.getParameterString("client_id").trim();
		if (!clientId.isEmpty() && !clientId.matches("\\d+"))
		{
			fehler.add("Die Client-ID muss eine Zahl sein.");
		}
		else
		{
			konfiguration.setClientId(leerZuNull(clientId));
		}

		// Geheimnisse werden nie ausgegeben: ein leeres Feld laesst den bisherigen Wert stehen
		String botToken = request.getParameterString("bot_token").trim();
		if (!botToken.isEmpty())
		{
			konfiguration.setBotToken(botToken);
		}
		if ("1".equals(request.getParameterString("bot_token_loeschen")))
		{
			konfiguration.setBotToken(null);
		}
		String clientSecret = request.getParameterString("client_secret").trim();
		if (!clientSecret.isEmpty())
		{
			konfiguration.setClientSecret(clientSecret);
		}
		if ("1".equals(request.getParameterString("client_secret_loeschen")))
		{
			konfiguration.setClientSecret(null);
		}

		konfiguration.setRedirectUri(leerZuNull(request.getParameterString("redirect_uri").trim()));
		konfiguration.setInviteUrl(leerZuNull(request.getParameterString("invite_url").trim()));

		if (!em.contains(konfiguration))
		{
			em.persist(konfiguration);
		}
		if (!Objects.equals(alteGuildId, konfiguration.getGuildId()) || !Objects.equals(altesToken, konfiguration.getBotToken()))
		{
			discordService.zuruecksetzen();
		}
		meldungen.add("Einstellungen gespeichert.");
	}

	private void synchronisieren(EntityManager em, DiscordKonfiguration konfiguration)
	{
		if (!DiscordKonfiguration.isSetzt(konfiguration.getBotToken()) || !DiscordKonfiguration.isSetzt(konfiguration.getGuildId()))
		{
			fehler.add("Zum Synchronisieren müssen Bot-Token und Server-ID gesetzt sein.");
			return;
		}

		List<DiscordGuildKanal> discordKanaele = discordService.ladeKanaele(konfiguration);
		Map<String, DiscordKanal> vorhandene = new HashMap<>();
		for (DiscordKanal kanal : DiscordKanal.alle(em, konfiguration.getGuildId()))
		{
			vorhandene.put(kanal.getChannelId(), kanal);
		}

		int neu = 0;
		for (DiscordGuildKanal discordKanal : discordKanaele)
		{
			DiscordKanal kanal = vorhandene.remove(discordKanal.id);
			boolean istNeu = kanal == null;
			if (istNeu)
			{
				kanal = new DiscordKanal(konfiguration.getGuildId(), discordKanal.id);
				neu++;
			}
			kanal.setName(discordKanal.name);
			kanal.setKategorie(discordKanal.kategorie);
			kanal.setPosition(discordKanal.position);
			kanal.setBotHinweis(discordKanal.hinweis);
			if (istNeu)
			{
				em.persist(kanal);
			}
		}
		for (DiscordKanal entfernt : vorhandene.values())
		{
			em.remove(entfernt);
		}

		meldungen.add(discordKanaele.size() + " Kanäle synchronisiert, davon " + neu + " neu (gesperrt), "
				+ vorhandene.size() + " nicht mehr vorhandene entfernt.");
	}

	private void kanaeleSpeichern(EntityManager em, Request request, DiscordKonfiguration konfiguration)
	{
		String standard = request.getParameterString("standard");
		for (DiscordKanal kanal : DiscordKanal.alle(em, konfiguration.getGuildId()))
		{
			kanal.setLesen(zugriff(request.getParameterString("lesen_" + kanal.getId()), kanal.getLesen()));
			kanal.setSchreiben(zugriff(request.getParameterString("schreiben_" + kanal.getId()), kanal.getSchreiben()));
			kanal.setStandard(String.valueOf(kanal.getId()).equals(standard));
			if (kanal.isStandard() && kanal.getLesen() != DiscordZugriff.ALLE)
			{
				fehler.add("Der Standardkanal #" + kanal.getName() + " ist nicht für alle Spieler lesbar. Wer ihn nicht lesen darf, bekommt den ersten für ihn lesbaren Kanal angezeigt.");
			}
		}
		meldungen.add("Kanalrechte gespeichert.");
	}

	private static DiscordZugriff zugriff(String wert, DiscordZugriff bisher)
	{
		try
		{
			return DiscordZugriff.valueOf(wert);
		}
		catch (IllegalArgumentException e)
		{
			return bisher;
		}
	}

	private void ausgabeMeldungen(StringBuilder echo)
	{
		for (String meldung : meldungen)
		{
			echo.append("<div style='color:#4ec94e'>").append(Common.escapeHTML(meldung)).append("</div>");
		}
		for (String meldung : fehler)
		{
			echo.append("<div style='color:#ff6060'>").append(Common.escapeHTML(meldung)).append("</div>");
		}
		if (!meldungen.isEmpty() || !fehler.isEmpty())
		{
			echo.append("<br />");
		}
	}

	private void ausgabeEinstellungen(StringBuilder echo, Request request, DiscordKonfiguration konfiguration)
	{
		String redirectUri = konfiguration.getRedirectUri(request.getRequestURL());

		echo.append("<h2>Einstellungen</h2>");
		formularAnfang(echo, "speichern");
		echo.append("<table width='100%'>");
		echo.append("<tr><td width='200'>Chat aktiv</td><td><input type='checkbox' name='aktiv' value='1'")
				.append(konfiguration.isAktiv() ? " checked='checked'" : "").append(" /></td></tr>");
		textzeile(echo, "Server-ID (Guild)", "guild_id", konfiguration.getGuildId(), "z.B. 471610776126226432 - zum Testen einfach einen anderen Server eintragen");
		textzeile(echo, "Einladungslink", "invite_url", konfiguration.getInviteUrl(), "wird Spielern angezeigt, die noch nicht auf dem Server sind");
		geheimzeile(echo, "Bot-Token", "bot_token", konfiguration.getBotToken());
		textzeile(echo, "Client-ID", "client_id", konfiguration.getClientId(), "Developer Portal → OAuth2");
		geheimzeile(echo, "Client-Secret", "client_secret", konfiguration.getClientSecret());
		textzeile(echo, "Redirect-URI", "redirect_uri", konfiguration.getRedirectUri(), "leer = " + redirectUri);
		echo.append("<tr><td></td><td><input type='submit' value='Speichern' /></td></tr>");
		echo.append("</table></form>");

		echo.append("<p>Im Developer Portal unter OAuth2 → Redirects eintragen: <code>").append(Common.escapeHTML(redirectUri)).append("</code><br />");
		if (DiscordKonfiguration.isSetzt(konfiguration.getClientId()))
		{
			String einladung = "https://discord.com/oauth2/authorize?client_id=" + konfiguration.getClientId().trim()
					+ "&scope=bot&permissions=" + DiscordService.BENOETIGTE_RECHTE;
			echo.append("Bot auf den Server einladen: <a href='").append(Common.escapeHTML(einladung)).append("' target='_blank'>Einladungslink des Bots</a>");
		}
		else
		{
			echo.append("Nach dem Eintragen der Client-ID erscheint hier der Einladungslink für den Bot.");
		}
		echo.append("</p>");
	}

	private void ausgabePruefung(StringBuilder echo, DiscordPruefung pruefung)
	{
		echo.append("<h2>Verbindung</h2>");
		if (pruefung != null)
		{
			echo.append("<ul>");
			for (DiscordPruefung.Punkt punkt : pruefung.punkte)
			{
				echo.append("<li style='color:").append(punkt.ok ? "#4ec94e" : "#ff6060").append("'>")
						.append(punkt.ok ? "&#10004; " : "&#10008; ")
						.append(Common.escapeHTML(punkt.text)).append("</li>");
			}
			echo.append("</ul>");
		}
		formularAnfang(echo, "pruefen");
		echo.append("<input type='submit' value='Verbindung prüfen' /></form>");
	}

	private void ausgabeKanaele(StringBuilder echo, EntityManager em, DiscordKonfiguration konfiguration)
	{
		echo.append("<h2>Kanäle</h2>");
		echo.append("<p>Lesen und Schreiben lassen sich getrennt freigeben. Schreiben setzt das Leserecht voraus. ")
				.append("Admins dürfen jeden nicht gesperrten Kanal nutzen. Neue Kanäle sind gesperrt, bis sie hier freigegeben werden. ")
				.append("Spieler bekommen nur Kanäle zu sehen, die sie lesen dürfen; Anfragen an andere Kanäle lehnt der Server ab.</p>");

		formularAnfang(echo, "synchronisieren");
		echo.append("<input type='submit' value='Kanäle von Discord synchronisieren' /></form><br />");

		List<DiscordKanal> kanaele = DiscordKanal.alle(em, konfiguration.getGuildId());
		if (kanaele.isEmpty())
		{
			echo.append("<p>Für diesen Server sind noch keine Kanäle synchronisiert.</p>");
			return;
		}

		formularAnfang(echo, "kanaele");
		echo.append("<table width='100%'>");
		echo.append("<tr><th align='left'>Kanal</th><th align='left'>Lesen</th><th align='left'>Schreiben</th><th>Standard</th><th align='left'>Hinweis</th></tr>");
		String kategorie = null;
		boolean ersteZeile = true;
		for (DiscordKanal kanal : kanaele)
		{
			if (ersteZeile || !Objects.equals(kategorie, kanal.getKategorie()))
			{
				kategorie = kanal.getKategorie();
				echo.append("<tr><td colspan='5' style='padding-top:8px;font-weight:bold;color:#FEB626'>")
						.append(kategorie != null ? Common.escapeHTML(kategorie) : "Ohne Kategorie").append("</td></tr>");
				ersteZeile = false;
			}
			echo.append("<tr><td>#").append(Common.escapeHTML(kanal.getName())).append("</td>");
			echo.append("<td>");
			zugriffAuswahl(echo, "lesen_" + kanal.getId(), kanal.getLesen());
			echo.append("</td><td>");
			zugriffAuswahl(echo, "schreiben_" + kanal.getId(), kanal.getSchreiben());
			echo.append("</td><td align='center'><input type='radio' name='standard' value='").append(kanal.getId()).append("'")
					.append(kanal.isStandard() ? " checked='checked'" : "").append(" /></td>");
			echo.append("<td style='color:#ff6060;font-size:12px'>")
					.append(kanal.getBotHinweis() != null ? Common.escapeHTML(kanal.getBotHinweis()) : "").append("</td></tr>");
		}
		echo.append("<tr><td colspan='5'><br /><input type='submit' value='Kanalrechte speichern' /></td></tr>");
		echo.append("</table></form>");
	}

	private void formularAnfang(StringBuilder echo, String aktion)
	{
		echo.append("<form action='./ds' method='post'>");
		echo.append("<input type='hidden' name='module' value='admin' />");
		echo.append("<input type='hidden' name='namedplugin' value='").append(getClass().getName()).append("' />");
		echo.append("<input type='hidden' name='aktion' value='").append(aktion).append("' />");
	}

	private static void textzeile(StringBuilder echo, String label, String name, String wert, String hinweis)
	{
		echo.append("<tr><td>").append(label).append("</td><td><input type='text' size='60' name='").append(name)
				.append("' value='").append(wert != null ? Common.escapeHTML(wert) : "").append("' />");
		echo.append("<br /><span style='font-size:11px;color:#a0a0a0'>").append(Common.escapeHTML(hinweis)).append("</span></td></tr>");
	}

	private static void geheimzeile(StringBuilder echo, String label, String name, String wert)
	{
		boolean gesetzt = DiscordKonfiguration.isSetzt(wert);
		echo.append("<tr><td>").append(label).append("</td><td><input type='password' size='60' autocomplete='new-password' name='").append(name)
				.append("' placeholder='").append(gesetzt ? "gesetzt - leer lassen, um ihn zu behalten" : "nicht gesetzt").append("' />");
		if (gesetzt)
		{
			echo.append(" <label><input type='checkbox' name='").append(name).append("_loeschen' value='1' /> löschen</label>");
		}
		echo.append("</td></tr>");
	}

	private static void zugriffAuswahl(StringBuilder echo, String name, DiscordZugriff aktuell)
	{
		echo.append("<select name='").append(name).append("'>");
		for (DiscordZugriff zugriff : DiscordZugriff.values())
		{
			echo.append("<option value='").append(zugriff.name()).append("'")
					.append(zugriff == aktuell ? " selected='selected'" : "").append(">")
					.append(zugriff.getLabel()).append("</option>");
		}
		echo.append("</select>");
	}

	private static String leerZuNull(String wert)
	{
		return wert.isEmpty() ? null : wert;
	}
}
