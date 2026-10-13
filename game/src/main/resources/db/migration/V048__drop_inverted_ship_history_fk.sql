-- The legacy foreign key points from ships to ship_history, although the history takes its id from the ship.
-- Hibernate inserts the ship first, so creating any ship violated it.
ALTER TABLE ships DROP FOREIGN KEY FK6856DB7B6B202B4;
