create table items_schiffstyp_modifikation (items_id integer not null, setEffekte_id bigint not null, setEffekte_KEY integer, primary key (items_id, setEffekte_KEY)) ENGINE=InnoDB;
alter table items_schiffstyp_modifikation add index schiffstypmodifikation_fk_schiffsmodulset (setEffekte_id), add constraint schiffstypmodifikation_fk_schiffsmodulset foreign key (setEffekte_id) references schiffstyp_modifikation (id);
alter table items_schiffstyp_modifikation add index schiffsmodulset_fk_schiffstypmodifikation (items_id), add constraint schiffsmodulset_fk_schiffstypmodifikation foreign key (items_id) references items (id);

INSERT INTO schiffstyp_modifikation (id, aDocks, ablativeArmor, bounty, cargo, cost, crew, deutFactor, eps, heat, hull, hydro, jDocks, lostInEmpChance, maxunitsize, minCrew, nahrungcargo, nickname, oneWayWerft_id, panzerung, pickingCost, picture, ra, rd, reCost, rm, ru, scanCost, sensorRange, shields, size, srs, torpedoDef, unitspace, version, werft) VALUES (9532, 0, 0, null, 2500, 0, 1000, 0, 100, 0, 50000, 0, 0, 0, 0, 0, 0, null, null, 5, 0, null, 20, 5, 0, 50, 4, 0, 2, 0, 0, null, 0, 0, 1, 0);
INSERT INTO schiffstyp_modifikation (id, aDocks, ablativeArmor, bounty, cargo, cost, crew, deutFactor, eps, heat, hull, hydro, jDocks, lostInEmpChance, maxunitsize, minCrew, nahrungcargo, nickname, oneWayWerft_id, panzerung, pickingCost, picture, ra, rd, reCost, rm, ru, scanCost, sensorRange, shields, size, srs, torpedoDef, unitspace, version, werft) VALUES (9533, 0, 0, null, 5000, -1, 2000, 0, 200, 0, 100000, 0, 10, 0, 0, 0, 0, null, null, 10, 0, null, 30, 10, 0, 80, 8, 0, 3, 0, 0, null, 0, 0, 1, 0);
INSERT INTO schiffstyp_modifikation (id, aDocks, ablativeArmor, bounty, cargo, cost, crew, deutFactor, eps, heat, hull, hydro, jDocks, lostInEmpChance, maxunitsize, minCrew, nahrungcargo, nickname, oneWayWerft_id, panzerung, pickingCost, picture, ra, rd, reCost, rm, ru, scanCost, sensorRange, shields, size, srs, torpedoDef, unitspace, version, werft) VALUES (9534, 0, 0, null, 10500, -2, 3000, 0, 800, -1, 200000, 0, 20, 0, 0, 0, 0, null, null, 15, 0, null, 50, 15, 0, 100, 12, 0, 6, 0, 0, null, 0, 0, 1, 0);
INSERT INTO schiffstyp_modifikation (id, aDocks, ablativeArmor, bounty, cargo, cost, crew, deutFactor, eps, heat, hull, hydro, jDocks, lostInEmpChance, maxunitsize, minCrew, nahrungcargo, nickname, oneWayWerft_id, panzerung, pickingCost, picture, ra, rd, reCost, rm, ru, scanCost, sensorRange, shields, size, srs, torpedoDef, unitspace, version, werft) VALUES (9535, 0, 0, null, 70000, -3, 6000, 0, 2200, -2, 200000, 0, 35, 0, 0, 0, 0, null, null, 40, 0, null, 100, 25, 0, 200, 16, 0, 12, 0, 0, null, 0, 0, 1, 0);
INSERT INTO schiffstyp_modifikation (id, aDocks, ablativeArmor, bounty, cargo, cost, crew, deutFactor, eps, heat, hull, hydro, jDocks, lostInEmpChance, maxunitsize, minCrew, nahrungcargo, nickname, oneWayWerft_id, panzerung, pickingCost, picture, ra, rd, reCost, rm, ru, scanCost, sensorRange, shields, size, srs, torpedoDef, unitspace, version, werft) VALUES (9536, 0, 0, null, 0, 0, 0, 0, 2000, 0, 1500000, 0, 0, 0, 0, 0, 0, null, null, 0, 0, null, 0, 0, 0, 1250, 0, 0, 0, 0, 0, null, 0, 0, 1, 0);
INSERT INTO schiffstyp_modifikation (id, aDocks, ablativeArmor, bounty, cargo, cost, crew, deutFactor, eps, heat, hull, hydro, jDocks, lostInEmpChance, maxunitsize, minCrew, nahrungcargo, nickname, oneWayWerft_id, panzerung, pickingCost, picture, ra, rd, reCost, rm, ru, scanCost, sensorRange, shields, size, srs, torpedoDef, unitspace, version, werft) VALUES (9537, 0, 0, null, 10500, -8, 0, 0, 3200, -8, 105000, 0, 60, 0, 0, 0, 0, null, null, 40, 0, null, 300, 75, 0, 1500, 25, 0, 10, 0, 0, null, 0, 0, 1, 0);
INSERT INTO schiffstyp_modifikation (id, aDocks, ablativeArmor, bounty, cargo, cost, crew, deutFactor, eps, heat, hull, hydro, jDocks, lostInEmpChance, maxunitsize, minCrew, nahrungcargo, nickname, oneWayWerft_id, panzerung, pickingCost, picture, ra, rd, reCost, rm, ru, scanCost, sensorRange, shields, size, srs, torpedoDef, unitspace, version, werft) VALUES (9538, 0, 0, null, 0, 0, 0, 0, 500, 0, 0, 0, 0, 0, 0, 0, 0, null, null, 0, 0, null, 0, 0, 0, 0, 0, 0, 1, 0, 0, null, 0, 0, 0, 0);
INSERT INTO schiffstyp_modifikation (id, aDocks, ablativeArmor, bounty, cargo, cost, crew, deutFactor, eps, heat, hull, hydro, jDocks, lostInEmpChance, maxunitsize, minCrew, nahrungcargo, nickname, oneWayWerft_id, panzerung, pickingCost, picture, ra, rd, reCost, rm, ru, scanCost, sensorRange, shields, size, srs, torpedoDef, unitspace, version, werft) VALUES (9539, 0, 0, null, 0, 0, 0, 0, 1500, 0, 0, 0, 1, 0, 0, 0, 0, null, null, 10, 0, null, 0, 0, 0, 0, 0, 0, 3, 0, 0, null, 0, 0, 1, 0);
INSERT INTO schiffstyp_modifikation (id, aDocks, ablativeArmor, bounty, cargo, cost, crew, deutFactor, eps, heat, hull, hydro, jDocks, lostInEmpChance, maxunitsize, minCrew, nahrungcargo, nickname, oneWayWerft_id, panzerung, pickingCost, picture, ra, rd, reCost, rm, ru, scanCost, sensorRange, shields, size, srs, torpedoDef, unitspace, version, werft) VALUES (9540, 0, 0, null, 0, 0, 0, 0, 50, 0, 0, 0, 0, 0, 0, 0, 0, null, null, 0, 0, null, 0, 0, 0, 0, 0, 0, 1, 0, 0, null, 0, 0, 0, 0);
INSERT INTO schiffstyp_modifikation (id, aDocks, ablativeArmor, bounty, cargo, cost, crew, deutFactor, eps, heat, hull, hydro, jDocks, lostInEmpChance, maxunitsize, minCrew, nahrungcargo, nickname, oneWayWerft_id, panzerung, pickingCost, picture, ra, rd, reCost, rm, ru, scanCost, sensorRange, shields, size, srs, torpedoDef, unitspace, version, werft) VALUES (9541, 0, 0, null, 0, 0, 0, 0, 150, 0, 0, 0, 1, 0, 0, 0, 0, null, null, 0, 0, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, 0, 0, 1, 0);

