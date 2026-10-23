/* ============================================================
   werkzeuge.js - interaktive Rechner.
   Jedes Werkzeug meldet sich mit Werkzeuge.registrieren({...}) an
   und bekommt eine Wurzel, in die es seine Bedienelemente baut.
   ============================================================ */
(function () {
  'use strict';
  var el = K.el;
  var W = { liste: {}, reihenfolge: [] };
  window.Werkzeuge = W;

  W.registrieren = function (w) {
    W.liste[w.id] = w;
    W.reihenfolge.push(w.id);
  };

  /* ---------------- Bedien-Bausteine ----------------
     Beschriftungen (label, text, Notizen) kommen schon uebersetzt vom Aufrufer (K.t).
     o.einheit ist die deutsche Einheit (' %', ' Jahre' ...); K.mitEinheit haengt sie an
     und setzt sie im Englischen um. */
  var ui = {};
  W.ui = ui;

  ui.schieber = function (wurzel, o, beiAenderung) {
    var f = el('div', { class: 'feld' });
    var lab = el('label');
    var name = el('span', { text: o.label });
    var wertA = el('b');
    lab.appendChild(name); lab.appendChild(wertA);
    var inp = el('input', {
      type: 'range', min: o.min, max: o.max, step: o.schritt || 1, value: o.wert
    });
    f.appendChild(lab); f.appendChild(inp);
    wurzel.appendChild(f);
    function zeigen() {
      var v = parseFloat(inp.value);
      // Englisch in der Einzahl "1 year"; die deutsche Ausgabe bleibt unveraendert
      var einheit = (K.en && v === 1 && o.einheit === ' Jahre') ? ' Jahr' : o.einheit;
      wertA.textContent = o.format ? o.format(v) : K.mitEinheit(K.z(v, o.nachkomma || 0), einheit);
    }
    inp.addEventListener('input', function () { zeigen(); beiAenderung && beiAenderung(); });
    zeigen();
    return { knoten: inp, wert: function () { return parseFloat(inp.value); }, setzen: function (v) { inp.value = v; zeigen(); } };
  };

  ui.zahl = function (wurzel, o, beiAenderung) {
    var f = el('div', { class: 'feld' });
    f.appendChild(el('label', null, [el('span', { text: o.label })]));
    var inp = el('input', { type: 'number', value: o.wert, step: o.schritt || 'any', inputmode: 'decimal' });
    f.appendChild(inp);
    wurzel.appendChild(f);
    inp.addEventListener('input', function () { beiAenderung && beiAenderung(); });
    return { knoten: inp, wert: function () { var v = parseFloat(inp.value); return isFinite(v) ? v : (o.wert || 0); }, setzen: function (v) { inp.value = v; } };
  };

  ui.auswahl = function (wurzel, o, beiAenderung) {
    var f = el('div', { class: 'feld' });
    f.appendChild(el('label', null, [el('span', { text: o.label })]));
    var s = el('select');
    (o.optionen || []).forEach(function (x) {
      var op = el('option', { value: x.wert });
      op.textContent = x.text;
      if (String(x.wert) === String(o.wert)) op.selected = true;
      s.appendChild(op);
    });
    f.appendChild(s); wurzel.appendChild(f);
    s.addEventListener('change', function () { beiAenderung && beiAenderung(); });
    return {
      knoten: s,
      wert: function () { var v = s.value; var n = parseFloat(v); return (String(n) === v) ? n : v; },
      text: function () { return s.options[s.selectedIndex] ? s.options[s.selectedIndex].textContent : ''; },
      setzen: function (v) { s.value = v; }
    };
  };

  ui.schalter = function (wurzel, o, beiAenderung) {
    var f = el('div', { class: 'feld' });
    if (o.label) f.appendChild(el('label', null, [el('span', { text: o.label })]));
    var reihe = el('div', { class: 'schalter' });
    var akt = o.wert;
    var knoepfe = [];
    (o.optionen || []).forEach(function (x) {
      var b = el('button', { text: x.text, type: 'button' });
      b.addEventListener('click', function () {
        akt = x.wert;
        knoepfe.forEach(function (k) { k.b.classList.toggle('an', k.w === akt); });
        beiAenderung && beiAenderung();
      });
      if (String(x.wert) === String(akt)) b.classList.add('an');
      knoepfe.push({ b: b, w: x.wert });
      reihe.appendChild(b);
    });
    f.appendChild(reihe); wurzel.appendChild(f);
    return { wert: function () { return akt; }, setzen: function (v) { akt = v; knoepfe.forEach(function (k) { k.b.classList.toggle('an', k.w === akt); }); } };
  };

  ui.ergebnis = function (wurzel) {
    var d = el('div', { class: 'ergebnis' });
    wurzel.appendChild(d);
    return {
      knoten: d,
      setzen: function (felder) {
        d.innerHTML = '';
        felder.forEach(function (f) {
          if (!f) return;
          var k = el('div', { class: 'erg' });
          k.appendChild(el('b', { class: f.farbe || '', text: f.wert }));
          k.appendChild(el('span', { text: f.label }));
          d.appendChild(k);
        });
      }
    };
  };

  ui.platz = function (wurzel) {
    var d = el('div');
    wurzel.appendChild(d);
    return {
      knoten: d,
      zeige: function (spez) { d.innerHTML = ''; if (spez) d.appendChild(Viz.zeichne(spez)); },
      html: function (h) { d.innerHTML = h; },
      leer: function () { d.innerHTML = ''; }
    };
  };

  ui.notiz = function (wurzel, text, art) {
    var d = el('div', { class: 'kasten ' + (art || 'info') });
    d.style.marginTop = '12px';
    d.appendChild(el('div', { html: Mark.block(text) }));
    wurzel.appendChild(d);
    return { setzen: function (t) { d.innerHTML = ''; d.appendChild(el('div', { html: Mark.block(t) })); } };
  };

  /* =========================================================
     1 - Zinseszins & Sparplan
     ========================================================= */
  W.registrieren({
    id: 'zinseszins', titel: K.t('Sparplan & Zinseszins'), kategorie: K.t('Sparen und Planen'),
    unter: K.t('Berechnet das Endvermögen aus regelmäßigem Sparen und den Anteil, den die Zinsen dazu beitragen.'),
    bauen: function (w, v) {
      var start = ui.schieber(w, { label: K.t('Startkapital'), min: 0, max: 100000, schritt: 500, wert: v.start !== undefined ? v.start : 1000, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var rate = ui.schieber(w, { label: K.t('monatliche Sparrate'), min: 0, max: 2000, schritt: 25, wert: v.rate !== undefined ? v.rate : 250, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var zins = ui.schieber(w, { label: K.t('Rendite pro Jahr'), min: 0, max: 12, schritt: 0.1, wert: v.zins !== undefined ? v.zins : 7, nachkomma: 1, einheit: ' %' }, rechnen);
      var jahre = ui.schieber(w, { label: K.t('Anlagedauer'), min: 1, max: 50, schritt: 1, wert: v.jahre || 30, einheit: ' Jahre' }, rechnen);
      var dyn = ui.schieber(w, { label: K.t('jährliche Erhöhung der Rate'), min: 0, max: 10, schritt: 0.5, wert: v.dyn || 0, nachkomma: 1, einheit: ' %' }, rechnen);
      var erg = ui.ergebnis(w);
      var bild = ui.platz(w);

      function rechnen() {
        var r = R.sparplanVerlauf(start.wert(), rate.wert(), zins.wert(), jahre.wert(), dyn.wert());
        var zinsanteil = r.endwert - r.eingezahlt;
        erg.setzen([
          { wert: K.eurKurz(r.endwert), label: K.t('Endvermögen'), farbe: 'gruen' },
          { wert: K.eurKurz(r.eingezahlt), label: K.t('davon eingezahlt') },
          { wert: K.eurKurz(zinsanteil), label: K.t('davon Zinsen'), farbe: 'blau' },
          { wert: K.pro(r.endwert ? zinsanteil / r.endwert * 100 : 0, 0), label: K.t('Zinsanteil') }
        ]);
        bild.zeige({
          art: 'flaeche', hoehe: 190, yEinheit: ' €',
          serien: [
            { name: K.t('eingezahlt'), werte: r.einzahlung, farbe: 'text-still', gestrichelt: true },
            { name: K.t('Depotwert'), werte: r.kurve, farbe: 'gold', gefuellt: true }
          ],
          unter: K.t('Der Abstand zwischen den beiden Linien entspricht den Zinsen einschließlich Zinseszinsen. Er wird von Jahr zu Jahr schneller größer.')
        });
      }
      rechnen();
    }
  });

  /* =========================================================
     2 - Sparziel: welche Rate brauche ich?
     ========================================================= */
  W.registrieren({
    id: 'sparziel', titel: K.t('Sparziel rückwärts rechnen'), kategorie: K.t('Sparen und Planen'),
    unter: K.t('Berechnet aus einem Zielbetrag die dafür nötige Monatsrate.'),
    bauen: function (w, v) {
      var ziel = ui.schieber(w, { label: K.t('Zielbetrag'), min: 5000, max: 2000000, schritt: 5000, wert: v.ziel || 500000, format: function (x) { return K.eurKurz(x); } }, rechnen);
      var start = ui.schieber(w, { label: K.t('schon vorhanden'), min: 0, max: 500000, schritt: 1000, wert: v.start || 0, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var zins = ui.schieber(w, { label: K.t('erwartete Rendite'), min: 0, max: 12, schritt: 0.1, wert: v.zins || 6, nachkomma: 1, einheit: ' %' }, rechnen);
      var jahre = ui.schieber(w, { label: K.t('Zeit bis zum Ziel'), min: 1, max: 50, schritt: 1, wert: v.jahre || 25, einheit: ' Jahre' }, rechnen);
      var erg = ui.ergebnis(w);
      var bild = ui.platz(w);
      function rechnen() {
        var rate = R.rateFuerZiel(ziel.wert(), start.wert(), zins.wert(), jahre.wert());
        var ein = start.wert() + rate * 12 * jahre.wert();
        erg.setzen([
          { wert: K.eur(rate, 0), label: K.t('nötig pro Monat'), farbe: 'gruen' },
          { wert: K.eurKurz(ein), label: K.t('Summe der Einzahlungen') },
          { wert: K.eurKurz(ziel.wert() - ein), label: K.t('Beitrag der Rendite'), farbe: 'blau' }
        ]);
        // Wie stark haengt die Rate an der Zeit?
        var pkt = [];
        for (var j = 5; j <= 45; j += 1) pkt.push([j, R.rateFuerZiel(ziel.wert(), start.wert(), zins.wert(), j)]);
        bild.zeige({
          art: 'linie', hoehe: 170, yEinheit: ' €', xEinheit: K.t(' J'),
          serien: [{ name: K.t('nötige Monatsrate'), werte: pkt, farbe: 'gold' }],
          marken: [{ x: jahre.wert(), text: K.t('du') }],
          unter: K.t('Die Zeit bis zum Ziel hat großen Einfluss auf die nötige Monatsrate. Bei einem Start zehn Jahre früher genügt oft die halbe Rate.')
        });
      }
      rechnen();
    }
  });

  /* =========================================================
     3 - Kaufkraft / Inflation
     ========================================================= */
  W.registrieren({
    id: 'inflation', titel: K.t('Kaufkraft und Inflation'), kategorie: K.t('Sparen und Planen'),
    unter: K.t('Berechnet die künftige Kaufkraft von Geld, das auf dem Konto liegt oder unverzinst aufbewahrt wird.'),
    bauen: function (w, v) {
      var betrag = ui.schieber(w, { label: K.t('Betrag heute'), min: 1000, max: 500000, schritt: 1000, wert: v.betrag || 50000, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var infl = ui.schieber(w, { label: K.t('Inflation pro Jahr'), min: 0, max: 10, schritt: 0.1, wert: v.infl || 2.5, nachkomma: 1, einheit: ' %' }, rechnen);
      var zins = ui.schieber(w, { label: K.t('Zins auf dem Konto'), min: 0, max: 8, schritt: 0.1, wert: v.zins || 1.5, nachkomma: 1, einheit: ' %' }, rechnen);
      var jahre = ui.schieber(w, { label: K.t('Zeitraum'), min: 1, max: 40, schritt: 1, wert: v.jahre || 20, einheit: ' Jahre' }, rechnen);
      var erg = ui.ergebnis(w);
      var bild = ui.platz(w);
      function rechnen() {
        var j = jahre.wert();
        var nominal = R.endwert(betrag.wert(), zins.wert(), j);
        var real = R.kaufkraft(nominal, infl.wert(), j);
        var ohneZins = R.kaufkraft(betrag.wert(), infl.wert(), j);
        var rz = R.realzins(zins.wert(), infl.wert());
        erg.setzen([
          { wert: K.eurKurz(nominal), label: K.t('Kontostand nominal') },
          { wert: K.eurKurz(real), label: K.t('reale Kaufkraft'), farbe: real < betrag.wert() ? 'rot' : 'gruen' },
          { wert: K.pro(rz, 2), label: K.t('Realzins'), farbe: rz < 0 ? 'rot' : 'gruen' },
          { wert: K.eurKurz(ohneZins), label: K.t('Kaufkraft ohne Zins'), farbe: 'rot' }
        ]);
        var a = [], b = [], c = [];
        for (var t = 0; t <= j; t++) {
          a.push([t, betrag.wert()]);
          b.push([t, R.kaufkraft(R.endwert(betrag.wert(), zins.wert(), t), infl.wert(), t)]);
          c.push([t, R.kaufkraft(betrag.wert(), infl.wert(), t)]);
        }
        bild.zeige({
          art: 'linie', hoehe: 180, yEinheit: ' €', xEinheit: K.t(' J'),
          serien: [
            { name: K.t('Nennbetrag'), werte: a, farbe: 'text-still', gestrichelt: true },
            { name: K.t('Kaufkraft mit Zins'), werte: b, farbe: 'gold' },
            { name: K.t('Kaufkraft ohne Zins'), werte: c, farbe: 'rot' }
          ],
          unter: K.t('Ein Kontoguthaben ist nur nominal sicher. Wie sich seine Kaufkraft entwickelt, hängt vom Abstand zwischen Zins und Inflation ab.')
        });
      }
      rechnen();
    }
  });

  /* =========================================================
     4 - Kostenfresser: TER und Gebuehren
     ========================================================= */
  W.registrieren({
    id: 'kosten', titel: K.t('Laufende Kosten im Vergleich'), kategorie: K.t('Kosten und Steuern'),
    unter: K.t('Berechnet, wie stark höhere laufende Kosten das Endvermögen eines Sparplans verringern.'),
    bauen: function (w, v) {
      var start = ui.schieber(w, { label: K.t('Startkapital'), min: 0, max: 200000, schritt: 1000, wert: v.start || 10000, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var rate = ui.schieber(w, { label: K.t('monatliche Sparrate'), min: 0, max: 1500, schritt: 25, wert: v.rate || 300, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var brutto = ui.schieber(w, { label: K.t('Rendite vor Kosten'), min: 1, max: 12, schritt: 0.1, wert: v.brutto || 7, nachkomma: 1, einheit: ' %' }, rechnen);
      var guenstig = ui.schieber(w, { label: K.t('Kosten ETF'), min: 0, max: 1, schritt: 0.01, wert: v.guenstig !== undefined ? v.guenstig : 0.2, nachkomma: 2, einheit: ' %' }, rechnen);
      var teuer = ui.schieber(w, { label: K.t('Kosten aktiver Fonds / Police'), min: 0, max: 3.5, schritt: 0.05, wert: v.teuer || 1.8, nachkomma: 2, einheit: ' %' }, rechnen);
      var jahre = ui.schieber(w, { label: K.t('Anlagedauer'), min: 5, max: 50, schritt: 1, wert: v.jahre || 30, einheit: ' Jahre' }, rechnen);
      var erg = ui.ergebnis(w);
      var bild = ui.platz(w);
      function rechnen() {
        var a = R.sparplanVerlauf(start.wert(), rate.wert(), brutto.wert() - guenstig.wert(), jahre.wert());
        var b = R.sparplanVerlauf(start.wert(), rate.wert(), brutto.wert() - teuer.wert(), jahre.wert());
        var diff = a.endwert - b.endwert;
        erg.setzen([
          { wert: K.eurKurz(a.endwert), label: K.t('mit {0} Kosten', K.pro(guenstig.wert(), 2)), farbe: 'gruen' },
          { wert: K.eurKurz(b.endwert), label: K.t('mit {0} Kosten', K.pro(teuer.wert(), 2)), farbe: 'rot' },
          { wert: K.eurKurz(diff), label: K.t('Unterschied') },
          { wert: K.pro(a.endwert ? diff / a.endwert * 100 : 0, 0), label: K.t('des Endvermögens') }
        ]);
        bild.zeige({
          art: 'flaeche', hoehe: 190, yEinheit: ' €', xEinheit: K.t(' J'),
          serien: [
            { name: K.t('günstig'), werte: a.kurve, farbe: 'gruen', gefuellt: true },
            { name: K.t('teuer'), werte: b.kurve, farbe: 'rot', gefuellt: true }
          ],
          unter: K.t('Die Differenz wächst exponentiell, weil jeder Euro, der nicht als Gebühr abfließt, im Depot bleibt und weiter Rendite bringt.')
        });
      }
      rechnen();
    }
  });

  /* =========================================================
     5 - Verlust und Erholung
     ========================================================= */
  W.registrieren({
    id: 'erholung', titel: K.t('Verlust und Erholung'), kategorie: K.t('Risiko'),
    unter: K.t('Berechnet den Gewinn, der einen Kursverlust ausgleicht, zum Beispiel ein Plus von 100 Prozent nach einem Minus von 50 Prozent.'),
    bauen: function (w, v) {
      var verlust = ui.schieber(w, { label: K.t('Kursverlust'), min: 5, max: 95, schritt: 1, wert: v.verlust || 50, einheit: ' %' }, rechnen);
      var rendite = ui.schieber(w, { label: K.t('Rendite danach pro Jahr'), min: 1, max: 15, schritt: 0.5, wert: v.rendite || 7, nachkomma: 1, einheit: ' %' }, rechnen);
      var erg = ui.ergebnis(w);
      var bild = ui.platz(w);
      function rechnen() {
        var noetig = R.erholung(-verlust.wert());
        var jahre = Math.log(1 / (1 - verlust.wert() / 100)) / Math.log(1 + rendite.wert() / 100);
        erg.setzen([
          { wert: K.pro(-verlust.wert(), 0), label: K.t('Verlust'), farbe: 'rot' },
          { wert: '+' + K.pro(noetig, 0), label: K.t('nötig zum Ausgleich'), farbe: 'gruen' },
          { wert: K.mitEinheit(K.z(jahre, 1), ' J.'), label: K.t('Dauer bei {0}', K.pro(rendite.wert(), 1)) }
        ]);
        var kat = [], serie = [];
        [10, 20, 30, 40, 50, 60, 70, 80, 90].forEach(function (x) {
          kat.push(K.mitEinheit('-' + x, ' %')); serie.push(R.erholung(-x));
        });
        bild.zeige({
          art: 'balken', hoehe: 190, yEinheit: ' %', kategorien: kat,
          serien: [{ name: K.t('nötiger Gewinn'), werte: serie, farbe: 'gruen' }],
          referenz: { wert: noetig, text: K.t('dein Fall') },
          unter: K.t('Der nötige Gewinn ist immer größer als der Verlust und steigt mit dessen Höhe immer steiler an. Verluste zu vermeiden, bringt deshalb rechnerisch mehr als das Streben nach möglichst hoher Rendite.')
        });
      }
      rechnen();
    }
  });

  /* =========================================================
     6 - Abgeltungsteuer
     ========================================================= */
  W.registrieren({
    id: 'abgeltungsteuer', titel: K.t('Steuer auf Kapitalerträge'), kategorie: K.t('Kosten und Steuern'),
    unter: K.t('Berechnet die Steuer auf Kapitalerträge mit Abgeltungsteuer, Soli, Kirchensteuer, Teilfreistellung und Pauschbetrag.'),
    bauen: function (w, v) {
      var ertrag = ui.schieber(w, { label: K.t('Gewinn / Ertrag'), min: 0, max: 100000, schritt: 250, wert: v.ertrag || 10000, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var art = ui.auswahl(w, {
        label: K.t('Art der Anlage'), wert: v.art || 30, optionen: [
          { wert: 30, text: K.t('Aktienfonds / Aktien-ETF (30 % Teilfreistellung)') },
          { wert: 15, text: K.t('Mischfonds ab 25 % Aktien (15 %)') },
          { wert: 60, text: K.t('Immobilienfonds Inland (60 %)') },
          { wert: 80, text: K.t('Immobilienfonds Ausland (80 %)') },
          { wert: 0, text: K.t('Einzelaktie, Anleihe, Zinsen (keine)') }
        ]
      }, rechnen);
      var pausch = ui.schalter(w, { label: K.t('Sparerpauschbetrag'), wert: v.pausch !== undefined ? v.pausch : 1000, optionen: [{ wert: 0, text: K.t('schon verbraucht') }, { wert: 1000, text: K.t('einzeln 1.000 €') }, { wert: 2000, text: K.t('zusammen 2.000 €') }] }, rechnen);
      var kirche = ui.schalter(w, { label: K.t('Kirchensteuer'), wert: v.kirche || 0, optionen: [{ wert: 0, text: K.t('keine') }, { wert: 8, text: K.t('8 % (BW, BY)') }, { wert: 9, text: K.t('9 % (übrige)') }] }, rechnen);
      var erg = ui.ergebnis(w);
      var bild = ui.platz(w);
      function rechnen() {
        var s = R.kapSteuer(ertrag.wert(), { teilfreistellung: art.wert(), kirche: kirche.wert(), pauschbetrag: pausch.wert() });
        erg.setzen([
          { wert: K.eur(s.netto, 0), label: K.t('bleibt übrig'), farbe: 'gruen' },
          { wert: K.eur(s.gesamt, 0), label: K.t('Steuer gesamt'), farbe: 'rot' },
          { wert: K.pro(ertrag.wert() ? s.gesamt / ertrag.wert() * 100 : 0, 1), label: K.t('effektive Belastung') }
        ]);
        bild.zeige({
          art: 'wasserfall', hoehe: 200,
          schritte: [
            { name: K.t('Ertrag'), wert: ertrag.wert() },
            { name: K.t('Teilfreistellung'), wert: -(ertrag.wert() * art.wert() / 100) },
            { name: K.t('Pauschbetrag'), wert: -Math.min(pausch.wert(), ertrag.wert() * (1 - art.wert() / 100)) },
            { name: K.t('Abgeltungsteuer'), wert: -s.abgeltung },
            { name: K.t('Soli'), wert: -s.soli },
            { name: K.t('Kirche'), wert: -s.kirche }
          ],
          unter: K.t('Teilfreistellung und Pauschbetrag senken nur die Bemessungsgrundlage, dieser Teil des Ertrags bleibt dir. An das Finanzamt fließen nur Abgeltungsteuer, Soli und Kirchensteuer.')
        });
      }
      rechnen();
    }
  });

  /* =========================================================
     7 - Entnahmeplan
     ========================================================= */
  W.registrieren({
    id: 'entnahme', titel: K.t('Entnahmeplan und 4-Prozent-Regel'), kategorie: K.t('Ruhestand'),
    unter: K.t('Simuliert, wie lange ein Vermögen bei regelmäßiger Entnahme reicht und wie sich schlechte Anfangsjahre auswirken.'),
    bauen: function (w, v) {
      var verm = ui.schieber(w, { label: K.t('Vermögen bei Start'), min: 50000, max: 3000000, schritt: 25000, wert: v.verm || 800000, format: function (x) { return K.eurKurz(x); } }, rechnen);
      var quote = ui.schieber(w, { label: K.t('Entnahme im ersten Jahr'), min: 2, max: 8, schritt: 0.1, wert: v.quote || 4, nachkomma: 1, einheit: ' %' }, rechnen);
      var rend = ui.schieber(w, { label: K.t('erwartete Rendite (nominal)'), min: 0, max: 10, schritt: 0.1, wert: v.rend || 6.5, nachkomma: 1, einheit: ' %' }, rechnen);
      var vola = ui.schieber(w, { label: K.t('Schwankung (Standardabweichung)'), min: 0, max: 30, schritt: 1, wert: v.vola || 13, einheit: ' %' }, rechnen);
      var infl = ui.schieber(w, { label: K.t('Inflation'), min: 0, max: 6, schritt: 0.1, wert: v.infl || 2, nachkomma: 1, einheit: ' %' }, rechnen);
      var jahre = ui.schieber(w, { label: K.t('Zeitraum'), min: 10, max: 50, schritt: 1, wert: v.jahre || 35, einheit: ' Jahre' }, rechnen);
      var erg = ui.ergebnis(w);
      var bild = ui.platz(w);
      function rechnen() {
        var J = jahre.wert(), start = verm.wert(), entn0 = start * quote.wert() / 100;
        var pfade = 400, rnd = K.wuerfel(4711), ende = [], bahnen = [];
        for (var p = 0; p < pfade; p++) {
          var v2 = start, e2 = entn0, bahn = [v2], tot = false;
          for (var j = 0; j < J; j++) {
            var r = rend.wert() / 100 + vola.wert() / 100 * K.normal(rnd);
            v2 = (v2 - e2) * (1 + r);
            if (v2 <= 0) { v2 = 0; tot = true; }
            e2 *= (1 + infl.wert() / 100);
            bahn.push(v2);
          }
          bahnen.push(bahn); ende.push(v2);
        }
        function perz(q, idx) {
          var sp = bahnen.map(function (b) { return b[idx]; }).sort(function (a, b) { return a - b; });
          return sp[Math.floor((sp.length - 1) * q)];
        }
        var x = [], p10 = [], p50 = [], p90 = [];
        for (var i = 0; i <= J; i++) { x.push(i); p10.push(perz(0.1, i)); p50.push(perz(0.5, i)); p90.push(perz(0.9, i)); }
        var pleite = ende.filter(function (e) { return e <= 0; }).length / pfade * 100;
        erg.setzen([
          { wert: K.eur(entn0 / 12, 0), label: K.t('pro Monat zu Beginn'), farbe: 'gruen' },
          { wert: K.pro(100 - pleite, 0), label: K.t('Erfolgsquote'), farbe: pleite > 10 ? 'rot' : 'gruen' },
          { wert: K.eurKurz(p50[J]), label: K.t('Median am Ende') },
          { wert: K.eurKurz(p10[J]), label: K.t('schlechte Fälle'), farbe: 'rot' }
        ]);
        bild.zeige({
          art: 'faecher', hoehe: 200, x: x, yEinheit: ' €',
          baender: [{ unten: p10, oben: p90, farbe: 'blau', opazitaet: 0.18 }],
          linien: [{ name: K.t('Median'), werte: p50, farbe: 'gold' }],
          unter: K.t('Das blaue Band umfasst 80 Prozent der simulierten Verläufe. Wie lange das Vermögen reicht, hängt stärker von der Reihenfolge der Jahre ab als von der Durchschnittsrendite.')
        });
      }
      rechnen();
    },
    hinweis: K.t('Die Simulation zieht 400 Zufallspfade mit unabhängigen, normalverteilten Jahresrenditen und einer starren Entnahme, die nur an die Inflation angepasst wird. Beide Annahmen sind streng gewählt. Echte Märkte kehren nach Extremjahren eher zum Mittelwert zurück, und niemand entnimmt unverändert weiter, wenn das Depot nur noch halb so viel wert ist. Die klassische Trinity-Studie kommt deshalb für 4 Prozent auf deutlich höhere Erfolgsquoten. Sie rechnet mit historischen US-Renditefolgen statt mit Zufallszahlen. Wird die Entnahme nach schlechten Jahren gekürzt, steigt die Erfolgsquote; wie stark, hängt von der Kürzungsregel ab.')
  });

  /* =========================================================
     8 - Positionsgroesse
     ========================================================= */
  W.registrieren({
    id: 'position', titel: K.t('Positionsgröße und Risiko'), kategorie: K.t('Risiko'),
    unter: K.t('Berechnet die Stückzahl einer Position aus dem Verlust, der bis zum Stopkurs höchstens entstehen darf.'),
    bauen: function (w, v) {
      var kap = ui.schieber(w, { label: K.t('Depotgröße'), min: 1000, max: 500000, schritt: 1000, wert: v.kap || 50000, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var risiko = ui.schieber(w, { label: K.t('Risiko je Position'), min: 0.1, max: 5, schritt: 0.1, wert: v.risiko || 1, nachkomma: 1, einheit: ' %' }, rechnen);
      var ein = ui.zahl(w, { label: K.t('Einstiegskurs (€)'), wert: v.ein || 100 }, rechnen);
      var stop = ui.zahl(w, { label: K.t('Stopkurs (€)'), wert: v.stop || 92 }, rechnen);
      var erg = ui.ergebnis(w);
      var bild = ui.platz(w);
      function rechnen() {
        var p = R.positionsgroesse(kap.wert(), risiko.wert(), ein.wert(), stop.wert());
        erg.setzen([
          { wert: K.z(p.stueck, 0), label: K.t('Stück') },
          { wert: K.eur(p.kapitaleinsatz, 0), label: K.t('Kapitaleinsatz') },
          { wert: K.eur(p.risikoEuro, 0), label: K.t('Verlust am Stop'), farbe: 'rot' },
          { wert: K.pro(p.anteil, 1), label: K.t('Anteil am Depot') }
        ]);
        // Wie oft kann man hintereinander verlieren?
        var reihe = [], v2 = 100;
        for (var i = 0; i <= 20; i++) { reihe.push([i, v2]); v2 *= (1 - risiko.wert() / 100); }
        var nach20 = reihe[20][1];   // Depot nach genau zwanzig Verlusten
        bild.zeige({
          art: 'linie', hoehe: 170, yEinheit: ' %', xEinheit: '',
          serien: [{ name: K.t('Depot nach n Fehltrades'), werte: reihe, farbe: 'rot', gefuellt: true }],
          unter: K.t('Zwanzig Verluste in Folge sind selten, aber möglich. Bei {0} Risiko je Trade bleiben danach noch {1} des Depots übrig.', K.pro(risiko.wert(), 1), K.pro(nach20, 0))
        });
      }
      rechnen();
    }
  });
})();
