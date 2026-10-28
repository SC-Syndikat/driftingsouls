-- The distorted distress call of the "Testkampagne Maelstrom" (needs V050-V052 and campaign_test_dialogs.sql).
-- Once per campaign tick the player may try to decode it; success reveals the whole message, partial success one
-- more fragment, failure nothing. The message leads to the wreck (ship 2338365, 621:35/42) with the data disk.
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE ships SET name = 'Trümmerteil' WHERE id = 2338365;

-- The old signal: no direct disk and no ambush any more
DELETE FROM dialog_option_action WHERE option_id IN (12, 14, 15);
DELETE FROM dialog_option_modifier WHERE option_id = 12;
DELETE FROM dialog_option_condition WHERE option_id = 12;

UPDATE dialog_node SET title = 'Notruf', speaker_name = 'Bordcomputer', text =
'Die Sensoren fangen einen stark verzerrten Notruf aus dem Inneren des Maelstrom auf.

[wenn notruf_teile == 0][i]»...krrz... hier K##ier ... ~~~ ... #####... Maels... krrz ... ##~~#«[/i][/wenn][wenn notruf_teile == 1][i]»Hier Kurier D#ro ... Maelstrom-Fl#tte. Triebw~rke ausg#fallen ... treibe ... ##### ... 6#1:??/?? ... krrz«[/i][/wenn][wenn notruf_teile == 2][i]»Hier Kurier Daro von der Maelstrom-Flotte. Triebwerke ausgefallen, treibe manövrierunfähig. Mein Wrack liegt bei 621:3?/4?. Die D#tend#sk ... ~~~ ... krrz«[/i][/wenn][wenn notruf_teile >= 3][i]»Hier Kurier Daro von der Maelstrom-Flotte. Triebwerke ausgefallen, treibe manövrierunfähig. Mein Wrack liegt bei [b]621:35/42[/b]. Die Datendisk für Admiral Vex ist an Bord. Bitte helft!«[/i][/wenn]'
WHERE id = 6;

UPDATE dialog_node SET title = 'Notruf: entschlüsselt', speaker_name = 'Bordcomputer', text =
'Die Entschlüsselung gelingt vollständig:

[i]»Hier Kurier Daro von der Maelstrom-Flotte. Triebwerke ausgefallen, treibe manövrierunfähig. Mein Wrack liegt bei [b]621:35/42[/b]. Die Datendisk für Admiral Vex ist an Bord. Bitte helft!«[/i]'
WHERE id = 7;

UPDATE dialog_node SET title = 'Notruf: nur Rauschen', speaker_name = 'Bordcomputer', text =
'Der Entschlüsselungsversuch scheitert, das Signal geht im Rauschen unter. Neue Erkenntnisse gibt es keine.

Beim nächsten Kampagnentick kannst du es erneut versuchen.'
WHERE id = 8;

