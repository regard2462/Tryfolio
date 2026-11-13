/* ============================================================
   rechner.js - die Finanzmathematik hinter den Werkzeugen.
   Bewusst ohne Rundungstricks: was hier steht, ist nachrechenbar.
   ============================================================ */
(function () {
  'use strict';
  var R = {};
  window.R = R;

  /* ---------- Zins und Zeit ---------- */
  R.endwert = function (barwert, zinsProzent, jahre) {
    return barwert * Math.pow(1 + zinsProzent / 100, jahre);
  };
  R.barwert = function (endwert, zinsProzent, jahre) {
    return endwert / Math.pow(1 + zinsProzent / 100, jahre);
  };
  /** Sparplan: monatliche Rate, nachschuessig oder vorschuessig */
  R.sparplan = function (start, monatlich, zinsProzent, jahre, vorschuessig) {
    var i = zinsProzent / 100 / 12, n = Math.round(jahre * 12), v = start || 0;
    for (var k = 0; k < n; k++) {
      if (vorschuessig) v = (v + monatlich) * (1 + i);
      else v = v * (1 + i) + monatlich;
    }
    return v;
  };
  R.sparplanVerlauf = function (start, monatlich, zinsProzent, jahre, dynamikProzent) {
    var i = zinsProzent / 100 / 12, v = start || 0, rate = monatlich, ein = start || 0;
    var kurve = [[0, v]], einzahlung = [[0, ein]];
    for (var j = 1; j <= jahre; j++) {
      for (var m = 0; m < 12; m++) { v = v * (1 + i) + rate; ein += rate; }
      if (dynamikProzent) rate *= (1 + dynamikProzent / 100);
      kurve.push([j, v]); einzahlung.push([j, ein]);
    }
    return { kurve: kurve, einzahlung: einzahlung, endwert: v, eingezahlt: ein };
  };
  /** notwendige monatliche Rate fuer ein Ziel */
  R.rateFuerZiel = function (ziel, start, zinsProzent, jahre) {
    var i = zinsProzent / 100 / 12, n = Math.round(jahre * 12);
    var ausStart = (start || 0) * Math.pow(1 + i, n);
    var rest = ziel - ausStart;
    if (rest <= 0) return 0;
    if (Math.abs(i) < 1e-12) return rest / n;
    return rest * i / (Math.pow(1 + i, n) - 1);
  };
  R.regel72 = function (zinsProzent) { return 72 / zinsProzent; };
  R.cagr = function (anfang, ende, jahre) {
    if (anfang <= 0 || jahre <= 0) return NaN;
    return (Math.pow(ende / anfang, 1 / jahre) - 1) * 100;
  };
  R.realzins = function (nominalProzent, inflationProzent) {
    return ((1 + nominalProzent / 100) / (1 + inflationProzent / 100) - 1) * 100;
  };

  /* ---------- Barwert / IRR ---------- */
  R.npv = function (zahlungen, zinsProzent) {
    var i = zinsProzent / 100;
    return zahlungen.reduce(function (s, z, t) { return s + z / Math.pow(1 + i, t); }, 0);
  };
  R.irr = function (zahlungen) {
    var lo = -0.99, hi = 10;
    function f(r) { return zahlungen.reduce(function (s, z, t) { return s + z / Math.pow(1 + r, t); }, 0); }
    if (f(lo) * f(hi) > 0) return NaN;
    for (var k = 0; k < 200; k++) {
      var m = (lo + hi) / 2;
      if (f(lo) * f(m) <= 0) hi = m; else lo = m;
    }
    return (lo + hi) / 2 * 100;
  };

  /* ---------- Kredit / Annuitaet ---------- */
  R.annuitaet = function (darlehen, zinsProzent, tilgungProzent) {
    return darlehen * (zinsProzent + tilgungProzent) / 100 / 12;
  };
  R.tilgungsplan = function (darlehen, zinsProzent, tilgungProzent, jahre, sondertilgung) {
    var rate = R.annuitaet(darlehen, zinsProzent, tilgungProzent);
    var i = zinsProzent / 100 / 12, rest = darlehen;
    var plan = [], zinsSumme = 0, tilgSumme = 0;
    for (var j = 1; j <= jahre && rest > 0; j++) {
      var zj = 0, tj = 0;
      for (var m = 0; m < 12 && rest > 0; m++) {
        var z = rest * i;
        var t = Math.min(rate - z, rest);
        rest -= t; zj += z; tj += t;
      }
      if (sondertilgung) { var s = Math.min(sondertilgung, rest); rest -= s; tj += s; }
      zinsSumme += zj; tilgSumme += tj;
      plan.push({ jahr: j, zins: zj, tilgung: tj, rest: Math.max(0, rest) });
    }
    return { rate: rate, plan: plan, zinsSumme: zinsSumme, tilgSumme: tilgSumme, restschuld: Math.max(0, rest) };
  };
  /** Laufzeit in Jahren mit jaehrlicher Sondertilgung am Jahresende (monatlich gerechnet). */
  R.laufzeitMitSonder = function (darlehen, zinsProzent, tilgungProzent, sondertilgung) {
    if (!sondertilgung) return R.laufzeitJahre(darlehen, zinsProzent, tilgungProzent);
    var rate = R.annuitaet(darlehen, zinsProzent, tilgungProzent);
    var i = zinsProzent / 100 / 12, rest = darlehen;
    for (var m = 1; m <= 1200; m++) {
      rest -= rate - rest * i;
      if (rest <= 0) return m / 12;
      if (m % 12 === 0) {
        rest -= sondertilgung;
        if (rest <= 0) return m / 12;
      }
    }
    return Infinity;
  };
  R.laufzeitJahre = function (darlehen, zinsProzent, tilgungProzent) {
    var rate = R.annuitaet(darlehen, zinsProzent, tilgungProzent);
    var i = zinsProzent / 100 / 12;
    if (rate <= darlehen * i) return Infinity;
    if (i === 0) return darlehen / rate / 12;
    return Math.log(rate / (rate - darlehen * i)) / Math.log(1 + i) / 12;
  };

  /* ---------- Anleihen ---------- */
  R.anleiheKurs = function (nennwert, kuponProzent, renditeProzent, jahre, zahlungenProJahr) {
    var f = zahlungenProJahr || 1;
    var c = nennwert * kuponProzent / 100 / f, y = renditeProzent / 100 / f, n = Math.round(jahre * f);
    var s = 0;
    for (var t = 1; t <= n; t++) s += c / Math.pow(1 + y, t);
    return s + nennwert / Math.pow(1 + y, n);
  };
  R.anleiheRendite = function (kurs, nennwert, kuponProzent, jahre, zahlungenProJahr) {
    var lo = -50, hi = 200;
    for (var k = 0; k < 200; k++) {
      var m = (lo + hi) / 2;
      if (R.anleiheKurs(nennwert, kuponProzent, m, jahre, zahlungenProJahr) > kurs) lo = m; else hi = m;
    }
    return (lo + hi) / 2;
  };
  R.duration = function (nennwert, kuponProzent, renditeProzent, jahre, zahlungenProJahr) {
    var f = zahlungenProJahr || 1;
    var c = nennwert * kuponProzent / 100 / f, y = renditeProzent / 100 / f, n = Math.round(jahre * f);
    var kurs = 0, gew = 0;
    for (var t = 1; t <= n; t++) {
      var bw = c / Math.pow(1 + y, t);
      kurs += bw; gew += bw * t / f;
    }
    var bwN = nennwert / Math.pow(1 + y, n);
    kurs += bwN; gew += bwN * n / f;
    var mac = gew / kurs;
    return { kurs: kurs, macaulay: mac, modified: mac / (1 + y) };
  };

  /* ---------- Optionen (Black-Scholes) ---------- */
  function NV(x) {
    // Normalverteilungsfunktion ueber die Fehlerfunktion, Naeherung Abramowitz-Stegun 7.1.26
    var a1 = 0.254829592, a2 = -0.284496736, a3 = 1.421413741, a4 = -1.453152027, a5 = 1.061405429, p = 0.3275911;
    var vz = x < 0 ? -1 : 1; x = Math.abs(x) / Math.sqrt(2);
    var t = 1 / (1 + p * x);
    var y = 1 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);
    return 0.5 * (1 + vz * y);
  }
  function nv(x) { return Math.exp(-x * x / 2) / Math.sqrt(2 * Math.PI); }
  R.NV = NV;

  R.blackScholes = function (S, X, tJahre, volProzent, zinsProzent, divProzent, art) {
    var v = volProzent / 100, r = zinsProzent / 100, q = (divProzent || 0) / 100, t = Math.max(tJahre, 1e-6);
    var d1 = (Math.log(S / X) + (r - q + v * v / 2) * t) / (v * Math.sqrt(t));
    var d2 = d1 - v * Math.sqrt(t);
    var call = S * Math.exp(-q * t) * NV(d1) - X * Math.exp(-r * t) * NV(d2);
    var put = X * Math.exp(-r * t) * NV(-d2) - S * Math.exp(-q * t) * NV(-d1);
    var preis = art === 'put' ? put : call;
    var delta = art === 'put' ? Math.exp(-q * t) * (NV(d1) - 1) : Math.exp(-q * t) * NV(d1);
    var gamma = Math.exp(-q * t) * nv(d1) / (S * v * Math.sqrt(t));
    var vega = S * Math.exp(-q * t) * nv(d1) * Math.sqrt(t) / 100;
    var theta = (art === 'put'
      ? -S * Math.exp(-q * t) * nv(d1) * v / (2 * Math.sqrt(t)) + r * X * Math.exp(-r * t) * NV(-d2) - q * S * Math.exp(-q * t) * NV(-d1)
      : -S * Math.exp(-q * t) * nv(d1) * v / (2 * Math.sqrt(t)) - r * X * Math.exp(-r * t) * NV(d2) + q * S * Math.exp(-q * t) * NV(d1)) / 365;
    var rho = (art === 'put' ? -X * t * Math.exp(-r * t) * NV(-d2) : X * t * Math.exp(-r * t) * NV(d2)) / 100;
    return { preis: preis, call: call, put: put, d1: d1, d2: d2, delta: delta, gamma: gamma, vega: vega, theta: theta, rho: rho, innerer: Math.max(0, art === 'put' ? X - S : S - X) };
  };

  /* ---------- Uhr und Jahreswerte ---------- */
  /** Heutiges Datum; Tests ersetzen die Uhr. */
  R.heute = function () { return new Date(); };
  R.jahr = function () { return R.heute().getFullYear(); };
  /** Volle Monate zwischen einem Datum (JJJJ-MM oder JJJJ-MM-TT) und heute; 0 bei unlesbarem Datum oder Datum in der Zukunft. */
  R.monateSeit = function (iso) {
    var m = String(iso || '').match(/^(\d{4})-(\d{2})(?:-(\d{2}))?$/);
    if (!m) return 0;
    var h = R.heute();
    var tag = m[3] ? parseInt(m[3], 10) : 1;
    var n = (h.getFullYear() - parseInt(m[1], 10)) * 12 + (h.getMonth() + 1 - parseInt(m[2], 10)) - (h.getDate() < tag ? 1 : 0);
    return Math.max(0, n);
  };
  /** Rechtsstand einer Lektion ueberholt? Ab dem 1. Januar des Folgejahres koennen Jahreswerte neu festgesetzt sein. */
  R.rechtsstandAlt = function (iso) {
    var m = String(iso || '').match(/^(\d{4})-\d{2}(?:-\d{2})?$/);
    return !!m && R.jahr() > parseInt(m[1], 10);
  };

  /* ---------- Jahrestabelle der Steuerwerte ----------
     Eine Tabelle je Feld: Jahr -> Wert. Eingebaut sind die Werte bei Veroeffentlichung; beim Start mischt R.laden die Datei
     data/steuerwerte.json ein, die die App aus ihrem Datenbuendel liefert (dieselbe Datei wie fuer die Depot-Steuer).
     Fehlt einem Jahr ein Feld, gilt der Wert des juengsten Jahres davor, das das Feld hat; das Ergebnis heisst dann geschaetzt. */
  R.WERTE = {
    abgeltung: { 2023: 25, 2024: 25, 2025: 25, 2026: 25 },
    soli: { 2023: 5.5, 2024: 5.5, 2025: 5.5, 2026: 5.5 },
    pauschbetrag: { 2023: 1000, 2024: 1000, 2025: 1000, 2026: 1000 },
    pauschbetragPaar: { 2023: 2000, 2024: 2000, 2025: 2000, 2026: 2000 },
    /** Basiszins nach §18 InvStG je Jahr in Prozent (vom BMF jedes Jahr im Januar festgesetzt). */
    basiszins: { 2023: 2.55, 2024: 2.29, 2025: 2.53, 2026: 3.20 },
    grundfreibetrag: { 2026: 12348 },
    /** Freigrenze des Solidaritaetszuschlags auf die Einkommensteuer (Einzel- und Zusammenveranlagung) */
    soliFreigrenze: { 2026: 20350 },
    soliFreigrenzePaar: { 2026: 40700 }
  };
  R.BASISZINS = R.WERTE.basiszins;

  /** Eintrag einer Tabelle fuers Jahr: der juengste bekannte bis dahin, vor dem aeltesten der aelteste. */
  function juengster(tabelle, jahr) {
    var js = Object.keys(tabelle).map(Number).filter(function (x) { return isFinite(x); }).sort(function (a, b) { return a - b; });
    if (!js.length) return null;
    var j = js.filter(function (x) { return x <= jahr; });
    var nimm = j.length ? j[j.length - 1] : js[0];
    return { wert: tabelle[nimm], jahr: nimm, geschaetzt: nimm !== jahr };
  }
  /** Wert eines Feldes fuers Jahr: {wert, jahr (aus dem der Wert stammt), geschaetzt}. */
  R.wertFuer = function (feld, jahr) {
    return juengster(R.WERTE[feld] || {}, jahr) || { wert: NaN, jahr: jahr, geschaetzt: true };
  };
  /** Basiszins fuers Jahr: der juengste bekannte bis dahin. geschaetzt, wenn das Jahr keinen eigenen Wert hat. */
  R.basiszinsFuer = function (jahr) { return R.wertFuer('basiszins', jahr); };
  /** Sparer-Pauschbetrag fuers Jahr: einzeln und zusammen veranlagt. */
  R.pauschbetragFuer = function (jahr) {
    var e = R.wertFuer('pauschbetrag', jahr), p = R.wertFuer('pauschbetragPaar', jahr);
    return { einzeln: e.wert, paar: p.wert, jahr: e.jahr, geschaetzt: e.geschaetzt || p.geschaetzt };
  };
  /** Soli-Freigrenze fuers Jahr: einzeln und zusammen veranlagt. */
  R.soliFreigrenzeFuer = function (jahr) {
    var e = R.wertFuer('soliFreigrenze', jahr), p = R.wertFuer('soliFreigrenzePaar', jahr);
    return { einzeln: e.wert, paar: p.wert, jahr: e.jahr, geschaetzt: e.geschaetzt || p.geschaetzt };
  };

  /* ---------- Deutsche Steuer auf Kapitalertraege ---------- */
  /** Saetze und Pauschbetrag des laufenden Jahres (oder des juengsten bekannten). */
  R.STEUER = {
    teilfrei: { aktienfonds: 30, mischfonds: 15, immobilien: 60, immobilienAusland: 80, keiner: 0 }
  };
  ['abgeltung', 'soli', 'pauschbetrag', 'pauschbetragPaar'].forEach(function (f) {
    Object.defineProperty(R.STEUER, f, { enumerable: true, get: function () { return R.wertFuer(f, R.jahr()).wert; } });
  });
  /** Steuer auf einen Kapitalertrag. kirche in Prozent (0, 8 oder 9). */
  R.kapSteuer = function (ertrag, opt) {
    opt = opt || {};
    var teilfrei = opt.teilfreistellung || 0;
    var kirche = opt.kirche || 0;
    var frei = opt.pauschbetrag === undefined ? 0 : opt.pauschbetrag;
    var stpfl = Math.max(0, ertrag * (1 - teilfrei / 100) - frei);
    // Kirchensteuer mindert die Bemessungsgrundlage (§32d Abs. 1 EStG)
    var a = R.STEUER.abgeltung / 100;
    var satz = kirche ? (a / (1 + a * kirche / 100)) : a;
    var abg = stpfl * satz;
    var soli = abg * R.STEUER.soli / 100;
    var ks = abg * kirche / 100;
    return { steuerpflichtig: stpfl, abgeltung: abg, soli: soli, kirche: ks, gesamt: abg + soli + ks, netto: ertrag - (abg + soli + ks) };
  };
  /** Vorabpauschale nach §18 InvStG */
  R.vorabpauschale = function (wertAnfang, wertEnde, basiszinsProzent, ausschuettung, teilfreistellung, monateImBesitz) {
    var basisertrag = wertAnfang * (basiszinsProzent / 100) * 0.7;
    var wertsteigerung = Math.max(0, wertEnde - wertAnfang + (ausschuettung || 0));
    var vorab = Math.max(0, Math.min(basisertrag, wertsteigerung) - (ausschuettung || 0));
    if (monateImBesitz && monateImBesitz < 12) vorab *= monateImBesitz / 12;
    var stpfl = vorab * (1 - (teilfreistellung || 0) / 100);
    return { basisertrag: basisertrag, vorabpauschale: vorab, steuerpflichtig: stpfl };
  };

  /* ---------- Einkommensteuer Deutschland (Tarif) ----------
     Ein Tarif ist eine Liste von Zonen nach §32a Abs. 1 EStG. Eine Zone gilt bis bis (einschliesslich; die letzte hat keine
     Grenze), ihre Steuer ist (a * y + b) * y + c + satz * x - abzug mit y = (x - ab) / 10.000, auf volle Euro abgerundet.
     Felder ohne Angabe sind 0. So passt jeder Tarif hinein, auch einer mit weiteren Stufen. */
  R.TARIFE = {
    2026: {
      jahr: 2026,
      grundfreibetrag: 12348,
      zonen: [
        { bis: 12348 },
        { bis: 17799, ab: 12348, a: 914.51, b: 1400 },
        { bis: 69878, ab: 17799, a: 173.10, b: 2397, c: 1034.87 },
        { bis: 277825, satz: 0.42, abzug: 11135.63 },
        { satz: 0.45, abzug: 19470.38 }
      ]
    }
  };
  /** Tarif fuers Jahr: der juengste bekannte bis dahin. geschaetzt, wenn das Jahr keinen eigenen Eintrag hat. */
  R.tarifFuer = function (jahr) {
    var t = juengster(R.TARIFE, jahr);
    return { tarif: t.wert, jahr: t.jahr, geschaetzt: t.geschaetzt };
  };
  /** Tarif des laufenden Jahres (oder des juengsten bekannten, siehe tarifFuer). */
  Object.defineProperty(R, 'TARIF', { get: function () { return R.tarifFuer(R.jahr()).tarif; } });
  function zoneSteuer(z, x) {
    var s = 0;
    if (z.a || z.b) {
      var y = (x - (z.ab || 0)) / 10000;
      s = ((z.a || 0) * y + (z.b || 0)) * y;
    }
    s += z.c || 0;
    if (z.satz) s += z.satz * x - (z.abzug || 0);
    return Math.floor(s);
  }
  R.einkommensteuer = function (zvE, tarif) {
    var zonen = (tarif || R.TARIF).zonen;
    var x = Math.floor(zvE);
    for (var i = 0; i < zonen.length - 1; i++) if (x <= zonen[i].bis) return zoneSteuer(zonen[i], x);
    return zoneSteuer(zonen[zonen.length - 1], x);
  };
  /** Ist eine Zonenliste brauchbar? Aufsteigende Grenzen, letzte Zone offen, Steuer nie negativ und mit dem Einkommen steigend. */
  R.tarifGueltig = function (zonen) {
    if (!Array.isArray(zonen) || !zonen.length) return false;
    var vorher = -Infinity;
    for (var i = 0; i < zonen.length; i++) {
      var z = zonen[i];
      if (!z || typeof z !== 'object') return false;
      var felder = ['ab', 'a', 'b', 'c', 'satz', 'abzug'];
      for (var k = 0; k < felder.length; k++) if (z[felder[k]] !== undefined && !isFinite(z[felder[k]])) return false;
      if (z.satz !== undefined && (z.satz < 0 || z.satz > 1)) return false;
      var letzte = i === zonen.length - 1;
      if (letzte !== (z.bis === undefined || z.bis === null)) return false;
      if (!letzte && !(isFinite(z.bis) && z.bis > vorher)) return false;
      if (!letzte) vorher = z.bis;
    }
    var t = { zonen: zonen }, alt = 0;
    for (var x = 0; x <= 2000000; x += 500) {
      var s = R.einkommensteuer(x, t);
      if (!isFinite(s) || s < 0 || s < alt - 1) return false;
      alt = s;
    }
    return true;
  };
  /** Solidaritaetszuschlag: 5,5 % mit Freigrenze des Jahres (Standard: laufendes Jahr) und Milderungszone 11,9 %. */
  R.soli = function (einkommensteuer, zusammenveranlagt, jahr) {
    var g = R.soliFreigrenzeFuer(jahr === undefined ? R.jahr() : jahr);
    var frei = zusammenveranlagt ? g.paar : g.einzeln;
    if (einkommensteuer <= frei) return 0;
    return Math.min((einkommensteuer - frei) * 0.119, einkommensteuer * R.wertFuer('soli', jahr === undefined ? R.jahr() : jahr).wert / 100);
  };
  R.grenzsteuersatz = function (zvE, tarif) {
    return (R.einkommensteuer(zvE + 100, tarif) - R.einkommensteuer(zvE, tarif)) / 100 * 100;
  };

  /* ---------- Steuerwerte aus der App ---------- */
  var BEREICHE = {
    abgeltung: [0, 60], soli: [0, 20], pauschbetrag: [0, 1e6], pauschbetragPaar: [0, 1e6], basiszins: [-5, 20],
    grundfreibetrag: [0, 1e6], soliFreigrenze: [0, 1e7], soliFreigrenzePaar: [0, 1e7]
  };
  /** Mischt den Inhalt von steuerwerte.json ein ({jahre: {JJJJ: {feld: wert, tarif: [...]}}}). Unbrauchbare Felder bleiben
      aussen vor, ein fehlendes Feld setzt nie einen Wert (also nie Basiszins 0). Gibt die Zahl der uebernommenen Werte zurueck. */
  R.steuerwerteEinmischen = function (d) {
    if (!d || typeof d !== 'object' || !d.jahre || typeof d.jahre !== 'object') return 0;
    var n = 0;
    Object.keys(d.jahre).forEach(function (k) {
      var e = d.jahre[k];
      if (!/^\d{4}$/.test(k) || !e || typeof e !== 'object') return;
      var j = parseInt(k, 10);
      Object.keys(BEREICHE).forEach(function (f) {
        var v = e[f], b = BEREICHE[f];
        if (typeof v === 'number' && isFinite(v) && v >= b[0] && v <= b[1]) { R.WERTE[f][j] = v; n++; }
      });
      if (e.tarif !== undefined && R.tarifGueltig(e.tarif)) {
        var gfb = typeof e.grundfreibetrag === 'number' ? e.grundfreibetrag : (e.tarif.length > 1 ? e.tarif[0].bis : 0);
        R.TARIFE[j] = { jahr: j, grundfreibetrag: gfb, zonen: e.tarif };
        n++;
      }
    });
    return n;
  };
  /** Holt data/steuerwerte.json einmal (die App liefert den geprueften Stand); bei jedem Fehler bleiben die eingebauten Werte. */
  R.laden = function () {
    if (R.bereit) return R.bereit;
    if (typeof fetch !== 'function') { R.bereit = Promise.resolve(0); return R.bereit; }
    R.bereit = fetch('data/steuerwerte.json', { cache: 'no-store' })
      .then(function (r) { return r.ok ? r.json() : null; })
      .then(function (d) { return R.steuerwerteEinmischen(d); })
      .catch(function () { return 0; });
    return R.bereit;
  };
  // Beim Start im Browser gleich holen; app.js zeichnet erst, wenn die Werte da sind
  if (typeof document !== 'undefined') R.laden();

  /* ---------- Portfolio ---------- */
  R.portfolioRendite = function (gewichte, renditen) {
    return gewichte.reduce(function (s, g, i) { return s + g * renditen[i]; }, 0);
  };
  R.portfolioVola = function (gewichte, volas, korrelation) {
    var v = 0;
    for (var i = 0; i < gewichte.length; i++)
      for (var j = 0; j < gewichte.length; j++)
        v += gewichte[i] * gewichte[j] * volas[i] * volas[j] * (i === j ? 1 : korrelation[i][j]);
    return Math.sqrt(Math.max(0, v));
  };
  R.sharpe = function (rendite, vola, risikolos) { return vola ? (rendite - risikolos) / vola : 0; };
  R.maxDrawdown = function (reihe) {
    var hoch = -Infinity, mdd = 0;
    reihe.forEach(function (v) { hoch = Math.max(hoch, v); mdd = Math.min(mdd, v / hoch - 1); });
    return mdd * 100;
  };
  /** Wieviel Plus braucht es, um ein Minus auszugleichen */
  R.erholung = function (verlustProzent) { return (1 / (1 + verlustProzent / 100) - 1) * 100; };

  /* ---------- Monte Carlo ---------- */
  R.monteCarlo = function (opt) {
    var pfade = opt.pfade || 500, jahre = opt.jahre || 30;
    var mu = opt.rendite / 100, sig = opt.vola / 100;
    var rnd = K.wuerfel(opt.saat || 12345);
    var ende = [], bahnen = [];
    for (var p = 0; p < pfade; p++) {
      var v = opt.start || 0, bahn = [v];
      for (var j = 0; j < jahre; j++) {
        var r = mu + sig * K.normal(rnd);
        v = v * (1 + r) + (opt.jaehrlich || 0) - (opt.entnahme || 0);
        if (opt.entnahmeDynamisch) v = Math.max(0, v);
        bahn.push(v);
      }
      bahnen.push(bahn); ende.push(v);
    }
    // Perzentilbaender je Jahr
    var perz = function (arr, q) {
      var s = arr.slice().sort(function (a, b) { return a - b; });
      var i = (s.length - 1) * q, lo = Math.floor(i), hi = Math.ceil(i);
      return s[lo] + (s[hi] - s[lo]) * (i - lo);
    };
    var p5 = [], p25 = [], p50 = [], p75 = [], p95 = [];
    for (var j2 = 0; j2 <= jahre; j2++) {
      var spalte = bahnen.map(function (b) { return b[j2]; });
      p5.push(perz(spalte, 0.05)); p25.push(perz(spalte, 0.25)); p50.push(perz(spalte, 0.5));
      p75.push(perz(spalte, 0.75)); p95.push(perz(spalte, 0.95));
    }
    var pleite = ende.filter(function (v) { return v <= 0; }).length / pfade;
    return {
      ende: ende, p5: p5, p25: p25, p50: p50, p75: p75, p95: p95,
      median: perz(ende, 0.5), schlecht: perz(ende, 0.1), gut: perz(ende, 0.9), pleiteQuote: pleite * 100
    };
  };

  /* ---------- Entnahme ---------- */
  R.entnahmeTest = function (vermoegen, entnahmeProzent, renditen, inflation, jahre) {
    var v = vermoegen, entnahme = vermoegen * entnahmeProzent / 100, verlauf = [v];
    for (var j = 0; j < jahre; j++) {
      var r = renditen[j % renditen.length] / 100;
      v = (v - entnahme) * (1 + r);
      entnahme *= (1 + (inflation || 0) / 100);
      verlauf.push(Math.max(0, v));
      if (v <= 0) { v = 0; }
    }
    return { verlauf: verlauf, endwert: v, pleite: v <= 0 };
  };

  /* ---------- Kennzahlen ---------- */
  R.kgv = function (kurs, gewinnJeAktie) { return gewinnJeAktie ? kurs / gewinnJeAktie : NaN; };
  R.dividendenrendite = function (dividende, kurs) { return kurs ? dividende / kurs * 100 : NaN; };
  R.dcf = function (opt) {
    var fcf = opt.fcf, w1 = opt.wachstum1 / 100, w2 = opt.wachstumEwig / 100, wacc = opt.wacc / 100;
    var jahre = opt.jahre || 10, bw = 0, letzter = fcf;
    var reihe = [];
    for (var t = 1; t <= jahre; t++) {
      letzter = letzter * (1 + w1);
      var d = letzter / Math.pow(1 + wacc, t);
      bw += d; reihe.push({ jahr: t, fcf: letzter, barwert: d });
    }
    var tv = (wacc > w2) ? letzter * (1 + w2) / (wacc - w2) : NaN;
    var tvBw = tv / Math.pow(1 + wacc, jahre);
    var unternehmenswert = bw + tvBw;
    var eigenkapital = unternehmenswert - (opt.nettoschulden || 0);
    return {
      // Die Formel fuer den Endwert gilt nur, wenn die Kapitalkosten ueber dem ewigen Wachstum liegen
      gueltig: wacc > w2,
      reihe: reihe, barwertPhase1: bw, terminalwert: tv, terminalBarwert: tvBw,
      unternehmenswert: unternehmenswert, eigenkapital: eigenkapital,
      jeAktie: opt.aktien ? eigenkapital / opt.aktien : NaN,
      anteilTerminal: unternehmenswert ? tvBw / unternehmenswert * 100 : NaN
    };
  };
  /** Kennzahlen je Aktie. Bei Verlust (Gewinn 0 oder kleiner) sind KGV, PEG und Ausschuettungsquote nicht aussagekraeftig (NaN). */
  R.kennzahlen = function (o) {
    function q(a, b) { return b ? a / b : NaN; }
    var verlust = !(o.gewinn > 0);
    var kgv = verlust ? NaN : q(o.kurs, o.gewinn);
    return {
      verlust: verlust, kgv: kgv, kbv: q(o.kurs, o.buch), kuv: q(o.kurs, o.umsatz),
      divRendite: q(o.dividende, o.kurs) * 100, payout: verlust ? NaN : q(o.dividende, o.gewinn) * 100,
      peg: verlust || !(o.wachstum > 0) ? NaN : kgv / o.wachstum, roe: q(o.gewinn, o.buch) * 100, gewinnRendite: q(o.gewinn, o.kurs) * 100
    };
  };
  R.wacc = function (ekQuote, ekKosten, fkKosten, steuersatz) {
    var e = ekQuote / 100;
    return e * ekKosten + (1 - e) * fkKosten * (1 - steuersatz / 100);
  };
  R.capm = function (risikolos, beta, marktpraemie) { return risikolos + beta * marktpraemie; };

  /* ---------- Schuldenabbau ---------- */
  /** Tilgt mehrere Schulden mit einer festen Monatsrate: erst die Mindestraten, der Rest in der gegebenen Reihenfolge.
      Hoechstens 50 Jahre; offen sagt, ob danach noch Schuld bleibt, zinsGedeckt, ob die Rate anfangs die Monatszinsen deckt. */
  R.schuldenTilgen = function (schulden, rate, reihenfolge) {
    var s = schulden.map(function (x) { return { name: x.name, rest: x.betrag, zins: x.zins, mindest: x.mindest }; });
    if (reihenfolge) s.sort(reihenfolge);
    function monatszins(x) { return x.rest * (Math.pow(1 + x.zins / 100, 1 / 12) - 1); }
    var zinsAnfang = s.reduce(function (a, x) { return a + Math.max(0, monatszins(x)); }, 0);
    var monat = 0, zinsSumme = 0, verlauf = [], MAX = 600;
    while (s.some(function (x) { return x.rest > 0.01; }) && monat < MAX) {
      var frei = rate;
      s.forEach(function (x) {
        if (x.rest <= 0) return;
        var z = monatszins(x);
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
      verlauf.push([monat / 12, s.reduce(function (a, x) { return a + Math.max(0, x.rest); }, 0)]);
    }
    var rest = s.reduce(function (a, x) { return a + Math.max(0, x.rest); }, 0);
    return { monate: monat, zins: zinsSumme, verlauf: verlauf, offen: rest > 0.01, rest: rest, zinsGedeckt: rate > zinsAnfang, zinsAnfang: zinsAnfang };
  };

  /* ---------- Positionsgroesse / Trading ---------- */
  R.positionsgroesse = function (kapital, risikoProzent, einstieg, stop) {
    var risikoEuro = kapital * risikoProzent / 100;
    var jeStueck = Math.abs(einstieg - stop);
    var stueck = jeStueck > 0 ? Math.floor(risikoEuro / jeStueck) : 0;
    return { risikoEuro: risikoEuro, stueck: stueck, kapitaleinsatz: stueck * einstieg, anteil: kapital ? stueck * einstieg / kapital * 100 : 0 };
  };
  R.erwartungswert = function (trefferquote, gewinn, verlust) {
    var p = trefferquote / 100;
    return p * gewinn - (1 - p) * Math.abs(verlust);
  };
  R.kelly = function (trefferquote, chanceRisiko) {
    var p = trefferquote / 100, b = chanceRisiko;
    return Math.max(0, (p * (b + 1) - 1) / b) * 100;
  };

  /* ---------- Inflation ---------- */
  R.kaufkraft = function (betrag, inflationProzent, jahre) {
    return betrag / Math.pow(1 + inflationProzent / 100, jahre);
  };
})();
