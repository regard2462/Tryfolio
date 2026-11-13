package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Rohstoffprofil
import de.troetenrepublik.app.market.SecBericht.Status
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream

// Abruf und Speicher der Firmenangaben aus den Jahresberichten der SEC (Segmente, Regionen, Grosskunden) und der
// Konzernmutter aus GLEIF. Abgerufen wird nur, wenn die Karte geoeffnet wird; das Ergebnis liegt je Wert auf dem Geraet.
// Die SEC wird hoechstens einmal je Woche gefragt und der Bericht nur gelesen, wenn ein neuer vorliegt; die Mutter wird
// hoechstens monatlich erneuert. Netz, Speicher, Uhr und Pause sind einspeisbar.
interface FirmenAbruf {
    // Antworttext der SEC (Kennung mit Kontakt gehoert in die Umsetzung); null bei Fehler
    suspend fun sec(url: String): String?

    // Antwort der SEC als Strom; null bei Fehler
    suspend fun secStrom(url: String, lesen: (InputStream) -> Xbrl.Gelesen): Xbrl.Gelesen?

    // Jahresbericht (HTML) als Text, nur bis zum Abschnitt Risk Factors und hoechstens SecBericht.MAX_TEXT_BYTES entpackt;
    // null bei Fehler. Ohne eigene Umsetzung gilt der ganze Text der Antwort.
    suspend fun secAbschnitt(url: String): String? = sec(url)

    // Antworttext von GLEIF ohne Kennung; null bei Fehler
    suspend fun gleif(url: String): String?
}

interface FirmenSpeicher {
    fun lesen(name: String): String?
    fun schreiben(name: String, text: String)
}

