CREATE TABLE `gtu_zwischenlager` (
  `id` int(10) unsigned NOT NULL auto_increment,
  `posten` int(11) NOT NULL default '0',
  `user1` int(11) NOT NULL default '0',
  `user2` int(11) NOT NULL default '0',
  `cargo1` text NOT NULL,
  `cargo1need` text NOT NULL,
  `cargo2` text NOT NULL,
  `cargo2need` text NOT NULL,
  PRIMARY KEY  (`id`),
  KEY `posten` (`posten`,`user1`,`user2`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8; 

ALTER TABLE gtu_zwischenlager ADD CONSTRAINT gtu_zwischenlager_fk_ships FOREIGN KEY (posten) REFERENCES ships(id);
ALTER TABLE gtu_zwischenlager ADD CONSTRAINT gtu_zwischenlager_fk_users1 FOREIGN KEY (user1) REFERENCES users(id);
ALTER TABLE gtu_zwischenlager ADD CONSTRAINT gtu_zwischenlager_fk_users2 FOREIGN KEY (user2) REFERENCES users(id);