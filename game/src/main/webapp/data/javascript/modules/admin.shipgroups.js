/**
 * Formular fuer einen Eintrag einer Schiffsgruppen-Vorlage: Cargo- und Einheiten-Editor,
 * die Grenzen des gewaehlten Schiffstyps, Module je Slot und ein Offizier mit Zufallswerten.
 * Erwartet die Daten des Plugins in sgShipTypes ({id: {cargo, unitSpace, maxUnitSize, slots: [[slot, slotTyp]]}}),
 * sgSlotTypes ({slotTyp: {name, modules: [itemId]}}), sgModuleNames ({itemId: name}),
 * sgCurrentModules ({slot: itemId}), sgUnitTypes ([{id, name, size, picture}]) und sgMaxRank.
 */
$(function() {
	var form = $('#sgEntryForm');
	if( form.length === 0 ) {
		return;
	}

	new CargoEditor('#sgCargo');
	var unitEditor = UnitEditor('#sgUnits', sgUnitTypes);

	var shipType = form.find('select[name=shiptype]');
	var modulesBox = form.find('.sgModules');
	var chosenModules = $.extend({}, sgCurrentModules);

	function showModules(type) {
		// Keep what was chosen per slot number when switching between ship types
		modulesBox.find('select').each(function() {
			chosenModules[$(this).attr('data-slot')] = $(this).val();
		});
		modulesBox.empty();
		if( !type.slots || type.slots.length === 0 ) {
			modulesBox.text('Dieser Schiffstyp hat keine Modul-Slots.');
			return;
		}
		type.slots.forEach(function(slot) {
			var slotType = sgSlotTypes[slot[1]] || {name: slot[1], modules: []};
			var select = $('<select />').attr('name', 'module_'+slot[0]).attr('data-slot', slot[0]);
			select.append($('<option value="0" />').text('– leer –'));
			slotType.modules.forEach(function(id) {
				select.append($('<option />').attr('value', id).text(sgModuleNames[id]));
			});
			var chosen = String(chosenModules[slot[0]] || 0);
			select.val(select.find('option[value="'+chosen+'"]').length ? chosen : '0');
			modulesBox.append($('<div />').text('Slot '+slot[0]+' ('+slotType.name+') ').append(select));
		});
	}

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
		showModules(type);
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
