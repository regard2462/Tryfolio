package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.engine.KursDatei
import de.troetenrepublik.app.engine.Splits
import de.troetenrepublik.app.plattform.SichereDatei
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

// [0.78] Von der Person eingelesene Kursreihen (engine/KursDatei.kt). Je Symbol eine kleine Textdatei im Ordner der App,
// dazu eine Liste mit Zeitraum und Zahl der Tage. Die Reihen dienen als letzte Quelle hinter Yahoo und Massive
// (EigeneQuelle) und schliessen Luecken in der Vergangenheit (ergaenzt). Die App ruft dafuer nichts ab.
object EigeneKurse {
    // Herkunft an Kursen aus diesen Dateien (Quote.exchange); der Quellenanhang zeigt daran „Eigene Datei“
    const val NAME = "Eigene Datei"

    private const val TAG = 86_400_000L
    private const val INDEX = "liste.txt"

    class Eintrag(val symbol: String, val vonTag: Long, val bisTag: Long, val anzahl: Int, val waehrung: String)

    private val sperre = Any()
    @Volatile private var ordner: File? = null
    private val liste = HashMap<String, Eintrag>()
    private val zwischen = HashMap<String, List<Candle>>()
    private val _eintraege = MutableStateFlow<List<Eintrag>>(emptyList())

    // Alle gespeicherten Reihen, nach Symbol geordnet
    val eintraege: StateFlow<List<Eintrag>> = _eintraege

    // Ordner festlegen und die Liste lesen; fehlt sie, wird sie aus den Dateien neu aufgebaut.
    fun einrichten(dir: File?) {
        synchronized(sperre) {
            ordner = dir
            liste.clear(); zwischen.clear()
            if (dir != null) listeLaden(dir)
            melden()
        }
    }

    // Erlaubte Zeichen eines Symbols: Buchstaben, Ziffern und . ^ = -, hoechstens 24
    fun symbolSauber(s: String): String =
        s.trim().uppercase().filter { it in 'A'..'Z' || it in '0'..'9' || it == '.' || it == '^' || it == '=' || it == '-' }.take(24)

    private fun datei(dir: File, symbol: String) =
        File(dir, "reihe-" + symbol.map { it.code.toString(16).padStart(2, '0') }.joinToString("") + ".txt")

    private fun melden() { _eintraege.value = liste.values.sortedBy { it.symbol } }

    private fun eintrag(symbol: String, waehrung: String, k: List<Candle>) =
        Eintrag(symbol, Math.floorDiv(k.first().t, TAG), Math.floorDiv(k.last().t, TAG), k.size, waehrung)

    private fun lesen(f: File): Pair<String, List<Candle>>? = runCatching {
        val z = SichereDatei(f).lesen().decodeToString().split('\n')
        val kopf = z.firstOrNull()?.trim()?.split(';') ?: return null
        if (kopf.size < 3 || kopf[0] != "v1") return null
        val k = ArrayList<Candle>(z.size)
        for (i in 1 until z.size) {
            val p = z[i].trim().split(';')
            if (p.size < 6) continue
            val d = p[0].toLongOrNull() ?: continue
            val c = p[4].toDoubleOrNull() ?: continue
            k.add(Candle(d * TAG, p[1].toDoubleOrNull() ?: c, p[2].toDoubleOrNull() ?: c, p[3].toDoubleOrNull() ?: c, c, p[5].toDoubleOrNull() ?: 0.0))
        }
        (kopf[2]) to k
    }.getOrNull()

    private fun schreiben(dir: File, symbol: String, waehrung: String, k: List<Candle>) {
        val b = StringBuilder("v1;").append(symbol).append(';').append(waehrung).append('\n')
        for (c in k) b.append(Math.floorDiv(c.t, TAG)).append(';').append(c.open).append(';').append(c.high).append(';')
            .append(c.low).append(';').append(c.close).append(';').append(c.volume).append('\n')
        SichereDatei(datei(dir, symbol)).schreiben(b.toString().encodeToByteArray())
    }

