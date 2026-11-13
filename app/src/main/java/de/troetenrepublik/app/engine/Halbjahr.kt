package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.pow

// Halbjahresregel (0.78): Winterhalbjahr (November bis April) gegen Sommerhalbjahr (Mai bis Oktober) aus denselben
// langen Tagesreihen wie die Saisonmuster (S&P 500 ab 1928, DAX ab 1988). Die Regel geht auf Bouman und Jacobsen
// (2002) zurueck. Ein Jahr der Auswertung laeuft von Ende Oktober bis Ende Oktober: erst der Winter, dann der Sommer.
// Dazu der Vergleich, den die Regel bestehen muss: nur im Winter investiert (im Sommer ohne Zins) gegen durchgehend
// investiert, beide als Rendite pro Jahr ueber alle Jahre der Auswertung, ohne Kosten und Steuern.
// Gerechnet wird hier; die mitgelieferte Datei assets/indikatoren/halbjahr.json traegt nur die Ergebnisse
// (gebaut mit gradlew :desktop:kursmuster -Phalbjahr=<Datei>).
object Halbjahr {

    const val DATEI = "indikatoren/halbjahr.json"

    // Monat im Jahr (0 = Januar), mit dessen Ende der Winter beginnt und endet
    private const val OKTOBER = 9
    private const val APRIL = 3

    // vonJahr: Jahr des ersten Novembers, bisJahr: Jahr des letzten Oktobers. winter, sommer: mittlere Rendite des
    // Halbjahrs in Prozent und wie oft es im Plus endete. winterVorn von jahre: Jahre, in denen der Winter mehr brachte
    // als der Sommer danach. regelJahr, haltenJahr: Rendite pro Jahr in Prozent.
    @Serializable
    data class Index(
        val id: String, val vonJahr: Int, val bisJahr: Int, val winter: Kursmuster.Wert, val sommer: Kursmuster.Wert,
        val winterVorn: Int, val jahre: Int, val regelJahr: Double, val haltenJahr: Double,
    )

    @Serializable
    data class Datei(val stand: String, val indizes: List<Index>)

    // Ein Jahr der Auswertung: Novemberjahr, Rendite des Winters und des Sommers danach in Prozent
    data class Jahr(val jahr: Int, val winter: Double, val sommer: Double)

    // Liegt der Monat (1 bis 12) im Winterhalbjahr?
    fun imWinter(monat: Int): Boolean = monat >= 11 || monat <= 4

    // Jahre aus Monatsenden (Jahr mal 12 plus Monat minus 1, Kurs), aelteste zuerst: je Jahr braucht es das Ende von
    // Oktober, das von April danach und das vom folgenden Oktober.
    fun jahre(enden: List<Pair<Int, Double>>): List<Jahr> {
        val kurs = enden.toMap()
        return enden.filter { it.first % 12 == OKTOBER }.mapNotNull { (m, start) ->
            val april = kurs[m + (12 - OKTOBER) + APRIL] ?: return@mapNotNull null
            val ende = kurs[m + 12] ?: return@mapNotNull null
            if (start <= 0 || april <= 0) return@mapNotNull null
            Jahr(m / 12, (april / start - 1) * 100, (ende / april - 1) * 100)
        }
    }

    private fun runden(v: Double): Double = Math.round(v * 100) / 100.0

    private fun wert(r: List<Double>): Kursmuster.Wert = Kursmuster.Wert(runden(r.average()), r.count { it > 0 }, r.size)

    // Rendite pro Jahr in Prozent aus den Renditen (Prozent) aufeinanderfolgender Abschnitte ueber jahre Jahre
    fun proJahr(renditen: List<Double>, jahre: Int): Double {
        if (jahre <= 0) return 0.0
        val faktor = renditen.fold(1.0) { f, r -> f * (1 + r / 100) }
        return if (faktor <= 0) -100.0 else (faktor.pow(1.0 / jahre) - 1) * 100
    }

    // Auswertung der Jahre; null ohne ein volles Jahr
    fun auswerten(id: String, jahre: List<Jahr>): Index? {
        if (jahre.isEmpty()) return null
        return Index(
            id, jahre.first().jahr, jahre.last().jahr + 1, wert(jahre.map { it.winter }), wert(jahre.map { it.sommer }),
            jahre.count { it.winter > it.sommer }, jahre.size,
            runden(proJahr(jahre.map { it.winter }, jahre.size)),
            runden(proJahr(jahre.flatMap { listOf(it.winter, it.sommer) }, jahre.size)),
        )
    }

    // Auswertung einer Tagesreihe (tage: Tage seit 1970)
    fun rechnen(id: String, tage: LongArray, kurs: DoubleArray): Index? = auswerten(id, jahre(Kursmuster.monatsEnden(tage, kurs)))

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun lesen(text: String): Datei? = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull()
        ?.takeIf { d -> d.indizes.isNotEmpty() && d.indizes.all { it.jahre > 0 && it.winter.anzahl == it.jahre && it.sommer.anzahl == it.jahre } }

    fun schreiben(d: Datei): String = json.encodeToString(Datei.serializer(), d)

    val datei: Datei? by lazy { runCatching { lesen(de.troetenrepublik.app.market.Daten.text(DATEI)) }.getOrNull() }

    fun index(d: Datei?, id: String): Index? = d?.indizes?.firstOrNull { it.id == id }
}