INSERT INTO dialog_node (id, title, speaker_name, image, text) VALUES
(11, 'Notruf: teilweise entschlüsselt', 'Bordcomputer', '',
'Ein Teil der Nachricht lässt sich wiederherstellen:

[wenn notruf_teile == 1][i]»Hier Kurier D#ro ... Maelstrom-Fl#tte. Triebw~rke ausg#fallen ... treibe ... ##### ... 6#1:??/?? ... krrz«[/i][/wenn][wenn notruf_teile == 2][i]»Hier Kurier Daro von der Maelstrom-Flotte. Triebwerke ausgefallen, treibe manövrierunfähig. Mein Wrack liegt bei 621:3?/4?. Die D#tend#sk ... ~~~ ... krrz«[/i][/wenn][wenn notruf_teile >= 3][i]»Hier Kurier Daro von der Maelstrom-Flotte. Triebwerke ausgefallen, treibe manövrierunfähig. Mein Wrack liegt bei [b]621:35/42[/b]. Die Datendisk für Admiral Vex ist an Bord. Bitte helft!«[/i][/wenn]

Beim nächsten Kampagnentick kannst du weiter entschlüsseln.'),
(12, 'Trümmerteil', 'Bordcomputer', '',
'Ein ausgebranntes Wrack treibt langsam um die eigene Achse. Die Kennung ist noch lesbar: Kurierschiff der Maelstrom-Flotte. Von der Besatzung fehlt jede Spur.'),
(13, 'Trümmerteil: Fund', 'Bordcomputer', '',
'Im zerstörten Cockpit findet ihr zwischen Trümmern eine intakte [b]Datendisk[/b]. Admiral Vex wird sie sicher zurückhaben wollen.');

-- Decode: 20 % success plus modifiers, then 40 percentage points partial success, the rest failure
UPDATE dialog_option SET text = 'Notruf entschlüsseln', next_node_id = 7, failure_node_id = 8, base_chance = 20,
	partial_chance = 40, partial_node_id = 11, hide_if_unavailable = 1, once_per_user = 0
WHERE id = 12;
UPDATE dialog_option SET text = 'Später.' WHERE id = 13;
UPDATE dialog_option SET text = 'Kurs auf das Wrack setzen.' WHERE id = 14;
UPDATE dialog_option SET text = 'Verstanden.' WHERE id = 15;

INSERT INTO dialog_option_condition (option_id, type, params, comparator, threshold) VALUES
(12, 'VARIABLE', '{"name":"notruf_teile","scope":"USER"}', '<', 3);

INSERT INTO dialog_option_modifier (option_id, type, params, factor, min_value, max_value) VALUES
(12, 'SHIP_VALUE', '{"property":"SCAN_RANGE"}', 3, NULL, 6),
(12, 'SHIP_FLAG', '{"flag":"SRS_AWAC"}', 10, NULL, NULL),
(12, 'SHIP_FLAG', '{"flag":"SRS_EXT_AWAC"}', 10, NULL, NULL),
(12, 'OFFICER_SKILL', '{"ability":"ING"}', 0.1, NULL, 200),
(12, 'RANDOM', '{"from":"-10","to":"10"}', 1, NULL, NULL);

INSERT INTO dialog_option_action (option_id, sort, outcome, type, params) VALUES
(12, 1, 'SUCCESS', 'SET_VARIABLE', '{"name":"notruf_teile","scope":"USER","value":"3"}'),
(12, 2, 'PARTIAL', 'ADD_VARIABLE', '{"name":"notruf_teile","scope":"USER","delta":"1"}');

INSERT INTO dialog_option (id, node_id, sort, text, next_node_id, failure_node_id, base_chance, hide_if_unavailable, once_per_user) VALUES
(21, 11, 1, 'Verstanden.', NULL, NULL, NULL, 1, 0),
(22, 12, 1, 'Wrack durchsuchen', 13, NULL, NULL, 1, 1),
(23, 12, 2, 'Weiterfliegen.', NULL, NULL, NULL, 1, 0),
(24, 13, 1, 'Datendisk einstecken.', NULL, NULL, NULL, 1, 0);

INSERT INTO dialog_option_action (option_id, sort, outcome, type, params) VALUES
(22, 1, 'ALWAYS', 'GIVE_RESOURCE', '{"resource":"8","count":"1"}'),
(22, 2, 'ALWAYS', 'SET_VARIABLE', '{"name":"signal_gefunden","scope":"USER","value":"1"}');

-- Once per campaign tick, as often as needed
UPDATE interaction SET label = 'Notruf empfangen', max_uses_per_user = NULL, cooldown_ticks = 1 WHERE id = 2;
INSERT INTO interaction (id, label, object_type, object_id, start_node_id, npc_user_id, active, max_uses_per_user, cooldown_ticks, audience) VALUES
(3, 'Trümmerteil untersuchen', 'SHIP', 2338365, 12, -900, 1, NULL, NULL, 'ALL');

COMMIT;
