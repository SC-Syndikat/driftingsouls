-- Reusable NPC fleets for campaigns: spawned by the game master, or by dialog actions
-- (reinforcements, a battle with a predefined line-up).
CREATE TABLE `ship_group_template` (
    `id` int NOT NULL AUTO_INCREMENT,
    `name` varchar(255) NOT NULL,
    `description` text NOT NULL,
    `create_fleet` tinyint(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

CREATE TABLE `ship_group_template_entry` (
    `id` int NOT NULL AUTO_INCREMENT,
    `template_id` int NOT NULL,
    `ship_type_id` int NOT NULL,
    `count` int NOT NULL DEFAULT 1,
    -- {n} is replaced by the running number of the ship
    `name_pattern` varchar(255) NOT NULL,
    -- cargo string as the ship editor shows it; empty for default fuel only
    `cargo` text NOT NULL,
    -- unit type id:count, separated by |
    `units` varchar(1000) NOT NULL,
    -- NULL: no officer; otherwise all abilities of the officer
    `officer_skill` int DEFAULT NULL,
    `alarm` int NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `ship_group_template_entry_fk_template` FOREIGN KEY (`template_id`) REFERENCES `ship_group_template` (`id`) ON DELETE CASCADE,
    CONSTRAINT `ship_group_template_entry_fk_ship_type` FOREIGN KEY (`ship_type_id`) REFERENCES `ship_types` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
