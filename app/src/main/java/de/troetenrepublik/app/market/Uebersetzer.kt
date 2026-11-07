package de.troetenrepublik.app.market

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

// Uebersetzt Marktinhalte der Prediction Markets mit DeepL ins Deutsche. Jede Uebersetzung wird dauerhaft gemerkt
// (hoechstens MAX Texte), damit derselbe Text nie zweimal Zeichen kostet. Vor jedem Abruf prueft das Abrufkontingent
// die Zeichen der letzten 31 Tage.
class Uebersetzer(
    private val anfrage: suspend (url: String, kopf: Map<String, String>, formular: List<Pair<String, String>>) -> MarketApi.Antwort,
    private val kontingent: Abrufkontingent,
    private val datei: File?,
) {
    constructor(api: MarketApi, kontingent: Abrufkontingent, datei: File?) :
        this({ url, kopf, formular -> api.anfrage(url, kopf, formular) }, kontingent, datei)


    companion object {
        const val MAX = 6_000
        // Hoechstens so viele Texte und Zeichen je Abruf.
        private const val JE_ABRUF = 40
        private const val ZEICHEN_JE_ABRUF = 6_000
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val karte = LinkedHashMap<String, String>()
    private val sperre = kotlinx.coroutines.sync.Mutex()

    // Letzte Meldung (Limit, falscher Schluessel); null, wenn der letzte Abruf gelang.
    @Volatile var meldung: String? = null
        private set

    init {
        runCatching { if (datei != null && datei.exists()) karte.putAll(json.decodeFromString<Map<String, String>>(datei.readText())) }
    }

    fun bekannt(): Map<String, String> = synchronized(karte) { HashMap(karte) }

    // Fehlende Uebersetzungen fuer texte holen; Rueckgabe: die neu hinzugekommenen. Ohne Schluessel, ohne Kontingent
    // oder bei Fehlern bleibt es bei den bekannten.
    suspend fun deutsch(texte: Collection<String>, schluessel: String): Map<String, String> {
        if (schluessel.isBlank()) return emptyMap()
        return sperre.withLockSafe {
            val fehlt = synchronized(karte) { texte.map { it.trim() }.filter { it.isNotBlank() && it !in karte }.distinct() }
            val neu = HashMap<String, String>()
            var rest = fehlt
            while (rest.isNotEmpty()) {
                // Paket bis zu 40 Texten oder 6.000 Zeichen
                val paket = ArrayList<String>()
                var zeichen = 0
                for (t in rest) {
                    if (paket.size >= JE_ABRUF || (paket.isNotEmpty() && zeichen + t.length > ZEICHEN_JE_ABRUF)) break
                    paket += t
                    zeichen += t.length
                }
                rest = rest.drop(paket.size)
                if (!kontingent.erlaubt(Abrufkontingent.DEEPL, zeichen.toLong())) { meldung = NewsRepository.LIMIT; break }
                kontingent.buchen(Abrufkontingent.DEEPL, zeichen.toLong())
                val a = anfrage(NachrichtenApis.deeplAdresse(schluessel), NachrichtenApis.deeplKopf(schluessel), NachrichtenApis.deeplFormular(paket))
                when {
                    a.code == 456 -> { kontingent.erschoepft(Abrufkontingent.DEEPL); meldung = NewsRepository.LIMIT; break }
                    a.code == 401 || a.code == 403 -> { meldung = NewsRepository.SCHLUESSEL; break }
                    !a.ok -> break
                }
                val ergebnis = NachrichtenApis.deeplLesen(a.text!!) ?: break
                if (ergebnis.size != paket.size) break
                paket.zip(ergebnis).forEach { (en, de) -> if (de.isNotBlank()) neu[en] = de }
                meldung = null
            }
            if (neu.isNotEmpty()) {
                synchronized(karte) {
                    karte.putAll(neu)
                    while (karte.size > MAX) karte.remove(karte.keys.first())
                }
                speichern()
            }
            neu
        }
    }

    private suspend fun <T> kotlinx.coroutines.sync.Mutex.withLockSafe(block: suspend () -> T): T {
        lock()
        try { return block() } finally { unlock() }
    }

    private fun speichern() {
        val d = datei ?: return
        val kopie = bekannt()
        runCatching {
            d.parentFile?.mkdirs()
            val tmp = File(d.parentFile, d.name + ".tmp")
            tmp.writeText(json.encodeToString(kopie))
            if (!tmp.renameTo(d)) { d.delete(); tmp.renameTo(d) }
        }
    }
}
