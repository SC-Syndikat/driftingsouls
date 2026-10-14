# Kampagnen- und GameMaster-System: Umsetzungsplan

**Status:** Umgesetzt auf `feature/campaign` (P0–P9). Offen sind die Prüfung auf dem Dev-System und die
Fragen in Abschnitt 8.
**Bezug:** `docs/CAMPAIGN_SYSTEM_PLAN.md` (Anforderungen v4.0)
**Erstellt:** 2026-09-24, **Stand:** 2026-09-25

## Umsetzungsstand

Legende: ✅ umgesetzt · ✅* umgesetzt, aber abweichend vom Plan (die Abweichung steht bei der Phase) ·
❓ offene Frage (Abschnitt 8)

| Phase | Stand | Commit |
|---|---|---|
| P0 Transaktionsgrenze, Branch | ✅* minimal statt Roadmap 3.1–3.3 | `1bb8c3351` |
| P1 Kampagne, Teilnehmer, GM-Rechte, Online-Status | ✅* | `2846f36f5` |
| P2 Kampagnensysteme, Zugangssperre | ✅* | `11073768e` |
| P3 Kampagnen-Sprungpunkte, Kampagnenende | ✅* | `11073768e` |
| P4 „Fertig“-Status | ✅* | `cf90871e9` |
| P5 Schlachtregeln, Kampagnentick | ✅* (P5.2 im Commit von P2/P3) | `cf90871e9` |
| P6 GM-Objekte, Schiffsgruppen, Masterlogin | ✅* ❓ | `f892c27e0` |
| P7 Dialog-Kern | ✅* | `6ca36289b` |
| P8 Interaktionen, Spieler-Dialog | ✅* ❓ | `6ca36289b` |
| P9 GM-Editor | ✅ | `45e8338c8` |

Migrationen: V043 (Kampagne, Teilnehmer), V044 (Systeme, Sprungpunkte), V045 (Schiffsgruppen),
V046 (Dialoge, Variablen, Vertrauen), V047 (Interaktionen, Sessions, Protokoll).

Vokabular gemäß `CONTEXT.md`. Neuer Code verwendet englische Bezeichner. Bestehende Klassen, in die wir
nur Hooks einbauen, werden **nicht** umbenannt (das macht Track Zero).

---

## 0. Getroffene Entscheidungen

| Thema | Entscheidung |
|---|---|
| Persistenz | Neue Tabellen gehören jOOQ (ADR-0004/0005). Vorher werden **Roadmap 3.1–3.3** umgesetzt, damit Hibernate- und jOOQ-Schreibzugriffe in derselben Transaktion laufen. |
| Bestehende Tabellen | `systems`, `jumpnodes`, `ships`, `users`, `bases` bleiben Hibernate-owned. Kampagnen-Zusatzdaten kommen in **eigene jOOQ-Tabellen** und nicht als neue Spalten an Hibernate-Tabellen. |
| GM-Oberfläche | Klassische `AdminPlugin`s (`output(StringBuilder)`) mit jOOQ-Repositories in einer neuen Admin-Kategorie **„Kampagne“**. `EditorForm8`/`EntityEditor` ist an JPA gebunden und wird deshalb für die neuen Tabellen nicht verwendet. Die bestehende Seite `/gamemaster` (Start/Tick/Ende) bleibt und wird nur minimal ergänzt. |
| Spieler-Dialog | Ein Schiffsplugin im Legacy-Template nach dem Muster von `Handelsposten`. |
| GM-Rechte (Grundsatz) | Der GM erhält nur so viele Adminrechte, wie er für die Kampagne braucht. Jedes Recht, das ihm das Flag `GAMEMASTER` gibt, gilt nur für Kampagnenobjekte: Kampagnensysteme und NPC-Accounts, die an der Kampagne teilnehmen. |
| GM-Objekte | Die bestehenden Admin-Plugins (`AddShips`, `CreateObjects`, `EditShip`, `EditBases`) werden weiterverwendet. Der GM erhält die nötigen Rechte automatisch über das Flag `GAMEMASTER`. Nicht-Admins dürfen nur in Kampagnensystemen arbeiten. |
| NPC-Steuerung | Masterlogin nur in NPC-Accounts (`id < 0`), die an der Kampagne teilnehmen. Dass der GM theoretisch jeden NPC teilnehmen lassen und dadurch übernehmen könnte, wird bewusst akzeptiert: Dem GM wird vertraut. |
| Kampagne | Neue, minimale Tabelle `campaign` mit dem Lebenszyklus PREPARING → RUNNING → ENDED. Es gibt höchstens eine nicht beendete Kampagne. |
| Teilnehmer | Sie werden **in der Vorbereitung** ausgewählt, in einer Zwei-Listen-Ansicht (links alle Spieler, rechts die Teilnehmer) mit Online-Status. Beim Start wird die Liste nur noch geprüft und freigegeben. |
| Online-Status | Neu, weil es heute keinen gibt. Ein Tracker im Speicher erfasst die letzte Aktivität pro User, ohne Datenbank-Schreibzugriff pro Request. Online heißt: in den letzten 5 Minuten aktiv. |
| „Fertig“-Sperre | Gilt in Kampagnensystemen für: Schlacht starten/beitreten, Fliegen/Springen, Dialoge, Transfer/Andocken/Landen/Kapern. Der Spieler kann den Status selbst zurücknehmen. |
| Sprungpunkte | Drei Zustände: PREPARED (unsichtbar, gesperrt) → VISIBLE (sichtbar, gesperrt) → ACTIVE. Bei Kampagnenende gehen sie zurück auf PREPARED und werden nicht gelöscht. |
| Kampagnenende | Der GM sieht eine Liste der Spielerschiffe, die noch in Kampagnensystemen sind, und entscheidet: versetzen oder bewusst dort lassen. |
| Bedingungen | Strukturierte, typisierte Bausteine plus Erfolgschance nach der Formel `Basis% + Σ(Faktor × Einflussgröße)` mit Erfolgs- und Misserfolgszweig. |
| Script-Aktion | Java-Aktions-Plugins: Interface plus Annotation, automatisch gefunden wie `@AdminMenuEntry`. Es gibt keine eingebettete Skriptsprache. |
| Vertrauen | Der **Rang bei der NPC-Fraktion** (`UserRank`) wird genutzt. Zusätzlich gibt es einen **eigenen Vertrauenswert**. Er bleibt über Kampagnen hinweg bestehen und berücksichtigt neben den eigenen Aktionen des Spielers anteilig auch die Aktionen seiner Allianzmitglieder. Der GM kann den Wert anpassen oder zurücksetzen. Loyalitätspunkte werden nicht verwendet (sie sind eine Währung). |
| System-Interaktionen | Block „Signale im System“ in der Schiffsansicht. |
| Ergänzungen | Kampagnenvariablen, Interaktions-Limits, Dialog-/Aktionsprotokoll, Schiffsgruppen-Vorlagen. |
| Roadmap | Die Arbeit läuft **parallel zu Track Zero** auf `feature/campaign`. Die Hooks in Altcode bleiben einzeilig, um Merge-Konflikte klein zu halten. |

---

## 1. Bestandsaufnahme: was wiederverwendet wird

Pfade relativ zu `game/src/main/java/net/driftingsouls/ds2/server/`.