    private fun listeSchreiben(dir: File) {
        val b = StringBuilder()
        for (e in liste.values.sortedBy { it.symbol }) b.append(e.symbol).append(';').append(e.vonTag).append(';').append(e.bisTag)
            .append(';').append(e.anzahl).append(';').append(e.waehrung).append('\n')
        SichereDatei(File(dir, INDEX)).schreiben(b.toString().encodeToByteArray())
    }

    private fun listeLaden(dir: File) {
        val gelesen = runCatching { SichereDatei(File(dir, INDEX)).lesen().decodeToString() }.getOrNull()
        if (gelesen != null) {
            for (z in gelesen.split('\n')) {
                val p = z.trim().split(';')
                if (p.size < 5) continue
                val von = p[1].toLongOrNull() ?: continue
                val bis = p[2].toLongOrNull() ?: continue
                val n = p[3].toIntOrNull() ?: continue
                liste[p[0]] = Eintrag(p[0], von, bis, n, p[4])
            }
            return
        }
        // Liste fehlt: aus den Dateien aufbauen
        val dateien = dir.listFiles { f -> f.name.startsWith("reihe-") && f.name.endsWith(".txt") } ?: return
        for (f in dateien) {
            val kopf = runCatching { SichereDatei(f).lesen().decodeToString().substringBefore('\n').trim().split(';') }.getOrNull() ?: continue
            if (kopf.size < 3 || kopf[0] != "v1") continue
            val (w, k) = lesen(f) ?: continue
            if (k.isNotEmpty()) liste[kopf[1]] = eintrag(kopf[1], w, k)
        }
        if (liste.isNotEmpty()) runCatching { listeSchreiben(dir) }
    }

    fun hat(symbol: String): Boolean = synchronized(sperre) { liste.containsKey(symbol.uppercase()) }

    // Tageskerzen eines Symbols, aelteste zuerst; leer ohne gespeicherte Reihe
    fun reihe(symbol: String): List<Candle> {
        synchronized(sperre) {
            val s = symbol.uppercase()
            if (!liste.containsKey(s)) return emptyList()
            zwischen[s]?.let { return it }
            val dir = ordner ?: return emptyList()
            val k = lesen(datei(dir, s))?.second.orEmpty()
            if (zwischen.size >= 6) zwischen.clear()
            zwischen[s] = k
            return k
        }
    }

    fun waehrung(symbol: String): String = synchronized(sperre) { liste[symbol.uppercase()]?.waehrung ?: "USD" }

    // Speichert Reihen; ein vorhandenes Symbol wird je Tag mit den neuen Kursen ueberschrieben. Rueckgabe: Zahl der
    // gespeicherten Reihen. umbenennen gilt nur bei genau einer Reihe.
    fun speichern(reihen: List<KursDatei.Reihe>, umbenennen: String? = null): Int {
        synchronized(sperre) {
        val dir = ordner ?: return 0
        var n = 0
        for (r in reihen) {
            val symbol = symbolSauber(if (reihen.size == 1 && !umbenennen.isNullOrBlank()) umbenennen else r.symbol)
            if (symbol.isEmpty() || r.kerzen.isEmpty()) continue
            val alt = if (liste.containsKey(symbol)) lesen(datei(dir, symbol))?.second.orEmpty() else emptyList()
            val neu = KursDatei.zusammen(alt, r.kerzen)
            val w = if (alt.isEmpty()) r.waehrung else liste[symbol]?.waehrung ?: r.waehrung
            if (runCatching { schreiben(dir, symbol, w, neu) }.isFailure) continue
            liste[symbol] = eintrag(symbol, w, neu)
            zwischen.remove(symbol)
            n++
        }
        if (n > 0) { runCatching { listeSchreiben(dir) }; melden() }
        return n
        }
    }