INSERT INTO schiffswaffenkonfiguration (id, anzahl, hitze, maxUeberhitzung, version, waffe_id, schiffstyp_modifikation_id) VALUES (null, 1, 1, 0, 0, 'set_hol_shrine_of_sacrifice', 9535);
INSERT INTO schiffswaffenkonfiguration (id, anzahl, hitze, maxUeberhitzung, version, waffe_id, schiffstyp_modifikation_id) VALUES (null, 0, 120, 0, 0, 'AAAf', 9536);
INSERT INTO schiffswaffenkonfiguration (id, anzahl, hitze, maxUeberhitzung, version, waffe_id, schiffstyp_modifikation_id) VALUES (null, 0, 80, 0, 0, 'heavy_flak', 9536);
INSERT INTO schiffswaffenkonfiguration (id, anzahl, hitze, maxUeberhitzung, version, waffe_id, schiffstyp_modifikation_id) VALUES (null, 0, 40, 0, 0, 'BGreen', 9536);
INSERT INTO schiffswaffenkonfiguration (id, anzahl, hitze, maxUeberhitzung, version, waffe_id, schiffstyp_modifikation_id) VALUES (null, 0, 120, 0, 0, 'standard_flak', 9536);
INSERT INTO schiffswaffenkonfiguration (id, anzahl, hitze, maxUeberhitzung, version, waffe_id, schiffstyp_modifikation_id) VALUES (null, 8, 80, 0, 0, 'BVas', 9537);
INSERT INTO schiffswaffenkonfiguration (id, anzahl, hitze, maxUeberhitzung, version, waffe_id, schiffstyp_modifikation_id) VALUES (null, 10, 100, 0, 0, 'heavy_flak', 9537);
INSERT INTO schiffswaffenkonfiguration (id, anzahl, hitze, maxUeberhitzung, version, waffe_id, schiffstyp_modifikation_id) VALUES (null, 6, 60, 0, 0, 'AAAf', 9537);
INSERT INTO schiffswaffenkonfiguration (id, anzahl, hitze, maxUeberhitzung, version, waffe_id, schiffstyp_modifikation_id) VALUES (null, 1, 3, 0, 0, 'set_ewige_verdammnis', 9537);

