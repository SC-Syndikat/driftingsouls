-- The game master who looks after a request. NULL while nobody has taken it; another game master answers
-- only after taking it over.
ALTER TABLE `gm_request`
    ADD COLUMN `assigned_gm_id` int DEFAULT NULL AFTER `status`,
    ADD CONSTRAINT `gm_request_fk_assigned_gm` FOREIGN KEY (`assigned_gm_id`) REFERENCES `users` (`id`) ON DELETE SET NULL;
