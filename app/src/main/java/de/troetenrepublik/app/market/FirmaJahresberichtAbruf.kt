package de.troetenrepublik.app.market

import de.troetenrepublik.app.plattform.SichereDatei
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

// Abruf und Speicher der Angaben aus dem juengsten Jahresbericht (Pruefer, Zweifel, Kontrollschwaeche, CAM, Verfahren).
// Das Hauptdokument wird nur gelesen, wenn zur Firma ein neuer Bericht vorliegt (ein Abruf der Einreichungsliste prueft
// das); gelesen wird im Strom, ohne es abzulegen, gespeichert wird nur das kleine Ergebnis je Firma.
object JahresberichtAbruf {
    class Ergebnis(
        val cik: String, val acc: String, val form: String, val eingang: String, val stichtag: String, val datei: String,
        // null: Dokument zu gross oder nicht lesbar
        val befund: Jahresbericht.Befund?,
    ) {
        // Adresse des Dokuments bei der SEC
        val adresse: String get() = SecInsider.archivAdresse(cik, acc, datei)
    }

    @Serializable
    private class Stand(
        val acc: String = "", val form: String = "", val eingang: String = "", val stichtag: String = "", val datei: String = "",
        val lesestand: Int = 0, val befund: Jahresbericht.Befund? = null,
    )

    @Serializable
    private class Datei(val werte: Map<String, Stand> = emptyMap())

    // Mit jeder Aenderung an den Leseregeln hochzaehlen, dann wird neu gelesen
    private const val LESE_STAND = 1
    private const val MAX_WERTE = 150
    private const val GUELTIG_MS = 12 * 3_600_000L
    // Die Groesse der Liste zaehlt die ganze Einreichung; das Dokument selbst begrenzt das Lesen (Jahresbericht.MAX_ZEICHEN)
    private const val MAX_DOKUMENT = 400_000_000L

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    private val sperre = Mutex()
    private val speicher = ConcurrentHashMap<String, Pair<Long, Ergebnis>>()
    private val stand = HashMap<String, Stand>()
    private var gelesen = false

    fun gemerkt(cik: String): Ergebnis? = speicher[cik]?.second

    private fun lesenVonDatei(datei: SichereDatei?) {
        if (gelesen || datei == null) return
        gelesen = true
        val d = runCatching { json.decodeFromString(Datei.serializer(), datei.lesen().decodeToString()) }.getOrNull() ?: return
        stand.putAll(d.werte.filterValues { it.lesestand == LESE_STAND })
    }

    private fun schreiben(datei: SichereDatei?) {
        if (datei == null) return
        while (stand.size > MAX_WERTE) stand.remove(stand.keys.first())
        runCatching { datei.schreiben(json.encodeToString(Datei.serializer(), Datei(stand.toMap())).encodeToByteArray()) }
    }

    // null: kein Jahresbericht oder Abruf gescheitert (nichts gemerkt). heute: laufendes Jahr.
    suspend fun laden(cik: String, kennung: String, heute: Int, jetzt: Long = System.currentTimeMillis(), datei: SichereDatei? = null): Ergebnis? {
        speicher[cik]?.let { if (jetzt - it.first < GUELTIG_MS) return it.second }
        return sperre.withLock {
            speicher[cik]?.let { if (jetzt - it.first < GUELTIG_MS) return@withLock it.second }
            lesenVonDatei(datei)
            try {
                val liste = FirmaSec.anfrage(SecBericht.submissionsAdresse(cik), kennung, max = 6_000_000)
                if (!liste.ok) return@withLock null
                val text = liste.text.orEmpty()
                val jb = SecBericht.juengsterJahresbericht(text) ?: return@withLock null
                val dok = SecBericht.hauptdokument(text, jb.acc) ?: return@withLock null
                val alt = stand[cik]
                val befund: Jahresbericht.Befund? = if (alt != null && alt.acc == jb.acc) alt.befund
                else if (dok.groesse > MAX_DOKUMENT) null
                else {
                    val url = SecBericht.archivAdresse(cik, jb.acc, dok.datei)
                    val t = try {
                        FirmaSec.strom(url, kennung) { Jahresbericht.klartext(it) } ?: return@withLock null
                    } catch (_: Jahresbericht.ZuGross) { null }
                    t?.let { Jahresbericht.auswerten(it, heute) }
                }
                stand[cik] = Stand(jb.acc, jb.form, jb.eingang, jb.stichtag, dok.datei, LESE_STAND, befund)
                schreiben(datei)
                val e = Ergebnis(cik, jb.acc, jb.form, jb.eingang, jb.stichtag, dok.datei, befund)
                speicher[cik] = jetzt to e
                e
            } catch (_: FirmaSec.Gesperrt) {
                null
            }
        }
    }
}
