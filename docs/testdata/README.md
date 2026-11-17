# Testkampagne „Maelstrom“

Testdaten für das Kampagnensystem auf einer Datenbank mit Produktionsdaten (Backup vom 2026-10-12).
Die Skripte setzen voraus, dass die Kampagne, ihr System, die Sprungpunkte, die erste Vorlage und die
Schiffe schon angelegt sind. Das geht über die GM-Werkzeuge; die Schritte stehen unten.

Die Schiffe suchen die Skripte über Besitzer und Namen (das Wrack über seine Position), weil ihre IDs
von der Datenbank abhängen. Fehlt eines, bricht das Skript mit „Subquery returns more than 1 row“ ab,
bevor es etwas ändert.

## Voraussetzungen

**Accounts** (in den Skripten fest verdrahtet):

| ID | Rolle |
|---|---|
| 90002 | GameMaster (Flag `gamemaster`) |
| 90003 | Spieler |
| -900 | NPC „Maelstrom-Flotte“, mit Flag `nodeserteur` |

Den NPC -901 „Piraten vom Roten Nebel“ legt `campaign_piraten.sql` als Kopie von -900 an.

**Sold:** Der Kampagnentick ist ein voller Tick, die Besatzungen wollen Sold. Ohne Geld desertieren die
Schiffe zu NPC -15. NPCs zahlen keinen Sold, wenn sie wie die NPCs im laufenden Spiel das Flag
`nodeserteur` tragen. `reset_test_campaign.sql` setzt es für -900 und -901 und gibt dem Spieler
100.000 RE.

**Über die GM-Werkzeuge anlegen**, als 90002:

1. Kampagne vorbereiten: Kampagne 1 „Testkampagne Maelstrom“, Teilnehmer 90003 und -900.
2. Systeme: 621 Maelstrom zur Kampagne hinzufügen.
3. Sprungpunkte: „Riss im Maelstrom“ von 612:150/150 nach 621:20/20, mit Rückweg.
4. Schiffsgruppen: Vorlage 1 „Piratenrotte“, mit Flotte:
   - 2× Typ 20 (Triton CV 34 Raider), „Pirat {n}“, Alarm rot, Offizier mit 40 in allen Fähigkeiten;
   - 3× Typ 271 (AMF Kentáuroi), „Piraten-Jäger {n}“, Alarm rot, ohne Offizier.
5. Schiffe hinzufügen:

| Name | Besitzer | Typ | Position | Cargo |
|---|---|---|---|---|
| Flaggschiff Vex | -900 | 5 (GTCv Deimos) | 621:20/20 | beliebig |
| Testkreuzer | 90003 | 16 (GTC Fenris) | 612:150/150 | beliebig |
| Testfrachter | 90003 | 27 (GTT Triton) | 612:150/150 | 50 Nahrung (der Reset setzt `16\|50\|0\|0;17\|80\|0\|0;20\|40\|0\|0`) |
| beliebig, wird „Trümmerteil“ | -900 | 20 (Triton CV 34 Raider) | 621:35/42 | beliebig |

## Reihenfolge

```sh
mysql ds < campaign_test_dialogs.sql   # Vex-Dialog, System-Interaktion „Notsignal“
mysql ds < campaign_notruf.sql         # verzerrter Notruf, benennt und belegt das Trümmerteil
mysql ds < campaign_piraten.sql        # Kapitän Skarn (NPC -901), Vorlagen 2 und 3
```

Danach startet der GM die Kampagne auf `/gamemaster`. Der Spieler 90003 steht mit beiden Schiffen am
Sprungpunkt in 612:150/150.

`reset_test_campaign.sql` setzt den Fortschritt des Spielers zurück: gespawnte NPC-Schiffe,
Leih-Raider, Schlachten aus Dialogen, Gespräche, Anfragen, beide Protokolle (Interaktionen und
GM-Handlungen), Variablen, Vertrauen, Beziehungen, Position und Ladung der Spielerschiffe und die
Konten. Die Kampagne selbst und ihre Dialoge bleiben.

Die Interaktionen legen die Skripte für Kampagne 1 an (`interaction.campaign_id`, ab V059).

Die drei Aufbauskripte legen Datensätze mit festen IDs an (Dialogknoten, Optionen, Interaktionen 1–4,
Vorlagen 2 und 3) und lassen sich deshalb nur einmal auf eine leere Kampagne anwenden.
