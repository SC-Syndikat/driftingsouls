-- An interaction belongs to one campaign: players are offered it only while that campaign runs, and copying
-- the campaign copies it. NULL: belongs to no campaign and is never offered.
-- origin_id: for a copy, the interaction it was first copied from. Dialogs are shared by all campaigns and
-- refer to interactions by id; such a reference is resolved to the running campaign's copy.
ALTER TABLE `interaction`
    ADD COLUMN `campaign_id` int DEFAULT NULL AFTER `id`,
    ADD COLUMN `origin_id` int DEFAULT NULL AFTER `campaign_id`,
    ADD KEY `interaction_campaign` (`campaign_id`),
    ADD KEY `interaction_origin` (`origin_id`),
    ADD CONSTRAINT `interaction_fk_campaign` FOREIGN KEY (`campaign_id`) REFERENCES `campaign` (`id`) ON DELETE CASCADE;

-- Interactions so far were made for the most recent campaign
UPDATE `interaction` SET `campaign_id` = (SELECT MAX(`id`) FROM `campaign`);
