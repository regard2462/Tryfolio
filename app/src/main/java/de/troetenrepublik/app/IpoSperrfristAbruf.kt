package de.troetenrepublik.app

import de.troetenrepublik.app.engine.IpoSperrfristen
import de.troetenrepublik.app.market.SecInsider
import de.troetenrepublik.app.plattform.SichereDatei
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

// Sperrfristen, die bald enden (0.78): je Boersengang wird der Prospekt bei der SEC einmal gelesen (Kontaktadresse
// unter Zugaenge wie bei allen SEC-Abrufen) und das Ergebnis auf dem Geraet gemerkt. Je Laden nur wenige neue
// Prospekte, die groessten Angebote zuerst.
internal object IpoSperrfristSpeicher {
    val sperre = Mutex()
    @Volatile var gelesen: Map<String, IpoSperrfristen.Gelesen>? = null
}

// null ohne Kontaktadresse. zwischenstand: die Liste nach jedem gelesenen Prospekt.
suspend fun AppState.ipoSperrfristen(zwischenstand: (List<IpoSperrfristen.Eintrag>) -> Unit = {}): List<IpoSperrfristen.Eintrag>? = IpoSperrfristSpeicher.sperre.withLock {
    if (SecInsider.kennung(settingsFlow.value.secKontakt) == null) return@withLock null
    val sp = IpoSperrfristSpeicher
    val datei = SichereDatei(File(store.ordner, "ipo-sperrfristen.txt"))
    var gelesen = sp.gelesen ?: withContext(Dispatchers.IO) {
        runCatching { IpoSperrfristen.lesen(datei.lesen().toString(Charsets.UTF_8)) }.getOrDefault(emptyMap())
    }.also { sp.gelesen = it }
    val heute = java.time.LocalDate.now(java.time.ZoneId.of("America/New_York")).toEpochDay()
    val kandidaten = IpoSperrfristen.kandidaten(ipoKalenderZurueck(IpoSperrfristen.MONATE), heute)
    val jetzt = System.currentTimeMillis()
    val offen = IpoSperrfristen.offen(kandidaten, gelesen, jetzt)
    if (offen.isNotEmpty()) zwischenstand(IpoSperrfristen.eintraege(kandidaten, gelesen, heute))
    var geaendert = false
    // Antwortet die Kuerzelliste der SEC nicht, wird nichts gelesen und nichts gemerkt
    val listeDa = offen.isEmpty() || ipoCik("AAPL") != null
    // Ein Abruf ohne Antwort zaehlt als „ohne Ergebnis“, wird aber schon am naechsten Tag wieder versucht
    val baldWieder = jetzt - (IpoSperrfristen.ERNEUT_TAGE - 1) * 86_400_000L
    var fehlschlaege = 0
    if (listeDa) for (a in offen) {
        val cik = ipoCik(a.symbol)
        var zeit = jetzt
        var auszug: de.troetenrepublik.app.engine.IpoProspekt.Auszug? = null
        if (cik != null) {
            delay(200)
            val doks = ipoDokumente(cik).orEmpty()
            val wahl = de.troetenrepublik.app.engine.IpoProspekt.wahl(doks)
            if (wahl != null) auszug = ipoProspekt(doks)
            if (doks.isEmpty() || (wahl != null && auszug == null)) { zeit = baldWieder; fehlschlaege++ } else fehlschlaege = 0
        }
        gelesen = gelesen + (IpoSperrfristen.schluessel(a) to IpoSperrfristen.ausAuszug(auszug, zeit))
        sp.gelesen = gelesen
        geaendert = true
        zwischenstand(IpoSperrfristen.eintraege(kandidaten, gelesen, heute))
        // Drei Abrufe in Folge ohne Antwort: die SEC ist nicht erreichbar oder bremst, nicht weiter anfragen
        if (fehlschlaege >= 3) break
    }
    if (geaendert) {
        // Nur Boersengaenge behalten, die noch im Blick sind
        val behalten = kandidaten.map { IpoSperrfristen.schluessel(it) }.toSet()
        val text = IpoSperrfristen.schreiben(gelesen.filterKeys { it in behalten })
        withContext(Dispatchers.IO) { runCatching { datei.schreiben(text.toByteArray(Charsets.UTF_8)) } }
    }
    IpoSperrfristen.eintraege(kandidaten, gelesen, heute)
}
