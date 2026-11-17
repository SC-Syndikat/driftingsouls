-- GIVE_RESOURCE and TAKE_RESOURCE got a choice where the goods come from or go to; existing actions keep
-- their behaviour: newly created goods, and goods that disappear.
UPDATE dialog_option_action SET params = JSON_SET(params, '$.from', 'NEW')
WHERE type = 'GIVE_RESOURCE' AND JSON_EXTRACT(params, '$.from') IS NULL;
UPDATE dialog_option_action SET params = JSON_SET(params, '$.to', 'GONE')
WHERE type = 'TAKE_RESOURCE' AND JSON_EXTRACT(params, '$.to') IS NULL;
