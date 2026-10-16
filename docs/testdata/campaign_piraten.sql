-- Pirates at the wreck of the "Testkampagne Maelstrom" (needs V050-V053, campaign_test_dialogs.sql and campaign_notruf.sql).
-- Searching the wreck may attract pirate captain Skarn: hand over the disk, talk him out of it or fight. If the
-- disk is lost, Vex lends the player a Raider; Skarn gives the disk back when facing three times his strength.
SET NAMES utf8mb4;
START TRANSACTION;

-- ---------------------------------------------------------------- the pirate NPC (-901), a copy of -900
CREATE TEMPORARY TABLE pirate_user AS SELECT * FROM users WHERE id = -900;
UPDATE pirate_user SET id = -901, un = '-901', name = 'Piraten vom Roten Nebel', nickname = 'Piraten vom Roten Nebel',
	plainname = 'Piraten vom Roten Nebel', email = 'pirates@localhost', konto = 0;
INSERT INTO users SELECT * FROM pirate_user;
INSERT INTO user_relations (status, version, target_id, user_id) VALUES (2, 0, -901, -901);
INSERT INTO ordner (flags, name, parent, version, playerid) VALUES (1, 'Papierkorb', 0, 0, -901);
INSERT INTO campaign_participant (campaign_id, user_id, added_by, added_at) VALUES (1, -901, 90002, UNIX_TIMESTAMP());

-- ---------------------------------------------------------------- ship group templates
INSERT INTO ship_group_template (id, name, description, create_fleet) VALUES
(2, 'Piratenschiff Rotes Auge', 'Kapitän Skarn, erscheint beim Wrack', 0),
(3, 'Leih-Raider', 'Raider, den Vex dem Spieler stellt', 0);
INSERT INTO ship_group_template_entry (template_id, ship_type_id, count, name_pattern, cargo, units,
    officer_nav, officer_ing, officer_waf, officer_sec, officer_com, officer_special, alarm) VALUES
(2, 20, 1, 'Rotes Auge', '', '', 40, 40, 40, 40, 40, '', 0),
(3, 20, 1, 'Leih-Raider der Maelstrom-Flotte', '', '', NULL, NULL, NULL, NULL, NULL, '', 0);

