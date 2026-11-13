package de.troetenrepublik.app

import de.troetenrepublik.app.engine.Eingepreist
import de.troetenrepublik.app.engine.Enso
import de.troetenrepublik.app.engine.Fruehindikatoren
import de.troetenrepublik.app.engine.OelWelt
import de.troetenrepublik.app.engine.RezessionNyFed
import de.troetenrepublik.app.engine.RohstoffHandel
import de.troetenrepublik.app.engine.RohstoffWaage
import de.troetenrepublik.app.engine.RohstoffWaage.Treiber
import de.troetenrepublik.app.market.Ersatzabrufe
import de.troetenrepublik.app.market.FredAbruf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

// Abrufe fuer Fuer und Wider, Signale und Terminkurve der Rohstoffe (0.78). Geladen wird nur, was die Treiber des
// Rohstoffs brauchen; jede Quelle hat ihren eigenen Zwischenspeicher (hier oder in AppState). Was nicht antwortet, fehlt.
internal object RohstoffSpeicher {
    const val ZWOELF_STUNDEN = 12 * 3_600_000L
    val kurven = ConcurrentHashMap<String, Pair<Long, List<Pair<Eingepreist.Kontrakt, Double>>>>()

    // Reihen der Federal Reserve ueber FRED
    const val REALZINS = "DFII10"
    const val INFLATION = "T10YIE"
    const val DOLLAR = "DX-Y.NYB"
}

