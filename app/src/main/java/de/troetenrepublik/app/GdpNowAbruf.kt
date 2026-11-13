package de.troetenrepublik.app

import de.troetenrepublik.app.engine.GdpNow
import de.troetenrepublik.app.engine.GdpNowAtlanta
import de.troetenrepublik.app.market.Ersatzabrufe
import de.troetenrepublik.app.market.FredAbruf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

// GDPNow (0.78): zuerst die Tabelle der Atlanta Fed (eine Datei, Verlauf des laufenden Quartals), ohne Antwort die
// Reihe bei FRED (zwei Abrufe der Staende). Nichts bleibt ueber die offene Ansicht hinaus gehalten und nichts liegt auf
// dem Geraet, FRED verbietet das Zwischenspeichern; gleichzeitige Anfragen teilen sich einen Abruf (FredAbruf).
// Nach einem Fehlschlag beider Quellen ruht der Abruf eine Stunde (nur der Zeitpunkt wird gemerkt).
internal object GdpNowSpeicher {
    @Volatile var fehler = 0L
}

suspend fun AppState.gdpNow(): GdpNow.Stand? = FredAbruf.geteilt("gdpnow") {
    val sp = GdpNowSpeicher
    val jetzt = System.currentTimeMillis()
    if (jetzt - sp.fehler in 0 until HistorieSpeicher.STUNDE_MS) return@geteilt null
    val kopf = mapOf("User-Agent" to Ersatzabrufe.KENNUNG)
    val atlanta = runCatching {
        HistorieSpeicher.netz.datei(GdpNowAtlanta.DATEI, GdpNowAtlanta.MAX_BYTES, kopf)
            ?.let { b -> withContext(Dispatchers.Default) { GdpNowAtlanta.lesen(b) } }
    }.onFailure { if (it is CancellationException) throw it }.getOrNull()
    if (atlanta != null) return@geteilt atlanta
    val heute = LocalDate.now(ZoneId.of("America/New_York"))
    val csvKopf = mapOf("Accept" to "text/csv", "User-Agent" to Ersatzabrufe.KENNUNG)
    suspend fun datei(tage: List<LocalDate>): String? =
        runCatching { HistorieSpeicher.netz.anfrage(GdpNow.adresse(tage), csvKopf, max = 500_000).takeIf { it.ok }?.text }
            .onFailure { if (it is CancellationException) throw it }.getOrNull()
    val dateien = coroutineScope {
        val nah = async { datei(GdpNow.tageNah(heute)) }
        val fern = async { datei(GdpNow.tageFern(heute)) }
        listOfNotNull(fern.await(), nah.await())
    }
    val neu = withContext(Dispatchers.Default) { GdpNow.lesen(dateien) }
    if (neu == null) sp.fehler = jetzt
    neu
}
