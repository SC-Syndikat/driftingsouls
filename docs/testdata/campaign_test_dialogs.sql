-- Test dialogs for the "Testkampagne Maelstrom" on the dev database (prod backup of 2026-10-12).
-- Requires campaign 1, ship group template 1 and the ships 2338362-2338364 created beforehand.
SET NAMES utf8mb4;
START TRANSACTION;

-- Ships created while save() skipped the history
INSERT INTO ship_history (id, history)
SELECT s.id, 'Indienststellung (Testkampagne)' FROM ships s LEFT JOIN ship_history h ON h.id = s.id
WHERE h.id IS NULL AND s.id IN (2338362, 2338363, 2338364);

INSERT INTO dialog_node (id, title, speaker_name, image, text) VALUES
(1, 'Vex: Begrüßung', 'Admiral Vex', '',
 'Halt! Ihr befindet euch im Sperrgebiet des Maelstrom. Nennt euer Anliegen, bevor meine Geschütze es für euch tun.'),
(2, 'Vex: Freundlich', 'Admiral Vex', '',
 'Friedlich, so so. Unsere Vorräte gehen zur Neige. Bringt uns 10 Einheiten Nahrung, und ich zahle gut dafür.'),
(3, 'Vex: Erpresst', 'Admiral Vex', '',
 'Na schön, na schön! Nehmt die Credits und verschwindet. Das werde ich mir merken.'),
(4, 'Vex: Notsignal', 'Admiral Vex', '',
 'Das Notsignal? Es stammt von einem unserer Kuriere. Er trug eine Datendisk bei sich. Habt ihr sie gefunden?'),
(5, 'Vex: Verbündet', 'Admiral Vex', '',
 'Ihr habt Wort gehalten. Ab heute seid ihr Freunde der Maelstrom-Flotte. Meine Schiffe werden euch nicht behelligen.'),
(6, 'Notsignal', 'Bordcomputer', '',
 'Die Sensoren empfangen ein schwaches Notsignal aus dem Inneren des Maelstrom. Die Quelle scheint ein treibendes Wrack zu sein.'),
(7, 'Notsignal: Fund', 'Bordcomputer', '',
 'Ihr dockt am Wrack an. Die Besatzung ist längst fort, doch im Cockpit findet ihr eine intakte Datendisk.'),
(8, 'Notsignal: Falle', 'Bordcomputer', '',
 'Das Signal war ein Köder! Mehrere Piratenschiffe tauchen aus dem Nebel auf und eröffnen das Feuer.'),
(9, 'Vex: Angriff', 'Admiral Vex', '',
 'Ihr wagt es, mir zu drohen? Alle Mann auf Gefechtsstation - Feuer frei!'),
(10, 'Vex: Auftrag', 'Admiral Vex', '',
 'Ihr scheint vertrauenswürdig. Einer meiner Kuriere ist im Maelstrom verschollen. Sucht nach seinem Notsignal und bringt mir, was er bei sich trug.');

INSERT INTO dialog_option (id, node_id, sort, text, next_node_id, failure_node_id, base_chance, hide_if_unavailable) VALUES
-- Begrüßung
(1, 1, 1, 'Wir kommen in friedlicher Absicht.', 2, NULL, NULL, 1),
(2, 1, 2, 'Gebt uns eure Vorräte, oder wir nehmen sie uns!', 3, 9, 35, 1),
(3, 1, 3, 'Habt ihr Arbeit für uns?', 10, NULL, NULL, 1),
(4, 1, 4, 'Was wisst ihr über das Notsignal?', 4, NULL, NULL, 1),
(5, 1, 5, 'Wir ziehen weiter.', NULL, NULL, NULL, 1),
-- Freundlich
(6, 2, 1, 'Hier sind 10 Einheiten Nahrung.', 5, NULL, NULL, 0),
(7, 2, 2, 'Wir kommen später wieder.', NULL, NULL, NULL, 1),
-- Erpresst
(8, 3, 1, 'Danke für die Spende.', NULL, NULL, NULL, 1),
-- Notsignal-Wissen
(9, 4, 1, 'Ja, hier ist die Datendisk.', 5, NULL, NULL, 0),
(10, 4, 2, 'Die behalten wir.', NULL, NULL, NULL, 1),
-- Verbündet
(11, 5, 1, 'Auf gute Zusammenarbeit.', NULL, NULL, NULL, 1),
-- System: Notsignal
(12, 6, 1, 'Signal untersuchen', 7, 8, 60, 1),
(13, 6, 2, 'Lieber nicht.', NULL, NULL, NULL, 1),
(14, 7, 1, 'Datendisk einstecken und abfliegen.', NULL, NULL, NULL, 1),
(15, 8, 1, 'Gefechtsbereitschaft herstellen!', NULL, NULL, NULL, 1),
-- Angriff
(16, 9, 1, 'Schilde hoch!', NULL, NULL, NULL, 1),
-- Auftrag
(17, 10, 1, 'Wir kümmern uns darum.', NULL, NULL, NULL, 1);

