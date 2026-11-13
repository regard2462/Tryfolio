// Finanzwissen: Jahreswerte, Steuerwerte aus der App und Rechtsstand mit vorgestellter Uhr (ein, drei, fuenf Jahre).
// Aufruf: node --test app/src/test/js/alterung.test.mjs  (vom Wurzelordner des Projekts)
import test from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import fs from 'node:fs';
import vm from 'node:vm';

const hier = path.dirname(fileURLToPath(import.meta.url));
const wurzel = path.join(hier, '..', '..', 'main', 'assets', 'finanzwissen');
const quelle = fs.readFileSync(path.join(wurzel, 'js', 'rechner.js'), 'utf8');
const steuerwerte = JSON.parse(fs.readFileSync(path.join(hier, '..', '..', 'main', 'assets', 'steuerwerte.json'), 'utf8'));

// Jede Pruefung bekommt ein frisches R (eigener Kontext), damit eingemischte Werte nicht durchsickern
function frisch(extra) {
  const ctx = vm.createContext(Object.assign({}, extra || {}));
  ctx.window = ctx;
  vm.runInContext(quelle, ctx, { filename: 'rechner.js' });
  return ctx.R;
}
const json = (x) => JSON.parse(JSON.stringify(x));
function am(R, tag) { R.heute = () => new Date(tag + 'T12:00:00Z'); return R; }

// Der bisherige Aufbau mit festen Zonen 2 bis 5 (Fassung 0.74), als Vergleich fuer den Tarif 2026
function alt2026(zvE) {
  const x = Math.floor(zvE);
  if (x <= 12348) return 0;
  if (x <= 17799) { const y = (x - 12348) / 10000; return Math.floor((914.51 * y + 1400) * y); }
  if (x <= 69878) { const z = (x - 17799) / 10000; return Math.floor((173.10 * z + 2397) * z + 1034.87); }
  if (x <= 277825) return Math.floor(0.42 * x - 11135.63);
  return Math.floor(0.45 * x - 19470.38);
}

test('Tarif und Basiszins im Jahr der Daten sind nicht geschaetzt', () => {
  const R = am(frisch(), '2026-11-10');
  assert.equal(R.TARIF.jahr, 2026);
  assert.equal(R.tarifFuer(R.jahr()).geschaetzt, false);
  assert.equal(R.basiszinsFuer(R.jahr()).wert, 3.20);
  assert.equal(R.basiszinsFuer(R.jahr()).geschaetzt, false);
  assert.equal(R.pauschbetragFuer(R.jahr()).einzeln, 1000);
  assert.equal(R.pauschbetragFuer(R.jahr()).paar, 2000);
  assert.equal(R.STEUER.pauschbetrag, 1000);
  assert.equal(R.STEUER.abgeltung, 25);
});

for (const [name, tag] of [['ein Jahr', '2027-11-10'], ['drei Jahre', '2029-11-10'], ['fuenf Jahre', '2031-11-10']]) {
  test('nach ' + name + ' rechnet der Tarif weiter und meldet das Jahr als geschaetzt', () => {
    const R = am(frisch(), tag);
    const t = R.tarifFuer(R.jahr());
    assert.equal(t.geschaetzt, true);
    assert.equal(t.jahr, 2026);
    assert.equal(R.TARIF.jahr, 2026);
    assert.equal(R.einkommensteuer(45000), 8835);
    const b = R.basiszinsFuer(R.jahr());
    assert.equal(b.geschaetzt, true);
    assert.equal(b.jahr, 2026);
    assert.ok(R.grenzsteuersatz(45000) > 0);
    assert.equal(R.pauschbetragFuer(R.jahr()).geschaetzt, true);
    assert.equal(R.soliFreigrenzeFuer(R.jahr()).einzeln, 20350);
    assert.equal(R.soli(20350, false), 0);
    assert.ok(R.soli(30000, false) > 0);
  });
}

test('Tarif als Zonenliste gibt fuer 2026 genau die bisherigen Werte', () => {
  const R = frisch();
  const t = R.TARIFE[2026];
  for (let x = -100; x <= 400000; x += 7) assert.equal(R.einkommensteuer(x, t), alt2026(x), 'zvE ' + x);
  for (const x of [12348, 12349, 17799, 17800, 69878, 69879, 277825, 277826, 1e6, 12348.99]) assert.equal(R.einkommensteuer(x, t), alt2026(x), 'zvE ' + x);
  // Tabellenwerte Grundtabelle 2026
  assert.equal(R.einkommensteuer(12348, t), 0);
  assert.equal(R.einkommensteuer(45000, t), 8835);
  assert.ok(R.tarifGueltig(t.zonen));
});

