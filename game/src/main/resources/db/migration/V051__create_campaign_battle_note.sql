-- Why a player is in a battle a dialog started: shown on the battle screen while the battle lasts.
CREATE TABLE `campaign_battle_note` (
    `id` int NOT NULL AUTO_INCREMENT,
    `battle_id` int NOT NULL,
    `user_id` int NOT NULL,
    `speaker` varchar(255) NOT NULL,
    -- what the player answered
    `answer` text NOT NULL,
    -- the NPC's reply, BBCode; empty if the dialog ended with the choice
    `reply` text NOT NULL,
    `created_at` bigint NOT NULL,
    PRIMARY KEY (`id`),
    KEY `campaign_battle_note_battle_user` (`battle_id`, `user_id`),
    CONSTRAINT `campaign_battle_note_fk_battle` FOREIGN KEY (`battle_id`) REFERENCES `battles` (`id`) ON DELETE CASCADE,
    CONSTRAINT `campaign_battle_note_fk_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
