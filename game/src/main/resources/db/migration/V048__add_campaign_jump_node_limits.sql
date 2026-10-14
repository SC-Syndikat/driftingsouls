-- Limits on who and what may pass a campaign jump node. NULL / empty means no limit.
ALTER TABLE `campaign_jump_node`
    -- ship class names, comma separated
    ADD COLUMN `allowed_ship_classes` varchar(1000) NOT NULL DEFAULT '',
    -- ship type ids, comma separated
    ADD COLUMN `allowed_ship_types` varchar(1000) NOT NULL DEFAULT '',
    ADD COLUMN `max_points_per_ship` int DEFAULT NULL,
    ADD COLUMN `max_points_per_player` int DEFAULT NULL,
    -- TOTAL: for the whole campaign; PER_TICK: per campaign tick
    ADD COLUMN `player_limit_period` varchar(16) NOT NULL DEFAULT 'TOTAL',
    -- SIZE: ship size; BATTLE_VALUE: battle value of military ships
    ADD COLUMN `points_metric` varchar(16) NOT NULL DEFAULT 'SIZE';

-- Every passage of a campaign jump node, the basis of the per-player limit.
CREATE TABLE `campaign_jump_node_passage` (
    `id` int NOT NULL AUTO_INCREMENT,
    `jump_node_id` int NOT NULL,
    `campaign_id` int NOT NULL,
    `campaign_tick` int NOT NULL,
    `user_id` int NOT NULL,
    `ship_count` int NOT NULL,
    `points` int NOT NULL,
    `created_at` bigint NOT NULL,
    PRIMARY KEY (`id`),
    KEY `campaign_jump_node_passage_use` (`jump_node_id`, `campaign_id`, `user_id`),
    CONSTRAINT `campaign_jump_node_passage_fk_node` FOREIGN KEY (`jump_node_id`) REFERENCES `jumpnodes` (`id`) ON DELETE CASCADE,
    CONSTRAINT `campaign_jump_node_passage_fk_campaign` FOREIGN KEY (`campaign_id`) REFERENCES `campaign` (`id`) ON DELETE CASCADE,
    CONSTRAINT `campaign_jump_node_passage_fk_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
