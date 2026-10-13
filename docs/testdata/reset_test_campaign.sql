-- Resets the test campaign to its state before any player interaction.
SET NAMES utf8mb4;
START TRANSACTION;

-- Ships the dialogs brought in: NPC ships spawned later (pirates, reinforcements) and Raiders lent to the player
CREATE TEMPORARY TABLE reset_ships AS
SELECT id, fleet FROM ships
WHERE (id > 2338365 AND owner IN (-900, -901)) OR (owner = 90003 AND name LIKE 'Leih-Raider%');

-- Battles started by dialogs
CREATE TEMPORARY TABLE reset_battles AS
SELECT DISTINCT battle AS id FROM ships
WHERE battle IS NOT NULL AND (id IN (2338362, 2338363, 2338364) OR id IN (SELECT id FROM reset_ships));

DELETE FROM battles_ships WHERE battleid IN (SELECT id FROM reset_battles);
UPDATE ships SET battle = NULL, battleAction = 0 WHERE battle IN (SELECT id FROM reset_battles);
DELETE FROM battles WHERE id IN (SELECT id FROM reset_battles);

DELETE FROM offiziere WHERE stationiertAufSchiff_id IN (SELECT id FROM reset_ships);
DELETE FROM ship_flags WHERE ship IN (SELECT id FROM reset_ships);
DELETE FROM ships WHERE id IN (SELECT id FROM reset_ships);
DELETE FROM ship_history WHERE id IN (SELECT id FROM reset_ships);
DELETE FROM ship_fleets WHERE id IN (SELECT fleet FROM reset_ships) AND id NOT IN (SELECT fleet FROM ships WHERE fleet IS NOT NULL);

-- Interactions copied onto spawned ships (1-4 are the scenario's own)
DELETE FROM interaction WHERE id > 4;

-- Dialog progress
DELETE FROM dialog_session;
DELETE FROM interaction_log;
DELETE FROM campaign_variable WHERE campaign_id = 1;
DELETE FROM npc_trust_event WHERE npc_user_id IN (-900, -901);
DELETE FROM user_relations WHERE (user_id = 90003 AND target_id IN (-900, -901)) OR (user_id IN (-900, -901) AND target_id = 90003);

-- Player ships back to the jump node in 612, freighter with 50 Nahrung
UPDATE ships SET star_system = 612, x = 150, y = 150 WHERE id IN (2338363, 2338364);
UPDATE ships SET cargo = '16|50|0|0;17|80|0|0;20|40|0|0' WHERE id = 2338364;
UPDATE users SET konto = 0 WHERE id = 90003;

COMMIT;
