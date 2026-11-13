package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Zweitnotiz
import kotlin.math.abs

// Dieselbe Firma unter mehreren Kuerzeln erkennen (5.94), etwa SK Hynix in Seoul und als SKHY an der Nasdaq, Novo
// Nordisk in Kopenhagen und als ADR in New York oder die Gattungen A und C von Alphabet. Gleicher Name allein reicht
// nicht, damit verschiedene Firmen mit gleichem Namen getrennt bleiben: Zusaetzlich muss das bekannte Sitzland
// uebereinstimmen oder der Kurs in Euro fast gleich sein.
object GleicheFirma {

    // land: Sitzland laut Katalog, null wenn unbekannt; kursEur: letzter Kurs in Euro, null oder 0 ohne Kurs.
    data class Eintrag(val symbol: String, val name: String, val land: String?, val kursEur: Double?)

    // Hoechste Abweichung der Euro-Kurse, damit zwei Notierungen ohne bekanntes Land als dieselbe Aktie gelten.
    const val KURS_TOLERANZ = 0.03

    private val HINTERLEGUNG = Regex(
        "(?i)[-–,]?\\s*\\b(sponsored\\s+|unsponsored\\s+)?(adrs?|ads|american\\s+depositary\\s+(shares?|receipts?)|global\\s+depositary\\s+receipts?|gdrs?)\\b.*$",
    )
    private val GATTUNG = Regex("(?i)\\b(class|klasse|serie|series)\\s+[a-c]\\b|\\b(ordinary|common)\\s+shares?\\b|\\b(vz|st|pref)\\.?$")

    // Kern des Namens: ohne Hinterlegungsschein-Zusatz, Gattung in Klammern oder als Wort und Rechtsform.
    fun stamm(name: String): String {
        var s = name.replace(HINTERLEGUNG, " ").replace(GATTUNG, " ")
        s = Zweitnotiz.suchbegriff(s).lowercase()
        return s.replace(de.troetenrepublik.app.engine.Muster.re("[^\\p{L}\\p{N}&]+"), " ").trim()
    }

    fun gleich(a: Eintrag, b: Eintrag): Boolean {
        if (a.symbol == b.symbol) return true
        val sa = stamm(a.name)
        if (sa.length < 2 || sa != stamm(b.name)) return false
        if (a.land != null && b.land != null) return a.land == b.land
        val ka = a.kursEur?.takeIf { it > 0 } ?: return false
        val kb = b.kursEur?.takeIf { it > 0 } ?: return false
        return abs(ka / kb - 1) <= KURS_TOLERANZ
    }

    // Gruppen gleicher Firmen als Indizes in die Liste, in der Reihenfolge des ersten Auftretens. Verglichen wird nur
    // innerhalb gleicher Namensstaemme, damit auch der ganze Katalog schnell geht.
    fun gruppen(liste: List<Eintrag>): List<List<Int>> {
        val eltern = IntArray(liste.size) { it }
        fun wurzel(i: Int): Int {
            var x = i
            while (eltern[x] != x) { eltern[x] = eltern[eltern[x]]; x = eltern[x] }
            return x
        }
        liste.indices.groupBy { stamm(liste[it].name) }.values.forEach { idx ->
            for (i in idx.indices) for (j in i + 1 until idx.size) {
                if (gleich(liste[idx[i]], liste[idx[j]])) {
                    val a = wurzel(idx[i]); val b = wurzel(idx[j])
                    if (a != b) eltern[maxOf(a, b)] = minOf(a, b)
                }
            }
        }
        return liste.indices.groupBy { wurzel(it) }.values.toList()
    }

    // Heimatnotierung: bei Firmen ausserhalb der USA ein Kuerzel mit Boersenzusatz, sonst eines ohne.
    fun heimat(e: Eintrag): Boolean = if (e.land == null || e.land == "US") '.' !in e.symbol else '.' in e.symbol

    // [0.78] Eine Notierung fuer Listen und Heatmaps der Unternehmen. schluessel: Kuerzel der Kursquelle oder ein anderer
    // eindeutiger Schluessel (Firmen ohne Kuerzel: "#Name"); kennung: Kuerzel an der Heimatboerse laut Quelle (Moskauer
    // Boerse), isin: falls bekannt; boersenwert in Euro und kursEur: null oder 0 ohne Angabe.
    data class Notierung(
        val schluessel: String, val name: String, val land: String?, val boersenwert: Double? = null,
        val kursEur: Double? = null, val kennung: String = "", val isin: String = "",
    )