| Anforderung | Vorhanden | Verwendung |
|---|---|---|
| Kampagne starten/beenden | `modules/thymeleaf/GameMasterController` (`/gamemaster`), `users.campaign_participant` (V041) | Bleibt. Start und Ende schreiben zusätzlich den Datensatz in `campaign` und lösen die Folgeaktionen aus (Sprungpunkte, Warnungen). Die Teilnehmerauswahl zieht aus START in die Vorbereitung um (P1.4). |
| Online-Status | nicht vorhanden. `sessions` enthält nur Token und Tick, `users.inakt` zählt ganze Ticks. | Neuer `UserActivityTracker` (P1.5). `inakt` wird als Zusatzinfo angezeigt. |
| GM-Rolle | `UserFlag.GAMEMASTER`, Menülink in `MainController:131` | Bleibt und wird zusätzlich Quelle von Rechten (siehe P1.3). |
| Fertig-Status | `users.tick_ready`, `UeberController.tickAction()`, `ueber.html:94-106`, Reset in `UserTick:72` | Bleibt. Neu: Rücknahme, Sperren, automatischer Reset bei einem Angriff. |
| Kampagnentick | `tick campaign run <sys;sys>` → `TickAdminCommand.runRegularTick(null, systems)` → `TickController.isCampaignTick()` in allen Sub-Ticks | Bleibt. Neu: Bestätigungsschritt, Vorauswahl der Kampagnensysteme, Tickzähler. |
| 30-Minuten-Sperre | `tick/regular/BattleTick:54` (hart codiert `-1800`) | Wird parametrisiert. **Befund:** Auch der Kampagnentick wendet heute die 30-Minuten-Sperre an. |
| Schlacht starten | `services/SchlachtErstellenService.erstelle()`, `checkBattleConditions()` (l.441) | Hier werden die Guard-Prüfung und der Verteidiger-Reset eingehängt. |
| Sprung | `ships/SchiffSprungService.sprungViaSprungpunkt()` (l.71) und `sprungViaSchiff()` (l.195) | Hier wird der Zugangs-Guard eingehängt. Heute gibt es keine Prüfung pro User oder System. |
| Sichtbarkeit von Systemen | `StarSystem.isStarmapVisible` / `Access`, Admin `EditSystem` | Der GM-Schalter „sichtbar/versteckt“ setzt `isStarmapVisible` (per Hibernate). |
| Sprungpunkt-Sichtbarkeit | `JumpNode.hidden` (nur für die Anzeige) | PREPARED setzt `hidden=true`. Die Benutzbarkeit prüft der neue Guard. |
| Objekt → Dialog | `modules/schiffplugins/Handelsposten`, `SensorsDefault:554` (Link `communicate`), `SchiffController.ermittlePluginsFuer/pluginAction` | Dient als Vorlage für das Plugin `CampaignDialog` und den Sensor-Link „Interagieren“. |
| Schiffe erzeugen | `ships/SchiffHinzufuegenService.erstelle()`, `AddShips` (Cargo, Offizier, Flotte) | Wird für Schiffsgruppen-Vorlagen und die Aktion „Verstärkung spawnen“ genutzt. |
| Diplomatie | `User.setRelation()`, `User.getRelation()` | Aktion `SET_RELATION` und Bedingung `RELATION`. |
| NPC-Rang | `User.setRank(User npc, int)`, `User.getRank(User npc)` → `UserRank` | Aktion `SET_NPC_RANK` und Bedingung `NPC_RANK`. |
| Belohnungen | `User.transferMoneyFrom()`, `Cargo.addResource()`, `User.addKnownItem()`, `User.addResearch()`, `Ship.consign()` | Belohnungs-Aktionen. |
| Offiziere, Schiffsstatus | `Offizier.getAbility()`, `Ship.getHull/Shields/Crew/Units/Cargo` | Bedingungen und Einflussgrößen. |
| Flottenstärke | `BattleShip.getBattleValue()` (nur innerhalb einer Schlacht) | Die Formel wird in einen Helper außerhalb der Schlacht extrahiert. |
| Nachrichten | `comm/PM.send()` (Absender -1) | Warnungen und GM-Benachrichtigungen. |
| Admin-Framework | `AdminController` (Auto-Discovery via `@AdminMenuEntry`), `WellKnownAdminPermission`, `PermissionDelegatePermissionResolver` | Neue Plugins und Rechte. |
| Plugin-Discovery | `AnnotationUtils.findeKlassenMitAnnotation` | Wird auch für Dialog-Aktionen und -Bedingungen verwendet. |
| BBCode | bestehender Text-/BBCode-Renderer (wie in `Handelsposten`) | Dialogtexte. |
| Migrationen | Flyway `game/src/main/resources/db/migration/`, letzte Version V041 | Neue Migrationen ab V043. |

---

## 2. Datenmodell (neu, jOOQ-owned)

Die Migrationen beginnen bei V043; V042 belegt der Branch `feature/discord-chat`, der zuerst nach main geht. Es ist MySQL. Die jOOQ-Codegen läuft nach Flyway (`game/pom.xml`).

```text
campaign
  id, name, gamemaster_id → users, status (PREPARING|RUNNING|ENDED),
  started_at, ended_at, tick_count

campaign_participant          -- in der Vorbereitung gepflegte Teilnehmerliste
  campaign_id → campaign, user_id → users, added_by → users, added_at   (PK: campaign_id, user_id)
  -- START überträgt die Liste auf users.campaign_participant (Laufzeit-Flag, Hibernate-owned).
  -- Während RUNNING werden Änderungen sofort auf das Flag übertragen.

campaign_star_system          -- Markierung "Teil der Kampagne"
  campaign_id → campaign, star_system_id → systems   (PK: star_system_id, campaign_id)

campaign_jump_node            -- temporäre Zugänge
  jump_node_id → jumpnodes (PK), state (PREPARED|VISIBLE|ACTIVE)

dialog_node                   -- wiederverwendbar, nicht an eine Kampagne gebunden
  id, title (GM-intern), speaker_name, image, text (BBCode)

dialog_option
  id, node_id → dialog_node, sort, text,
  next_node_id → dialog_node (NULL = Ende), failure_node_id (NULL = wie next / Ende),
  base_chance (NULL = immer Erfolg, sonst 0–100), hide_if_unavailable (bool)

dialog_option_condition       -- Verfügbarkeit der Option
  id, option_id, type, params (JSON), negate

dialog_option_modifier        -- Einfluss auf die Erfolgschance
  id, option_id, type, params (JSON), factor (decimal), min, max

dialog_option_action
  id, option_id, sort, outcome (ALWAYS|SUCCESS|FAILURE), type, params (JSON)

interaction
  id, label ("Mit Admiral sprechen"), object_type (SHIP|BASE|STAR_SYSTEM), object_id,
  start_node_id → dialog_node, npc_user_id → users (Sprecher/Fraktion),
  active, max_uses_per_user (NULL = unbegrenzt), cooldown_ticks (NULL = keiner),
  audience (ALL|RESTRICTED)

interaction_audience
  interaction_id, user_id (NULL), alliance_id (NULL)

dialog_session                -- laufender Dialog; verhindert Sprünge zu beliebigen Knoten per URL
  id, user_id, ship_id, interaction_id, current_node_id, started_at

campaign_variable             -- Kampagnenfortschritt
  campaign_id, user_id (0 = global), name, value (bigint)   (PK: alle außer value)

npc_trust_event               -- Vertrauenswert als Ereignisjournal, kampagnenübergreifend
  id, campaign_id (NULL = außerhalb einer Kampagne), npc_user_id, user_id, delta, reason,
  kind (ACTION|GM_CORRECTION), interaction_id (NULL), created_by → users, created_at

npc_trust_setting             -- optional, pro NPC
  npc_user_id (PK), alliance_factor (decimal)   -- fehlt der Eintrag: ConfigValue NPC_TRUST_ALLIANCE_FACTOR (Default 0.5)

interaction_log               -- Protokoll, Grundlage für Limits und Cooldowns
  id, campaign_id, campaign_tick, user_id, ship_id, interaction_id, node_id, option_id,
  chance, success, actions_summary (Text), created_at

ship_group_template / ship_group_template_entry
  template: id, name, description
  entry:    id, template_id, ship_type_id, count, name_pattern, cargo (Cargo-String),
            units (String), officer_skill (NULL = kein Offizier), alarm, create_fleet
```

**Berechnung des Vertrauenswerts:**

```text
trust(user, npc) = Σ deltas des users (ACTION und GM_CORRECTION)
                 + alliance_factor(npc) × Σ ACTION-deltas der aktuellen Allianzmitglieder (ohne user)
```

- Nicht nur die eigenen Aktionen eines Spielers zählen, sondern anteilig auch die Aktionen seiner
  Allianzmitglieder. Maßgeblich ist die Allianzzugehörigkeit zum Zeitpunkt der Auswertung. Tritt ein
  Spieler einer Allianz bei oder verlässt er sie, ändert sich sein Allianzanteil entsprechend.
- Der Wert wird kampagnenübergreifend gespeichert. `campaign_id` dient nur der Nachvollziehbarkeit
  und der Filterung im Protokoll.
- **GM-Korrekturen** sind Ereignisse vom Typ `GM_CORRECTION` und wirken **nur** auf den betroffenen
  Spieler. Sie fließen nicht in den Allianzanteil anderer Mitglieder ein, damit eine Korrektur nicht
  unbeabsichtigt die ganze Allianz verschiebt.
  - „Anpassen“: der GM gibt ein Delta vor.
  - „Zurücksetzen“ bzw. „auf Wert setzen“: der GM gibt einen Zielwert für den Gesamtwert vor. Das
    System berechnet das nötige Delta und schreibt es als Korrekturereignis.
- Weil der Wert als Journal gespeichert wird, bleibt jede Änderung samt Grund und Urheber
  nachvollziehbar, und kein früherer Stand geht verloren.

---

## 3. Querschnitt: `CampaignService` und `CampaignActionGuard`

Die Regeln „Nicht-Teilnehmer“ und „fertig“ laufen durch **einen** zentralen Guard. So braucht jede
Altcode-Stelle nur einen einzeiligen Aufruf.

```java
// neu: net.driftingsouls.ds2.server.campaign
CampaignService
  Optional<Campaign> findOpenCampaign()            // PREPARING oder RUNNING
  boolean isCampaignSystem(int starSystemId)
  Set<Integer> campaignSystemIds()
  long ticksSinceStart()

CampaignActionGuard
  void check(User user, int starSystemId, CampaignAction action)  // wirft ValidierungException
  enum CampaignAction { ENTER_SYSTEM, MOVE, JUMP, START_BATTLE, JOIN_BATTLE,
                        INTERACT, TRANSFER, DOCK, LAND, BOARD }
```

**Regeln in `check`:**
- Das System ist kein Kampagnensystem, oder die Kampagne läuft nicht → erlaubt.
- Der User ist ein NPC (`id < 0`) oder hat das Flag `GAMEMASTER` → erlaubt. So kann der GM seine
  NPC-Objekte steuern.