    fun loeschen(symbol: String) {
        synchronized(sperre) {
            val dir = ordner ?: return
            val s = symbol.uppercase()
            if (liste.remove(s) == null) return
            zwischen.remove(s)
            runCatching { datei(dir, s).delete() }
            runCatching { listeSchreiben(dir) }
            melden()
        }
    }

    private fun fensterAb(range: String, jetzt: Long): Long = when (range) {
        "max" -> 0L
        "ytd" -> (KursDatei.tageSeit1970(KursDatei.datumAusTagen(Math.floorDiv(jetzt, TAG)).first, 1, 1) ?: 0L) * TAG
        else -> jetzt - Kursarchiv.tage(range) * TAG
    }

    // Luecken einer Kursreihe aus den eigenen Dateien fuellen (nur Tages-, Wochen- und Monatskerzen); ohne gespeicherte
    // Reihe oder bei nicht passendem Massstab bleibt die Reihe, wie sie ist.
    fun ergaenzt(symbol: String, s: Series, range: String, interval: String, jetzt: Long = System.currentTimeMillis()): Series =
        ergaenzt(symbol, s, fensterAb(range, jetzt), jetzt, interval)

    fun ergaenzt(symbol: String, s: Series, abMs: Long, bisMs: Long, interval: String): Series {
        if (interval !in MassiveKurse.ABSTAENDE || KursQuellen.istExtern(symbol) || !hat(symbol)) return s
        val neu = KursDatei.ergaenzenMitAbstand(s.candles, reihe(symbol), abMs, bisMs, interval)
        return if (neu === s.candles) s else s.copy(candles = neu)
    }
}

// Letzte Quelle der Kette: die eingelesenen Reihen. Antwortet nur fuer Symbole mit gespeicherter Reihe. Kerzen gibt es
// nur als Tages-, Wochen- und Monatskerzen; ein Zeitraum reicht vom letzten gespeicherten Tag zurueck. Kurs ist der
// Schluss des letzten Tags (Markt geschlossen), die Herkunft steht in Quote.exchange.
class EigeneQuelle(private val uhr: () -> Long = System::currentTimeMillis) : KursQuelle {
    override val praefix: String = ""
    override val waehrung: String = ""

    override suspend fun kurse(kennungen: List<String>): Map<String, Quote> {
        val aus = HashMap<String, Quote>()
        for (k in kennungen) {
            val r = EigeneKurse.reihe(k).takeIf { it.isNotEmpty() } ?: continue
            MassiveKurse.kursAus(k, r, uhr())?.copy(nativeCurrency = EigeneKurse.waehrung(k), exchange = EigeneKurse.NAME)?.let { aus[k] = it }
        }
        return aus
    }

    override suspend fun kerzen(kennung: String, range: String, interval: String): Series {
        if (interval !in MassiveKurse.ABSTAENDE) return Series(emptyList())
        val r = EigeneKurse.reihe(kennung).takeIf { it.isNotEmpty() } ?: return Series(emptyList())
        val ab = if (range == "max") 0L else r.last().t - Kursarchiv.tage(range) * 86_400_000L
        return Series(KursDatei.verdichten(r.filter { it.t >= ab }, interval))
    }

    override suspend fun kerzenZwischen(kennung: String, vonMs: Long, bisMs: Long, interval: String): Series {
        if (interval !in MassiveKurse.ABSTAENDE) return Series(emptyList())
        return Series(KursDatei.verdichten(EigeneKurse.reihe(kennung).filter { it.t in vonMs..bisMs }, interval))
    }

    override suspend fun dividenden(kennung: String): List<MarketApi.DividendEvent> = emptyList()

    override suspend fun splits(kennung: String): List<Splits.Split> = emptyList()

    override suspend fun suche(text: String): List<MarketApi.SearchHit> = emptyList()

    override suspend fun wechselkurse(): Map<String, Double> = emptyMap()
}
