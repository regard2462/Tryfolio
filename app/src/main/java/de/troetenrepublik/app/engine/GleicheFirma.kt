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
        return s.replace(Regex("[^\\p{L}\\p{N}&]+"), " ").trim()
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
}