- Der User nimmt nicht an der Kampagne teil → gesperrt („Nur Kampagnenteilnehmer …“).
- Der User hat `tickReady` gesetzt → gesperrt („Du hast deine Runde beendet – Runde fortsetzen?“,
  mit Link auf die Rücknahme).

---

## 4. Phasen

Jede Phase ist in sich abgeschlossen und lässt sich auf dem Dev-System prüfen. Die Reihenfolge ergibt
sich aus den Abhängigkeiten: Das Dialogsystem (P7–P9) braucht P1 und P2, aber nicht P3–P5.

### P0 – Voraussetzungen *(auf `develop`, vor dem Branch)*

- **P0.1 umgesetzt (minimal, 2026-09-24).** `DBUtil.getConnection(em)` liefert bei aktiver
  Transaktion die Session-Connection. Nachweis: `DBUtilTest`. Die Details und die Korrektur der
  Diagnose zu `MapSectorPopupTest` stehen in `docs/roadmap.md`, Schritt 3. Der ursprünglich geplante
  Weg folgt, er ist bewusst zurückgestellt:
- ~~P0.1 Roadmap 3.1–3.3 vorziehen.~~ Transaktionsgrenze: Transaktionsgrenzen-Tests, Hikari-`DataSource`
  als eigenständiges Bean, `DataSourceTransactionManager`. Danach sehen jOOQ-Lesezugriffe die
  Hibernate-Schreibzugriffe desselben Requests, und ein Fehler rollt beide zurück.
  *Fertig, wenn:* die Tests aus 3.1 grün sind (inkl. der entfernten `@Ignore` in `MapSectorPopupTest`)
  und das Dev-System unter realer Nutzung geprüft wurde.
- **P0.2** Branch `feature/campaign` von `develop` anlegen, regelmäßig mergen oder rebasen
  (parallel zu Track Zero).

### P1 – Kampagne als Objekt und GM-Rechte ✅*

> **Abweichungen:**
> - Die Kampagne wird im Plugin „Kampagne / Teilnehmer“ angelegt („Kampagne vorbereiten“), nicht beim
>   ersten Aufruf von `/gamemaster`. Die GM-Seite verlinkt dorthin.
> - `UserActivityTracker` ist ein einfaches Singleton und kein Spring-Bean.

- **P1.1** Migration V043 `campaign`, jOOQ-Repository `CampaignRepository`, `CampaignService`.
- **P1.2** Den `GameMasterController` minimal erweitern. Die Thymeleaf-Seite existiert schon, es
  entsteht keine neue.
  - Beim ersten Aufruf der Einrichtung wird die Kampagne als PREPARING angelegt (Name, GM).
  - Die START-Ansicht **wählt keine Teilnehmer mehr aus**. Die bisherige Checkbox-Liste
    (`gamemaster.html:85-101`, `startAction` mit `usercheckbox`) entfällt. Die Auswahl passiert in
    der Vorbereitung (P1.4).
  - Stattdessen **prüft und gibt der GM frei**. Die Ansicht zeigt:
    - die vorbereitete Teilnehmerliste (read-only) mit Online-Status, Allianz, NPC-Kennzeichnung und
      Inaktivität, dazu einen Link „Teilnehmer bearbeiten“, der zu P1.4 führt;
    - die Warnungen aus P2.3;
    - Hinweise auf Teilnehmer, die inzwischen im Urlaubsmodus oder gelöscht sind, und auf eine leere
      Liste. Eine leere Liste blockiert den Start.
  - „Kampagne starten“ verlangt eine ausdrückliche Bestätigung (`confirm=1`).
  - START setzt die Kampagne auf RUNNING (`started_at`) und aktiviert die Sprungpunkte (P3).
    Außerdem überträgt START die vorbereitete Liste auf `users.campaign_participant = true`. Das
    bestehende Flag bleibt damit die Laufzeit-Quelle für alle bisherigen Stellen, etwa den
    Kampagnen-Kasten in `ueber.html` und die Tick-Ansicht.
  - Die bisherige Prüfung „bereits eine Kampagne gestartet“ (jemand hat `campaign_participant`)
    wird durch `campaign.status` ersetzt.
  - Die TICK-Ansicht zeigt zusätzlich zum Bereit-Status den Online-Status der Teilnehmer.
  - END zeigt zuerst die Schiffsliste (P3.4) und setzt dann die Kampagne auf ENDED. Die Sprungpunkte
    gehen zurück auf PREPARED, `campaign_participant` und `tick_ready` werden zurückgesetzt.
  - Nebenbefund korrigieren: `"fuuml;r"` in `GameMasterController:150`.
- **P1.3** GM-Rechte ohne händisch gepflegte Permission-Zeilen:
  - Einen `GameMasterPermissionResolver` in die bestehende Resolver-Kette
    (`DefaultAuthenticationManager:272`) aufnehmen. Er vergibt bei `UserFlag.GAMEMASTER` einen festen
    Satz: `admin#sichtbar`, die neuen `CAMPAIGN_*`-Rechte, `AddShips`, `CreateObjects`, `EditShip`,
    `EditBases`. Die Admin-Konsole und alle übrigen Plugins gehören **nicht** dazu.
  - Neue `WellKnownAdminPermission`-Werte: `CAMPAIGN_PARTICIPANTS`, `CAMPAIGN_SYSTEMS`, `CAMPAIGN_JUMPNODES`,
    `CAMPAIGN_DIALOGS`, `CAMPAIGN_INTERACTIONS`, `CAMPAIGN_SHIPGROUPS`, `CAMPAIGN_TRUST`,
    `CAMPAIGN_LOG`.
  - **Grundsatz:** Jedes Recht, das der Resolver dem GM gibt, wird im jeweiligen Plugin auf
    Kampagnenobjekte eingeschränkt, sobald der Nutzer kein Admin ist. Kampagnenobjekte sind
    Kampagnensysteme und die NPC-Accounts, die an der Kampagne teilnehmen (siehe P6.1, P6.3). Ein
    Recht ohne eine solche Einschränkung wird dem GM nicht gegeben.
  - Auf `gamemaster.html` einen Link zur Admin-Kategorie „Kampagne“ ergänzen.
- **P1.4** Teilnehmer in der Vorbereitung: Migration `campaign_participant`, AdminPlugin
  **Kampagne / Teilnehmer** (`CAMPAIGN_PARTICIPANTS`).
  - **Zwei-Listen-Ansicht:** links alle Spieler, rechts die Teilnehmer.
    - Verschieben per Buttons (→, ←, alle →, alle ←) und per Doppelklick; Mehrfachauswahl mit
      Strg/Shift.
    - Über beiden Listen ein Filterfeld (Name/ID) und ein Filter „nur online“.
    - Jeder Eintrag zeigt: Online-Punkt (grün = online, grau = offline, mit Tooltip
      „zuletzt aktiv vor …“), Name (ID), Allianz, NPC-Kennzeichen und Inaktivität in Ticks.
    - Umsetzung: zwei `<select multiple>` mit etwas Inline-JavaScript und ohne neue Bibliothek.
      „Speichern“ sendet die rechte Liste als versteckte Felder. Der Server bildet die Differenz,
      schreibt nur die Änderungen und prüft jede ID (existiert, nicht im Urlaubsmodus).
  - **Quelle der linken Liste:** alle Accounts mit `vaccount = 0`, dazu **NPC-Accounts** (`id < 0`).
    Accounts mit dem Flag `hide` erscheinen nur mit dem Recht
    `STATISTIK_ERWEITERTE_SPIELERLISTE`, wie in der bisherigen START-Ansicht. Der GM selbst wird
    markiert, aber nicht ausgeschlossen.
  - **Änderungen, während die Kampagne läuft (RUNNING):** Die Ansicht bleibt nutzbar, etwa für
    Nachzügler. Änderungen werden dann sofort auf `users.campaign_participant` übertragen.
    - Beim Entfernen eines Teilnehmers erscheint eine Warnung, wenn er noch Schiffe oder Basen in
      Kampagnensystemen hat (dieselbe Abfrage wie P2.3).
    - Das Entfernen setzt auch `tick_ready` zurück und beendet die Möglichkeit zur Masterlogin, falls
      es ein NPC-Account ist (P6.3).
- **P1.5** Online-Status: neuer `UserActivityTracker` (Spring-Singleton).
  - Die Authentifizierungs-Pipeline (`DefaultAuthenticationManager`, bei jedem authentifizierten
    Request) meldet `userId → lastSeen` in eine `ConcurrentHashMap`. Es gibt **keine
    Datenbank-Schreibzugriffe pro Request** und keine Migration.
  - `isOnline(userId)` gilt, wenn die letzte Aktivität höchstens 5 Minuten zurückliegt. Der
    Schwellwert kommt als `WellKnownConfigValue` `ONLINE_THRESHOLD_SECONDS`.
  - Bei einer Masterlogin zählt die Aktivität für den übernommenen NPC-Account, nicht für den GM.
  - Grenze: Nach einem Server-Neustart gilt jeder als offline, bis er wieder aktiv ist. Für
    „zuletzt aktiv“ jenseits des Neustarts gibt es nur die Inaktivität in Ticks (`inakt`). Soll der
    Status einen Neustart überleben, kann man die Map später gedrosselt, etwa alle 60 s, in eine
    Tabelle `user_activity` schreiben.
