-- Requests can also come from a battle, e.g. to negotiate terms
ALTER TABLE gm_request ADD COLUMN battle_id int DEFAULT NULL;
ALTER TABLE gm_request ADD CONSTRAINT gm_request_fk_battle FOREIGN KEY (battle_id) REFERENCES battles (id) ON DELETE SET NULL;
