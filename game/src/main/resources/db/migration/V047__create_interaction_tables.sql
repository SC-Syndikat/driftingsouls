-- An object (ship, base or star system) offering a dialog to players.
CREATE TABLE `interaction` (
    `id` int NOT NULL AUTO_INCREMENT,
    `label` varchar(255) NOT NULL,
    `object_type` varchar(16) NOT NULL,
    `object_id` int NOT NULL,
    `start_node_id` int NOT NULL,
    -- the NPC speaking; its relations, rank and trust are what the dialog reads and changes
    `npc_user_id` int NOT NULL,
    `active` tinyint(1) NOT NULL DEFAULT 1,
    -- NULL: unlimited
    `max_uses_per_user` int DEFAULT NULL,
    -- NULL: none; otherwise campaign ticks between two uses by the same player
    `cooldown_ticks` int DEFAULT NULL,
    -- ALL or RESTRICTED (then interaction_audience lists who may use it)
    `audience` varchar(16) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `interaction_object` (`object_type`, `object_id`),
    CONSTRAINT `interaction_fk_start_node` FOREIGN KEY (`start_node_id`) REFERENCES `dialog_node` (`id`),
    CONSTRAINT `interaction_fk_npc` FOREIGN KEY (`npc_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

CREATE TABLE `interaction_audience` (
    `id` int NOT NULL AUTO_INCREMENT,
    `interaction_id` int NOT NULL,
    `user_id` int DEFAULT NULL,
    `alliance_id` int DEFAULT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `interaction_audience_fk_interaction` FOREIGN KEY (`interaction_id`) REFERENCES `interaction` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

-- A dialog in progress. Choices are only accepted for options of current_node_id.
CREATE TABLE `dialog_session` (
    `id` int NOT NULL AUTO_INCREMENT,
    `user_id` int NOT NULL,
    `ship_id` int NOT NULL,
    `interaction_id` int NOT NULL,
    `current_node_id` int NOT NULL,
    `started_at` bigint NOT NULL,
    PRIMARY KEY (`id`),
    KEY `dialog_session_user` (`user_id`),
    CONSTRAINT `dialog_session_fk_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `dialog_session_fk_interaction` FOREIGN KEY (`interaction_id`) REFERENCES `interaction` (`id`) ON DELETE CASCADE,
    CONSTRAINT `dialog_session_fk_node` FOREIGN KEY (`current_node_id`) REFERENCES `dialog_node` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

-- Every choice made, for the game master and for use limits and cooldowns.
CREATE TABLE `interaction_log` (
    `id` int NOT NULL AUTO_INCREMENT,
    `campaign_id` int DEFAULT NULL,
    `campaign_tick` int NOT NULL,
    `user_id` int NOT NULL,
    `ship_id` int NOT NULL,
    `interaction_id` int NOT NULL,
    `node_id` int NOT NULL,
    `option_id` int DEFAULT NULL,
    -- NULL when the option had no chance roll
    `chance` int DEFAULT NULL,
    `success` tinyint(1) NOT NULL,
    `actions_summary` text NOT NULL,
    `created_at` bigint NOT NULL,
    PRIMARY KEY (`id`),
    KEY `interaction_log_use` (`interaction_id`, `user_id`),
    CONSTRAINT `interaction_log_fk_campaign` FOREIGN KEY (`campaign_id`) REFERENCES `campaign` (`id`) ON DELETE SET NULL,
    CONSTRAINT `interaction_log_fk_interaction` FOREIGN KEY (`interaction_id`) REFERENCES `interaction` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