test('Rechtsstand meldet sich ab dem 1. Januar des Folgejahres', () => {
  const R = frisch();
  am(R, '2026-12-31');
  assert.equal(R.rechtsstandAlt('2026-09-01'), false);
  am(R, '2027-01-01');
  assert.equal(R.rechtsstandAlt('2026-09-01'), true);
  assert.equal(R.rechtsstandAlt('2026-12'), true);
  assert.equal(R.rechtsstandAlt('2027-01-01'), false);
  am(R, '2031-11-10');
  assert.equal(R.rechtsstandAlt('2026-09-01'), true);
  am(R, '2020-01-01');
  assert.equal(R.rechtsstandAlt('2026-09-01'), false);
  assert.equal(R.rechtsstandAlt('kaputt'), false);
  assert.equal(R.rechtsstandAlt(''), false);
  // Volle Monate bleiben fuer andere Zwecke
  am(R, '2027-09-01');
  assert.equal(R.monateSeit('2026-09-01'), 12);
});

test('steuerwerte.json der App passt zu den eingebauten Werten', () => {
  const R = frisch();
  const vorher = json({ werte: R.WERTE, tarife: R.TARIFE });
  assert.ok(R.steuerwerteEinmischen(steuerwerte) > 0);
  assert.deepEqual(json({ werte: R.WERTE, tarife: R.TARIFE }), vorher);
  // Und umgekehrt: jedes Feld der Datei steht so auch eingebaut im Rechner
  for (const [j, e] of Object.entries(steuerwerte.jahre)) {
    for (const [f, v] of Object.entries(e)) {
      if (f === 'tarif') assert.deepEqual(json(R.TARIFE[j].zonen), v, j);
      else assert.equal(R.WERTE[f][j], v, j + ' ' + f);
    }
  }
});

test('ein Jahr ohne Basiszins nimmt den des Vorjahrs und ist geschaetzt, nie 0', () => {
  const R = am(frisch(), '2027-03-01');
  R.steuerwerteEinmischen({ jahre: { 2027: { abgeltung: 25, soli: 5.5, pauschbetrag: 1100, pauschbetragPaar: 2200, basiszins: null } } });
  const b = R.basiszinsFuer(2027);
  assert.equal(b.wert, 3.20);
  assert.equal(b.geschaetzt, true);
  const p = R.pauschbetragFuer(2027);
  assert.equal(p.einzeln, 1100);
  assert.equal(p.paar, 2200);
  assert.equal(p.geschaetzt, false);
  assert.equal(R.STEUER.pauschbetrag, 1100);
  // Tarif 2027 fehlt: der von 2026, geschaetzt
  assert.equal(R.tarifFuer(2027).jahr, 2026);
  assert.equal(R.tarifFuer(2027).geschaetzt, true);
});

test('ein Tarif mit weiteren Stufen bis 47 Prozent passt in die Zonenliste', () => {
  const R = am(frisch(), '2028-06-01');
  // Erfundene Werte, nur fuer den Aufbau
  const zonen = [{ bis: 13000 }, { bis: 18000, ab: 13000, a: 900, b: 1400 }, { bis: 70000, ab: 18000, a: 170, b: 2400, c: 1000 },
    { bis: 200000, satz: 0.42, abzug: 11000 }, { bis: 280000, satz: 0.45, abzug: 17000 }, { satz: 0.47, abzug: 22600 }];
  assert.ok(R.steuerwerteEinmischen({ jahre: { 2028: { tarif: zonen, grundfreibetrag: 13000, soliFreigrenze: 21000, soliFreigrenzePaar: 42000 } } }) >= 4);
  assert.equal(R.tarifFuer(2028).geschaetzt, false);
  assert.equal(R.TARIF.grundfreibetrag, 13000);
  assert.equal(R.einkommensteuer(300000), Math.floor(0.47 * 300000 - 22600));
  assert.equal(R.einkommensteuer(250000), Math.floor(0.45 * 250000 - 17000));
  assert.equal(R.einkommensteuer(13000), 0);
  // 2026 rechnet weiter mit dem Tarif 2026
  assert.equal(R.einkommensteuer(45000, R.tarifFuer(2026).tarif), 8835);
  // Soli-Freigrenze des Jahres
  assert.equal(R.soli(21000, false), 0);
  assert.ok(R.soli(21000, false, 2026) > 0);
  assert.equal(R.soli(42000, true), 0);
});