- *Fertig, wenn:*
  - ein GM ohne Admin-Level eine Kampagne anlegen kann;
  - er in der Vorbereitung Spieler und NPCs per Zwei-Listen-Ansicht mit sichtbarem Online-Status
    als Teilnehmer auswählen und die Liste beim Start geprüft freigeben kann;
  - er die Kampagne beenden kann;
  - er im Adminmenü genau die freigegebenen Plugins sieht;
  - `campaign.status` korrekt wechselt;
  - `users.campaign_participant` erst mit dem Start gesetzt wird.

### P2 – Kampagnensysteme und Zugangsregel ✅*

> **Abweichungen:**
> - Der Guard sperrt das Betreten schon während der **Vorbereitung** für alle außer NPCs und GMs, weil
>   das System dann gerade hergerichtet wird. Während die Kampagne läuft, gilt „nur Teilnehmer“.
> - Die Sichtbarkeit wird außerdem aus dem `StarsystemRepository`-Cache entfernt. Dieser Cache wurde
>   bisher nie geleert, auch nicht von `EditSystem`.

- **P2.1** Migration `campaign_star_system`, Repository, `CampaignService.isCampaignSystem`.
- **P2.2** AdminPlugin **Kampagne / Systeme**:
  - Liste aller Sternensysteme mit ID, Name, `Access`, Sichtbarkeit, `allowBattles` und
    Kampagnenstatus.
  - Aktionen: hinzufügen/entfernen (jOOQ) und sichtbar ↔ versteckt (`StarSystem.setStarmapVisible`
    per Hibernate, weil `systems` Hibernate-owned bleibt; danach TileCache leeren wie in `EditSystem`).
  - Hinweise bei `Access` ≠ `NORMAL` und bei `allowBattles = false`. Beides passt nicht zu einem
    Kampagnensystem.
- **P2.3** Warnungen zu fremden Objekten:
  - Eine jOOQ-Abfrage findet Schiffe und Basen von Nicht-Teilnehmern in Kampagnensystemen
    (`owner > 0` und nicht in `campaign_participant`). Maßgeblich ist die vorbereitete Liste, so
    dass die Warnung schon in PREPARING stimmt.
  - Sie wird beim Hinzufügen eines Systems angezeigt (mit Bestätigung), ebenso beim Kampagnenstart
    in P1.2 (mit Bestätigung).
- **P2.4** `CampaignActionGuard` (Abschnitt 3) mit der Aktion `ENTER_SYSTEM` einhängen:
  - `SchiffSprungService.sprungViaSprungpunkt()` (Zielsystem `node.getSystemOut()`).
  - `SchiffSprungService.sprungViaSchiff()` (Knossos).
  - `SchiffController.inselAction()` (`NPC_ISLAND`-Teleport).
  - Geprüft werden alle Schiffe des Sprungverbands (`findJumpCapableShips` plus gedockte Schiffe).
- *Fertig, wenn:* ein Nicht-Teilnehmer nicht in ein Kampagnensystem springen kann, ein Teilnehmer
  schon, und der GM beim Hinzufügen eines Systems mit fremden Schiffen gewarnt wird.

### P3 – Kampagnen-Sprungpunkte ✅*

> **Abweichungen:**
> - Eine eigene Extraktion der Jumpnode-Erzeugung war nicht nötig: `new JumpNode(...)` genügt.
>   Zusätzlich kann der Rückweg gleich mit angelegt werden.
> - Das Plugin listet auch **normale** Sprungpunkte in und aus Kampagnensystemen und kann sie als
>   Kampagnenknoten übernehmen.
> - GTU-Routen (`ErsteigernController`) überspringen alle Kampagnenknoten. `HandleGanyTransport` ist
>   vollständig auskommentiert und wurde deshalb nicht angepasst.
> - Beim Kampagnenende versetzt der GM Schiffe zu einem **Ausgang**: einem Kampagnenknoten aus einem
>   Kampagnensystem heraus, auf Wunsch alle auf einmal.

- **P3.1** Migration `campaign_jump_node`. Die Zustände werden auf `JumpNode.hidden` abgebildet:
  PREPARED → `hidden=true`, VISIBLE und ACTIVE → `hidden=false`. `hidden` wird per Hibernate
  geschrieben.
- **P3.2** Sperre der Benutzung: In `sprungViaSprungpunkt()` gilt für Knoten in `campaign_jump_node`
  mit Zustand ≠ ACTIVE die Meldung „Der Sprungpunkt ist noch instabil“. PREPARED-Knoten werden
  zusätzlich aus `SensorsDefault.outputJumpnodes()` (dort sieht man heute *alle* Knoten im Sektor) und
  aus `JumpNodeRouter` herausgefiltert.
- **P3.3** AdminPlugin **Kampagne / Sprungpunkte**:
  - Neuen Knoten anlegen: Dafür wird die Jumpnode-Erzeugung aus `CreateObjects` (l.250-257) in einen
    kleinen Service extrahiert, den beide nutzen.
  - Einen bestehenden Knoten als Kampagnenknoten registrieren.
  - Zustand wechseln.
  - Ein Knoten muss mindestens eine Seite in einem Kampagnensystem haben.
- **P3.4** Lebenszyklus:
  - START: alle Knoten → ACTIVE.
  - END: alle Knoten → PREPARED.
  - Vorher zeigt END die Liste der Spielerschiffe in Kampagnensystemen. Pro Schiff oder Flotte kann
    der GM „zum Ausgang des Eintrittspunkts versetzen“ (Standard: der nächste Kampagnenknoten auf der
    Nicht-Kampagnenseite) oder „belassen“ wählen. Das Versetzen nutzt die bestehende
    Positionslogik (Flotte plus gedockte Schiffe, analog `SchiffSprungService`).
- *Fertig, wenn:* ein vorbereiteter Knoten weder sichtbar noch benutzbar ist, ein VISIBLE-Knoten
  sichtbar, aber gesperrt ist, ab dem Start der Sprung funktioniert und nach dem Ende der
  PREPARED-Zustand wiederhergestellt ist.

### P4 – „Fertig“-Status ✅*

> **Abweichungen:**
> - START_BATTLE wird in `AngriffController` geprüft und **nicht** in `checkBattleConditions()`.
>   Letztere läuft auch beim Rote-Alarm-Autoangriff, und dort ist der wartende Spieler der Angreifer.
> - DOCK/LAND werden an den Controller-Aktionen geprüft (`SchiffController` land/start/aufladen/abladen,
>   `FleetMgntController` undock/redock/jstart/jland) und nicht an `Ship.dock/land`, die auch Ticks
>   aufrufen.
> - Der Angriffshinweis in der Übersicht wird über „gelesen“ quittiert, weil die Übersicht
>   `readOnly` ist. Spätestens das nächste „Bereit“ löscht ihn ebenfalls.

- **P4.1** Rücknahme: `UeberController.tickAction()` erhält eine Gegenaktion (`resumeTurnAction`,
  setzt `tickReady=false`). `ueber.html:94-106` zeigt dann „Runde fortsetzen“.
- **P4.2** Sperren über `CampaignActionGuard.check(...)` (einzeilige Aufrufe):

  | Aktion | Stelle |
  |---|---|
  | START_BATTLE | `SchlachtErstellenService.checkBattleConditions()` (Angreifer) |
  | JOIN_BATTLE | `Battle.load(...)` bzw. `AngriffController` beim Beitritt |
  | MOVE | `SchiffFlugService` (Einstieg des Flugs) |
  | JUMP | `SchiffSprungService.sprungViaSprungpunkt/sprungViaSchiff` |
  | INTERACT | `DialogService` (P8) |
  | TRANSFER | `TransportController.transferAction`, `UnitTauschController.transferAction`, `PluendernController.transferAction` |
  | DOCK / LAND | `Ship.dock/undock/land/start` bzw. deren Controller-Aktionen in `SchiffController` und `FleetMgntController` |
  | BOARD | `KapernController` |

  Der **Rote-Alarm-Autoangriff** (`SchiffFlugService.handleAlert`) wird *nicht* gesperrt, weil er
  keine aktive Handlung des wartenden Spielers ist. Der Verteidiger ist dort der fliegende Spieler,
  und der kann nicht „fertig“ sein, weil MOVE gesperrt ist.
- **P4.3** Automatischer Reset bei einem Angriff mit einem neuen `CampaignBattleNotifier`:
  - Aufgerufen wird er am Ende von `SchlachtErstellenService.erstelle()` und beim Beitritt eines
    Angreifers zu einer bestehenden Schlacht.
  - Für jeden User der Gegenseite gilt: Er nimmt teil, ist `tickReady` und die Schlacht liegt in
    einem Kampagnensystem. Dann wird `tickReady=false` gesetzt, eine PM gesendet („Du wurdest
    angegriffen, Runde fortgesetzt“) und die User-Einstellung `CAMPAIGN_ATTACK_NOTICE`
    (`WellKnownUserValue`) gesetzt.
  - `ueber.html` zeigt den Hinweis einmal an und löscht ihn danach.
