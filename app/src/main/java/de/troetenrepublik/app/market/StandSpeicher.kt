package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.plattform.SichereDatei
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

// Dauerhafter Zwischenspeicher auf dem Geraet (Stand 0.76): je Schluessel ein Text mit Abrufzeit. Er ueberbrueckt nur
// die Wartezeit nach dem Oeffnen; der Abruf selbst findet weiterhin nach den bisherigen Fristen statt (StandRegel).
interface StandDatei {
    // null, wenn es die Datei nicht gibt oder sie nicht lesbar ist
    fun lesen(): String?
    fun schreiben(text: String)
}

// Datei im Speicherordner; geschrieben wird ueber SichereDatei, also nie halb fertig.
class SichereStandDatei(private val datei: File) : StandDatei {
    override fun lesen(): String? = runCatching { SichereDatei(datei).lesen().toString(Charsets.UTF_8) }.getOrNull()
    override fun schreiben(text: String) { SichereDatei(datei).schreiben(text.toByteArray(Charsets.UTF_8)) }
}

// Schluessel und Werte in mehreren Dateien (nach Hash des Schluessels), je Datei hoechstens maxZeichen Zeichen und
// maxEintraege Eintraege; ist eine voll, fallen die aeltesten Eintraege dieser Datei weg. Eine Zeile je Eintrag:
// Schluessel, Abrufzeit und Wert (einzeiliger Text), durch Tabulatoren getrennt. Eine beschaedigte Zeile faellt allein
// weg, eine unlesbare Datei gilt als leer. Geschrieben wird erst bei sichern(), das der Aufrufer im Hintergrund ruft.
class StandSpeicher(
    dateien: List<StandDatei>,
    private val maxZeichen: Int = 800_000,
    private val maxEintraege: Int = 40,
    private val jetzt: () -> Long = { System.currentTimeMillis() },
) {
    data class Eintrag(val t: Long, val wert: String)

    private class Teil(val datei: StandDatei) {
        var karte: MutableMap<String, Eintrag>? = null
        var geaendert = false
    }

    private val teile = dateien.map { Teil(it) }

    init { require(teile.isNotEmpty()) }

    private fun teil(key: String): Teil = teile[(key.hashCode() and Int.MAX_VALUE) % teile.size]

    private fun karte(t: Teil): MutableMap<String, Eintrag> = t.karte ?: parsen(runCatching { t.datei.lesen() }.getOrNull()).also { t.karte = it }

    private fun parsen(text: String?): MutableMap<String, Eintrag> {
        val m = mutableMapOf<String, Eintrag>()
        if (text.isNullOrEmpty()) return m
        for (zeile in text.split('\n')) {
            val teile = zeile.split('\t', limit = 3)
            if (teile.size != 3 || teile[0].isBlank()) continue
            val t = teile[1].toLongOrNull() ?: continue
            if (teile[2].isEmpty()) continue
            m[teile[0]] = Eintrag(t, teile[2])
        }
        return m
    }

    private fun einzeilig(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    @Synchronized
    fun lesen(key: String): Eintrag? = karte(teil(key))[einzeilig(key)]

    @Synchronized
    fun schreiben(key: String, wert: String, t: Long = jetzt()) {
        if (wert.isEmpty()) return
        val te = teil(key)
        val m = karte(te)
        m[einzeilig(key)] = Eintrag(t, einzeilig(wert))
        verdraengen(m)
        te.geaendert = true
    }

    @Synchronized
    fun entfernen(key: String) {
        val te = teil(key)
        if (karte(te).remove(einzeilig(key)) != null) te.geaendert = true
    }

    // Alle Eintraege (fuer Listen wie die gespeicherten Kurse)
    @Synchronized
    fun alle(): Map<String, Eintrag> = teile.flatMap { karte(it).entries }.associate { it.key to it.value }

    private fun groesse(m: Map<String, Eintrag>) = m.entries.sumOf { it.key.length + it.value.wert.length + 24 }

    private fun verdraengen(m: MutableMap<String, Eintrag>) {
        if (m.size <= maxEintraege && groesse(m) <= maxZeichen) return
        val nachAlter = m.entries.sortedBy { it.value.t }.map { it.key }
        var i = 0
        // Den juengsten Eintrag behalten, auch wenn er allein zu gross waere
        while (i < nachAlter.size - 1 && (m.size > maxEintraege || groesse(m) > maxZeichen)) m.remove(nachAlter[i++])
    }

    // Zeichen aller Eintraege (grobe Dateigroesse)
    @Synchronized
    fun zeichen(): Int = teile.sumOf { groesse(karte(it)) }

    @Synchronized
    fun eintraege(): Int = teile.sumOf { karte(it).size }

    @Synchronized
    fun offen(): Boolean = teile.any { it.geaendert }

    // Geaenderte Dateien schreiben; gibt die Zahl geschriebener Dateien zurueck. Ein Fehler laesst die Datei fuer den
    // naechsten Versuch offen.
    fun sichern(): Int {
        var n = 0
        for (te in teile) {
            val text = synchronized(this) {
                if (!te.geaendert) null
                else {
                    te.geaendert = false
                    karte(te).entries.joinToString("\n") { "${it.key}\t${it.value.t}\t${it.value.wert}" }
                }
            } ?: continue
            try {
                te.datei.schreiben(text)
                n++
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                synchronized(this) { te.geaendert = true }
            }
        }
        return n
    }
}

// Regeln fuer den gespeicherten Stand. Er ersetzt nie den Abruf: erst steht er da, dann kommt die neue Antwort drueber.
object StandRegel {
    // wert gehoert zum Zeitpunkt abgerufen; frisch = innerhalb der Frist, also ohnehin gueltig
    class Gespeichert<T>(val wert: T, val abgerufen: Long, val frisch: Boolean)

    // Was die Seite zeigt: wert (null = nichts da), abgerufen (Zeit des Werts), alt = gespeicherter Stand ausserhalb der
    // Frist (mit Zeitangabe kennzeichnen), laedt = Abruf laeuft noch
    class Anzeige<T>(val wert: T?, val abgerufen: Long, val alt: Boolean, val laedt: Boolean)

    // Ein Wert gilt nur innerhalb seiner Frist als frisch; eine zurueckgestellte Uhr (Alter unter null) nie.
    fun frisch(abgerufen: Long, fristMs: Long, jetzt: Long): Boolean = jetzt - abgerufen in 0 until fristMs

    // Uhrzeit, wenn der Stand von heute ist, sonst Datum mit Uhrzeit.
    fun beschriftung(abgerufen: Long, jetzt: Long, zeit: (Long) -> String, datumZeit: (Long) -> String, tag: (Long) -> String): String =
        if (tag(abgerufen) == tag(jetzt)) zeit(abgerufen) else datumZeit(abgerufen)

    // Gespeicherten Stand sofort melden, dann abrufen und die Antwort melden. Ohne Antwort (null) bleibt der Stand
    // sichtbar, jetzt als alt gekennzeichnet; ohne Stand bleibt nichts. Abbruch der Coroutine wird nicht verschluckt.
    suspend fun <T> laden(gespeichert: Gespeichert<T>?, jetzt: () -> Long, abruf: suspend () -> T?, ausgabe: (Anzeige<T>) -> Unit) {
        ausgabe(
            if (gespeichert != null) Anzeige(gespeichert.wert, gespeichert.abgerufen, alt = !gespeichert.frisch, laedt = true)
            else Anzeige(null, 0L, alt = false, laedt = true),
        )
        val neu = try { abruf() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        ausgabe(
            if (neu != null) Anzeige(neu, jetzt(), alt = false, laedt = false)
            else Anzeige(gespeichert?.wert, gespeichert?.abgerufen ?: 0L, alt = gespeichert != null, laedt = false),
        )
    }
}

// Kursreihe als Text mit parallelen Zahlenlisten (kleiner als eine Liste von Objekten).
object SerieCodec {
    @Serializable
    private class Dto(
        val s: Long, val e: Long, val t: List<Long>, val o: List<Double>, val h: List<Double>,
        val l: List<Double>, val c: List<Double>, val v: List<Double>,
    )

    private val json = Json { allowSpecialFloatingPointValues = true; ignoreUnknownKeys = true }

    fun kodieren(s: Series): String {
        val k = s.candles
        return json.encodeToString(Dto(s.dayStart, s.dayEnd, k.map { it.t }, k.map { it.open }, k.map { it.high }, k.map { it.low }, k.map { it.close }, k.map { it.volume }))
    }

    // null bei unlesbarem Text oder ungleich langen Listen
    fun lesen(text: String): Series? {
        val d = runCatching { json.decodeFromString<Dto>(text) }.getOrNull() ?: return null
        val n = d.t.size
        if (d.o.size != n || d.h.size != n || d.l.size != n || d.c.size != n || d.v.size != n) return null
        return Series(List(n) { Candle(d.t[it], d.o[it], d.h[it], d.l[it], d.c[it], d.v[it]) }, d.s, d.e)
    }
}
