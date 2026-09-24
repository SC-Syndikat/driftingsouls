# Kampagnen- und GameMaster-System: Funktionale Anforderungen

**Status:** Anforderungsdokument
**Version:** 4.0
**Erstellt:** 2026-09-24
**Ziel:** Ein GameMaster-System für episodische Kampagnen (wie DnD-Abende)

---

## 🎯 ÜBERBLICK

Das System ermöglicht einem GameMaster:
- Eine **Kampagne** zu starten und zu beenden
- Bestimmte **Sternensysteme** als Kampagnen-Orte zu konfigurieren
- Zugänge über **Sprungpunkte** in diese System zu konfigurieren und aktivieren
- **Objekte** (Schiffe, Basen, Brocken etc) in diesen Systemen zu erstellen
- **Dialog-Interaktionen** zwischen Spielern und Objekten (z.B. Geheimbündnis mit NPC starten, Quests, Kämpfe)
- **Schlacht-Regeln** für Kampagnensysteme (2 Min statt 30 Min Sperrzeit vor regulärem Tick)
- **Spieler-Status** zu verwalten (wer ist "fertig" mit dieser Runde)
- **Kampagnenticks** durchzuführen

---

## 📋 KERNFEATURES

### 1. KAMPAGNENSYSTEM (EXISTIEREND + MINIMAL ERWEITERT)

#### 1.1 Kampagne Starten/Beenden
- **Was:** Bestehende Kampagnen-Verwaltung wird verwendet (nicht ändern)
- **Nutzen:** Keine neuen Features nötig, nur Erweiterung der Integrationspunkte

#### 1.2 Kampagnenspieler-Status bei Schlacht
- **Was:** Ein Spieler kann seine Runde als "fertig" markieren
- **Regel 1:** Spieler mit "fertig"-Status darf keine Aktionen im Kapagnensystem mehr ausführen, insbesondere keine neue Schlacht im Kampagnensystem initiieren
- **Regel 2:** Wenn ein Spieler angegriffen wird, aber bereits "fertig" ist:
  - dem Spieler eine Warnung anzeigen
  - "Fertig"-Status automatisch zurücksetzen, damit Spieler reagieren kann

---

### 2. KAMPAGNENSYSTEME (STERNENSYSTEM-MARKIERUNGEN)

#### 2.1 Sternensystem-Status
- **Was:** Jedes Sternensystem kann einen Status haben:
  - `Nicht in Kampagne` (Standard)
  - `Teil der Kampagne`

#### 2.2 GameMaster-Operationen
- **Was:** GameMaster kann Systeme verwalten:
  - Systeme zur aktiven Kampagne hinzufügen/entfernen
  - Systeme zwischen sichtbar ↔ versteckt wechseln
  - Liste aller Sternensysteme ansehen
  - vergleiche hier auch das bestehende Adminmenü

#### 2.3 Spieler-Zugriff
- **Was:** Spieler können:
  - nur Spieler, die an der Kamapagne teilnehmen, können mit Schiffen Kampagnensysteme betreten und dort Aktionen durchführen. Sollten Schiffe von nicht teilnehmenden Spielern in einem Kampagnensystem sein, mus der GameMaster vor Kampagnenstart gewarnt werden. Er soll auch gewarnt werden, wenn er den Status für ein solches System anpasst, damit er das System entsprechend vorbereiten kann.

---

### 3. DIALOG-SYSTEM (WIEDERVERWENDBAR)

#### 3.1 Was sind Dialoge?
- **Konzept:** Eine Dialogkette ist eine Abfolge von Text-Nachrichten und Spieler-Entscheidungen
- **Wiederverwendbar:** Derselbe Dialog kann bei verschiedenen Objekten oder NPCs verwendet werden
- **Dynamisch:** Jede Antwort-Option kann mehrere Aktionen auslösen (Belohnung, Beziehung ändern, Schlacht, etc.)