test('unbrauchbare Werte aus der Datei bleiben aussen vor', () => {
  const R = frisch();
  const vorher = json({ werte: R.WERTE, tarife: R.TARIFE });
  assert.equal(R.steuerwerteEinmischen(null), 0);
  assert.equal(R.steuerwerteEinmischen('kaputt'), 0);
  assert.equal(R.steuerwerteEinmischen({ jahre: {
    zwanzig: { basiszins: 1 },
    2027: { basiszins: 'drei', abgeltung: 250, pauschbetrag: -1, tarif: [{ bis: 20000 }, { bis: 10000 }, { satz: 0.45 }] },
    2028: { tarif: [{ bis: 20000 }, { bis: 30000 }] },
    2029: { tarif: [{ bis: 20000 }, { satz: 1.5 }] },
    2030: { tarif: [] }
  } }), 0);
  assert.deepEqual(json({ werte: R.WERTE, tarife: R.TARIFE }), vorher);
});

test('Laden der Datei: ohne fetch, bei Fehler und mit Datei', async () => {
  const ohne = frisch();
  assert.equal(await ohne.laden(), 0);

  const fehler = frisch({ fetch: () => Promise.reject(new Error('kein Netz')) });
  assert.equal(await fehler.laden(), 0);
  assert.equal(fehler.basiszinsFuer(2026).wert, 3.20);

  const leer = frisch({ fetch: () => Promise.resolve({ ok: false, json: () => Promise.resolve(null) }) });
  assert.equal(await leer.laden(), 0);

  const mitDatei = { jahre: { 2027: { abgeltung: 25, soli: 5.5, pauschbetrag: 1000, pauschbetragPaar: 2000, basiszins: 2.9 } } };
  let pfad = null;
  const gut = frisch({ fetch: (p) => { pfad = p; return Promise.resolve({ ok: true, json: () => Promise.resolve(mitDatei) }); } });
  assert.ok(await gut.laden() > 0);
  assert.equal(pfad, 'data/steuerwerte.json');
  am(gut, '2027-02-01');
  assert.equal(gut.basiszinsFuer(2027).wert, 2.9);
  assert.equal(gut.basiszinsFuer(2027).geschaetzt, false);
  // Einmal je Start
  assert.equal(gut.laden(), gut.laden());
});

test('jede Lektion mit Rechtsstand hat ein lesbares Datum', () => {
  for (const ordner of ['lektionen', 'en/lektionen', 'us/lektionen', 'en/us/lektionen']) {
    const dir = path.join(wurzel, 'data', ordner);
    for (const f of fs.readdirSync(dir)) {
      const o = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
      if (o.rechtsstand !== undefined) assert.match(o.rechtsstand, /^\d{4}-\d{2}-\d{2}$/, f);
    }
  }
});

test('alle Lektionen der US-Fassung tragen einen Rechtsstand, in beiden Sprachen', () => {
  for (const ordner of ['us/lektionen', 'en/us/lektionen']) {
    const dir = path.join(wurzel, 'data', ordner);
    for (const f of fs.readdirSync(dir)) {
      const o = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
      assert.ok(o.rechtsstand, ordner + '/' + f);
    }
  }
});

test('englische Lektionen tragen denselben Rechtsstand wie die deutschen', () => {
  for (const [de, en] of [['lektionen', 'en/lektionen'], ['us/lektionen', 'en/us/lektionen']]) {
    const dirDe = path.join(wurzel, 'data', de), dirEn = path.join(wurzel, 'data', en);
    for (const f of fs.readdirSync(dirDe)) {
      const p = path.join(dirEn, f);
      if (!fs.existsSync(p)) continue;
      const a = JSON.parse(fs.readFileSync(path.join(dirDe, f), 'utf8')).rechtsstand;
      const b = JSON.parse(fs.readFileSync(p, 'utf8')).rechtsstand;
      assert.equal(b, a, en + '/' + f);
    }
  }
});

test('Lektionen mit jaehrlich festgesetzten Werten tragen einen Rechtsstand', () => {
  // Begriffe mit Werten, die jedes Jahr neu festgesetzt werden, zusammen mit einer Jahreszahl
  const stark = /Pauschbetrag|Basiszins|Bemessungsgrenze|Grundfreibetrag|Kindergeld|Vorabpauschale|Zusatzbeitrag|Mindestlohn|Minijob|Rentenwert|Kinderfreibetrag|Freigrenze/i;
  const jahr = /\b20(2[3-9])\b/;
  const dir = path.join(wurzel, 'data', 'lektionen');
  for (const f of fs.readdirSync(dir)) {
    const roh = fs.readFileSync(path.join(dir, f), 'utf8');
    if (stark.test(roh) && jahr.test(roh)) assert.ok(JSON.parse(roh).rechtsstand, f);
  }
});
