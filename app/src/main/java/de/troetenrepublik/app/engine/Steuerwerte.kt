package de.troetenrepublik.app.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.floor

// Steuerwerte je Jahr (steuerwerte.json, ueber das Datenbuendel nachlieferbar): Abgeltungsteuer, Solidaritaetszuschlag,
// Sparer-Pauschbetrag, Basiszins und fuer das Finanzwissen der Einkommensteuertarif mit Grundfreibetrag und die
// Soli-Freigrenzen. Dieselbe Datei liest die Web-App des Finanzwissens (finanzwissen/data/steuerwerte.json).
// Jedes Feld eines Jahres ist freiwillig: Fehlt es, gilt der Wert des juengsten Jahres davor, das das Feld hat (liegt keines
// davor, der des aeltesten Jahres mit dem Feld), und das Feld gilt fuer dieses Jahr als geschaetzt.
// Fassungen ab 0.73 lesen die Datei mit ignoreUnknownKeys und Standardwerten; deshalb tragen alle Jahre weiter abgeltung,
// soli, pauschbetrag und pauschbetragPaar.
object Steuerwerte {

    // Eine Zone des Tarifs nach § 32a EStG fuer zu versteuernde Einkommen bis bis (einschliesslich; fehlt in der letzten Zone).
    // Steuer = (a * y + b) * y + c + satz * x - abzug mit y = (x - ab) / 10.000, abgerundet auf volle Euro.
    @Serializable
    data class Zone(
        val bis: Double? = null,
        val ab: Double = 0.0,
        val a: Double = 0.0,
        val b: Double = 0.0,
        val c: Double = 0.0,
        val satz: Double = 0.0,
        val abzug: Double = 0.0,
    )

    @Serializable
    data class Jahr(
        @SerialName("abgeltung") val abgeltungFeld: Double? = null,
        @SerialName("soli") val soliFeld: Double? = null,
        @SerialName("pauschbetrag") val pauschbetragFeld: Double? = null,
        @SerialName("pauschbetragPaar") val pauschbetragPaarFeld: Double? = null,
        // Basiszins nach § 18 InvStG in Prozent; null, solange er nicht festgesetzt ist (nie 0 als Ersatz)
        val basiszins: Double? = null,
        val grundfreibetrag: Double? = null,
        val tarif: List<Zone>? = null,
        // Freigrenzen des Solidaritaetszuschlags auf die Einkommensteuer (Einzel- und Zusammenveranlagung)
        val soliFreigrenze: Double? = null,
        val soliFreigrenzePaar: Double? = null,
    ) {
        val abgeltung: Double get() = abgeltungFeld ?: 25.0
        val soli: Double get() = soliFeld ?: 5.5
        val pauschbetrag: Double get() = pauschbetragFeld ?: 1_000.0
        val pauschbetragPaar: Double get() = pauschbetragPaarFeld ?: 2_000.0

        // Satz einschliesslich Soli, als Anteil (0,26375)
        val satz: Double get() = abgeltung / 100.0 * (1 + soli / 100.0)
    }

    @Serializable
    data class Datei(val v: Int = 1, val stand: String = "", val jahre: Map<String, Jahr> = emptyMap())

    private fun basis(basiszins: Double) = Jahr(abgeltungFeld = 25.0, soliFeld = 5.5, pauschbetragFeld = 1_000.0, pauschbetragPaarFeld = 2_000.0, basiszins = basiszins)

    // Grundtarif 2026 (§ 32a Abs. 1 EStG)
    private val TARIF_2026 = listOf(
        Zone(bis = 12_348.0),
        Zone(bis = 17_799.0, ab = 12_348.0, a = 914.51, b = 1_400.0),
        Zone(bis = 69_878.0, ab = 17_799.0, a = 173.10, b = 2_397.0, c = 1_034.87),
        Zone(bis = 277_825.0, satz = 0.42, abzug = 11_135.63),
        Zone(satz = 0.45, abzug = 19_470.38),
    )

