/* ============================================================
   viz.js - Zeichenwerk: alle Diagramme der App, reines SVG,
   ohne Fremdbibliothek, an das Farbschema gekoppelt.
   Aufruf:  Viz.zeichne({art:'linie', ...})  ->  DOM-Knoten

   Fuer schmale Bildschirme gebaut: Beschriftungen werden gemessen,
   umbrochen oder gekuerzt und so gesetzt, dass sie sich nicht
   ueberdecken und nicht aus der Zeichenflaeche ragen. Lange
   Kategorienamen machen aus Saeulen liegende Balken, Legenden
   stehen als Liste unter dem Diagramm.
   ============================================================ */
(function () {
  'use strict';

  var NS = 'http://www.w3.org/2000/svg';
  var B = 340;            // Breite im viewBox-Raster
  var S = 10;             // Grundschrift der Beschriftungen im Raster
  var farbSpeicher = {};

  function farbe(name) {
    if (farbSpeicher[name]) return farbSpeicher[name];
    var v = getComputedStyle(document.documentElement).getPropertyValue('--' + name).trim();
    farbSpeicher[name] = v || '#888';
    return farbSpeicher[name];
  }
  function farbenNeu() { farbSpeicher = {}; }

  var REIHE = ['gold', 'blau', 'gruen', 'lila', 'orange', 'tuerkis', 'rot'];
  function serienFarbe(i, eigen) {
    if (eigen) return eigen.charAt(0) === '#' ? eigen : farbe(eigen);
    return farbe(REIHE[i % REIHE.length]);
  }

  function e(tag, attr) {
    var n = document.createElementNS(NS, tag);
    if (attr) for (var a in attr) if (attr[a] !== null && attr[a] !== undefined) n.setAttribute(a, attr[a]);
    return n;
  }
  function txt(x, y, s, attr) {
    var n = e('text', attr || {});
    n.setAttribute('x', x); n.setAttribute('y', y);
    n.textContent = s;
    return n;
  }

  /* ---------- Textmass ---------- */
  var mctx = null, familie = null;
  function breite(text, groesse, fett) {
    if (!mctx) {
      mctx = document.createElement('canvas').getContext('2d');
      familie = getComputedStyle(document.body || document.documentElement).fontFamily || 'sans-serif';
    }
    mctx.font = (fett ? '700 ' : '400 ') + (groesse || S) + 'px ' + familie;
    return mctx.measureText(String(text)).width;
  }

  /** Text auf hoechstens [max] Breite kuerzen (mit Auslassungspunkt). */
  function kuerzen(text, max, groesse, fett) {
    text = String(text);
    if (breite(text, groesse, fett) <= max) return text;
    var t = text;
    while (t.length > 1 && breite(t + '…', groesse, fett) > max) t = t.slice(0, -1);
    return t.replace(/\s+$/, '') + '…';
  }

  /** Ein zu langes Wort mit Trennstrich auf mehrere Zeilen verteilen. */
  function zerteilen(w, max, groesse, fett) {
    // Zu schmal zum sinnvollen Trennen: Wort bleibt ganz (der Aufrufer kuerzt bei Bedarf)
    if (!(max >= breite('Mmm-', groesse, fett))) return [w];
    var teile = [], rest = w, runden = 0;
    var vokal = /[aeiouyäöüAEIOUYÄÖÜ]/, kons = /[b-df-hj-np-tv-zßB-DF-HJ-NP-TV-Z]/;
    function gut(t, i) {
      // Trennung vor Konsonant mit folgendem Vokal (Ver-rech-nungs-kon-to), nicht in ch, ck, sch
      if (!kons.test(t.charAt(i)) || !vokal.test(t.charAt(i + 1))) return false;
      var davor = t.charAt(i - 1).toLowerCase(), hier = t.charAt(i).toLowerCase();
      if (davor === 'c' && (hier === 'h' || hier === 'k')) return false;
      if (hier === 'h' && t.slice(i - 2, i).toLowerCase() === 'sc') return false;
      return true;
    }
    while (rest && breite(rest, groesse, fett) > max && runden++ < 40) {
      var n = rest.length - 1;
      while (n > 3 && breite(rest.slice(0, n) + '-', groesse, fett) > max) n--;
      // Ein vorhandener Bindestrich ist die beste Trennstelle (US-Kern-|CPI statt US-Kern-C-|PI)
      var bind = rest.lastIndexOf('-', n - 1);
      if (bind >= 2) n = bind + 1;
      else for (var j = n; j >= 3; j--) { if (gut(rest, j)) { n = j; break; } }
      // nicht direkt hinter einem Bindestrich einen weiteren setzen
      var teil = rest.slice(0, n);
      teile.push(teil.charAt(teil.length - 1) === '-' ? teil : teil + '-');
      rest = rest.slice(n);
    }
    if (rest) teile.push(rest);
    return teile;
  }

  /** Text in Zeilen von hoechstens [max] Breite umbrechen; zu viele Zeilen werden gekuerzt. */
  function umbrechen(text, max, groesse, fett, maxZeilen) {
    var absaetze = String(text).split('\n');
    var zeilen = [];
    absaetze.forEach(function (abs) {
      var woerter = abs.split(/\s+/).filter(Boolean);
      var z = '';
      woerter.forEach(function (w) {
        var probe = z ? z + ' ' + w : w;
        if (breite(probe, groesse, fett) <= max) z = probe;
        else {
          if (z) zeilen.push(z);
          if (breite(w, groesse, fett) <= max) z = w;
          else {
            var teile = zerteilen(w, max, groesse, fett);
            for (var i = 0; i < teile.length - 1; i++) zeilen.push(teile[i]);
            z = teile[teile.length - 1];
          }
        }
      });
      if (z) zeilen.push(z);
    });
    if (maxZeilen && zeilen.length > maxZeilen) {
      var rest = zeilen.slice(maxZeilen - 1).join(' ');
      zeilen = zeilen.slice(0, maxZeilen - 1).concat([kuerzen(rest, max, groesse, fett)]);
    }
    return zeilen.length ? zeilen : [''];
  }

  /** Mehrzeiliger Text; [y] ist die Grundlinie der ersten Zeile. */
  function mehrzeilig(x, y, zeilen, attr, zh) {
    var t = e('text', attr || {});
    t.setAttribute('x', x); t.setAttribute('y', y);
    zeilen.forEach(function (z, i) {
      var sp = e('tspan', { x: x, dy: i ? (zh || S * 1.2) : 0 });
      sp.textContent = z;
      t.appendChild(sp);
    });
    return t;
  }

  /** Belegte Flaechen fuer Beschriftungen, damit sich nichts ueberdeckt. */
  function Platz(breiteMax, hoeheMax) { this.b = []; this.w = breiteMax; this.h = hoeheMax; }
  Platz.prototype.kasten = function (x, y, w, groesse, anker) {
    var l = anker === 'end' ? x - w : anker === 'middle' ? x - w / 2 : x;
    return { l: l, r: l + w, t: y - groesse * 0.9, u: y + groesse * 0.3 };
  };
  Platz.prototype.frei = function (k) {
    if (k.l < 0 || k.r > this.w || k.t < 0 || (this.h && k.u > this.h)) return false;
    for (var i = 0; i < this.b.length; i++) {
      var o = this.b[i];
      if (k.l < o.r + 1 && k.r > o.l - 1 && k.t < o.u + 1 && k.u > o.t - 1) return false;
    }
    return true;
  };
  Platz.prototype.belegen = function (k) { this.b.push(k); };
  /** Beschriftung an der ersten freien Stelle setzen; liefert den Knoten oder null. */
  Platz.prototype.setze = function (svg, text, kandidaten, groesse, stil) {
    var w = breite(text, groesse);
    for (var i = 0; i < kandidaten.length; i++) {
      var c = kandidaten[i];
      var k = this.kasten(c.x, c.y, w, groesse, c.anker);
      if (this.frei(k)) {
        this.belegen(k);
        var t = txt(c.x, c.y, text, { 'text-anchor': c.anker || 'start', class: 'wert' });
        if (stil) t.setAttribute('style', stil);
        svg.appendChild(t);
        return t;
      }
    }
    return null;
  };

  /* ---------- Achsenteilung ---------- */
  function huebsch(spanne, ziel) {
    var roh = spanne / Math.max(1, ziel);
    var mag = Math.pow(10, Math.floor(Math.log(roh) / Math.LN10));
    var rest = roh / mag;
    var s = rest > 5 ? 10 : rest > 2 ? 5 : rest > 1 ? 2 : 1;
    return s * mag;
  }
  function teilung(min, max, ziel) {
    if (!isFinite(min) || !isFinite(max)) return { min: 0, max: 1, schritt: 1, marken: [0, 1] };
    if (min === max) { min -= 1; max += 1; }
    var s = huebsch(max - min, ziel || 5);
    var a = Math.floor(min / s) * s, b = Math.ceil(max / s) * s;
    var m = [];
    for (var v = a; v <= b + s * 1e-6; v += s) m.push(Math.abs(v) < s * 1e-9 ? 0 : v);
    return { min: a, max: b, schritt: s, marken: m };
  }
  /** Englische Einheit, die schon eine Groessenordnung traegt (" €m", " $bn", " €k")? */
  function stufeInEinheit(einheit) {
    if (!K.en || !einheit) return false;
    var tab = window.TEXTE_EN || {};
    var en = Object.prototype.hasOwnProperty.call(tab, einheit) ? tab[einheit] : einheit;
    return /^[€$£]?(k|m|bn|tn)$/.test(String(en).trim());
  }
  /** Kurze Achsenzahl mit Einheit: Deutsch "1,2 Mrd €", "12k", "5,5 %"; Englisch "€1.2bn", "12k", "5.5%".
      Englisch wird bei Einheiten wie " €bn" ausgeschrieben ("€10,000bn" statt "€10kbn"). */
  function kurz(v, einheit) {
    var a = Math.abs(v);
    var s;
    if (a >= 1e4 && stufeInEinheit(einheit)) s = K.z(v, 0);
    else if (a >= 1e9) s = K.z(v / 1e9, a >= 1e10 ? 0 : 1) + K.t(' Mrd');
    else if (a >= 1e6) s = K.z(v / 1e6, a >= 1e7 ? 0 : 1) + K.t(' Mio');
    else if (a >= 1e4) s = K.z(v / 1e3, 0) + 'k';
    else if (a >= 100) s = K.z(v, 0);
    else if (a >= 10) s = K.z(v, a % 1 === 0 ? 0 : 1);
    else s = K.z(v, a % 1 === 0 ? 0 : (a < 1 ? 2 : 1));
    return K.mitEinheit(s, einheit);
  }

  /* ---------- Rahmen ---------- */
  /** Sicherheitsnetz: ragt trotz allem etwas ueber den Rand, wird die Zeichenflaeche erweitert. */
  function einpassen(svg) {
    try {
      // Nur Beschriftungen zaehlen (auch gedrehte); Linien und Hilfselemente bleiben aussen vor
      var r = svg.getBoundingClientRect();
      var vb = svg.viewBox.baseVal;
      if (!vb || !r.width || !r.height) return;
      var sx = vb.width / r.width, sy = vb.height / r.height;
      var x0 = vb.x, y0 = vb.y, x1 = vb.x + vb.width, y1 = vb.y + vb.height;
      Array.prototype.forEach.call(svg.querySelectorAll('text'), function (t) {
        var b = t.getBoundingClientRect();
        if (!b.width) return;
        x0 = Math.min(x0, vb.x + (b.left - r.left) * sx - 1);
        x1 = Math.max(x1, vb.x + (b.right - r.left) * sx + 1);
        y0 = Math.min(y0, vb.y + (b.top - r.top) * sy - 1);
        y1 = Math.max(y1, vb.y + (b.bottom - r.top) * sy + 1);
      });
      if (x0 < vb.x - 0.5 || y0 < vb.y - 0.5 || x1 > vb.x + vb.width + 0.5 || y1 > vb.y + vb.height + 0.5) {
        svg.setAttribute('viewBox', [x0, y0, x1 - x0, y1 - y0].join(' '));
      }
    } catch (err) { /* nicht sichtbar - spaeter erneut */ }
  }
  function spaeterEinpassen(svg, versuche) {
    if (typeof requestAnimationFrame !== 'function') return;
    requestAnimationFrame(function () {
      if (svg.isConnected) einpassen(svg);
      else if ((versuche || 0) < 30) spaeterEinpassen(svg, (versuche || 0) + 1);
    });
  }

  function rahmen(spez, svg, extras) {
    var d = K.el('div', { class: 'viz' });
    var innen = K.el('div', { class: 'viz-rahmen' });
    if (spez.titel) innen.appendChild(K.el('div', { class: 'viz-titel', text: spez.titel }));
    innen.appendChild(svg);
    if (extras) extras.forEach(function (x) { if (x) innen.appendChild(x); });
    d.appendChild(innen);
    if (spez.unter) d.appendChild(K.el('div', { class: 'viz-unter', html: Mark.inline(spez.unter) }));
    d._spez = spez;
    if (svg && svg.tagName && svg.tagName.toLowerCase() === 'svg') spaeterEinpassen(svg);
    return d;
  }

  function legende(serien) {
    var l = K.el('div', { class: 'legende' });
    serien.forEach(function (s, i) {
      var sp = K.el('span', {});
      var ic = K.el('i', {});
      ic.style.background = serienFarbe(i, s.farbe);
      sp.appendChild(ic);
      sp.appendChild(document.createTextNode(s.name || K.t('Reihe {0}', i + 1)));
      l.appendChild(sp);
    });
    return l;
  }

  /** Legende als Liste mit Wert rechts (Anteile, Punkte). */
  function listenLegende(eintraege) {
    var l = K.el('div', { class: 'legende-liste' });
    eintraege.forEach(function (x) {
      var z = K.el('div', { class: 'll-zeile' });
      var ic = K.el('i', {});
      ic.style.background = x.farbe;
      z.appendChild(ic);
      z.appendChild(K.el('span', { class: 'll-name', text: x.name }));
      if (x.wert !== undefined) z.appendChild(K.el('b', { text: x.wert }));
      l.appendChild(z);
    });
    return l;
  }

  /* ---------- Werkzeug: Kartesische Flaeche ---------- */
  function flaeche(opt) {
    var H = opt.hoehe || 200;
    var l = opt.links === undefined ? 38 : opt.links;
    var r = opt.rechts === undefined ? 10 : opt.rechts;
    var o = opt.oben === undefined ? 12 : opt.oben;
    var u = opt.unten === undefined ? 26 : opt.unten;
    var w = B - l - r, h = H - o - u;
    return {
      H: H, l: l, r: r, o: o, u: u, w: w, h: h,
      x: function (v) { return l + (v - opt.xMin) / (opt.xMax - opt.xMin || 1) * w; },
      y: function (v) { return o + h - (v - opt.yMin) / (opt.yMax - opt.yMin || 1) * h; }
    };
  }

  /** Linker Rand nach der breitesten Achsenbeschriftung. */
  function randFuer(beschriftungen) {
    var m = 0;
    beschriftungen.forEach(function (s) { m = Math.max(m, breite(s)); });
    return Math.max(24, Math.min(90, Math.ceil(m) + 9));
  }

  function gitterY(svg, f, marken, text, nullBetonen, platz) {
    marken.forEach(function (v) {
      var y = f.y(v);
      if (y < f.o - 1 || y > f.o + f.h + 1) return;
      svg.appendChild(e('line', { x1: f.l, x2: f.l + f.w, y1: y, y2: y, class: (nullBetonen && v === 0) ? 'achse' : 'gitter' }));
      var s = text(v);
      var t = txt(f.l - 5, y + 3.4, s, { 'text-anchor': 'end', class: 'wert' });
      svg.appendChild(t);
      if (platz) platz.belegen(platz.kasten(f.l - 5, y + 3.4, breite(s), S, 'end'));
    });
  }

  /** x-Beschriftungen ohne Ueberlappung: jede zweite, dritte ... wenn es eng wird. */
  function xBeschriftung(svg, f, werte, text, platz) {
    var y = f.o + f.h + 15;
    var n = werte.length;
    for (var schritt = 1; schritt <= n; schritt++) {
      var boxen = [], ok = true;
      for (var i = 0; i < n; i += schritt) {
        var s = text(werte[i]);
        var w = breite(s);
        var x = f.x(werte[i]);
        var anker = 'middle';
        if (x - w / 2 < 0) anker = 'start';
        else if (x + w / 2 > B) anker = 'end';
        var k = platz.kasten(x, y, w, S, anker);
        if (!platz.frei(k) || boxen.some(function (o) { return k.l < o.r + 4 && k.r > o.l - 4; })) { ok = false; break; }
        boxen.push({ l: k.l, r: k.r, k: k, s: s, x: x, anker: anker });
      }
      if (ok) {
        boxen.forEach(function (b) {
          platz.belegen(b.k);
          svg.appendChild(txt(b.x, y, b.s, { 'text-anchor': b.anker, class: 'wert' }));
        });
        return;
      }
    }
  }

  /* ============================================================
     LINIE / FLAECHE
     ============================================================ */
  function linie(spez) {
    var serien = (spez.serien || []).filter(function (s) { return s.werte && s.werte.length; });
    if (!serien.length) return rahmen(spez, e('svg', { viewBox: '0 0 ' + B + ' 40' }));

    var alleX = [], alleY = [];
    serien.forEach(function (s) {
      s.werte.forEach(function (p) {
        if (isFinite(p[0])) alleX.push(p[0]);
        if (isFinite(p[1])) alleY.push(p[1]);
      });
    });
    var xMin = spez.xMin !== undefined ? spez.xMin : Math.min.apply(null, alleX);
    var xMax = spez.xMax !== undefined ? spez.xMax : Math.max.apply(null, alleX);
    var yRoh = { min: Math.min.apply(null, alleY), max: Math.max.apply(null, alleY) };
    if (spez.yNull !== false && yRoh.min > 0 && !spez.logY) yRoh.min = 0;
    if (yRoh.max < 0 && spez.yNull !== false) yRoh.max = 0;
    var log = !!spez.logY && yRoh.min > 0;
    var tY = log
      ? { min: Math.log10(yRoh.min), max: Math.log10(yRoh.max), marken: [] }
      : teilung(spez.yMin !== undefined ? spez.yMin : yRoh.min, spez.yMax !== undefined ? spez.yMax : yRoh.max, 5);
    if (log) {
      var a = Math.floor(tY.min), b = Math.ceil(tY.max);
      tY.min = a; tY.max = b;
      for (var q = a; q <= b; q++) tY.marken.push(q);
    }
    function yText(v) { return log ? kurz(Math.pow(10, v), spez.yEinheit) : kurz(v, spez.yEinheit); }

    // Markierungen: Platz oben fuer ihre Beschriftungen (bis zu drei Reihen)
    var marken = (spez.marken || []).filter(function (m) { return isFinite(m.x); });
    var oben = marken.some(function (m) { return m.text; }) ? 12 + Math.min(3, marken.length) * 13 : 12;

    var f = flaeche({
      xMin: xMin, xMax: xMax, yMin: tY.min, yMax: tY.max, hoehe: (spez.hoehe || 200) + (oben - 12),
      links: spez.links || randFuer(tY.marken.map(yText)), oben: oben, unten: 26,
    });
    var svg = e('svg', { viewBox: '0 0 ' + B + ' ' + f.H, role: 'img' });
    var platz = new Platz(B, f.H);

    gitterY(svg, f, tY.marken, yText, !log, platz);
    var tX = teilung(xMin, xMax, spez.xMarken || 4);
    var xs = tX.marken.filter(function (v) { return v >= xMin - 1e-9 && v <= xMax + 1e-9; });
    xBeschriftung(svg, f, xs, function (v) { return spez.xTyp === 'jahr' ? String(Math.round(v)) : kurz(v, spez.xEinheit); }, platz);
    svg.appendChild(e('line', { x1: f.l, x2: f.l + f.w, y1: f.o + f.h, y2: f.o + f.h, class: 'achse' }));

    function yPos(v) { return f.y(log ? Math.log10(Math.max(v, 1e-9)) : v); }

    // Markierungen (senkrechte Hinweislinien) mit Beschriftung in freien Reihen ueber der Flaeche
    marken.forEach(function (m) {
      var x = f.x(m.x);
      var li = e('line', { x1: x, x2: x, y1: f.o, y2: f.o + f.h });
      li.setAttribute('style', 'stroke:' + farbe('rot') + ';stroke-width:1;stroke-dasharray:3 3;opacity:.7');
      svg.appendChild(li);
      if (m.text) {
        var s = kuerzen(m.text, B * 0.6);
        var kand = [];
        for (var reihe = 0; reihe < 3; reihe++) {
          var y = 11 + reihe * 13;
          kand.push({ x: x + 3, y: y, anker: 'start' }, { x: x - 3, y: y, anker: 'end' });
        }
        platz.setze(svg, s, kand, S, 'fill:' + farbe('rot'));
      }
    });

    serien.forEach(function (s, i) {
      var c = serienFarbe(i, s.farbe);
      var pfad = s.werte.map(function (p, j) { return (j ? 'L' : 'M') + f.x(p[0]).toFixed(2) + ' ' + yPos(p[1]).toFixed(2); }).join(' ');
      if (s.gefuellt || spez.art === 'flaeche') {
        var basis = f.y(log ? tY.min : Math.max(tY.min, Math.min(0, tY.max)));
        var fl = e('path', { d: pfad + ' L' + f.x(s.werte[s.werte.length - 1][0]) + ' ' + basis + ' L' + f.x(s.werte[0][0]) + ' ' + basis + ' Z' });
        fl.setAttribute('style', 'fill:' + c + ';opacity:.16;stroke:none');
        svg.appendChild(fl);
      }
      var p = e('path', { d: pfad });
      p.setAttribute('style', 'fill:none;stroke:' + c + ';stroke-width:' + (s.dick || 2) + ';stroke-linejoin:round;stroke-linecap:round' + (s.gestrichelt ? ';stroke-dasharray:4 3' : ''));
      svg.appendChild(p);
      if (spez.punkte && s.werte.length <= 30) {
        s.werte.forEach(function (pt) {
          var c2 = e('circle', { cx: f.x(pt[0]), cy: yPos(pt[1]), r: 2.4 });
          c2.setAttribute('style', 'fill:' + c);
          svg.appendChild(c2);
        });
      }
    });

    // Antippen: Werte anzeigen
    var lupe = K.el('div', { class: 'lupe' });
    var kreuz = e('line', { y1: f.o, y2: f.o + f.h, x1: f.l, x2: f.l });
    kreuz.setAttribute('style', 'stroke:' + farbe('text-still') + ';stroke-width:1;opacity:.6;display:none');
    svg.appendChild(kreuz);
    function zeigen(ev) {
      var r = svg.getBoundingClientRect();
      var vb = svg.viewBox.baseVal;
      var px = vb.x + ((ev.touches ? ev.touches[0].clientX : ev.clientX) - r.left) / r.width * vb.width;
      var xw = xMin + (px - f.l) / f.w * (xMax - xMin);
      if (px < f.l || px > f.l + f.w) return;
      kreuz.setAttribute('x1', px); kreuz.setAttribute('x2', px);
      kreuz.style.display = '';
      var teile = [];
      serien.forEach(function (s) {
        var best = null, bd = 1e18;
        s.werte.forEach(function (p) { var d = Math.abs(p[0] - xw); if (d < bd) { bd = d; best = p; } });
        if (best) teile.push((s.name ? s.name + ': ' : '') + K.mitEinheit(K.z(best[1], Math.abs(best[1]) >= 100 ? 0 : 2), spez.yEinheit));
      });
      var bx = null, bd2 = 1e18;
      serien[0].werte.forEach(function (p) { var d = Math.abs(p[0] - xw); if (d < bd2) { bd2 = d; bx = p[0]; } });
      lupe.textContent = (spez.xTyp === 'jahr' ? Math.round(bx) : K.z(bx, 1)) + '  ·  ' + teile.join('  ·  ');
    }
    // Nur waagerechtes Ziehen zeigt Werte; senkrecht bleibt die Seite scrollbar.
    // Die Klasse sagt der Zurueck-Geste, dass dieses Diagramm waagerechte Bewegungen selbst braucht.
    var start = null;
    svg.classList.add('zieht');
    svg.addEventListener('touchstart', function (ev) { start = ev.touches[0] ? { x: ev.touches[0].clientX, y: ev.touches[0].clientY } : null; }, { passive: true });
    svg.addEventListener('touchmove', function (ev) {
      if (!start || !ev.touches[0]) return;
      var dx = Math.abs(ev.touches[0].clientX - start.x), dy = Math.abs(ev.touches[0].clientY - start.y);
      if (dx > dy && dx > 6) { if (ev.cancelable) ev.preventDefault(); zeigen(ev); }
    }, { passive: false });
    svg.addEventListener('click', zeigen);
    svg.addEventListener('mousemove', zeigen);

    var extras = [];
    if (serien.length > 1 || serien[0].name) extras.push(legende(serien));
    extras.push(lupe);
    return rahmen(spez, svg, extras);
  }

  /* ============================================================
     BALKEN  (senkrecht oder waagerecht, gruppiert oder gestapelt)
     ============================================================ */
  function balken(spez) {
    var kat = spez.kategorien || [];
    var serien = spez.serien || [];
    var n = kat.length, m = serien.length;
    if (!n || !m) return rahmen(spez, e('svg', { viewBox: '0 0 ' + B + ' 40' }));

    // Passen die Kategorienamen unter die Saeulen (hoechstens zwei Zeilen)? Sonst liegende Balken.
    var gruppeSchaetz = (B - 50) / n;
    var passt = kat.every(function (k) {
      var z = umbrechen(k, gruppeSchaetz - 4, S, false);
      return z.length <= 2 && z.every(function (l) { return breite(l) <= gruppeSchaetz - 3; });
    });
    // Jahre und Jahrzehnte ("1990er", englisch "1990s") gelten als Zeitreihe
    var zeitreihe = kat.every(function (k) { return /^[\s\d'.,\/–-]+(er|s)?\*?$/.test(String(k)); });
    if (spez.waagerecht || (!passt && !zeitreihe && !spez.gestapelt && n <= 14)) return balkenLiegend(spez);
    return balkenStehend(spez, zeitreihe);
  }

  function werteSpanne(spez) {
    var serien = spez.serien || [], kat = spez.kategorien || [];
    var werte = [];
    if (spez.gestapelt) {
      for (var i = 0; i < kat.length; i++) {
        var pos = 0, neg = 0;
        serien.forEach(function (s) { var v = s.werte[i] || 0; if (v >= 0) pos += v; else neg += v; });
        werte.push(pos); werte.push(neg);
      }
    } else {
      serien.forEach(function (s) { s.werte.forEach(function (v) { if (isFinite(v)) werte.push(v); }); });
    }
    if (spez.referenz && isFinite(spez.referenz.wert)) werte.push(spez.referenz.wert);
    return teilung(Math.min(0, Math.min.apply(null, werte)), Math.max(0, Math.max.apply(null, werte)), 4);
  }

  function wertText(v, spez) { return K.mitEinheit(K.z(v, Math.abs(v) >= 100 ? 0 : 1), spez.yEinheit); }

  function balkenLiegend(spez) {
    var kat = spez.kategorien, serien = spez.serien, n = kat.length, m = serien.length;
    var t = werteSpanne(spez);
    var zeigeWerte = spez.wertAnzeigen !== false;
    var gestapelt = !!spez.gestapelt && m > 1;
    // Summen je Kategorie fuer gestapelte Balken
    var summen = kat.map(function (_, i) {
      var sm = 0;
      serien.forEach(function (s) { var v = s.werte[i]; if (isFinite(v)) sm += v; });
      return sm;
    });
    // Wertbeschriftungen messen: rechts fuer positive, links fuer negative
    var wPos = 0, wNeg = 0;
    if (zeigeWerte) (gestapelt ? [{ werte: summen }] : serien).forEach(function (s) {
      s.werte.forEach(function (v) {
        if (!isFinite(v)) return;
        var w = breite(wertText(v, spez)) + 5;
        if (v < 0) wNeg = Math.max(wNeg, w); else wPos = Math.max(wPos, w);
      });
    });
    // Namensspalte: bis 42 % der Breite, lange Namen in bis zu drei Zeilen
    var maxName = 0;
    kat.forEach(function (k) { maxName = Math.max(maxName, breite(k)); });
    var lb = Math.min(Math.ceil(maxName) + 8, Math.round(B * 0.42));
    var zeilenJe = kat.map(function (k) { return umbrechen(k, lb - 8, S, false, 3); });
    var zeilenMax = Math.max.apply(null, zeilenJe.map(function (z) { return z.length; }).concat([1]));
    var balkenH = gestapelt ? 14 : (m > 1 ? Math.max(6, 16 / m) : 12);
    var zh = Math.max(spez.zeilenHoehe || 0, zeilenMax > 2 ? 40 : (zeilenMax > 1 ? 30 : 22), (gestapelt ? 1 : m) * balkenH + 8);
    var plotL = lb + wNeg, plotR = B - Math.max(wPos, 6);
    var o = 6, h = n * zh;
    var H = o + h + 22;
    var svg = e('svg', { viewBox: '0 0 ' + B + ' ' + H });
    function xv(v) { return plotL + (v - t.min) / (t.max - t.min || 1) * (plotR - plotL); }
    // Gitter und Achsenwerte, ohne Ueberlappung
    var platz = new Platz(B, H);
    t.marken.forEach(function (v) {
      var x = xv(v);
      svg.appendChild(e('line', { x1: x, x2: x, y1: o, y2: o + h, class: v === 0 ? 'achse' : 'gitter' }));
    });
    xBeschriftung(svg, { x: xv, o: o, h: h }, t.marken, function (v) { return kurz(v, spez.yEinheit); }, platz);
    kat.forEach(function (k, i) {
      var y0 = o + i * zh;
      var mitte = y0 + zh / 2;
      var zeilen = zeilenJe[i];
      var lab = mehrzeilig(lb - 6, mitte + 3.4 - (zeilen.length - 1) * 5.8, zeilen, { 'text-anchor': 'end' }, 11.6);
      svg.appendChild(lab);
      if (gestapelt) {
        var basisPos = 0, basisNeg = 0;
        serien.forEach(function (s, j) {
          var v = s.werte[i];
          if (v === undefined || v === null || !isFinite(v) || v === 0) return;
          var von = v >= 0 ? basisPos : basisNeg + v, bis = v >= 0 ? basisPos + v : basisNeg;
          if (v >= 0) basisPos += v; else basisNeg += v;
          var rs = e('rect', { x: xv(von), y: mitte - balkenH / 2, width: Math.max(0.8, xv(bis) - xv(von)), height: balkenH });
          rs.setAttribute('style', 'fill:' + serienFarbe(j, s.farbe) + ';stroke:' + farbe('flaeche') + ';stroke-width:.8');
          svg.appendChild(rs);
        });
        if (zeigeWerte) {
          var sm = summen[i];
          svg.appendChild(txt(sm < 0 ? xv(basisNeg) - 4 : xv(basisPos) + 4, mitte + 3.4, wertText(sm, spez), { 'text-anchor': sm < 0 ? 'end' : 'start', class: 'wert' }));
        }
        return;
      }
      serien.forEach(function (s, j) {
        var v = s.werte[i];
        if (v === undefined || v === null || !isFinite(v)) return;
        var yb = m > 1 ? mitte - m * balkenH / 2 + j * balkenH : mitte - balkenH / 2;
        var x1 = xv(Math.min(0, v)), x2 = xv(Math.max(0, v));
        var rect = e('rect', { x: x1, y: yb + 0.5, width: Math.max(0.8, x2 - x1), height: Math.max(2, balkenH - 1), rx: 2 });
        var c = spez.diverging ? farbe(v < 0 ? 'rot' : 'gruen') : serienFarbe(j, s.farbe);
        rect.setAttribute('style', 'fill:' + c);
        svg.appendChild(rect);
        if (zeigeWerte && (m === 1 || balkenH >= 9)) {
          var tv = txt(v < 0 ? x1 - 4 : x2 + 4, yb + balkenH / 2 + 3.4, wertText(v, spez), { 'text-anchor': v < 0 ? 'end' : 'start', class: 'wert' });
          if (m > 1) tv.setAttribute('style', 'font-size:9px');
          svg.appendChild(tv);
        }
      });
    });
    if (spez.referenz && isFinite(spez.referenz.wert)) {
      var xr = xv(spez.referenz.wert);
      var lr = e('line', { x1: xr, x2: xr, y1: o, y2: o + h });
      lr.setAttribute('style', 'stroke:' + farbe('gold') + ';stroke-width:1.4;stroke-dasharray:4 3');
      svg.appendChild(lr);
    }
    var extras = [];
    if (m > 1) extras.push(legende(serien));
    if (spez.referenz && spez.referenz.text) extras.push(referenzHinweis(spez.referenz.text));
    return rahmen(spez, svg, extras);
  }

  function referenzHinweis(text) {
    var l = K.el('div', { class: 'legende' });
    var sp = K.el('span', {});
    var ic = K.el('i', { class: 'gestrichelt' });
    ic.style.background = 'transparent';
    ic.style.borderTop = '2px dashed ' + farbe('gold');
    sp.appendChild(ic);
    sp.appendChild(document.createTextNode(text));
    l.appendChild(sp);
    return l;
  }

  function balkenStehend(spez, zeitreihe) {
    var kat = spez.kategorien, serien = spez.serien, n = kat.length, m = serien.length;
    var t = werteSpanne(spez);
    var yText = function (v) { return kurz(v, spez.yEinheit); };
    var links = randFuer(t.marken.map(yText));
    var gruppe = (B - links - 10) / n;
    // Beschriftung: waagerecht, zweizeilig oder nur jede k-te (Zeitreihen)
    var zeilenJe = kat.map(function (k) { return umbrechen(k, gruppe - 3, S, false, 2); });
    var zweizeilig = zeilenJe.some(function (z) { return z.length > 1; });
    var maxW = 0;
    kat.forEach(function (k) { maxW = Math.max(maxW, breite(k)); });
    var jede = 1;
    if (zeitreihe) { while (maxW + 6 > gruppe * jede && jede < n) jede++; }
    else {
      // Namen, die auch zweizeilig nicht unter die Saeule passen: nur jede k-te Beschriftung
      var breitesteZeile = 0;
      zeilenJe.forEach(function (z) { z.forEach(function (l) { breitesteZeile = Math.max(breitesteZeile, breite(l)); }); });
      // gleiche Grenze wie beim Umbrechen (gruppe - 3), sonst verschwindet bei Zeilen im letzten Pixel jede zweite Beschriftung
      while (breitesteZeile + 3 > gruppe * jede && jede < n) jede++;
    }
    var unten = zweizeilig && !zeitreihe ? 38 : 26;
    var f = flaeche({ xMin: 0, xMax: n, yMin: t.min, yMax: t.max, hoehe: spez.hoehe || 200, links: links, unten: unten });
    var svg = e('svg', { viewBox: '0 0 ' + B + ' ' + f.H });
    var platz = new Platz(B, f.H);
    gitterY(svg, f, t.marken, yText, true, platz);

    var lueck = Math.min(6, gruppe * 0.22);
    var bw = spez.gestapelt ? (gruppe - lueck) : (gruppe - lueck) / m;

    kat.forEach(function (k, i) {
      var basePos = 0, baseNeg = 0;
      serien.forEach(function (s, j) {
        var v = s.werte[i];
        if (v === undefined || v === null || !isFinite(v)) return;
        var x = f.l + i * gruppe + lueck / 2 + (spez.gestapelt ? 0 : j * bw);
        var y0, y1;
        if (spez.gestapelt) {
          if (v >= 0) { y0 = f.y(basePos + v); y1 = f.y(basePos); basePos += v; }
          else { y0 = f.y(baseNeg); y1 = f.y(baseNeg + v); baseNeg += v; }
        } else {
          y0 = f.y(Math.max(0, v)); y1 = f.y(Math.min(0, v));
        }
        var rect = e('rect', { x: x, y: y0, width: Math.max(0.8, bw), height: Math.max(0.8, y1 - y0), rx: 1.5 });
        var c = spez.diverging ? farbe(v < 0 ? 'rot' : 'gruen') : serienFarbe(j, s.farbe);
        rect.setAttribute('style', 'fill:' + c);
        svg.appendChild(rect);
      });
      if (i % jede === 0) {
        var lx = f.l + i * gruppe + gruppe / 2;
        var zeilen = zeitreihe ? [String(k)] : zeilenJe[i];
        var lt = mehrzeilig(lx, f.o + f.h + 16, zeilen, { 'text-anchor': 'middle', class: 'wert' }, 11.2);
        svg.appendChild(lt);
      }
    });

    if (spez.referenz && isFinite(spez.referenz.wert)) {
      var yr = f.y(spez.referenz.wert);
      var lr = e('line', { x1: f.l, x2: f.l + f.w, y1: yr, y2: yr });
      lr.setAttribute('style', 'stroke:' + farbe('gold') + ';stroke-width:1.4;stroke-dasharray:4 3');
      svg.appendChild(lr);
    }
    var ex = [];
    if (m > 1) ex.push(legende(serien));
    if (spez.referenz && spez.referenz.text) ex.push(referenzHinweis(spez.referenz.text));
    return rahmen(spez, svg, ex);
  }

  /* ============================================================
     DONUT / RING  -  Legende als Liste darunter
     ============================================================ */
  function donut(spez) {
    var teile = (spez.teile || []).filter(function (t) { return t.wert > 0; });
    var gesamt = K.summe(teile.map(function (t) { return t.wert; }));
    var H = 160;
    var svg = e('svg', { viewBox: '0 0 ' + B + ' ' + H });
    var cx = B / 2, cy = H / 2, ra = 74, ri = spez.voll ? 0 : 44;
    var a0 = -Math.PI / 2;
    teile.forEach(function (t, i) {
      var an = t.wert / gesamt * Math.PI * 2;
      if (an >= Math.PI * 2 - 1e-6) an = Math.PI * 2 - 1e-4;
      var a1 = a0 + an;
      var gross = an > Math.PI ? 1 : 0;
      var p1 = [cx + ra * Math.cos(a0), cy + ra * Math.sin(a0)];
      var p2 = [cx + ra * Math.cos(a1), cy + ra * Math.sin(a1)];
      var p3 = [cx + ri * Math.cos(a1), cy + ri * Math.sin(a1)];
      var p4 = [cx + ri * Math.cos(a0), cy + ri * Math.sin(a0)];
      var d = 'M' + p1 + ' A' + ra + ' ' + ra + ' 0 ' + gross + ' 1 ' + p2 +
        (ri ? ' L' + p3 + ' A' + ri + ' ' + ri + ' 0 ' + gross + ' 0 ' + p4 : ' L' + cx + ' ' + cy) + ' Z';
      var pa = e('path', { d: d });
      pa.setAttribute('style', 'fill:' + serienFarbe(i, t.farbe) + ';stroke:' + farbe('flaeche') + ';stroke-width:1.2');
      svg.appendChild(pa);
      a0 = a1;
    });
    if (spez.mitte && ri) {
      var zeilenM = umbrechen(spez.mitte, ri * 1.7, 13, true, 2);
      // Unterzeile: passt sie nicht in eine Zeile, bricht sie in bis zu drei schmalere Zeilen um (Rundung des Lochs)
      var zeilenU = [];
      if (spez.mitteUnter) {
        var einzeilig = kuerzen(spez.mitteUnter, ri * 1.7);
        zeilenU = einzeilig === String(spez.mitteUnter) || zeilenM.length > 1 ? [einzeilig] : umbrechen(spez.mitteUnter, ri * 1.5, S, false, 3);
      }
      var hoch = Math.max(0, zeilenU.length - 1) * 6;
      var t1 = mehrzeilig(cx, cy + (spez.mitteUnter ? -2 : 4) - (zeilenM.length - 1) * 7 - hoch, zeilenM, { 'text-anchor': 'middle', class: 'wert' }, 14);
      t1.setAttribute('style', 'font-size:13px;font-weight:700;fill:' + farbe('text'));
      svg.appendChild(t1);
      if (zeilenU.length) svg.appendChild(mehrzeilig(cx, cy + 13 - hoch, zeilenU, { 'text-anchor': 'middle' }, 12));
    }
    var liste = listenLegende(teile.map(function (t, i) {
      var p = t.wert / gesamt * 100;
      return { name: t.name, farbe: serienFarbe(i, t.farbe), wert: K.mitEinheit(K.z(p, p < 10 ? 1 : 0), ' %') };
    }));
    return rahmen(spez, svg, [liste]);
  }

  /* ============================================================
     ZINSESZINS - Kurve mit Einzahlungen
     ============================================================ */
  function zinseszins(spez) {
    var jahre = spez.jahre || 30;
    function reihe(r) {
      var v = spez.start || 0, pkt = [[0, v]];
      for (var j = 1; j <= jahre; j++) {
        for (var m = 0; m < 12; m++) v = v * (1 + r / 100 / 12) + (spez.monatlich || 0);
        pkt.push([j, v]);
      }
      return pkt;
    }
    var einzahlung = [];
    var ez = spez.start || 0;
    for (var j = 0; j <= jahre; j++) { einzahlung.push([j, ez]); ez += (spez.monatlich || 0) * 12; }

    var serien = [{ name: K.t('eingezahlt'), werte: einzahlung, farbe: 'text-still', gestrichelt: true }];
    serien.push({ name: K.mitEinheit(K.z(spez.rate, 1), ' % p. a.'), werte: reihe(spez.rate), farbe: 'gold', gefuellt: true });
    (spez.vergleich || []).forEach(function (v, i) {
      serien.push({ name: v.name || K.mitEinheit(K.z(v.rate, 1), ' % p. a.'), werte: reihe(v.rate), farbe: v.farbe || REIHE[(i + 1) % REIHE.length] });
    });
    return linie({
      art: 'linie', titel: spez.titel, unter: spez.unter, serien: serien,
      xTyp: 'zahl', xEinheit: '', yEinheit: ' €', hoehe: spez.hoehe || 200, xMarken: 5
    });
  }

  /* ============================================================
     DRAWDOWN (Unterwasserkurve)
     ============================================================ */
  function drawdown(spez) {
    var s = spez.serie || [];
    // Enthaelt die Serie schon Rueckgaenge in Prozent (alle Werte <= 0), wird sie unveraendert gezeichnet.
    var schonProzent = s.length > 0 && s.every(function (p) { return p[1] <= 0; });
    var hoch = -1e18, dd = [];
    s.forEach(function (p) {
      if (schonProzent) { dd.push([p[0], p[1]]); return; }
      hoch = Math.max(hoch, p[1]);
      dd.push([p[0], (p[1] / hoch - 1) * 100]);
    });
    return linie({
      art: 'flaeche', titel: spez.titel, unter: spez.unter, xTyp: spez.xTyp || 'jahr',
      serien: [{ name: K.t('Rückgang vom Höchststand'), werte: dd, farbe: 'rot', gefuellt: true }],
      yEinheit: ' %', yMax: 0, hoehe: spez.hoehe || 170
    });
  }

  /* ============================================================
     PAYOFF - Gewinn-Verlust-Profil von Optionen
     ============================================================ */
  function payoff(spez) {
    var beine = spez.beine || [];
    var strikes = beine.map(function (b) { return b.strike || spez.spot || 100; });
    var s0 = spez.spot || K.mittel(strikes) || 100;
    var xMin = spez.xMin !== undefined ? spez.xMin : Math.max(0, Math.min.apply(null, strikes.concat([s0])) * 0.6);
    var xMax = spez.xMax !== undefined ? spez.xMax : Math.max.apply(null, strikes.concat([s0])) * 1.4;

    function gv(S0) {
      var g = 0;
      beine.forEach(function (b) {
        var anz = (b.anzahl === undefined ? 1 : b.anzahl);
        var lang = b.richtung !== 'short';
        var pr = b.praemie || 0;
        var inner = 0;
        if (b.art === 'aktie') inner = S0 - (b.strike || s0);
        else if (b.art === 'call') inner = Math.max(0, S0 - b.strike);
        else if (b.art === 'put') inner = Math.max(0, b.strike - S0);
        if (b.art === 'aktie') g += (lang ? 1 : -1) * anz * inner;
        else g += anz * (lang ? (inner - pr) : (pr - inner));
      });
      return g;
    }

    var pkt = [];
    for (var i = 0; i <= 120; i++) {
      var sx = xMin + (xMax - xMin) * i / 120;
      pkt.push([sx, gv(sx)]);
    }
    var yv = pkt.map(function (p) { return p[1]; });
    var t = teilung(Math.min.apply(null, yv), Math.max.apply(null, yv), 4);
    var yText = function (v) { return kurz(v, ' €'); };
    var f = flaeche({ xMin: xMin, xMax: xMax, yMin: t.min, yMax: t.max, hoehe: 210, links: randFuer(t.marken.map(yText)), oben: 22 });
    var svg = e('svg', { viewBox: '0 0 ' + B + ' ' + f.H });
    var platz = new Platz(B, f.H);
    gitterY(svg, f, t.marken, yText, true, platz);
    var tX = teilung(xMin, xMax, 4);
    xBeschriftung(svg, f, tX.marken.filter(function (v) { return v >= xMin && v <= xMax; }), function (v) { return kurz(v, ''); }, platz);

    function bereich(obenSeite) {
      var d = '', offen = false;
      pkt.forEach(function (p, k) {
        var drin = obenSeite ? p[1] > 0 : p[1] < 0;
        if (drin && !offen) { d += 'M' + f.x(p[0]) + ' ' + f.y(0) + ' L' + f.x(p[0]) + ' ' + f.y(p[1]); offen = true; }
        else if (drin) { d += ' L' + f.x(p[0]) + ' ' + f.y(p[1]); }
        else if (!drin && offen) { d += ' L' + f.x(pkt[k - 1][0]) + ' ' + f.y(0) + ' Z'; offen = false; }
      });
      if (offen) d += ' L' + f.x(pkt[pkt.length - 1][0]) + ' ' + f.y(0) + ' Z';
      return d;
    }
    [[true, 'gruen'], [false, 'rot']].forEach(function (x) {
      var d = bereich(x[0]);
      if (!d) return;
      var pa = e('path', { d: d });
      pa.setAttribute('style', 'fill:' + farbe(x[1]) + ';opacity:.18;stroke:none');
      svg.appendChild(pa);
    });

    var pfad = pkt.map(function (p, k) { return (k ? 'L' : 'M') + f.x(p[0]) + ' ' + f.y(p[1]); }).join(' ');
    var lp = e('path', { d: pfad });
    lp.setAttribute('style', 'fill:none;stroke:' + farbe('gold') + ';stroke-width:2.2;stroke-linejoin:round');
    svg.appendChild(lp);

    if (spez.spot) {
      var xs = f.x(spez.spot);
      var ls = e('line', { x1: xs, x2: xs, y1: f.o, y2: f.o + f.h });
      ls.setAttribute('style', 'stroke:' + farbe('blau') + ';stroke-width:1;stroke-dasharray:3 3');
      svg.appendChild(ls);
      platz.setze(svg, K.t('Kurs heute'), [{ x: xs + 3, y: 12, anker: 'start' }, { x: xs - 3, y: 12, anker: 'end' }], S, 'fill:' + farbe('blau'));
    }

    var be = [];
    for (var k = 1; k < pkt.length; k++) {
      if ((pkt[k - 1][1] < 0 && pkt[k][1] >= 0) || (pkt[k - 1][1] > 0 && pkt[k][1] <= 0)) {
        var x1 = pkt[k - 1][0], x2 = pkt[k][0], y1 = pkt[k - 1][1], y2 = pkt[k][1];
        be.push(x1 + (0 - y1) / (y2 - y1) * (x2 - x1));
      }
    }
    var unten = K.el('div', { class: 'lupe' });
    var schwellen = be.map(function (x) { return K.z(x, 2); });
    // Grenzwerte ueber den ganzen Kursbereich ab 0, nicht nur ueber den gezeichneten Ausschnitt:
    // Knicke gibt es nur bei den Basispreisen, rechts davon entscheidet die Steigung.
    var stellen = yv.concat([0].concat(strikes).map(gv));
    var rechts = Math.max.apply(null, strikes.concat([xMax])) + 1;
    var steigung = gv(rechts + 1) - gv(rechts);
    var gMax = steigung > 1e-9 ? Infinity : Math.max.apply(null, stellen);
    var vMin = steigung < -1e-9 ? -Infinity : Math.min.apply(null, stellen);
    unten.textContent = (be.length ? K.t('Gewinnschwelle bei {0}', schwellen.reduce(function (a, b) { return K.t('{0} und {1}', a, b); })) + '  ·  ' : '') +
      K.t('max. Gewinn {0}', gMax === Infinity ? K.t('unbegrenzt') : K.eur(gMax, 2)) +
      '  ·  ' + K.t('max. Verlust {0}', vMin === -Infinity ? K.t('unbegrenzt') : K.eur(vMin, 2));
    return rahmen(spez, svg, [unten]);
  }

  /* ============================================================
     WASSERFALL  -  liegend: je Schritt eine Zeile
     ============================================================ */
  function wasserfall(spez) {
    var s = spez.schritte || [];
    var lauf = spez.start || 0, pkt = [], min = lauf, max = lauf;
    s.forEach(function (x, k) {
      var von = lauf;
      if (x.summe) {
        // Summe am Anfang ohne Startwert: ihr Wert ist der Ausgangsbetrag. Sonst zeigt sie den laufenden Stand.
        if (k === 0 && spez.start === undefined) lauf = x.wert || 0;
        pkt.push({ name: x.name, von: 0, bis: lauf, wert: lauf, summe: true });
        min = Math.min(min, 0, lauf); max = Math.max(max, lauf);
        return;
      }
      lauf += x.wert;
      pkt.push({ name: x.name, von: von, bis: lauf, wert: x.wert, summe: false });
      min = Math.min(min, lauf, von); max = Math.max(max, lauf, von);
    });
    var t = teilung(Math.min(0, min), max, 4);
    var einheit = spez.yEinheit || ' €';
    function wt(p) { return p.summe ? K.z(p.bis, Math.abs(p.bis) >= 100 ? 0 : 1) : K.vorz(p.wert, Math.abs(p.wert) >= 100 ? 0 : 1); }
    var wMax = 0, nMax = 0;
    pkt.forEach(function (p) { wMax = Math.max(wMax, breite(wt(p))); nMax = Math.max(nMax, breite(p.name)); });
    var lb = Math.min(Math.ceil(nMax) + 8, Math.round(B * 0.4));
    var zeilenJe = pkt.map(function (p) { return umbrechen(p.name, lb - 8, S, false, 3); });
    var zeilenMax = Math.max.apply(null, zeilenJe.map(function (z) { return z.length; }).concat([1]));
    var zh = zeilenMax > 2 ? 40 : (zeilenMax > 1 ? 30 : 22);
    var plotL = lb, plotR = B - wMax - 8;
    var o = 6, h = pkt.length * zh, H = o + h + 22;
    var svg = e('svg', { viewBox: '0 0 ' + B + ' ' + H });
    function xv(v) { return plotL + (v - t.min) / (t.max - t.min || 1) * (plotR - plotL); }
    var platz = new Platz(B, H);
    t.marken.forEach(function (v) { svg.appendChild(e('line', { x1: xv(v), x2: xv(v), y1: o, y2: o + h, class: v === 0 ? 'achse' : 'gitter' })); });
    xBeschriftung(svg, { x: xv, o: o, h: h }, t.marken, function (v) { return kurz(v, einheit); }, platz);
    pkt.forEach(function (p, i) {
      var y0 = o + i * zh, mitte = y0 + zh / 2, bh = 12;
      var a = xv(Math.min(p.von, p.bis)), b2 = xv(Math.max(p.von, p.bis));
      var c = p.summe ? farbe('blau') : (p.wert >= 0 ? farbe('gruen') : farbe('rot'));
      var r = e('rect', { x: a, y: mitte - bh / 2, width: Math.max(1, b2 - a), height: bh, rx: 2 });
      r.setAttribute('style', 'fill:' + c);
      svg.appendChild(r);
      if (i < pkt.length - 1) {
        var xk = xv(p.bis);
        var lv = e('line', { x1: xk, x2: xk, y1: mitte + bh / 2, y2: mitte + zh - bh / 2 });
        lv.setAttribute('style', 'stroke:' + farbe('text-still') + ';stroke-width:1;stroke-dasharray:2 2;opacity:.6');
        svg.appendChild(lv);
      }
      var zeilen = zeilenJe[i];
      svg.appendChild(mehrzeilig(lb - 6, mitte + 3.4 - (zeilen.length - 1) * 5.8, zeilen, { 'text-anchor': 'end' }, 11.6));
      svg.appendChild(txt(b2 + 5, mitte + 3.4, wt(p), { 'text-anchor': 'start', class: 'wert' }));
    });
    return rahmen(spez, svg);
  }

  /* ============================================================
     HEATMAP / Korrelationsmatrix
     ============================================================ */
  function heatmap(spez) {
    var zeilen = spez.zeilen || [], spalten = spez.spalten || [], w = spez.werte || [];
    var maxZ = 0;
    zeilen.forEach(function (z) { maxZ = Math.max(maxZ, breite(z)); });
    var lb = spez.links || Math.min(Math.ceil(maxZ) + 8, Math.round(B * 0.34));
    var zellB = (B - lb - 4) / Math.max(1, spalten.length);
    // Spaltenkoepfe: waagerecht in bis zu zwei Zeilen, sonst schraeg mit berechnetem Rand
    var kopfZeilen = spalten.map(function (s) { return umbrechen(s, zellB - 2, 9.5, false, 2); });
    var passt = kopfZeilen.every(function (z) { return z.every(function (l) { return breite(l, 9.5) <= zellB - 2; }); }) &&
      spalten.every(function (s, j) { return kopfZeilen[j].join(' ') === String(s); });
    var maxS = 0;
    spalten.forEach(function (s) { maxS = Math.max(maxS, breite(s, 9.5)); });
    var schraeg = !passt;
    // Schraege Koepfe laufen von links oben zur Spaltenmitte; sie duerfen den linken Rand nicht verlassen
    var schraegMax = Math.min(90, maxS, (lb + zellB / 2 - 2) / Math.cos(40 * Math.PI / 180));
    var kopfH = schraeg ? Math.ceil(schraegMax * Math.sin(40 * Math.PI / 180)) + 14 : (kopfZeilen.some(function (z) { return z.length > 1; }) ? 28 : 18);
    var zellH = spez.zellHoehe || 20;
    // Zeilennamen, die nicht passen, stehen mit etwas kleinerer Schrift in einer oder zwei Zeilen
    var zeilenTxt = zeilen.map(function (z) {
      if (breite(z) <= lb - 6 || zellH < 20) return { z: [kuerzen(z, lb - 6)], klein: false };
      return { z: umbrechen(z, lb - 6, 9, false, 2), klein: true };
    });
    var H = kopfH + zeilen.length * zellH + 6;
    var svg = e('svg', { viewBox: '0 0 ' + B + ' ' + H });
    var alle = [].concat.apply([], w).filter(function (x) { return isFinite(x); });
    var mx = spez.max !== undefined ? spez.max : Math.max.apply(null, alle.map(Math.abs));
    spalten.forEach(function (s, j) {
      var cx = lb + j * zellB + zellB / 2;
      if (schraeg) {
        var t = txt(cx + 2, kopfH - 5, kuerzen(s, schraegMax, 9.5), { 'text-anchor': 'end' });
        t.setAttribute('transform', 'rotate(40 ' + (cx + 2) + ' ' + (kopfH - 5) + ')');
        t.setAttribute('style', 'font-size:9.5px');
        svg.appendChild(t);
      } else {
        var zl = kopfZeilen[j];
        var tt = mehrzeilig(cx, kopfH - 5 - (zl.length - 1) * 10.5, zl, { 'text-anchor': 'middle' }, 10.5);
        tt.setAttribute('style', 'font-size:9.5px');
        svg.appendChild(tt);
      }
    });
    zeilen.forEach(function (z, i) {
      var y = kopfH + i * zellH;
      var zt = zeilenTxt[i];
      if (zt.klein) {
        var zm = mehrzeilig(lb - 5, y + zellH / 2 + (zt.z.length > 1 ? -1.2 : 3.1), zt.z, { 'text-anchor': 'end' }, 9.4);
        zm.setAttribute('style', 'font-size:9px');
        svg.appendChild(zm);
      } else svg.appendChild(txt(lb - 5, y + zellH / 2 + 3.4, zt.z[0], { 'text-anchor': 'end' }));
      spalten.forEach(function (s, j) {
        var v = w[i] && w[i][j];
        var r = e('rect', { x: lb + j * zellB + 1, y: y + 1, width: zellB - 2, height: zellH - 2, rx: 2 });
        if (v === null || v === undefined || !isFinite(v)) {
          r.setAttribute('style', 'fill:' + farbe('flaeche2'));
        } else {
          var stark = Math.min(1, Math.abs(v) / (mx || 1));
          var basis = spez.skala === 'einfarbig' ? farbe('blau') : (v >= 0 ? farbe('gruen') : farbe('rot'));
          r.setAttribute('style', 'fill:' + basis + ';opacity:' + (0.12 + stark * 0.78));
        }
        svg.appendChild(r);
        if (isFinite(v)) {
          var s2 = K.z(v, spez.nachkomma === undefined ? (Math.abs(v) < 10 ? 2 : 0) : spez.nachkomma);
          if (breite(s2, 9) <= zellB - 4) {
            var tv = txt(lb + j * zellB + zellB / 2, y + zellH / 2 + 3.2, s2, { 'text-anchor': 'middle', class: 'wert' });
            tv.setAttribute('style', 'fill:' + farbe('text') + ';font-size:9px');
            svg.appendChild(tv);
          }
        }
      });
    });
    return rahmen(spez, svg);
  }

  /* ============================================================
     STREUUNG  -  Namen an freien Stellen, sonst nummeriert
     ============================================================ */
  function streu(spez) {
    var p = spez.punkte || [];
    var xs = p.map(function (q) { return q.x; }), ys = p.map(function (q) { return q.y; });
    var tX = teilung(Math.min.apply(null, xs), Math.max.apply(null, xs), 4);
    var tY = teilung(Math.min.apply(null, ys), Math.max.apply(null, ys), 4);
    var yText = function (v) { return kurz(v, spez.yEinheit); };
    var f = flaeche({ xMin: tX.min, xMax: tX.max, yMin: tY.min, yMax: tY.max, hoehe: 220, links: randFuer(tY.marken.map(yText)), oben: spez.yLabel ? 22 : 12, unten: spez.xLabel ? 38 : 26 });
    var svg = e('svg', { viewBox: '0 0 ' + B + ' ' + f.H });
    var platz = new Platz(B, f.H);
    gitterY(svg, f, tY.marken, yText, true, platz);
    tX.marken.forEach(function (v) { svg.appendChild(e('line', { x1: f.x(v), x2: f.x(v), y1: f.o, y2: f.o + f.h, class: v === 0 ? 'achse' : 'gitter' })); });
    xBeschriftung(svg, f, tX.marken, function (v) { return kurz(v, spez.xEinheit); }, platz);
    if (spez.xLabel) platz.setze(svg, kuerzen(spez.xLabel, B - f.l), [{ x: f.l + f.w, y: f.o + f.h + 30, anker: 'end' }], S, 'fill:' + farbe('text-still'));
    if (spez.yLabel) platz.setze(svg, kuerzen(spez.yLabel, B - 4), [{ x: 2, y: 11, anker: 'start' }], S, 'fill:' + farbe('text-still'));
    // Punkte zuerst belegen, damit Namen sie nicht verdecken
    p.forEach(function (q) { var cx = f.x(q.x), cy = f.y(q.y), r = q.r || 4; platz.belegen({ l: cx - r, r: cx + r, t: cy - r, u: cy + r }); });
    var ohneNamen = [];
    p.forEach(function (q, i) {
      var cx = f.x(q.x), cy = f.y(q.y);
      var farbeP = serienFarbe(q.gruppe !== undefined ? q.gruppe : 1, q.farbe);
      var c = e('circle', { cx: cx, cy: cy, r: q.r || 4 });
      c.setAttribute('style', 'fill:' + farbeP + ';opacity:.85');
      svg.appendChild(c);
      if (!q.name) return;
      var name = kuerzen(q.name, 110);
      var stellen = [
        { x: cx + 7, y: cy + 3.4, anker: 'start' }, { x: cx - 7, y: cy + 3.4, anker: 'end' },
        { x: cx, y: cy - 8, anker: 'middle' }, { x: cx, y: cy + 15, anker: 'middle' },
        { x: cx + 5, y: cy - 6, anker: 'start' }, { x: cx - 5, y: cy - 6, anker: 'end' },
        { x: cx + 5, y: cy + 13, anker: 'start' }, { x: cx - 5, y: cy + 13, anker: 'end' },
      ];
      var gesetzt = platz.setze(svg, name, stellen, S, 'fill:' + farbe('text-leise'))
        || platz.setze(svg, name, stellen, 8.5, 'fill:' + farbe('text-leise') + ';font-size:8.5px');
      if (!gesetzt) {
        var nr = String(ohneNamen.length + 1);
        ohneNamen.push({ name: nr + '  ' + q.name, farbe: farbeP });
        platz.setze(svg, nr, [{ x: cx + 6, y: cy - 4, anker: 'start' }, { x: cx - 6, y: cy - 4, anker: 'end' }, { x: cx, y: cy + 14, anker: 'middle' }], 9, 'fill:' + farbe('text'));
      }
    });
    var liste = ohneNamen.length ? listenLegende(ohneNamen) : null;
    if (liste && ohneNamen.every(function (x) { return x.name.length <= 16; })) liste.classList.add('kompakt');
    return rahmen(spez, svg, liste ? [liste] : []);
  }

  /* ============================================================
     HISTOGRAMM
     ============================================================ */
  function histogramm(spez) {
    var w = (spez.werte || []).filter(function (x) { return isFinite(x); });
    if (!w.length) return rahmen(spez, e('svg', { viewBox: '0 0 ' + B + ' 40' }));
    var min = spez.min !== undefined ? spez.min : Math.min.apply(null, w);
    var max = spez.max !== undefined ? spez.max : Math.max.apply(null, w);
    var n = spez.klassen || 12;
    var br = (max - min) / n || 1;
    var eimer = new Array(n).fill(0);
    w.forEach(function (x) { eimer[K.grenz(Math.floor((x - min) / br), 0, n - 1)]++; });
    var kat = eimer.map(function (_, i) { return K.z(min + i * br, Math.abs(br) < 5 ? 1 : 0); });
    return balkenStehend({
      titel: spez.titel, unter: spez.unter, kategorien: kat,
      serien: [{ name: K.t('Häufigkeit'), werte: eimer, farbe: spez.farbe || 'blau' }],
      yEinheit: '', hoehe: spez.hoehe || 180, diverging: false
    }, true);
  }

  /* ============================================================
     ZEITSTRAHL
     ============================================================ */
  function zeitstrahl(spez) {
    var ev = spez.ereignisse || [];
    var d = K.el('div', { class: 'viz' });
    var innen = K.el('div', { class: 'viz-rahmen' });
    if (spez.titel) innen.appendChild(K.el('div', { class: 'viz-titel', text: spez.titel }));
    var liste = K.el('div', {});
    liste.style.cssText = 'position:relative;padding-left:22px';
    var linieD = K.el('div', {});
    linieD.style.cssText = 'position:absolute;left:6px;top:6px;bottom:6px;width:2px;background:var(--rand)';
    liste.appendChild(linieD);
    ev.forEach(function (x) {
      var punkt = K.el('div', {});
      punkt.style.cssText = 'position:relative;padding:0 0 14px 0';
      var kugel = K.el('div', {});
      var f2 = x.art === 'krise' ? 'var(--rot)' : x.art === 'gut' ? 'var(--gruen)' : 'var(--gold)';
      kugel.style.cssText = 'position:absolute;left:-20px;top:5px;width:10px;height:10px;border-radius:50%;background:' + f2 + ';box-shadow:0 0 0 3px var(--flaeche)';
      punkt.appendChild(kugel);
      var kopf = K.el('div', { html: '<b style="color:' + f2 + ';font-size:13px">' + K.htmlEsc(x.jahr) + '</b> <b style="font-size:14px">' + K.htmlEsc(x.titel) + '</b>' });
      punkt.appendChild(kopf);
      if (x.text) punkt.appendChild(K.el('div', { class: 'still', html: Mark.inline(x.text) }));
      liste.appendChild(punkt);
    });
    innen.appendChild(liste);
    d.appendChild(innen);
    if (spez.unter) d.appendChild(K.el('div', { class: 'viz-unter', html: Mark.inline(spez.unter) }));
    return d;
  }

  /* ============================================================
     PYRAMIDE (Schichten, unten breit)  -  Texte umbrochen
     ============================================================ */
  function pyramide(spez) {
    var st = spez.stufen || [];
    var n = st.length;
    var cx = B / 2;
    // Breite je Stufe: oben mindestens 150, unten die volle Breite
    function breiteBei(anteil) { return 150 + anteil * (B - 8 - 150); }
    var stufen = st.map(function (s, i) {
      var wMitte = breiteBei((i + 0.5) / n);
      var titel = umbrechen(s.titel, wMitte - 30, 11.5, true, 2);
      var text = s.text ? umbrechen(s.text, wMitte - 30, 9.5, false, 5) : [];
      var h = Math.max(34, titel.length * 13 + text.length * 11.5 + 12);
      return { s: s, titel: titel, text: text, h: h };
    });
    var H = stufen.reduce(function (a, x) { return a + x.h + 5; }, 0) + 6;
    var svg = e('svg', { viewBox: '0 0 ' + B + ' ' + H });
    var y = 4, gesamtH = H - 10;
    stufen.forEach(function (x, i) {
      var oben = breiteBei((y - 4) / gesamtH), unten = breiteBei((y - 4 + x.h) / gesamtH);
      var d = 'M' + (cx - oben / 2) + ' ' + y + ' L' + (cx + oben / 2) + ' ' + y +
        ' L' + (cx + unten / 2) + ' ' + (y + x.h) + ' L' + (cx - unten / 2) + ' ' + (y + x.h) + ' Z';
      var p = e('path', { d: d });
      p.setAttribute('style', 'fill:' + serienFarbe(n - 1 - i, x.s.farbe) + ';opacity:' + (0.35 + 0.5 * (n - i) / n) + ';stroke:' + farbe('flaeche') + ';stroke-width:1.5');
      svg.appendChild(p);
      var inhaltH = x.titel.length * 13 + x.text.length * 11.5;
      var ty = y + (x.h - inhaltH) / 2 + 10;
      var t = mehrzeilig(cx, ty, x.titel, { 'text-anchor': 'middle' }, 13);
      t.setAttribute('style', 'fill:' + farbe('text') + ';font-size:11.5px;font-weight:600');
      svg.appendChild(t);
      if (x.text.length) {
        var t2 = mehrzeilig(cx, ty + x.titel.length * 13, x.text, { 'text-anchor': 'middle' }, 11.5);
        t2.setAttribute('style', 'fill:' + farbe('text-leise') + ';font-size:9.5px');
        svg.appendChild(t2);
      }
      y += x.h + 5;
    });
    return rahmen(spez, svg);
  }

  /* ============================================================
     QUADRANTEN (2x2-Matrix)  -  Titel und Texte umbrochen
     ============================================================ */
  function quadranten(spez) {
    var felder = spez.felder || [];
    var l = spez.yLabel ? 22 : 6, r = 6, w = B - l - r;
    var fw = w / 2 - 14;
    var inhalt = [0, 1, 2, 3].map(function (i) {
      var fld = felder[i];
      if (!fld) return null;
      var titel = umbrechen(String(fld.titel || fld), fw, 11, true, 3);
      var text = fld.text ? umbrechen(fld.text, fw, 9.5, false, 10) : [];
      return { titel: titel, text: text, h: titel.length * 13 + (text.length ? 4 + text.length * 11.5 : 0) };
    });
    var hMax = 0;
    inhalt.forEach(function (x) { if (x) hMax = Math.max(hMax, x.h); });
    var fh = Math.max(64, hMax + 20);
    var o = 8, h = fh * 2;
    var hatUnten = spez.xLabel || spez.xLabelLinks;
    var H = o + h + (hatUnten ? 22 : 6);
    var svg = e('svg', { viewBox: '0 0 ' + B + ' ' + H });
    var paare = [[0, 0], [1, 0], [0, 1], [1, 1]];
    paare.forEach(function (p, i) {
      var x = inhalt[i];
      if (!x) return;
      var x0 = l + p[0] * w / 2, y0 = o + p[1] * h / 2;
      var rr = e('rect', { x: x0 + 2, y: y0 + 2, width: w / 2 - 4, height: h / 2 - 4, rx: 8 });
      rr.setAttribute('style', 'fill:' + serienFarbe(i, felder[i].farbe) + ';opacity:.13');
      svg.appendChild(rr);
      var cx = x0 + w / 4;
      var ty = y0 + (h / 2 - x.h) / 2 + 11;
      var t = mehrzeilig(cx, ty, x.titel, { 'text-anchor': 'middle' }, 13);
      t.setAttribute('style', 'fill:' + farbe('text') + ';font-size:11px;font-weight:600');
      svg.appendChild(t);
      if (x.text.length) {
        var t3 = mehrzeilig(cx, ty + x.titel.length * 13 + 3, x.text, { 'text-anchor': 'middle' }, 11.5);
        t3.setAttribute('style', 'fill:' + farbe('text-leise') + ';font-size:9.5px');
        svg.appendChild(t3);
      }
    });
    svg.appendChild(e('line', { x1: l + w / 2, x2: l + w / 2, y1: o, y2: o + h, class: 'achse' }));
    svg.appendChild(e('line', { x1: l, x2: l + w, y1: o + h / 2, y2: o + h / 2, class: 'achse' }));
    var platz = new Platz(B, H);
    (spez.punkte || []).forEach(function (q) {
      var cx = l + (q.x + 1) / 2 * w, cy = o + (1 - (q.y + 1) / 2) * h;
      var c = e('circle', { cx: cx, cy: cy, r: 4.5 });
      c.setAttribute('style', 'fill:' + farbe('gold'));
      svg.appendChild(c);
      if (q.name) platz.setze(svg, kuerzen(q.name, 110), [{ x: cx + 7, y: cy + 3.4, anker: 'start' }, { x: cx - 7, y: cy + 3.4, anker: 'end' }, { x: cx, y: cy - 8, anker: 'middle' }], S, 'fill:' + farbe('text'));
    });
    if (hatUnten) {
      var yU = o + h + 15;
      var halb = w / 2 - 6;
      if (spez.xLabelLinks) svg.appendChild(txt(l, yU, kuerzen(spez.xLabelLinks, halb), { 'text-anchor': 'start', class: 'wert' }));
      if (spez.xLabel) svg.appendChild(txt(l + w, yU, kuerzen(spez.xLabel, halb), { 'text-anchor': 'end', class: 'wert' }));
    }
    if (spez.yLabel) {
      var ym = o + h / 2;
      var yl = txt(12, ym, kuerzen(spez.yLabel, h - 10), { 'text-anchor': 'middle' });
      yl.setAttribute('transform', 'rotate(-90 12 ' + ym + ')');
      svg.appendChild(yl);
    }
    return rahmen(spez, svg);
  }

  /* ============================================================
     FLUSS / Ablaufdiagramm  -  Kaestchen wachsen mit dem Text,
     Pfeile werden um die Kaestchen herum gefuehrt (Wegsuche auf
     einem feinen Raster), Beschriftungen suchen freie Stellen
     ============================================================ */

  /** Kleiner Binaerheap fuer die Wegsuche. */
  function Haufen() { this.a = []; }
  Haufen.prototype.rein = function (p, v) {
    var a = this.a; a.push([p, v]);
    var i = a.length - 1;
    while (i > 0) { var j = (i - 1) >> 1; if (a[j][0] <= a[i][0]) break; var t = a[i]; a[i] = a[j]; a[j] = t; i = j; }
  };
  Haufen.prototype.raus = function () {
    var a = this.a, top = a[0], last = a.pop();
    if (a.length) {
      a[0] = last;
      var i = 0;
      for (;;) {
        var l = 2 * i + 1, r = l + 1, m = i;
        if (l < a.length && a[l][0] < a[m][0]) m = l;
        if (r < a.length && a[r][0] < a[m][0]) m = r;
        if (m === i) break;
        var t = a[i]; a[i] = a[m]; a[m] = t; i = m;
      }
    }
    return top[1];
  };

  function fluss(spez) {
    var kn = (spez.knoten || []).map(function (k) { return Object.assign({}, k); });
    var ka = spez.kanten || [];
    if (!kn.length) return rahmen(spez, e('svg', { viewBox: '0 0 ' + B + ' 40' }));
    // Raster normalisieren: manche Daten zaehlen Spalten und Zeilen ab 1
    var minS = Math.min.apply(null, kn.map(function (k) { return k.spalte || 0; }));
    var minZ = Math.min.apply(null, kn.map(function (k) { return k.zeile || 0; }));
    kn.forEach(function (k) { k.spalte = (k.spalte || 0) - minS; k.zeile = (k.zeile || 0) - minZ; });
    var spalten = Math.max.apply(null, kn.map(function (k) { return k.spalte; })) + 1;
    var zeilen = Math.max.apply(null, kn.map(function (k) { return k.zeile; })) + 1;
    // Viele Spalten nebeneinander passen nicht auf ein Telefon: dann Zeilen und Spalten tauschen
    if (spalten >= 4 && zeilen < spalten) {
      kn.forEach(function (k) { var s = k.spalte; k.spalte = k.zeile; k.zeile = s; });
      var tmp = spalten; spalten = zeilen; zeilen = tmp;
    }
    var eng = spalten >= 4;
    var luecke = spalten > 1 ? (eng ? 16 : 24) : 16;
    var rand = 4;
    var bw = (B - 2 * rand - (spalten - 1) * luecke) / spalten;
    var innenB = bw - (eng ? 6 : 10);
    var tGr = eng ? 9.5 : 10.5, nGr = eng ? 8.5 : 9;
    var tZh = tGr * 1.2, nZh = nGr * 1.17;
    var texte = kn.map(function (k) {
      var titel = umbrechen(String(k.titel || ''), innenB, tGr, true, 4);
      var notiz = k.notiz ? umbrechen(k.notiz, innenB, nGr, false, 6) : [];
      return { titel: titel, notiz: notiz, h: titel.length * tZh + (notiz.length ? 3 + notiz.length * nZh : 0) };
    });
    // Zeilenhoehe nach dem hoechsten Kaestchen der Zeile
    var zeilenH = [];
    for (var r0 = 0; r0 < zeilen; r0++) zeilenH.push(Math.max(34, spez.knotenHoehe || 0));
    kn.forEach(function (k, i) { zeilenH[k.zeile] = Math.max(zeilenH[k.zeile], texte[i].h + 14); });
    var beschriftet = ka.filter(function (k) { return k.text; }).length;
    // Platz zwischen den Zeilen fuer Pfeile und ihre Beschriftungen
    var zAbstand = !beschriftet ? 24 : (zeilen > 1 && beschriftet / (zeilen - 1) > 2.5 ? 44 : 34);
    var oben = beschriftet ? 16 : 8;
    var yZeile = [], yy = oben;
    zeilenH.forEach(function (h) { yZeile.push(yy); yy += h + zAbstand; });
    var H = yy - zAbstand + (beschriftet ? 16 : 8);
    var svg = e('svg', { viewBox: '0 0 ' + B + ' ' + H });
    var pos = {};
    kn.forEach(function (k) {
      var x = rand + k.spalte * (bw + luecke), y = yZeile[k.zeile], h = zeilenH[k.zeile];
      pos[k.id] = { x: x, y: y, w: bw, h: h, cx: x + bw / 2, cy: y + h / 2 };
    });

    /* ---- Raster fuer die Wegsuche ---- */
    var Z = 4, GX = Math.ceil(B / Z), GY = Math.ceil(H / Z);
    var frei = new Uint8Array(GX * GY), benutzt = new Uint8Array(GX * GY);
    for (var gi = 0; gi < frei.length; gi++) frei[gi] = 1;
    kn.forEach(function (k) {
      var p = pos[k.id];
      for (var gy = Math.max(0, Math.floor((p.y - 3) / Z)); gy <= Math.min(GY - 1, Math.floor((p.y + p.h + 3) / Z)); gy++)
        for (var gx = Math.max(0, Math.floor((p.x - 3) / Z)); gx <= Math.min(GX - 1, Math.floor((p.x + p.w + 3) / Z)); gx++)
          frei[gy * GX + gx] = 0;
    });
    function zelle(v) { return Math.floor(v / Z); }
    function mitteZ(g) { return g * Z + Z / 2; }
    var RICHT = [[1, 0], [0, 1], [-1, 0], [0, -1]];   // rechts, unten, links, oben

    /** Anschlusspunkte eines Kaestchens: je Seite drei, auf das Raster gelegt. */
    function aussen(gx, gy, d) {
      // vom Rand des Kaestchens nach aussen bis zur ersten freien Zelle
      for (var i = 0; i < 4; i++) {
        if (gx < 0 || gy < 0 || gx >= GX || gy >= GY) return null;
        if (frei[gy * GX + gx]) return { gx: gx, gy: gy };
        gx += RICHT[d][0]; gy += RICHT[d][1];
      }
      return null;
    }
    function anschluesse(p) {
      var r = [];
      [-1, 0, 1].forEach(function (o) {
        var gyL = zelle(p.cy + o * 8), gxT = zelle(p.cx + o * 12);
        var y = mitteZ(gyL), x = mitteZ(gxT), a;
        if (y > p.y + 4 && y < p.y + p.h - 4) {
          a = aussen(zelle(p.x - 1), gyL, 2); if (a) r.push({ px: p.x, py: y, gx: a.gx, gy: a.gy, d: 2, abw: Math.abs(o) });              // links
          a = aussen(zelle(p.x + p.w + 1), gyL, 0); if (a) r.push({ px: p.x + p.w, py: y, gx: a.gx, gy: a.gy, d: 0, abw: Math.abs(o) });   // rechts
        }
        if (x > p.x + 6 && x < p.x + p.w - 6) {
          a = aussen(gxT, zelle(p.y - 1), 3); if (a) r.push({ px: x, py: p.y, gx: a.gx, gy: a.gy, d: 3, abw: Math.abs(o) });               // oben
          a = aussen(gxT, zelle(p.y + p.h + 1), 1); if (a) r.push({ px: x, py: p.y + p.h, gx: a.gx, gy: a.gy, d: 1, abw: Math.abs(o) });   // unten
        }
      });
      return r;
    }

    /** Kuerzester Weg mit wenigen Knicken von einem der Start- zu einem der Zielanschluesse. */
    function weg(starts, ziele) {
      var N = GX * GY;
      var kosten = new Float64Array(N * 4); for (var i = 0; i < kosten.length; i++) kosten[i] = Infinity;
      var von = new Int32Array(N * 4); for (var j = 0; j < von.length; j++) von[j] = -1;
      var zielVon = {};
      ziele.forEach(function (z) { zielVon[z.gy * GX + z.gx] = z; });
      function h(gx, gy) {
        var m = Infinity;
        ziele.forEach(function (z) { m = Math.min(m, Math.abs(z.gx - gx) + Math.abs(z.gy - gy)); });
        return m;
      }
      var heap = new Haufen();
      starts.forEach(function (s) {
        var st = (s.gy * GX + s.gx) * 4 + s.d, c = s.abw * 2 + (benutzt[s.gy * GX + s.gx] ? 6 : 0);
        if (c < kosten[st]) { kosten[st] = c; heap.rein(c + h(s.gx, s.gy), st); }
      });
      var bestes = null, besteKosten = Infinity, runden = 0;
      while (heap.a.length && runden++ < 400000) {
        var st2 = heap.raus();
        var c0 = kosten[st2];
        if (c0 >= besteKosten) break;
        var zi = st2 >> 2, d0 = st2 & 3, gx0 = zi % GX, gy0 = (zi - gx0) / GX;
        var z = zielVon[zi];
        if (z) {
          // Ankunft: zuletzt in Richtung auf das Kaestchen (gegen die Aussenrichtung des Anschlusses)
          var ein = (z.d + 2) % 4;
          var cz = c0 + (d0 === ein ? 0 : 5) + z.abw * 2;
          if (cz < besteKosten) { besteKosten = cz; bestes = st2; }
        }
        for (var d = 0; d < 4; d++) {
          if (d === ((d0 + 2) & 3)) continue;
          var nx = gx0 + RICHT[d][0], ny = gy0 + RICHT[d][1];
          if (nx < 0 || ny < 0 || nx >= GX || ny >= GY) continue;
          var ni = ny * GX + nx;
          if (!frei[ni]) continue;
          var nc = c0 + 1 + (d === d0 ? 0 : 5) + (benutzt[ni] ? 3 : 0);
          var ns = ni * 4 + d;
          if (nc < kosten[ns]) { kosten[ns] = nc; von[ns] = st2; heap.rein(nc + h(nx, ny), ns); }
        }
      }
      if (bestes === null) return null;
      var zellen = [], s3 = bestes;
      while (s3 !== -1) { zellen.push(s3 >> 2); s3 = von[s3]; }
      zellen.reverse();
      var zEnd = zielVon[zellen[zellen.length - 1]];
      var zStartIdx = zellen[0];
      var sAnf = null;
      starts.forEach(function (s) { if (s.gy * GX + s.gx === zStartIdx && !sAnf) sAnf = s; });
      return { zellen: zellen, start: sAnf, ziel: zEnd };
    }

    function vereinfachen(pkte) {
      var r = [pkte[0]];
      for (var i = 1; i < pkte.length - 1; i++) {
        var a = r[r.length - 1], b = pkte[i], c = pkte[i + 1];
        var kollinear = Math.abs((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)) < 0.5;
        if (!kollinear) r.push(b);
      }
      r.push(pkte[pkte.length - 1]);
      return r;
    }

    /** Linienzug mit abgerundeten Ecken. */
    function rundPfad(pkte) {
      var d = 'M' + pkte[0].x.toFixed(1) + ' ' + pkte[0].y.toFixed(1);
      for (var i = 1; i < pkte.length - 1; i++) {
        var a = pkte[i - 1], b = pkte[i], c = pkte[i + 1];
        var l1 = Math.hypot(b.x - a.x, b.y - a.y), l2 = Math.hypot(c.x - b.x, c.y - b.y);
        var r = Math.min(6, l1 / 2, l2 / 2);
        var p1 = { x: b.x - (b.x - a.x) / (l1 || 1) * r, y: b.y - (b.y - a.y) / (l1 || 1) * r };
        var p2 = { x: b.x + (c.x - b.x) / (l2 || 1) * r, y: b.y + (c.y - b.y) / (l2 || 1) * r };
        d += ' L' + p1.x.toFixed(1) + ' ' + p1.y.toFixed(1) + ' Q' + b.x.toFixed(1) + ' ' + b.y.toFixed(1) + ' ' + p2.x.toFixed(1) + ' ' + p2.y.toFixed(1);
      }
      var z = pkte[pkte.length - 1];
      return d + ' L' + z.x.toFixed(1) + ' ' + z.y.toFixed(1);
    }

    function punktAuf(pkte, anteil) {
      var len = 0, seg = [];
      for (var i = 1; i < pkte.length; i++) { var d = Math.hypot(pkte[i].x - pkte[i - 1].x, pkte[i].y - pkte[i - 1].y); seg.push(d); len += d; }
      var ziel = len * anteil;
      for (var j = 0; j < seg.length; j++) {
        if (ziel <= seg[j] || j === seg.length - 1) {
          var f = seg[j] ? Math.min(1, ziel / seg[j]) : 0;
          return { x: pkte[j].x + (pkte[j + 1].x - pkte[j].x) * f, y: pkte[j].y + (pkte[j + 1].y - pkte[j].y) * f, senkrecht: Math.abs(pkte[j + 1].x - pkte[j].x) < 1 };
        }
        ziel -= seg[j];
      }
      return { x: pkte[0].x, y: pkte[0].y, senkrecht: false };
    }

    /* ---- Pfeile ---- */
    var farbeLinie = farbe('rand');
    var linien = [];
    // kurze Verbindungen zuerst, damit sie die direkten Wege bekommen
    var reihenfolge = ka.map(function (k, i) { return i; }).filter(function (i) { return pos[ka[i].von] && pos[ka[i].bis] && ka[i].von !== ka[i].bis; });
    reihenfolge.sort(function (i, j) {
      function abst(k) { var a = pos[k.von], b = pos[k.bis]; return Math.abs(a.cx - b.cx) + Math.abs(a.cy - b.cy); }
      return abst(ka[i]) - abst(ka[j]);
    });
    reihenfolge.forEach(function (i) {
      var k = ka[i], a = pos[k.von], b = pos[k.bis];
      var w = weg(anschluesse(a), anschluesse(b));
      var pkte;
      if (w) {
        pkte = [{ x: w.start.px, y: w.start.py }];
        w.zellen.forEach(function (zi) { var gx = zi % GX; pkte.push({ x: mitteZ(gx), y: mitteZ((zi - gx) / GX) }); });
        pkte.push({ x: w.ziel.px, y: w.ziel.py });
        // Anschlussstuecke exakt waagerecht bzw. senkrecht halten
        if (w.start.d === 0 || w.start.d === 2) pkte[1].y = pkte[0].y; else pkte[1].x = pkte[0].x;
        var n = pkte.length;
        if (w.ziel.d === 0 || w.ziel.d === 2) pkte[n - 2].y = pkte[n - 1].y; else pkte[n - 2].x = pkte[n - 1].x;
        pkte = vereinfachen(pkte);
        w.zellen.forEach(function (zi) { benutzt[zi] = 1; });
      } else {
        // Notfall: gerade Linie zwischen den Mittelpunkten der Kanten
        pkte = [{ x: a.cx, y: a.cy < b.cy ? a.y + a.h : a.y }, { x: b.cx, y: a.cy < b.cy ? b.y : b.y + b.h }];
      }
      linien.push({ k: k, pkte: pkte, a: a });
    });

    linien.forEach(function (l) {
      var pkte = l.pkte;
      var spitze = pkte[pkte.length - 1], vor = pkte[pkte.length - 2];
      var lg = Math.hypot(spitze.x - vor.x, spitze.y - vor.y) || 1;
      var ux = (spitze.x - vor.x) / lg, uy = (spitze.y - vor.y) / lg;
      var zug = pkte.slice(0, -1).concat([{ x: spitze.x - ux * 4, y: spitze.y - uy * 4 }]);
      var p = e('path', { d: rundPfad(zug) });
      p.setAttribute('style', 'fill:none;stroke:' + farbeLinie + ';stroke-width:1.6;stroke-linejoin:round');
      svg.appendChild(p);
      var nx = -uy, ny = ux;
      var pfeil = e('path', { d: 'M' + spitze.x + ' ' + spitze.y + ' L' + (spitze.x - ux * 6 + nx * 3.6) + ' ' + (spitze.y - uy * 6 + ny * 3.6) + ' L' + (spitze.x - ux * 6 - nx * 3.6) + ' ' + (spitze.y - uy * 6 - ny * 3.6) + ' Z' });
      pfeil.setAttribute('style', 'fill:' + farbeLinie);
      svg.appendChild(pfeil);
    });

    /* ---- Kaestchen ---- */
    kn.forEach(function (k, i) {
      var p = pos[k.id], t = texte[i];
      var r = e('rect', { x: p.x, y: p.y, width: p.w, height: p.h, rx: 8 });
      var c = serienFarbe(i, k.farbe);
      r.setAttribute('style', 'fill:' + c + ';fill-opacity:.14;stroke:' + c + ';stroke-width:1.2');
      svg.appendChild(r);
      var ty = p.y + (p.h - t.h) / 2 + tGr * 0.95;
      var tt = mehrzeilig(p.cx, ty, t.titel, { 'text-anchor': 'middle' }, tZh);
      tt.setAttribute('style', 'fill:' + farbe('text') + ';font-size:' + tGr + 'px;font-weight:600');
      svg.appendChild(tt);
      if (t.notiz.length) {
        var t2 = mehrzeilig(p.cx, ty + t.titel.length * tZh + 2, t.notiz, { 'text-anchor': 'middle' }, nZh);
        t2.setAttribute('style', 'fill:' + farbe('text-leise') + ';font-size:' + nGr + 'px');
        svg.appendChild(t2);
      }
    });

    /* ---- Beschriftungen: an freier Stelle entlang des Pfeils, sonst Nummer mit Liste darunter ---- */
    var platz = new Platz(B, H);
    kn.forEach(function (k) { var p = pos[k.id]; platz.belegen({ l: p.x - 1, r: p.x + p.w + 1, t: p.y - 1, u: p.y + p.h + 1 }); });
    var gr = 8.5, nummern = [];
    var mitte = gr * 0.9 / 2 - 1;   // Grundlinie so, dass der Text mittig auf dem Punkt sitzt
    linien.forEach(function (l) {
      if (!l.k.text) return;
      var s = kuerzen(l.k.text, B * 0.6, gr);
      var w = breite(s, gr) + 6;
      var kand = [];
      [0.5, 0.4, 0.6, 0.3, 0.7, 0.2, 0.8, 0.15, 0.85].forEach(function (f) {
        var q = punktAuf(l.pkte, f);
        [0, -7, 7, -12, 12].forEach(function (dy) {
          [0, -w * 0.35, w * 0.35].forEach(function (dx) { kand.push({ x: q.x + dx, y: q.y + dy + mitte }); });
        });
        if (q.senkrecht) {
          kand.push({ x: q.x + w / 2 + 3, y: q.y + mitte });
          kand.push({ x: q.x - w / 2 - 3, y: q.y + mitte });
        }
      });
      // Kurze waagerechte Pfeile zwischen Nachbarn: Beschriftung in den Zwischenraum darueber oder darunter
      var y0 = l.pkte[0].y;
      if (l.pkte.every(function (p) { return Math.abs(p.y - y0) < 1; })) {
        var xm = punktAuf(l.pkte, 0.5).x, a = l.a;
        [a.y - Math.min(zAbstand / 2, a.y / 2), a.y + a.h + Math.min(zAbstand / 2, (H - a.y - a.h) / 2)].forEach(function (yz) {
          [0, -w * 0.3, w * 0.3, -w * 0.5, w * 0.5].forEach(function (dx) { kand.push({ x: xm + dx, y: yz + mitte }); });
        });
      }
      for (var i = 0; i < kand.length; i++) {
        var c = kand[i];
        var kst = { l: c.x - w / 2, r: c.x + w / 2, t: c.y - gr * 0.9 - 1, u: c.y + 3 };
        if (platz.frei(kst)) {
          platz.belegen(kst);
          var hg = e('rect', { x: kst.l, y: kst.t, width: w, height: kst.u - kst.t, rx: 3 });
          hg.setAttribute('style', 'fill:' + farbe('flaeche'));
          svg.appendChild(hg);
          var t = txt(c.x, c.y, s, { 'text-anchor': 'middle' });
          t.setAttribute('style', 'fill:' + farbe('text-still') + ';font-size:' + gr + 'px');
          svg.appendChild(t);
          return;
        }
      }
      // kein Platz: kleine Nummer am Pfeil, Text in der Liste unter der Grafik
      var nr = nummern.length + 1;
      nummern.push({ name: nr + '  ' + l.k.text, farbe: farbeLinie });
      var m = null;
      var anteile = [0.5, 0.4, 0.6, 0.3, 0.7, 0.2, 0.8, 0.1, 0.9];
      for (var j = 0; j < anteile.length && !m; j++) {
        var q2 = punktAuf(l.pkte, anteile[j]);
        [0, -8, 8].some(function (dy) {
          var kk = { l: q2.x - 7, r: q2.x + 7, t: q2.y + dy - 7, u: q2.y + dy + 7 };
          if (platz.frei(kk)) { m = { x: q2.x, y: q2.y + dy }; return true; }
          return false;
        });
      }
      if (!m) m = punktAuf(l.pkte, 0.85);
      platz.belegen({ l: m.x - 7, r: m.x + 7, t: m.y - 7, u: m.y + 7 });
      var kreis = e('circle', { cx: m.x, cy: m.y, r: 6.5 });
      kreis.setAttribute('style', 'fill:' + farbe('flaeche') + ';stroke:' + farbeLinie + ';stroke-width:1.2');
      svg.appendChild(kreis);
      var tn = txt(m.x, m.y + 3, String(nr), { 'text-anchor': 'middle', class: 'wert' });
      tn.setAttribute('style', 'fill:' + farbe('text') + ';font-size:8.5px;font-weight:700');
      svg.appendChild(tn);
    });
    return rahmen(spez, svg, nummern.length ? [listenLegende(nummern)] : []);
  }

  /* ============================================================
     ZINSKURVE
     ============================================================ */
  function zinskurve(spez) {
    var serien = (spez.kurven || []).map(function (k, i) {
      return { name: k.name, werte: k.punkte, farbe: k.farbe || REIHE[i % REIHE.length], gestrichelt: k.gestrichelt };
    });
    return linie({
      art: 'linie', titel: spez.titel, unter: spez.unter, serien: serien,
      xTyp: 'zahl', xEinheit: ' J', yEinheit: ' %', punkte: true, yNull: false, hoehe: spez.hoehe || 190
    });
  }

  /* ============================================================
     KERZEN
     ============================================================ */
  function kerzen(spez) {
    var k = spez.kerzen || [];
    if (!k.length) return rahmen(spez, e('svg', { viewBox: '0 0 ' + B + ' 40' }));
    var hoch = k.map(function (x) { return x[2]; }), tief = k.map(function (x) { return x[3]; });
    var t = teilung(Math.min.apply(null, tief), Math.max.apply(null, hoch), 4);
    var yText = function (v) { return kurz(v, spez.yEinheit); };
    var f = flaeche({ xMin: 0, xMax: k.length, yMin: t.min, yMax: t.max, hoehe: 200, links: randFuer(t.marken.map(yText)), unten: 12 });
    var svg = e('svg', { viewBox: '0 0 ' + B + ' ' + f.H });
    gitterY(svg, f, t.marken, yText, false);
    var bw = f.w / k.length;
    k.forEach(function (x, i) {
      var cx = f.l + i * bw + bw / 2;
      var auf = x[4] >= x[1];
      var c = farbe(auf ? 'gruen' : 'rot');
      var l1 = e('line', { x1: cx, x2: cx, y1: f.y(x[2]), y2: f.y(x[3]) });
      l1.setAttribute('style', 'stroke:' + c + ';stroke-width:1');
      svg.appendChild(l1);
      var y0 = f.y(Math.max(x[1], x[4])), y1 = f.y(Math.min(x[1], x[4]));
      var r = e('rect', { x: cx - bw * 0.3, y: y0, width: bw * 0.6, height: Math.max(1, y1 - y0) });
      r.setAttribute('style', 'fill:' + c);
      svg.appendChild(r);
    });
    (spez.linien || []).forEach(function (ln, j) {
      var pfad = ln.werte.map(function (v, i) { return (i ? 'L' : 'M') + (f.l + i * bw + bw / 2) + ' ' + f.y(v); }).join(' ');
      var p = e('path', { d: pfad });
      p.setAttribute('style', 'fill:none;stroke:' + serienFarbe(j, ln.farbe) + ';stroke-width:1.5');
      svg.appendChild(p);
    });
    return rahmen(spez, svg, spez.linien && spez.linien.length ? [legende(spez.linien)] : []);
  }

  /* ============================================================
     RADAR
     ============================================================ */
  function radar(spez) {
    var achsen = spez.achsen || [], serien = spez.serien || [];
    var n = achsen.length, H = 240, cx = B / 2, cy = H / 2, R = 72;
    var svg = e('svg', { viewBox: '0 0 ' + B + ' ' + H });
    var max = spez.max || 10;
    [0.25, 0.5, 0.75, 1].forEach(function (q) {
      var d = '';
      for (var i = 0; i < n; i++) {
        var a = -Math.PI / 2 + i / n * Math.PI * 2;
        d += (i ? 'L' : 'M') + (cx + Math.cos(a) * R * q) + ' ' + (cy + Math.sin(a) * R * q);
      }
      var p = e('path', { d: d + ' Z', class: 'gitter' });
      p.setAttribute('style', 'fill:none;stroke:' + farbe('rand-weich'));
      svg.appendChild(p);
    });
    achsen.forEach(function (a, i) {
      var w = -Math.PI / 2 + i / n * Math.PI * 2;
      var l = e('line', { x1: cx, y1: cy, x2: cx + Math.cos(w) * R, y2: cy + Math.sin(w) * R });
      l.setAttribute('style', 'stroke:' + farbe('rand-weich'));
      svg.appendChild(l);
      var cos = Math.cos(w), anker = Math.abs(cos) < 0.3 ? 'middle' : (cos > 0 ? 'start' : 'end');
      var tx2 = cx + cos * (R + 10), ty = cy + Math.sin(w) * (R + 10);
      var maxB = anker === 'middle' ? 120 : (anker === 'start' ? B - tx2 - 2 : tx2 - 2);
      var zl = umbrechen(a, Math.max(40, maxB), S, false, 2);
      svg.appendChild(mehrzeilig(tx2, ty + 3.4 - (zl.length - 1) * 5.8, zl, { 'text-anchor': anker }, 11.6));
    });
    serien.forEach(function (s, j) {
      var d = '';
      s.werte.forEach(function (v, i) {
        var a = -Math.PI / 2 + i / n * Math.PI * 2, q = K.grenz(v / max, 0, 1);
        d += (i ? 'L' : 'M') + (cx + Math.cos(a) * R * q) + ' ' + (cy + Math.sin(a) * R * q);
      });
      var p = e('path', { d: d + ' Z' });
      var c = serienFarbe(j, s.farbe);
      p.setAttribute('style', 'fill:' + c + ';fill-opacity:.18;stroke:' + c + ';stroke-width:2');
      svg.appendChild(p);
    });
    return rahmen(spez, svg, serien.length > 1 ? [legende(serien)] : []);
  }

  /* ============================================================
     TREEMAP
     ============================================================ */
  function treemap(spez) {
    var teile = (spez.teile || []).slice().sort(function (a, b) { return b.wert - a.wert; });
    var gesamt = K.summe(teile.map(function (t) { return t.wert; }));
    var H = spez.hoehe || 190;
    var svg = e('svg', { viewBox: '0 0 ' + B + ' ' + H });
    var x = 0, y = 0, w = B, h = H, i = 0;
    var rest = gesamt;
    var ohne = [];
    while (i < teile.length) {
      var waagerecht = w >= h;
      var t = teile[i];
      var anteil = t.wert / rest;
      var bw = waagerecht ? w * anteil : w;
      var bh = waagerecht ? h : h * anteil;
      if (i === teile.length - 1) { bw = w; bh = h; }
      var r = e('rect', { x: x + 1, y: y + 1, width: Math.max(1, bw - 2), height: Math.max(1, bh - 2), rx: 3 });
      r.setAttribute('style', 'fill:' + serienFarbe(i, t.farbe) + ';opacity:' + (0.85 - i * 0.045));
      svg.appendChild(r);
      var nameKurz = kuerzen(t.name, bw - 10, 10, true);
      if (bw > 34 && bh > 18 && (nameKurz === t.name || bh > 30)) {
        if (nameKurz !== t.name || bh <= 30) ohne.push({ name: t.name, farbe: serienFarbe(i, t.farbe), wert: K.mitEinheit(K.z(t.wert / gesamt * 100, 1), ' %') });
        // Ein Stummel wie „Gro…“ hilft niemandem; der Name steht dann nur in der Liste darunter
        if (nameKurz === t.name || nameKurz.length >= 6) {
          var tt = txt(x + 5, y + 14, nameKurz, {});
          tt.setAttribute('style', 'fill:var(--grund);font-size:10px;font-weight:700');
          svg.appendChild(tt);
        }
        if (bh > 30) {
          var tv = txt(x + 5, y + 26, K.mitEinheit(K.z(t.wert / gesamt * 100, 1), ' %'), { class: 'wert' });
          tv.setAttribute('style', 'fill:var(--grund);opacity:.75;font-size:9.5px');
          svg.appendChild(tv);
        }
      } else {
        ohne.push({ name: t.name, farbe: serienFarbe(i, t.farbe), wert: K.mitEinheit(K.z(t.wert / gesamt * 100, 1), ' %') });
      }
      rest -= t.wert;
      if (waagerecht) { x += bw; w -= bw; } else { y += bh; h -= bh; }
      i++;
    }
    return rahmen(spez, svg, ohne.length ? [listenLegende(ohne)] : []);
  }

  /* ============================================================
     TACHO / Bandanzeige
     ============================================================ */
  function tacho(spez) {
    var textZeilen = spez.text ? umbrechen(spez.text, B - 24, S, false, 3) : [];
    var H = 140 + textZeilen.length * 12.5, cx = B / 2, cy = 122, R = 88;
    var svg = e('svg', { viewBox: '0 0 ' + B + ' ' + H });
    var min = spez.min || 0, max = spez.max || 100;
    var zonen = spez.zonen || [{ bis: max, farbe: 'gold' }];
    var a0 = Math.PI;
    var platz = new Platz(B, H);
    var beschr = [];
    zonen.forEach(function (z, i) {
      var a1 = Math.PI + (K.grenz(z.bis, min, max) - min) / (max - min) * Math.PI;
      var p1 = [cx + R * Math.cos(a0), cy + R * Math.sin(a0)];
      var p2 = [cx + R * Math.cos(a1), cy + R * Math.sin(a1)];
      var ri = R - 20;
      var p3 = [cx + ri * Math.cos(a1), cy + ri * Math.sin(a1)];
      var p4 = [cx + ri * Math.cos(a0), cy + ri * Math.sin(a0)];
      var pa = e('path', { d: 'M' + p1 + ' A' + R + ' ' + R + ' 0 0 1 ' + p2 + ' L' + p3 + ' A' + ri + ' ' + ri + ' 0 0 0 ' + p4 + ' Z' });
      pa.setAttribute('style', 'fill:' + serienFarbe(i, z.farbe) + ';opacity:.6');
      svg.appendChild(pa);
      if (z.label) beschr.push({ text: z.label, am: (a0 + a1) / 2, farbe: serienFarbe(i, z.farbe) });
      a0 = a1;
    });
    var aw = Math.PI + (K.grenz(spez.wert, min, max) - min) / (max - min) * Math.PI;
    var zg = e('line', { x1: cx, y1: cy, x2: cx + (R - 6) * Math.cos(aw), y2: cy + (R - 6) * Math.sin(aw) });
    zg.setAttribute('style', 'stroke:' + farbe('text') + ';stroke-width:2.6;stroke-linecap:round');
    svg.appendChild(zg);
    var kn = e('circle', { cx: cx, cy: cy, r: 5 });
    kn.setAttribute('style', 'fill:' + farbe('text'));
    svg.appendChild(kn);
    var tw = txt(cx, cy - 28, K.mitEinheit(K.z(spez.wert, spez.nachkomma === undefined ? 1 : spez.nachkomma), spez.einheit), { 'text-anchor': 'middle', class: 'wert' });
    tw.setAttribute('style', 'fill:' + farbe('gold') + ';font-size:20px;font-weight:700');
    svg.appendChild(tw);
    platz.belegen({ l: cx - 50, r: cx + 50, t: cy - 46, u: cy - 22 });
    if (textZeilen.length) {
      svg.appendChild(mehrzeilig(cx, cy + 22, textZeilen, { 'text-anchor': 'middle' }, 12.5));
      textZeilen.forEach(function (z, i) { platz.belegen(platz.kasten(cx, cy + 22 + i * 12.5, breite(z), S, 'middle')); });
    }
    // Zonenbeschriftungen aussen am Bogen; wo kein Platz ist, stehen sie in der Legende
    var ohne = [];
    beschr.forEach(function (b) {
      var x = cx + (R + 8) * Math.cos(b.am), y = cy + (R + 8) * Math.sin(b.am) + 3;
      var anker = Math.cos(b.am) < -0.3 ? 'end' : Math.cos(b.am) > 0.3 ? 'start' : 'middle';
      if (!platz.setze(svg, b.text, [{ x: x, y: y, anker: anker }], 9, 'fill:' + farbe('text-leise') + ';font-size:9px')) ohne.push({ name: b.text, farbe: b.farbe });
    });
    return rahmen(spez, svg, ohne.length ? [listenLegende(ohne)] : []);
  }

  /* ============================================================
     BOXPLOT
     ============================================================ */
  function boxplot(spez) {
    var gr = spez.gruppen || [];
    var alle = [].concat.apply([], gr.map(function (g) { return g.werte; }));
    var t = teilung(Math.min.apply(null, alle), Math.max.apply(null, alle), 4);
    var yText = function (v) { return kurz(v, spez.yEinheit); };
    var links = randFuer(t.marken.map(yText));
    var bw = (B - links - 10) / gr.length;
    var namen = gr.map(function (g) { return umbrechen(g.name, bw - 4, S, false, 2); });
    var zwei = namen.some(function (z) { return z.length > 1; });
    var f = flaeche({ xMin: 0, xMax: gr.length, yMin: t.min, yMax: t.max, hoehe: 210, links: links, unten: zwei ? 36 : 26 });
    var svg = e('svg', { viewBox: '0 0 ' + B + ' ' + f.H });
    gitterY(svg, f, t.marken, yText, true);
    gr.forEach(function (g, i) {
      var s = g.werte.slice().sort(function (a, b) { return a - b; });
      function q(p) { var idx = (s.length - 1) * p; var lo = Math.floor(idx), hi = Math.ceil(idx); return s[lo] + (s[hi] - s[lo]) * (idx - lo); }
      var q1 = q(0.25), q2 = q(0.5), q3 = q(0.75), mn = s[0], mx = s[s.length - 1];
      var cx = f.l + i * bw + bw / 2, w2 = Math.min(30, bw * 0.4);
      var c = serienFarbe(i, g.farbe);
      var lv = e('line', { x1: cx, x2: cx, y1: f.y(mx), y2: f.y(mn) });
      lv.setAttribute('style', 'stroke:' + c + ';stroke-width:1');
      svg.appendChild(lv);
      var r = e('rect', { x: cx - w2 / 2, y: f.y(q3), width: w2, height: Math.max(1, f.y(q1) - f.y(q3)), rx: 2 });
      r.setAttribute('style', 'fill:' + c + ';fill-opacity:.3;stroke:' + c + ';stroke-width:1.2');
      svg.appendChild(r);
      var lm = e('line', { x1: cx - w2 / 2, x2: cx + w2 / 2, y1: f.y(q2), y2: f.y(q2) });
      lm.setAttribute('style', 'stroke:' + c + ';stroke-width:2.2');
      svg.appendChild(lm);
      svg.appendChild(mehrzeilig(cx, f.o + f.h + 14, namen[i], { 'text-anchor': 'middle' }, 11.2));
    });
    return rahmen(spez, svg);
  }

  /* ============================================================
     FAECHER / Bandbreite (z.B. Monte-Carlo)
     ============================================================ */
  function faecher(spez) {
    var x = spez.x || [];
    var alle = [];
    (spez.baender || []).forEach(function (b) { alle = alle.concat(b.unten, b.oben); });
    (spez.linien || []).forEach(function (l) { alle = alle.concat(l.werte); });
    alle = alle.filter(isFinite);
    var t = teilung(spez.yMin !== undefined ? spez.yMin : Math.min(0, Math.min.apply(null, alle)), Math.max.apply(null, alle), 5);
    var yText = function (v) { return kurz(v, spez.yEinheit || ' €'); };
    var f = flaeche({ xMin: x[0], xMax: x[x.length - 1], yMin: t.min, yMax: t.max, hoehe: spez.hoehe || 200, links: randFuer(t.marken.map(yText)) });
    var svg = e('svg', { viewBox: '0 0 ' + B + ' ' + f.H });
    var platz = new Platz(B, f.H);
    gitterY(svg, f, t.marken, yText, true, platz);
    var tX = teilung(x[0], x[x.length - 1], 4);
    xBeschriftung(svg, f, tX.marken.filter(function (v) { return v >= x[0] && v <= x[x.length - 1]; }), function (v) { return K.z(v, 0); }, platz);
    (spez.baender || []).forEach(function (b, i) {
      var d = '';
      x.forEach(function (xv, j) { d += (j ? 'L' : 'M') + f.x(xv) + ' ' + f.y(b.oben[j]); });
      for (var j = x.length - 1; j >= 0; j--) d += 'L' + f.x(x[j]) + ' ' + f.y(b.unten[j]);
      var p = e('path', { d: d + ' Z' });
      p.setAttribute('style', 'fill:' + serienFarbe(i, b.farbe || 'blau') + ';opacity:' + (b.opazitaet || 0.16) + ';stroke:none');
      svg.appendChild(p);
    });
    (spez.linien || []).forEach(function (l, i) {
      var d = x.map(function (xv, j) { return (j ? 'L' : 'M') + f.x(xv) + ' ' + f.y(l.werte[j]); }).join(' ');
      var p = e('path', { d: d });
      p.setAttribute('style', 'fill:none;stroke:' + serienFarbe(i, l.farbe || 'gold') + ';stroke-width:2' + (l.gestrichelt ? ';stroke-dasharray:4 3' : ''));
      svg.appendChild(p);
    });
    var ex = spez.linien && spez.linien.length ? [legende(spez.linien)] : [];
    return rahmen(spez, svg, ex);
  }

  /* ============================================================
     FORTSCHRITTSRING (klein, fuer Listen)
     ============================================================ */
  function ring(quote, text) {
    var d = K.el('div', { class: 'ring' });
    var svg = e('svg', { viewBox: '0 0 38 38' });
    var r = 16, u = 2 * Math.PI * r;
    var bg = e('circle', { cx: 19, cy: 19, r: r });
    bg.setAttribute('style', 'fill:none;stroke:' + farbe('rand') + ';stroke-width:3');
    svg.appendChild(bg);
    var fg = e('circle', { cx: 19, cy: 19, r: r });
    fg.setAttribute('style', 'fill:none;stroke:' + farbe(quote >= 1 ? 'gruen' : 'gold') + ';stroke-width:3;stroke-linecap:round;stroke-dasharray:' + (u * quote) + ' ' + u);
    svg.appendChild(fg);
    d.appendChild(svg);
    d.appendChild(K.el('b', { text: text }));
    return d;
  }

  /* ============================================================
     Verteiler
     ============================================================ */
  var arten = {
    linie: linie, flaeche: linie, balken: balken, saeulen: balken, donut: donut, ring: donut,
    zinseszins: zinseszins, drawdown: drawdown, payoff: payoff, wasserfall: wasserfall,
    heatmap: heatmap, streu: streu, histogramm: histogramm, zeitstrahl: zeitstrahl,
    pyramide: pyramide, quadranten: quadranten, fluss: fluss, zinskurve: zinskurve,
    kerzen: kerzen, radar: radar, treemap: treemap, tacho: tacho, boxplot: boxplot, faecher: faecher
  };

  function zeichne(spez) {
    try {
      var fn = arten[spez.art];
      if (!fn) return K.el('div', { class: 'still', text: K.t('Unbekannte Darstellung: {0}', String(spez.art)) });
      return fn(spez);
    } catch (err) {
      console.warn('Viz-Fehler', spez.art, err);
      return K.el('div', { class: 'still', text: K.t('Diagramm konnte nicht gezeichnet werden.') });
    }
  }

  window.Viz = {
    zeichne: zeichne, farbe: farbe, farbenNeu: farbenNeu, ring: ring,
    teilung: teilung, arten: Object.keys(arten), einpassen: einpassen
  };
})();
