package de.troetenrepublik.app.werkzeuge

import de.troetenrepublik.app.engine.IndikatorAllgemein
import de.troetenrepublik.app.engine.IndikatorTreffer
import de.troetenrepublik.app.market.MarketApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.LocalDate

// Baut die allgemeine Trefferbilanz der Indikatoren (assets/indikatoren/allgemein.json): die ersten Aktien des
// Katalogs (nach Beliebtheit sortiert) und die grossen Indizes mit ihrer ganzen Kursgeschichte, je Indikator die
// Signale zusammengezaehlt. Aufruf: gradlew :desktop:indikatorAllgemein -Pziel=<Datei> -Panzahl=<Aktien>. Zwischen den
// Abrufen wartet es, um Yahoo nicht zu belasten.
private val ARTEN = listOf("sma50", "sma200", "bb", "ichimoku", "sar", "rsi", "stoch", "macd", "atr", "obv")
private val INDIZES = listOf("^GSPC", "^NDX", "^DJI", "^GDAXI", "^STOXX50E", "^FTSE", "^FCHI", "^N225", "^HSI")

fun main(args: Array<String>) {
    val ziel = File(args.getOrNull(0) ?: error("Ziel fehlt"))
    val anzahl = args.getOrNull(1)?.toIntOrNull() ?: 150
    val katalog = File(args.getOrNull(2) ?: error("Katalog fehlt"))
    val aktien = Json.parseToJsonElement(katalog.readText()).jsonObject["aktien"]!!.jsonArray
        .mapNotNull { it.jsonObject["s"]?.jsonPrimitive?.content }.take(anzahl)
    val api = MarketApi()
    val je = HashMap<String, MutableList<IndikatorTreffer.Bilanz>>()
    var werte = 0
    var ab = Int.MAX_VALUE
    runBlocking {
        for (s in INDIZES + aktien) {
            val kerzen = runCatching { api.candlesBetween(s, 0L, System.currentTimeMillis() / 1000).candles }.getOrDefault(emptyList())
            delay(400)
            if (kerzen.size < 500) continue
            val b = IndikatorTreffer.alle(kerzen, ARTEN)
            if (b.isEmpty()) continue
            werte++
            b.forEach { (art, bilanz) -> je.getOrPut(art) { mutableListOf() }.add(bilanz) }
            ab = minOf(ab, LocalDate.ofEpochDay(b.values.minOf { it.ab }).year)
            System.err.println("$s: ${kerzen.size} Tage")
        }
    }
    val arten = je.mapNotNull { (art, l) -> IndikatorAllgemein.zusammenfassen(l)?.let { art to it } }.toMap()
    val d = IndikatorAllgemein.Datei(LocalDate.now().toString(), werte, if (ab == Int.MAX_VALUE) 0 else ab, IndikatorTreffer.TAGE, arten)
    ziel.parentFile?.mkdirs()
    ziel.writeText(IndikatorAllgemein.schreiben(d))
    System.err.println("fertig: $werte Werte, ${arten.size} Indikatoren")
}
