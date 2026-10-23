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

  /* ---------- Deutsche Steuer auf Kapitalertraege ---------- */
  R.STEUER = {
    abgeltung: 25,          // Prozent
    soli: 5.5,              // Prozent auf die Abgeltungsteuer
    pauschbetrag: 1000,     // Euro, Einzelveranlagung (Stand 2026)
    pauschbetragPaar: 2000,
    teilfrei: { aktienfonds: 30, mischfonds: 15, immobilien: 60, immobilienAusland: 80, keiner: 0 }
  };
  /** Steuer auf einen Kapitalertrag. kirche in Prozent (0, 8 oder 9). */
  R.kapSteuer = function (ertrag, opt) {
    opt = opt || {};
    var teilfrei = opt.teilfreistellung || 0;
    var kirche = opt.kirche || 0;
    var frei = opt.pauschbetrag === undefined ? 0 : opt.pauschbetrag;
    var stpfl = Math.max(0, ertrag * (1 - teilfrei / 100) - frei);
    // Kirchensteuer mindert die Bemessungsgrundlage (§32d Abs. 1 EStG)
    var satz = kirche ? (0.25 / (1 + 0.25 * kirche / 100)) : 0.25;
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

  /* ---------- Einkommensteuer Deutschland (Tarif) ---------- */
  /** Einkommensteuertarif 2026 nach §32a Abs. 1 EStG (Fassung fuer VZ 2026 ff.). */
  R.TARIF = {
    jahr: 2026,
    grundfreibetrag: 12348,
    zone2: { bis: 17799, a: 914.51, b: 1400 },
    zone3: { bis: 69878, a: 173.10, b: 2397, c: 1034.87 },
    zone4: { bis: 277825, satz: 0.42, abzug: 11135.63 },
    zone5: { satz: 0.45, abzug: 19470.38 }
  };
  R.einkommensteuer = function (zvE, tarif) {
    var T = tarif || R.TARIF;
    var x = Math.floor(zvE);
    if (x <= T.grundfreibetrag) return 0;
    if (x <= T.zone2.bis) {
      var y = (x - T.grundfreibetrag) / 10000;
      return Math.floor((T.zone2.a * y + T.zone2.b) * y);
    }
    if (x <= T.zone3.bis) {
      var z = (x - T.zone2.bis) / 10000;
      return Math.floor((T.zone3.a * z + T.zone3.b) * z + T.zone3.c);
    }
    if (x <= T.zone4.bis) return Math.floor(T.zone4.satz * x - T.zone4.abzug);
    return Math.floor(T.zone5.satz * x - T.zone5.abzug);
  };
  /** Solidaritaetszuschlag: 5,5 %, Freigrenze 20.350 Euro Einkommensteuer, Milderungszone 11,9 %. */
  R.soli = function (einkommensteuer, zusammenveranlagt) {
    var frei = zusammenveranlagt ? 40700 : 20350;
    if (einkommensteuer <= frei) return 0;
    return Math.min((einkommensteuer - frei) * 0.119, einkommensteuer * 0.055);
  };
  R.grenzsteuersatz = function (zvE, tarif) {
    return (R.einkommensteuer(zvE + 100, tarif) - R.einkommensteuer(zvE, tarif)) / 100 * 100;
  };

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
      reihe: reihe, barwertPhase1: bw, terminalwert: tv, terminalBarwert: tvBw,
      unternehmenswert: unternehmenswert, eigenkapital: eigenkapital,
      jeAktie: opt.aktien ? eigenkapital / opt.aktien : NaN,
      anteilTerminal: unternehmenswert ? tvBw / unternehmenswert * 100 : NaN
    };
  };
  R.wacc = function (ekQuote, ekKosten, fkKosten, steuersatz) {
    var e = ekQuote / 100;
    return e * ekKosten + (1 - e) * fkKosten * (1 - steuersatz / 100);
  };
  R.capm = function (risikolos, beta, marktpraemie) { return risikolos + beta * marktpraemie; };

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
