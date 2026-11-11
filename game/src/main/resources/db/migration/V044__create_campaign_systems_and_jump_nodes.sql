CREATE TABLE `campaign_star_system` (
    `campaign_id` int NOT NULL,
    `star_system_id` int NOT NULL,
    `added_at` bigint NOT NULL,
    PRIMARY KEY (`campaign_id`, `star_system_id`),
    KEY `campaign_star_system_system` (`star_system_id`),
    CONSTRAINT `campaign_star_system_fk_campaign` FOREIGN KEY (`campaign_id`) REFERENCES `campaign` (`id`) ON DELETE CASCADE,
    CONSTRAINT `campaign_star_system_fk_system` FOREIGN KEY (`star_system_id`) REFERENCES `systems` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

-- Temporary accesses to campaign star systems. Not bound to one campaign: they are reused, being reset
-- to PREPARED when a campaign ends.
CREATE TABLE `campaign_jump_node` (
    `jump_node_id` int NOT NULL,
    `state` varchar(16) NOT NULL,
    PRIMARY KEY (`jump_node_id`),
    CONSTRAINT `campaign_jump_node_fk_jump_node` FOREIGN KEY (`jump_node_id`) REFERENCES `jumpnodes` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
