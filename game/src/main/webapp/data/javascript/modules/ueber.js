/*
	Umschalten der Flotten/Bookmark-Box
*/
var UeberBox = {
	zeige : function(box) {
		var buttons = $('#ueber-box-umschalter button');
		buttons.removeClass('aktiv');
		buttons.filter('[data-box=' + box + ']').addClass('aktiv');

		$('#ueber-flotten').toggle(box === 'fleets');
		$('#ueber-bookmarks').toggle(box === 'bookmarks');

		DS.getJSON({module:'ueber', action:'box', box:box}, function() {});
	}
};

/*
	Discord-Chat
*/
var DiscordChat = {
	INTERVALL : 5000,
	__timer : null,
	__signatur : null,
	__laedt : false,

	init : function() {
		var chat = $('#discord-chat');
		if( chat.length === 0 ) {
			return;
		}

		chat.find('.discord-kanal').on('click', function() {
			DiscordChat.wechsleKanal($(this));
		});
		chat.find('form.discord-eingabe').on('submit', function(event) {
			event.preventDefault();
			DiscordChat.senden();
		});
		$(document).on('visibilitychange', function() {
			if( !document.hidden ) {
				DiscordChat.laden();
			}
		});

		this.laden();
	},

	__kanal : function() {
		return $('#discord-chat').attr('data-channel');
	},

	wechsleKanal : function(kanalElement) {
		var chat = $('#discord-chat');
		var kanal = kanalElement.attr('data-channel');
		if( kanal === this.__kanal() ) {
			return;
		}

		chat.attr('data-channel', kanal);
		chat.find('.discord-kanal').removeClass('aktiv');
		kanalElement.addClass('aktiv');
		chat.find('.discord-extern').attr('href', 'https://discord.com/channels/' + chat.attr('data-guild') + '/' + kanal);
		chat.find('form.discord-eingabe input[name=text]').attr('placeholder', 'Nachricht an #' + kanalElement.attr('data-name'));

		this.__signatur = null;
		chat.find('.discord-nachrichten').empty().append($('<div class="discord-hinweis"></div>').text('Lade Nachrichten...'));
		this.laden();
	},

	__planeNaechstesLaden : function() {
		clearTimeout(this.__timer);
		this.__timer = setTimeout(function() {
			if( document.hidden ) {
				// Erst beim Zurueckkehren auf die Seite wieder laden (visibilitychange)
				return;
			}
			DiscordChat.laden();
		}, this.INTERVALL);
	},

	laden : function() {
		if( this.__laedt ) {
			return;
		}
		this.__laedt = true;
		var kanal = this.__kanal();

		jQuery.ajax({
			url: DS.getUrl(),
			data: {module:'ueber', action:'discordChat', channel:kanal, FORMAT:'JSON'},
			dataType: 'json',
			success: function(result) {
				if( kanal === DiscordChat.__kanal() ) {
					DiscordChat.__zeigeErgebnis(result);
				}
			},
			error: function() {
				DiscordChat.__zeigeFehler('Der Chat konnte nicht geladen werden.');
			},
			complete: function() {
				DiscordChat.__laedt = false;
				DiscordChat.__planeNaechstesLaden();
			}
		});
	},

	senden : function() {
		var form = $('#discord-chat form.discord-eingabe');
		var eingabe = form.find('input[name=text]');
		var text = $.trim(eingabe.val());
		if( text === '' ) {
			return;
		}

		var kanal = this.__kanal();
		form.find('input').prop('disabled', true);

		jQuery.ajax({
			url: DS.getUrl(),
			type: 'POST',
			data: {module:'ueber', action:'discordSend', channel:kanal, text:text, FORMAT:'JSON'},
			dataType: 'json',
			success: function(result) {
				if( !result.error ) {
					eingabe.val('');
				}
				if( kanal === DiscordChat.__kanal() ) {
					DiscordChat.__zeigeErgebnis(result, true);
				}
			},
			error: function() {
				DiscordChat.__zeigeFehler('Die Nachricht konnte nicht gesendet werden.');
			},
			complete: function() {
				form.find('input').prop('disabled', false);
				eingabe.focus();
			}
		});
	},

	__zeigeFehler : function(text) {
		var chat = $('#discord-chat');
		var fehler = chat.find('.discord-fehler');
		if( fehler.length === 0 ) {
			fehler = $('<div class="discord-fehler"></div>').insertAfter(chat.find('.discord-nachrichten'));
		}
		fehler.text(text);
	},

	__zeigeErgebnis : function(result, zumEnde) {
		var chat = $('#discord-chat');
		if( typeof result.messages === 'undefined' ) {
			// Fehlerantwort des Frameworks (z.B. nicht mehr eingeloggt)
			this.__zeigeFehler(result.message && result.message.description ? result.message.description : 'Der Chat konnte nicht geladen werden.');
			return;
		}

		if( result.error ) {
			this.__zeigeFehler(result.error);
		}
		else {
			chat.find('.discord-fehler').remove();
		}

		var signatur = $.map(result.messages, function(nachricht) { return nachricht.id; }).join(',');
		if( signatur === this.__signatur ) {
			return;
		}
		if( result.messages.length === 0 && result.error && this.__signatur !== null ) {
			// Bei einem Fehler die bisherigen Nachrichten stehen lassen
			return;
		}
		this.__signatur = signatur;

		var liste = chat.find('.discord-nachrichten');
		var amEnde = zumEnde || liste.scrollTop() + liste.innerHeight() >= liste[0].scrollHeight - 30;

		liste.empty();
		if( result.messages.length === 0 && !result.error ) {
			liste.append($('<div class="discord-hinweis"></div>').text('Noch keine Nachrichten in diesem Kanal.'));
		}
		$.each(result.messages, function(index, nachricht) {
			liste.append(DiscordChat.__erzeugeNachricht(nachricht));
		});

		if( amEnde ) {
			liste.scrollTop(liste[0].scrollHeight);
		}
	},

	__erzeugeNachricht : function(nachricht) {
		var element = $('<div class="discord-nachricht"></div>');
		if( nachricht.ingame ) {
			element.addClass('discord-ingame');
		}

		$('<img class="discord-avatar" alt="" />').attr('src', nachricht.avatarUrl).appendTo(element);

		var kopf = $('<div class="discord-kopf"></div>').appendTo(element);
		$('<span class="discord-autor"></span>').text(nachricht.author).appendTo(kopf);
		$('<span class="discord-zeit"></span>').text(this.__formatiereZeit(nachricht.timestamp)).appendTo(kopf);

		var text = $('<div class="discord-text"></div>').appendTo(element);
		this.__fuegeTextMitLinksEin(text, nachricht.text);

		$.each(nachricht.attachments || [], function(index, anhang) {
			if( !/^https:\/\//.test(anhang.url) ) {
				return;
			}
			$('<a class="discord-anhang" target="_blank" rel="noopener noreferrer"></a>')
				.attr('href', anhang.url)
				.text('[Anhang] ' + anhang.name)
				.appendTo(text);
		});

		return element;
	},

	__fuegeTextMitLinksEin : function(ziel, text) {
		var muster = /https?:\/\/[^\s<>"]+/g;
		var position = 0;
		var treffer;
		while( (treffer = muster.exec(text)) !== null ) {
			ziel.append(document.createTextNode(text.substring(position, treffer.index)));
			$('<a target="_blank" rel="noopener noreferrer"></a>')
				.attr('href', treffer[0])
				.text(treffer[0])
				.appendTo(ziel);
			position = treffer.index + treffer[0].length;
		}
		ziel.append(document.createTextNode(text.substring(position)));
	},

	__formatiereZeit : function(timestamp) {
		var datum = new Date(timestamp);
		if( isNaN(datum.getTime()) ) {
			return '';
		}
		var zweistellig = function(zahl) { return (zahl < 10 ? '0' : '') + zahl; };
		var zeit = zweistellig(datum.getHours()) + ':' + zweistellig(datum.getMinutes());
		if( datum.toDateString() === new Date().toDateString() ) {
			return zeit;
		}
		return zweistellig(datum.getDate()) + '.' + zweistellig(datum.getMonth() + 1) + '. ' + zeit;
	}
};

$(document).ready(function() {
	$('#ueber-box-umschalter button').on('click', function() {
		UeberBox.zeige($(this).attr('data-box'));
	});

	DiscordChat.init();
});
