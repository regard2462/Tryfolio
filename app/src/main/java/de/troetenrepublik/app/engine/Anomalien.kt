package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Renditemuster aus der Forschung vor und nach ihrer Veroeffentlichung (0.78, Werkzeug „Markt schlagen“): je Signal die
// mittlere Monatsrendite des Long-Short-Portfolios in der Stichprobe der Studie, danach bis zum Jahr der
// Veroeffentlichung und ab dem Folgejahr. Die mitgelieferte Datei assets/indikatoren/anomalien.json traegt nur diese
// Mittelwerte (gebaut mit tools/daten/anomalien.py aus dem Datensatz von Chen und Zimmermann, CC BY 4.0); gerechnet
// wird hier. US-Aktien, Renditen vor Kosten.
object Anomalien {

    const val DATEI = "indikatoren/anomalien.json"

    // k: Kurzzeichen des Datensatzes, name: Bezeichnung dort (englisch), jahr: Jahr der Veroeffentlichung, von/bis:
    // Stichprobe der Studie. studie, zwischen, danach: mittlere Monatsrendite in Prozent; n…: Monate dahinter.
    @Serializable
    data class Signal(
        val k: String, val name: String, val autoren: String = "", val jahr: Int, val von: Int, val bis: Int,
        val studie: Double, val danach: Double, val nStudie: Int = 0, val nDanach: Int = 0,
        val zwischen: Double? = null, val nZwischen: Int = 0,
    )

    // stand: Fassung des Datensatzes (JJJJ-MM), bis: letzter Monat der Renditen, minMonate: Mindestzahl Monate je Abschnitt
    @Serializable
    data class Datei(val stand: String, val bis: String = "", val minMonate: Int = 0, val signale: List<Signal>)

    // Mittel ueber alle Signale in Prozent je Monat. rueckgang: um wie viel Prozent das Mittel nach der Veroeffentlichung
    // unter dem der Studien liegt (Verhaeltnis der Mittelwerte), rueckgangZwischen dasselbe fuer die Zeit bis zur
    // Veroeffentlichung, nur ueber die Signale mit solchen Monaten. schwaecher: Signale, deren Rendite danach unter der
    // der Studie lag. negativ: Signale mit Rendite unter null nach der Veroeffentlichung.
    data class Auswertung(
        val anzahl: Int, val studie: Double, val zwischen: Double?, val danach: Double,
        val rueckgang: Double, val rueckgangZwischen: Double?, val schwaecher: Int, val negativ: Int,
    ) {
        val anteilSchwaecher: Double get() = schwaecher * 100.0 / anzahl
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun lesen(text: String): Datei? = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull()
        ?.takeIf { d -> d.signale.isNotEmpty() && d.signale.all { it.studie.isFinite() && it.danach.isFinite() && (it.zwischen?.isFinite() ?: true) } }

    // null ohne Signale oder wenn das Mittel der Studien nicht ueber null liegt (ein Rueckgang laesst sich dann nicht nennen)
    fun auswerten(signale: List<Signal>): Auswertung? {
        if (signale.isEmpty()) return null
        val studie = signale.map { it.studie }.average()
        val danach = signale.map { it.danach }.average()
        if (!(studie > 0)) return null
        val mit = signale.filter { it.zwischen != null }
        val zwischen = if (mit.isEmpty()) null else mit.mapNotNull { it.zwischen }.average()
        val studieMit = if (mit.isEmpty()) null else mit.map { it.studie }.average()
        return Auswertung(
            anzahl = signale.size, studie = studie, zwischen = zwischen, danach = danach,
            rueckgang = (1 - danach / studie) * 100,
            rueckgangZwischen = if (zwischen != null && studieMit != null && studieMit > 0) (1 - zwischen / studieMit) * 100 else null,
            schwaecher = signale.count { it.danach < it.studie }, negativ = signale.count { it.danach < 0 },
        )
    }

    // Kleinster und groesster Wert beider Achsen des Streubilds (Studie und danach), null eingeschlossen
    fun spanne(signale: List<Signal>): Pair<Double, Double> {
        val lo = minOf(0.0, signale.minOfOrNull { minOf(it.studie, it.danach) } ?: 0.0)
        val hi = maxOf(0.0, signale.maxOfOrNull { maxOf(it.studie, it.danach) } ?: 0.0)
        return lo to if (hi - lo < 1e-9) lo + 1 else hi
    }

    // Das Signal, dessen Punkt (studie, danach) dem Ort (x, y) in Achseneinheiten am naechsten liegt. breiteZuHoehe:
    // Seitenverhaeltnis der Zeichenflaeche, damit der Abstand so zaehlt, wie er auf dem Bildschirm aussieht.
    fun naechstes(signale: List<Signal>, x: Double, y: Double, breiteZuHoehe: Double = 1.0): Signal? =
        signale.minByOrNull { ((it.studie - x) * breiteZuHoehe).let { dx -> dx * dx } + (it.danach - y) * (it.danach - y) }

    val datei: Datei? by lazy { runCatching { lesen(de.troetenrepublik.app.market.Daten.text(DATEI)) }.getOrNull() }
}
