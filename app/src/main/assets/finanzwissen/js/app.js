/* ============================================================
   app.js - Navigation des Finanzwissen-Bereichs in Tryfolio.
   Ein Stapel von Ansichten, keine Reiter und kein Zurück-Pfeil im Kopf.
   Erscheinungsbild und Sprache kommen von der Depot-App (URL-Parameter,
   spaeter ueber window.setzeTheme). Die Zurueck-Geste des Telefons geht
   ueber window.appBack eine Ebene zurueck; am Anfang des Stapels schliesst
   die Depot-App den Bereich.
   Vorwaerts gleitet die neue Ebene von rechts ueber die bisherige. Von
   links nach rechts wischen geht eine Ebene zurueck: Die Seite folgt dem
   Finger nach rechts, die vorherige liegt darunter und kommt leicht
   versetzt von links nach. Auf der Startseite schliesst ein Wisch nach
   unten den Bereich; das uebernimmt die Depot-App, dafuer meldet die Seite
   ihren Zustand ueber Troete.zustand().
   ============================================================ */
(function () {
  'use strict';
  var el = K.el;
  var App = {};
  window.App = App;

  var stapel = [];               // [{ansicht, par, scroll, knoten}]
  var buehne;

  /* ---------- Parameter der Depot-App ---------- */
  function parameter() {
    var p = {};
    String(location.search || '').replace(/^\?/, '').split('&').forEach(function (t) {
      if (!t) return;
      var i = t.indexOf('=');
      p[decodeURIComponent(i < 0 ? t : t.slice(0, i))] = decodeURIComponent(i < 0 ? '' : t.slice(i + 1));
    });
    return p;
  }
  var par0 = parameter();
  // Unter Windows und Linux oeffnet die Depot-App den Bereich im Browser (umgebung=browser)
  var imBrowser = par0.umgebung === 'browser';
  App.lang = K.lang;

  window.setzeTheme = function (t) {
    fertigSofort();
    document.documentElement.setAttribute('data-theme', t === 'hell' ? 'hell' : 'dunkel');
    if (window.Viz) Viz.farbenNeu();
    // Zwischengespeicherte tiefere Ebenen tragen noch die alten Farben: beim Zurueckgehen neu bauen
    stapel.forEach(function (s) { delete s.knoten; });
    if (buehne) zeichnen(true);
  };

  /* ---------- Zustand fuer die Depot-App ---------- */
  var gemeldet = '';
  function melden() {
    var tiefe = stapel.length, oben = (window.scrollY || 0) <= 0, blatt = !!K.blattOffen;
    var z = tiefe + '|' + oben + '|' + blatt;
    if (z === gemeldet) return;
    gemeldet = z;
    try { if (window.Troete && Troete.zustand) Troete.zustand(tiefe, oben, blatt); } catch (e) {}
  }
  App.melden = melden;
  window.addEventListener('scroll', melden, { passive: true });

  /* ---------- Navigation ---------- */
  var wartet = null;             // Wechsel, der ohne Animationen kurz auf die sichtbare Hervorhebung wartet

  App.gehe = function (ansicht, par, ersetzen) {
    par = par || {};
    if (wartet) { clearTimeout(wartet); wartet = null; }
    fertigSofort();
    if (K.blattOffen) K.blattZu();
    var alt = buehne.firstChild;
    // Sind Animationen ausgeschaltet, bleibt die angetippte Zeile einen Augenblick hell stehen
    if (bewegungAus() && alt && alt.querySelector && alt.querySelector('.gedrueckt')) {
      wartet = setTimeout(function () { wartet = null; wechseln(ansicht, par, ersetzen, false); }, 120);
      return;
    }
    wechseln(ansicht, par, ersetzen, !bewegungAus());
  };

  function wechseln(ansicht, par, ersetzen, gleitend) {
    var aktuell = stapel[stapel.length - 1];
    var altKnoten = buehne.firstChild, altScroll = window.scrollY;
    if (aktuell) aktuell.scroll = altScroll;
    if (ersetzen && stapel.length) stapel.pop();
    stapel.push({ ansicht: ansicht, par: par });
    if (imBrowser && !ersetzen) { try { history.pushState({ tiefe: stapel.length }, ''); } catch (e) {} }
    if (!altKnoten || !gleitend) { zeichnen(); return; }
    // Die bisherige Ebene bleibt als Unterlage stehen (mit der angetippten Zeile), die neue gleitet
    // von rechts darueber
    unterlage = unterlageMit(altKnoten, altScroll);
    zeichnen();
    gleiten(window.innerWidth, 0, unterlageWeg);
  }

  /**
   * Weiter zur naechsten Lektion: ersetzt die aktuelle Lektion, damit Zurueck beim Thema landet.
   * Gehoert sie zum naechsten Thema, wechselt auch die Themenseite darunter.
   */
  App.weiter = function (id) {
    var ziel = App.lektionStelle(id);
    var unten = stapel[stapel.length - 2];
    if (ziel && unten && unten.ansicht === 'modul' && unten.par && unten.par.id !== ziel.m.id) {
      stapel[stapel.length - 2] = { ansicht: 'modul', par: { id: ziel.m.id } };
    }
    App.gehe('lektion', { id: id }, true);
  };

  /** true, wenn etwas geschlossen oder eine Ebene zurueckgegangen wurde. Die Seite gleitet nach rechts hinaus. */
  App.zurueck = function () {
    if (K.blattOffen) { K.blattZu(); melden(); return true; }
    if (wartet) { clearTimeout(wartet); wartet = null; return true; }  // der angetippte Wechsel faellt aus
    if (stapel.length < 2) return false;
    if (auslauf || (wisch && wisch.art === 'zurueck')) return true;   // eine Wischgeste laeuft oder laeuft aus
    fertigSofort();
    if (bewegungAus()) { stapel.pop(); zeichnen(); return true; }
    unterlage = unterlageAufbauen();
    gleiten(0, window.innerWidth, zurueckAbschliessen);
    return true;
  };
  window.appBack = App.zurueck;

  /* ---------- Im Browser: Zurueck des Browsers (auch die Maustaste) und Escape gehen eine Ebene zurueck ---------- */
  if (imBrowser) {
    window.addEventListener('popstate', function () { App.zurueck(); });
    document.addEventListener('keydown', function (ev) {
      if (ev.key === 'Escape' && App.zurueck()) ev.preventDefault();
    });
  }

  App.neuZeichnen = function () { zeichnen(true); };
  App.scrollHoch = function () { window.scrollTo(0, 0); };

  /** Baut die Ansicht eines Stapeleintrags (ohne sie einzuhaengen). */
  function bauen(s) {
    var fn = Ansichten[s.ansicht];
    try {
      return fn ? fn(s.par) : el('div', { class: 'leer', text: K.t('Diese Seite gibt es nicht.') });
    } catch (e) {
      console.error(e);
      return el('div', { class: 'leer', text: K.t('Beim Aufbau ist etwas schiefgegangen.') });
    }
  }

  function zeichnen(behalteScroll) {
    var s = stapel[stapel.length - 1];
    if (!s) { stapel = [{ ansicht: 'start', par: {} }]; s = stapel[0]; }
    var y = window.scrollY;
    // Die Ansichten tieferer Ebenen bleiben erhalten (Eingaben, aufgeklappte Kaesten, Scrollstand)
    var alt = buehne.firstChild;
    if (alt) buehne.removeChild(alt);
    if (behalteScroll || !s.knoten) s.knoten = bauen(s);
    buehne.appendChild(s.knoten);
    if (behalteScroll) window.scrollTo(0, y);
    else if (s.scroll !== undefined) { var sc = s.scroll; window.scrollTo(0, sc); setTimeout(function () { window.scrollTo(0, sc); melden(); }, 0); delete s.scroll; }
    else window.scrollTo(0, 0);
    melden();
  }

  /* ---------- Hilfen fuer Ansichten ---------- */
  App.modul = function (id) {
    return (window.KURS && KURS.module || []).filter(function (m) { return m.id === id; })[0];
  };

  App.lektionStelle = function (id) {
    var mm = window.KURS && KURS.module || [];
    for (var i = 0; i < mm.length; i++) {
      var ls = mm[i].lektionen || [];
      for (var j = 0; j < ls.length; j++) {
        if (ls[j].id === id) {
          var naechste = ls[j + 1] || (mm[i + 1] && mm[i + 1].lektionen && mm[i + 1].lektionen[0]) || null;
          var naechstesModul = ls[j + 1] ? null : (mm[i + 1] || null);
          return { m: mm[i], l: ls[j], i: j, naechste: naechste, naechstesModul: naechstesModul };
        }
      }
    }
    return null;
  };

  /** Glossareintrag zu einem Verweis: genau, als Teilwort, dann ueber Wortstaemme (gebeugte Formen). */
  App.begriffFinden = function (begriff) {
    var such = String(begriff).toLowerCase().trim();
    var gl = window.GLOSSAR || [];
    var endungen = K.en ? /(ies|es|s|ed|ing)$/ : /(en|em|er|es|e|n|s)$/;
    function staemme(s) {
      return s.toLowerCase().replace(/[^a-z0-9äöüß ]/g, ' ').trim().split(/\s+/).map(function (w) {
        return w.replace(endungen, '');
      });
    }
    function stamm(s) { return staemme(s)[0] || ''; }
    var g = gl.filter(function (x) { return x.begriff.toLowerCase() === such; })[0];
    if (!g) g = gl.filter(function (x) {
      var b = x.begriff.toLowerCase();
      return b.indexOf(such) >= 0 || such.indexOf(b) >= 0;
    })[0];
    // Gebeugte Formen („narrativen Fehlschluss“): alle Wortstaemme muessen passen
    if (!g) {
      var alle = staemme(such).join(' ');
      if (alle.length >= 5) g = gl.filter(function (x) { return staemme(x.begriff).join(' ') === alle; })[0];
    }
    if (!g) {
      var st = stamm(such);
      if (st.length >= 5) g = gl.filter(function (x) { return stamm(x.begriff).indexOf(st) === 0 || st.indexOf(stamm(x.begriff)) === 0; })[0];
    }
    return g || null;
  };

  App.begriffZeigen = function (begriff) {
    var g = App.begriffFinden(begriff);
    if (!g) { K.hinweis(K.t('Kein Glossareintrag zu „{0}“.', begriff)); return; }
    var b = el('div');
    b.appendChild(el('h2', { style: 'font-size:22px;margin-bottom:8px', text: g.begriff }));
    b.appendChild(el('div', { class: 'inhalt', style: 'font-size:16px', html: Mark.block(g.text) }));
    if (g.siehe) b.appendChild(el('div', { class: 'still', style: 'margin-top:6px', text: K.t('Siehe auch: ') + [].concat(g.siehe).join(', ') }));
    var zu = el('button', { class: 'knopf haupt breit', style: 'margin-top:16px', text: K.t('Schließen') });
    zu.addEventListener('click', function () { K.blattZu(); melden(); });
    b.appendChild(zu);
    K.blatt(b);
    melden();
  };

  /* ---------- Wischen von links nach rechts: eine Ebene zurueck ---------- */
  var wisch = null;          // laufende Geste
  var unterlage = null;      // die vorherige Ebene, unter der aktuellen
  var auslauf = null;        // laufende Animation nach dem Loslassen
  var VERSATZ = 0.3;         // wie weit die vorherige Ebene links versetzt beginnt (Anteil der Breite)

  /** Elemente, die waagerechte Bewegungen selbst brauchen: Diagramme (Werte ablesen), Regler, breite Tabellen. */
  function eigeneWaagerechte(ziel) {
    if (!ziel || !ziel.closest) return false;
    // Nur was waagerecht selbst etwas tut: Diagramme mit Werteanzeige beim Ziehen, Regler, breite Tabellen
    if (ziel.closest('svg.zieht, input[type="range"]')) return true;
    var t = ziel.closest('.tab-huelle');
    return !!(t && t.scrollWidth > t.clientWidth + 2 && !t.classList.contains('stapel'));
  }

  /** Legt [knoten] (eine Ebene, gescrollt um [scroll]) als feste Unterlage unter die Buehne. */
  function unterlageMit(knoten, scroll) {
    var u = el('div', { class: 'unterlage' });
    var innen = el('div', { class: 'unterlage-buehne' });
    innen.style.transform = 'translateY(' + (-(scroll || 0)) + 'px)';
    innen.appendChild(knoten);
    u.appendChild(innen);
    u.appendChild(el('div', { class: 'unterlage-schatten' }));
    document.body.appendChild(u);
    // Die aktuelle Ebene liegt jetzt ueber der Unterlage und wirft einen Schatten auf sie
    document.body.classList.add('wischt');
    return u;
  }

  /** Die vorherige Ebene als Unterlage, fuer den Weg zurueck. */
  function unterlageAufbauen() {
    var vorher = stapel[stapel.length - 2];
    if (!vorher) return null;
    if (!vorher.knoten) vorher.knoten = bauen(vorher);
    return unterlageMit(vorher.knoten, vorher.scroll);
  }

  function unterlageWeg() {
    buehne.style.transform = '';
    if (unterlage && unterlage.parentNode) unterlage.parentNode.removeChild(unterlage);
    unterlage = null;
    document.body.classList.remove('wischt');
    // Auf der verlassenen Ebene bleibt keine Hervorhebung haengen
    var alt = stapel[stapel.length - 2];
    if (alt && alt.knoten) [].forEach.call(alt.knoten.querySelectorAll('.gedrueckt'), function (x) { x.classList.remove('gedrueckt'); });
    melden();
  }

  /** Ende des Wegs zurueck: Die vorherige Ebene wandert aus der Unterlage zurueck auf die Buehne. */
  function zurueckAbschliessen() {
    buehne.style.transform = '';
    var vorher = stapel[stapel.length - 2];
    var y = vorher ? (vorher.scroll || 0) : 0;
    if (vorher) delete vorher.scroll;
    stapel.pop();
    var alt = buehne.firstChild;
    if (alt) buehne.removeChild(alt);
    if (vorher && vorher.knoten) buehne.appendChild(vorher.knoten);
    window.scrollTo(0, y);
    if (unterlage && unterlage.parentNode) unterlage.parentNode.removeChild(unterlage);
    unterlage = null;
    document.body.classList.remove('wischt');
    melden();
  }

  /* ---------- Uebergaenge ohne Finger (Antippen, Zurueck-Taste) ---------- */
  var DAUER = 320;                          // ms, wie die Blaetter der Depot-App
  var KURVE = 'cubic-bezier(.2,0,0,1)';     // schnell los, sanft zur Ruhe
  var uebergang = null;                     // laufender Uebergang: { ende }

  function bewegungAus() {
    return !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  }

  /** Die Buehne gleitet von [von] nach [nach] (0 = an ihrem Platz, Breite = ganz rechts), dann [fertig]. */
  function gleiten(von, nach, fertig) {
    versetzen(von);
    void buehne.offsetWidth;                // Ausgangslage festschreiben, sonst gibt es keinen Uebergang
    var t = 'transform ' + DAUER + 'ms ' + KURVE;
    buehne.style.transition = t;
    if (unterlage) {
      unterlage.style.transition = t;
      unterlage.lastChild.style.transition = 'opacity ' + DAUER + 'ms ' + KURVE;
    }
    versetzen(nach);
    var zeit = setTimeout(ende, DAUER + 40);
    function ende() {
      if (!uebergang || uebergang.ende !== ende) return;
      clearTimeout(zeit);
      uebergang = null;
      buehne.style.transition = '';
      if (unterlage) { unterlage.style.transition = ''; unterlage.lastChild.style.transition = ''; }
      fertig();
    }
    uebergang = { ende: ende };
  }

  /** Einen laufenden Uebergang sofort an sein Ziel bringen (neue Navigation, Themawechsel). */
  function fertigSofort() { if (uebergang) uebergang.ende(); }

  /** Aktuelle Ebene um dx nach rechts; die Unterlage kommt anteilig von links nach und wird heller. */
  function versetzen(dx) {
    var w = window.innerWidth;
    buehne.style.transform = dx > 0 ? 'translate3d(' + dx + 'px,0,0)' : '';
    if (unterlage) {
      var f = Math.max(0, Math.min(1, dx / w));
      unterlage.style.transform = 'translate3d(' + (-(1 - f) * w * VERSATZ) + 'px,0,0)';
      unterlage.lastChild.style.opacity = String(0.4 * (1 - f));
    }
  }

  /**
   * Weicher Auslauf nach dem Loslassen: beginnt mit der Fingergeschwindigkeit v0 (px/ms) und bremst
   * ohne Nachschwingen ab (kritisch gedaempfte Feder), bis die Seite am Ziel steht.
   */
  function auslaufen(von, nach, v0, fertig) {
    var w = window.innerWidth, om = 20;          // Eigenkreisfrequenz in 1/s
    var A = von - nach, B = v0 * 1000 + om * A, t0 = 0;
    function schritt(jetzt) {
      if (!t0) t0 = jetzt;
      var t = (jetzt - t0) / 1000;
      var x = (A + B * t) * Math.exp(-om * t);
      if (t > 0.04 && Math.abs(x) < 0.6) { auslauf = null; versetzen(nach); fertig(); return; }
      versetzen(Math.max(0, Math.min(w, nach + x)));
      auslauf = requestAnimationFrame(schritt);
    }
    auslauf = requestAnimationFrame(schritt);
  }

  function wischEnde(zurueck, v0) {
    var w = window.innerWidth;
    auslaufen(wisch0, zurueck ? w : 0, v0 || 0, function () {
      if (zurueck) { zurueckAbschliessen(); return; }
      buehne.style.transform = '';
      if (unterlage && unterlage.parentNode) unterlage.parentNode.removeChild(unterlage);
      unterlage = null;
      document.body.classList.remove('wischt');
      melden();
    });
  }
  var wisch0 = 0;            // Stand der Seite beim Loslassen

  function wischAn() {
    document.addEventListener('touchstart', function (ev) {
      wisch = null;
      if (ev.touches.length !== 1 || K.blattOffen || stapel.length < 2 || unterlage || auslauf || uebergang) return;
      if (eigeneWaagerechte(ev.target)) return;
      var p = ev.touches[0];
      wisch = { x0: p.clientX, y0: p.clientY, art: 'offen', dx: 0, vx: 0, tl: performance.now(), xl: p.clientX };
    }, { passive: true });

    document.addEventListener('touchmove', function (ev) {
      if (!wisch || ev.touches.length !== 1) return;
      var p = ev.touches[0];
      var dx = p.clientX - wisch.x0, dy = p.clientY - wisch.y0;
      if (wisch.art === 'offen') {
        if (Math.abs(dy) > 8 && Math.abs(dy) >= Math.abs(dx)) { wisch = null; return; }
        // Von rechts nach links tut nichts
        if (dx < -8) { wisch = null; return; }
        if (dx > 8 && dx > Math.abs(dy) * 1.2) {
          if (!ev.cancelable) { wisch = null; return; }
          wisch.art = 'zurueck';
          wisch.x0 = p.clientX - 1;      // ab hier folgt die Seite dem Finger ohne Sprung
          unterlage = unterlageAufbauen();
          if (!unterlage) { wisch = null; return; }
          dx = 1;
        } else return;
      }
      ev.preventDefault();
      var jetzt = performance.now();
      if (jetzt > wisch.tl) {
        // geglaettete Geschwindigkeit in px/ms
        var v = (p.clientX - wisch.xl) / (jetzt - wisch.tl);
        wisch.vx = wisch.vx * 0.4 + v * 0.6;
        wisch.tl = jetzt; wisch.xl = p.clientX;
      }
      wisch.dx = Math.max(0, dx);
      versetzen(wisch.dx);
    }, { passive: false });

    function loslassen() {
      if (!wisch || wisch.art !== 'zurueck') { wisch = null; return; }
      var w = window.innerWidth;
      // Liegt die letzte Bewegung lange zurueck, zaehlt die Geschwindigkeit nicht mehr
      var v = performance.now() - wisch.tl > 80 ? 0 : wisch.vx;
      var zurueck = wisch.dx > w * 0.25 || (v > 0.35 && wisch.dx > 24);
      wisch0 = wisch.dx;
      wisch = null;
      wischEnde(zurueck, zurueck ? Math.max(v, 0) : Math.min(v, 0));
    }
    document.addEventListener('touchend', loslassen, { passive: true });
    document.addEventListener('touchcancel', function () {
      if (wisch && wisch.art === 'zurueck') { wisch0 = wisch.dx; wisch = null; wischEnde(false, 0); } else wisch = null;
    }, { passive: true });
  }

  /* ---------- Rueckmeldung beim Antippen ---------- */
  // Antippbares hellt sich auf, wie in der Depot-App: nach kurzem Halten sofort, bei schnellem Tippen
  // fuer einen Augenblick nach dem Loslassen. Scrollen oder Wischen nimmt die Hervorhebung zurueck.
  var TIPPBAR = 'button, a, summary, [role="button"], .zeile, .treffer, .karte.klick, .marke.klick';
  function tippAn() {
    var ziel = null, an = false, x0 = 0, y0 = 0, seit = 0, verzug = null;
    function zeigen() { if (ziel) { ziel.classList.add('gedrueckt'); an = true; seit = performance.now(); } }
    // Liegt die Zeile nach dem Antippen auf der Unterlage (die neue Ebene gleitet gerade darueber),
    // bleibt sie hervorgehoben, bis der Uebergang fertig ist (unterlageWeg nimmt es dann zurueck)
    function aus(z, nach) { setTimeout(function () { if (z.closest && z.closest('.unterlage')) return; z.classList.remove('gedrueckt'); }, nach); }
    document.addEventListener('touchstart', function (ev) {
      clearTimeout(verzug);
      if (ziel && an) ziel.classList.remove('gedrueckt');
      ziel = null; an = false;
      if (ev.touches.length !== 1) return;
      var t = ev.target.closest && ev.target.closest(TIPPBAR);
      if (!t || t.disabled || t.getAttribute('aria-disabled') === 'true') return;
      ziel = t;
      x0 = ev.touches[0].clientX; y0 = ev.touches[0].clientY;
      verzug = setTimeout(zeigen, 70);
    }, { passive: true });
    document.addEventListener('touchmove', function (ev) {
      if (!ziel) return;
      var p = ev.touches[0];
      if (Math.abs(p.clientX - x0) > 10 || Math.abs(p.clientY - y0) > 10) {
        clearTimeout(verzug);
        if (an) ziel.classList.remove('gedrueckt');
        ziel = null; an = false;
      }
    }, { passive: true });
    document.addEventListener('touchend', function () {
      if (!ziel) return;
      clearTimeout(verzug);
      var z = ziel;
      if (!an) { zeigen(); aus(z, 140); }
      else aus(z, Math.max(0, 110 - (performance.now() - seit)));
      ziel = null; an = false;
    }, { passive: true });
    document.addEventListener('touchcancel', function () {
      clearTimeout(verzug);
      if (ziel && an) ziel.classList.remove('gedrueckt');
      ziel = null; an = false;
    }, { passive: true });
  }

  /* ---------- Blatt nach unten wegwischen ---------- */
  function blattWischAn() {
    var b = document.getElementById('blatt'), inhalt = document.getElementById('blattInhalt');
    var g = null;
    b.addEventListener('touchstart', function (ev) {
      g = null;
      if (ev.touches.length !== 1 || inhalt.scrollTop > 0) return;
      g = { y0: ev.touches[0].clientY, x0: ev.touches[0].clientX, dy: 0, art: 'offen', tl: Date.now(), yl: ev.touches[0].clientY, vy: 0 };
    }, { passive: true });
    b.addEventListener('touchmove', function (ev) {
      if (!g) return;
      var p = ev.touches[0], dy = p.clientY - g.y0, dx = p.clientX - g.x0;
      if (g.art === 'offen') {
        if (Math.abs(dx) > 8 && Math.abs(dx) > Math.abs(dy)) { g = null; return; }
        if (dy < -8) { g = null; return; }
        if (dy > 8 && inhalt.scrollTop <= 0 && ev.cancelable) g.art = 'zu'; else return;
      }
      ev.preventDefault();
      var jetzt = Date.now();
      if (jetzt > g.tl) { g.vy = (p.clientY - g.yl) / (jetzt - g.tl); g.tl = jetzt; g.yl = p.clientY; }
      g.dy = Math.max(0, dy);
      inhalt.style.animation = 'none';
      inhalt.style.transition = 'none';
      inhalt.style.transform = 'translateY(' + g.dy + 'px)';
    }, { passive: false });
    function los() {
      if (!g || g.art !== 'zu') { g = null; return; }
      var zu = g.dy > inhalt.offsetHeight * 0.25 || (g.vy > 0.5 && g.dy > 24);
      g = null;
      inhalt.style.transition = 'transform .2s ease-out';
      inhalt.style.transform = zu ? 'translateY(100%)' : '';
      setTimeout(function () {
        inhalt.style.transition = ''; inhalt.style.transform = ''; inhalt.style.animation = '';
        if (zu) { K.blattZu(); melden(); }
      }, 210);
    }
    b.addEventListener('touchend', los, { passive: true });
    b.addEventListener('touchcancel', los, { passive: true });
  }

  /* ---------- Start ---------- */
  function daten(datei, ziel) {
    return K.hole(K.daten(datei)).then(function (d) { if (d) window[ziel] = d; return d; });
  }

  function los() {
    buehne = document.getElementById('buehne');

    document.documentElement.setAttribute('data-theme', par0.theme === 'hell' ? 'hell' : 'dunkel');
    document.documentElement.setAttribute('lang', K.lang);
    document.getElementById('suchKnopf').setAttribute('aria-label', K.t('Suchen'));
    var laedt = buehne.querySelector('.leer');
    if (laedt) laedt.textContent = K.t('wird geladen …');

    document.getElementById('suchKnopf').addEventListener('click', function () { App.gehe('suche'); });
    document.getElementById('blatt').addEventListener('click', function (ev) {
      if (ev.target.id === 'blatt') { K.blattZu(); melden(); }
    });
    document.body.addEventListener('click', function (ev) {
      var a = ev.target.closest && ev.target.closest('a.gl');
      if (a) { ev.preventDefault(); App.begriffZeigen(a.getAttribute('data-begriff')); }
    });
    wischAn();
    blattWischAn();
    tippAn();

    var laden = [
      daten('kurs.json', 'KURS'),
      daten('glossar.json', 'GLOSSAR'),
      daten('formeln.json', 'FORMELN'),
      daten('chronik.json', 'CHRONIK'),
      daten('reihen.json', 'REIHEN'),
      daten('merksaetze.json', 'MERKSAETZE'),
      daten('fehler.json', 'FEHLER'),
      daten('suchindex.json', 'SUCHINDEX'),
      // Angepasste Lektionen je Region (fuer beide Sprachen eine Datei)
      K.region ? K.hole('data/regional.json').then(function (d) { if (d) window.REGIONAL = d; return d; }) : Promise.resolve(null)
    ];

    Promise.all(laden).then(function () {
      if (!window.KURS) window.KURS = { module: [] };
      K.regionAnwenden();
      stapel = [{ ansicht: 'start', par: {} }];
      zeichnen();
    }).catch(function (e) {
      console.error(e);
      buehne.innerHTML = '';
      buehne.appendChild(el('div', { class: 'leer', text: K.t('Die Inhalte konnten nicht geladen werden.') }));
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', los);
  else los();
})();
