// Finanzwissen: Rechen-Smoke-Test fuer alle Rechner. Die Funktionen aus rechner.js mit typischen Eingaben, dazu jeder
// Rechner aus werkzeuge.js und werkzeuge2.js in einem kleinen Ersatz-DOM: aufbauen, jeden Schieber an beide Enden,
// jede Auswahl und jeden Schalter durchgehen. Kein Ergebnis darf NaN, Infinity oder unendlich zeigen.
// Aufruf: node --test app/src/test/js/rechner.test.mjs  (vom Wurzelordner des Projekts)
import test from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import fs from 'node:fs';
import vm from 'node:vm';

const hier = path.dirname(fileURLToPath(import.meta.url));
const js = path.join(hier, '..', '..', 'main', 'assets', 'finanzwissen', 'js');
const lies = (f) => fs.readFileSync(path.join(js, f), 'utf8');

/* ---------- Ersatz-DOM: nur, was die Rechner brauchen ---------- */
class Knoten {
  constructor(tag) {
    this.tagName = String(tag).toUpperCase();
    this.children = []; this.attrs = {}; this.style = {}; this.lauscher = {};
    this.text = ''; this.html = ''; this.className = ''; this.parentNode = null;
    this._value = ''; this.selected = false; this.hidden = false;
    const k = this;
    this.classList = {
      add: (c) => { if (!k.hat(c)) k.className = (k.className + ' ' + c).trim(); },
      remove: (c) => { k.className = k.className.split(/\s+/).filter((x) => x && x !== c).join(' '); },
      toggle: (c, an) => { const soll = an === undefined ? !k.hat(c) : !!an; if (soll) k.classList.add(c); else k.classList.remove(c); return soll; },
      contains: (c) => k.hat(c)
    };
  }
  hat(c) { return this.className.split(/\s+/).indexOf(c) >= 0; }
  appendChild(c) { if (c) { c.parentNode = this; this.children.push(c); } return c; }
  setAttribute(a, v) { this.attrs[a] = String(v); if (a === 'value') this._value = String(v); if (a === 'class') this.className = String(v); }
  getAttribute(a) { return a in this.attrs ? this.attrs[a] : null; }
  addEventListener(t, f) { (this.lauscher[t] = this.lauscher[t] || []).push(f); }
  removeEventListener() {}
  ausloesen(t) { (this.lauscher[t] || []).forEach((f) => f({ target: this, preventDefault() {}, stopPropagation() {} })); }
  set textContent(v) { this.text = String(v); this.children = []; }
  get textContent() { return this.text + this.children.map((c) => c.textContent).join(''); }
  set innerHTML(v) { this.html = String(v); this.children = []; this.text = ''; }
  get innerHTML() { return this.html; }
  get value() {
    if (this.tagName === 'SELECT') { const o = this.options[this.selectedIndex]; return o ? o.value : ''; }
    return this._value;
  }
  set value(v) {
    if (this.tagName === 'SELECT') { this.children.forEach((o) => { o.selected = String(o.value) === String(v); }); return; }
    this._value = String(v);
  }
  get options() { return this.children.filter((c) => c.tagName === 'OPTION'); }
  get selectedIndex() { const i = this.options.findIndex((o) => o.selected); return i < 0 ? 0 : i; }
  querySelector() { return null; }
  querySelectorAll() { return []; }
  alle(test) { const r = []; const lauf = (n) => { if (test(n)) r.push(n); n.children.forEach(lauf); }; lauf(this); return r; }
}
const document = {
  createElement: (t) => new Knoten(t),
  createTextNode: (s) => { const n = new Knoten('#text'); n.text = String(s); return n; },
  getElementById: () => new Knoten('div'),
  documentElement: new Knoten('html')
};

