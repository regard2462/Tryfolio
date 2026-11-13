package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Catalog
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

// Stichprobe mit dem Katalog der App (0.78): Texte, die Aktien nennen, muessen sie finden; Texte ohne Aktie duerfen
// keine melden. Erwartet wird je Text die Menge der Kuerzel.
class Fassung078KuerzelStichprobeTest {
    private val katalog: List<Pair<String, String>> by lazy {
        val datei = listOf(File("src/main/assets/katalog.json"), File("../app/src/main/assets/katalog.json")).first { it.exists() }
        Json { ignoreUnknownKeys = true }.decodeFromString(Catalog.Data.serializer(), datei.readText()).aktien.map { it.s to it.n }
    }

    private fun kuerzel(text: String) = NachrichtEinordnung.erwaehnt(listOf(text), katalog, hoechstens = 20).map { it.first }.toSet()

    private val proben = listOf(
        // Genannt: Name im Text
        "Nvidia Shares Plummet as Board Considers Jensen Huang's Future as CEO" to setOf("NVDA"),
        "Nebius falls 10% as company expects increased 2026 capex spend, issues weak outlook" to setOf("NBIS"),
        "Lieferkette unter Beschuss: BMW und Skoda müssen wegen Ukraine-Krieg Produktion drosseln" to setOf("BMW.DE"),
        "ServiceNow raises full-year guidance after strong subscription growth" to setOf("NOW"),
        "Intercontinental Exchange reports record futures volume" to setOf("ICE"),
        "Microsoft and Amazon expand cloud spending, Apple holds back" to setOf("MSFT", "AMZN", "AAPL"),
        "Siemens Energy hebt die Prognose an, Rheinmetall erhält Großauftrag" to setOf("ENR.DE", "RHM.DE"),
        "Siemens und Siemens Energy legen Zahlen vor" to setOf("SIE.DE", "ENR.DE"),
        "Deutsche Bank stuft Deutsche Telekom hoch, Commerzbank bleibt neutral" to setOf("DBK.DE", "DTE.DE", "CBK.DE"),
        "Porsche Automobil Holding senkt Ausblick" to setOf("PAH3.DE"),
        // Genannt: Kuerzel mit Kennzeichnung
        "ServiceNow (NYSE: NOW) and Allstate (NYSE: ALL) report after the bell" to setOf("NOW", "ALL"),
        "Traders pile into \$ICE and \$AMD calls ahead of earnings" to setOf("ICE", "AMD"),
        "Analysts lift targets for NVDA and MSFT after the report" to setOf("NVDA", "MSFT"),
        "Chipmaker AMD gains share in data centers" to setOf("AMD"),
        // Nicht genannt: Kuerzel als gewoehnliches Wort oder in Grossschrift
        "MEXICO MUST STOP THEM, NOW, and if they do not do so, the Tariffs on Mexico will be IMMEDIATELY DOUBLED. OUR GREAT BORDER PATROL, & ICE, HAVE BEEN SO INFORMED." to emptySet(),
        "ICE agents detained dozens at the border, officials said" to emptySet(),
        "Buy NOW, pay later: ALL you need to know about the KEY changes" to emptySet(),
        "As credit card balances hit a record high, medical bills can become 'shadow debt,' expert says" to emptySet(),
        "DAX auf Rekordhoch - Anleger vertrauen deutscher Wachstumsstory" to emptySet(),
        "The FED and the SEC meet as GDP data shows the USA slowing" to emptySet(),
        "WE WILL MAKE AMERICA GREAT AGAIN, THE BEST DEAL EVER, SO TRUE" to emptySet(),
        "If the Democrats want to root out corruption, they should start with investigating Blue States" to emptySet(),
        "Wetter bleibt mild, Bahn meldet Verspätungen im Fernverkehr" to emptySet(),
    )

    @Test fun stichprobe() {
        val bericht = StringBuilder()
        var fehler = 0
        for ((text, soll) in proben) {
            val ist = kuerzel(text)
            if (ist != soll) fehler++
            bericht.append(if (ist == soll) "OK   " else "FALSCH ").append(soll).append(" / ").append(ist).append(" : ").append(text.take(70)).append('\n')
        }
        File("build/kuerzel-stichprobe.txt").writeText(bericht.toString())
        assertEquals(bericht.toString(), 0, fehler)
    }
}
