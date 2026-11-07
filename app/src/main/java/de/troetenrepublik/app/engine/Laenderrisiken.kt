package de.troetenrepublik.app.engine

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.text.Normalizer

// Gemeinsames fuer den Abschnitt Risiken im Laenderblatt: Laendernamen der Quellen, Zwischenspeicher-Format,
// Governance-Rang und strukturelle Hinweise aus Weltbank-Zahlen.
object Laenderrisiken {
    // Namen ohne Akzente, Satzzeichen und fuehrendes "The", fuer den Vergleich mit Namen aus Wikipedia, US-Hinweisen usw.
    fun norm(name: String): String {
        val ohne = Normalizer.normalize(name, Normalizer.Form.NFKD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        val klein = ohne.lowercase().trim().removePrefix("the ")
        return klein.filter { it in 'a'..'z' || it in '0'..'9' }
    }

    // Weitere Schreibweisen je ISO-alpha-3-Kennung (nach norm), wo die Quellen vom Namen im Globus abweichen.
    private val ALIAS: Map<String, List<String>> = mapOf(
        "BRN" to listOf("Brunei Darussalam"), "CPV" to listOf("Cabo Verde"), "CHN" to listOf("China"),
        "COG" to listOf("Congo", "Republic of Congo", "Republic of the Congo"), "COD" to listOf("DR Congo", "Congo Democratic Republic"),
        "CIV" to listOf("Cote d'Ivoire"), "KOR" to listOf("Korea", "Republic of Korea"), "MAC" to listOf("Macao"),
        "ARE" to listOf("UAE"), "TLS" to listOf("Timor-Leste"), "TUR" to listOf("Turkiye"),
        "USA" to listOf("USA", "United States", "United States of America"), "FSM" to listOf("Micronesia"),
        "MMR" to listOf("Burma", "Myanmar (Burma)"), "NRU" to listOf("Naoero"), "KGZ" to listOf("Kyrgyz Republic"),
        "CZE" to listOf("Czechia"), "PSE" to listOf("Gaza", "West Bank", "State of Palestine"), "VAT" to listOf("Vatican", "Holy See"),
        "SWZ" to listOf("Eswatini", "Swaziland"), "ESH" to listOf("Sahrawi Arab Democratic Republic"), "RUS" to listOf("Russian Federation"),
    )

    // Alle Schluessel (norm), unter denen ein Land in den Namenstabellen der Quellen stehen kann.
    fun schluessel(land: GlobusLand): List<String> =
        (listOf(land.en, land.de) + (ALIAS[land.a3] ?: emptyList())).map(::norm).distinct()

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun <T> schreiben(ser: KSerializer<T>, x: T): String = json.encodeToString(ser, x)
    fun <T> lesen(ser: KSerializer<T>, text: String): T? = runCatching { json.decodeFromString(ser, text) }.getOrNull()

    // ---- Governance (Weltbank WGI) ----

    // Kuerzel der sechs Dimensionen; Kennung "GOV_WGI_<kuerzel>.SC" in Quelle 3, Wert 0 bis 100.
    val WGI = listOf("PV", "GE", "RQ", "RL", "CC", "VA")
    fun wgiKennung(k: String) = "GOV_WGI_$k.SC"

    class Rang(val perzentil: Int, val jahr: Int?)

    // Anteil der uebrigen Laender (0 bis 100), die schlechter abschneiden; jahr nur, wenn nicht das juengste der Reihe.
    fun perzentil(r: Weltbank.Reihe, a3: String): Rang? {
        val w = r.werte[a3] ?: return null
        val alle = r.werte.values
        if (alle.size < 2) return null
        val schlechter = alle.count { it.wert < w.wert }
        val p = Math.round(schlechter * 100.0 / (alle.size - 1)).toInt().coerceIn(0, 100)
        return Rang(p, jahrWennAlt(r, w))
    }

    fun jahrWennAlt(r: Weltbank.Reihe, w: Weltbank.Wert): Int? =
        if (w.jahr < (r.werte.values.maxOfOrNull { it.jahr } ?: w.jahr)) w.jahr else null

    // ---- Strukturelle Hinweise ----

    enum class Art { RESERVEN, ROHSTOFF, JUGEND, ALTER, AUSLAND }

    // a und b je nach Art: Rohstoff = Brennstoffe und Erze/Metalle in Prozent der Warenausfuhr; Jugend: b = Weltwert.
    class Hinweis(val art: Art, val wert: Double, val jahr: Int?, val a: Double? = null, val b: Double? = null)

    const val KENN_RESERVEN = "FI.RES.TOTL.MO"
    const val KENN_BRENNSTOFF = "TX.VAL.FUEL.ZS.UN"
    const val KENN_ERZE = "TX.VAL.MMTL.ZS.UN"
    const val KENN_JUGEND = "SL.UEM.1524.ZS"
    const val KENN_ALTER = "SP.POP.65UP.TO.ZS"
    const val KENN_AUSLAND = "DT.DOD.DECT.GN.ZS"

    // Schwellen mit Beleg: Reserven unter drei Monaten Importe (IWF-Faustregel), Rohstoffe ab 20 % der Exporte (IWF-Definition
    // rohstoffreich), Anteil 65+ ab 14 % (gaengige Einteilung "aged society"). Jugendarbeitslosigkeit und Auslandsschulden
    // haben keine belegte Schwelle und stehen als Zahl da.
    const val SCHWELLE_RESERVEN = 3.0
    const val SCHWELLE_ROHSTOFF = 20.0
    const val SCHWELLE_ALTER = 14.0

    fun struktur(
        a3: String, reserven: Weltbank.Reihe?, brennstoff: Weltbank.Reihe?, erze: Weltbank.Reihe?,
        jugend: Weltbank.Reihe?, alter: Weltbank.Reihe?, ausland: Weltbank.Reihe?,
    ): List<Hinweis> {
        val aus = ArrayList<Hinweis>()
        reserven?.werte?.get(a3)?.let { w -> if (w.wert < SCHWELLE_RESERVEN) aus += Hinweis(Art.RESERVEN, w.wert, jahrWennAlt(reserven, w)) }
        val b = brennstoff?.werte?.get(a3)
        val e = erze?.werte?.get(a3)
        if (b != null && e != null && b.wert + e.wert >= SCHWELLE_ROHSTOFF) {
            aus += Hinweis(Art.ROHSTOFF, b.wert + e.wert, jahrWennAlt(brennstoff, b) ?: jahrWennAlt(erze, e), b.wert, e.wert)
        }
        jugend?.werte?.get(a3)?.let { w -> aus += Hinweis(Art.JUGEND, w.wert, jahrWennAlt(jugend, w), b = jugend.werte["WLD"]?.wert) }
        alter?.werte?.get(a3)?.let { w -> if (w.wert >= SCHWELLE_ALTER) aus += Hinweis(Art.ALTER, w.wert, jahrWennAlt(alter, w)) }
        ausland?.werte?.get(a3)?.let { w -> aus += Hinweis(Art.AUSLAND, w.wert, jahrWennAlt(ausland, w)) }
        return aus
    }
}
