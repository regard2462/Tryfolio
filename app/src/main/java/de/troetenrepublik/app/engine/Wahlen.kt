package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth

// Wahlen: naechste nationale Wahl aus der englischen Wikipedia, "List of next general elections" (CC BY-SA 4.0).
// Spalten: Land, Parlament (Term, Last, Next), Praesident (Term, Last, Next), Fairness, Regierung. Fairness wird nicht gelesen.
object Wahlen {
    const val ADRESSE = "https://en.wikipedia.org/w/api.php?action=parse&page=List_of_next_general_elections&prop=wikitext&format=json&formatversion=2"
    const val GUELTIG_MS = 7 * 24 * 3_600_000L

    // monat und tag 0 = unbekannt
    @Serializable
    class Datum(val jahr: Int, val monat: Int = 0, val tag: Int = 0) {
        // Letzter moeglicher Tag, fuer die Frage, ob die Wahl schon vorbei ist.
        fun spaetestens(): LocalDate = when {
            monat == 0 -> LocalDate.of(jahr, 12, 31)
            tag == 0 -> YearMonth.of(jahr, monat).atEndOfMonth()
            else -> LocalDate.of(jahr, monat, tag)
        }
    }

    @Serializable
    class Wahl(val parlament: Datum? = null, val praesident: Datum? = null)

    // Schluessel: Landesname nach Laenderrisiken.norm
    @Serializable
    class Stand(val abgerufen: Long, val laender: Map<String, Wahl>)

    private val FLAGGE = Regex("""\{\{\s*[Ff]lag\s*\|\s*([^}|]+)""")
    private val DTS = Regex("""\{\{\s*dts\s*\|\s*([^}|]+)""")
    private val MONATE = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    // "7 Jan 2030", "Jul 2030", "2030"
    fun datum(s: String): Datum? {
        val teile = s.trim().split(Regex("\\s+"))
        val jahr = teile.lastOrNull()?.toIntOrNull()?.takeIf { it in 1900..2200 } ?: return null
        var monat = 0
        var tag = 0
        for (t in teile.dropLast(1)) {
            val m = MONATE.indexOf(t.lowercase().take(3))
            if (m >= 0) monat = m + 1 else t.toIntOrNull()?.takeIf { it in 1..31 }?.let { tag = it }
        }
        if (monat == 0) tag = 0
        return Datum(jahr, monat, tag)
    }

    private fun zelleDatum(z: String?): Datum? = z?.let { DTS.find(it)?.groupValues?.get(1) }?.let(::datum)

    fun lesen(wikitext: String, jetzt: Long): Stand? {
        val aus = HashMap<String, Wahl>()
        for (zeile in wikitext.split("\n|-")) {
            val zellen = zeile.lines().map { it.trim() }.filter { it.startsWith("|") && !it.startsWith("|}") }.map { it.removePrefix("|") }
            val land = zellen.firstOrNull()?.let { FLAGGE.find(it)?.groupValues?.get(1)?.trim() } ?: continue
            // 0 Land, 1 Term, 2 Last, 3 Next (Parlament); 4 Term, 5 Last, 6 Next (Praesident), oder eine Zelle ueber drei Spalten
            val parl = zelleDatum(zellen.getOrNull(3))
            val praes = if (zellen.getOrNull(4)?.contains("colspan") == true) null else zelleDatum(zellen.getOrNull(6))
            if (parl == null && praes == null) continue
            aus[Laenderrisiken.norm(land)] = Wahl(parl, praes)
        }
        return if (aus.isEmpty()) null else Stand(jetzt, aus)
    }

    fun fuer(s: Stand, land: GlobusLand, heute: LocalDate): Wahl? {
        val w = Laenderrisiken.schluessel(land).firstNotNullOfOrNull { s.laender[it] } ?: return null
        val p = w.parlament?.takeIf { it.spaetestens() >= heute }
        val r = w.praesident?.takeIf { it.spaetestens() >= heute }
        return if (p == null && r == null) null else Wahl(p, r)
    }
}
