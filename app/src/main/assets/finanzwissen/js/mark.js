/* ============================================================
   mark.js - sehr kleiner Markdown-Satz fuer Lektionstexte
   Unterstuetzt: **fett**, *kursiv*, `code`, ~~durch~~,
   Absaetze (Leerzeile), - Listen, 1. Listen, > Zitat,
   ## Zwischenueberschrift, [[Begriff]] -> Glossar-Verweis.
   ============================================================ */
(function () {
  'use strict';

  function esc(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  function inline(s) {
    s = esc(s);
    s = s.replace(/`([^`]+)`/g, function (_, t) { return '<code>' + t + '</code>'; });
    s = s.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
    s = s.replace(/(^|[\s(])\*([^*\n]+)\*(?=[\s).,;:!?]|$)/g, '$1<em>$2</em>');
    s = s.replace(/~~([^~]+)~~/g, '<s>$1</s>');
    s = s.replace(/\[\[([^\]|]+)(?:\|([^\]]+))?\]\]/g, function (_, begriff, zeige) {
      return '<a href="#" class="gl" data-begriff="' + begriff.replace(/"/g, '') + '">' + (zeige || begriff) + '</a>';
    });
    return s;
  }

  function block(text) {
    if (!text) return '';
    var zeilen = String(text).replace(/\r/g, '').split('\n');
    var aus = [], liste = null, absatz = [];

    function absatzSchliessen() {
      if (absatz.length) { aus.push('<p>' + inline(absatz.join(' ')) + '</p>'); absatz = []; }
    }
    function listeSchliessen() {
      if (liste) { aus.push('<' + liste.tag + '>' + liste.eintraege.map(function (e) { return '<li>' + inline(e) + '</li>'; }).join('') + '</' + liste.tag + '>'); liste = null; }
    }

    for (var i = 0; i < zeilen.length; i++) {
      var z = zeilen[i];
      var t = z.trim();

      if (!t) { absatzSchliessen(); listeSchliessen(); continue; }

      var h = t.match(/^(#{2,4})\s+(.*)$/);
      if (h) {
        absatzSchliessen(); listeSchliessen();
        var stufe = Math.min(4, h[1].length);
        aus.push('<h' + stufe + '>' + inline(h[2]) + '</h' + stufe + '>');
        continue;
      }

      var zit = t.match(/^>\s?(.*)$/);
      if (zit) {
        absatzSchliessen(); listeSchliessen();
        aus.push('<blockquote class="zitat">' + inline(zit[1]) + '</blockquote>');
        continue;
      }

      var ul = t.match(/^[-*+]\s+(.*)$/);
      var ol = t.match(/^\d+[.)]\s+(.*)$/);
      if (ul || ol) {
        absatzSchliessen();
        var tag = ul ? 'ul' : 'ol';
        if (!liste || liste.tag !== tag) { listeSchliessen(); liste = { tag: tag, eintraege: [] }; }
        liste.eintraege.push((ul || ol)[1]);
        continue;
      }

      listeSchliessen();
      absatz.push(t);
    }
    absatzSchliessen(); listeSchliessen();
    return aus.join('');
  }

  window.Mark = { block: block, inline: inline, esc: esc };
})();