INSERT INTO schiffstyp_modifikation_flags (SchiffstypModifikation_id, flags) VALUES (9532, 9);
INSERT INTO schiffstyp_modifikation_flags (SchiffstypModifikation_id, flags) VALUES (9532, 12);
INSERT INTO schiffstyp_modifikation_flags (SchiffstypModifikation_id, flags) VALUES (9533, 9);
INSERT INTO schiffstyp_modifikation_flags (SchiffstypModifikation_id, flags) VALUES (9533, 12);
INSERT INTO schiffstyp_modifikation_flags (SchiffstypModifikation_id, flags) VALUES (9533, 11);
INSERT INTO schiffstyp_modifikation_flags (SchiffstypModifikation_id, flags) VALUES (9534, 9);
INSERT INTO schiffstyp_modifikation_flags (SchiffstypModifikation_id, flags) VALUES (9534, 12);
INSERT INTO schiffstyp_modifikation_flags (SchiffstypModifikation_id, flags) VALUES (9534, 11);
INSERT INTO schiffstyp_modifikation_flags (SchiffstypModifikation_id, flags) VALUES (9534, 6);
INSERT INTO schiffstyp_modifikation_flags (SchiffstypModifikation_id, flags) VALUES (9535, 9);
INSERT INTO schiffstyp_modifikation_flags (SchiffstypModifikation_id, flags) VALUES (9535, 12);
INSERT INTO schiffstyp_modifikation_flags (SchiffstypModifikation_id, flags) VALUES (9535, 11);
INSERT INTO schiffstyp_modifikation_flags (SchiffstypModifikation_id, flags) VALUES (9535, 6);
INSERT INTO schiffstyp_modifikation_flags (SchiffstypModifikation_id, flags) VALUES (9535, 4);
INSERT INTO schiffstyp_modifikation_flags (SchiffstypModifikation_id, flags) VALUES (9535, 10);
INSERT INTO schiffstyp_modifikation_flags (SchiffstypModifikation_id, flags) VALUES (9539, 3);
INSERT INTO schiffstyp_modifikation_flags (SchiffstypModifikation_id, flags) VALUES (9539, 11);
INSERT INTO schiffstyp_modifikation_flags (SchiffstypModifikation_id, flags) VALUES (9541, 12);

INSERT INTO items_schiffstyp_modifikation (items_id, setEffekte_id, setEffekte_KEY) VALUES (995, 9532, 3);
INSERT INTO items_schiffstyp_modifikation (items_id, setEffekte_id, setEffekte_KEY) VALUES (995, 9533, 5);
INSERT INTO items_schiffstyp_modifikation (items_id, setEffekte_id, setEffekte_KEY) VALUES (995, 9534, 6);
INSERT INTO items_schiffstyp_modifikation (items_id, setEffekte_id, setEffekte_KEY) VALUES (995, 9535, 7);
INSERT INTO items_schiffstyp_modifikation (items_id, setEffekte_id, setEffekte_KEY) VALUES (996, 9536, 7);
INSERT INTO items_schiffstyp_modifikation (items_id, setEffekte_id, setEffekte_KEY) VALUES (997, 9537, 7);
INSERT INTO items_schiffstyp_modifikation (items_id, setEffekte_id, setEffekte_KEY) VALUES (998, 9538, 4);
INSERT INTO items_schiffstyp_modifikation (items_id, setEffekte_id, setEffekte_KEY) VALUES (998, 9539, 6);
INSERT INTO items_schiffstyp_modifikation (items_id, setEffekte_id, setEffekte_KEY) VALUES (999, 9540, 2);
INSERT INTO items_schiffstyp_modifikation (items_id, setEffekte_id, setEffekte_KEY) VALUES (999, 9541, 3);