// Tagesreihe von FRED ueber zwei Jahre als (Tag seit 1970, Wert); leer ohne Antwort. FRED verbietet das
// Zwischenspeichern: kein Speicher, gleichzeitige Anfragen teilen sich den Abruf.
internal suspend fun fredTagesreihe(id: String): List<Pair<Long, Double>> {
    val von = LocalDate.now(ZoneOffset.UTC).minusYears(2)
    return runCatching {
        FredAbruf.geteilt("fred2:$id") {
            HistorieSpeicher.netz.anfrage(
                Fruehindikatoren.fredAdresse(von, id), mapOf("Accept" to "text/csv", "User-Agent" to Ersatzabrufe.KENNUNG), max = 1_000_000,
            ).takeIf { it.ok }?.text?.let(Fruehindikatoren::fredLesen)
        }
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull().orEmpty()
}

// Gehandelte Terminkontrakte mit letztem Schlusskurs, naechster zuerst, zwoelf Stunden gespeichert.
internal suspend fun AppState.terminkurve(t: RohstoffHandel.Termin, anzahl: Int = 4): List<Pair<Eingepreist.Kontrakt, Double>> {
    val sp = RohstoffSpeicher
    val name = t.wurzel + "." + t.boerse + "/" + anzahl
    val jetzt = System.currentTimeMillis()
    sp.kurven[name]?.takeIf { jetzt - it.first < sp.ZWOELF_STUNDEN }?.let { return it.second }
    val h = LocalDate.now(ZoneOffset.UTC)
    val kandidaten = Eingepreist.kontrakte(t.wurzel, t.boerse, h.year, h.monthValue, RohstoffHandel.KANDIDATEN)
    val k = Eingepreist.kurve(kandidaten, schlusskurse(kandidaten.map { it.symbol }, "5d"), anzahl)
    if (k.isNotEmpty()) sp.kurven[name] = jetzt to k
    return k.ifEmpty { sp.kurven[name]?.second.orEmpty() }
}

// Alle Messungen fuer die Waage eines Rohstoffs. symbol: Kuerzel der Seite (fuer die eigenen Kurse).
internal suspend fun AppState.rohstoffEingaben(symbol: String, schluessel: String): RohstoffWaage.Eingaben = coroutineScope {
    val treiber = RohstoffWaage.treiber(schluessel)
    fun <T> wenn(t: Treiber, laden: suspend () -> T?) = if (t in treiber) async { runCatching { laden() }.getOrNull() } else null

    val realzins = wenn(Treiber.REALZINS) { fredTagesreihe(RohstoffSpeicher.REALZINS) }
    val inflation = wenn(Treiber.INFLATION) { fredTagesreihe(RohstoffSpeicher.INFLATION) }
    val dollar = wenn(Treiber.DOLLAR) {
        schlusskurse(listOf(RohstoffSpeicher.DOLLAR), "6mo")[RohstoffSpeicher.DOLLAR].orEmpty()
            .filter { it.close.isFinite() && it.close > 0 }.map { Math.floorDiv(it.t, 86_400_000L) to it.close }
    }
    val kurse = if (Treiber.TREND in treiber || Treiber.SAISON in treiber) async {
        runCatching { gesamtHistorie(symbol).map { Math.floorDiv(it.t, 86_400_000L) to it.close } }.getOrNull()
    } else null
    val position = wenn(Treiber.POSITIONIERUNG) {
        RohstoffWaage.cotMarkt(schluessel)?.let { m -> positionierung(nur = setOf(m)).firstOrNull { it.schluessel == m } }
    }
    val lager = wenn(Treiber.LAGER) {
        val k = if (schluessel == "GAS") Fruehindikatoren.GAS_US else Fruehindikatoren.ROHOEL
        fruehSignale(listOf(k))[k]
    }
    val boersenlager = wenn(Treiber.BOERSENLAGER) { kupferLager() }
    val kurve = wenn(Treiber.TERMINKURVE) { RohstoffHandel.terminFuer(schluessel)?.let { terminkurve(it) } }
    val enso = wenn(Treiber.ENSO) { ensoStatus() }
    val ensoIndex = wenn(Treiber.ENSO) { ensoIndex() }
    val rezession = wenn(Treiber.KONJUNKTUR) { nyFedRezession() ?: nyFedRezessionBekannt() }

    val pos = position?.await()
    val lag = lager?.await()
    val bl = boersenlager?.await()
    val kv = kurve?.await().orEmpty()
    val en = enso?.await()
    val rez = rezession?.await()?.let { r -> RezessionNyFed.aktuell(r) }
    val oel = if (Treiber.OPEC in treiber || Treiber.WELTLAGER in treiber) OelWelt.datei else null
    fun messung(reihe: List<Double?>, d: OelWelt.Datei): RohstoffWaage.Messung? {
        val w = OelWelt.wert(reihe) ?: return null
        return RohstoffWaage.Messung(w.jetzt, w.aenderung ?: return null, OelWelt.ms(d.abMonat + w.platz))
    }
    RohstoffWaage.Eingaben(
        realzins = realzins?.await().orEmpty(), inflation = inflation?.await().orEmpty(), dollar = dollar?.await().orEmpty(),
        kurse = kurse?.await().orEmpty(),
        positionRang = pos?.index, positionAnteil = pos?.anteil, positionStand = pos?.stand ?: 0L,
        lagerAbweichung = lag?.abweichung, lagerTag = lag?.stand ?: 0L,
        boersenlagerAenderung = bl?.second, boersenlager = bl?.first ?: 0.0, boersenlagerTag = bl?.third ?: 0L,
        kurvenSpanne = RohstoffHandel.spanne(kv), kurveStand = System.currentTimeMillis(),
        ensoPhase = en?.let { Enso.phase(it.zustand) }, ensoIndex = ensoIndex?.await()?.punkte?.lastOrNull()?.wert, ensoStand = en?.abgerufen ?: 0L,
        rezession = rez?.second, rezessionStand = rez?.let { RezessionNyFed.ms(it.first - RezessionNyFed.VORLAUF) } ?: 0L,
        opecPlus = oel?.let { messung(it.rohoel.opecplus, it) }, oecdLager = oel?.let { messung(it.lager.oecd, it) },
        heute = LocalDate.now(ZoneOffset.UTC).toEpochDay(),
    )
}

// Kupferlager der Metallboersen: Bestand, Aenderung gegen den Vormonat in Prozent, Tag. Zuerst die LME (nur wo ihre Dateien
// auf dem Geraet liegen), sonst die COMEX aus selbst geladenen Berichten.
private suspend fun AppState.kupferLager(): Triple<Double, Double, Long>? {
    lmeLagerStand()?.tage?.let { de.troetenrepublik.app.engine.LmeLager.auswerten(it) }?.metalle?.firstOrNull { it.kuerzel == "CA" }?.let { m ->
        OelWelt.gegenVormonat(m.verlauf)?.let { a -> return Triple(m.tonnen, a, m.verlauf.last().first) }
    }
    comexLagerStand()?.metalle?.firstOrNull { it.schluessel == "KUPFER" }?.let { m ->
        OelWelt.gegenVormonat(m.verlauf)?.let { a -> return Triple(m.letzter.gesamt, a, m.letzter.tag) }
    }
    return null
}
