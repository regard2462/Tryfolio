package de.troetenrepublik.app.werkzeuge

import de.troetenrepublik.app.market.EuInsider
import de.troetenrepublik.app.market.InsiderQuellen
import de.troetenrepublik.app.market.MarketApi
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate

// Baut den mitgelieferten Stand der Insidergeschaefte eines Landes (assets/copytrading/insider_<land>.json) mit
// denselben Lesern, die die App fuer neue Meldungen nutzt. Aufruf ueber die Gradle-Aufgabe :desktop:insiderBuendel
// mit -Pland=FR|NL|GB, -Pziel=<Datei> und -Ptage=<Tage zurueck>. Zwischen den Abrufen wartet es, um die Quellen nicht
// zu belasten; ein laengerer Zeitraum dauert deshalb einige Minuten.
fun main(args: Array<String>) {
    val land = args.getOrNull(0)?.uppercase() ?: error("Land fehlt")
    val ziel = File(args.getOrNull(1) ?: error("Ziel fehlt"))
    val tage = args.getOrNull(2)?.toLongOrNull() ?: 365
    val heute = LocalDate.now()
    val von = heute.minusDays(tage).toString()
    val netz = InsiderQuellen.netz(MarketApi())
    val alle = runBlocking {
        when (land) {
            "FR" -> InsiderQuellen.amfHolen(netz, von, heute.toString(), emptySet(), max = 50_000, pause = 250)
            "NL" -> InsiderQuellen.afmHolen(netz, von, emptySet(), max = 50_000, pause = 250)
            "GB" -> InsiderQuellen.nsmHolen(netz, von, heute.toString(), emptySet(), max = 50_000, pause = 200)
            else -> error("Unbekanntes Land $land")
        }
    }
    val geschaefte = EuInsider.vereinen(emptyList(), alle.filter { it.isin.isNotEmpty() || it.c.isNotEmpty() }, von)
    val quelle = when (land) {
        "FR" -> "Autorité des marchés financiers, BDIF"
        "NL" -> "Autoriteit Financiële Markten, Register Transacties leidinggevenden"
        else -> "Financial Conduct Authority, National Storage Mechanism"
    }
    val d = EuInsider.Datei(stand = heute.toString(), quelle = quelle, geschaefte = geschaefte, land = land,
        gelesen = alle.map { it.id }.filter { it.isNotEmpty() }.distinct())
    ziel.writeText(EuInsider.schreiben(d))
    System.err.println("fertig: ${geschaefte.size} Geschaefte aus ${d.gelesen.size} Meldungen, ${geschaefte.count { it.kauf }} Kaeufe, ${geschaefte.count { it.verkauf }} Verkaeufe")
}
