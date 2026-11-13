package de.troetenrepublik.app

import de.troetenrepublik.app.engine.BrokerExport
import de.troetenrepublik.app.engine.FlexAbgleich
import de.troetenrepublik.app.engine.FlexAbruf
import de.troetenrepublik.app.engine.FlexBericht
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.engine.TradeRepublicImport
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.plattform.Plattform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

// Abgleich des Depots mit dem Flex-Bericht von Interactive Brokers (0.78): Bericht holen, Papiere zuordnen, Vorschlag
// rechnen und zeigen. Gebucht wird erst nach der Bestaetigung (uebernehmen). Token und Abfragenummer kommen aus den
// Einstellungen (Tresor), gehen nur an den Rechner des Brokers und stehen in keiner Meldung.
class FlexLauf(private val state: AppState, private val scope: CoroutineScope) {

    sealed interface Stand {
        data object Ruht : Stand
        data object Laedt : Stand
        // Token oder Abfragenummer fehlen.
        data object OhneZugang : Stand
        data class Meldung(val text: String) : Stand
        // hinweis: Zeile ueber der Vorschau, etwa nach einer Aenderung des Depots.
        data class Vorschau(val plan: FlexAbgleich.Plan, val hinweis: String = "") : Stand
    }

    private val _stand = MutableStateFlow<Stand>(Stand.Ruht)
    val stand: StateFlow<Stand> = _stand

    private val netz by lazy { MarketApi() }
    private val abruf by lazy {
        FlexAbruf(
            holen = { url -> netz.bytes(url, mapOf("Accept" to "application/xml, text/xml, */*"), max = MAX_BYTES)?.decodeToString() },
            uhr = { System.currentTimeMillis() },
            warten = { delay(it) },
        )
    }

    private var job: Job? = null
    // Bericht und Zuordnung der gezeigten Vorschau, um sie vor dem Buchen auf dem aktuellen Depot neu zu rechnen
    private var gelesen: Pair<FlexBericht.Bericht, Map<String, String>>? = null

    // still: Abgleich beim Start; zeigt nur etwas, wenn der Bericht vom Depot abweicht.
    fun starten(still: Boolean = false) {
        if (job?.isActive == true) return
        job = scope.launch {
            val s = state.settings.flow.first()
            if (s.flexToken.isBlank() || s.flexAbfrage.isBlank()) { if (!still) _stand.value = Stand.OhneZugang; return@launch }
            if (!still) _stand.value = Stand.Laedt
            val ergebnis = runCatching { lesen(s.flexToken, s.flexAbfrage) }.getOrNull()
            ensureActive()
            // Abgebrochen (schliessen) oder beim Start ohne Ergebnis: nichts zeigen
            if (still && ergebnis !is Stand.Vorschau) return@launch
            if (still && (ergebnis as Stand.Vorschau).plan.let { it.leer && it.offen.isEmpty() }) return@launch
            _stand.value = ergebnis ?: Stand.Meldung(S.flex("f.unlesbar"))
        }
    }

    private suspend fun lesen(token: String, abfrage: String): Stand {
        val xml = when (val e = abruf.bericht(token, abfrage)) {
            is FlexAbruf.Ergebnis.Ok -> e.xml
            is FlexAbruf.Ergebnis.Fehler -> return Stand.Meldung(S.flex("f." + e.art.name, listOf(e.code.toString())))
            FlexAbruf.Ergebnis.KeineAntwort -> return Stand.Meldung(S.flex("f.netz"))
            FlexAbruf.Ergebnis.Eingabe -> return Stand.Meldung(S.flex("f.eingabe"))
        }
        val bericht = withContext(Dispatchers.Default) { FlexBericht.lesen(xml) } ?: return Stand.Meldung(S.flex("f.unlesbar"))
        val kuerzel = zuordnen(FlexAbgleich.gesucht(bericht))
        gelesen = bericht to kuerzel
        return Stand.Vorschau(withContext(Dispatchers.Default) { FlexAbgleich.plan(bericht, kuerzel, state.account.value, System.currentTimeMillis()) })
    }

    // Kuerzel der App je Papier: ISIN ueber den Katalog, sonst ueber die Suche; Krypto als Paar gegen Euro. Ohne ISIN
    // wird nichts zugeordnet, weil dasselbe Boersenkuerzel an anderen Plaetzen ein anderes Papier meint.
    private suspend fun zuordnen(gesucht: List<FlexAbgleich.Gesucht>): Map<String, String> {
        val katalog = Symbols.all.filter { it.isin.isNotBlank() }.associate { it.isin to it.symbol }
        val gleichzeitig = Semaphore(6)
        return coroutineScope {
            gesucht.map { g ->
                async {
                    gleichzeitig.withPermit {
                        if (g.klasse == "CRYPTO") {
                            val k = TradeRepublicImport.kryptoKuerzel(g.schluessel)
                            "$k-EUR".takeIf { c -> runCatching { state.market.snapshot(listOf(c))[c]?.price ?: 0.0 }.getOrDefault(0.0) > 0 }?.let { k to it }
                        } else if (BrokerExport.istIsin(g.schluessel)) {
                            (katalog[g.schluessel] ?: runCatching { state.symbolFuerIsin(g.schluessel) }.getOrNull())?.let { g.schluessel to it }
                        } else null
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
    }

    // Vorschlag buchen. Hat sich das Depot seit der Vorschau so geaendert, dass der Vorschlag anders ausfaellt, erscheint
    // die neue Vorschau statt einer Buchung.
    fun uebernehmen() {
        val v = _stand.value as? Stand.Vorschau ?: return
        val (bericht, kuerzel) = gelesen ?: return
        val neu = runCatching { FlexAbgleich.plan(bericht, kuerzel, state.account.value, System.currentTimeMillis()) }.getOrNull()
        if (neu == null) { _stand.value = Stand.Meldung(S.flex("f.unlesbar")); return }
        if (neu.unterschiede != v.plan.unterschiede || neu.buchungen != v.plan.buchungen || neu.einzahlung != v.plan.einzahlung) {
            _stand.value = Stand.Vorschau(neu, S.flex("depotGeaendert"))
            return
        }
        if (neu.leer) { schliessen(); return }
        if (state.abgleichUebernehmen(neu.konto)) state.showToast(S.flex("fertig"))
        schliessen()
    }

    fun schliessen() {
        job?.cancel()
        gelesen = null
        _stand.value = Stand.Ruht
    }

    // Beim Start, wenn eingeschaltet: hoechstens alle sechs Stunden und nie bei gesperrter App.
    suspend fun beimStart() {
        if (!Plattform.kursabruf || state.store.gesperrt.value) return
        val s = state.settings.flow.first()
        if (!s.flexBeimStart || s.flexToken.isBlank() || s.flexAbfrage.isBlank()) return
        val jetzt = System.currentTimeMillis()
        if (jetzt - s.flexZuletzt in 0 until START_ABSTAND_MS) return
        state.settings.update { it.copy(flexZuletzt = jetzt) }
        starten(still = true)
    }

    private companion object {
        const val MAX_BYTES = 16_000_000
        const val START_ABSTAND_MS = 6 * 3_600_000L
    }
}
