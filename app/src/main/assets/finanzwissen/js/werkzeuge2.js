/* ============================================================
   werkzeuge2.js - zweiter Satz Rechner
   Sichtbare Texte laufen ueber K.t (Schluessel = deutscher Text), Zahlen mit Einheit ueber
   K.mitEinheit, K.eur und K.pro. Einheiten in einheit/xEinheit/yEinheit bleiben deutsch;
   Schieber und Diagramme uebersetzen sie selbst.
   ============================================================ */
(function () {
  'use strict';
  var W = window.Werkzeuge, ui = W.ui, el = K.el;

  /* ---------- Vorabpauschale ---------- */
  W.registrieren({
    id: 'vorabpauschale', titel: K.t('Vorabpauschale'), kategorie: K.t('Kosten und Steuern'),
    unter: K.t('Berechnet die Steuer, die im Januar vom Verrechnungskonto abgebucht wird, obwohl nichts verkauft wurde.'),
    bauen: function (w, v) {
      var anf = ui.schieber(w, { label: K.t('Fondswert am 1. Januar'), min: 1000, max: 500000, schritt: 1000, wert: v.anf || 50000, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var rend = ui.schieber(w, { label: K.t('Wertentwicklung im Jahr'), min: -30, max: 40, schritt: 0.5, wert: v.rend === undefined ? 8 : v.rend, nachkomma: 1, einheit: ' %' }, rechnen);
      // Basiszins: wird jedes Jahr vom BMF festgesetzt (2026: 3,20 %)
      var basis = ui.schieber(w, { label: K.t('Basiszins (2026: 3,20 %)'), min: 0, max: 4, schritt: 0.01, wert: v.basis || 3.20, nachkomma: 2, einheit: ' %' }, rechnen);
      var aus = ui.schieber(w, { label: K.t('Ausschüttung im Jahr'), min: 0, max: 10000, schritt: 50, wert: v.aus || 0, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var tf = ui.schalter(w, { label: K.t('Teilfreistellung'), wert: v.tf === undefined ? 30 : v.tf, optionen: [{ wert: 30, text: K.t('Aktienfonds 30 %') }, { wert: 15, text: K.t('Mischfonds 15 %') }, { wert: 0, text: K.t('keine') }] }, rechnen);
      var pausch = ui.schalter(w, { label: K.t('Sparerpauschbetrag noch frei'), wert: v.pausch === undefined ? 1000 : v.pausch, optionen: [{ wert: 0, text: K.t('nein') }, { wert: 1000, text: K.eur(1000, 0) }, { wert: 2000, text: K.eur(2000, 0) }] }, rechnen);
      var erg = ui.ergebnis(w);
      var hinw = ui.notiz(w, '', 'info');
      function rechnen() {
        var ende = anf.wert() * (1 + rend.wert() / 100);
        var vp = R.vorabpauschale(anf.wert(), ende, basis.wert(), aus.wert(), tf.wert());
        var st = R.kapSteuer(vp.vorabpauschale, { teilfreistellung: tf.wert(), pauschbetrag: pausch.wert(), kirche: 0 });
        erg.setzen([
          { wert: K.eur(vp.basisertrag, 0), label: K.t('Basisertrag') },
          { wert: K.eur(vp.vorabpauschale, 0), label: K.t('Vorabpauschale'), farbe: 'blau' },
          { wert: K.eur(st.gesamt, 2), label: K.t('Steuer im Januar'), farbe: 'rot' }
        ]);
        hinw.setzen(K.t('Basisertrag = Wert am Jahresanfang × Basiszins × 0,7. Die Vorabpauschale ist der kleinere Wert aus Basisertrag und tatsächlicher Wertsteigerung einschließlich Ausschüttungen, abzüglich der Ausschüttungen. In einem Verlustjahr entfällt sie. Beim späteren Verkauf werden die während der Besitzzeit angesetzten Vorabpauschalen vom Veräußerungsgewinn abgezogen (§ 19 Abs. 1 Satz 3 InvStG). Die Steuer darauf ist also eine Vorauszahlung auf die spätere Steuer.'));
      }
      rechnen();
    }
  });

  /* ---------- Brutto / Netto ---------- */
  W.registrieren({
    id: 'netto', titel: K.t('Steuerlast und Grenzsteuersatz'), kategorie: K.t('Kosten und Steuern'),
    unter: K.t('Berechnet Einkommensteuer, Durchschnittssatz und Grenzsteuersatz, also den Steueranteil am nächsten verdienten Euro.'),
    bauen: function (w, v) {
      var zve = ui.schieber(w, { label: K.t('zu versteuerndes Einkommen'), min: 0, max: 200000, schritt: 500, wert: v.zve || 45000, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var kirche = ui.schalter(w, { label: K.t('Kirchensteuer'), wert: v.kirche || 0, optionen: [{ wert: 0, text: K.t('keine') }, { wert: 8, text: K.pro(8, 0) }, { wert: 9, text: K.pro(9, 0) }] }, rechnen);
      var erg = ui.ergebnis(w);
      var bild = ui.platz(w);
      function rechnen() {
        var x = zve.wert();
        var est = R.einkommensteuer(x);
        var soli = R.soli(est, false);
        var ks = est * kirche.wert() / 100;
        var ges = est + soli + ks;
        var grenz = R.grenzsteuersatz(x);
        erg.setzen([
          { wert: K.eur(est, 0), label: K.t('Einkommensteuer') },
          { wert: K.pro(x ? ges / x * 100 : 0, 1), label: K.t('Durchschnittssatz') },
          { wert: K.pro(grenz, 1), label: K.t('Grenzsteuersatz'), farbe: 'rot' },
          { wert: K.eur(x - ges, 0), label: K.t('nach Steuern'), farbe: 'gruen' }
        ]);
        var d = [], g = [];
        for (var e = 10000; e <= 160000; e += 2500) {
          var estE = R.einkommensteuer(e);
          d.push([e, (estE + R.soli(estE, false) + estE * kirche.wert() / 100) / e * 100]);
          g.push([e, R.grenzsteuersatz(e)]);
        }
        bild.zeige({
          art: 'linie', hoehe: 190, yEinheit: ' %', xEinheit: ' €',
          serien: [{ name: K.t('Grenzsteuersatz'), werte: g, farbe: 'rot' }, { name: K.t('Durchschnittssatz'), werte: d, farbe: 'gold' }],
          marken: [{ x: x, text: K.t('du') }],
          unter: K.t('Der Grenzsteuersatz gilt für den nächsten verdienten Euro, der Durchschnittssatz für das gesamte Einkommen. Der Durchschnittssatz enthält wie oben Solidaritätszuschlag und Kirchensteuer, der Grenzsteuersatz nur die Einkommensteuer. Für Sparentscheidungen ist der Grenzsteuersatz maßgeblich, also die obere Linie.')
        });
      }
      rechnen();
    },
    hinweis: K.t('Die Rechnung wendet den Grundtarif 2026 (§ 32a EStG, ohne Splitting) auf das eingegebene zu versteuernde Einkommen an, also auf das Einkommen nach Abzug von Freibeträgen und Sonderausgaben. Zur gesamten Belastung kommen die Beiträge zur Kranken-, Pflege-, Renten- und Arbeitslosenversicherung hinzu.')
  });

  /* ---------- Kredit ---------- */
  W.registrieren({
    id: 'kredit', titel: K.t('Annuitätendarlehen und Tilgungsplan'), kategorie: K.t('Immobilien'),
    unter: K.t('Berechnet Rate, Restschuld nach der Zinsbindung und Gesamtzinsen und zeigt, was ein Prozent mehr Tilgung bewirkt.'),
    bauen: function (w, v) {
      var summe = ui.schieber(w, { label: K.t('Darlehenssumme'), min: 20000, max: 1200000, schritt: 5000, wert: v.summe || 350000, format: function (x) { return K.eurKurz(x); } }, rechnen);
      var zins = ui.schieber(w, { label: K.t('Sollzins'), min: 0.5, max: 9, schritt: 0.05, wert: v.zins || 3.6, nachkomma: 2, einheit: ' %' }, rechnen);
      var tilg = ui.schieber(w, { label: K.t('anfängliche Tilgung'), min: 1, max: 10, schritt: 0.25, wert: v.tilg || 2, nachkomma: 2, einheit: ' %' }, rechnen);
      var bind = ui.schieber(w, { label: K.t('Zinsbindung'), min: 5, max: 30, schritt: 1, wert: v.bind || 10, einheit: ' Jahre' }, rechnen);
      var sonder = ui.schieber(w, { label: K.t('Sondertilgung pro Jahr'), min: 0, max: 20000, schritt: 500, wert: v.sonder || 0, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var erg = ui.ergebnis(w);
      var bild = ui.platz(w);
      var bild2 = ui.platz(w);
      function rechnen() {
        var laufzeit = R.laufzeitMitSonder(summe.wert(), zins.wert(), tilg.wert(), sonder.wert());
        var p = R.tilgungsplan(summe.wert(), zins.wert(), tilg.wert(), Math.min(50, Math.ceil(isFinite(laufzeit) ? laufzeit : 50)), sonder.wert());
        var bisBindung = p.plan.filter(function (z) { return z.jahr <= bind.wert(); });
        var rest = bisBindung.length ? bisBindung[bisBindung.length - 1].rest : summe.wert();
        var zinsBis = K.summe(bisBindung.map(function (z) { return z.zins; }));
        erg.setzen([
          { wert: K.eur(p.rate, 0), label: K.t('Monatsrate'), farbe: 'gold' },
          { wert: isFinite(laufzeit) ? K.mitEinheit(K.z(laufzeit, 1), ' J.') : K.t('nie'), label: K.t('bis schuldenfrei') },
          { wert: K.eurKurz(rest), label: K.t('Restschuld nach {0} J.', bind.wert()), farbe: 'rot' },
          { wert: K.eurKurz(p.zinsSumme), label: K.t('Zinsen gesamt') }
        ]);
        var zinsanteil = p.plan.length && p.rate > 0 ? p.plan[0].zins / (p.rate * 12) * 100 : 0;
        bild.zeige({
          art: 'balken', hoehe: 190, gestapelt: true, yEinheit: ' €',
          titel: K.t('Zins und Tilgung pro Jahr'),
          kategorien: p.plan.slice(0, 30).map(function (z) { return String(z.jahr); }),
          serien: [
            { name: K.t('Zins'), werte: p.plan.slice(0, 30).map(function (z) { return z.zins; }), farbe: 'rot' },
            { name: K.t('Tilgung'), werte: p.plan.slice(0, 30).map(function (z) { return z.tilgung; }), farbe: 'gruen' }
          ],
          unter: K.t('Im ersten Jahr entfallen {0} der Raten auf Zinsen. Mit jeder Tilgung sinkt der Zinsanteil, und der Tilgungsanteil wächst. Die Laufzeit hängt deshalb vor allem vom Tilgungssatz ab, die Höhe der Rate allein sagt darüber wenig aus.', K.pro(zinsanteil, 0))
        });
        bild2.zeige({
          art: 'flaeche', hoehe: 170, yEinheit: ' €', xEinheit: ' J', titel: K.t('Restschuld'),
          serien: [{ name: K.t('Restschuld'), werte: [[0, summe.wert()]].concat(p.plan.map(function (z) { return [z.jahr, z.rest]; })), farbe: 'gold', gefuellt: true }],
          marken: [{ x: bind.wert(), text: K.t('Zinsbindung endet') }]
        });
      }
      rechnen();
    },
    hinweis: K.t('Nach Ende der Zinsbindung wird die Restschuld zu dem dann gültigen Zins neu finanziert. Rechne zur Probe mit einem um zwei Prozentpunkte höheren Zins. Ist die Rate dann nicht mehr tragbar, ist das Darlehen zu groß.')
  });

  /* ---------- Anleihe ---------- */
  W.registrieren({
    id: 'anleihe', titel: K.t('Anleihekurs, Rendite und Duration'), kategorie: K.t('Anlegen'),
    unter: K.t('Berechnet die gegenläufige Bewegung von Kurs und Rendite einer Anleihe und wie stark sie bei langer Laufzeit ausfällt.'),
    bauen: function (w, v) {
      var kupon = ui.schieber(w, { label: K.t('Kupon'), min: 0, max: 10, schritt: 0.125, wert: v.kupon === undefined ? 2.5 : v.kupon, nachkomma: 3, einheit: ' %' }, rechnen);
      var jahre = ui.schieber(w, { label: K.t('Restlaufzeit'), min: 1, max: 40, schritt: 1, wert: v.jahre || 10, einheit: ' Jahre' }, rechnen);
      var rendite = ui.schieber(w, { label: K.t('Marktrendite'), min: 0, max: 12, schritt: 0.05, wert: v.rendite === undefined ? 3.0 : v.rendite, nachkomma: 2, einheit: ' %' }, rechnen);
      var schock = ui.schieber(w, { label: K.t('Zinsänderung'), min: -3, max: 3, schritt: 0.05, wert: v.schock || 1, nachkomma: 2, einheit: ' %-Punkte' }, rechnen);
      var erg = ui.ergebnis(w);
      var bild = ui.platz(w);
      function rechnen() {
        var d = R.duration(100, kupon.wert(), rendite.wert(), jahre.wert(), 1);
        var neu = R.anleiheKurs(100, kupon.wert(), rendite.wert() + schock.wert(), jahre.wert(), 1);
        var aend = (neu / d.kurs - 1) * 100;
        erg.setzen([
          { wert: K.mitEinheit(K.z(d.kurs, 2), ' %'), label: K.t('Kurs heute'), farbe: 'gold' },
          { wert: K.z(d.modified, 2), label: K.t('Modified Duration') },
          { wert: K.mitEinheit(K.z(neu, 2), ' %'), label: K.t('Kurs nach Zinsänderung') },
          { wert: K.mitEinheit(K.vorz(aend, 1), ' %'), label: K.t('Kursänderung'), farbe: aend < 0 ? 'rot' : 'gruen' }
        ]);
        var serien = [3, 10, 30].map(function (L, i) {
          var pkt = [];
          for (var y = 0; y <= 8; y += 0.25) pkt.push([y, R.anleiheKurs(100, kupon.wert(), y, L, 1)]);
          return { name: K.t('{0} Jahre Laufzeit', L), werte: pkt, farbe: ['gruen', 'gold', 'rot'][i] };
        });
        bild.zeige({
          art: 'linie', hoehe: 195, yEinheit: ' %', xEinheit: ' %', yNull: false,
          serien: serien, marken: [{ x: rendite.wert(), text: K.t('heute') }],
          unter: K.t('Je länger die Laufzeit, desto steiler verläuft die Kurve und desto stärker ändert sich der Kurs bei gleicher Zinsänderung. Die Steilheit der Kurve veranschaulicht die Duration.')
        });
      }
      rechnen();
    }
  });

  /* ---------- DCF ---------- */
  W.registrieren({
    id: 'dcf', titel: K.t('DCF-Bewertung'), kategorie: K.t('Bewerten'),
    unter: K.t('Berechnet den Barwert der künftigen Zahlungen und zeigt, wie stark das Ergebnis von den Annahmen abhängt.'),
    bauen: function (w, v) {
      var fcf = ui.schieber(w, { label: K.t('Freier Cashflow heute'), min: 10, max: 20000, schritt: 10, wert: v.fcf || 1000, format: function (x) { return K.mitEinheit(K.z(x, 0), ' Mio. €'); } }, rechnen);
      var w1 = ui.schieber(w, { label: K.t('Wachstum Jahre 1–10'), min: -5, max: 25, schritt: 0.5, wert: v.w1 === undefined ? 6 : v.w1, nachkomma: 1, einheit: ' %' }, rechnen);
      var w2 = ui.schieber(w, { label: K.t('ewiges Wachstum danach'), min: 0, max: 4, schritt: 0.1, wert: v.w2 === undefined ? 2 : v.w2, nachkomma: 1, einheit: ' %' }, rechnen);
      var wacc = ui.schieber(w, { label: K.t('Kapitalkosten (WACC)'), min: 3, max: 15, schritt: 0.1, wert: v.wacc || 8.5, nachkomma: 1, einheit: ' %' }, rechnen);
      var schuld = ui.schieber(w, { label: K.t('Nettoverschuldung'), min: -20000, max: 40000, schritt: 250, wert: v.schuld || 2000, format: function (x) { return K.mitEinheit(K.z(x, 0), ' Mio. €'); } }, rechnen);
      var aktien = ui.schieber(w, { label: K.t('Aktienanzahl'), min: 1, max: 5000, schritt: 1, wert: v.aktien || 250, format: function (x) { return K.mitEinheit(K.z(x, 0), ' Mio.'); } }, rechnen);
      var erg = ui.ergebnis(w);
      var bild = ui.platz(w);
      var hinw = ui.notiz(w, '', 'warn');
      function rechnen() {
        var r = R.dcf({ fcf: fcf.wert(), wachstum1: w1.wert(), wachstumEwig: w2.wert(), wacc: wacc.wert(), jahre: 10, nettoschulden: schuld.wert(), aktien: aktien.wert() });
        erg.setzen([
          { wert: K.mitEinheit(K.z(r.unternehmenswert / 1000, 1), ' Mrd.'), label: K.t('Unternehmenswert') },
          { wert: K.mitEinheit(K.z(r.eigenkapital / 1000, 1), ' Mrd.'), label: K.t('Eigenkapitalwert') },
          { wert: K.eur(r.jeAktie, 2), label: K.t('Wert je Aktie'), farbe: 'gruen' },
          { wert: K.pro(r.anteilTerminal, 0), label: K.t('davon Endwert'), farbe: 'rot' }
        ]);
        var kat = [], serie = [];
        for (var x = wacc.wert() - 2; x <= wacc.wert() + 2.01; x += 1) {
          kat.push(K.mitEinheit(K.z(x, 1), ' %'));
          serie.push(R.dcf({ fcf: fcf.wert(), wachstum1: w1.wert(), wachstumEwig: w2.wert(), wacc: x, jahre: 10, nettoschulden: schuld.wert(), aktien: aktien.wert() }).jeAktie);
        }
        bild.zeige({
          art: 'balken', hoehe: 180, kategorien: kat, yEinheit: ' €',
          titel: K.t('Wert je Aktie bei anderen Kapitalkosten'),
          serien: [{ name: K.t('Wert je Aktie'), werte: serie, farbe: 'gold' }],
          unter: K.t('Zwei Prozentpunkte Unterschied beim Diskontsatz verändern das Ergebnis oft um die Hälfte. Ein DCF ergibt deshalb eher eine Bandbreite als einen genauen Preis.')
        });
        hinw.setzen(K.t('{0} des Werts entfallen auf den Endwert nach Jahr 10, also auf Annahmen über einen Zeitraum, den niemand überblickt. Die Genauigkeit des Ergebnisses ist deshalb nur scheinbar.', K.pro(r.anteilTerminal, 0)));
      }
      rechnen();
    }
  });

  /* ---------- Optionen ---------- */
  W.registrieren({
    id: 'option', titel: K.t('Optionspreis und Griechen'), kategorie: K.t('Derivate'),
    unter: K.t('Berechnet den Optionspreis nach Black-Scholes und wie er auf Änderungen von Kurs, Zeit oder Volatilität reagiert.'),
    bauen: function (w, v) {
      var art = ui.schalter(w, { label: K.t('Art'), wert: v.art || 'call', optionen: [{ wert: 'call', text: K.t('Call') }, { wert: 'put', text: K.t('Put') }] }, rechnen);
      var S = ui.schieber(w, { label: K.t('Kurs Basiswert'), min: 10, max: 300, schritt: 1, wert: v.S || 100, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var X = ui.schieber(w, { label: K.t('Basispreis'), min: 10, max: 300, schritt: 1, wert: v.X || 100, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var tage = ui.schieber(w, { label: K.t('Restlaufzeit'), min: 1, max: 730, schritt: 1, wert: v.tage || 90, einheit: ' Tage' }, rechnen);
      var vola = ui.schieber(w, { label: K.t('implizite Volatilität'), min: 5, max: 90, schritt: 1, wert: v.vola || 25, einheit: ' %' }, rechnen);
      var zins = ui.schieber(w, { label: K.t('risikoloser Zins'), min: 0, max: 6, schritt: 0.1, wert: v.zins === undefined ? 2.5 : v.zins, nachkomma: 1, einheit: ' %' }, rechnen);
      var erg = ui.ergebnis(w);
      var erg2 = ui.ergebnis(w);
      var bild = ui.platz(w);
      function rechnen() {
        var t = tage.wert() / 365;
        var o = R.blackScholes(S.wert(), X.wert(), t, vola.wert(), zins.wert(), 0, art.wert());
        erg.setzen([
          { wert: K.eur(o.preis, 2), label: K.t('Optionspreis'), farbe: 'gold' },
          { wert: K.eur(o.innerer, 2), label: K.t('innerer Wert') },
          { wert: K.eur(o.preis - o.innerer, 2), label: K.t('Zeitwert'), farbe: 'blau' }
        ]);
        erg2.setzen([
          { wert: K.z(o.delta, 2), label: K.t('Delta') },
          { wert: K.z(o.gamma, 3), label: K.t('Gamma') },
          { wert: K.eur(o.theta, 3), label: K.t('Theta pro Tag'), farbe: 'rot' },
          { wert: K.eur(o.vega, 3), label: K.t('Vega je 1 % Vola') }
        ]);
        var zeit = [], p = [];
        for (var d = tage.wert(); d >= 0; d -= Math.max(1, Math.round(tage.wert() / 40))) {
          p.push([tage.wert() - d, R.blackScholes(S.wert(), X.wert(), Math.max(d, 0.5) / 365, vola.wert(), zins.wert(), 0, art.wert()).preis]);
        }
        bild.zeige({
          art: 'linie', hoehe: 180, yEinheit: ' €', xEinheit: ' Tage',
          serien: [{ name: K.t('Optionspreis im Zeitverlauf'), werte: p, farbe: 'gold', gefuellt: true }],
          unter: K.t('Der Zeitwert nimmt nicht gleichmäßig ab, sein Verfall beschleunigt sich zum Laufzeitende hin. Im fairen Preis ist dieser Verfall bereits eingerechnet. Stillhalter verdienen im Mittel vor allem, weil die eingepreiste Schwankung meist über der später tatsächlich eingetretenen liegt. Optionskäufer können mit ihrer Markterwartung richtig liegen und trotzdem verlieren, wenn die Bewegung zu spät oder zu klein ausfällt.')
        });
      }
      rechnen();
    }
  });

  /* ---------- Allokation ---------- */
  W.registrieren({
    id: 'allokation', titel: K.t('Mischung aus Aktien und sicherer Anlage'), kategorie: K.t('Anlegen'),
    unter: K.t('Berechnet für eine gewählte Aktienquote die erwartete Rendite, die Schwankung und einen groben Extremverlust.'),
    bauen: function (w, v) {
      var quote = ui.schieber(w, { label: K.t('Aktienanteil'), min: 0, max: 100, schritt: 5, wert: v.quote === undefined ? 70 : v.quote, einheit: ' %' }, rechnen);
      var rA = ui.schieber(w, { label: K.t('Rendite Aktien'), min: 2, max: 12, schritt: 0.1, wert: v.rA || 7, nachkomma: 1, einheit: ' %' }, rechnen);
      var vA = ui.schieber(w, { label: K.t('Schwankung Aktien'), min: 5, max: 35, schritt: 1, wert: v.vA || 17, einheit: ' %' }, rechnen);
      var rB = ui.schieber(w, { label: K.t('Rendite sicherer Teil'), min: 0, max: 6, schritt: 0.1, wert: v.rB === undefined ? 2.5 : v.rB, nachkomma: 1, einheit: ' %' }, rechnen);
      var vB = ui.schieber(w, { label: K.t('Schwankung sicherer Teil'), min: 0, max: 12, schritt: 0.5, wert: v.vB === undefined ? 4 : v.vB, nachkomma: 1, einheit: ' %' }, rechnen);
      var korr = ui.schieber(w, { label: K.t('Korrelation'), min: -1, max: 1, schritt: 0.05, wert: v.korr === undefined ? 0.1 : v.korr, nachkomma: 2, einheit: '' }, rechnen);
      var erg = ui.ergebnis(w);
      var bild = ui.platz(w);
      var bild2 = ui.platz(w);
      function rechnen() {
        var q = quote.wert() / 100;
        var g = [q, 1 - q];
        var rend = R.portfolioRendite(g, [rA.wert(), rB.wert()]);
        var vol = R.portfolioVola(g, [vA.wert(), vB.wert()], [[1, korr.wert()], [korr.wert(), 1]]);
        // grobe Abschaetzung eines typischen Extremverlusts: 2,6 Standardabweichungen
        var dd = -Math.min(95, vol * 2.6);
        erg.setzen([
          { wert: K.pro(rend, 1), label: K.t('erwartete Rendite'), farbe: 'gruen' },
          { wert: K.pro(vol, 1), label: K.t('Schwankung') },
          { wert: K.z(R.sharpe(rend, vol, rB.wert()), 2), label: K.t('Sharpe-Verhältnis') },
          { wert: K.pro(dd, 0), label: K.t('grober Extremverlust'), farbe: 'rot' }
        ]);
        var punkte = [];
        for (var x = 0; x <= 100; x += 5) {
          var gg = [x / 100, 1 - x / 100];
          punkte.push({
            x: R.portfolioVola(gg, [vA.wert(), vB.wert()], [[1, korr.wert()], [korr.wert(), 1]]),
            y: R.portfolioRendite(gg, [rA.wert(), rB.wert()]),
            name: (x % 25 === 0 ? K.mitEinheit(x, ' %') : ''), gruppe: x === quote.wert() ? 0 : 1, r: x === quote.wert() ? 6 : 3
          });
        }
        bild.zeige({
          art: 'streu', hoehe: 200, titel: K.t('Effizienzkurve'), xLabel: K.t('Schwankung in %'), yLabel: K.t('Rendite in %'),
          punkte: punkte, xEinheit: ' %', yEinheit: ' %',
          unter: K.t('Jeder Punkt steht für eine Mischung. Bei Korrelationen unter eins wölbt sich die Kurve nach links, weil die Mischung weniger schwankt als der gewichtete Durchschnitt ihrer Teile.')
        });
        bild2.zeige({
          art: 'donut', titel: K.t('Deine Mischung'), mitte: K.mitEinheit(quote.wert(), ' %'), mitteUnter: K.t('Aktien'),
          teile: [{ name: K.t('Aktien'), wert: quote.wert(), farbe: 'gold' }, { name: K.t('sicherer Teil'), wert: 100 - quote.wert(), farbe: 'blau' }]
        });
      }
      rechnen();
    },
    hinweis: K.t('Der „grobe Extremverlust“ ist eine Faustzahl. Wie hoch der Verlust in einer künftigen Krise ausfällt, lässt sich damit nicht vorhersagen. Historisch verlor ein reines Weltaktienportfolio in schweren Krisen 40 bis 55 Prozent. Wähle die Quote so, dass du diesen Fall aushältst, ohne zu verkaufen.')
  });

  /* ---------- Korrelation zeigen ---------- */
  W.registrieren({
    id: 'korrelation', titel: K.t('Korrelation und Diversifikation'), kategorie: K.t('Risiko'),
    unter: K.t('Berechnet die Schwankung einer Mischung aus zwei Anlagen mit gleicher Rendite und wählbarer Korrelation.'),
    bauen: function (w, v) {
      var korr = ui.schieber(w, { label: K.t('Korrelation der beiden Anlagen'), min: -1, max: 1, schritt: 0.05, wert: v.korr === undefined ? 0.3 : v.korr, nachkomma: 2 }, rechnen);
      var erg = ui.ergebnis(w);
      var bild = ui.platz(w);
      function rechnen() {
        var vol = [], x = [];
        for (var q = 0; q <= 100; q += 5) {
          var g = [q / 100, 1 - q / 100];
          x.push(q);
          vol.push(R.portfolioVola(g, [18, 18], [[1, korr.wert()], [korr.wert(), 1]]));
        }
        var min = Math.min.apply(null, vol);
        erg.setzen([
          { wert: K.pro(18, 0), label: K.t('Schwankung je Anlage') },
          { wert: K.pro(min, 1), label: K.t('geringste Schwankung'), farbe: 'gruen' },
          { wert: K.pro((min / 18 - 1) * 100, 0), label: K.t('Risikoersparnis'), farbe: 'blau' }
        ]);
        bild.zeige({
          art: 'linie', hoehe: 190, yEinheit: ' %', xEinheit: ' %', yNull: false,
          titel: K.t('Schwankung der Mischung je nach Anteil'),
          serien: [{ name: K.t('Portfolioschwankung'), werte: x.map(function (q, i) { return [q, vol[i]]; }), farbe: 'gold' }],
          unter: K.t('Bei Korrelation 1 verläuft die Linie flach, eine Mischung senkt die Schwankung dann nicht. Je weiter die Korrelation darunter liegt, desto tiefer ist die Delle. Diese Verringerung der Schwankung heißt Diversifikationseffekt.')
        });
      }
      rechnen();
    }
  });

  /* ---------- Budget ---------- */
  W.registrieren({
    id: 'budget', titel: K.t('Haushalt und Sparquote'), kategorie: K.t('Sparen und Planen'),
    unter: K.t('Berechnet, wie sich das Nettoeinkommen auf die Ausgaben verteilt und was aus dem Rest in zwanzig Jahren werden kann.'),
    bauen: function (w, v) {
      var netto = ui.schieber(w, { label: K.t('Nettoeinkommen im Monat'), min: 800, max: 15000, schritt: 50, wert: v.netto || 3000, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var wohnen = ui.schieber(w, { label: K.t('Wohnen'), min: 0, max: 4000, schritt: 25, wert: v.wohnen || 1000, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var fix = ui.schieber(w, { label: K.t('weitere Fixkosten'), min: 0, max: 3000, schritt: 25, wert: v.fix || 500, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var leben = ui.schieber(w, { label: K.t('Leben und Freizeit'), min: 0, max: 4000, schritt: 25, wert: v.leben || 900, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var erg = ui.ergebnis(w);
      var bild = ui.platz(w);
      var bild2 = ui.platz(w);
      function rechnen() {
        var rest = netto.wert() - wohnen.wert() - fix.wert() - leben.wert();
        var quote = netto.wert() ? rest / netto.wert() * 100 : 0;
        var in20 = R.sparplan(0, Math.max(0, rest), 6, 20);
        erg.setzen([
          { wert: K.eur(rest, 0), label: K.t('bleibt übrig'), farbe: rest < 0 ? 'rot' : 'gruen' },
          { wert: K.pro(quote, 0), label: K.t('Sparquote') },
          { wert: K.eurKurz(in20), label: K.t('in 20 Jahren bei 6 %'), farbe: 'blau' },
          { wert: K.pro(wohnen.wert() / netto.wert() * 100, 0), label: K.t('Wohnquote'), farbe: wohnen.wert() / netto.wert() > 0.4 ? 'rot' : '' }
        ]);
        bild.zeige({
          art: 'donut', titel: K.t('Aufteilung des Nettoeinkommens'),
          teile: [{ name: K.t('Wohnen'), wert: wohnen.wert() }, { name: K.t('Fixkosten'), wert: fix.wert() },
                  { name: K.t('Leben'), wert: leben.wert() }, { name: K.t('Sparen'), wert: Math.max(0, rest) }]
        });
        var kat = [], ser = [];
        [5, 10, 15, 20, 25, 30, 40, 50].forEach(function (q) {
          kat.push(K.mitEinheit(q, ' %'));
          // Jahre bis 25-faches der Jahresausgaben, bei 5 % Realrendite
          var ausg = 1 - q / 100, r = 0.05, ziel = 25 * ausg;
          var j = Math.log(1 + ziel * r / (q / 100)) / Math.log(1 + r);
          ser.push(Math.min(60, j));
        });
        bild2.zeige({
          art: 'balken', titel: K.t('Jahre bis zur finanziellen Unabhängigkeit'), kategorien: kat,
          serien: [{ name: K.t('Jahre'), werte: ser, farbe: 'gold' }], yEinheit: ' J', hoehe: 180,
          referenz: { wert: Math.min(60, Math.log(1 + 25 * (1 - quote / 100) * 0.05 / Math.max(0.01, quote / 100)) / Math.log(1.05)), text: K.t('du') },
          unter: K.t('Berechnet mit 5 % Realrendite und dem 25-Fachen der Jahresausgaben als Ziel. Die Sparquote wirkt doppelt, denn sie erhöht die Einzahlungen ins Depot und senkt zugleich den Bedarf.')
        });
      }
      rechnen();
    }
  });

  /* ---------- Schulden ---------- */
  W.registrieren({
    id: 'schulden', titel: K.t('Schuldenabbau mit Lawine oder Schneeball'), kategorie: K.t('Sparen und Planen'),
    unter: K.t('Vergleicht Dauer und Zinskosten, wenn zuerst die teuerste oder zuerst die kleinste Schuld getilgt wird.'),
    bauen: function (w, v) {
      var rate = ui.schieber(w, { label: K.t('monatlich verfügbar'), min: 50, max: 3000, schritt: 25, wert: v.rate || 500, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var erg = ui.ergebnis(w);
      var tab = ui.platz(w);
      var bild = ui.platz(w);
      var schulden = v.schulden || [
        { name: K.t('Dispo'), betrag: 2000, zins: 11.5, mindest: 50 },
        { name: K.t('Ratenkredit Auto'), betrag: 9000, zins: 6.9, mindest: 180 },
        { name: K.t('Kreditkarte'), betrag: 800, zins: 18.0, mindest: 25 }
      ];
      function tilgen(reihenfolge) {
        var s = schulden.map(function (x) { return { name: x.name, rest: x.betrag, zins: x.zins, mindest: x.mindest }; });
        s.sort(reihenfolge);
        var monat = 0, zinsSumme = 0, verlauf = [];
        while (s.some(function (x) { return x.rest > 0.01; }) && monat < 600) {
          var frei = rate.wert();
          s.forEach(function (x) {
            if (x.rest <= 0) return;
            var z = x.rest * (Math.pow(1 + x.zins / 100, 1 / 12) - 1);
            x.rest += z; zinsSumme += z;
          });
          s.forEach(function (x) {
            if (x.rest <= 0) return;
            var m = Math.min(x.mindest, x.rest, frei);
            x.rest -= m; frei -= m;
          });
          for (var i = 0; i < s.length && frei > 0; i++) {
            if (s[i].rest <= 0) continue;
            var t = Math.min(frei, s[i].rest);
            s[i].rest -= t; frei -= t;
          }
          monat++;
          verlauf.push([monat / 12, K.summe(s.map(function (x) { return Math.max(0, x.rest); }))]);
        }
        return { monate: monat, zins: zinsSumme, verlauf: verlauf };
      }
      function rechnen() {
        var lawine = tilgen(function (a, b) { return b.zins - a.zins; });
        var schnee = tilgen(function (a, b) { return a.rest - b.rest; });
        erg.setzen([
          { wert: K.mitEinheit(K.z(lawine.monate, 0), ' Mon.'), label: K.t('Lawine: Dauer'), farbe: 'gruen' },
          { wert: K.eur(lawine.zins, 0), label: K.t('Lawine: Zinsen'), farbe: 'gruen' },
          { wert: K.mitEinheit(K.z(schnee.monate, 0), ' Mon.'), label: K.t('Schneeball: Dauer') },
          { wert: K.eur(schnee.zins, 0), label: K.t('Schneeball: Zinsen') }
        ]);
        tab.knoten.innerHTML = '';
        tab.knoten.appendChild(Bloecke.bauen([{
          t: 'tabelle', titel: K.t('Beispielschulden'), kopf: [K.t('Schuld'), K.t('Betrag'), K.t('eff. Jahreszins'), K.t('Mindestrate')],
          zeilen: schulden.map(function (x) { return [x.name, K.eur(x.betrag, 0), K.pro(x.zins, 1), K.eur(x.mindest, 0)]; }),
          zahlSpalten: [1, 2, 3]
        }]));
        bild.zeige({
          art: 'linie', hoehe: 180, yEinheit: ' €', xEinheit: ' J',
          serien: [
            { name: K.t('Lawine (höchster Zins zuerst)'), werte: lawine.verlauf, farbe: 'gruen' },
            { name: K.t('Schneeball (kleinste zuerst)'), werte: schnee.verlauf, farbe: 'orange' }
          ],
          unter: K.t('Mathematisch ist die Lawine immer mindestens so gut wie der Schneeball. Der Schneeball ist nur dann im Vorteil, wenn das frühe Erfolgserlebnis dafür sorgt, dass man die Tilgung überhaupt durchhält.')
        });
      }
      rechnen();
    }
  });

  /* ---------- Kelly / Erwartungswert ---------- */
  W.registrieren({
    id: 'kelly', titel: K.t('Erwartungswert und Positionsgröße'), kategorie: K.t('Risiko'),
    unter: K.t('Berechnet den Erwartungswert einer Wette und wie viel man nach der Kelly-Formel darauf setzen darf.'),
    bauen: function (w, v) {
      var quote = ui.schieber(w, { label: K.t('Trefferquote'), min: 5, max: 95, schritt: 1, wert: v.quote || 45, einheit: ' %' }, rechnen);
      var crv = ui.schieber(w, { label: K.t('Chance-Risiko-Verhältnis'), min: 0.2, max: 6, schritt: 0.1, wert: v.crv || 2, nachkomma: 1, einheit: ' : 1' }, rechnen);
      var erg = ui.ergebnis(w);
      var bild = ui.platz(w);
      function rechnen() {
        var ew = R.erwartungswert(quote.wert(), crv.wert(), 1);
        var k = R.kelly(quote.wert(), crv.wert());
        erg.setzen([
          { wert: K.z(ew, 2) + ' R', label: K.t('Erwartungswert je Trade'), farbe: ew > 0 ? 'gruen' : 'rot' },
          { wert: K.pro(k, 1), label: K.t('voller Kelly-Einsatz') },
          { wert: K.pro(k / 4, 1), label: K.t('Viertel-Kelly (üblich)'), farbe: 'blau' }
        ]);
        var bahnen = [];
        [k / 100, k / 400, Math.min(0.5, k / 50)].forEach(function (f, i) {
          // jede Linie mit derselben Folge von Treffern und Fehlschlaegen
          var rnd = K.wuerfel(2024);
          var v2 = 100, pfad = [[0, 100]];
          for (var t = 1; t <= 200; t++) {
            var treffer = rnd() < quote.wert() / 100;
            v2 *= (1 + (treffer ? f * crv.wert() : -f));
            pfad.push([t, Math.max(0.01, v2)]);
          }
          bahnen.push({ name: [K.t('Kelly'), K.t('Viertel-Kelly'), K.t('doppelter Kelly')][i], werte: pfad, farbe: ['gold', 'gruen', 'rot'][i] });
        });
        bild.zeige({
          art: 'linie', hoehe: 190, logY: true, yEinheit: '', xEinheit: ' Trades',
          serien: bahnen,
          unter: K.t('Die Linien zeigen 200 simulierte Trades mit denselben Signalen und unterschiedlicher Einsatzgröße. Zu große Einsätze ruinieren auch eine Strategie mit positivem Erwartungswert.')
        });
      }
      rechnen();
    }
  });

  /* ---------- Mietrendite ---------- */
  W.registrieren({
    id: 'mietrendite', titel: K.t('Immobilie als Kapitalanlage'), kategorie: K.t('Immobilien'),
    unter: K.t('Berechnet die drei oft verwechselten Renditen einer vermieteten Immobilie: brutto, netto und nach Finanzierung.'),
    bauen: function (w, v) {
      var preis = ui.schieber(w, { label: K.t('Kaufpreis'), min: 50000, max: 1500000, schritt: 5000, wert: v.preis || 300000, format: function (x) { return K.eurKurz(x); } }, rechnen);
      var neben = ui.schieber(w, { label: K.t('Kaufnebenkosten'), min: 5, max: 16, schritt: 0.5, wert: v.neben || 11, nachkomma: 1, einheit: ' %' }, rechnen);
      var miete = ui.schieber(w, { label: K.t('Kaltmiete im Monat'), min: 200, max: 6000, schritt: 25, wert: v.miete || 1000, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var kosten = ui.schieber(w, { label: K.t('nicht umlegbare Kosten im Jahr'), min: 0, max: 12000, schritt: 100, wert: v.kosten || 2400, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var ek = ui.schieber(w, { label: K.t('Eigenkapital'), min: 0, max: 100, schritt: 5, wert: v.ek === undefined ? 25 : v.ek, einheit: ' %' }, rechnen);
      var zins = ui.schieber(w, { label: K.t('Kreditzins'), min: 0.5, max: 8, schritt: 0.1, wert: v.zins || 3.6, nachkomma: 1, einheit: ' %' }, rechnen);
      var erg = ui.ergebnis(w);
      var bild = ui.platz(w);
      function rechnen() {
        var gesamt = preis.wert() * (1 + neben.wert() / 100);
        var jahresmiete = miete.wert() * 12;
        var brutto = jahresmiete / preis.wert() * 100;
        var netto = (jahresmiete - kosten.wert()) / gesamt * 100;
        var eigen = gesamt * ek.wert() / 100;
        var darlehen = gesamt - eigen;
        var zinsKosten = darlehen * zins.wert() / 100;
        var ekRendite = eigen > 0 ? (jahresmiete - kosten.wert() - zinsKosten) / eigen * 100 : NaN;
        var faktor = preis.wert() / jahresmiete;
        erg.setzen([
          { wert: K.pro(brutto, 2), label: K.t('Bruttorendite') },
          { wert: K.pro(netto, 2), label: K.t('Nettorendite'), farbe: netto < 2.5 ? 'rot' : 'gruen' },
          { wert: isFinite(ekRendite) ? K.pro(ekRendite, 1) : '–', label: K.t('auf Eigenkapital'), farbe: ekRendite > netto ? 'gruen' : 'rot' },
          { wert: K.z(faktor, 1), label: K.t('Kaufpreisfaktor') }
        ]);
        bild.zeige({
          art: 'wasserfall', hoehe: 200, titel: K.t('Vom Mietertrag zum Überschuss'),
          schritte: [
            { name: K.t('Jahresmiete'), wert: jahresmiete },
            { name: K.t('Bewirtschaftung'), wert: -kosten.wert() },
            { name: K.t('Zinsen'), wert: -zinsKosten },
            { name: K.t('Überschuss'), wert: 0, summe: true }
          ],
          unter: K.t('Die Tilgung ist hier nicht enthalten, weil sie Vermögen aufbaut und deshalb nicht zu den Kosten zählt. Sie muss aber aus dem Überschuss oder aus dem Gehalt bezahlt werden.')
        });
      }
      rechnen();
    },
    hinweis: K.t('Der Hebeleffekt wirkt in beide Richtungen. Liegt die Nettorendite über dem Kreditzins, erhöht Fremdkapital die Eigenkapitalrendite. Liegt sie darunter, senkt es die Eigenkapitalrendite.')
  });

  /* ---------- Kaufen oder mieten ---------- */
  W.registrieren({
    id: 'kaufenMieten', titel: K.t('Kaufen oder mieten'), kategorie: K.t('Immobilien'),
    unter: K.t('Vergleicht das Vermögen eines Käufers mit dem eines Mieters, der sein Eigenkapital stattdessen anlegt.'),
    bauen: function (w, v) {
      var preis = ui.schieber(w, { label: K.t('Kaufpreis'), min: 100000, max: 1500000, schritt: 10000, wert: v.preis || 450000, format: function (x) { return K.eurKurz(x); } }, rechnen);
      var neben = ui.schieber(w, { label: K.t('Kaufnebenkosten'), min: 5, max: 16, schritt: 0.5, wert: v.neben || 11, nachkomma: 1, einheit: ' %' }, rechnen);
      var miete = ui.schieber(w, { label: K.t('Vergleichsmiete kalt'), min: 300, max: 5000, schritt: 25, wert: v.miete || 1400, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var ek = ui.schieber(w, { label: K.t('Eigenkapital'), min: 0, max: 600000, schritt: 5000, wert: v.ek || 120000, format: function (x) { return K.eurKurz(x); } }, rechnen);
      var zins = ui.schieber(w, { label: K.t('Kreditzins'), min: 0.5, max: 8, schritt: 0.1, wert: v.zins || 3.6, nachkomma: 1, einheit: ' %' }, rechnen);
      var wert = ui.schieber(w, { label: K.t('Wertsteigerung Immobilie'), min: -2, max: 6, schritt: 0.1, wert: v.wert === undefined ? 2 : v.wert, nachkomma: 1, einheit: ' % p. a.' }, rechnen);
      var kapital = ui.schieber(w, { label: K.t('Rendite am Kapitalmarkt'), min: 0, max: 10, schritt: 0.1, wert: v.kapital === undefined ? 6 : v.kapital, nachkomma: 1, einheit: ' % p. a.' }, rechnen);
      var jahre = ui.schieber(w, { label: K.t('Betrachtungszeitraum'), min: 5, max: 40, schritt: 1, wert: v.jahre || 20, einheit: ' Jahre' }, rechnen);
      var erg = ui.ergebnis(w);
      var bild = ui.platz(w);
      function rechnen() {
        var J = jahre.wert();
        var gesamt = preis.wert() * (1 + neben.wert() / 100);
        var darlehen = Math.max(0, gesamt - ek.wert());
        var tilgung = 2;
        var rate = R.annuitaet(darlehen, zins.wert(), tilgung);
        var plan = R.tilgungsplan(darlehen, zins.wert(), tilgung, J, 0);
        var rest = plan.plan.length ? plan.plan[plan.plan.length - 1].rest : darlehen;
        var instand = preis.wert() * 0.01;
        var immoWert = preis.wert() * Math.pow(1 + wert.wert() / 100, J);
        // Mieter: EK anlegen, monatliche Differenz (Rate + Instandhaltung - Miete) ebenfalls anlegen.
        // Ist die Miete hoeher, legt der Kaeufer die Differenz an, ebenso Eigenkapital ueber dem Gesamtpreis.
        var kaeuferKosten = rate + instand / 12;
        var differenz = kaeuferKosten - miete.wert();
        var kaeuferStart = Math.max(0, ek.wert() - gesamt);
        var kaeuferVermoegen = immoWert - rest + R.sparplan(kaeuferStart, Math.max(0, -differenz), kapital.wert(), J);
        var mieterVermoegen = R.sparplan(ek.wert(), Math.max(0, differenz), kapital.wert(), J);
        var mieterVerlauf = [], kaeuferVerlauf = [];
        for (var j = 0; j <= J; j++) {
          mieterVerlauf.push([j, R.sparplan(ek.wert(), Math.max(0, differenz), kapital.wert(), j)]);
          var r2 = j < plan.plan.length ? plan.plan[j === 0 ? 0 : j - 1].rest : rest;
          if (j === 0) r2 = darlehen;
          kaeuferVerlauf.push([j, preis.wert() * Math.pow(1 + wert.wert() / 100, j) - r2 + R.sparplan(kaeuferStart, Math.max(0, -differenz), kapital.wert(), j)]);
        }
        erg.setzen([
          { wert: K.eur(rate, 0), label: K.t('Kreditrate') },
          { wert: K.eurKurz(kaeuferVermoegen), label: K.t('Vermögen Käufer'), farbe: kaeuferVermoegen > mieterVermoegen ? 'gruen' : '' },
          { wert: K.eurKurz(mieterVermoegen), label: K.t('Vermögen Mieter'), farbe: mieterVermoegen > kaeuferVermoegen ? 'gruen' : '' },
          { wert: K.eurKurz(kaeuferVermoegen - mieterVermoegen), label: K.t('Unterschied'), farbe: kaeuferVermoegen > mieterVermoegen ? 'gruen' : 'rot' }
        ]);
        bild.zeige({
          art: 'linie', hoehe: 195, yEinheit: ' €', xEinheit: ' J',
          serien: [
            { name: K.t('Käufer (Immobilie minus Restschuld, Anlage)'), werte: kaeuferVerlauf, farbe: 'gold' },
            { name: K.t('Mieter (Depot)'), werte: mieterVerlauf, farbe: 'blau' }
          ],
          unter: K.t('Das Ergebnis hängt vor allem von drei Größen ab: dem Kaufpreisfaktor, der Wertsteigerung und der Rendite, die das Eigenkapital sonst am Kapitalmarkt gebracht hätte.')
        });
      }
      rechnen();
    },
    hinweis: K.t('Annahmen: 2 % anfängliche Tilgung, Instandhaltung 1 % des Kaufpreises im Jahr. Wer im Monat weniger ausgibt als der andere, legt die Differenz zur Rendite am Kapitalmarkt an. Die Rechnung lässt Steuern, Umzugskosten, Mietsteigerungen und den Wert, selbst zu bestimmen, wo man wohnt, außer Acht. Sie ersetzt keine Entscheidung und zeigt nur, wie groß der finanzielle Unterschied ist.')
  });

  /* ---------- Rentenlücke ---------- */
  W.registrieren({
    id: 'rente', titel: K.t('Rentenlücke'), kategorie: K.t('Ruhestand'),
    unter: K.t('Berechnet die Lücke zwischen gesetzlicher Rente und Bedarf und die private Sparrate, mit der sie sich schließen lässt.'),
    bauen: function (w, v) {
      var alter = ui.schieber(w, { label: K.t('heutiges Alter'), min: 18, max: 66, schritt: 1, wert: v.alter || 35, einheit: ' Jahre' }, rechnen);
      var netto = ui.schieber(w, { label: K.t('heutiges Nettoeinkommen'), min: 1000, max: 12000, schritt: 100, wert: v.netto || 3000, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var rente = ui.schieber(w, { label: K.t('erwartete Nettorente heute'), min: 0, max: 5000, schritt: 50, wert: v.rente || 1400, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var bedarf = ui.schieber(w, { label: K.t('Bedarf im Ruhestand'), min: 50, max: 120, schritt: 5, wert: v.bedarf || 80, einheit: ' % vom Netto' }, rechnen);
      var infl = ui.schieber(w, { label: K.t('Inflation'), min: 0, max: 5, schritt: 0.1, wert: v.infl === undefined ? 2 : v.infl, nachkomma: 1, einheit: ' %' }, rechnen);
      var rendite = ui.schieber(w, { label: K.t('Rendite bis zur Rente'), min: 0, max: 10, schritt: 0.1, wert: v.rendite || 6, nachkomma: 1, einheit: ' %' }, rechnen);
      var erg = ui.ergebnis(w);
      var bild = ui.platz(w);
      function rechnen() {
        var bisRente = Math.max(1, 67 - alter.wert());
        var bedarfHeute = netto.wert() * bedarf.wert() / 100;
        var luecke = Math.max(0, bedarfHeute - rente.wert());
        // Kapitalbedarf: 25 Jahre Entnahme, real 2 % Rendite nach Inflation
        var realR = 0.02;
        var kapital = luecke * 12 * (1 - Math.pow(1 + realR, -25)) / realR;
        var rate = R.rateFuerZiel(kapital, 0, rendite.wert() - infl.wert(), bisRente);
        erg.setzen([
          { wert: K.eur(luecke, 0), label: K.t('Lücke im Monat'), farbe: 'rot' },
          { wert: K.eurKurz(kapital), label: K.t('nötiges Kapital') },
          { wert: K.eur(rate, 0), label: K.t('Sparrate ab heute'), farbe: 'gold' },
          { wert: K.mitEinheit(K.z(bisRente, 0), ' J.'), label: K.t('Zeit bis 67') }
        ]);
        var kat = [], ser = [];
        for (var a = 25; a <= 60; a += 5) {
          kat.push(K.t('{0} J.', a));
          ser.push(R.rateFuerZiel(kapital, 0, rendite.wert() - infl.wert(), Math.max(1, 67 - a)));
        }
        var spaeter = bisRente > 10 ? R.rateFuerZiel(kapital, 0, rendite.wert() - infl.wert(), bisRente - 10) : NaN;
        var faktor = rate > 0 && isFinite(spaeter) ? spaeter / rate : NaN;
        bild.zeige({
          art: 'balken', hoehe: 185, kategorien: kat, yEinheit: ' €',
          titel: K.t('Nötige Sparrate je nach Startalter'),
          serien: [{ name: K.t('Monatsrate'), werte: ser, farbe: 'gold' }],
          referenz: { wert: rate, text: K.t('du') },
          unter: K.t('Alle Beträge sind in heutiger Kaufkraft gerechnet.') + (isFinite(faktor) ? ' ' + K.t('Wer zehn Jahre später beginnt, braucht für dasselbe Ziel das {0}-Fache der Rate.', K.z(faktor, 1)) : '')
        });
      }
      rechnen();
    },
    hinweis: K.t('Die Rechnung nimmt 25 Jahre Rentenbezug und 2 Prozent Realrendite im Ruhestand an. Die Höhe deiner Rente steht in der Renteninformation. Die Schätzung dort ist brutto und vor Abzügen.')
  });

  /* ---------- Monte Carlo ---------- */
  W.registrieren({
    id: 'montecarlo', titel: K.t('Portfolio-Simulation'), kategorie: K.t('Risiko'),
    unter: K.t('Simuliert viele mögliche Verläufe eines Depots und zeigt, was ihre Streuung für die Planbarkeit bedeutet.'),
    bauen: function (w, v) {
      var start = ui.schieber(w, { label: K.t('Startkapital'), min: 0, max: 500000, schritt: 5000, wert: v.start || 20000, format: function (x) { return K.eurKurz(x); } }, rechnen);
      var jaehrlich = ui.schieber(w, { label: K.t('Einzahlung pro Jahr'), min: 0, max: 40000, schritt: 500, wert: v.jaehrlich || 6000, format: function (x) { return K.eur(x, 0); } }, rechnen);
      var rend = ui.schieber(w, { label: K.t('erwartete Rendite'), min: 0, max: 12, schritt: 0.1, wert: v.rend || 7, nachkomma: 1, einheit: ' %' }, rechnen);
      var vola = ui.schieber(w, { label: K.t('Schwankung'), min: 2, max: 35, schritt: 1, wert: v.vola || 17, einheit: ' %' }, rechnen);
      var jahre = ui.schieber(w, { label: K.t('Zeitraum'), min: 5, max: 45, schritt: 1, wert: v.jahre || 25, einheit: ' Jahre' }, rechnen);
      var erg = ui.ergebnis(w);
      var bild = ui.platz(w);
      var bild2 = ui.platz(w);
      function rechnen() {
        var m = R.monteCarlo({ start: start.wert(), jaehrlich: jaehrlich.wert(), rendite: rend.wert(), vola: vola.wert(), jahre: jahre.wert(), pfade: 600, saat: 99 });
        var x = []; for (var i = 0; i <= jahre.wert(); i++) x.push(i);
        erg.setzen([
          { wert: K.eurKurz(m.median), label: K.t('Median'), farbe: 'gold' },
          { wert: K.eurKurz(m.schlecht), label: K.t('schlechtes Zehntel'), farbe: 'rot' },
          { wert: K.eurKurz(m.gut), label: K.t('gutes Zehntel'), farbe: 'gruen' },
          { wert: K.z(m.gut / Math.max(1, m.schlecht), 1) + '×', label: K.t('Spanne') }
        ]);
        bild.zeige({
          art: 'faecher', hoehe: 200, x: x, yEinheit: ' €',
          baender: [{ unten: m.p5, oben: m.p95, farbe: 'blau', opazitaet: 0.12 }, { unten: m.p25, oben: m.p75, farbe: 'blau', opazitaet: 0.2 }],
          linien: [{ name: K.t('Median'), werte: m.p50, farbe: 'gold' }],
          unter: K.t('Das helle Band enthält 90 Prozent der Verläufe, das dunkle die mittleren 50 Prozent. Die Streuung wächst mit der Zeit, für die Planung ist deshalb eine Bandbreite aussagekräftiger als ein einzelner Zielwert.')
        });
        bild2.zeige({
          art: 'histogramm', hoehe: 175, titel: K.t('Verteilung der Endwerte'), werte: m.ende, klassen: 14,
          unter: K.t('Die Verteilung ist rechtsschief. Der wahrscheinlichste Endwert liegt deshalb unter dem Durchschnitt.')
        });
      }
      rechnen();
    },
    hinweis: K.t('Die Simulation unterstellt unabhängige, normalverteilte Jahresrenditen. An echten Märkten hat die Verteilung dickere Ränder, und die Renditen aufeinanderfolgender Jahre sind nicht unabhängig voneinander. Schwere Verluste kommen deshalb häufiger vor, als dieses Modell zeigt.')
  });

  /* ---------- Rebalancing ---------- */
  W.registrieren({
    id: 'rebalancing', titel: K.t('Rebalancing'), kategorie: K.t('Anlegen'),
    unter: K.t('Simuliert, wie sich die Aktienquote ohne Eingriff verschiebt und was das Zurücksetzen auf die Zielquote bewirkt.'),
    bauen: function (w, v) {
      var quote = ui.schieber(w, { label: K.t('Zielquote Aktien'), min: 20, max: 100, schritt: 5, wert: v.quote || 60, einheit: ' %' }, rechnen);
      var band = ui.schieber(w, { label: K.t('Toleranzband'), min: 0, max: 20, schritt: 1, wert: v.band === undefined ? 5 : v.band, einheit: ' %-Punkte' }, rechnen);
      var erg = ui.ergebnis(w);
      var bild = ui.platz(w);
      function rechnen() {
        var rnd = K.wuerfel(777);
        var jahre = 30;
        var a = 100 * quote.wert() / 100, b = 100 - a;
        var aF = a, bF = b;
        var mitR = [[0, 100]], ohneR = [[0, 100]], quoteVerlauf = [[0, quote.wert()]];
        var anpassungen = 0;
        for (var j = 1; j <= jahre; j++) {
          var rA = 0.07 + 0.18 * K.normal(rnd);
          var rB = 0.025 + 0.05 * K.normal(rnd);
          a *= (1 + rA); b *= (1 + rB);
          aF *= (1 + rA); bF *= (1 + rB);
          var g = a + b;
          var ist = a / g * 100;
          if (Math.abs(ist - quote.wert()) > band.wert()) {
            a = g * quote.wert() / 100; b = g - a; anpassungen++;
          }
          mitR.push([j, a + b]); ohneR.push([j, aF + bF]);
          quoteVerlauf.push([j, aF / (aF + bF) * 100]);
        }
        erg.setzen([
          { wert: K.z(mitR[jahre][1], 0), label: K.t('mit Rebalancing'), farbe: 'gold' },
          { wert: K.z(ohneR[jahre][1], 0), label: K.t('ohne'), farbe: 'blau' },
          { wert: K.z(anpassungen, 0), label: K.t('Anpassungen in 30 J.') },
          { wert: K.pro(quoteVerlauf[jahre][1], 0), label: K.t('Aktienquote ohne'), farbe: 'rot' }
        ]);
        bild.zeige({
          art: 'linie', hoehe: 190, xEinheit: ' J', yEinheit: ' %',
          titel: K.t('Aktienquote ohne Rebalancing'),
          serien: [{ name: K.t('tatsächliche Aktienquote'), werte: quoteVerlauf, farbe: 'rot' }],
          marken: [{ x: 0, text: K.t('Ziel {0}', K.mitEinheit(quote.wert(), ' %')) }],
          unter: K.t('Ohne Eingriff wächst der Aktienanteil im Depot, am stärksten nach langen Phasen mit guten Aktienjahren. Rebalancing dient dazu, das Risiko konstant zu halten, und ist nicht auf Mehrrendite ausgelegt.')
        });
      }
      rechnen();
    }
  });

  /* ---------- Währung ---------- */
  W.registrieren({
    id: 'waehrung', titel: K.t('Währungseffekt'), kategorie: K.t('Anlegen'),
    unter: K.t('Berechnet, wie viel ein Euro-Anleger an einer Dollar-Anlage durch den Wechselkurs gewinnt oder verliert.'),
    bauen: function (w, v) {
      var rendite = ui.schieber(w, { label: K.t('Rendite in Fremdwährung'), min: -30, max: 40, schritt: 0.5, wert: v.rendite === undefined ? 10 : v.rendite, nachkomma: 1, einheit: ' %' }, rechnen);
      var kurs = ui.schieber(w, { label: K.t('Änderung des Wechselkurses'), min: -30, max: 30, schritt: 0.5, wert: v.kurs === undefined ? -8 : v.kurs, nachkomma: 1, einheit: ' %' }, rechnen);
      var erg = ui.ergebnis(w);
      var bild = ui.platz(w);
      function rechnen() {
        var ges = ((1 + rendite.wert() / 100) * (1 + kurs.wert() / 100) - 1) * 100;
        erg.setzen([
          { wert: K.pro(rendite.wert(), 1), label: K.t('in Fremdwährung') },
          { wert: K.pro(kurs.wert(), 1), label: K.t('Wechselkurs'), farbe: kurs.wert() < 0 ? 'rot' : 'gruen' },
          { wert: K.pro(ges, 1), label: K.t('in Euro'), farbe: ges < 0 ? 'rot' : 'gruen' }
        ]);
        var zeilen = [], werte = [];
        for (var k = -20; k <= 20; k += 5) {
          zeilen.push(K.mitEinheit(K.vorz(k, 0), ' %'));
          werte.push(((1 + rendite.wert() / 100) * (1 + k / 100) - 1) * 100);
        }
        bild.zeige({
          art: 'balken', hoehe: 180, kategorien: zeilen, yEinheit: ' %', diverging: true,
          titel: K.t('Euro-Rendite bei verschiedenen Wechselkursänderungen'),
          serien: [{ name: K.t('Rendite in Euro'), werte: werte }],
          unter: K.t('Rendite und Wechselkursänderung multiplizieren sich. Über lange Zeiträume gleichen sich Währungsschwankungen bei Aktien weitgehend aus, über kurze Zeiträume nicht.')
        });
      }
      rechnen();
    },
    hinweis: K.t('Ein „in Euro währungsgesicherter“ ETF sichert nur die Notierung ab, das Geschäft der Unternehmen bleibt von Wechselkursen abhängig. Für Aktien ist eine Sicherung meist unnötig und kostet die Zinsdifferenz. Für Anleihen ist sie sinnvoll, weil dort das Währungsrisiko größer ist als die Rendite.')
  });

  /* ---------- Kennzahlen ---------- */
  W.registrieren({
    id: 'kennzahlen', titel: K.t('Aktienkennzahlen'), kategorie: K.t('Bewerten'),
    unter: K.t('Berechnet gängige Kennzahlen wie KGV, KBV, PEG und Dividendenrendite aus den Angaben je Aktie.'),
    bauen: function (w, v) {
      var kurs = ui.zahl(w, { label: K.t('Kurs je Aktie (€)'), wert: v.kurs || 100 }, rechnen);
      var gewinn = ui.zahl(w, { label: K.t('Gewinn je Aktie (€)'), wert: v.gewinn || 6.5 }, rechnen);
      var buch = ui.zahl(w, { label: K.t('Buchwert je Aktie (€)'), wert: v.buch || 32 }, rechnen);
      var umsatz = ui.zahl(w, { label: K.t('Umsatz je Aktie (€)'), wert: v.umsatz || 55 }, rechnen);
      var div = ui.zahl(w, { label: K.t('Dividende je Aktie (€)'), wert: v.div || 2.4 }, rechnen);
      var wachstum = ui.zahl(w, { label: K.t('erwartetes Gewinnwachstum (%)'), wert: v.wachstum || 8 }, rechnen);
      var erg = ui.ergebnis(w);
      var erg2 = ui.ergebnis(w);
      var bild = ui.platz(w);
      function rechnen() {
        var kgv = kurs.wert() / gewinn.wert();
        var kbv = kurs.wert() / buch.wert();
        var kuv = kurs.wert() / umsatz.wert();
        var divR = div.wert() / kurs.wert() * 100;
        var payout = div.wert() / gewinn.wert() * 100;
        var peg = kgv / wachstum.wert();
        var roe = gewinn.wert() / buch.wert() * 100;
        var gewinnRendite = gewinn.wert() / kurs.wert() * 100;
        erg.setzen([
          { wert: K.z(kgv, 1), label: K.t('KGV'), farbe: kgv > 30 ? 'rot' : kgv < 12 ? 'gruen' : '' },
          { wert: K.z(kbv, 2), label: K.t('KBV') },
          { wert: K.z(kuv, 2), label: K.t('KUV') },
          { wert: K.z(peg, 2), label: K.t('PEG'), farbe: peg < 1 ? 'gruen' : peg > 2 ? 'rot' : '' }
        ]);
        erg2.setzen([
          { wert: K.pro(roe, 1), label: K.t('Eigenkapitalrendite'), farbe: roe > 15 ? 'gruen' : '' },
          { wert: K.pro(divR, 2), label: K.t('Dividendenrendite') },
          { wert: K.pro(payout, 0), label: K.t('Ausschüttungsquote'), farbe: payout > 80 ? 'rot' : '' },
          { wert: K.pro(gewinnRendite, 1), label: K.t('Gewinnrendite') }
        ]);
        bild.zeige({
          art: 'radar', hoehe: 220, max: 10, titel: K.t('Profil (0–10, je höher, desto günstiger/besser)'),
          achsen: [K.t('Bewertung'), K.t('Rendite'), K.t('Dividende'), K.t('Wachstum'), K.t('Substanz')],
          serien: [{
            name: K.t('dieses Unternehmen'), farbe: 'gold', werte: [
              K.grenz(10 - kgv / 4, 0, 10), K.grenz(roe / 3, 0, 10), K.grenz(divR * 2, 0, 10),
              K.grenz(wachstum.wert() / 2, 0, 10), K.grenz(10 - kbv * 2, 0, 10)]
          }],
          unter: K.t('Das Netzdiagramm ersetzt keine eigene Beurteilung, denn Kennzahlen werfen Fragen auf, ohne sie zu beantworten. Ein niedriges KGV kann zum Beispiel auf eine günstige Gelegenheit oder auf ein Problem hinweisen.')
        });
      }
      rechnen();
    }
  });
})();
