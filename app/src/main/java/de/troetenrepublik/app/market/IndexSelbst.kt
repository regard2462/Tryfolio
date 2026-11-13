package de.troetenrepublik.app.market

import java.time.LocalDate

// [Globus Boersen 0.73] Leitindizes von Boersen und Diensten hinter einer Bot-Pruefung: Die Person oeffnet die Seite sichtbar im
// Browser der App, loest eine Pruefung selbst und uebernimmt die geladene Seite; die App liest nur, was angezeigt wird. Nichts
// wird automatisch geloest oder im Hintergrund geladen. Der Wert gilt wie jeder andere nur, solange sein Kursdatum nicht aelter
// als vier Handelstage ist; Yahoo und Direktabruf gehen vor.
object IndexSelbst {
    // namen: Muster fuer die Zeile des Index im Seitentext; stooq: Verlaufstabelle statt Zeilenmuster
    class Quelle(val id: String, val land: String, val name: String, val index: String, val adresse: String, val namen: Regex? = null, val stooq: Boolean = false)

    val QUELLEN: List<Quelle> = listOf(
        Quelle("stooq-ar", "AR", "Stooq", "S&P Merval", "https://stooq.com/q/d/?s=%5Emrv", stooq = true),
        Quelle("adx", "AE", "Abu Dhabi Securities Exchange", "ADX General Index", "https://www.adx.ae/", Regex("FADGI|ADX General Index|\\bGeneral Index\\b", RegexOption.IGNORE_CASE)),
        Quelle("luse", "ZM", "Lusaka Securities Exchange", "LuSE All Share Index", "https://www.luse.co.zm/", Regex("LASI|All[- ]Share Index", RegexOption.IGNORE_CASE)),
        Quelle("bvrd", "DO", "Bolsa de Valores de la República Dominicana", "Índice BVRD", "https://www.bvrd.com.do/", Regex("IPBVRD|[ÍI]ndice BVRD|BVRD Index|[ÍI]ndice de Precios", RegexOption.IGNORE_CASE)),
        Quelle("ttse", "TT", "Trinidad and Tobago Stock Exchange", "TTSE Composite", "https://www.stockex.co.tt/", Regex("Composite (?:Price )?Index|Composite Total Return|\\bComposite\\b", RegexOption.IGNORE_CASE)),
        Quelle("ilboursa", "TN", "ilBoursa (Börse Tunis)", "TUNINDEX", "https://www.ilboursa.com/", Regex("TUNINDEX", RegexOption.IGNORE_CASE)),
    )

    fun fuer(land: String): Quelle? = QUELLEN.firstOrNull { it.land == land }

    // Zahl, dann Prozent innerhalb von 160 Zeichen nach dem Namen; das erste Paar mit Prozent gilt
    fun zeile(html: String, namen: Regex): BoersenDirekt.Stand? {
        val t = BoersenDirekt.klartext(html)
        for (m in namen.findAll(t)) {
            val rest = t.substring(m.range.last + 1, minOf(t.length, m.range.last + 1 + 160))
            val z = Regex("(\\d{1,3}(?:[ ,.]\\d{3})+(?:[.,]\\d+)?|\\d+[.,]\\d+)").find(rest) ?: continue
            val p = Regex("([+\\-\\u2212]?\\d+(?:[.,]\\d+)?)\\s*%").find(rest.substring(z.range.last + 1)) ?: continue
            val wert = zahlSchrift(z.value)
            val prozent = zahlSchrift(p.groupValues[1]) ?: continue
            return BoersenDirekt.Stand(prozent, wert, datumIn(t))
        }
        return null
    }

