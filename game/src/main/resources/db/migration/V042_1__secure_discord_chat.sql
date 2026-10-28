-- Freigaben gelten fuer die Sichtbarkeit, die ein Kanal in Discord beim Freigeben hatte.
-- Bisherige Freigaben haben keine Signatur und muessen einmal neu gespeichert werden.
ALTER TABLE `discord_kanal`
  ADD COLUMN `oeffentlich` tinyint(1) NOT NULL DEFAULT 0 AFTER `bot_hinweis`,
  ADD COLUMN `discord_signatur` varchar(64) DEFAULT NULL AFTER `oeffentlich`,
  ADD COLUMN `freigabe_signatur` varchar(64) DEFAULT NULL AFTER `discord_signatur`;

-- Eintraege werden nach 30 Tagen, beim Trennen der Verknuepfung und beim Loeschen des Spielers entfernt
ALTER TABLE `discord_nachricht`
  ADD COLUMN `gesendet` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ADD KEY `discord_nachricht_user` (`user_id`),
  ADD KEY `discord_nachricht_gesendet` (`gesendet`);
