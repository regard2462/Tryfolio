package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.FinderVorlagen.Feld
import de.troetenrepublik.app.engine.FinderVorlagen.Grenze
import de.troetenrepublik.app.engine.FinderVorlagen.Pruefung
import kotlin.math.ceil

// Auswahlraster im Aktienfinder (0.78): Regeln, die ueber die festen Grenzen eines Werts hinausgehen und den ganzen
// gewaehlten Markt brauchen. Anteilsgrenzen (der guenstigste Teil des Markts), „mindestens n von m“, die n hoechsten oder
// niedrigsten Werte und die Rangsumme ueber mehrere Felder. Dazu die Felder aus den Jahresabschluessen. Reine Rechnung.
object FinderRaster {

    // ------------------------------------------------------------ Rangsumme

    // Ein Feld der Rangsumme; oben: der hoechste Wert bekommt Rang 1.
    data class Rangfeld(val feld: Feld, val oben: Boolean = true)

    // Raenge ab 1 (bester Wert); gleiche Werte teilen sich den mittleren Rang.
    fun raenge(werte: Map<String, Double>, oben: Boolean = true): Map<String, Double> {
        val sortiert = werte.entries.filter { it.value.isFinite() }.sortedBy { if (oben) -it.value else it.value }
        val aus = HashMap<String, Double>(sortiert.size)
        var i = 0
        while (i < sortiert.size) {
            var j = i
            while (j + 1 < sortiert.size && sortiert[j + 1].value == sortiert[i].value) j++
            val rang = (i + j) / 2.0 + 1.0
            for (k in i..j) aus[sortiert[k].key] = rang
            i = j + 1
        }
        return aus
    }

    // Summe der Raenge ueber alle Reihen (Werte je Schluessel und ob der hoechste Wert der beste ist). Gezaehlt werden
    // nur Schluessel mit einem Wert in jeder Reihe; die Raenge gelten unter genau diesen. Die kleinste Summe ist die beste.
    fun rangsumme(reihen: List<Pair<Map<String, Double>, Boolean>>): Map<String, Double> {
        if (reihen.isEmpty()) return emptyMap()
        val alle = reihen.map { r -> r.first.filterValues { it.isFinite() }.keys }.reduce { a, b -> a intersect b }
        val aus = HashMap<String, Double>(alle.size)
        for ((werte, oben) in reihen) raenge(werte.filterKeys { it in alle }, oben).forEach { (k, r) -> aus[k] = (aus[k] ?: 0.0) + r }
        return aus
    }

    // ------------------------------------------------------------ Anteil, Auswahl, Spitze

    // Eine Anteilsgrenze braucht mindestens so viele Werte im Markt.
    const val ANTEIL_MIN = 5

    // Schwelle fuer den Anteil (0 bis 1) der niedrigsten Werte, mit oben der hoechsten: der letzte Wert, der noch
    // dazugehoert. null mit weniger als ANTEIL_MIN Werten.
    fun schwelle(werte: List<Double>, anteil: Double, oben: Boolean = false): Double? {
        val s = werte.filter { it.isFinite() }.sorted()
        if (s.size < ANTEIL_MIN || !(anteil > 0)) return null
        val n = ceil(s.size * anteil - 1e-9).toInt().coerceIn(1, s.size)
        return if (oben) s[s.size - n] else s[n - 1]
    }

    // „Mindestens n von m“: JA, sobald n Teile erfuellt sind; NEIN, wenn das auch mit den offenen nicht mehr geht.
    fun mindestens(teile: List<Pruefung>, n: Int): Pruefung {
        val ja = teile.count { it == Pruefung.JA }
        val offen = teile.count { it == Pruefung.OFFEN }
        return when {
            ja >= n -> Pruefung.JA
            ja + offen < n -> Pruefung.NEIN
            else -> Pruefung.OFFEN
        }
    }

    // Die n Schluessel mit den hoechsten (oben) oder niedrigsten Werten; bei Gleichstand entscheidet der Schluessel.
    fun spitze(werte: Map<String, Double>, n: Int, oben: Boolean): Set<String> =
        werte.entries.filter { it.value.isFinite() }
            .sortedWith(compareBy<Map.Entry<String, Double>> { if (oben) -it.value else it.value }.thenBy { it.key })
            .take(n.coerceAtLeast(0)).mapTo(HashSet()) { it.key }

    // Branchen des Katalogs und Sektoren von Yahoo, die eine Vorlage ausnimmt.
    fun ausgenommen(d: Aktienfinder.Daten, v: FinderVorlagen.Vorlage?): Boolean {
        val ohne = v?.ohneBranchen.orEmpty()
        if (ohne.isEmpty()) return false
        if (d.branche in ohne) return true
        if ("BANKEN" in ohne && (d.sektor.isNotBlank() || d.industrie.isNotBlank()) && Qualitaet.ausgenommen(d.sektor, d.industrie)) return true
        return "VERSORGER" in ohne && d.sektor.lowercase().contains("utilit")
    }

