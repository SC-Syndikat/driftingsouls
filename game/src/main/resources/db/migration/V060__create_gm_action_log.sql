-- What game masters did in a campaign beyond the dialogs: spawning, handing over ships, credits, ending
-- battles, actions from requests, steering NPCs, master logins and trust corrections.
CREATE TABLE `gm_action_log` (
    `id` int NOT NULL AUTO_INCREMENT,
    `campaign_id` int DEFAULT NULL,
    `campaign_tick` int NOT NULL,
    -- the game master (or admin) behind the action, also while he steers an NPC
    `gamemaster_id` int NOT NULL,
    -- the NPC acted for, if any
    `npc_user_id` int DEFAULT NULL,
    -- the player concerned, if any
    `user_id` int DEFAULT NULL,
    -- the kind of action, see GmAction
    `action` varchar(32) NOT NULL,
    `details` text NOT NULL,
    `created_at` bigint NOT NULL,
    PRIMARY KEY (`id`),
    KEY `gm_action_log_campaign` (`campaign_id`, `id`),
    CONSTRAINT `gm_action_log_fk_campaign` FOREIGN KEY (`campaign_id`) REFERENCES `campaign` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
