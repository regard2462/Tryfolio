/* ============================================================
   kern.js - Zustand, Speicher, Formatierung, kleine Helfer
   ============================================================ */
(function () {
  'use strict';

  var K = {};
  window.K = K;

  /* ---------- Sprache ----------
     Die Depot-App gibt die Sprache als URL-Parameter mit. Englisch laedt eigene Datendateien
     (data/en/...) und nimmt die Oberflaechentexte aus window.TEXTE_EN (js/texte-en.js).
     Schluessel ist jeweils der deutsche Text; {0}, {1} ... sind Platzhalter fuer Werte. */
  K.lang = /(^|[?&])lang=en(&|$)/.test(String(location.search || '').replace(/^\?/, '')) ? 'en' : 'de';
  K.en = K.lang === 'en';
  K.fehlend = {};   // englische Texte, die beim Aufruf fehlten (nur zum Pruefen)

  K.t = function (s) {
    var r = s;
    if (K.en) {
      var tab = window.TEXTE_EN || {};
      if (Object.prototype.hasOwnProperty.call(tab, s)) r = tab[s];
      else K.fehlend[s] = (K.fehlend[s] || 0) + 1;
    }
    if (arguments.length < 2) return r;
    var a = arguments;
    return String(r).replace(/\{(\d+)\}/g, function (m, i) {
      var v = a[parseInt(i, 10) + 1];
      return v === undefined ? m : String(v);
    });
  };

  /** Pfad einer Datendatei in der gewaehlten Sprache: "kurs.json" -> "data/kurs.json" oder "data/en/kurs.json". */
  K.daten = function (datei) { return 'data/' + (K.en ? 'en/' : '') + datei; };

  /* ---------- Region ----------
     Die Depot-App gibt das Land des Nutzers mit (region=us). Wo deutsche Regeln dort nicht gelten (Steuern,
     Altersvorsorge, Kredite), gibt es angepasste Lektionen unter data/<region>/lektionen bzw.
     data/en/<region>/lektionen; data/regional.json sagt, welche es sind und was angepasst ist. Jede angepasste
     Lektion sagt das oben und zeigt auf Wunsch die deutsche Fassung (gemerkt je Lektion). */
  K.region = (String(location.search || '').match(/(^|[?&])region=([a-z]{2})(&|$)/) || [])[2] || '';

  /** Eintrag aus data/regional.json fuer die Region der App, sonst null. */
  K.regional = function () {
    var r = window.REGIONAL;
    return (K.region && r && r[K.region]) || null;
  };

  /** Angaben zur angepassten Fassung einer Lektion in der Sprache ({titel, hinweis}), sonst null. */
  K.angepasst = function (id) {
    var r = K.regional();
    var e = r && r.lektionen && r.lektionen[id];
    return e ? (e[K.lang] || e.de || null) : null;
  };

  /** Die deutsche Fassung statt der angepassten lesen (je Lektion gemerkt). */
  K.original = function (id) { return !!(K.st.original && K.st.original[id]); };
  K.originalSetzen = function (id, an) {
    if (!K.st.original) K.st.original = {};
    if (an) K.st.original[id] = true; else delete K.st.original[id];
    K.sichern();
  };

  /** Pfad der Lektion: die angepasste Fassung der Region, wenn es eine gibt und sie nicht abgewaehlt ist. */
  K.lektionPfad = function (id) {
    return K.daten((K.angepasst(id) && !K.original(id) ? K.region + '/' : '') + 'lektionen/' + id + '.json');
  };

  /** Titel von Themen und Lektionen im Kursverzeichnis durch die angepassten ersetzen (einmal nach dem Laden). */
  K.regionAnwenden = function () {
    var r = K.regional();
    if (!r || !window.KURS) return;
    (KURS.module || []).forEach(function (m) {
      var mm = r.module && r.module[m.id];
      var t = mm && (mm[K.lang] || mm.de);
      if (t) ['titel', 'unter', 'beschreibung'].forEach(function (k) { if (t[k]) { m[k + 'Original'] = m[k]; m[k] = t[k]; } });
      (m.lektionen || []).forEach(function (l) {
        var a = K.angepasst(l.id);
        if (a && a.titel) { l.titelOriginal = l.titel; l.titel = a.titel; }
        if (a) l.angepasst = true;
      });
    });
  };

  /* ---------- Speicher ---------- */
  var SCHLUESSEL = 'finanzwissen.v1';
  var leer = {
    fertig: {},        // lektionId -> Zeitstempel
    fragen: {},        // lektionId -> {richtig:n, gesamt:n}
    notizen: {},       // lektionId -> Text
    lesezeichen: {},   // lektionId -> true
    zuletzt: null,     // lektionId
    verlauf: [],       // [{id, t}]
    streak: { tage: 0, letzterTag: null, beste: 0 },
    minuten: 0,
    theme: 'dunkel',
    schrift: 0,        // -1,0,1,2
    werkzeugStand: {}, // werkzeugId -> eingaben
    pruefungen: [],    // {t, richtig, gesamt, bereich}
    gesehen: {}        // einmalige Hinweise
  };

  function laden() {
    try {
      var roh = localStorage.getItem(SCHLUESSEL);
      if (!roh) return JSON.parse(JSON.stringify(leer));
      var d = JSON.parse(roh);
      for (var k in leer) if (!(k in d)) d[k] = JSON.parse(JSON.stringify(leer[k]));
      return d;
    } catch (e) {
      return JSON.parse(JSON.stringify(leer));
    }
  }

  K.st = laden();

  var speicherTimer = null;
  K.sichern = function () {
    clearTimeout(speicherTimer);
    speicherTimer = setTimeout(function () {
      try { localStorage.setItem(SCHLUESSEL, JSON.stringify(K.st)); } catch (e) {}
    }, 120);
  };

  K.zuruecksetzen = function () {
    K.st = JSON.parse(JSON.stringify(leer));
    try { localStorage.removeItem(SCHLUESSEL); } catch (e) {}
  };

  /* ---------- Datum / Streak ---------- */
  K.heuteStr = function () {
    var d = new Date();
    return d.getFullYear() + '-' + ('0' + (d.getMonth() + 1)).slice(-2) + '-' + ('0' + d.getDate()).slice(-2);
  };

  K.streakPflegen = function () {
    var h = K.heuteStr(), s = K.st.streak;
    if (s.letzterTag === h) return;
    var g = new Date(); g.setDate(g.getDate() - 1);
    var gestern = g.getFullYear() + '-' + ('0' + (g.getMonth() + 1)).slice(-2) + '-' + ('0' + g.getDate()).slice(-2);
    s.tage = (s.letzterTag === gestern) ? s.tage + 1 : 1;
    s.letzterTag = h;
    if (s.tage > (s.beste || 0)) s.beste = s.tage;
    K.sichern();
  };

  /* ---------- Formatierung ---------- */
  // Deutsch "1.234,56", Englisch (britisch) "1,234.56"
  var nf = function (min, max) {
    try { return new Intl.NumberFormat(K.en ? 'en-GB' : 'de-DE', { minimumFractionDigits: min, maximumFractionDigits: max }); }
    catch (e) { return { format: function (x) { return String(Math.round(x * 100) / 100); } }; }
  };
  var f0 = nf(0, 0), f1 = nf(1, 1), f2 = nf(2, 2);

  K.z = function (x, n) {
    if (x === null || x === undefined || isNaN(x)) return '–';
    if (n === 0) return f0.format(x);
    if (n === 1) return f1.format(x);
    if (n === 2) return f2.format(x);
    return nf(n, n).format(x);
  };

  /** Betrag mit Waehrungszeichen: Deutsch "1.234 €", Englisch "€1,234" (Minus vor dem Zeichen). */
  function mitWaehrung(x, n, zeichen, nach) {
    if (!K.en) return K.z(x, n) + ' ' + zeichen + (nach ? ' ' + nach : '');
    return (x < 0 ? '-' : '') + zeichen + K.z(Math.abs(x), n) + (nach || '');
  }

  K.eur = function (x, n) {
    if (x === null || x === undefined || isNaN(x)) return '–';
    var abs = Math.abs(x);
    if (n === undefined) n = abs >= 1000 ? 0 : 2;
    return mitWaehrung(x, n, '€');
  };

  K.eurKurz = function (x) {
    var a = Math.abs(x);
    if (K.en) {
      if (a >= 1e9) return mitWaehrung(x / 1e9, 2, '€', 'bn');
      if (a >= 1e6) return mitWaehrung(x / 1e6, 2, '€', 'm');
      if (a >= 1e4) return mitWaehrung(Math.round(x / 1000), 0, '€', 'k');
      return K.eur(x, 0);
    }
    if (a >= 1e9) return K.z(x / 1e9, 2) + ' Mrd. €';
    if (a >= 1e6) return K.z(x / 1e6, 2) + ' Mio. €';
    if (a >= 1e4) return K.z(Math.round(x / 1000), 0) + ' Tsd. €';
    return K.eur(x, 0);
  };

  K.pro = function (x, n) {
    if (x === null || x === undefined || isNaN(x)) return '–';
    return K.z(x, n === undefined ? 1 : n) + (K.en ? '%' : ' %');
  };

  /**
   * Zahl (schon formatiert) mit Einheit. Deutsch haengt die Einheit unveraendert an. Englisch
   * uebersetzt die Einheit ueber TEXTE_EN, setzt Waehrungszeichen vor die Zahl (" €" -> "€1,234",
   * " €m" -> "€1.2m") und schreibt Prozent ohne Leerzeichen ("5.5%").
   */
  K.mitEinheit = function (zahlText, einheit) {
    einheit = einheit || '';
    if (!K.en || !einheit) return zahlText + einheit;
    var tab = window.TEXTE_EN || {};
    var e = Object.prototype.hasOwnProperty.call(tab, einheit) ? tab[einheit] : einheit;
    var roh = String(e).trim();
    var m = roh.match(/^([€$£])(.*)$/);
    if (m) {
      var neg = /^[-−]/.test(zahlText);
      return (neg ? '-' : '') + m[1] + String(zahlText).replace(/^[-−]/, '') + m[2];
    }
    if (roh === '%') return zahlText + '%';
    return zahlText + e;
  };

  K.vorz = function (x, n) {
    return (x > 0 ? '+' : '') + K.z(x, n === undefined ? 1 : n);
  };

  /* ---------- DOM ---------- */
  K.el = function (tag, attr, kinder) {
    var e = document.createElement(tag);
    if (attr) for (var a in attr) {
      if (a === 'class') e.className = attr[a];
      else if (a === 'html') e.innerHTML = attr[a];
      else if (a === 'text') e.textContent = attr[a];
      else if (a === 'auf') e.addEventListener('click', attr[a]);
      else if (a.slice(0, 2) === 'on') e.addEventListener(a.slice(2), attr[a]);
      else if (attr[a] !== null && attr[a] !== undefined && attr[a] !== false) e.setAttribute(a, attr[a]);
    }
    if (kinder) {
      if (!Array.isArray(kinder)) kinder = [kinder];
      kinder.forEach(function (k) {
        if (k === null || k === undefined || k === false) return;
        e.appendChild(typeof k === 'string' ? document.createTextNode(k) : k);
      });
    }
    return e;
  };

  K.htmlEsc = function (s) {
    return String(s === undefined || s === null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  };

  K.hinweis = function (text, ms) {
    var n = document.getElementById('hinweis');
    n.textContent = text;
    n.hidden = false;
    clearTimeout(n._t);
    n._t = setTimeout(function () { n.hidden = true; }, ms || 2200);
  };

  /* ---------- Blatt (Overlay) ---------- */
  K.blattOffen = false;
  K.blatt = function (knoten) {
    var b = document.getElementById('blatt'), i = document.getElementById('blattInhalt');
    i.innerHTML = '';
    if (typeof knoten === 'string') i.innerHTML = knoten; else i.appendChild(knoten);
    b.hidden = false;
    K.blattOffen = true;
    i.scrollTop = 0;
  };
  K.blattZu = function () {
    document.getElementById('blatt').hidden = true;
    K.blattOffen = false;
  };

  /* ---------- Mathe ---------- */
  K.summe = function (a) { return a.reduce(function (s, x) { return s + x; }, 0); };
  K.mittel = function (a) { return a.length ? K.summe(a) / a.length : 0; };
  K.stdabw = function (a) {
    if (a.length < 2) return 0;
    var m = K.mittel(a);
    return Math.sqrt(a.reduce(function (s, x) { return s + (x - m) * (x - m); }, 0) / (a.length - 1));
  };
  K.median = function (a) {
    if (!a.length) return 0;
    var b = a.slice().sort(function (x, y) { return x - y; });
    var m = Math.floor(b.length / 2);
    return b.length % 2 ? b[m] : (b[m - 1] + b[m]) / 2;
  };
  K.grenz = function (x, a, b) { return Math.max(a, Math.min(b, x)); };

  /* deterministischer Zufall, damit Simulationen reproduzierbar bleiben */
  K.wuerfel = function (saat) {
    var s = saat >>> 0 || 1;
    return function () {
      s ^= s << 13; s >>>= 0;
      s ^= s >> 17;
      s ^= s << 5; s >>>= 0;
      return s / 4294967296;
    };
  };
  K.normal = function (rnd) {
    var u = 0, v = 0;
    while (u === 0) u = rnd();
    while (v === 0) v = rnd();
    return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
  };

  /* ---------- Daten laden ----------
     Die Ausgabe haengt an der URL: So kann der WebView-Zwischenspeicher
     aggressiv arbeiten, liefert nach einer App-Aktualisierung aber nie alte Inhalte. */
  K.AUSGABE = '3.5.0';
  var puffer = {};
  K.hole = function (pfad) {
    if (puffer[pfad]) return puffer[pfad];
    var url = pfad + (pfad.indexOf('?') >= 0 ? '&' : '?') + 'v=' + K.AUSGABE;
    puffer[pfad] = fetch(url, { cache: 'force-cache' }).then(function (r) {
      if (!r.ok) throw new Error(pfad + ' ' + r.status);
      return r.json();
    }).catch(function (e) {
      console.warn('Konnte nicht laden:', pfad, e);
      return null;
    });
    return puffer[pfad];
  };

  /* ---------- Fortschritt ---------- */
  K.lektionFertig = function (id) { return !!K.st.fertig[id]; };

  K.modulFortschritt = function (modul) {
    var ids = (modul.lektionen || []).map(function (l) { return l.id; });
    var f = ids.filter(function (i) { return K.lektionFertig(i); }).length;
    return { fertig: f, gesamt: ids.length, quote: ids.length ? f / ids.length : 0 };
  };

  K.gesamtFortschritt = function () {
    var g = 0, f = 0;
    (window.KURS && window.KURS.module || []).forEach(function (m) {
      var p = K.modulFortschritt(m); g += p.gesamt; f += p.fertig;
    });
    return { fertig: f, gesamt: g, quote: g ? f / g : 0 };
  };

  K.abhaken = function (id, minuten) {
    if (!K.st.fertig[id]) K.st.fertig[id] = Date.now();
    K.st.zuletzt = id;
    K.st.minuten += (minuten || 0);
    K.st.verlauf.unshift({ id: id, t: Date.now() });
    if (K.st.verlauf.length > 60) K.st.verlauf.length = 60;
    K.streakPflegen();
    K.sichern();
  };
})();
