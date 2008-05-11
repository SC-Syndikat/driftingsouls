CREATE TABLE `stats_user_cargo` (
  `user_id` int(11) NOT NULL default '0',
  `cargo` text NOT NULL,
  `version` int(10) unsigned not null default '0',
  PRIMARY KEY  (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='Die User-Cargo-Stats'; 

ALTER TABLE stats_user_cargo ADD CONSTRAINT stats_user_cargo_fk_user_id FOREIGN KEY (user_id) REFERENCES users(id);