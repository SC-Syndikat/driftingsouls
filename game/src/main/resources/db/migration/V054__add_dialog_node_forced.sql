-- The player must answer: the dialog cannot be left at this node, nor replaced by another one
ALTER TABLE dialog_node ADD COLUMN forced tinyint(1) NOT NULL DEFAULT 0;