    // Eine Firma mit allen ihren Notierungen (Indizes in die Eingabe). haupt: die Hauptnotierung (Heimatboerse, sonst die
    // groesste), von ihr stammen Name, Tippziel und Tagesaenderung; boersenwert: Wert der Firma in Euro, null ohne Angabe.
    data class Firma(val haupt: Int, val mitglieder: List<Int>, val boersenwert: Double?)

    // Hoechste Abweichung der Boersenwerte, damit Kurzname und voller Name als dieselbe Firma gelten. Hinterlegungsscheine
    // laufen dem Heimatkurs bis etwa ein Fuenftel voraus (TSMC in New York).
    const val WERT_TOLERANZ = 0.25

    // Teil der ISIN, der den Emittenten nennt: in den USA und Kanada die sechs Stellen nach dem Land (CUSIP), sonst die
    // ersten neun Zeichen (dann zaehlt zusaetzlich das erste Wort des Namens). Null ohne gueltige ISIN.
    fun isinStamm(isin: String): String? {
        val s = isin.trim().uppercase()
        if (s.length != 12 || !s[0].isLetter() || !s[1].isLetter()) return null
        return if (s.startsWith("US") || s.startsWith("CA")) s.substring(0, 8) else s.substring(0, 9)
    }

    // Stamm- und Vorzugsaktie an der Moskauer Boerse: vier Buchstaben und dieselben mit angehaengtem P (SBER, SBERP).
    fun vorzugKennung(a: String, b: String): Boolean {
        if (a.length == b.length) return false
        val (kurz, lang) = if (a.length < b.length) a to b else b to a
        return kurz.length == 4 && lang.length == 5 && lang == kurz + "P" && kurz.all { it in 'A'..'Z' }
    }

    // Vorbereitete Angaben einer Notierung, damit grosse Listen schnell gehen
    private class Kern(val n: Notierung) {
        val stamm = stamm(n.name)
        val worte = stamm.split(' ').filter { it.isNotBlank() && it != "&" }
        // Kurzname: ein einziges Wort aus zwei bis sechs Grossbuchstaben oder Ziffern (TSMC, UMC)
        val kurz: String? = n.name.trim().takeIf { t -> t.length in 2..6 && t.all { it in 'A'..'Z' || it in '0'..'9' } && t.any { it in 'A'..'Z' } }
        val initialen = if (worte.size >= 2) worte.joinToString("") { it.take(1) }.uppercase() else ""
        val kuerzel = HeatmapBeschriftung.kuerzel(n.schluessel).takeIf { !n.schluessel.startsWith("#") && !HeatmapBeschriftung.nurZiffern(n.schluessel) }.orEmpty().uppercase()
        val isin = isinStamm(n.isin)
        val wert = n.boersenwert?.takeIf { it.isFinite() && it > 0 }
        val eintrag = Eintrag(n.schluessel, n.name, n.land, n.kursEur)
    }

    // Kurzname k und voller Name l derselben Firma: Die Anfangsbuchstaben des vollen Namens beginnen den Kurznamen
    // (TSMC, Taiwan Semiconductor), oder das Kuerzel der anderen Notierung tut es (TSM). Reicht nur zusammen mit gleichem
    // Land und fast gleichem Boersenwert.
    private fun kurzform(k: Kern, l: Kern): Boolean {
        val kurz = k.kurz ?: return false
        if (l.kurz != null || l.worte.size < 2) return false
        if (l.initialen.length >= 2 && kurz.startsWith(l.initialen)) return true
        return l.kuerzel.length >= 3 && kurz.startsWith(l.kuerzel)
    }