- *Fertig, wenn:* ein fertiger Spieler in einem Kampagnensystem keine der Aktionen ausführen kann, sie
  in anderen Systemen aber weiter funktionieren, und ein Angriff auf ihn den Status zurücksetzt und
  ihn benachrichtigt.

### P5 – Schlachtregeln und Kampagnentick ✅*

> **Abweichungen:**
> - Die Sperre wird pro Schlacht als `lockSeconds(...)` berechnet. Mit 0 Sekunden wird im
>   Kampagnentick nie übersprungen, ohne die übrige `blockCount`-Logik anzufassen.
> - Getestet ist die Berechnung als Unit-Test. Die DB-Szenarien mit echten Schlachten fehlen, siehe
>   Abschnitt 8.

- **P5.1** `BattleTick` parametrisieren. Die Konstante `1800` wird durch eine Sperrzeit pro Schlacht
  ersetzt:
  - Regulärer Tick, normales System: 1800 s (unverändert).
  - Regulärer Tick, Kampagnensystem: 120 s.
  - Kampagnentick: keine Sperre. `blockCount` und die Prüfung auf `lastaction` entfallen, die Runde
    wird sofort berechnet.

  Die Werte kommen als `WellKnownConfigValue` (`BATTLE_TICK_LOCK_SECONDS`,
  `CAMPAIGN_BATTLE_TICK_LOCK_SECONDS`), damit sie ohne Deployment geändert werden können.
- **P5.2** Kampagnentick in `GameMasterController.tickAction()`:
  - Die Systemauswahl enthält nur Kampagnensysteme, alle sind vorausgewählt (bisher alle sichtbaren
    Systeme).
  - Sind nicht alle Teilnehmer `tickReady`, zeigt die Seite die Liste der Wartenden und verlangt
    `confirm=1`.
  - Neuer Sub-Tick `CampaignTick`. Er läuft nur bei `isCampaignTick()` und wird in
    `RegularTick.executeTicks()` vor `BattleTick` eingereiht. Er erhöht `campaign.tick_count`.
    Der Reset von `tickReady` bleibt in `UserTick`.
- *Fertig, wenn:* eine 1 Minute vor dem Kampagnentick gestartete Schlacht in diesem Tick eine Runde
  weiterläuft, eine 1 Minute vor dem regulären Tick gestartete Schlacht in einem Kampagnensystem
  nicht, eine 5 Minuten vorher gestartete aber schon. Das wird mit Tests belegt.

### P6 – Objekte für den GM ✅* ❓

> **Abweichungen:**
> - `EditorForm8` erhielt `validateUpdate(...)`. Diese Prüfung läuft **vor** dem Commit und
>   verwirft die Änderung. Die bestehenden Post-Update-Tasks laufen erst nach einem Commit.
> - Die Listen in `EditShip` und `EditBases` sind für den GM nicht gefiltert. Er sieht alle Objekte,
>   darf aber nur Kampagnenobjekte ändern. ❓
> - `CreateObjects` bietet dem GM nur Basis und Nebel an. Jumpnodes laufen über die
>   Kampagnen-Sprungpunkte, und SystemXML liest Serverpfade.
> - Die Masterlogin ist auch während der **Vorbereitung** möglich, damit der GM NPC-Objekte
>   platzieren kann. Sie wird in `login.log` protokolliert. ❓
> - `ShipGroupSpawner` erzeugt die Schiffe direkt wie `AddShips`, ohne `SchiffHinzufuegenService`.
>   Dessen Namensgenerator braucht Rassendaten, und der Name kommt ohnehin aus der Vorlage.

- **P6.1** Einschränkung auf Kampagnensysteme in `AddShips`, `CreateObjects`, `EditShip`, `EditBases`,
  wenn der Nutzer kein Admin ist:
  - Zielsystem und bei Bearbeitungen auch das Ursprungssystem müssen Kampagnensysteme sein.
  - Für die `EntityEditor`-Plugins braucht `EditorForm8` einen kleinen Hook, etwa ein
    `allowedFor(User, E)`-Prädikat, das auf die Listenansicht und den Update angewendet wird.
    Vorher prüfen, ob sich ein bestehender Pre-Update-Hook dafür eignet.
- **P6.2** Schiffsgruppen-Vorlagen: Migration, AdminPlugin **Kampagne / Schiffsgruppen** (CRUD) und
  die Schaltfläche „jetzt spawnen“ (Besitzer ist ein NPC-User, Ziel ist ein Sektor im
  Kampagnensystem). Die Spawn-Logik steckt in `ShipGroupSpawner`: `SchiffHinzufuegenService.erstelle`
  plus Cargo, Einheiten, Offizier und Flotte wie in `AddShips` (l.257-343). Sie wird in P7 von den
  Aktionen wiederverwendet.
- **P6.1a** Als Besitzer neuer Objekte kann ein GM ohne Admin-Level nur NPC-Accounts wählen, die an
  der Kampagne teilnehmen. Das gilt für die Owner-Felder in `AddShips`, `EditShip`, `EditBases` und
  für den Spawn aus einer Vorlage.
- **P6.3** NPC-Schiffe steuern: Die Masterlogin (`PlayerLoginSuper`) wird dem GM über den Resolver
  freigegeben, aber nur für NPC-Accounts (`id < 0`), die an der **laufenden** Kampagne teilnehmen.
  - Die Auswahlliste und die serverseitige Prüfung in `PlayerLoginSuper` werden entsprechend
    eingeschränkt.
  - Endet die Kampagne oder verliert der NPC-Account den Teilnehmerstatus, ist keine neue
    Masterlogin mehr möglich.
  - Akzeptiertes Restrisiko: Der GM kann jeden NPC teilnehmen lassen und ihn dadurch übernehmen.
    Dem GM wird vertraut. Jede Masterlogin eines GMs wird ins `interaction_log` bzw. Admin-Log
    geschrieben, damit sie nachvollziehbar bleibt.
- *Fertig, wenn:* ein GM ohne Admin-Level in Kampagnensystemen Schiffe, Basen und Asteroiden anlegen
  und bearbeiten kann, außerhalb davon nicht, und wenn er eine Vorlage spawnen kann.

### P7 – Dialog-Kern (ohne UI) ✅*

> **Abweichungen:**
> - Bedingungen und Einflussgrößen teilen sich eine Schnittstelle `DialogFactor` (liefert eine Zahl).
>   Eine Bedingung vergleicht diese Zahl mit einer Schwelle (`>= <= > < == !=`), ein Modifikator
>   gewichtet sie. Ja/Nein-Faktoren liefern 1 oder 0.
> - Die Spalte `negate` entfällt, der Vergleich `!=` deckt das ab.
> - Die Faktoren stehen als verschachtelte Klassen in `StandardFactors`, die Aktionen in
>   `StandardActions`. Gefunden werden sie über `@DialogFactor.Type` bzw. `@DialogAction.Type`.

- **P7.1** Migrationen für `dialog_node`, `dialog_option`, `dialog_option_condition`,
  `dialog_option_modifier`, `dialog_option_action`, `campaign_variable` und `npc_trust_event`, dazu
  die Repositories.
- **P7.2** Auswertungskontext `DialogContext`: User, Schiff, Objekt, NPC-User, Kampagne,
  Kampagnentick, Allianz.
- **P7.3** Bedingungen als Plugins: `interface DialogCondition { boolean test(ctx, params); }` und
  Einflussgrößen `interface DialogInfluence { double value(ctx, params); }`. Beide werden per
  Annotation `@DialogConditionType("…")` gefunden (`AnnotationUtils.findeKlassenMitAnnotation`).
  Jeder Typ deklariert seine Parameter (`ParamDef`: Name, Typ, Label, Optionen), damit der Editor die
  Felder generisch rendern und validieren kann.

  | Typ | Bedingung | Einflussgröße | Quelle |
  |---|---|---|---|
  | `RELATION` | NPC↔User = FRIEND/ENEMY/NEUTRAL | – | `User.getRelation` |
  | `NPC_RANK` | Rang ≥ n | Rang | `User.getRank(npc)` |
  | `TRUST` | Vertrauen ≥ n | Vertrauen | `npc_trust_event` |
  | `OFFICER_SKILL` | Fähigkeit (NAV/ING/WAF/SEC/COM) ≥ n | Wert | `Ship.getOffizier().getAbility` |
  | `SHIP_TYPE` | Typ ∈ Liste | – | `Ship.getTypeData` |
  | `SHIP_STATUS` | Hülle/Schilde/Crew in % ≥ n | Prozent | `Ship` |
  | `CARGO` | Ressource ≥ n | Menge | `Ship.getCargo` |
  | `UNITS` | Einheitentyp ≥ n | Menge | `Ship.getUnits` |
  | `FLEET_STRENGTH` | Stärke im Sektor ≥ n | Stärke | neuer `FleetStrength`-Helper (Formel aus `BattleShip.getBattleValue`) |
  | `CAMPAIGN_TICKS` | Ticks seit Start ≥ n | Ticks | `campaign.tick_count` |
  | `VARIABLE` | Variable (User oder global) vergleichen | Wert | `campaign_variable` |
  | `RESEARCH` | erforscht | – | `User.hasResearched` |
  | `ALLIANCE` | Mitglied einer Allianz / einer bestimmten Allianz | – | `User.getAlly` |
