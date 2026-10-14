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
	__folgeEnde : true,

	init : function() {
		var chat = $('#discord-chat');
		if( chat.length === 0 ) {
			return;
		}

		chat.find('.discord-kanal').on('click', function() {
			DiscordChat.wechsleKanal($(this));
		});
		chat.find('.discord-nachrichten').on('scroll', function() {
			DiscordChat.__folgeEnde = this.scrollTop + this.clientHeight >= this.scrollHeight - 30;
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

		this.__aktualisiereEingabe();
		this.laden();
	},

	/**
	 * Sperrt die Eingabe in Kanaelen, in die der Spieler nur lesen darf.
	 * Das ist nur Komfort - der Server prueft das Schreibrecht bei jedem Senden selbst.
	 */
	__aktualisiereEingabe : function() {
		var chat = $('#discord-chat');
		var kanal = chat.find('.discord-kanal.aktiv');
		var darfSchreiben = kanal.attr('data-schreiben') === '1';
		var form = chat.find('form.discord-eingabe');
		form.find('input').prop('disabled', !darfSchreiben);
		form.find('input[name=text]').attr('placeholder', darfSchreiben ?
			'Nachricht an #' + kanal.attr('data-name') :
			'In #' + kanal.attr('data-name') + ' darfst du nur mitlesen');
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
		this.__aktualisiereEingabe();

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
				DiscordChat.__aktualisiereEingabe();
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

		if( result.channel === this.__kanal() ) {
			// Rechte koennen sich geaendert haben, seit die Seite geladen wurde
			chat.find('.discord-kanal.aktiv').attr('data-schreiben', result.schreiben ? '1' : '0');
			this.__aktualisiereEingabe();
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
		this.__folgeEnde = amEnde;

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
			var link = $('<a class="discord-anhang" target="_blank" rel="noopener noreferrer"></a>').attr('href', anhang.url);
			if( anhang.art === 'bild' || anhang.art === 'video' ) {
				var medium = anhang.art === 'bild' ?
					$('<img class="discord-medium" alt="" />').attr('alt', anhang.name || '') :
					$('<video class="discord-medium" autoplay loop muted playsinline></video>');
				DiscordChat.__setzeGroesse(medium, anhang);
				// Ohne bekannte Groesse verschiebt das Laden den Inhalt - dann ggf. wieder ans Ende scrollen
				medium.on('load loadeddata', function() {
					if( DiscordChat.__folgeEnde ) {
						var liste = $('#discord-chat .discord-nachrichten');
						liste.scrollTop(liste[0].scrollHeight);
					}
				});
				medium.attr('src', anhang.url);
				link.append(medium).appendTo(text);
			}
			else {
				link.text('[Anhang] ' + anhang.name).appendTo(text);
			}
		});

		return element;
	},

	MAX_BREITE : 320,
	MAX_HOEHE : 220,

	__setzeGroesse : function(medium, anhang) {
		if( !anhang.breite || !anhang.hoehe ) {
			return;
		}
		var faktor = Math.min(1, this.MAX_BREITE / anhang.breite, this.MAX_HOEHE / anhang.hoehe);
		medium.attr('width', Math.round(anhang.breite * faktor));
		medium.attr('height', Math.round(anhang.hoehe * faktor));
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
