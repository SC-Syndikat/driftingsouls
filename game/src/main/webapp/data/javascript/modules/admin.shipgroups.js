/**
 * Formular fuer einen Eintrag einer Schiffsgruppen-Vorlage: Cargo- und Einheiten-Editor,
 * die Grenzen des gewaehlten Schiffstyps und ein Offizier mit Zufallswerten.
 * Erwartet die Daten des Plugins in sgShipTypes ({id: {cargo, unitSpace, maxUnitSize}}),
 * sgUnitTypes ([{id, name, size, picture}]) und sgMaxRank.
 */
$(function() {
	var form = $('#sgEntryForm');
	if( form.length === 0 ) {
		return;
	}

	new CargoEditor('#sgCargo');
	var unitEditor = UnitEditor('#sgUnits', sgUnitTypes);

	var shipType = form.find('select[name=shiptype]');
	function showShipType() {
		var type = sgShipTypes[shipType.val()];
		if( !type ) {
			return;
		}
		form.find('.sgShipTypeInfo').text('Laderaum '+type.cargo+', Einheitenraum '+type.unitSpace+
			(type.maxUnitSize === -1 ? '' : ', Einheiten bis Größe '+type.maxUnitSize));
		if( unitEditor ) {
			unitEditor.setCapacity(type.unitSpace, type.maxUnitSize);
		}
	}
	shipType.on('change', showShipType);
	showShipType();

	var abilities = ['nav', 'ing', 'waf', 'sec', 'com'];
	function abilityField(ability) {
		return form.find('input[name=officer_'+ability+']');
	}
	function number(field) {
		var value = parseInt(field.val(), 10);
		return isNaN(value) || value < 0 ? 0 : value;
	}

	function showRank() {
		var sum = 0;
		abilities.forEach(function(ability) {
			sum += number(abilityField(ability));
		});
		form.find('.sgOfficerRank').text(Math.min(sgMaxRank, Math.floor(sum / 5 / 125)));
	}

	function showOfficer() {
		var enabled = form.find('input[name=officer]').is(':checked');
		form.find('.sgOfficerFields').find('input, select, button').prop('disabled', !enabled);
	}

	form.find('.sgOfficerRandom').on('click', function() {
		var min = number(form.find('input[name=officer_min]'));
		var max = number(form.find('input[name=officer_max]'));
		if( max < min ) {
			var swap = min;
			min = max;
			max = swap;
		}
		abilities.forEach(function(ability) {
			abilityField(ability).val(min + Math.floor(Math.random() * (max - min + 1)));
		});
		showRank();
		return false;
	});

	abilities.forEach(function(ability) {
		abilityField(ability).on('change keyup', showRank);
	});
	form.find('input[name=officer]').on('change', showOfficer);
	showRank();
	showOfficer();
});