#### 3.2 Dialog-Aufbau
- **Intro-Text:** Die Nachricht des NPCs/Objekts
- **Antwort-Optionen:** Der Spieler kann wählen zwischen mehreren Antworten
- **Verkettung:** Jede Antwort kann zu einem neuen Dialog führen oder den Dialog beenden
- **Aktionen:** Pro Antwort können Aktionen ausgelöst werden

#### 3.3 Dialog-Aktion-Typen
1. **Diplomatie-Aktion**
   - Beziehung zu Spieler ändern (Freund, Feind, Neutral)
   - NPC-Vertrauenspunkte vergeben/abnehmen

2. **Belohnungs-Aktion**
   - Items/Ausrüstung an Spieler geben
   - Kredits transferieren
   - Waren frei schalten
   - Schiff übergeben

3. **Schlacht-Aktion**
   - NPC/Objekt fordert zum Kampf auf
   - Schlacht mit vordefinierter Konstellation einleiten

4. **Script-Aktion**
   - Custom-Logik ausführen (z.B. Verstärkungen spawnen, Schiff reparieren)
   - Erweiterbar für zukünftige Features

5. **Zufallsfaktoren und Abhängigkeiten**
   - Die Optionen sollen einstellbare Zufallsfaktoren und Einflussgrößen haben, wie zB
    - Diplomatiestatus
    - Offizierswerte
    - Typ und Status des interagierenden Schiffes
    - Verfügbare Ressourcen / Truppen auf dem Schiff
    - Flottenstärke aller Schiffe des interagierenden Spielers auf dem Sektor
    - Ticks seit Kampagnenstart
    - ...

#### 3.4 Dialog-Management (GameMaster-Perspektive)
- **Was:** GameMaster kann:
  - Neue Dialoge erstellen
  - Dialoge bearbeiten (Text, Optionen, Aktionen)
  - Dialoge löschen
  - Dialoge in Objekt-Interaktionen verknüpfen

---

### 4. OBJEKT-INTERAKTIONEN

#### 4.1 Was ist eine Interaktion?
- **Konzept:** Ein Objekt (Schiff, Basis, Asteroid, Sternensystem) kann mit Spielern interagieren
- **Funktion:** Interaktion = Objekt + Dialog + Meta-Informationen

#### 4.2 Interaktions-Lifecycle
- **Erstellen:** GameMaster verknüpft ein Objekt mit einem Dialog
- **Aktivieren/Deaktivieren:** GameMaster kann Interaktionen an/ausschalten (z.B. nur für bestimmte Quest-Phase)
- **Spieler-Zugriff:** Spieler sieht verfügbare Interaktionen beim Anklicken des Objekts
- **Ausführen:** Spieler startet Dialog, wählt Optionen, Aktionen werden ausgelöst

#### 4.3 Objekt-Typen
- Schiffe (fremdgesteuert durch GameMaster)
- Basen
- Asteroiden
- Sternensysteme (globale Interaktionen)

#### 4.4 GameMaster-Operationen
- **Was:** GameMaster kann:
  - Interaktion zu Objekt hinzufügen
  - Dialog austauschen
  - Interaktion aktivieren/deaktivieren
  - Interaktion testen (Preview)

---

### 5. SCHLACHT-REGELN IN KAMPAGNENSYSTEMEN

#### 5.1 Zeitrestriktionen
- **Normales Sternensystem:** 30 Minuten Sperrzeit vor Tick. Schlachten, die innerhalb dieser Zeit gestartet wurden, werden im regulären Tick nicht ausgeführt. (ist bereits implementiert)
- **Kampagnensystem (regulärer Tick):** 2 Minuten Sperrzeit bis zum nächsten regulären Tick
- **Kampagnensystem (Kampagnen-Tick):** Keine Sperrzeit, Schlachten sofort im selben Tick berechnet

#### 5.2 Kampagnen-Tick vs. Regulärer Tick
- **Regulärer Tick:**
  - Läuft automatisch alle X Stunden
  - Schlachten mit zu kurzer Erstellungszeit werden **nicht** verarbeitet
  - Werden im **nächsten** Tick verarbeitet

