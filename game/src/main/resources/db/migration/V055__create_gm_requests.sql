-- A player's request to the game master, written from a dialog when none of the answers fits.
CREATE TABLE `gm_request` (
    `id` int NOT NULL AUTO_INCREMENT,
    `campaign_id` int NOT NULL,
    `user_id` int NOT NULL,
    -- the player's ship the request was written from
    `ship_id` int NOT NULL,
    `interaction_id` int DEFAULT NULL,
    -- the dialog node the player was at
    `node_id` int DEFAULT NULL,
    -- OPEN or CLOSED
    `status` varchar(16) NOT NULL,
    `created_at` bigint NOT NULL,
    `updated_at` bigint NOT NULL,
    `unread_by_gm` tinyint(1) NOT NULL DEFAULT 1,
    `unread_by_user` tinyint(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `gm_request_campaign_status` (`campaign_id`, `status`),
    KEY `gm_request_user_ship` (`user_id`, `ship_id`),
    CONSTRAINT `gm_request_fk_campaign` FOREIGN KEY (`campaign_id`) REFERENCES `campaign` (`id`) ON DELETE CASCADE,
    CONSTRAINT `gm_request_fk_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `gm_request_fk_interaction` FOREIGN KEY (`interaction_id`) REFERENCES `interaction` (`id`) ON DELETE SET NULL,
    CONSTRAINT `gm_request_fk_node` FOREIGN KEY (`node_id`) REFERENCES `dialog_node` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

-- The conversation about a request: the player's text, the game master's answers, and what his actions did.
CREATE TABLE `gm_request_message` (
    `id` int NOT NULL AUTO_INCREMENT,
    `request_id` int NOT NULL,
    `author_id` int DEFAULT NULL,
    -- PLAYER, GAMEMASTER or ACTION (the outcome of an action the game master ran)
    `kind` varchar(16) NOT NULL,
    `text` text NOT NULL,
    `created_at` bigint NOT NULL,
    PRIMARY KEY (`id`),
    KEY `gm_request_message_request` (`request_id`),
    CONSTRAINT `gm_request_message_fk_request` FOREIGN KEY (`request_id`) REFERENCES `gm_request` (`id`) ON DELETE CASCADE,
    CONSTRAINT `gm_request_message_fk_author` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