    // Prueft alle Werte des Markts gegen die Grenzen. Feste Grenzen wie FinderVorlagen.pruefen; Anteilsgrenzen gegen die
    // Schwelle unter allen Werten des Markts, die in den festen Rahmen der Grenze fallen; Grenzen mit wahl zaehlen
    // zusammen als „mindestens n von m“ (n aus der Vorlage, ohne Vorlage alle); Grenzen mit spitze behalten zuletzt, in
    // ihrer Reihenfolge, nur die n besten der sicher passenden Werte. Solange Werte fehlen, sind Schwellen und Spitze vorlaeufig.
    fun pruefen(liste: List<Aktienfinder.Daten>, grenzen: List<Grenze>, v: FinderVorlagen.Vorlage?, jetzt: Long = System.currentTimeMillis()): Map<String, Pruefung> {
        val aus = HashMap<String, Pruefung>(liste.size)
        val dabei = ArrayList<Aktienfinder.Daten>(liste.size)
        for (d in liste) if (ausgenommen(d, v)) aus[d.symbol] = Pruefung.NEIN else dabei += d
        val schwellen = HashMap<Grenze, Double?>()
        val vollstaendig = HashMap<Grenze, Boolean>()
        for (g in grenzen) {
            val a = g.anteil ?: continue
            schwellen[g] = schwelle(dabei.mapNotNull { FinderVorlagen.wert(it, g.feld, jetzt) }.filter { g.passt(it) }, a, g.oben)
            vollstaendig[g] = dabei.all { FinderVorlagen.geladen(it, g.feld.stufe) }
        }
        fun eine(d: Aktienfinder.Daten, g: Grenze): Pruefung {
            val w = FinderVorlagen.wert(d, g.feld, jetzt)
            val da = FinderVorlagen.geladen(d, g.feld.stufe)
            if (w == null) {
                // Ohne gemeldeten Leerverkauf gilt eine reine Obergrenze als erfuellt (wie FinderVorlagen.pruefen)
                if (g.feld == Feld.LEER && g.min == null && g.anteil == null && g.spitze == 0 && da) return Pruefung.JA
                return if (da) Pruefung.NEIN else Pruefung.OFFEN
            }
            if (!g.passt(w)) return Pruefung.NEIN
            if (g.anteil == null) return Pruefung.JA
            val s = schwellen[g] ?: return if (vollstaendig[g] == true) Pruefung.NEIN else Pruefung.OFFEN
            return if (if (g.oben) w >= s else w <= s) Pruefung.JA else Pruefung.NEIN
        }
        val pflicht = grenzen.filter { !it.wahl }
        val wahl = grenzen.filter { it.wahl }
        val n = (v?.mindestens?.takeIf { it > 0 } ?: wahl.size).coerceAtMost(wahl.size)
        for (d in dabei) {
            var offen = false
            var nein = false
            for (g in pflicht) {
                when (eine(d, g)) {
                    Pruefung.NEIN -> nein = true
                    Pruefung.OFFEN -> offen = true
                    Pruefung.JA -> {}
                }
                if (nein) break
            }
            if (!nein && n > 0) when (mindestens(wahl.map { eine(d, it) }, n)) {
                Pruefung.NEIN -> nein = true
                Pruefung.OFFEN -> offen = true
                Pruefung.JA -> {}
            }
            aus[d.symbol] = if (nein) Pruefung.NEIN else if (offen) Pruefung.OFFEN else Pruefung.JA
        }
        for (g in grenzen) {
            if (g.spitze <= 0) continue
            val sicher = dabei.filter { aus[it.symbol] == Pruefung.JA }
            val bleibt = spitze(sicher.mapNotNull { d -> FinderVorlagen.wert(d, g.feld, jetzt)?.let { d.symbol to it } }.toMap(), g.spitze, g.oben)
            for (d in sicher) if (d.symbol !in bleibt) aus[d.symbol] = Pruefung.NEIN
            // Offene Werte bleiben nur offen, solange sie noch unter die n besten kommen koennen: Ist die Spitze voll,
            // braucht es einen bekannten Wert, der besser ist als der schwaechste der Spitze.
            if (bleibt.size < g.spitze) continue
            val grenzwert = sicher.filter { it.symbol in bleibt }.mapNotNull { FinderVorlagen.wert(it, g.feld, jetzt) }
                .let { if (g.oben) it.minOrNull() else it.maxOrNull() } ?: continue
            for (d in dabei) if (aus[d.symbol] == Pruefung.OFFEN) {
                val w = FinderVorlagen.wert(d, g.feld, jetzt)
                if (w == null || !(if (g.oben) w > grenzwert else w < grenzwert)) aus[d.symbol] = Pruefung.NEIN
            }
        }
        return aus
    }