- **P7.4** Erfolgschance:
  `chance = clamp(base_chance + Σ factor × clamp(influence, min, max), 0, 100)`. Gewürfelt wird mit
  einem injizierbaren `RandomSource`, damit Tests deterministisch sind. Das Ergebnis wählt den Zweig
  (`next_node_id` oder `failure_node_id`) und die Aktionen (`outcome`).
- **P7.5** Aktionen als Plugins: `interface DialogAction { void execute(ctx, params);
  String describe(params); }` mit `@DialogActionType("…")` und derselben `ParamDef`-Deklaration.

  | Gruppe | Typen |
  |---|---|
  | Diplomatie | `SET_RELATION` (Richtung NPC→User / User→NPC / beide), `SET_NPC_RANK`, `CHANGE_TRUST` |
  | Belohnung | `GIVE_RESOURCE` (Item oder Ware ins Schiffscargo), `GIVE_CREDITS` (`transferMoneyFrom` vom NPC), `UNLOCK_ITEM` (`addKnownItem`), `UNLOCK_RESEARCH` (`addResearch`), `GIVE_SHIP` (Objektschiff per `Ship.consign` oder neu aus einer Vorlage) |
  | Schlacht | `START_BATTLE`: NPC-Schiff (das Interaktionsobjekt oder eine frisch gespawnte Vorlage) greift das Spielerschiff über `SchlachtErstellenService.erstelle(npc, npcShip, playerShip, true)` an. Damit greift automatisch auch der Verteidiger-Reset aus P4.3. |
  | Script | `SPAWN_SHIP_GROUP` (Verstärkung, `ShipGroupSpawner`), `REPAIR_SHIP`, `SET_VARIABLE`/`ADD_VARIABLE`, `SET_INTERACTION_ACTIVE` (Kampagnenphasen), `SEND_PM` (an Spieler oder GM) |

  Alle Aktionen einer Auswahl laufen in **einer** Transaktion (dafür P0.1). Scheitert eine Aktion,
  zum Beispiel weil der Cargo voll ist oder das Ziel Noob-Schutz hat, wird alles zurückgerollt. Der
  Spieler bekommt eine Meldung, das Log einen Eintrag.
- **P7.6** `DialogEngine`: `start(...)` und `choose(session, optionId)`.
  - Serverseitig wird geprüft, dass die Option zum aktuellen Knoten der Session gehört und verfügbar
    ist.
  - Danach wird gewürfelt, die Aktionen ausgeführt, `interaction_log` geschrieben und der nächste
    Knoten gesetzt oder die Session beendet.
- *Fertig, wenn:* Unit-Tests pro Bedingung, Einflussgröße und Aktion grün sind, dazu ein Engine-Test
  über einen dreistufigen Dialog mit Zufall, Variablen und Vertrauen samt Allianzanteil.

### P8 – Interaktionen und Spieler-Erlebnis ✅* ❓

> **Abweichungen:**
> - Es gibt keinen Link in der Sensorliste. Das Plugin „Kontakte“ steht oben in der Schiffsansicht und
>   listet Schiffe und Basen im Sektor sowie „Signale im System“ mit ihren Interaktionen. Es
>   verschwindet, sobald nichts angeboten wird. ❓
> - Spieler sehen die Erfolgschance nicht, nur „geklappt“ oder „misslungen“. Die Vorschau zeigt sie
>   dem GM. ❓

- **P8.1** Migrationen für `interaction`, `interaction_audience`, `dialog_session`, `interaction_log`,
  dazu `DialogService.availableInteractions(user, ship, object)`. Eine Interaktion ist verfügbar, wenn
  alle Punkte zutreffen:
  - Sie ist aktiv.
  - Das Objekt liegt in einem Kampagnensystem.
  - Schiff und Objekt sind im selben Sektor; bei `STAR_SYSTEM` reicht dasselbe System.
  - Der Spieler gehört zur Zielgruppe.
  - `max_uses_per_user` und `cooldown_ticks` (gezählt über `interaction_log.campaign_tick`) sind
    nicht überschritten.
  - `CampaignActionGuard` mit INTERACT erlaubt es.
- **P8.2** Einstieg aus der Sensorliste: `SensorsDefault` ergänzt bei Schiffen und Basen mit
  verfügbaren Interaktionen einen Link „Interagieren“ (analog `communicate`, l.554). Bei mehreren
  Interaktionen erscheint zuerst eine Auswahlliste.
- **P8.3** Schiffsplugin `CampaignDialog` nach dem Muster von `Handelsposten`:
  - `SchiffController.ermittlePluginsFuer` nimmt es bei gesetztem Parameter `interaction` auf, und
    `pluginAction` leitet `choose` weiter.
  - Das neue Legacy-Template `schiff.campaigndialog.html` orientiert sich an
    `schiff.handelsposten.html`: Sprecher, Bild, BBCode-Text, Antwortlinks.
  - Nicht verfügbare Optionen werden je nach `hide_if_unavailable` ausgeblendet oder ausgegraut.
- **P8.4** Block „Signale im System“ in der Schiffsansicht für `STAR_SYSTEM`-Interaktionen. Er
  erscheint, wenn sich das Schiff in einem Kampagnensystem befindet.
- *Fertig, wenn:* der Use Case 7.1 aus den Anforderungen auf dem Dev-System vollständig durchspielbar
  ist.

### P9 – GM-Editor ✅

> Umgesetzt als Plugins „Dialoge“, „Interaktionen“ (mit Vorschau), „Vertrauen“ und „Protokoll“. Das
> Protokoll zeigt zusätzlich die Kampagnenvariablen. Der Formular-Helfer heißt `CampaignAdminHtml`.

Alle Plugins liegen in der Admin-Kategorie „Kampagne“. Ein gemeinsames kleines Formular-Hilfsmodul
(`CampaignAdminForms`) erzeugt HTML und **escaped jede Ausgabe**. Vorher prüfen, was
`modules/admin/editoren/HtmlUtils` schon bietet.

- **P9.1 Dialoge**:
  - Liste der Knoten; beim Bearbeiten eines Knotens werden die Optionen inline gepflegt, jeweils mit
    Bedingungen, Modifikatoren und Aktionen. Die Parameterfelder werden aus `ParamDef` erzeugt.
  - Strukturansicht als eingerückter Baum: Knoten → Option → Folgeknoten, mit Markierung von Zyklen.
  - Validierung: unerreichbare Knoten, tote Verweise, Optionen ohne Ausgang, ungültige Parameter.
  - Löschen erst nach Prüfung der Referenzen (Interaktionen, andere Optionen).
- **P9.2 Interaktionen**:
  - Anlegen: Objekttyp und Objekt wählen (Auswahl auf Kampagnensysteme beschränkt), Startknoten,
    NPC-Sprecher, Label, Limits, Zielgruppe.
  - Aktivieren und deaktivieren.
  - **Vorschau**: den Dialog als ausgewählter Spieler mit ausgewähltem Schiff durchklicken.
    Bedingungen und Chancen werden echt ausgewertet und angezeigt. Aktionen werden nur beschrieben
    (`describe`) und nicht ausgeführt. Das Ergebnis lässt sich wahlweise auf Erfolg oder Misserfolg
    erzwingen.
- **P9.3 Vertrauen** (`CAMPAIGN_TRUST`):
  - Matrix Spieler × NPC mit dem Gesamtwert, aufgeteilt in den eigenen Anteil und den Allianzanteil.
  - Aufklappbares Journal mit allen Ereignissen samt Grund und Urheber.
  - Aktionen: **Anpassen** (Delta plus Pflicht-Grund) und **Zurücksetzen / auf Wert setzen**
    (Zielwert). Beide schreiben `GM_CORRECTION`-Ereignisse und wirken nicht auf die
    Allianzmitglieder.
  - Den Allianzfaktor pro NPC pflegen (`npc_trust_setting`).
  - Ein GM ohne Admin-Level sieht und ändert nur die NPC-Accounts, die an der Kampagne teilnehmen.
- **P9.4 Protokoll**: `interaction_log` filtern nach Spieler, Interaktion und Tick. Zusätzlich kann
  die GM-PM-Benachrichtigung über die Aktion `SEND_PM` an Schlüsselstellen eingesetzt werden.
- *Fertig, wenn:* ein GM ohne Datenbankzugriff einen verzweigten Dialog mit Bedingungen, Zufall und
  Aktionen anlegen, einem Objekt zuweisen, per Vorschau testen und im Protokoll nachvollziehen kann.

---

## 5. Tests