/* ---------- Seite laden ---------- */
function seite(speicher) {
  const ctx = vm.createContext({
    document, location: { search: '' }, console, Intl,
    localStorage: speicher || { getItem: () => null, setItem: () => {}, removeItem: () => {} },
    setTimeout: (f) => { f(); return 0; }, clearTimeout: () => {}
  });
  ctx.window = ctx;
  const viz = [], notizen = [], ergebnisse = [];
  ctx.Viz = { zeichne: (spez) => { viz.push(spez); return new Knoten('div'); } };
  ctx.Mark = { block: (s) => String(s), inline: (s) => String(s) };
  ctx.Bloecke = { bauen: () => new Knoten('div') };
  for (const f of ['kern.js', 'rechner.js', 'werkzeuge.js']) vm.runInContext(lies(f), ctx, { filename: f });
  // Ergebnisfelder und Hinweise mitschreiben
  const ui = ctx.Werkzeuge.ui;
  const ergebnis = ui.ergebnis, notiz = ui.notiz;
  ui.ergebnis = function (w) { const e = ergebnis(w); const s = e.setzen; e.setzen = function (f) { ergebnisse.push(f.filter(Boolean)); return s(f); }; return e; };
  ui.notiz = function (w, t, art) { const n = notiz(w, t, art); const s = n.setzen; notizen.push(t); n.setzen = function (x) { notizen.push(x); return s(x); }; return n; };
  vm.runInContext(lies('werkzeuge2.js'), ctx, { filename: 'werkzeuge2.js' });
  return { ctx, R: ctx.R, K: ctx.K, W: ctx.Werkzeuge, viz, notizen, ergebnisse };
}

function bauen(s, id) {
  const w = new Knoten('div');
  s.W.aktiv = id;
  try { s.W.liste[id].bauen(w, {}); } finally { s.W.aktiv = null; }
  return w;
}

const KAPUTT = /NaN|Infinity|∞/;
// Alle Zahlen in einer Diagrammbeschreibung endlich?
function zahlenEndlich(x, weg) {
  if (typeof x === 'number') { if (!isFinite(x)) return weg; return null; }
  if (Array.isArray(x)) { for (let i = 0; i < x.length; i++) { const r = zahlenEndlich(x[i], weg + '[' + i + ']'); if (r) return r; } return null; }
  if (x && typeof x === 'object') { for (const k of Object.keys(x)) { const r = zahlenEndlich(x[k], weg + '.' + k); if (r) return r; } }
  return null;
}
function pruefen(s, id, wo, strengDefault) {
  const felder = s.ergebnisse[s.ergebnisse.length - 1] || [];
  for (const f of felder) {
    assert.ok(!KAPUTT.test(String(f.wert)), id + ' ' + wo + ': ' + f.label + ' = ' + f.wert);
    if (strengDefault) assert.notEqual(String(f.wert), '–', id + ' ' + wo + ': ' + f.label);
  }
  for (const n of s.notizen) assert.ok(!KAPUTT.test(String(n)), id + ' ' + wo + ': Hinweis ' + n);
  for (const v of s.viz) {
    const r = zahlenEndlich(v, 'viz');
    assert.equal(r, null, id + ' ' + wo + ': ' + r + ' in ' + (v.titel || v.art));
  }
  s.viz.length = 0;
}