-- ---------------------------------------------------------------- nodes
INSERT INTO dialog_node (id, title, speaker_name, image, text) VALUES
(14, 'Wrack: Piraten', 'Kapitän Skarn', '',
'Kaum habt ihr die Datendisk geborgen, schießt ein Piratenschiff aus dem Nebel. Eure Funkfrequenzen werden überlagert:

[i]»Hier spricht Kapitän Skarn. Ihr habt da etwas, das mir gehört. Übergebt die Datendisk – oder wir pusten euch aus dem All!«[/i]'),
(15, 'Skarn: zufrieden', 'Kapitän Skarn', '',
'[i]»Kluge Entscheidung. Und jetzt verschwindet, bevor ich es mir anders überlege.«[/i]'),
(16, 'Skarn: zieht ab', 'Kapitän Skarn', '',
'Skarn zögert. [i]»… Na schön. Die Disk ist den Ärger mit Vex nicht wert.«[/i] Das Piratenschiff dreht ab und verschwindet im Nebel.'),
(17, 'Skarn: greift an', 'Kapitän Skarn', '',
'[i]»Dann holen wir sie uns eben!«[/i] Das Piratenschiff eröffnet das Feuer.'),
(18, 'Skarn: Funkkontakt', 'Kapitän Skarn', '',
'[i]»Ihr schon wieder? Die Datendisk gehört jetzt uns.«[/i]'),
(19, 'Skarn: gibt nach', 'Kapitän Skarn', '',
'Skarn mustert eure Flotte und wird blass. [i]»Schon gut, schon gut! Nehmt das verdammte Ding und lasst uns in Ruhe!«[/i]'),
(20, 'Skarn: lacht', 'Kapitän Skarn', '',
'[i]»Mit diesem Aufgebot? Kommt wieder, wenn ihr mir wirklich Angst machen könnt!«[/i]'),
(21, 'Vex: Disk verloren', 'Admiral Vex', '',
'Vex'' Miene verfinstert sich. »Die Disk enthält die Zugangskodes unserer Sprungpunkt-Sicherungen. In Skarns Händen ist sie eine Katastrophe! Holt sie zurück – um jeden Preis.«

Er gibt einem Offizier ein Zeichen. »Nehmt diesen Raider aus meiner Flotte. Seine Disruptoren legen Skarns Schiff lahm, dann könnt ihr es entern oder plündern. Oder ihr kommt mit genug Feuerkraft – dann gibt Skarn vielleicht freiwillig auf.«'),
(22, 'Vex: Erinnerung', 'Admiral Vex', '',
'»Die Disk ist noch immer bei Skarn? Beeilt euch, bevor er sie verkauft!«');

-- ---------------------------------------------------------------- the wreck: careful search or pirates
UPDATE interaction SET npc_user_id = -901 WHERE id = 3;
-- Skarn's demand must be answered: no leaving the dialog, all ships in the sector stay
UPDATE dialog_node SET forced = 1 WHERE id = 14;
UPDATE dialog_option SET base_chance = 50, failure_node_id = 14 WHERE id = 22;
INSERT INTO dialog_option_modifier (option_id, type, params, factor, min_value, max_value) VALUES
(22, 'OFFICER_SKILL', '{"ability":"SEC"}', 0.1, NULL, 200);

-- Template interaction, copied onto the pirate ship when it appears; inactive itself
INSERT INTO interaction (id, label, object_type, object_id, start_node_id, npc_user_id, active, max_uses_per_user, cooldown_ticks, audience) VALUES
(4, 'Kapitän Skarn anfunken', 'SHIP', 0, 18, -901, 0, NULL, NULL, 'ALL');

INSERT INTO dialog_option_action (option_id, sort, outcome, type, params) VALUES
(22, 3, 'FAILURE', 'SPAWN_SHIP_GROUP', '{"template":"2","interaction":"4"}');

INSERT INTO dialog_option (id, node_id, sort, text, next_node_id, failure_node_id, base_chance, hide_if_unavailable, once_per_user) VALUES
(25, 14, 1, 'Die Datendisk übergeben.', 15, NULL, NULL, 1, 0),
(26, 14, 2, '»Legt Euch lieber nicht mit der Maelstrom-Flotte an, Skarn.«', 16, 17, 25, 1, 0),
(27, 14, 3, '»Niemals. Feuer frei!«', 17, NULL, NULL, 1, 0),
(28, 15, 1, 'Abdrehen.', NULL, NULL, NULL, 1, 0),
(29, 16, 1, 'Weiter.', NULL, NULL, NULL, 1, 0),
(30, 17, 1, 'Gefechtsstationen!', NULL, NULL, NULL, 1, 0),
-- the pirate ship
(31, 18, 1, '»Gebt die Datendisk heraus!«', 19, NULL, NULL, 1, 0),
(32, 18, 2, '»Gebt die Datendisk heraus!«', 20, NULL, NULL, 1, 0),
(33, 18, 3, 'Nichts.', NULL, NULL, NULL, 1, 0),
(34, 19, 1, 'Klug von Euch.', NULL, NULL, NULL, 1, 0),
(35, 20, 1, '»Dann holen wir sie uns!« (angreifen)', NULL, NULL, NULL, 1, 0),
(36, 20, 2, 'Rückzug.', NULL, NULL, NULL, 1, 0),
-- Vex
(37, 1, 6, 'Die Datendisk wurde uns von Piraten abgenommen.', 21, NULL, NULL, 1, 0),
(38, 1, 7, 'Die Datendisk wurde uns von Piraten abgenommen.', 22, NULL, NULL, 1, 0),
(39, 21, 1, 'Wir holen sie zurück.', NULL, NULL, NULL, 1, 0),
(40, 22, 1, 'Wir sind dran.', NULL, NULL, NULL, 1, 0);

INSERT INTO dialog_option_condition (option_id, type, params, comparator, threshold) VALUES
(31, 'VARIABLE', '{"name":"disk_verloren","scope":"USER"}', '>=', 1),
(31, 'STRENGTH_RATIO', '{}', '>=', 3),
(32, 'VARIABLE', '{"name":"disk_verloren","scope":"USER"}', '>=', 1),
(32, 'STRENGTH_RATIO', '{}', '<', 3),
(37, 'VARIABLE', '{"name":"disk_verloren","scope":"USER"}', '>=', 1),
(37, 'VARIABLE', '{"name":"raider_gestellt","scope":"USER"}', '<', 1),
(38, 'VARIABLE', '{"name":"disk_verloren","scope":"USER"}', '>=', 1),
(38, 'VARIABLE', '{"name":"raider_gestellt","scope":"USER"}', '>=', 1);

INSERT INTO dialog_option_modifier (option_id, type, params, factor, min_value, max_value) VALUES
(26, 'OFFICER_SKILL', '{"ability":"COM"}', 0.1, NULL, 200),
(26, 'STRENGTH_RATIO', '{}', 10, NULL, 3);

INSERT INTO dialog_option_action (option_id, sort, outcome, type, params) VALUES
(25, 1, 'ALWAYS', 'TAKE_RESOURCE', '{"resource":"8","count":"1","to":"NPC_SHIP"}'),
(25, 2, 'ALWAYS', 'SET_VARIABLE', '{"name":"disk_verloren","scope":"USER","value":"1"}'),
(26, 1, 'SUCCESS', 'REMOVE_NPC_SHIPS', '{}'),
(26, 2, 'FAILURE', 'START_BATTLE', '{"source":"SECTOR"}'),
(27, 1, 'ALWAYS', 'START_BATTLE', '{"source":"SECTOR"}'),
(31, 1, 'ALWAYS', 'GIVE_RESOURCE', '{"resource":"8","count":"1","from":"OBJECT"}'),
(31, 2, 'ALWAYS', 'SET_VARIABLE', '{"name":"disk_verloren","scope":"USER","value":"0"}'),
(35, 1, 'ALWAYS', 'START_BATTLE', '{"source":"OBJECT"}'),
(37, 1, 'ALWAYS', 'GIVE_SHIP', '{"source":"TEMPLATE","template":"3"}'),
(37, 2, 'ALWAYS', 'SET_VARIABLE', '{"name":"raider_gestellt","scope":"USER","value":"1"}');

COMMIT;
