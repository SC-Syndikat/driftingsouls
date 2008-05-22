CREATE TABLE `users` (
  `id` int(11) NOT NULL default '0',
  `un` varchar(40) NOT NULL default '',
  `name` varchar(255) NOT NULL default 'Kolonist',
  `passwort` varchar(40) NOT NULL default '',
  `race` int(11) NOT NULL default '1',
  `inakt` int(11) NOT NULL default '0',
  `signup` int(11) NOT NULL default '0',
  `history` text NOT NULL,
  `medals` varchar(50) NOT NULL default '',
  `rang` tinyint(3) unsigned NOT NULL default '0',
  `ally` int(11) default NULL,
  `konto` bigint(20) unsigned NOT NULL default '0',
  `cargo` text NOT NULL,
  `nstat` varchar(30) NOT NULL default '0',
  `email` varchar(60) NOT NULL default '',
  `log_fail` mediumint(9) NOT NULL default '0',
  `accesslevel` int(11) NOT NULL default '0',
  `npcpunkte` int(11) NOT NULL default '10',
  `nickname` varchar(255) NOT NULL default 'Kolonist',
  `plainname` varchar(255) NOT NULL default 'Kolonist',
  `allyposten` int(11) default NULL,
  `gtudropzone` tinyint(3) unsigned NOT NULL default '2',
  `npcorderloc` varchar(12) NOT NULL default '',
  `imgpath` varchar(200) NOT NULL default 'http://localhost/ds2/',
  `flagschiff` int(11) default NULL,
  `disabled` tinyint(3) unsigned NOT NULL default '0',
  `flags` tinytext NOT NULL,
  `vaccount` smallint(5) unsigned NOT NULL default '0',
  `wait4vac` smallint(5) unsigned NOT NULL default '0',
  `lostBattles` smallint(5) unsigned NOT NULL default '0',
  `wonBattles` smallint(5) unsigned NOT NULL default '0',
  `destroyedShips` int(10) unsigned NOT NULL default '0',
  `lostShips` int(10) unsigned NOT NULL default '0',
  `knownItems` text NOT NULL default '',
  `version` int(10) unsigned not null default '0',
  `blocked` tinyint(1) unsigned not null default '0',
  PRIMARY KEY  (`id`),
  KEY `ally` (`ally`),
  KEY `vaccount` (`vaccount`,`wait4vac`),
  KEY `un` (`un`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8; 
