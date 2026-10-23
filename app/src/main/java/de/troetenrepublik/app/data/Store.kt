package de.troetenrepublik.app.data

import de.troetenrepublik.app.engine.Trading
import de.troetenrepublik.app.plattform.SichereDatei
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

// Aenderungen und Schreibvorgaenge laufen nacheinander, damit ein aelterer Stand keinen neueren Trade ueberschreibt.
// Neben dem Konto liegen die Unternehmensstammdaten in einer eigenen Datei.
// Mit App-Sperre liegt das Konto mit AES-256-GCM verschluesselt in account.json. Bis zum Entsperren zeigt der
// Speicher ein leeres Konto und schreibt nichts, damit die verschluesselte Datei nie durch ein leeres ersetzt wird.
class Store(dir: File, scope: CoroutineScope) {
    val ordner: File = dir
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private val file = SichereDatei(File(dir, "account.json"))
    private val legacyBackup = File(dir, "account.bak.json")
    // Altes Zeitreise-Depot, wird beim Start entfernt.
    private val legacyTimeTravel = File(dir, "timetravel.json")
    private val companiesFile = SichereDatei(File(dir, "companies.json"))
    private val mutationLock = Any()
    private val writes = Channel<Unit>(Channel.CONFLATED)
    private val schreibLock = Mutex()

    // Datenschluessel der App-Sperre, solange entsperrt; null ohne Sperre.
    @Volatile private var schluessel: ByteArray? = null
    private val _gesperrt = MutableStateFlow(false)

    // Die Datei ist verschluesselt und noch nicht entsperrt.
    val gesperrt: StateFlow<Boolean> = _gesperrt.asStateFlow()

    val sperre = Sperre(dir)
    private val companyWrites = Channel<Map<String, CompanyInfo>>(Channel.UNLIMITED)
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _account = MutableStateFlow(load())
    val account: StateFlow<Account> = _account.asStateFlow()
    private val _companies = MutableStateFlow(loadCompanies())
    val companies: StateFlow<Map<String, CompanyInfo>> = _companies.asStateFlow()

    private val companySerializer = MapSerializer(String.serializer(), CompanyInfo.serializer())

    init {
        scope.launch(Dispatchers.IO) {
            for (signal in writes) schreibLock.withLock { schreibenJetzt() }
        }
        scope.launch(Dispatchers.IO) { runCatching { if (legacyTimeTravel.exists()) legacyTimeTravel.delete() } }
        scope.launch(Dispatchers.IO) {
            for (map in companyWrites) {
                runCatching {
                    // Noch gesperrt (verschluesselte Datei ohne Schluessel): nichts ueberschreiben
                    if (!gesperrtFuer(runCatching { companiesFile.lesen() }.getOrNull())) {
                        companiesFile.schreiben(verpacken(json.encodeToString(companySerializer, map).toByteArray(Charsets.UTF_8)))
                    }
                }
            }
        }
    }

    // Den neuesten Stand schreiben (nur unter schreibLock); verschluesselt, wenn die Sperre eingerichtet ist.
    private fun schreibenJetzt() {
        if (_gesperrt.value) return
        try {
            val klar = json.encodeToString(Account.serializer(), _account.value).toByteArray(Charsets.UTF_8)
            val k = schluessel
            file.schreiben(if (k == null) klar else KENNUNG + de.troetenrepublik.app.plattform.Krypto.verschluesseln(k, klar))
            _error.value = null
        } catch (e: Exception) {
            _error.value = "SAVE_FAILED"
        }
    }

    private fun verschluesselt(b: ByteArray): Boolean = b.size >= KENNUNG.size && KENNUNG.indices.all { b[it] == KENNUNG[it] }

    // Weitere Dateien im Datenordner (Rueckblicke, Firmen), verschluesselt wie account.json, sobald die Sperre besteht.
    fun verpacken(klar: ByteArray): ByteArray {
        val k = schluessel ?: return klar
        return KENNUNG + de.troetenrepublik.app.plattform.Krypto.verschluesseln(k, klar)
    }

    // Gegenstueck zu verpacken; null, wenn die Datei verschluesselt ist und der Schluessel fehlt (noch gesperrt).
    fun entpacken(roh: ByteArray): ByteArray? {
        if (!verschluesselt(roh)) return roh
        val k = schluessel ?: return null
        return de.troetenrepublik.app.plattform.Krypto.entschluesseln(k, roh.copyOfRange(KENNUNG.size, roh.size))
    }

    // Die Datei ist verschluesselt und der Schluessel fehlt noch: dann darf sie nicht ueberschrieben werden.
    fun gesperrtFuer(roh: ByteArray?): Boolean = roh != null && verschluesselt(roh) && schluessel == null