- **Kampagnen-Tick:**
  - Wird manuell vom GameMaster ausgelöst
  - **Alle** Schlachten werden verarbeitet (auch die gerade erstellten)
  - GameMaster muss Ausführung bestätigen mit Hinweis, falls nicht alle Spieler ready sind

#### 5.3 Spieler-Einschränkungen
- **Regel:** Spieler mit "fertig"-Status kann KEINE neue Schlacht starten
- **Ausnahme:** Wenn Spieler angegriffen wird, wird Status automatisch zurückgesetzt


---

### 6. JUMP-NODE-SICHTBARKEIT

#### 6.1 Bestehende Funktionalität erweitern
- Die Kampagnensysteme benötigen Zugänge. Hierzu möchte ich temporäre Jumpnodes verwenden. Diese Jumpnodes sollen vorab erstellt und konfiguriert werden können, aber für die Spieler zunächst unsichtbar bleiben, bis der GameMaster sie sichtbar schaltet. Erst ab Kampagnenstart sollen die Jumpnodes aktiviert werden, sodass die Spieler die Kampagnensysteme betreten können. Mit dem Ende der Kampagne sollen diese Sprungpunkte automatisch wieder entfernt werden.

---

### 7. SPIELER-ERLEBNIS (USE CASES)

#### 7.1 Spieler besucht Kampagnensystem
```
1. Spieler navigiert zu Kampagnensystem
2. Schiff landet, Liste der Objekte wird angezeigt
3. Spieler klickt auf Objekt
4. Verfügbare Interaktionen werden angezeigt
   (z.B. "Mit Admiral sprechen", "Handeln", "Kampf annehmen")
5. Spieler klickt auf Interaktion → Dialog startet
6. Intro-Text wird angezeigt + Antwort-Optionen
7. Spieler wählt Antwort → Aktionen werden ausgelöst
   (z.B. Belohnung erhalten, Beziehung geändert, Dialog fortgesetzt)
8. Dialog endet oder verzweigt sich
```

#### 7.2 Spieler wird angegriffen während "fertig"-Status
```
1. Gegner startet Kampf im Kampagnensystem
2. System erkennt: Verteidiger ist "fertig"
3. Status wird automatisch zurückgesetzt
4. Warnung: "Du wurdest angegriffen, Runde fortgesetzt"
5. Spieler kann jetzt reagieren (Verstärkungen, etc.)
```

---

### 8. GAMEMASTER-ERLEBNIS (ADMIN-OPERATIONEN)

#### 8.1 Kampagne vorbereiten
```
1. GameMaster öffnet "Kampagne einrichten"-Admin-Modul im GameMaster Menü
2. Bereich 1 - Systeme verwalten:
   - Liste aller Sternensysteme
   - Pro System: Status ändern (+hinzufügen, -entfernen, ↔ Sichtbarkeit)
3. Bereich 2 - Objekte erstellen:
   - Schiffe hinzufügen (Spezifikationen, Waffen)
   - Basen erstellen / bestehende Basen anpassen
   - Asteroiden platzieren
   - Nur in Kampagnensystemen möglich
4. Bereich 3 - Dialoge verwalten:
   - Neue Dialoge erstellen
   - Dialoge bearbeiten/löschen
   - Dialog-Optionen verwalten
   - Aktionen konfigurieren
5. Bereich 4 - Interaktionen verwalten:
   - Dialog zu Objekt verknüpfen
   - Interaktionen aktivieren/deaktivieren
   - Interaktionen testen
```

#### 8.2 Kampagne laufen lassen
```
1. Spieler spielen, interagieren mit Objekten
2. Schlachten werden eingeleitet
3. Sind alle Spieler fertig: GameMaster löst "Kampagnen-Tick" aus
4. Kampagnentick wird ausgeführt

```

---