/* ---------- 1. Funktionen aus rechner.js ---------- */
test('rechner.js: typische Eingaben ergeben endliche Zahlen', () => {
  const s = seite();
  const R = s.R;
  const endlich = (x, name) => assert.ok(typeof x === 'number' && isFinite(x), name + ' = ' + x);
  endlich(R.endwert(1000, 5, 10), 'endwert');
  endlich(R.barwert(1000, 5, 10), 'barwert');
  endlich(R.sparplan(1000, 100, 6, 20), 'sparplan');
  endlich(R.sparplan(0, 100, 0, 20, true), 'sparplan ohne Zins');
  endlich(R.sparplanVerlauf(1000, 250, 7, 30, 2).endwert, 'sparplanVerlauf');
  endlich(R.rateFuerZiel(500000, 0, 6, 25), 'rateFuerZiel');
  endlich(R.rateFuerZiel(100000, 0, 0, 10), 'rateFuerZiel ohne Zins');
  endlich(R.regel72(6), 'regel72');
  endlich(R.cagr(100, 200, 10), 'cagr');
  endlich(R.realzins(5, 2), 'realzins');
  endlich(R.npv([-1000, 300, 400, 500], 5), 'npv');
  endlich(R.irr([-1000, 300, 400, 500]), 'irr');
  const t = R.tilgungsplan(350000, 3.6, 2, 30, 0);
  endlich(t.rate, 'tilgungsplan'); endlich(t.zinsSumme, 'tilgungsplan Zinsen');
  endlich(R.laufzeitMitSonder(350000, 3.6, 2, 5000), 'laufzeitMitSonder');
  endlich(R.laufzeitJahre(350000, 3.6, 2), 'laufzeitJahre');
  endlich(R.anleiheKurs(100, 2.5, 3, 10, 1), 'anleiheKurs');
  endlich(R.anleiheRendite(95, 100, 2.5, 10, 1), 'anleiheRendite');
  endlich(R.duration(100, 2.5, 3, 10, 1).modified, 'duration');
  const o = R.blackScholes(100, 100, 0.25, 25, 2.5, 0, 'call');
  ['preis', 'delta', 'gamma', 'vega', 'theta', 'rho'].forEach((k) => endlich(o[k], 'blackScholes ' + k));
  endlich(R.blackScholes(100, 110, 0.5, 30, 2, 1, 'put').preis, 'blackScholes put');
  endlich(R.kapSteuer(10000, { teilfreistellung: 30, pauschbetrag: 1000, kirche: 9 }).gesamt, 'kapSteuer');
  endlich(R.vorabpauschale(50000, 54000, 3.2, 0, 30).vorabpauschale, 'vorabpauschale');
  endlich(R.einkommensteuer(45000), 'einkommensteuer');
  endlich(R.soli(30000, false), 'soli');
  endlich(R.grenzsteuersatz(45000), 'grenzsteuersatz');
  endlich(R.portfolioRendite([0.7, 0.3], [7, 2.5]), 'portfolioRendite');
  endlich(R.portfolioVola([0.7, 0.3], [17, 4], [[1, 0.1], [0.1, 1]]), 'portfolioVola');
  endlich(R.sharpe(6, 12, 2), 'sharpe');
  endlich(R.maxDrawdown([100, 120, 80, 130]), 'maxDrawdown');
  endlich(R.erholung(-50), 'erholung');
  const m = R.monteCarlo({ start: 20000, jaehrlich: 6000, rendite: 7, vola: 17, jahre: 25, pfade: 200, saat: 99 });
  endlich(m.median, 'monteCarlo'); endlich(m.pleiteQuote, 'monteCarlo pleite');
  endlich(R.entnahmeTest(800000, 4, [7, -10, 12], 2, 30).endwert, 'entnahmeTest');
  endlich(R.kgv(100, 6.5), 'kgv');
  endlich(R.dividendenrendite(2.4, 100), 'dividendenrendite');
  const d = R.dcf({ fcf: 1000, wachstum1: 6, wachstumEwig: 2, wacc: 8.5, jahre: 10, nettoschulden: 2000, aktien: 250 });
  assert.equal(d.gueltig, true);
  endlich(d.jeAktie, 'dcf');
  endlich(R.wacc(60, 9, 4, 30), 'wacc');
  endlich(R.capm(2.5, 1.1, 5), 'capm');
  endlich(R.positionsgroesse(50000, 1, 100, 92).stueck, 'positionsgroesse');
  endlich(R.erwartungswert(45, 2, 1), 'erwartungswert');
  endlich(R.kelly(45, 2), 'kelly');
  endlich(R.kaufkraft(1000, 2, 10), 'kaufkraft');
  const k = R.kennzahlen({ kurs: 100, gewinn: 6.5, buch: 32, umsatz: 55, dividende: 2.4, wachstum: 8 });
  ['kgv', 'kbv', 'kuv', 'divRendite', 'payout', 'peg', 'roe', 'gewinnRendite'].forEach((x) => endlich(k[x], 'kennzahlen ' + x));
  const sch = R.schuldenTilgen([{ name: 'a', betrag: 2000, zins: 11.5, mindest: 50 }], 500);
  endlich(sch.zins, 'schuldenTilgen');
});

/* ---------- 2. Die drei Rechenfehler ---------- */
test('F1 Schuldenabbau: Rate, die nicht tilgt, wird erkannt', () => {
  const s = seite();
  const schulden = [{ name: 'Dispo', betrag: 2000, zins: 11.5, mindest: 50 }, { name: 'Auto', betrag: 9000, zins: 6.9, mindest: 180 }, { name: 'Karte', betrag: 800, zins: 18, mindest: 25 }];
  const zuWenig = s.R.schuldenTilgen(schulden, 50, (a, b) => b.zins - a.zins);
  assert.equal(zuWenig.offen, true);
  assert.equal(zuWenig.zinsGedeckt, false);
  assert.equal(zuWenig.monate, 600);
  const reicht = s.R.schuldenTilgen(schulden, 500, (a, b) => b.zins - a.zins);
  assert.equal(reicht.offen, false);
  assert.ok(reicht.monate < 60);
  // Im Rechner: keine "600 Mon.", sondern der klare Hinweis
  const w = bauen(s, 'schulden');
  const schieber = w.alle((n) => n.tagName === 'INPUT' && n.attrs.type === 'range')[0];
  schieber.value = '50'; schieber.ausloesen('input');
  const felder = s.ergebnisse[s.ergebnisse.length - 1];
  assert.ok(!felder.some((f) => /600/.test(String(f.wert))), JSON.stringify(felder));
  assert.equal(felder[0].wert, 'über 50 J.');
  assert.ok(/decken nicht einmal die Zinsen/.test(s.notizen[s.notizen.length - 1]));
});

