-- Modules installed on each ship of a ship group template entry: slot:item id, separated by |
ALTER TABLE `ship_group_template_entry`
    ADD COLUMN `modules` varchar(1000) NOT NULL DEFAULT '';
