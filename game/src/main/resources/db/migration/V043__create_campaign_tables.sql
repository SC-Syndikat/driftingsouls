CREATE TABLE `campaign` (
    `id` int NOT NULL AUTO_INCREMENT,
    `name` varchar(255) NOT NULL,
    `gamemaster_id` int DEFAULT NULL,
    `status` varchar(16) NOT NULL,
    `created_at` bigint NOT NULL,
    `started_at` bigint DEFAULT NULL,
    `ended_at` bigint DEFAULT NULL,
    `tick_count` int NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `campaign_status` (`status`),
    CONSTRAINT `campaign_fk_gamemaster` FOREIGN KEY (`gamemaster_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

CREATE TABLE `campaign_participant` (
    `campaign_id` int NOT NULL,
    `user_id` int NOT NULL,
    `added_by` int DEFAULT NULL,
    `added_at` bigint NOT NULL,
    PRIMARY KEY (`campaign_id`, `user_id`),
    CONSTRAINT `campaign_participant_fk_campaign` FOREIGN KEY (`campaign_id`) REFERENCES `campaign` (`id`) ON DELETE CASCADE,
    CONSTRAINT `campaign_participant_fk_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `campaign_participant_fk_added_by` FOREIGN KEY (`added_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
