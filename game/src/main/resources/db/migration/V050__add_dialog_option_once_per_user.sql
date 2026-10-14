-- An answer the player may choose only once per campaign
ALTER TABLE dialog_option ADD COLUMN once_per_user tinyint(1) NOT NULL DEFAULT 0;
