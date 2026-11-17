-- A partial success: a band of percentage points right after the success chance.
-- roll < chance: success; roll < chance + partial_chance: partial success; otherwise failure.
ALTER TABLE dialog_option ADD COLUMN partial_chance int DEFAULT NULL;
-- node on partial success; NULL ends the dialog
ALTER TABLE dialog_option ADD COLUMN partial_node_id int DEFAULT NULL;
ALTER TABLE dialog_option ADD CONSTRAINT dialog_option_fk_partial FOREIGN KEY (partial_node_id) REFERENCES dialog_node (id) ON DELETE SET NULL;