test('F2 Kennzahlen: bei Verlust kein gruenes KGV', () => {
  const s = seite();
  const k = s.R.kennzahlen({ kurs: 100, gewinn: -2, buch: 32, umsatz: 55, dividende: 0, wachstum: 8 });
  assert.equal(k.verlust, true);
  assert.ok(Number.isNaN(k.kgv));
  assert.ok(Number.isNaN(k.peg));
  const w = bauen(s, 'kennzahlen');
  const gewinn = w.alle((n) => n.tagName === 'INPUT' && n.attrs.type === 'number')[1];
  gewinn.value = '-2'; gewinn.ausloesen('input');
  const felder = s.ergebnisse[s.ergebnisse.length - 2];
  assert.equal(felder[0].wert, 'Verlust');
  assert.equal(felder[0].label, 'KGV nicht aussagekräftig');
  assert.ok(!felder[0].farbe);
  gewinn.value = '0'; gewinn.ausloesen('input');
  assert.equal(s.ergebnisse[s.ergebnisse.length - 2][0].wert, 'Verlust');
});

test('F3 DCF: Wachstum ueber den Kapitalkosten wird erklaert', () => {
  const s = seite();
  assert.equal(s.R.dcf({ fcf: 1000, wachstum1: 6, wachstumEwig: 4, wacc: 3.5, jahre: 10, aktien: 250 }).gueltig, false);
  const w = bauen(s, 'dcf');
  const sch = w.alle((n) => n.tagName === 'INPUT' && n.attrs.type === 'range');
  sch[2].value = '4'; sch[2].ausloesen('input');
  sch[3].value = '3'; sch[3].ausloesen('input');
  const felder = s.ergebnisse[s.ergebnisse.length - 1];
  assert.ok(felder.every((f) => f.wert === '–'));
  assert.ok(/nicht unter den Kapitalkosten/.test(s.notizen[s.notizen.length - 1]));
  // Knapp darueber: wieder ein Wert, das Balkendiagramm laesst ungueltige Kapitalkosten weg
  sch[3].value = '5'; sch[3].ausloesen('input');
  assert.ok(s.ergebnisse[s.ergebnisse.length - 1].every((f) => f.wert !== '–' && !KAPUTT.test(f.wert)));
});

/* ---------- 3. Eingaben bleiben erhalten ---------- */
test('Eingaben der Rechner werden gemerkt und beim naechsten Aufbau gesetzt', () => {
  const ablage = {};
  const speicher = { getItem: (k) => (k in ablage ? ablage[k] : null), setItem: (k, v) => { ablage[k] = String(v); }, removeItem: (k) => { delete ablage[k]; } };
  const s = seite(speicher);
  let w = bauen(s, 'zinseszins');
  let sch = w.alle((n) => n.tagName === 'INPUT' && n.attrs.type === 'range');
  sch[1].value = '1000'; sch[1].ausloesen('input');
  assert.equal(s.K.st.werkzeugStand.zinseszins.rate, 1000);
  assert.ok(JSON.parse(ablage['finanzwissen.v1']).werkzeugStand.zinseszins.rate === 1000);
  w = bauen(s, 'zinseszins');
  sch = w.alle((n) => n.tagName === 'INPUT' && n.attrs.type === 'range');
  assert.equal(sch[1].value, '1000');
  // Neuer Start der Seite liest den Stand aus dem Speicher
  const s2 = seite(speicher);
  const w2 = bauen(s2, 'zinseszins');
  assert.equal(w2.alle((n) => n.tagName === 'INPUT' && n.attrs.type === 'range')[1].value, '1000');
  // Schalter und Auswahl
  const a = bauen(s2, 'abgeltungsteuer');
  const knopf = a.alle((n) => n.tagName === 'BUTTON' && n.textContent === 'zusammen 2.000 €')[0];
  assert.ok(knopf, 'Knopf zusammen 2.000 €');
  knopf.ausloesen('click');
  const auswahl = a.alle((n) => n.tagName === 'SELECT')[0];
  auswahl.value = '60'; auswahl.ausloesen('change');
  assert.equal(s2.K.st.werkzeugStand.abgeltungsteuer.pausch, 2000);
  assert.equal(s2.K.st.werkzeugStand.abgeltungsteuer.art, 60);
  const a2 = bauen(s2, 'abgeltungsteuer');
  assert.ok(a2.alle((n) => n.tagName === 'BUTTON' && n.textContent === 'zusammen 2.000 €')[0].hat('an'));
  assert.equal(a2.alle((n) => n.tagName === 'SELECT')[0].value, '60');
  // Unpassende gemerkte Werte gelten nicht (Bereich, Auswahl), die Voreinstellung bleibt
  s2.K.st.werkzeugStand.zinseszins.rate = 999999;
  s2.K.st.werkzeugStand.abgeltungsteuer.pausch = 1234;
  assert.equal(bauen(s2, 'zinseszins').alle((n) => n.tagName === 'INPUT' && n.attrs.type === 'range')[1].value, '250');
  assert.ok(bauen(s2, 'abgeltungsteuer').alle((n) => n.tagName === 'BUTTON' && n.textContent === 'einzeln 1.000 €')[0].hat('an'));
  // Vergessen
  s2.W.vergessen('zinseszins');
  assert.equal(s2.W.gemerkt('zinseszins'), false);
  // Ohne Speicher (Schreibfehler) laeuft alles weiter
  const s3 = seite({ getItem: () => { throw new Error('gesperrt'); }, setItem: () => { throw new Error('voll'); }, removeItem: () => {} });
  const w3 = bauen(s3, 'sparziel');
  const x = w3.alle((n) => n.tagName === 'INPUT' && n.attrs.type === 'range')[0];
  x.value = '100000'; x.ausloesen('input');
});