-- Answers that raise trust or pay out may be chosen only once per player (needs V049)
UPDATE dialog_option SET once_per_user = 1 WHERE id IN (1, 2, 6, 9, 10);

INSERT INTO dialog_option_condition (option_id, type, params, comparator, threshold) VALUES
(3, 'TRUST', '{}', '>=', 10),
(4, 'VARIABLE', '{"name":"signal_gefunden","scope":"USER"}', '>=', 1),
(6, 'CARGO', '{"resource":"16"}', '>=', 10),
(9, 'CARGO', '{"resource":"8"}', '>=', 1);

INSERT INTO dialog_option_modifier (option_id, type, params, factor, min_value, max_value) VALUES
(2, 'OFFICER_SKILL', '{"ability":"COM"}', 0.25, NULL, NULL),
(2, 'FLEET_STRENGTH', '{}', 0.5, NULL, 40),
(12, 'OFFICER_SKILL', '{"ability":"NAV"}', 0.3, NULL, NULL);

INSERT INTO dialog_option_action (option_id, sort, outcome, type, params) VALUES
(1, 1, 'ALWAYS', 'CHANGE_TRUST', '{"delta":"5","reason":"Friedlicher Erstkontakt"}'),
(2, 1, 'SUCCESS', 'GIVE_CREDITS', '{"amount":"2000","source":"NONE"}'),
(2, 2, 'ALWAYS', 'CHANGE_TRUST', '{"delta":"-15","reason":"Erpressungsversuch"}'),
(2, 3, 'FAILURE', 'START_BATTLE', '{"source":"OBJECT"}'),
(6, 1, 'ALWAYS', 'TAKE_RESOURCE', '{"resource":"16","count":"10"}'),
(6, 2, 'ALWAYS', 'GIVE_CREDITS', '{"amount":"3000","source":"NONE"}'),
(6, 3, 'ALWAYS', 'CHANGE_TRUST', '{"delta":"10","reason":"Nahrung geliefert"}'),
(6, 4, 'ALWAYS', 'SET_RELATION', '{"direction":"BOTH","relation":"FRIEND"}'),
(9, 1, 'ALWAYS', 'TAKE_RESOURCE', '{"resource":"8","count":"1"}'),
(9, 2, 'ALWAYS', 'GIVE_CREDITS', '{"amount":"5000","source":"NONE"}'),
(9, 3, 'ALWAYS', 'CHANGE_TRUST', '{"delta":"20","reason":"Datendisk zurückgebracht"}'),
(9, 4, 'ALWAYS', 'SET_RELATION', '{"direction":"BOTH","relation":"FRIEND"}'),
(10, 1, 'ALWAYS', 'CHANGE_TRUST', '{"delta":"-5","reason":"Datendisk behalten"}'),
(12, 1, 'SUCCESS', 'GIVE_RESOURCE', '{"resource":"8","count":"1"}'),
(12, 2, 'SUCCESS', 'SET_VARIABLE', '{"name":"signal_gefunden","scope":"USER","value":"1"}'),
(12, 3, 'FAILURE', 'START_BATTLE', '{"source":"TEMPLATE","template":"1"}'),
(17, 1, 'ALWAYS', 'SET_VARIABLE', '{"name":"auftrag_angenommen","scope":"USER","value":"1"}');

INSERT INTO interaction (id, label, object_type, object_id, start_node_id, npc_user_id, active, max_uses_per_user, cooldown_ticks, audience) VALUES
(1, 'Mit Admiral Vex sprechen', 'SHIP', 2338362, 1, -900, 1, NULL, NULL, 'ALL'),
(2, 'Notsignal untersuchen', 'STAR_SYSTEM', 621, 6, -900, 1, 1, NULL, 'ALL');

COMMIT;
