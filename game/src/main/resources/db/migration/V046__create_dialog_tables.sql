-- Reusable dialogs: not bound to a campaign.
CREATE TABLE `dialog_node` (
    `id` int NOT NULL AUTO_INCREMENT,
    `title` varchar(255) NOT NULL,
    `speaker_name` varchar(255) NOT NULL,
    `image` varchar(255) NOT NULL,
    `text` text NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

CREATE TABLE `dialog_option` (
    `id` int NOT NULL AUTO_INCREMENT,
    `node_id` int NOT NULL,
    `sort` int NOT NULL DEFAULT 0,
    `text` text NOT NULL,
    -- NULL ends the dialog
    `next_node_id` int DEFAULT NULL,
    -- node on failure; NULL ends the dialog
    `failure_node_id` int DEFAULT NULL,
    -- NULL: always succeeds; otherwise the base chance in percent
    `base_chance` int DEFAULT NULL,
    `hide_if_unavailable` tinyint(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    KEY `dialog_option_node` (`node_id`),
    CONSTRAINT `dialog_option_fk_node` FOREIGN KEY (`node_id`) REFERENCES `dialog_node` (`id`) ON DELETE CASCADE,
    CONSTRAINT `dialog_option_fk_next` FOREIGN KEY (`next_node_id`) REFERENCES `dialog_node` (`id`) ON DELETE SET NULL,
    CONSTRAINT `dialog_option_fk_failure` FOREIGN KEY (`failure_node_id`) REFERENCES `dialog_node` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

-- The option is available only if every condition holds: factor(params) <comparator> threshold
CREATE TABLE `dialog_option_condition` (
    `id` int NOT NULL AUTO_INCREMENT,
    `option_id` int NOT NULL,
    `type` varchar(64) NOT NULL,
    `params` text NOT NULL,
    `comparator` varchar(2) NOT NULL,
    `threshold` decimal(16,3) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `dialog_option_condition_fk_option` FOREIGN KEY (`option_id`) REFERENCES `dialog_option` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

-- chance = base_chance + sum(factor * clamp(value, min_value, max_value))
CREATE TABLE `dialog_option_modifier` (
    `id` int NOT NULL AUTO_INCREMENT,
    `option_id` int NOT NULL,
    `type` varchar(64) NOT NULL,
    `params` text NOT NULL,
    `factor` decimal(16,3) NOT NULL,
    `min_value` decimal(16,3) DEFAULT NULL,
    `max_value` decimal(16,3) DEFAULT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `dialog_option_modifier_fk_option` FOREIGN KEY (`option_id`) REFERENCES `dialog_option` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

CREATE TABLE `dialog_option_action` (
    `id` int NOT NULL AUTO_INCREMENT,
    `option_id` int NOT NULL,
    `sort` int NOT NULL DEFAULT 0,
    `outcome` varchar(16) NOT NULL,
    `type` varchar(64) NOT NULL,
    `params` text NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `dialog_option_action_fk_option` FOREIGN KEY (`option_id`) REFERENCES `dialog_option` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

-- Quest progress. user_id 0 holds variables global to the campaign.
CREATE TABLE `campaign_variable` (
    `campaign_id` int NOT NULL,
    `user_id` int NOT NULL,
    `name` varchar(64) NOT NULL,
    `value` bigint NOT NULL,
    PRIMARY KEY (`campaign_id`, `user_id`, `name`),
    CONSTRAINT `campaign_variable_fk_campaign` FOREIGN KEY (`campaign_id`) REFERENCES `campaign` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

-- Trust of a player towards an NPC, kept as a journal across campaigns.
CREATE TABLE `npc_trust_event` (
    `id` int NOT NULL AUTO_INCREMENT,
    `campaign_id` int DEFAULT NULL,
    `npc_user_id` int NOT NULL,
    `user_id` int NOT NULL,
    `delta` int NOT NULL,
    `reason` varchar(1000) NOT NULL,
    -- ACTION counts for the player's alliance too; GM_CORRECTION only for the player
    `kind` varchar(16) NOT NULL,
    `interaction_id` int DEFAULT NULL,
    `created_by` int DEFAULT NULL,
    `created_at` bigint NOT NULL,
    PRIMARY KEY (`id`),
    KEY `npc_trust_event_npc_user` (`npc_user_id`, `user_id`),
    CONSTRAINT `npc_trust_event_fk_campaign` FOREIGN KEY (`campaign_id`) REFERENCES `campaign` (`id`) ON DELETE SET NULL,
    CONSTRAINT `npc_trust_event_fk_npc` FOREIGN KEY (`npc_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `npc_trust_event_fk_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `npc_trust_event_fk_created_by` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

CREATE TABLE `npc_trust_setting` (
    `npc_user_id` int NOT NULL,
    -- share of the alliance members' trust events counted for a player
    `alliance_factor` decimal(6,3) NOT NULL,
    PRIMARY KEY (`npc_user_id`),
    CONSTRAINT `npc_trust_setting_fk_npc` FOREIGN KEY (`npc_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