- **Unit-Tests:** `CampaignActionGuard` (Matrix aus Teilnehmer/fertig/NPC/GM/System), Chancenformel,
  jede Bedingung, Einflussgröße und Aktion (mit Fakes), Vertrauensberechnung mit Allianzanteil
  (inklusive Allianzwechsel und GM-Korrekturen, die nicht an die Allianz weitergegeben werden),
  Einschränkung der GM-Rechte auf Kampagnenobjekte (Systeme, Besitzer, Masterlogin),
  `UserActivityTracker` (Schwellwert, Masterlogin), Differenzbildung beim Speichern der
  Teilnehmerliste.
- **Integration:** Die Teilnehmerliste wird in PREPARING gepflegt, ohne `users.campaign_participant`
  zu berühren. START überträgt sie. Änderungen während RUNNING greifen sofort.
- **`DBTest`-Integrationstests** (laufen in CI auf JDK 11):
  - Sperrzeiten im `BattleTick` (Szenarien aus P5).
  - Sprung-Sperren (P2, P3).
  - Reset bei einem Angriff (P4.3).
  - Rollback einer Dialogauswahl, bei der eine Aktion fehlschlägt (dieser Test hängt von P0.1 ab).
- **Manuell auf dem Dev-System:** die Use Cases 7.1, 7.2, 8.1 und 8.2 aus den Anforderungen.

---

## 6. Risiken

- **P0.1 ändert die Transaktionssemantik aller Requests.** Die Roadmap verlangt dafür ausdrücklich
  eine Prüfung auf dem Dev-System. Ohne P0.1 sind Dialogaktionen nicht atomar.
- **Parallelität mit Track Zero:** Die Hooks liegen genau in den Klassen, die umbenannt werden
  (`SchiffSprungService`, `SchlachtErstellenService`, `SchiffFlugService` …). Deshalb bleiben die
  Hooks einzeilig, die Logik steckt in neuen Klassen, und `develop` wird häufig gemergt.
- **Viele Einstiegspunkte für die Fertig-Sperre (P4.2):** Übersehene Pfade sind möglich, etwa die
  Ajax-Varianten wie `SchiffAjaxController`. Vor der Umsetzung alle Aufrufer der Zielmethoden
  aufzählen.
- **Neue Legacy-Templates** vergrößern den Portierungsumfang von Roadmap 4.2. Das wurde bewusst in
  Kauf genommen; es bleibt bei einem Dialog-Template.
- **ADR-0005-Cache-Gefahr:** Innerhalb einer Dialogauswahl kein `em.find()` auf Tabellen, die per jOOQ
  geschrieben werden. Die neuen Tabellen werden ausschließlich per jOOQ angefasst.

## 7. Geklärte Punkte und Annahmen

1. **Masterlogin für GMs (P6.3):** *Geklärt.* Nur NPC-Accounts, die an der Kampagne teilnehmen. Dass
   der GM jeden NPC teilnehmen lassen könnte, wird akzeptiert, weil ihm vertraut wird.
2. **Vertrauenswert:** *Geklärt.* Er wird kampagnenübergreifend gespeichert. Die Aktionen der
   Allianzmitglieder fließen anteilig ein, ohne Beschränkung auf eine Kampagne. Der GM kann den Wert
   anpassen und zurücksetzen (P9.3).
3. **Regulärer Tick in Kampagnensystemen:** *Geklärt.* Er läuft dort normal weiter. Nur die
   Schlachtsperrzeit ist auf 2 Minuten verkürzt.
4. **Asteroiden:** *Annahme:* Asteroiden sind `Base`s, auch ohne Besitzer. „Brocken“ (Felsbrocken)
   sind Schiffe. Beide werden über die Objekttypen `BASE` und `SHIP` abgedeckt.
5. **Nebenbefunde** außerhalb dieses Plans, die aufgefallen sind:
   - `TickAdminCommand.runRareTick` startet den Job des regulären Ticks.
   - ~~`HandleGanyTransport` fragt die nicht gemappte Property `Ship.scriptData` ab~~: *Korrektur.* Die
     Stelle liegt in einem vollständig auskommentierten Block (Zeilen 77–260) und läuft nie.
   - Der Parameter `only` und `affectedSystems` lassen sich im `AbstractTickExecuter` nicht
     kombinieren.
   - Der gesamte Rumpf von `MapController.sectorAction()` ist auskommentiert. Die drei ignorierten
     `MapSectorPopupTest`s scheitern daran und nicht an ADR-0002 (siehe `docs/roadmap.md`).
   - Die Caches in `StarsystemRepository` und `JumpNodeRepository` wurden nie geleert. Beide haben
     jetzt Leer-Methoden, die aber nur die Kampagnen-Plugins aufrufen.

## 8. Offene Fragen

Beantwortet am 2026-09-25:

1. **Erfolgschance anzeigen?** *Entschieden: nein*, sonst wird es zu einfach. So ist es umgesetzt.
2. **Einstieg über die Sensorliste?** *Entschieden: ja*, mit derselben Mechanik wie beim Handelsposten.
   ✅ Schiffe und Basen mit Interaktionen tragen das Kontakt-Symbol („Mit … Kontakt aufnehmen“). Bei
   genau einer Interaktion startet es sie direkt, bei mehreren öffnet es die Auswahl im Kasten
   „Kontakte“. Der Kasten zeigt sonst nur noch die „Signale im System“ und das laufende Gespräch.
3. **Listen in EditShip/EditBases filtern?** *Entschieden: ja.* ✅ `EditorForm8.restrictVisible(...)`
   filtert Auswahlliste, Tabelle und Formular. Auch ein fremdes Objekt, das direkt per `entityId`
   aufgerufen wird, erscheint nicht.
4. **Masterlogin in der Vorbereitung:** noch offen, wird später angeschaut.
5. **GIVE_CREDITS:** bleibt vorerst, wie es ist.
6. **Fehlende Tests:** vorerst zurückgestellt.
7. **Weitere Einstiege absichern:** *Entschieden: ja.* ✅ Der Guard prüft jetzt auch:
   - Schiff: Alarmstufe (auch Ajax), Übergabe, Selbstzerstörung, Flottenbeitritt, Schilde und alle
     Aktionen der Schiffsplugins außer dem Kampagnendialog;
   - Flotte: Übergabe, Schilde, Batterien, Zusammenlegen, Alarm, Kampfbereitschaft, Nahrung, Crew,
     Demontage, Reparatur, Bau;
   - Crew- und Offizierstausch, Deuterium sammeln, Handel mit Handelsposten, GTU-Zwischenlager,
     Kolonisieren, Schiffswerft;
   - Basis: Gebäude (inklusive Anzeige und Ajax, weil Gebäude dort Aktionen ausführen), Bau, Kern
     und die Aktionen der Seite `/base` (inklusive Scan);
   - Aktionen in einer laufenden Schlacht;
   - GTU: Die Dropzone und Ganymed-Transporte dürfen nicht in ein Kampagnensystem führen, das der
     Spieler nicht betreten darf.


## 9. Zweite Runde (Rückmeldung vom 2026-10-13)

1. **Misserfolg mit Aktion statt Folgeknoten**, damit der Spieler einen Schlachtbeginn nicht durch
   Abbrechen des Dialogs abwehren kann. ✅ Das ging technisch schon: Aktionen „bei Misserfolg“ laufen
   sofort bei der Auswahl, ein Folgeknoten ist optional. Der Editor macht das jetzt sichtbar: Die
   Aktionen sind nach „immer“, „bei Erfolg“ und „bei Misserfolg“ gruppiert, mit Hinweis am
   Misserfolgsfeld.
2. **Bessere Formatierung** der Kampagnen-Seiten. ✅ Gemeinsamer Stil mit mehr Zeilenabstand,
   Abständen in Tabellen, Formularen und Abschnitten (`CampaignAdminHtml.begin/end`).
3. **Testdaten für Schiffe, Asteroiden, Systeme.** ✅ Seit dem Import des Produktions-Backups vom
   2026-10-12 enthält das Dev-System die echten Daten (295 Accounts, 40 Systeme, rund 100.000
   Schiffe). Darauf liegt die Testkampagne „Testkampagne Maelstrom“ (`docs/testdata/`):
   - System 621 Maelstrom, Sprungpunkte 105/106 „Riss im Maelstrom“ (612:150/150 ↔ 621:20/20);
   - Teilnehmer 90003 (Spieler) und -900 (NPC), Schiffsgruppen-Vorlage 1 „Piratenrotte“;
   - Flaggschiff Vex (-900, 621:20/20) mit dem Dialog „Mit Admiral Vex sprechen“: friedlicher Weg
     (Nahrung gegen Credits, Freundschaft), Erpressung mit Erfolgschance und Schlacht bei
     Misserfolg, Auftrag ab Vertrauen 10, Abgabe der Datendisk;
   - System-Interaktion „Notsignal untersuchen“ (einmalig): Datendisk und Variable bei Erfolg,
     Hinterhalt der Piratenrotte bei Misserfolg;
   - `reset_test_campaign.sql` setzt den Fortschritt des Spielers zurück.

   Beim Anlegen fielen zwei Fehler auf: Der Prod-Dump enthielt keine Stored Procedures (im Dev
   nachträglich angelegt), und der alte Fremdschlüssel `ships.id → ship_history.id` verhinderte
   jedes neue Schiff (V049 entfernt ihn; AddShips und CreateObjects speichern die Historie jetzt
   mit).
