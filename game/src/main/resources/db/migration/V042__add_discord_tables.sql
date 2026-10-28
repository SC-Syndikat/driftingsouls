CREATE TABLE `discord_konfiguration` (
  `id` int NOT NULL,
  `aktiv` tinyint(1) NOT NULL,
  `bot_token` varchar(255) DEFAULT NULL,
  `client_id` varchar(32) DEFAULT NULL,
  `client_secret` varchar(255) DEFAULT NULL,
  `redirect_uri` varchar(500) DEFAULT NULL,
  `guild_id` varchar(32) DEFAULT NULL,
  `invite_url` varchar(255) DEFAULT NULL,
  `version` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

INSERT INTO `discord_konfiguration` (`id`, `aktiv`, `guild_id`, `invite_url`, `version`)
VALUES (1, 0, '471610776126226432', 'https://discord.gg/cpxxAGy', 0);

-- Freigaben gelten fuer die Sichtbarkeit, die ein Kanal in Discord beim Freigeben hatte
-- (freigabe_signatur). Weicht die zuletzt gesehene (discord_signatur) ab, ist der Kanal gesperrt.
CREATE TABLE `discord_kanal` (
  `id` int NOT NULL AUTO_INCREMENT,
  `guild_id` varchar(32) NOT NULL,
  `channel_id` varchar(32) NOT NULL,
  `name` varchar(100) NOT NULL,
  `kategorie` varchar(100) DEFAULT NULL,
  `position` int NOT NULL,
  `lesen` varchar(20) NOT NULL,
  `schreiben` varchar(20) NOT NULL,
  `standard` tinyint(1) NOT NULL,
  `bot_hinweis` varchar(255) DEFAULT NULL,
  `oeffentlich` tinyint(1) NOT NULL,
  `discord_signatur` varchar(64) DEFAULT NULL,
  `freigabe_signatur` varchar(64) DEFAULT NULL,
  `version` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `discord_kanal_guild_channel` (`guild_id`, `channel_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Aus dem Spiel gesendete Nachrichten: Webhook-Nachrichten verraten nicht, welcher Account sie
-- geschrieben hat. Damit lassen sich z.B. die Rollenfarben auch fuer sie anzeigen. Eintraege werden
-- nach 30 Tagen, beim Trennen der Verknuepfung und beim Loeschen des Spielers entfernt.
CREATE TABLE `discord_nachricht` (
  `message_id` varchar(32) NOT NULL,
  `discord_user_id` varchar(32) NOT NULL,
  `user_id` int NOT NULL,
  `gesendet` datetime NOT NULL,
  PRIMARY KEY (`message_id`),
  KEY `discord_nachricht_user` (`user_id`),
  KEY `discord_nachricht_gesendet` (`gesendet`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