    // Eingebaut: die Werte bei Veroeffentlichung dieser Fassung; die Datei ersetzt sie.
    val EINGEBAUT = Datei(
        v = 1, stand = "2026-11-10",
        jahre = mapOf(
            "2023" to basis(2.55), "2024" to basis(2.29), "2025" to basis(2.53),
            "2026" to basis(3.20).copy(grundfreibetrag = 12_348.0, tarif = TARIF_2026, soliFreigrenze = 20_350.0, soliFreigrenzePaar = 40_700.0),
        ),
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun Double?.inBereich(r: ClosedFloatingPointRange<Double>) = this == null || (isFinite() && this in r)

    // Tarif: mindestens eine Zone, alle ausser der letzten mit aufsteigender Obergrenze, die letzte ohne, Saetze zwischen 0 und 1.
    internal fun tarifGueltig(t: List<Zone>): Boolean {
        if (t.isEmpty() || t.last().bis != null) return false
        val grenzen = t.dropLast(1).map { it.bis ?: return false }
        if (grenzen.zipWithNext().any { (x, y) -> y <= x } || grenzen.any { !it.isFinite() || it < 0 }) return false
        return t.all { z -> listOf(z.ab, z.a, z.b, z.c, z.abzug).all { it.isFinite() } && z.satz in 0.0..1.0 }
    }

    private fun gueltig(j: Jahr): Boolean =
        j.abgeltungFeld.inBereich(0.0..60.0) && j.soliFeld.inBereich(0.0..20.0) && j.pauschbetragFeld.inBereich(0.0..1e6) &&
            j.pauschbetragPaarFeld.inBereich(0.0..1e6) && j.basiszins.inBereich(-5.0..20.0) && j.grundfreibetrag.inBereich(0.0..1e6) &&
            j.soliFreigrenze.inBereich(0.0..1e7) && j.soliFreigrenzePaar.inBereich(0.0..1e7) && (j.tarif?.let(::tarifGueltig) ?: true)

    fun lesen(text: String): Datei? = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull()
        ?.takeIf { d -> d.jahre.isNotEmpty() && d.jahre.keys.all { it.toIntOrNull() != null } && d.jahre.values.all(::gueltig) }

    @Volatile private var datei: Datei? = null
    private val geladen: Datei by lazy {
        runCatching { lesen(de.troetenrepublik.app.market.Daten.text(DATEI)) }.getOrNull() ?: EINGEBAUT
    }

    const val DATEI = "steuerwerte.json"

    // Pfad, unter dem die Web-App des Finanzwissens die Datei holt (relativ zum Ordner assets/); die Plattformen fangen ihn ab.
    const val WEB_PFAD = "finanzwissen/data/steuerwerte.json"

    // Inhalt fuer die Web-App: der gepruefte Stand vom Geraet, sonst die eingebaute Datei; null, wenn beides fehlt.
    fun fuerWebansicht(): ByteArray? = runCatching { de.troetenrepublik.app.market.Daten.bytes(DATEI) }.getOrNull()

    val aktiv: Datei get() = datei ?: geladen

    // Nur fuer Tests: Datei setzen (null: die gebuendelte)
    internal fun setzen(d: Datei?) { datei = d }

    // Jahr, aus dem ein Feld fuer dieses Jahr kommt: das Jahr selbst, sonst das juengste davor mit dem Feld, sonst das
    // aelteste mit dem Feld; null, wenn kein Jahr das Feld hat.
    fun feldJahr(jahr: Int, d: Datei = aktiv, feld: (Jahr) -> Any?): Int? {
        val mit = d.jahre.mapNotNull { (k, v) -> k.toIntOrNull()?.takeIf { feld(v) != null } }.sorted()
        return mit.lastOrNull { it <= jahr } ?: mit.firstOrNull()
    }

    // Werte fuer ein Jahr, Feld fuer Feld nach feldJahr zusammengesetzt.
    fun fuer(jahr: Int, d: Datei = aktiv): Jahr {
        fun <T> nimm(feld: (Jahr) -> T?): T? = feldJahr(jahr, d, feld)?.let { feld(d.jahre.getValue(it.toString())) }
        return Jahr(
            abgeltungFeld = nimm { it.abgeltungFeld }, soliFeld = nimm { it.soliFeld },
            pauschbetragFeld = nimm { it.pauschbetragFeld }, pauschbetragPaarFeld = nimm { it.pauschbetragPaarFeld },
            basiszins = nimm { it.basiszins }, grundfreibetrag = nimm { it.grundfreibetrag }, tarif = nimm { it.tarif },
            soliFreigrenze = nimm { it.soliFreigrenze }, soliFreigrenzePaar = nimm { it.soliFreigrenzePaar },
        )
    }

    // Juengstes Jahr mit eigenem Eintrag
    fun letztesJahr(d: Datei = aktiv): Int = d.jahre.keys.maxOf { it.toInt() }

    // Rechnet die App fuer dieses Jahr mit den Werten eines frueheren Jahres?
    fun geschaetzt(jahr: Int, d: Datei = aktiv): Boolean = !d.jahre.containsKey(jahr.toString())

    // Kommt ein einzelnes Feld fuer dieses Jahr aus einem anderen Jahr (oder fehlt ganz)?
    fun geschaetzt(jahr: Int, d: Datei = aktiv, feld: (Jahr) -> Any?): Boolean = feldJahr(jahr, d, feld) != jahr

    // Werte des laufenden Jahres (Uhr)
    fun jetzt(): Jahr = fuer(Uhr.heute().year)

    // Einkommensteuer nach einem Tarif (zu versteuerndes Einkommen auf volle Euro abgerundet).
    fun einkommensteuer(zve: Double, tarif: List<Zone>): Double {
        val x = floor(zve)
        val z = tarif.firstOrNull { it.bis == null || x <= it.bis } ?: tarif.last()
        var s = 0.0
        if (z.a != 0.0 || z.b != 0.0) {
            val y = (x - z.ab) / 10_000.0
            s = (z.a * y + z.b) * y
        }
        s += z.c
        if (z.satz != 0.0) s += z.satz * x - z.abzug
        return floor(s)
    }
}