    private fun load(): Account {
        val roh = runCatching { file.lesen() }.getOrNull()
        if (roh != null && verschluesselt(roh)) {
            // Verschluesselt: bis zum Entsperren ein leeres Konto, geschrieben wird nichts
            _gesperrt.value = true
            return Account()
        }
        if (roh != null) {
            runCatching { json.decodeFromString<Account>(roh.toString(Charsets.UTF_8)) }.getOrNull()?.let { return it }
            // Unlesbar (aus einer neueren Fassung oder beschaedigt): Kopie behalten und melden, nicht ueberschreiben
            runCatching { File(ordner, "account.unlesbar-${System.currentTimeMillis()}.json").writeBytes(roh) }
            _error.value = "LOAD_FAILED"
        }
        runCatching { json.decodeFromString<Account>(legacyBackup.readText()) }
            .getOrNull()?.let { return it }
        // Neue Installation: leeres Depot, leere Watchlist
        return Trading.fresh()
    }

    private fun loadCompanies(): Map<String, CompanyInfo> =
        runCatching { json.decodeFromString(companySerializer, entpacken(companiesFile.lesen())!!.toString(Charsets.UTF_8)) }.getOrDefault(emptyMap())

    fun update(block: (Account) -> Account) = synchronized(mutationLock) {
        if (_gesperrt.value) return@synchronized
        val current = _account.value
        val next = block(current)
        if (next != current) {
            _account.value = next
            check(writes.trySend(Unit).isSuccess) { "Persistence queue unavailable" }
        }
    }

    // Datenschluessel der App-Sperre (zum Neuverpacken bei geaenderter PIN); null ohne Sperre oder gesperrt.
    val datenschluessel: ByteArray? get() = schluessel

    // Mit dem Datenschluessel k entschluesseln und laden; false, wenn er nicht passt.
    // Eine noch unverschluesselte Datei wird verschluesselt.
    suspend fun entsperren(k: ByteArray): Boolean = withContext(Dispatchers.IO) {
        schreibLock.withLock {
            if (!_gesperrt.value) {
                schluessel = k
                schreibenJetzt()
                return@withLock true
            }
            val roh = runCatching { file.lesen() }.getOrNull() ?: return@withLock false
            val klar = de.troetenrepublik.app.plattform.Krypto.entschluesseln(k, roh.copyOfRange(KENNUNG.size, roh.size)) ?: return@withLock false
            val konto = runCatching { json.decodeFromString<Account>(klar.toString(Charsets.UTF_8)) }.getOrNull() ?: return@withLock false
            synchronized(mutationLock) {
                schluessel = k
                _account.value = konto
                _gesperrt.value = false
            }
            _companies.value = loadCompanies()
            true
        }
    }

    // Ab jetzt mit k verschluesselt speichern (Sperre eingerichtet); sofort geschrieben.
    suspend fun schuetzen(k: ByteArray) = withContext(Dispatchers.IO) {
        schreibLock.withLock {
            schluessel = k
            schreibenJetzt()
            companyWrites.trySend(_companies.value)
            runCatching { if (legacyBackup.exists()) legacyBackup.delete() }
        }
    }

    // Ab jetzt unverschluesselt speichern (Sperre ausgeschaltet); sofort geschrieben, bevor die Sperre geloescht wird.
    suspend fun freigeben(): Boolean = withContext(Dispatchers.IO) {
        schreibLock.withLock {
            val alt = schluessel
            schluessel = null
            schreibenJetzt()
            // Misslingt das Schreiben, bleibt die Datei verschluesselt: dann muss auch der Schluessel bleiben
            if (_error.value != null) schluessel = alt
            companyWrites.trySend(_companies.value)
            _error.value == null
        }
    }

    // PIN vergessen: die verschluesselten Daten verwerfen und mit einem neuen Uebungsdepot beginnen.
    suspend fun verwerfen() = withContext(Dispatchers.IO) {
        schreibLock.withLock {
            synchronized(mutationLock) {
                schluessel = null
                _account.value = Trading.fresh()
                _gesperrt.value = false
            }
            schreibenJetzt()
        }
    }

    // Ausstehende Aenderungen sofort schreiben (Desktop beim Schliessen).
    suspend fun sichern() = withContext(Dispatchers.IO) { schreibLock.withLock { schreibenJetzt() } }

    // Ersetzt das Konto komplett (Import).
    fun replace(account: Account) = update { account }

    // Neues Depot mit capital; die Watchlist bleibt, wie im Hinweis beim Zuruecksetzen beschrieben.
    fun reset(capital: Double = Trading.START_CASH) = update { Trading.fresh(capital = capital).copy(watchlist = it.watchlist, watchMeta = it.watchMeta) }

    private companion object {
        // Anfang einer verschluesselten account.json; dahinter Startwert, Chiffrat und Pruefsumme (AES-256-GCM).
        val KENNUNG = "TROETE-AES256GCM-1\n".toByteArray(Charsets.UTF_8)
    }

    fun putCompany(info: CompanyInfo) = synchronized(mutationLock) {
        val next = _companies.value + (info.symbol to info)
        _companies.value = next
        companyWrites.trySend(next)
    }
}