class Firmenberichte(
    private val abruf: FirmenAbruf,
    private val speicher: FirmenSpeicher,
    private val jetzt: () -> Long,
    private val warte: suspend (Long) -> Unit = { delay(it) },
) {
    companion object {
        const val WOCHE_MS = 7 * 86_400_000L
        const val MONAT_MS = 30 * 86_400_000L
        const val SEC_DATEI = "sec_bericht.json"
        const val GLEIF_DATEI = "gleif.json"
        // Hoechstens zwei Abrufe je Sekunde bei der SEC
        const val PAUSE_MS = 500L
        private const val MAX_WERTE = 300
    }

    // sd und roh: Angaben zu Rohstoffen aus den Einreichungen (siehe SecBericht.Eintrag)
    class Ergebnis(val status: Status, val bericht: SecBericht.Auswertung? = null, val sd: String = "", val roh: List<String> = emptyList())

    private val sperre = Mutex()
    private var sec: MutableMap<String, SecBericht.Eintrag>? = null
    private var gleif: MutableMap<String, Gleif.Eintrag>? = null
    private var kuerzel: String? = null
    private var abrufe = 0

    private suspend fun secText(url: String): String? {
        if (abrufe++ > 0) warte(PAUSE_MS)
        return abruf.sec(url)
    }

    private fun secStand(): MutableMap<String, SecBericht.Eintrag> =
        sec ?: (speicher.lesen(SEC_DATEI)?.let { SecBericht.lesen(it) }?.werte?.toMutableMap() ?: HashMap()).also { sec = it }

    private fun secSpeichern(m: MutableMap<String, SecBericht.Eintrag>) {
        if (m.size > MAX_WERTE) m.entries.sortedBy { it.value.abgerufen }.take(m.size - MAX_WERTE).forEach { m.remove(it.key) }
        runCatching { speicher.schreiben(SEC_DATEI, SecBericht.schreiben(SecBericht.Datei(m))) }
    }

    private fun ergebnis(e: SecBericht.Eintrag) = Ergebnis(Status.entries.getOrElse(e.status) { Status.FEHLER }, e.bericht, e.sd, e.roh)

    // Was ohne Abruf bekannt ist; null ohne gespeicherten Stand
    suspend fun gespeichert(symbol: String): Ergebnis? = sperre.withLock { secStand()[symbol]?.let { ergebnis(it) } }

    // Gespeicherte Einreichung (CIK, Archivnummer) des juengsten Jahresberichts; null ohne gespeicherten Stand (Verflechtungen)
    suspend fun einreichung(symbol: String): Pair<String, String>? =
        sperre.withLock { secStand()[symbol]?.takeIf { it.cik.isNotBlank() && it.acc.isNotBlank() }?.let { it.cik to it.acc } }

    // Auswertung des juengsten Jahresberichts. mitSec false: keine Kontaktadresse, dann nur der gespeicherte Stand.
    suspend fun laden(symbol: String, name: String, mitSec: Boolean, isin: String = ""): Ergebnis = sperre.withLock {
        val stand = secStand()
        val alt = stand[symbol]
        val t = jetzt()
        if (alt != null && t - alt.abgerufen < WOCHE_MS) return@withLock ergebnis(alt)
        if (!mitSec) return@withLock alt?.let { ergebnis(it) } ?: Ergebnis(Status.OHNE_KONTAKT)
        abrufe = 0

        var cik = alt?.cik.orEmpty()
        if (cik.isEmpty()) {
            val liste = kuerzel ?: secText(SecFormen.KUERZEL)?.also { kuerzel = it } ?: return@withLock alt?.let { ergebnis(it) } ?: Ergebnis(Status.FEHLER)
            cik = SecBericht.cikAusListe(liste, symbol, name).orEmpty()
            // Ist die Boersennotiz eine Tochter, zaehlt der Bericht der Konzernmutter (GLEIF) mit gleichem Namenstest
            if (cik.isEmpty() && isin.length == 12) {
                mutterOhneSperre(symbol, isin)?.let { cik = SecBericht.cikAusListe(liste, "", it.name).orEmpty() }
            }
            if (cik.isEmpty()) {
                val e = SecBericht.Eintrag(abgerufen = t, status = Status.KEIN_SEC.ordinal)
                stand[symbol] = e; secSpeichern(stand)
                return@withLock ergebnis(e)
            }
        }
        val subs = secText(SecBericht.submissionsAdresse(cik)) ?: return@withLock alt?.let { ergebnis(it) } ?: Ergebnis(Status.FEHLER)
        val neu = SecBericht.juengsterJahresbericht(subs)
        val sd = SecBericht.sdDatum(subs, 1970 + (t / 31_556_952_000L).toInt())
        if (neu == null) {
            val e = SecBericht.Eintrag(abgerufen = t, cik = cik, status = Status.KEIN_BERICHT.ordinal, sd = sd)
            stand[symbol] = e; secSpeichern(stand)
            return@withLock ergebnis(e)
        }
        // Derselbe Bericht wie beim letzten Mal: nur die Nachfrage vermerken
        // (Rohstoffabschnitt nur lesen, wenn er fuer diese Einreichung noch nicht gelesen ist)
        val roh = if (alt != null && alt.rohAcc == neu.acc) alt.roh else rohstoffeAusBericht(cik, subs, neu.acc)
        if (alt != null && alt.acc == neu.acc && alt.status != Status.FEHLER.ordinal) {
            val e = SecBericht.Eintrag(t, cik, alt.acc, alt.status, alt.bericht, alt.eingang, sd, roh, neu.acc)
            stand[symbol] = e; secSpeichern(stand)
            return@withLock ergebnis(e)
        }
        fun ablegen(status: Status, b: SecBericht.Auswertung? = null): Ergebnis {
            val e = SecBericht.Eintrag(t, cik, neu.acc, status.ordinal, b, neu.eingang, sd, roh, neu.acc)
            stand[symbol] = e; secSpeichern(stand)
            return ergebnis(e)
        }
        val index = secText(SecBericht.verzeichnisAdresse(cik, neu.acc)) ?: return@withLock alt?.let { ergebnis(it) } ?: Ergebnis(Status.FEHLER)
        val inst = SecBericht.instanzDatei(index) ?: return@withLock ablegen(Status.KEIN_BERICHT)
        if (inst.groesse > SecBericht.MAX_BYTES) return@withLock ablegen(Status.ZU_GROSS)
        if (abrufe++ > 0) warte(PAUSE_MS)
        val gelesen = abruf.secStrom(SecBericht.archivAdresse(cik, neu.acc, inst.datei)) { Xbrl.lesenGrenze(it, SecBericht.MAX_BYTES) }
            ?: return@withLock alt?.let { ergebnis(it) } ?: Ergebnis(Status.FEHLER)
        when (gelesen) {
            is Xbrl.Gelesen.Gross -> ablegen(Status.ZU_GROSS)
            is Xbrl.Gelesen.Ok -> {
                val b = runCatching { Xbrl.auswerten(gelesen.roh) }.getOrNull()
                if (b == null) ablegen(Status.NICHT_LESBAR) else ablegen(Status.OK, b)
            }
        }
    }

    // [0.76] Rohstoffe, die der Rohstoffabschnitt des Jahresberichts nennt (Text des Hauptdokuments, nur bis Risk Factors und MAX_TEXT_BYTES; die Groesse in der Einreichungsliste ist die der ganzen
    // Einreichung und sagt nichts ueber das Hauptdokument);
    // leer bei Fehler
    private suspend fun rohstoffeAusBericht(cik: String, subs: String, acc: String): List<String> {
        val d = SecBericht.hauptdokument(subs, acc) ?: return emptyList()
        if (abrufe++ > 0) warte(PAUSE_MS)
        val text = abruf.secAbschnitt(SecBericht.archivAdresse(cik, acc, d.datei)) ?: return emptyList()
        return runCatching { Rohstoffprofil.ausJahresbericht(text) }.getOrDefault(emptyList())
    }

    private fun gleifStand(): MutableMap<String, Gleif.Eintrag> =
        gleif ?: (speicher.lesen(GLEIF_DATEI)?.let { Gleif.lesen(it) }?.werte?.toMutableMap() ?: HashMap()).also { gleif = it }

    // Konzernmutter laut GLEIF; null ohne gemeldete Mutter, ohne ISIN oder bei Fehler ohne gespeicherten Stand
    suspend fun mutter(symbol: String, isin: String): Gleif.Mutter? = sperre.withLock { mutterOhneSperre(symbol, isin) }

    private suspend fun mutterOhneSperre(symbol: String, isin: String): Gleif.Mutter? {
        if (isin.length != 12) return null
        val stand = gleifStand()
        val alt = stand[symbol]?.takeIf { it.isin == isin }
        val t = jetzt()
        if (alt != null && t - alt.abgerufen < MONAT_MS) return alt.mutter
        fun ablegen(m: Gleif.Mutter?): Gleif.Mutter? {
            stand[symbol] = Gleif.Eintrag(t, isin, m)
            if (stand.size > MAX_WERTE) stand.entries.sortedBy { it.value.abgerufen }.take(stand.size - MAX_WERTE).forEach { stand.remove(it.key) }
            runCatching { speicher.schreiben(GLEIF_DATEI, Gleif.schreiben(Gleif.Datei(stand))) }
            return m
        }
        val antwort = abruf.gleif(Gleif.leiSuche(isin)) ?: return alt?.mutter
        val eigen = Gleif.satzLesen(antwort) ?: return ablegen(null)
        val link = eigen.oberste ?: eigen.direkt ?: return ablegen(null)
        val muttertext = abruf.gleif(link) ?: return alt?.mutter
        return ablegen(Gleif.mutterVon(eigen, Gleif.satzLesen(muttertext)))
    }
}
