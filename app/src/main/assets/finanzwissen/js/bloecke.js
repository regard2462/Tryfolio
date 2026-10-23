/* ============================================================
   bloecke.js - setzt die Bausteine einer Lektion in DOM um.
   Vertrag fuer Inhalte: jeder Block ist {t:"<art>", ...}
   ============================================================ */
(function () {
  'use strict';

  var el = K.el;

  /* Tabellen, die nicht in die Breite passen, werden zu Karten: je Zeile ein Kasten mit
     Spaltenkopf und Wert. Gemessen wird, sobald die Tabelle sichtbar ist und wenn sich die Breite aendert. */
  function breiteBeobachten(h) {
    var letzte = -1;
    function pruefen() {
      var w = h.clientWidth;
      if (!w || w === letzte) return;
      letzte = w;
      h.classList.remove('stapel');
      if (h.scrollWidth > w + 2) h.classList.add('stapel');
    }
    if (typeof ResizeObserver === 'function') new ResizeObserver(pruefen).observe(h);
    else if (typeof requestAnimationFrame === 'function') requestAnimationFrame(pruefen);
  }

  /* Zahlenzellen stehen rechtsbuendig. ZAHL erkennt die deutsche Schreibweise ("1.234,5 €", "-3,2 %",
     "4 p. a.") und damit auch Prozent ohne Leerzeichen ("5.5%"); ZAHL_EN erkennt englische Betraege mit
     Waehrungszeichen vor der Zahl ("€1,234", "-€50", "$2.5bn", "€1.2m"). Nur die Ausrichtung haengt daran. */
  var ZAHL = /^[-+()]?[\d.,\s]+( ?[%€$]| ?p\. ?a\.)?$/;
  var ZAHL_EN = /^[-+()]?[€$£][\d.,]*\d(k|m|bn|tn)?$/;
  function istZahlZelle(c) {
    var s = String(c);
    return ZAHL.test(s) || ZAHL_EN.test(s);
  }

  var bauer = {

    /* Fliesstext mit Mini-Markdown */
    text: function (b) {
      return el('div', { class: 'inhalt', html: Mark.block(b.md || b.text || '') });
    },

    ueberschrift: function (b) {
      return el('div', { class: 'inhalt', html: '<h2>' + Mark.inline(b.text || '') + '</h2>' });
    },

    /* farbige Kaesten */
    merk: function (b) { return kasten(b, 'merk', b.titel || K.t('Merksatz')); },
    warn: function (b) { return kasten(b, 'warn', b.titel || K.t('Vorsicht')); },
    bsp: function (b) { return kasten(b, 'bsp', b.titel || K.t('Beispiel')); },
    tipp: function (b) { return kasten(b, 'tipp', b.titel || K.t('Praxis')); },
    info: function (b) { return kasten(b, 'info', b.titel || K.t('Hintergrund')); },

    formel: function (b) {
      var d = el('div', { class: 'formel' });
      d.appendChild(el('div', { class: 'f-haupt', text: b.formel || b.f || '' }));
      if (b.erklaerung) d.appendChild(el('div', { class: 'f-erk', html: Mark.inline(b.erklaerung) }));
      if (b.variablen && b.variablen.length) {
        var v = b.variablen.map(function (x) {
          return '<b>' + K.htmlEsc(x[0]) + '</b> ' + K.htmlEsc(x[1]);
        }).join(' &nbsp;·&nbsp; ');
        d.appendChild(el('div', { class: 'f-var', html: v }));
      }
      if (b.beispiel) d.appendChild(el('div', { class: 'f-erk', html: '<b>' + K.htmlEsc(K.t('Beispiel:')) + '</b> ' + Mark.inline(b.beispiel) }));
      return d;
    },

    tabelle: function (b) {
      var h = el('div', { class: 'tab-huelle' });
      var spalten = Math.max((b.kopf || []).length, Math.max.apply(null, (b.zeilen || [[]]).map(function (z) { return z.length; })));
      var t = el('table', { class: 'daten' + (spalten >= 4 ? ' eng' : '') });
      var zs = (b.zahlSpalten || []);
      if (b.kopf) {
        var tr = el('tr');
        b.kopf.forEach(function (k) { tr.appendChild(el('th', { text: k })); });
        t.appendChild(el('thead', null, tr));
      }
      var tb = el('tbody');
      (b.zeilen || []).forEach(function (z) {
        var tr = el('tr');
        z.forEach(function (c, i) {
          var istZahl = zs.indexOf(i) >= 0 || (zs.length === 0 && i > 0 && istZahlZelle(c));
          var td = el('td', { class: istZahl ? 'z' : '' });
          // Spaltenkopf fuer die gestapelte Ansicht auf schmalen Bildschirmen
          if (b.kopf && b.kopf[i] !== undefined) td.setAttribute('data-k', b.kopf[i]);
          td.appendChild(el('span', { html: Mark.inline(c) }));
          tr.appendChild(td);
        });
        tb.appendChild(tr);
      });
      t.appendChild(tb);
      h.appendChild(t);
      breiteBeobachten(h);
      if (b.titel) {
        var w = el('div');
        w.appendChild(el('div', { class: 'viz-titel', text: b.titel }));
        w.appendChild(h);
        return w;
      }
      return h;
    },

    vergleich: function (b) {
      var d = el('div', { class: 'vergleich' });
      [['v-l', b.links], ['v-r', b.rechts]].forEach(function (p) {
        var s = p[1] || {};
        var box = el('div', { class: p[0] });
        box.appendChild(el('h4', { text: s.titel || '' }));
        var ul = el('ul');
        (s.punkte || []).forEach(function (x) { ul.appendChild(el('li', { html: Mark.inline(x) })); });
        box.appendChild(ul);
        d.appendChild(box);
      });
      return d;
    },

    schritte: function (b) {
      var d = el('div', { class: 'schritte' });
      (b.schritte || []).forEach(function (s, i) {
        var z = el('div', { class: 'schritt' });
        z.appendChild(el('div', { class: 'schritt-nr', text: String(i + 1) }));
        var inn = el('div');
        if (s.titel) inn.appendChild(el('b', { html: Mark.inline(s.titel) }));
        inn.appendChild(el('div', { html: Mark.inline(s.text || '') }));
        z.appendChild(inn);
        d.appendChild(z);
      });
      return d;
    },

    zitat: function (b) {
      var d = el('blockquote', { class: 'zitat', html: Mark.inline(b.text || '') });
      if (b.quelle) d.appendChild(el('cite', { text: b.quelle }));
      return d;
    },

    begriffe: function (b) {
      var d = el('div', { class: 'karte' });
      if (b.titel) d.appendChild(el('div', { class: 'karte-titel', text: b.titel }));
      (b.eintraege || []).forEach(function (x) {
        var z = el('div', { class: 'glossar-eintrag' });
        z.appendChild(el('b', { text: x.begriff }));
        z.appendChild(el('p', { html: Mark.inline(x.text) }));
        d.appendChild(z);
      });
      return d;
    },

    aufklapp: function (b) {
      var d = el('details', { class: 'karte' });
      d.style.padding = '12px 14px';
      var s = el('summary', { text: b.titel || K.t('Mehr dazu') });
      s.style.cssText = 'font-weight:600;cursor:pointer;color:var(--text)';
      d.appendChild(s);
      d.appendChild(el('div', { class: 'inhalt', html: Mark.block(b.md || '') , style:'margin-top:10px'}));
      return d;
    },

    viz: function (b) {
      var spez = {};
      for (var k in b) if (k !== 't') spez[k] = b[k];
      if (!spez.art && b.viz) spez.art = b.viz;
      return Viz.zeichne(spez);
    },

    werkzeug: function (b) {
      var w = window.Werkzeuge && window.Werkzeuge.liste[b.id];
      if (!w) return el('div', { class: 'still', text: K.t('Werkzeug nicht gefunden: {0}', String(b.id)) });
      var d = el('div', { class: 'werk' });
      d.appendChild(el('div', { class: 'karte-titel', text: w.titel }));
      if (w.unter) d.appendChild(el('div', { class: 'still', text: w.unter, style: 'margin:2px 0 12px' }));
      var koerper = el('div');
      d.appendChild(koerper);
      try { w.bauen(koerper, b.vorgabe || {}); }
      catch (e) { console.warn(e); koerper.appendChild(el('div', { class: 'still', text: K.t('Werkzeug konnte nicht geladen werden.') })); }
      return d;
    }
  };

  function kasten(b, art, titel) {
    var d = el('div', { class: 'kasten ' + art });
    if (titel !== false) d.appendChild(el('div', { class: 'kasten-titel', text: titel }));
    d.appendChild(el('div', { html: Mark.block(b.md || b.text || '') }));
    return d;
  }

  function bauen(bloecke) {
    var frag = document.createDocumentFragment();
    (bloecke || []).forEach(function (b) {
      if (!b || !b.t) return;
      var fn = bauer[b.t];
      if (!fn) {
        // unbekannte Art: als Text retten, damit nie Inhalt verloren geht
        if (b.md || b.text) frag.appendChild(bauer.text(b));
        return;
      }
      try { frag.appendChild(fn(b)); }
      catch (e) { console.warn('Block-Fehler', b.t, e); }
    });
    return frag;
  }

  window.Bloecke = { bauen: bauen, arten: Object.keys(bauer) };
})();
