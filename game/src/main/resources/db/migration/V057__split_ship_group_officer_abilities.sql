-- The officer of a ship group template entry gets each ability on its own, and a special ability.
-- NULL abilities: no officer.
ALTER TABLE `ship_group_template_entry`
    ADD COLUMN `officer_nav` int DEFAULT NULL,
    ADD COLUMN `officer_ing` int DEFAULT NULL,
    ADD COLUMN `officer_waf` int DEFAULT NULL,
    ADD COLUMN `officer_sec` int DEFAULT NULL,
    ADD COLUMN `officer_com` int DEFAULT NULL,
    -- name of the special ability, ZUFALL for a random one, empty for none
    ADD COLUMN `officer_special` varchar(32) NOT NULL DEFAULT '';

UPDATE `ship_group_template_entry`
SET `officer_nav` = `officer_skill`,
    `officer_ing` = `officer_skill`,
    `officer_waf` = `officer_skill`,
    `officer_sec` = `officer_skill`,
    `officer_com` = `officer_skill`
WHERE `officer_skill` IS NOT NULL;

ALTER TABLE `ship_group_template_entry` DROP COLUMN `officer_skill`;