4. **Dropdown für die Objekt-ID** bei den Interaktionen, gefiltert auf den Objekttyp und auf
   Kampagnensysteme, mit Name und Position. ✅ Dazu Besitzer, und der sprechende NPC als Auswahl der
   teilnehmenden NPC-Accounts.
5. **Rückweg aus der Schiffsansicht und dem Adminmenü.** ✅ In der Schiffsansicht sehen GMs und
   Admins die Interaktionen des Schiffs mit Links zum Bearbeiten und Hinzufügen. EditShip und
   EditBases haben ein Feld „Kampagne“ mit Link auf die Interaktionen des Objekts; neue Interaktionen
   sind dann vorbelegt.
6. **Limits für Kampagnen-Sprungpunkte.** ✅ Migration V048, `CampaignJumpNodeLimitService`:
   - erlaubte Schiffsklassen und erlaubte Schiffstypen;
   - maximale Punkte je Schiff und je Spieler;
   - das Spieler-Limit gilt wahlweise für die ganze Kampagne oder pro Kampagnentick;
   - jeder Durchgang wird in `campaign_jump_node_passage` protokolliert;
   - geprüft wird der ganze Verband (Flotte und gedockte Schiffe), NPCs und GMs sind ausgenommen;
   - alle Kombinationen sind möglich;
   - Dialog-Aktionen „Kampagnen-Sprungpunkt (de)aktivieren“ und „Limits eines
     Kampagnen-Sprungpunkts ändern“ (leer = unverändert, `-` = Limit aufheben);
   - Pflege im Plugin „Sprungpunkte“ über „Limits bearbeiten“, mit dem verbrauchten Kontingent je
     Spieler.

   GP sind die Größenpunkte des Schiffs (Standard). Je Sprungpunkt kann stattdessen der
   **Schlachtwert** zählen (Größe × Modifikator + Jägerdocks × Modifikator, nur Kriegsschiffe).
7. **Eigenes GM-Menü statt Adminmenü.** ✅ Ein GM ohne Admin-Level sieht das Adminmenü nicht mehr.
   Die GM-Seite listet alle Werkzeuge als Unterpunkte von „Kampagne vorbereiten“, und auf den
   Plugin-Seiten erscheint dasselbe Menü statt des Admin-Baums. Der Admin-Link im Hauptmenü ist für
   ihn ausgeblendet. Technisch laufen die Plugins weiter über den `AdminController`, damit
   Rechteprüfung und Formulare unverändert bleiben (`GameMasterMenu`).
8. **Kampagnenvariablen verwalten.** ✅ Eigenes Plugin „Kampagnenvariablen“ (auch im GM-Menü):
   anzeigen, setzen, löschen, global oder je teilnehmendem Spieler. „Quest“ heißt überall
   „Kampagne“.
9. **Antworten nur einmal wählbar.** ✅ Migration V050 (`dialog_option.once_per_user`): Eine so
   markierte Antwort kann jeder Spieler je Kampagne nur einmal wählen; danach ist sie versteckt bzw.
   ausgegraut. Zurückgerollte Versuche zählen nicht.
10. **Spieler sieht, was die Aktionen bewirkt haben.** ✅ Nach einer Antwort erscheinen die sichtbaren
    Folgen (z.B. „Abgegeben: 10× Nahrung“, „Erhalten: 3.000 RE“, „Das Vertrauen von … wurde
    gestärkt.“ – Vertrauen ist interne Währung, der Wert bleibt verborgen –, „Erhalten: 1× Datendisk“,
    Beziehung, Schlacht). Variablen, Verstärkungen, Sprungpunkte, Interaktionen und
    PMs an den GM bleiben verborgen (`DialogAction.playerMessage`).
11. **Schlacht aus einem Dialog.** ✅ Startet eine Antwort eine Schlacht, zeigt die folgende Seite statt
    der Fehlermeldung „Das Schiff ist in einen Kampf verwickelt“ die Reaktion des NPCs, die Folgen
    und den Link „Zur Schlacht“. Der Schlachtbildschirm zeigt Antwort und Reaktion oben, solange die
    Schlacht läuft (Migration V051, `campaign_battle_note`).
12. **GM-Navigation bleibt erhalten.** ✅ Wer als Admin über das GM-Menü ein Werkzeug öffnet, behält
    das GM-Menü auch bei Folgeklicks (Sitzungs-Flag); „Zum Admin-Menü“ oder die Admin-Startseite
    wechseln zurück.
13. **Teilerfolg.** ✅ Migration V052: Je Antwort eine Teilerfolg-Spanne in Prozentpunkten nach der
    Erfolgschance und ein eigener Folgeknoten; Aktionen „bei Teilerfolg“; Vorschau kann ihn erzwingen.
14. **Bedingter Text in Knoten.** ✅ `[wenn name >= 2]…[sonst]…[/wenn]` (auch `global:name`) zeigt Text
    abhängig von Kampagnenvariablen (`DialogText`).
15. **Alle Schiffs- und Offizierswerte als Faktoren.** ✅ „Schiffswert“ (aktueller Zustand und alle
    Zahlenwerte des Schiffstyps, z.B. Sensorreichweite, Größe, Hülle, Docks), „Schiffstyp hat
    Eigenschaft“ (jedes Schiffstyp-Flag, z.B. AWACS-SRS), „Fähigkeit oder Rang des Offiziers“ (alle
    fünf Fähigkeiten und Rang), „Zufallszahl“ (von–bis).
16. **Testkampagne: verzerrter Notruf.** ✅ `docs/testdata/campaign_notruf.sql`: einmal je
    Kampagnentick entschlüsseln (20 % Erfolg, 40 % Teilerfolg, Rest Misserfolg; plus Sensorreichweite,
    AWACS-Flags, Technik des Offiziers und Zufall). Die Nachricht wird schrittweise lesbar und nennt
    621:35/42; dort liegt das Trümmerteil mit der Datendisk für Vex.
17. **Bausteine für NPC-Begegnungen.** ✅ Faktor „Kampfkraft-Verhältnis Spieler / NPC im Sektor“;
    Schlacht gegen „ein Schiff des NPC im Sektor“; Ware an ein NPC-Schiff abgeben bzw. aus dem Cargo des
    Gesprächsschiffs erhalten (V053 setzt die Standardwerte bestehender Aktionen); Schiffsgruppe
    erscheinen lassen und dabei eine Vorlage-Interaktion nur für diesen Spieler auf das erste Schiff
    übertragen; „Schiffe des NPC im Sektor abziehen“.
18. **Testkampagne: Piraten am Wrack.** ✅ `docs/testdata/campaign_piraten.sql`: Die Durchsuchung
    (50 % + Sicherheit des Offiziers) kann Kapitän Skarn (NPC -901) anlocken. Disk übergeben,
    überzeugen (Kommando, Kampfkraft) oder kämpfen. Ist die Disk verloren, stellt Vex einmalig einen
    Raider. Bei dreifacher Kampfkraft gibt Skarn die Disk heraus; sonst angreifen, lahmlegen, plündern.
19. **Ein Gespräch bindet den Sektor.** ✅ Solange ein Spieler ein Gespräch führt, können seine Schiffe im
    Sektor des Gesprächs weder abfliegen noch springen, Waren oder Crew tauschen, handeln oder
    angreifen (`CampaignActionGuard.dialogDenial`, an allen Schiffsaktionen). Weitere Schiffe dürfen
    hineinfliegen und sind dann ebenfalls gebunden. Knoten mit „Spieler muss antworten“ (V054) lassen
    sich weder beenden noch durch ein anderes Gespräch ersetzen; Skarns Forderung ist so markiert.
    Startet eine Antwort eine Schlacht, endet das Gespräch. Dabei geschlossen: Der neue Flugweg
    (`ShipsMovement`) prüfte die Kampagnenregeln bisher gar nicht.
20. **Anfragen an den GameMaster.** ✅ Migration V055. In jedem Gespräch gibt es zusätzlich „Etwas anderes …
    (Anfrage an den GameMaster)“ mit Textfeld. Die Anfrage (Spieler, Schiff, Interaktion, Knoten) landet
    als Unterhaltung beim GM; er bekommt eine PM. Die GM-Startseite zeigt offene Anfragen, das Plugin
    „Spieler-Anfragen“ die Details mit Links zum Objekt, zur Vorschau und zum Protokoll: antworten
    (Spieler erhält eine PM), jede Dialog-Aktion für Spieler und Schiff ausführen, das Gespräch bei
    einem Knoten fortsetzen, abschließen. Der Spieler sieht die Unterhaltung in seiner Schiffsansicht
    und kann antworten; die GM-Box eines NPC-Schiffs zeigt dessen offene Anfragen.
21. **Interaktionen nur an NPC-Objekten.** ✅ Wird ein NPC-Schiff gekapert oder an einen Spieler
    übergeben, werden seine Interaktionen gelöscht (`Ship.consign`, `KapernController`). Zusätzlich
    bietet der DialogService Interaktionen nur an Schiffen und Basen von NPCs an, und der Editor lässt
    nur solche Objekte zu.
