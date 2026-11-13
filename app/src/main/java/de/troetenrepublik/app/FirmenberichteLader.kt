package de.troetenrepublik.app

import de.troetenrepublik.app.market.FirmenAbruf
import de.troetenrepublik.app.market.FirmenSpeicher
import de.troetenrepublik.app.market.Firmenberichte
import de.troetenrepublik.app.market.Gleif
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.SecBericht
import de.troetenrepublik.app.market.SecInsider
import de.troetenrepublik.app.market.Xbrl
import de.troetenrepublik.app.plattform.SichereDatei
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

// [0.74] Anbindung der Firmenangaben aus Jahresberichten (SEC) und der Konzernmutter (GLEIF) an Netz, Einstellungen und
// Speicher der App. Die SEC bekommt die Kennung mit der Kontaktadresse unter Zugaenge, GLEIF keine.

private class NetzAbruf(private val api: MarketApi, private val kennung: () -> String?) : FirmenAbruf {
    override suspend fun sec(url: String): String? {
        val k = kennung() ?: return null
        return runCatching { api.anfrage(url, mapOf("User-Agent" to k, "Accept" to "application/json")).takeIf { it.ok }?.text }.getOrNull()
    }

    override suspend fun secStrom(url: String, lesen: (InputStream) -> Xbrl.Gelesen): Xbrl.Gelesen? {
        val k = kennung() ?: return null
        return runCatching { api.strom(url, mapOf("User-Agent" to k, "Accept" to "application/xml, text/xml, */*"), lesen) }.getOrNull()
    }

    // Jahresbericht (HTML): der HTTP-Weg fordert gzip an und entpackt selbst; der Abruf bricht ab, sobald Risk Factors beginnt
    override suspend fun secAbschnitt(url: String): String? {
        val k = kennung() ?: return null
        return runCatching { api.strom(url, mapOf("User-Agent" to k, "Accept" to "text/html, */*")) { SecBericht.lesenAbschnitt(it) } }.getOrNull()
    }

    override suspend fun gleif(url: String): String? =
        runCatching { api.anfrage(url, mapOf("Accept" to "application/vnd.api+json")).takeIf { it.ok }?.text }.getOrNull()
}

private class DateiSpeicher(private val ordner: File) : FirmenSpeicher {
    override fun lesen(name: String): String? = runCatching { SichereDatei(File(ordner, name)).lesen().toString(Charsets.UTF_8) }.getOrNull()

    override fun schreiben(name: String, text: String) {
        SichereDatei(File(ordner, name)).schreiben(text.toByteArray(Charsets.UTF_8))
    }
}

internal fun AppState.neueFirmenberichte(): Firmenberichte = Firmenberichte(
    NetzAbruf(MarketApi()) { SecInsider.kennung(settingsFlow.value.secKontakt) },
    DateiSpeicher(store.ordner),
    { System.currentTimeMillis() },
)

// Angaben des juengsten Jahresberichts; abgerufen wird nur, wenn der gespeicherte Stand aelter als eine Woche ist
suspend fun AppState.firmenbericht(symbol: String, name: String): Firmenberichte.Ergebnis = withContext(Dispatchers.IO) {
    val mitSec = SecInsider.kennung(settingsFlow.value.secKontakt) != null
    runCatching { firmenberichte.laden(symbol, name, mitSec, isinFor(symbol)) }.getOrDefault(Firmenberichte.Ergebnis(SecBericht.Status.FEHLER))
}

// Konzernmutter des Werts laut GLEIF, nur wenn sie vom Wert selbst verschieden ist; abgerufen hoechstens einmal im Monat
suspend fun AppState.konzernmutter(symbol: String): Gleif.Mutter? = withContext(Dispatchers.IO) {
    val isin = isinFor(symbol)
    if (isin.length != 12) null else runCatching { firmenberichte.mutter(symbol, isin) }.getOrNull()
}