    // ------------------------------------------------------------ Felder aus den Jahresabschluessen

    // Aelter darf das letzte Geschaeftsjahr nicht sein, sonst gibt es keine Felder (Yahoo fuehrt die Firma nicht mehr fort).
    const val FRISCH_TAGE = 800L

    // Jahre fuer den Durchschnittsgewinn im KGV nach Graham.
    const val SCHNITT_JAHRE = 3

    private const val TAG_MS = 86_400_000L
    private const val LUECKE_TAGE = 400L

    // Felder der Vorlagen aus den Jahresabschluessen (aelteste zuerst, Betraege in der Berichtswaehrung).
    // boersenwert: Boersenwert in der Berichtswaehrung. jeEuro: Einheiten der Berichtswaehrung je Euro, fuer den Umsatz in
    // Euro. mitNeuenFeldern: der Abruf hat Sachanlagen, Schulden und Kasse angefragt; nur dann zaehlen fehlende Schulden
    // als null (schuldenfreie Firmen fuehrt Yahoo ohne den Posten). Fehlt sonst eine Angabe, fehlt das Feld.
    fun bilanzWerte(jahre: List<Qualitaet.Jahr>, boersenwert: Double?, jeEuro: Double?, mitNeuenFeldern: Boolean, jetzt: Long = System.currentTimeMillis()): Map<Feld, Double> {
        val j = jahre.lastOrNull() ?: return emptyMap()
        val ende = Historie078.datum(j.ende) ?: return emptyMap()
        if (jetzt / TAG_MS - ende > FRISCH_TAGE) return emptyMap()
        val aus = HashMap<Feld, Double>()
        fun setze(f: Feld, v: Double?) { if (v != null && v.isFinite()) aus[f] = v }
        val mv = boersenwert?.takeIf { it > 0 && it.isFinite() }
        val umsatz = j.umsatz?.takeIf { it > 0 }
        if (umsatz != null) {
            if (jeEuro != null && jeEuro > 0) setze(Feld.UMSATZ, umsatz / jeEuro)
            j.nettogewinn?.let { setze(Feld.MARGE, it / umsatz * 100.0) }
            if (mv != null) setze(Feld.KUV, mv / umsatz)
        }
        if (mv != null) {
            // Durchschnittsgewinn der letzten drei zusammenhaengenden Geschaeftsjahre; nur bei positivem Schnitt
            val letzte = jahre.takeLast(SCHNITT_JAHRE)
            val tage = letzte.map { Historie078.datum(it.ende) }
            val gewinne = letzte.mapNotNull { it.nettogewinn }
            val zusammen = tage.all { it != null } && tage.filterNotNull().zipWithNext().all { (a, b) -> b - a in 1..LUECKE_TAGE }
            if (letzte.size == SCHNITT_JAHRE && gewinne.size == SCHNITT_JAHRE && zusammen) {
                val schnitt = gewinne.average()
                if (schnitt > 0) setze(Feld.KGV_SCHNITT3, (mv / schnitt).takeIf { it < 1000 })
            }
            val umlauf = j.umlaufvermoegen
            val verbindlichkeiten = j.verbindlichkeiten
            if (umlauf != null && verbindlichkeiten != null) setze(Feld.NETTO_UMLAUF, (umlauf - verbindlichkeiten) / mv * 100.0)
            (j.cashflowLaufend ?: j.cashflow)?.takeIf { it > 0 }?.let { setze(Feld.KCF, mv / it) }
            (j.freierCashflowLaufend ?: j.freierCashflow)?.takeIf { it > 0 }?.let { setze(Feld.KFCF, mv / it) }
        }
        val ebit = j.ebit
        if (ebit != null && mitNeuenFeldern) {
            // Gewinnrendite: EBIT durch Unternehmenswert (Boersenwert plus Schulden minus Kasse)
            val kasse = j.kasse
            if (mv != null && kasse != null && j.bilanzsumme != null) {
                val ev = mv + (j.gesamtschulden ?: 0.0) - kasse
                if (ev > 0) setze(Feld.GEWINNRENDITE, ebit / ev * 100.0)
            }
            // Kapitalrendite: EBIT durch Nettoumlaufvermoegen plus Sachanlagen
            val netto = j.betriebskapital ?: j.umlaufvermoegen?.let { a -> j.kurzfristSchulden?.let { a - it } }
            val anlagen = j.sachanlagen
            if (netto != null && anlagen != null) {
                val kapital = netto + anlagen
                if (kapital > 0) setze(Feld.KAPITALRENDITE, ebit / kapital * 100.0)
            }
        }
        return aus
    }
}
