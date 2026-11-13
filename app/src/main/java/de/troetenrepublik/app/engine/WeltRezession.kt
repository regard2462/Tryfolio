package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

// Rezessionszeichen der Welt (0.69): drei US-Reihen von FRED (CSV ohne Schluessel, nur Anzeige beim Nutzer). Zinskurve
// und Sahm-Regel zeigt die Weltuebersicht mit dem Wert der Fruehwarnung (zinskurveAnzeige, sahmAnzeige); die FRED-Reihen
// dazu bleiben der gemerkte Rueckfall ohne Netz.
// T10Y3M: Rendite 10 Jahre minus 3 Monate in Prozentpunkten (taeglich).
// SAHMREALTIME: Sahm-Regel in Prozentpunkten (monatlich), Schwelle 0,5.
// RECPROUSM156N: geschaetzte Rezessionswahrscheinlichkeit in Prozent (monatlich), Modell von Chauvet und Piger.
object WeltRezession {
    const val ZINSKURVE = "T10Y3M"
    const val SAHM = "SAHMREALTIME"
    const val WAHRSCHEINLICHKEIT = "RECPROUSM156N"

    // Schwelle der Sahm-Regel in Prozentpunkten
    const val SCHWELLE_SAHM = 0.5

    const val GUELTIG_MS = 24 * 3_600_000L

    // tag: Tage seit 1970 (Zeitstempel des Werts bei FRED)
    @Serializable
    data class Wert(val tag: Long, val wert: Double)

    @Serializable
    data class Daten(val abgerufen: Long = 0L, val zinskurve: Wert? = null, val sahm: Wert? = null, val wahrscheinlichkeit: Wert? = null) {
        val leer: Boolean get() = zinskurve == null && sahm == null && wahrscheinlichkeit == null
    }

    fun adresse(reihe: String, von: LocalDate) = "https://fred.stlouisfed.org/graph/fredgraph.csv?id=$reihe&cosd=$von"

    // Letzter Wert einer FRED-CSV
    fun letzter(csv: String): Wert? = Fruehindikatoren.fredLesen(csv).lastOrNull()?.let { Wert(it.first, it.second) }

    fun veraltet(d: Daten?, jetzt: Long) = d == null || jetzt - d.abgerufen >= GUELTIG_MS || jetzt < d.abgerufen

    // Fehlendes bleibt vom letzten Stand.
    fun zusammen(neu: Daten, alt: Daten?) = Daten(
        neu.abgerufen, neu.zinskurve ?: alt?.zinskurve, neu.sahm ?: alt?.sahm, neu.wahrscheinlichkeit ?: alt?.wahrscheinlichkeit,
    )

    // Zinskurve und Sahm-Regel in der Weltuebersicht: Wert und Ampel des Fruehwarnzeichens der Stimmung, damit beide
    // Stellen dieselbe Zahl in derselben Farbe zeigen. Ohne Fruehwarnzeichen (ohne Netz nach dem Start) der gemerkte
    // FRED-Wert: Sahm mit den Schwellen der Fruehwarnung, die Zinskurve unter null Gelb und sonst ohne Farbe, weil die
    // Regel der Fruehwarnung den Verlauf der letzten 18 Monate braucht.
    // tag: Tage seit 1970 des gezeigten Werts
    data class Anzeige(val wert: Double, val tag: Long, val ampel: Fruehwarnung.Ampel?, val ausFruehwarnung: Boolean)

    private fun ausZeichen(fw: List<Fruehwarnung.Zeichen>, schluessel: String): Anzeige? {
        val z = fw.firstOrNull { it.schluessel == schluessel } ?: return null
        val t = z.verlauf.lastOrNull()?.first ?: return null
        return Anzeige(z.wert, Math.floorDiv(t, 86_400_000L), z.ampel, true)
    }

    fun zinskurveAnzeige(fw: List<Fruehwarnung.Zeichen>, d: Daten?): Anzeige? =
        ausZeichen(fw, Fruehwarnung.ZINSKURVE)
            ?: d?.zinskurve?.let { Anzeige(it.wert, it.tag, if (it.wert < 0) Fruehwarnung.Ampel.GELB else null, false) }

    fun sahmAnzeige(fw: List<Fruehwarnung.Zeichen>, d: Daten?): Anzeige? =
        ausZeichen(fw, Fruehwarnung.SAHM) ?: d?.sahm?.let { Anzeige(it.wert, it.tag, Fruehwarnung.sahmAmpel(it.wert), false) }

    private val json = Json { ignoreUnknownKeys = true }
    fun schreiben(d: Daten): String = json.encodeToString(Daten.serializer(), d)
    fun lesenGespeichert(text: String): Daten? = runCatching { json.decodeFromString(Daten.serializer(), text) }.getOrNull()?.takeIf { !it.leer }
}
