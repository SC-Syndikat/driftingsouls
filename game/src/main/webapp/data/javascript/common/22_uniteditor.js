/**
 * Konstruiert einen grafischen Editor fuer Einheiten-Strings der Form
 * <code>typId:anzahl|typId:anzahl</code>, analog zum CargoEditor.
 * Der Editor zeigt den belegten Einheitenraum und je Einheitentyp,
 * wie viele Einheiten hoechstens passen.
 * @param selector Selektor auf das (am besten versteckte) Input-Feld mit dem Einheiten-String
 * @param unitTypes Die waehlbaren Einheitentypen: [{id, name, size, picture}]
 * @returns {{setCapacity: Function}|null} Der Editor oder null, falls das Feld fehlt
 */
var UnitEditor = function(selector, unitTypes) {
	var target = $(selector).first();
	if( target.length === 0 ) {
		return null;
	}

	var typesById = {};
	for( var i=0; i < unitTypes.length; i++ ) {
		typesById[unitTypes[i].id] = unitTypes[i];
	}

	// Einheitenraum und maximale Einheitengroesse des Schiffstyps; -1 = unbegrenzt
	var space = -1;
	var maxSize = -1;
	var order = [];
	var counts = {};

	(target.val() || '').split('|').forEach(function(part) {
		var idAndCount = part.split(':');
		if( idAndCount.length !== 2 ) {
			return;
		}
		var id = parseInt(idAndCount[0], 10);
		var count = parseInt(idAndCount[1], 10);
		if( isNaN(id) || isNaN(count) || count <= 0 ) {
			return;
		}
		if( typeof counts[id] === 'undefined' ) {
			order.push(id);
			counts[id] = 0;
		}
		counts[id] += count;
	});

	var view = $('<div class="cargoEditor unitEditor"><table><tbody></tbody><tfoot>'+
		'<tr><td colspan="2"><select class="newEntry" size="1"></select></td>'+
		'<td><input type="text" size="4" value="0" class="newEntryCount" /></td>'+
		'<td><button class="addEntry">+</button></td></tr>'+
		'<tr><td colspan="4" class="unitSpace"></td></tr>'+
		'</tfoot></table></div>');
	target.after(view);

	function mass(exceptId) {
		var sum = 0;
		order.forEach(function(id) {
			if( id !== exceptId && typesById[id] ) {
				sum += counts[id] * typesById[id].size;
			}
		});
		return sum;
	}

	function fits(type) {
		return maxSize === -1 || type.size <= maxSize;
	}

	function maxCount(type) {
		if( space === -1 || type.size <= 0 ) {
			return null;
		}
		return Math.max(0, Math.floor((space - mass(type.id)) / type.size));
	}

	function label(type) {
		var max = maxCount(type);
		return type.name+' (Größe '+type.size+(max === null ? '' : ', max. '+max)+')';
	}

	function save() {
		var parts = [];
		order.forEach(function(id) {
			if( counts[id] > 0 ) {
				parts.push(id+':'+counts[id]);
			}
		});
		target.val(parts.join('|'));
	}

	function render() {
		var body = view.find('tbody').empty();
		order.forEach(function(id) {
			var type = typesById[id];
			var row = $('<tr><td class="picture"></td><td class="name"></td>'+
				'<td><input class="unitCount" type="text" size="4" /></td><td class="hint"></td></tr>');
			if( type ) {
				row.find('.picture').append($('<img alt="" />').attr('src', type.picture));
				row.find('.name').html(type.name);
				var max = maxCount(type);
				row.find('.hint').text((max === null ? '' : 'max. '+max)+(fits(type) ? '' : ' – zu groß für diesen Schiffstyp'));
			}
			else {
				row.find('.name').text('Unbekannter Einheitentyp '+id);
			}
			row.find('.unitCount').val(counts[id]).attr('unit-id', id);
			body.append(row);
		});

		var select = view.find('.newEntry').empty();
		unitTypes.forEach(function(type) {
			if( typeof counts[type.id] === 'undefined' && fits(type) ) {
				select.append($('<option />').attr('value', type.id).html(label(type)));
			}
		});

		var used = mass(null);
		var info = view.find('.unitSpace').removeClass('error');
		if( space === -1 ) {
			info.text('Belegter Einheitenraum: '+used);
		}
		else if( space === 0 ) {
			info.text('Dieser Schiffstyp kann keine Einheiten aufnehmen.');
			if( used > 0 ) {
				info.addClass('error');
			}
		}
		else {
			info.text('Belegter Einheitenraum: '+used+' von '+space+(maxSize === -1 ? '' : ' (Einheiten bis Größe '+maxSize+')'));
			if( used > space ) {
				info.addClass('error');
			}
		}
	}

	view.on('change', '.unitCount', function() {
		var id = parseInt($(this).attr('unit-id'), 10);
		var count = parseInt($(this).val(), 10);
		counts[id] = isNaN(count) || count < 0 ? 0 : count;
		if( counts[id] === 0 ) {
			order.splice(order.indexOf(id), 1);
			delete counts[id];
		}
		save();
		render();
	});

	view.find('.addEntry').on('click', function() {
		var id = parseInt(view.find('.newEntry').val(), 10);
		var count = parseInt(view.find('.newEntryCount').val(), 10);
		if( !isNaN(id) && !isNaN(count) && count > 0 ) {
			order.push(id);
			counts[id] = count;
			view.find('.newEntryCount').val('0');
			save();
			render();
		}
		return false;
	});

	render();

	return {
		/**
		 * Setzt die Grenzen des Schiffstyps und zeichnet den Editor neu.
		 * @param unitSpace Der Einheitenraum
		 * @param maxUnitSize Die groesste erlaubte Einheitengroesse; -1 fuer beliebig
		 */
		setCapacity: function(unitSpace, maxUnitSize) {
			space = unitSpace;
			maxSize = maxUnitSize;
			render();
		}
	};
};