    // Letzter Punkt oder Komma ist Dezimaltrenner, wenn danach nicht genau drei Ziffern stehen oder der andere Trenner davor kam
    internal fun zahlSchrift(s: String): Double? {
        val t = s.replace('−', '-').replace(" ", "").replace(" ", "").trimStart('+')
        val punkt = t.lastIndexOf('.'); val komma = t.lastIndexOf(',')
        val n = when {
            punkt >= 0 && komma >= 0 -> if (punkt > komma) t.replace(",", "") else t.replace(".", "").replace(',', '.')
            komma >= 0 -> if (t.count { it == ',' } > 1 || t.length - komma - 1 == 3) t.replace(",", "") else t.replace(',', '.')
            punkt >= 0 -> if (t.count { it == '.' } > 1 || t.length - punkt - 1 == 3 && t.substringBefore('.').length in 1..3 && t.substringBefore('.') != "0") t.replace(".", "") else t
            else -> t
        }
        return n.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    // Erstes Datum im Text (jjjj-mm-tt oder tt.mm.jjjj), sonst null
    private fun datumIn(t: String): LocalDate? {
        Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(t)?.destructured?.let { (j, m, d) -> runCatching { LocalDate.of(j.toInt(), m.toInt(), d.toInt()) }.getOrNull()?.let { return it } }
        Regex("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})").find(t)?.destructured?.let { (d, m, j) -> runCatching { LocalDate.of(j.toInt(), m.toInt(), d.toInt()) }.getOrNull()?.let { return it } }
        return null
    }

    private val MON = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private fun tabellenDatum(s: String): LocalDate? {
        datumIn(s)?.let { return it }
        val m = Regex("(\\d{1,2})\\s+([A-Za-z]{3})[a-z]*\\.?\\s+(\\d{4})").find(s) ?: return null
        val mo = MON.indexOf(m.groupValues[2].lowercase()) + 1
        return if (mo > 0) runCatching { LocalDate.of(m.groupValues[3].toInt(), mo, m.groupValues[1].toInt()) }.getOrNull() else null
    }

    // Verlaufstabelle (Datum, Eroeffnung, Hoch, Tief, Schluss ...): aus den zwei juengsten Schlusskursen die Aenderung
    fun verlauf(html: String): BoersenDirekt.Stand? {
        val seite = html.replace(Regex("(?is)<(script|style|noscript)\\b.*?</\\1>"), " ")
        for (tab in Regex("(?is)<table\\b[^>]*>(.*?)</table>").findAll(seite)) {
            val zeilen = Regex("(?is)<tr\\b[^>]*>(.*?)</tr>").findAll(tab.groupValues[1]).map { r ->
                Regex("(?is)<t[dh]\\b[^>]*>(.*?)</t[dh]>").findAll(r.groupValues[1]).map { BoersenDirekt.klartext(it.groupValues[1]) }.toList()
            }.toList()
            val kopf = zeilen.indexOfFirst { z -> z.any { it.equals("Close", true) } && z.any { it.equals("Date", true) } }
            if (kopf < 0) continue
            val iD = zeilen[kopf].indexOfFirst { it.equals("Date", true) }
            val iC = zeilen[kopf].indexOfFirst { it.equals("Close", true) }
            val reihe = zeilen.drop(kopf + 1).mapNotNull { z ->
                val d = z.getOrNull(iD)?.let { tabellenDatum(it) } ?: return@mapNotNull null
                val c = z.getOrNull(iC)?.let { zahlSchrift(it) }?.takeIf { it > 0 } ?: return@mapNotNull null
                d to c
            }.sortedByDescending { it.first }
            if (reihe.size < 2) continue
            return BoersenDirekt.Stand((reihe[0].second / reihe[1].second - 1.0) * 100.0, reihe[0].second, reihe[0].first)
        }
        return null
    }

    fun lesen(q: Quelle, html: String, jetzt: Long, heute: LocalDate): IndexStand.Wert? {
        val s = (if (q.stooq) verlauf(html) else q.namen?.let { zeile(html, it) }) ?: return null
        if (!s.prozent.isFinite() || kotlin.math.abs(s.prozent) > IndexLader.MAX_PROZENT) return null
        val d = s.datum?.takeIf { !it.isAfter(heute) } ?: heute
        return IndexStand.Wert(q.land, "selbst:" + q.id, s.prozent, s.wert, d, jetzt)
    }
}
