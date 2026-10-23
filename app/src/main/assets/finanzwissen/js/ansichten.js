/* ============================================================
   ansichten.js - die Bildschirme des Finanzwissen-Bereichs.
   Zum Lesen und Nachschlagen: Themen, Lektionen, Nachschlagewerk,
   Rechner, Suche. Kein Quiz, kein Fortschritt, keine Lernpfade.
   ============================================================ */
(function () {
  'use strict';
  var el = K.el;
  var A = {};
  window.Ansichten = A;

  var CHEVRON = '<svg viewBox="0 0 24 24"><path d="M9 6 L15 12 L9 18"/></svg>';

  function titelZeile(titel, unter) {
    var d = el('div');
    d.appendChild(el('h1', { class: 'seiten-titel', text: titel }));
    if (unter) d.appendChild(el('p', { class: 'seiten-unter', html: Mark.inline(unter) }));
    return d;
  }

  function abschnitt(text, klein) { return el('div', { class: 'abschnitt-titel' + (klein ? ' klein' : ''), text: text }); }

  /** Zeile wie in den Listen der Depot-App: Symbol links, Titel und Untertitel, rechts Text und Chevron. */
  function zeile(o) {
    var z = el('button', { class: 'zeile' });
    if (o.links) z.appendChild(o.links);
    var inn = el('div', { class: 'dehnen' });
    inn.appendChild(el('div', { class: 'z-titel', text: o.titel }));
    if (o.unter) inn.appendChild(el('div', { class: 'z-unter', text: o.unter }));
    z.appendChild(inn);
    if (o.rechts) z.appendChild(el('div', { class: 'z-rechts', text: o.rechts }));
    z.appendChild(el('div', { class: 'chev', html: CHEVRON }));
    if (o.auf) z.addEventListener('click', o.auf);
    return z;
  }

  var MONATE = K.en
    ? ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']
    : ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];
  /** "2026-09" -> "September 2026"; anderes bleibt, wie es ist. */
  function standText(s) {
    var m = String(s).match(/^(\d{4})-(\d{2})$/);
    return m ? MONATE[parseInt(m[2], 10) - 1] + ' ' + m[1] : (s || MONATE[8] + ' 2026');
  }

  /** Stufe eines Themas oder einer Lektion: Der Datenwert bleibt deutsch ("Grundlagen", "Aufbau", "Profi"), angezeigt wird er uebersetzt. */
  function stufeText(stufe) { return K.t(stufe); }

  function punkt(text, klein) { return el('div', { class: 'punkt' + (klein ? ' klein' : ''), text: String(text) }); }

  /* Titel und Texte sind die deutschen Schluessel; die Anzeige geht ueber K.t. */
  var NACHSCHLAGEN = [
    { id: 'glossar', titel: 'Glossar', text: 'Fachbegriffe von A bis Z' },
    { id: 'formeln', titel: 'Formelsammlung', text: 'Formeln nach Themen, mit Beispielen' },
    { id: 'chronik', titel: 'Chronik der Krisen', text: 'Blasen und Crashs seit 1637' },
    { id: 'zahlenwerk', titel: 'Zahlenwerk', text: 'Renditen, Inflation und Zinsen als Datenreihen' },
    { id: 'merksaetze', titel: 'Merksätze', text: 'Kurze Regeln aus den Lektionen' },
    { id: 'fehler', titel: 'Fehlerkatalog', text: 'Häufige Anlagefehler und was besser ist' }
  ];

  /* ============================================================
     START: Themen, Nachschlagen, Rechner
     ============================================================ */
  A.start = function () {
    var d = el('div');
    var module = (window.KURS && KURS.module) || [];
    var lektionen = 0;
    module.forEach(function (m) { lektionen += (m.lektionen || []).length; });

    d.appendChild(titelZeile(K.t('Finanzwissen'), K.t('{0} Themen mit {1} Lektionen, dazu Nachschlagewerk und Rechner.', module.length, lektionen)));

    d.appendChild(abschnitt(K.t('Themen')));
    module.forEach(function (m) {
      d.appendChild(zeile({
        links: punkt(m.nr),
        titel: m.titel,
        unter: K.t('{0} Lektionen', (m.lektionen || []).length) + (m.stufe ? ' · ' + stufeText(m.stufe) : ''),
        auf: function () { App.gehe('modul', { id: m.id }); }
      }));
    });

    var reg = K.regional();
    if (reg && reg.lektionen) {
      var n = Object.keys(reg.lektionen).length;
      d.appendChild(abschnitt(K.t('Deine Region')));
      d.appendChild(zeile({
        titel: K.t('Für die USA angepasst'),
        unter: K.t('{0} Lektionen, in denen deutsche Regeln in den USA nicht gelten', n),
        auf: function () { App.gehe('regional'); }
      }));
    }

    d.appendChild(abschnitt(K.t('Nachschlagen')));
    NACHSCHLAGEN.forEach(function (x) {
      d.appendChild(zeile({ titel: K.t(x.titel), unter: K.t(x.text), auf: function () { App.gehe(x.id); } }));
    });

    d.appendChild(abschnitt(K.t('Rechner')));
    var anzahl = (window.Werkzeuge && Werkzeuge.reihenfolge || []).length;
    d.appendChild(zeile({
      titel: K.t('Alle Rechner'),
      unter: K.t('{0} Rechner, vom Sparplan bis zur Optionsbewertung', anzahl),
      auf: function () { App.gehe('werkzeuge'); }
    }));

    d.appendChild(abschnitt(K.t('Weiterlernen')));
    d.appendChild(zeile({
      titel: K.t('Kanäle und Podcasts'),
      unter: K.t('Freies Finanzwissen auf YouTube und in Podcasts'),
      auf: function () { App.gehe('kanaele'); }
    }));

    d.appendChild(el('div', { class: 'fusszeile', text: K.t('Inhalte Stand {0} · Bildung, keine Anlageberatung.', standText((window.KURS && KURS.stand) || '')) }));
    return d;
  };

  /* ============================================================
     REGION: welche Lektionen fuer die Region des Nutzers angepasst sind und was daran anders ist
     ============================================================ */
  A.regional = function () {
    var d = el('div');
    var reg = K.regional();
    d.appendChild(titelZeile(K.t('Für die USA angepasst'), K.t('Die Depot-App steht auf den USA. Wo das Finanzwissen deutsche Gesetze, Steuern oder Einrichtungen beschreibt, zeigt es diese Lektionen in einer Fassung für die USA. Alles andere gilt überall und bleibt unverändert. Jede angepasste Lektion sagt das oben und zeigt auf Wunsch die deutsche Fassung.')));
    if (!reg || !reg.lektionen) { d.appendChild(el('div', { class: 'leer', text: K.t('Nicht verfügbar.') })); return d; }
    (KURS.module || []).forEach(function (m) {
      var ls = (m.lektionen || []).filter(function (l) { return l.angepasst; });
      if (!ls.length) return;
      d.appendChild(abschnitt(m.titel, true));
      ls.forEach(function (l) {
        var a = K.angepasst(l.id) || {};
        d.appendChild(zeile({ titel: l.titel, unter: a.hinweis || '', auf: function () { App.gehe('lektion', { id: l.id }); } }));
      });
    });
    if (reg.stand) d.appendChild(el('div', { class: 'fusszeile', text: K.t('Inhalte Stand {0} · Bildung, keine Anlageberatung.', standText(reg.stand)) }));
    return d;
  };

  /* ============================================================
     KANAELE: feste, gepruefte Liste mit Links (seit 3.0). Die Seite laedt selbst nichts von YouTube;
     erst ein Tipp oeffnet YouTube oder die Webseite (in der App im Browser bzw. in der YouTube-App).
     ============================================================ */
  A.kanaele = function () {
    var d = el('div');
    d.appendChild(titelZeile(K.t('Kanäle und Podcasts'), K.t('Freies Finanzwissen auf YouTube und in Podcasts')));
    d.appendChild(el('p', { class: 'still', style: 'margin:0 0 14px', text: K.t('Ausgewählt nach Fachlichkeit, freiem Zugang und offen gelegten Interessen; die App erhält dafür keine Vergütung. Ein Tipp öffnet YouTube oder die Webseite, vorher lädt die App nichts von dort. Externe Inhalte, keine Anlageberatung; Videos können Werbung und Affiliate-Links enthalten.') }));
    var liste = el('div', { class: 'leer', text: K.t('wird geladen …') });
    d.appendChild(liste);
    K.hole('data/kanaele.json').then(function (j) {
      liste.remove();
      if (!j || !j.kanaele) { d.appendChild(el('div', { class: 'leer', text: K.t('Nicht verfügbar.') })); return; }
      var reihenfolge = K.en ? ['en', 'de'] : ['de', 'en'];
      reihenfolge.forEach(function (sp) {
        var teil = j.kanaele.filter(function (k) { return k.sprache === sp; });
        if (!teil.length) return;
        d.appendChild(abschnitt(K.t(sp === 'de' ? 'Deutschsprachig' : 'Englischsprachig')));
        teil.forEach(function (k) {
          var c = el('div', { class: 'karte' });
          c.appendChild(el('div', { class: 'karte-titel', text: k.name }));
          c.appendChild(el('p', { style: 'margin:6px 0 0;font-size:15px;line-height:1.5', text: K.en ? k.text_en : k.text_de }));
          var h = K.en ? k.hinweis_en : k.hinweis_de;
          if (h) c.appendChild(el('p', { class: 'still', style: 'margin:6px 0 0', text: K.t('Hinweis: {0}', h) }));
          var knoepfe = el('div', { style: 'display:flex;gap:8px;flex-wrap:wrap;margin-top:12px' });
          [[k.youtube, 'YouTube'], [k.web, 'Webseite']].forEach(function (x) {
            if (!x[0]) return;
            var a = el('a', { class: 'knopf klein', text: K.t(x[1]) });
            a.setAttribute('href', x[0]);
            a.setAttribute('target', '_blank');
            a.setAttribute('rel', 'noopener noreferrer');
            knoepfe.appendChild(a);
          });
          c.appendChild(knoepfe);
          d.appendChild(c);
        });
      });
      d.appendChild(el('div', { class: 'fusszeile', text: K.t('Auswahl Stand {0}', standText(j.stand)) }));
    });
    return d;
  };

  /* ============================================================
     THEMA (Modul) mit Lektionsliste
     ============================================================ */
  A.modul = function (par) {
    var m = App.modul(par.id);
    var d = el('div');
    if (!m) { d.appendChild(el('div', { class: 'leer', text: K.t('Thema nicht gefunden.') })); return d; }
    d.appendChild(el('div', { class: 'still', style: 'margin-top:4px', text: K.t('Thema {0}', m.nr) + (m.stufe ? ' · ' + stufeText(m.stufe) : '') }));
    d.appendChild(titelZeile(m.titel, m.unter));
    if (m.beschreibung) d.appendChild(el('div', { class: 'inhalt', style: 'font-size:16px;color:var(--text-leise)', html: Mark.block(m.beschreibung) }));

    d.appendChild(abschnitt(K.t('Lektionen')));
    (m.lektionen || []).forEach(function (l, i) {
      d.appendChild(zeile({
        links: punkt(i + 1, true),
        titel: l.titel,
        unter: K.t('{0} Min.', l.minuten || 8) + (l.stufe && l.stufe !== m.stufe ? ' · ' + stufeText(l.stufe) : '') + (l.angepasst ? ' · ' + K.t('für die USA angepasst') : ''),
        auf: function () { App.gehe('lektion', { id: l.id }); }
      }));
    });

    if (m.buecher && m.buecher.length) {
      d.appendChild(abschnitt(K.t('Dazu lesen'), true));
      var bk = el('div', { class: 'karte', style: 'padding:4px 16px' });
      m.buecher.forEach(function (x) {
        var z = el('div', { class: 'buch-zeile' });
        z.appendChild(el('div', { class: 'buch-rueck' }));
        var inn = el('div');
        inn.appendChild(el('b', { style: 'font-size:15px', text: x.titel }));
        inn.appendChild(el('div', { class: 'still', text: x.autor + (x.warum ? ' · ' + x.warum : '') }));
        z.appendChild(inn);
        bk.appendChild(z);
      });
      d.appendChild(bk);
    }
    return d;
  };

  /* ============================================================
     LEKTION: Text, Kaesten, Diagramme, das Wichtigste, Quellen, weiter
     ============================================================ */
  A.lektion = function (par) {
    var d = el('div');
    var stelle = App.lektionStelle(par.id);
    d.appendChild(el('div', { class: 'leer', text: K.t('Lektion wird geladen …') }));
    K.hole(K.lektionPfad(par.id)).then(function (L) {
      d.innerHTML = '';
      if (!L) {
        d.appendChild(el('div', { class: 'leer', text: K.t('Diese Lektion ist in dieser Ausgabe nicht enthalten.') }));
        return;
      }
      var kopf = el('div', { class: 'lekt-kopf' });
      if (stelle) kopf.appendChild(el('div', { class: 'brotkrumen', text: stelle.m.titel + ' · ' + K.t('Lektion {0} von {1}', stelle.i + 1, (stelle.m.lektionen || []).length) }));
      kopf.appendChild(el('h1', { text: L.titel }));
      if (L.kurz) kopf.appendChild(el('p', { class: 'seiten-unter', style: 'margin-bottom:0', html: Mark.inline(L.kurz) }));
      var meta = el('div', { class: 'lekt-meta' });
      if (L.minuten) meta.appendChild(el('span', { class: 'marke', text: K.t('{0} Min. Lesezeit', L.minuten) }));
      // Die Farbe richtet sich nach dem deutschen Datenwert, der Text nach der Sprache
      if (L.stufe) meta.appendChild(el('span', { class: 'marke ' + (L.stufe === 'Grundlagen' ? 'gruen' : L.stufe === 'Profi' ? 'rot' : 'blau'), text: stufeText(L.stufe) }));
      (L.marken || []).forEach(function (t) { meta.appendChild(el('span', { class: 'marke', text: t })); });
      kopf.appendChild(meta);
      d.appendChild(kopf);

      // Angepasste Fassung fuer die Region: offen sagen, was anders ist, und die deutsche Fassung anbieten
      var ang = K.angepasst(par.id);
      if (ang) {
        var orig = K.original(par.id);
        var hk = el('div', { class: 'kasten info' });
        hk.appendChild(el('div', { class: 'kasten-titel', text: orig ? K.t('Deutsche Fassung') : K.t('Für die USA angepasst') }));
        hk.appendChild(el('p', { html: Mark.inline(orig ? K.t('Für die USA gibt es eine angepasste Fassung dieser Lektion.') : (ang.hinweis || K.t('Diese Lektion beschreibt die Regeln in den USA statt der deutschen.'))) }));
        var kn = el('button', { class: 'knopf klein', text: orig ? K.t('Fassung für die USA lesen') : K.t('Deutsche Fassung lesen') });
        kn.addEventListener('click', function () { K.originalSetzen(par.id, !orig); App.gehe('lektion', { id: par.id }, true); });
        hk.appendChild(kn);
        d.appendChild(hk);
      }

      d.appendChild(Bloecke.bauen(L.bloecke));

      if (L.mitnehmen && L.mitnehmen.length) {
        var mn = el('div', { class: 'kasten merk' });
        mn.appendChild(el('div', { class: 'kasten-titel', text: K.t('Das Wichtigste') }));
        var ul = el('ul');
        L.mitnehmen.forEach(function (x) { ul.appendChild(el('li', { html: Mark.inline(x) })); });
        mn.appendChild(ul);
        d.appendChild(mn);
      }

      if (L.begriffe && L.begriffe.length) {
        var bg = el('div', { class: 'karte' });
        bg.appendChild(el('div', { class: 'kasten-titel', style: 'font-weight:700;font-size:12.5px;letter-spacing:.05em;text-transform:uppercase;color:var(--text-leise);margin-bottom:4px', text: K.t('Begriffe') }));
        L.begriffe.forEach(function (x) {
          var z = el('div', { class: 'glossar-eintrag' });
          z.appendChild(el('b', { text: x.begriff }));
          z.appendChild(el('p', { html: Mark.inline(x.text) }));
          bg.appendChild(z);
        });
        d.appendChild(bg);
      }

      if (L.quellen && L.quellen.length) {
        var q = el('details', { class: 'karte' });
        var s = el('summary', { text: K.t('Quellen') });
        q.appendChild(s);
        var ul2 = el('ul', { style: 'margin:10px 0 0;padding-left:18px;color:var(--text-leise);font-size:13.5px;line-height:1.6' });
        L.quellen.forEach(function (x) { ul2.appendChild(el('li', { text: x })); });
        q.appendChild(ul2);
        d.appendChild(q);
      }

      // Weiter zur naechsten Lektion - ohne Abhaken, nur ein Weg weiter
      if (stelle && stelle.naechste) {
        d.appendChild(abschnitt(K.t('Weiter'), true));
        d.appendChild(zeile({
          titel: stelle.naechste.titel,
          unter: stelle.naechstesModul ? stelle.naechstesModul.titel : K.t('{0} Min.', stelle.naechste.minuten || 8),
          auf: function () { App.weiter(stelle.naechste.id); }
        }));
      }
      App.scrollHoch();
    });
    return d;
  };

  /* ============================================================
     RECHNER
     ============================================================ */
  A.werkzeuge = function () {
    var d = el('div');
    d.appendChild(titelZeile(K.t('Rechner'), K.t('Alle Ergebnisse werden auf dem Gerät berechnet.')));
    var nachKat = {}, reihenfolge = [];
    Werkzeuge.reihenfolge.forEach(function (id) {
      var t = Werkzeuge.liste[id];
      // Kategorie, Titel und Untertitel uebersetzt die Rechnerdatei selbst
      var kat = t.kategorie || K.t('Weiteres');
      if (!nachKat[kat]) { nachKat[kat] = []; reihenfolge.push(kat); }
      nachKat[kat].push(t);
    });
    reihenfolge.forEach(function (kat) {
      d.appendChild(abschnitt(kat, true));
      nachKat[kat].forEach(function (t) {
        d.appendChild(zeile({ titel: t.titel, unter: t.unter, auf: function () { App.gehe('werkzeug', { id: t.id }); } }));
      });
    });
    return d;
  };

  A.werkzeug = function (par) {
    var t = Werkzeuge.liste[par.id];
    var d = el('div');
    if (!t) { d.appendChild(el('div', { class: 'leer', text: K.t('Rechner nicht gefunden.') })); return d; }
    d.appendChild(titelZeile(t.titel, t.unter));
    var w = el('div', { class: 'werk' });
    d.appendChild(w);
    try { t.bauen(w, (K.st.werkzeugStand && K.st.werkzeugStand[t.id]) || {}); }
    catch (e) { console.warn(e); w.appendChild(el('div', { class: 'still', text: K.t('Der Rechner konnte nicht aufgebaut werden.') })); }
    if (t.hinweis) {
      var h = el('div', { class: 'kasten info' });
      h.appendChild(el('div', { class: 'kasten-titel', text: K.t('Einordnung') }));
      h.appendChild(el('div', { html: Mark.block(t.hinweis) }));
      d.appendChild(h);
    }
    return d;
  };

  /* ============================================================
     NACHSCHLAGEN
     ============================================================ */
  A.glossar = function () {
    var d = el('div');
    var alle = window.GLOSSAR || [];
    // Das englische Glossar steht in der Reihenfolge der deutschen Stichwoerter: fuer die Liste von A bis Z neu sortieren
    if (K.en) {
      var vergleich;
      try { vergleich = new Intl.Collator('en', { sensitivity: 'base', ignorePunctuation: true }).compare; }
      catch (e) { vergleich = function (a, b) { return a.localeCompare(b); }; }
      alle = alle.slice().sort(function (a, b) { return vergleich(String(a.begriff), String(b.begriff)); });
    }
    d.appendChild(titelZeile(K.t('Glossar'), K.t('{0} Begriffe', alle.length)));
    var such = el('input', { class: 'suchfeld', type: 'search', placeholder: K.t('Begriff suchen …') });
    d.appendChild(such);
    var box = el('div');
    d.appendChild(box);
    function zeigen() {
      var q = such.value.trim().toLowerCase();
      box.innerHTML = '';
      var g = alle.filter(function (x) {
        return !q || x.begriff.toLowerCase().indexOf(q) >= 0 || (x.text || '').toLowerCase().indexOf(q) >= 0;
      });
      if (!g.length) { box.appendChild(el('div', { class: 'leer', text: K.t('Kein Eintrag gefunden.') })); return; }
      g.slice(0, 400).forEach(function (x) {
        var z = el('div', { class: 'glossar-eintrag' });
        z.appendChild(el('b', { text: x.begriff }));
        z.appendChild(el('p', { html: Mark.inline(x.text) }));
        if (x.siehe) z.appendChild(el('div', { class: 'still', style: 'margin-top:4px', text: K.t('Siehe auch: ') + [].concat(x.siehe).join(', ') }));
        box.appendChild(z);
      });
      if (g.length > 400) box.appendChild(el('div', { class: 'still', style: 'padding:10px 0', text: K.t('Weitere {0} Einträge – bitte genauer suchen.', g.length - 400) }));
    }
    such.addEventListener('input', zeigen);
    zeigen();
    return d;
  };

  A.formeln = function () {
    var d = el('div');
    d.appendChild(titelZeile(K.t('Formelsammlung'), K.t('Nach Themen sortiert, jede Formel mit einer kurzen Erklärung.')));
    var nach = {}, reihenfolge = [];
    (window.FORMELN || []).forEach(function (f) {
      if (!nach[f.kategorie]) { nach[f.kategorie] = []; reihenfolge.push(f.kategorie); }
      nach[f.kategorie].push(f);
    });
    reihenfolge.forEach(function (kat) {
      d.appendChild(abschnitt(kat, true));
      nach[kat].forEach(function (f) {
        var k = el('div', { class: 'formel' });
        k.appendChild(el('div', { class: 'f-name', text: f.name }));
        k.appendChild(el('div', { class: 'f-haupt', text: f.formel }));
        if (f.erklaerung) k.appendChild(el('div', { class: 'f-erk', html: Mark.inline(f.erklaerung) }));
        if (f.variablen) k.appendChild(el('div', { class: 'f-var', html: f.variablen.map(function (v) { return '<b>' + K.htmlEsc(v[0]) + '</b> ' + K.htmlEsc(v[1]); }).join(' &nbsp;·&nbsp; ') }));
        d.appendChild(k);
      });
    });
    return d;
  };

  A.chronik = function () {
    var d = el('div');
    d.appendChild(titelZeile(K.t('Chronik der Krisen'), K.t('Krisen und Blasen seit 1637. Antippen zeigt die ausführliche Fassung.')));
    var ev = (window.CHRONIK || []);
    var dd = ev.filter(function (x) { return typeof x.rueckgang === 'number'; });
    if (dd.length) {
      d.appendChild(Viz.zeichne({
        art: 'balken', titel: K.t('Maximaler Rückgang der großen Krisen'),
        kategorien: dd.map(function (x) { return String(x.jahr); }),
        serien: [{ name: K.t('Rückgang'), werte: dd.map(function (x) { return -Math.abs(x.rueckgang); }) }],
        yEinheit: ' %', diverging: true, hoehe: 200, langeLabels: true,
        unter: K.t('Rückgang vom Höchststand bis zum Tiefpunkt, je Krise im jeweils betroffenen Markt.')
      }));
    }
    ev.forEach(function (x) {
      d.appendChild(zeile({
        links: el('div', { style: 'width:52px;flex:none;font-size:15px;font-weight:700;font-variant-numeric:tabular-nums', text: String(x.jahr) }),
        titel: x.titel,
        unter: x.text || '',
        rechts: typeof x.rueckgang === 'number' ? '−' + K.pro(Math.abs(x.rueckgang), 0) : null,
        auf: function () {
          var b = el('div');
          b.appendChild(el('div', { class: 'still', text: String(x.jahr) }));
          b.appendChild(el('h2', { style: 'font-size:22px;margin:2px 0 10px', text: x.titel }));
          b.appendChild(el('div', { class: 'inhalt', style: 'font-size:16px', html: Mark.block(x.lang || x.text || '') }));
          if (x.lehre) {
            var kk = el('div', { class: 'kasten merk' });
            kk.appendChild(el('div', { class: 'kasten-titel', text: K.t('Die Lehre') }));
            kk.appendChild(el('div', { html: Mark.inline(x.lehre) }));
            b.appendChild(kk);
          }
          var zu = el('button', { class: 'knopf haupt breit', text: K.t('Schließen') });
          zu.addEventListener('click', K.blattZu);
          b.appendChild(zu);
          K.blatt(b);
        }
      }));
    });
    return d;
  };

  A.zahlenwerk = function () {
    var d = el('div');
    d.appendChild(titelZeile(K.t('Zahlenwerk'), K.t('Die Datenreihen aus den Lektionen. Antippen oder waagerecht wischen zeigt die Werte.')));
    (window.REIHEN || []).forEach(function (r) {
      var spez = {};
      for (var k in (r.viz || {})) spez[k] = r.viz[k];
      spez.titel = r.titel;
      spez.unter = r.unter;
      d.appendChild(Viz.zeichne(spez));
      if (r.quelle) d.appendChild(el('div', { class: 'still', style: 'margin:-10px 0 20px;text-align:right', text: K.t('Quelle: {0}', r.quelle) }));
    });
    return d;
  };

  A.merksaetze = function () {
    var d = el('div');
    d.appendChild(titelZeile(K.t('Merksätze'), K.t('Die wichtigsten Regeln aus den Lektionen.')));
    (window.MERKSAETZE || []).forEach(function (m) {
      var k = el('div', { class: 'karte' });
      k.appendChild(el('div', { style: 'font-size:18px;font-weight:600;line-height:1.45', html: Mark.inline(m.satz) }));
      if (m.warum) k.appendChild(el('div', { class: 'still', style: 'margin-top:8px;font-size:14px', html: Mark.inline(m.warum) }));
      if (m.von) k.appendChild(el('div', { class: 'still', style: 'margin-top:6px', text: K.t('— {0}', m.von) }));
      d.appendChild(k);
    });
    return d;
  };

  A.fehler = function () {
    var d = el('div');
    d.appendChild(titelZeile(K.t('Fehlerkatalog'), K.t('Häufige Fehler bei Geldanlage und Finanzplanung, jeweils mit dem besseren Vorgehen.')));
    (window.FEHLER || []).forEach(function (f) {
      var k = el('div', { class: 'karte' });
      k.appendChild(el('div', { class: 'karte-titel', text: f.titel }));
      k.appendChild(el('div', { class: 'inhalt', style: 'font-size:15.5px;margin-top:6px', html: Mark.block(f.text) }));
      if (f.besser) {
        var b = el('div', { class: 'kasten tipp', style: 'margin-top:10px;margin-bottom:0;background:var(--flaeche2)' });
        b.appendChild(el('div', { class: 'kasten-titel', text: K.t('Besser') }));
        b.appendChild(el('div', { html: Mark.inline(f.besser) }));
        k.appendChild(b);
      }
      d.appendChild(k);
    });
    return d;
  };

  /* ============================================================
     SUCHE
     ============================================================ */
  A.suche = function () {
    var d = el('div');
    d.appendChild(titelZeile(K.t('Suche'), ''));
    var inp = el('input', { class: 'suchfeld', type: 'search', placeholder: K.t('Begriff, Thema, Rechner …'), autofocus: 'autofocus' });
    d.appendChild(inp);
    var box = el('div');
    d.appendChild(box);
    // Anriss fuer die Trefferliste: an einer Wortgrenze enden, gekuerzt mit Auslassungspunkt
    function anriss(s, n) {
      s = String(s || '');
      if (s.length <= n) return s;
      var t = s.slice(0, n), i = t.lastIndexOf(' ');
      return (i > n * 0.6 ? t.slice(0, i) : t).replace(/[\s,;:·(]+$/, '') + '…';
    }
    function suchen() {
      var q = inp.value.trim().toLowerCase();
      box.innerHTML = '';
      if (q.length < 2) { box.appendChild(el('div', { class: 'leer', text: K.t('Mindestens zwei Zeichen.') })); return; }
      var treffer = [];
      (window.SUCHINDEX || []).forEach(function (x) {
        var pkt = 0;
        if (x.t.toLowerCase().indexOf(q) >= 0) pkt += 10;
        if ((x.s || '').toLowerCase().indexOf(q) >= 0) pkt += 4;
        if ((x.w || '').toLowerCase().indexOf(q) >= 0) pkt += 1;
        if (pkt) treffer.push({ x: x, pkt: pkt });
      });
      (window.GLOSSAR || []).forEach(function (gl) {
        if (gl.begriff.toLowerCase().indexOf(q) >= 0) treffer.push({ x: { t: gl.begriff, s: gl.text, art: 'glossar' }, pkt: 8 });
      });
      Werkzeuge.reihenfolge.forEach(function (id) {
        var t = Werkzeuge.liste[id];
        if (t.titel.toLowerCase().indexOf(q) >= 0) treffer.push({ x: { t: t.titel, s: t.unter, art: 'werkzeug', id: id }, pkt: 7 });
      });
      treffer.sort(function (a, b) { return b.pkt - a.pkt; });
      if (!treffer.length) { box.appendChild(el('div', { class: 'leer', text: K.t('Nichts gefunden.') })); return; }
      treffer.slice(0, 40).forEach(function (t) {
        var z = el('button', { class: 'treffer' });
        z.appendChild(el('b', { text: t.x.t }));
        // Vorne die Art des Treffers: Glossar, ein einzelner Rechner oder das Thema der Lektion
        var kurz = anriss(t.x.s, 110);
        z.appendChild(el('span', { text: t.x.art === 'glossar' ? K.t('Glossar · {0}', kurz) : t.x.art === 'werkzeug' ? K.t('Rechner · {0}', kurz) : (t.x.m || '') + ' · ' + kurz }));
        z.addEventListener('click', function () {
          if (t.x.art === 'werkzeug') App.gehe('werkzeug', { id: t.x.id });
          else if (t.x.art === 'glossar') App.begriffZeigen(t.x.t);
          else App.gehe('lektion', { id: t.x.id });
        });
        box.appendChild(z);
      });
    }
    inp.addEventListener('input', suchen);
    setTimeout(function () { try { inp.focus(); } catch (e) {} }, 60);
    return d;
  };
})();