    private fun selbe(a: Kern, b: Kern): Boolean {
        if (a.n.schluessel == b.n.schluessel) return true
        if (a.stamm.length >= 2 && a.stamm == b.stamm) {
            if (a.n.land != null && b.n.land != null) return a.n.land == b.n.land
            val ka = a.n.kursEur?.takeIf { it > 0 }
            val kb = b.n.kursEur?.takeIf { it > 0 }
            if (ka != null && kb != null && abs(ka / kb - 1) <= KURS_TOLERANZ) return true
        }
        // Alles Weitere nur im selben bekannten Land
        if (a.n.land == null || a.n.land != b.n.land) return false
        if (a.isin != null && a.isin == b.isin) {
            if (a.isin.length == 8) return true
            if (a.worte.isNotEmpty() && a.worte.first() == b.worte.firstOrNull()) return true
        }
        if (a.n.kennung.isNotBlank() && b.n.kennung.isNotBlank() && vorzugKennung(a.n.kennung, b.n.kennung)) return true
        val wa = a.wert ?: return false
        val wb = b.wert ?: return false
        if (maxOf(wa, wb) / minOf(wa, wb) - 1 > WERT_TOLERANZ) return false
        return kurzform(a, b) || kurzform(b, a)
    }

    // Dieselbe Firma: gleicher Namensstamm im selben Land (oder fast gleicher Euro-Kurs), gleicher Emittent laut ISIN,
    // Stamm- und Vorzugsaktie laut Kennung der Boerse, oder Kurzname und voller Name mit fast gleichem Boersenwert.
    // Ein laengerer Name allein (Gazprom, Gazprom Neft) ist eine andere Firma.
    fun selbeFirma(a: Notierung, b: Notierung): Boolean = selbe(Kern(a), Kern(b))

    // Notierungen zu Firmen zusammenfuehren, in der Reihenfolge des ersten Auftretens. Gilt fuer jedes Land und jede Liste.
    fun zusammenfuehren(liste: List<Notierung>): List<Firma> {
        val kerne = liste.map { Kern(it) }
        val eltern = IntArray(liste.size) { it }
        fun wurzel(i: Int): Int {
            var x = i
            while (eltern[x] != x) { eltern[x] = eltern[eltern[x]]; x = eltern[x] }
            return x
        }
        fun pruefen(idx: List<Int>) {
            for (i in idx.indices) for (j in i + 1 until idx.size) {
                val a = wurzel(idx[i]); val b = wurzel(idx[j])
                if (a != b && selbe(kerne[idx[i]], kerne[idx[j]])) eltern[maxOf(a, b)] = minOf(a, b)
            }
        }
        // Gleicher Namensstamm, gleicher ISIN-Stamm, gleiche Kennung ohne P: je nur innerhalb der Gruppe vergleichen
        liste.indices.groupBy { kerne[it].stamm }.values.forEach { if (it.size > 1) pruefen(it) }
        liste.indices.filter { kerne[it].isin != null }.groupBy { kerne[it].isin }.values.forEach { if (it.size > 1) pruefen(it) }
        liste.indices.filter { kerne[it].n.kennung.length in 4..5 }.groupBy { kerne[it].n.kennung.take(4) + "|" + kerne[it].n.land }.values.forEach { if (it.size > 1) pruefen(it) }
        // Kurznamen gegen die uebrigen Notierungen desselben Landes
        liste.indices.filter { kerne[it].n.land != null }.groupBy { kerne[it].n.land }.values.forEach { land ->
            val kurze = land.filter { kerne[it].kurz != null && kerne[it].wert != null }
            if (kurze.isEmpty()) return@forEach
            for (k in kurze) for (l in land) {
                if (k == l) continue
                val a = wurzel(k); val b = wurzel(l)
                if (a != b && selbe(kerne[k], kerne[l])) eltern[maxOf(a, b)] = minOf(a, b)
            }
        }
        return liste.indices.groupBy { wurzel(it) }.values.map { g ->
            if (g.size == 1) return@map Firma(g[0], g, kerne[g[0]].wert)
            // Hauptnotierung: Heimatboerse (Firmen ohne Kuerzel zaehlen dazu), darunter die mit dem groessten Wert
            val daheim = g.filter { kerne[it].n.schluessel.startsWith("#") || heimat(kerne[it].eintrag) }.ifEmpty { g }
            val haupt = daheim.maxWith(compareBy<Int> { kerne[it].wert ?: 0.0 }.thenBy { -it })
            Firma(haupt, g, kerne[haupt].wert ?: g.mapNotNull { kerne[it].wert }.maxOrNull())
        }
    }
}