/* ---------- 4. Jeder Rechner im Ersatz-DOM ---------- */
test('alle 26 Rechner sind angemeldet', () => {
  const s = seite();
  assert.equal(s.W.reihenfolge.length, 26);
  assert.equal(new Set(s.W.reihenfolge).size, 26);
});

const ids = seite().W.reihenfolge;
for (const id of ids) {
  test('Rechner ' + id + ': Aufbau und alle Eingaben ohne NaN oder Infinity', () => {
    const s = seite();
    const w = bauen(s, id);
    assert.ok(s.ergebnisse.length > 0 || s.viz.length > 0, id + ' rechnet beim Aufbau nicht');
    pruefen(s, id, 'Voreinstellung', true);
    const hinweis = s.W.liste[id].hinweis;
    if (hinweis) assert.ok(!KAPUTT.test(typeof hinweis === 'function' ? hinweis() : hinweis));
    for (const inp of w.alle((n) => n.tagName === 'INPUT' && n.attrs.type === 'range')) {
      const vorher = inp.value;
      for (const v of [inp.attrs.min, inp.attrs.max, vorher]) {
        inp.value = v; inp.ausloesen('input');
        pruefen(s, id, 'Schieber auf ' + v, false);
      }
    }
    for (const inp of w.alle((n) => n.tagName === 'INPUT' && n.attrs.type === 'number')) {
      const vorher = inp.value;
      for (const v of ['0', '-5', '1000000', vorher]) {
        inp.value = v; inp.ausloesen('input');
        pruefen(s, id, 'Zahl ' + v, false);
      }
    }
    for (const sel of w.alle((n) => n.tagName === 'SELECT')) {
      for (const o of sel.options) { sel.value = o.value; sel.ausloesen('change'); pruefen(s, id, 'Auswahl ' + o.value, false); }
    }
    for (const b of w.alle((n) => n.tagName === 'BUTTON')) { b.ausloesen('click'); pruefen(s, id, 'Schalter ' + b.textContent, false); }
  });
}

test('Pauschbetrag der Knoepfe kommt aus der Jahrestabelle', () => {
  const s = seite();
  s.R.heute = () => new Date('2027-06-01T12:00:00Z');
  s.R.steuerwerteEinmischen({ jahre: { 2027: { pauschbetrag: 1100, pauschbetragPaar: 2200 } } });
  const a = bauen(s, 'abgeltungsteuer');
  const texte = a.alle((n) => n.tagName === 'BUTTON').map((b) => b.textContent);
  assert.ok(texte.includes('einzeln 1.100 €') && texte.includes('zusammen 2.200 €'), texte.join(' | '));
  const v = bauen(s, 'vorabpauschale');
  assert.ok(v.alle((n) => n.tagName === 'BUTTON').some((b) => b.textContent === '2.200 €'));
  // Hinweis zum Tarif mit dem Jahr des Tarifs (2027 ohne eigenen: 2026)
  assert.match(s.W.liste.netto.hinweis(), /Grundtarif 2026/);
});
