package de.troetenrepublik.app

import de.troetenrepublik.app.engine.Fruehindikatoren
import de.troetenrepublik.app.engine.KursUmsatz
import de.troetenrepublik.app.engine.Phasenzaehler
import de.troetenrepublik.app.engine.Rhythmus16
import de.troetenrepublik.app.market.Ersatzabrufe
import de.troetenrepublik.app.market.FredAbruf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset

// Abrufe fuer Phasenzaehler, Kurs und Umsatz und den 16-Wochen-Rhythmus (0.78). Renditen ueber FRED, Kurse ueber die
// Kursquelle der App. Was nicht antwortet, fehlt in der Rechnung.
internal object MarktzeichenSpeicher {
    const val ZWOELF_STUNDEN = 12 * 3_600_000L
    @Volatile var phasen: Pair<Long, Phasenzaehler.Ergebnis>? = null

    // Renditen zehn- und einjaehriger US-Staatsanleihen (FRED)
    const val RENDITE10 = "DGS10"
    const val RENDITE1 = "DGS1"
    const val EURO_DOLLAR = "EURUSD=X"
    const val OEL = "CL=F"
    // Rohstoffkorb: Oel, Gas, Gold, Kupfer, Weizen, Mais, Zucker
    val KORB = listOf(OEL, "NG=F", "GC=F", "HG=F", "ZW=F", "ZC=F", "SB=F")
    // Indizes fuer Kurs und Umsatz
    val INDIZES = listOf("^GSPC" to "S&P 500", "^GDAXI" to "DAX")
    const val RHYTHMUS_INDEX = "^GDAXI"
}

// Tagesreihe von FRED ueber drei Jahre als (Tag seit 1970, Wert); leer ohne Antwort.
// FRED verbietet das Zwischenspeichern: kein Speicher, gleichzeitige Anfragen teilen sich den Abruf.
private suspend fun fredDreiJahre(id: String): List<Pair<Long, Double>> = runCatching {
    FredAbruf.geteilt("fred3:$id") {
        HistorieSpeicher.netz.anfrage(
            Fruehindikatoren.fredAdresse(LocalDate.now(ZoneOffset.UTC).minusYears(3), id),
            mapOf("Accept" to "text/csv", "User-Agent" to Ersatzabrufe.KENNUNG), max = 1_000_000,
        ).takeIf { it.ok }?.text?.let(Fruehindikatoren::fredLesen)
    }
}.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull().orEmpty()

// Phasenzaehler aus Renditen, Dollar, Rohstoffkorb und Oel; zwoelf Stunden gespeichert. null ohne genug Groessen.
internal suspend fun AppState.phasenzaehler(): Phasenzaehler.Ergebnis? = coroutineScope {
    val sp = MarktzeichenSpeicher
    val jetzt = System.currentTimeMillis()
    sp.phasen?.takeIf { jetzt - it.first in 0 until sp.ZWOELF_STUNDEN }?.let { return@coroutineScope it.second }
    val r10 = async { fredDreiJahre(sp.RENDITE10) }
    val r1 = async { fredDreiJahre(sp.RENDITE1) }
    val kurse = async { schlusskurse((sp.KORB + sp.EURO_DOLLAR).distinct(), "5y") }
    val k = kurse.await()
    fun reihe(s: String) = k[s].orEmpty().filter { it.close.isFinite() && it.close > 0 }.map { Math.floorDiv(it.t, 86_400_000L) to it.close }
    val e = withContext(Dispatchers.Default) {
        Phasenzaehler.auswerten(
            Phasenzaehler.Eingaben(
                r10.await(), r1.await(), reihe(sp.EURO_DOLLAR), sp.KORB.map { reihe(it) }, reihe(sp.OEL),
                LocalDate.now(ZoneOffset.UTC).toEpochDay(),
            ),
        )
    }
    if (e != null) sp.phasen = jetzt to e
    e ?: sp.phasen?.second
}

// Kurs und Umsatz je Index (Name, Ergebnis); Indizes ohne Umsatzdaten fehlen.
internal suspend fun AppState.kursUmsatz(): List<Pair<String, KursUmsatz.Ergebnis>> {
    val heute = LocalDate.now(ZoneOffset.UTC).toEpochDay()
    return MarktzeichenSpeicher.INDIZES.mapNotNull { (symbol, name) ->
        val kerzen = kurseNativ(symbol)
        withContext(Dispatchers.Default) { KursUmsatz.auswerten(kerzen, heute) }?.let { name to it }
    }
}

// 16-Wochen-Rhythmus am DAX ueber die ganze Kursreihe; null ohne genug Kurse.
internal suspend fun AppState.rhythmus16(): Rhythmus16.Ergebnis? {
    val kerzen = gesamtHistorie(MarktzeichenSpeicher.RHYTHMUS_INDEX)
    return withContext(Dispatchers.Default) {
        Rhythmus16.auswerten(kerzen.map { Math.floorDiv(it.t, 86_400_000L) to it.close }, LocalDate.now(ZoneOffset.UTC).toEpochDay())
    }
}
