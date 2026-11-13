package de.troetenrepublik.app

import de.troetenrepublik.app.engine.Positionierung
import de.troetenrepublik.app.engine.RezessionNyFed
import de.troetenrepublik.app.engine.XlsLeser
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.plattform.SichereDatei
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

// Lange Reihen aus dem Datenbuendel, beim Nutzer fortgeschrieben (Bedingungen der Quellen erlauben den automatischen
// Abruf): Rezessionswahrscheinlichkeit der New York Fed. Der geladene Stand liegt auf dem Geraet; ohne Netz gilt er oder
// die mitgelieferte Datei.
internal object HistorieSpeicher {
    val netz by lazy { MarketApi() }
    val nyFedSperre = Mutex()
    @Volatile var nyFed: RezessionNyFed.Reihe? = null
    @Volatile var nyFedGeprueft = 0L
    @Volatile var nyFedFehler = 0L

    const val TAG_MS = 86_400_000L
    const val STUNDE_MS = 3_600_000L
}

// Rezessionswahrscheinlichkeit aus der Zinskurve: sofort der neuere von mitgelieferter Datei und Stand auf dem Geraet,
// hoechstens einmal am Tag (nach einem Fehler erst nach einer Stunde wieder) die aktuelle allmonth.xls.
suspend fun AppState.nyFedRezession(): RezessionNyFed.Reihe? = HistorieSpeicher.nyFedSperre.withLock {
    val sp = HistorieSpeicher
    val datei = SichereDatei(File(store.ordner, "nyfed-rezession.txt"))
    if (sp.nyFed == null) {
        // Erste Zeile: Zeit des letzten Abrufs, danach die Reihe
        val gespeichert = withContext(Dispatchers.IO) { runCatching { datei.lesen().toString(Charsets.UTF_8) }.getOrNull() }
        sp.nyFedGeprueft = gespeichert?.substringBefore('\n')?.toLongOrNull() ?: 0L
        sp.nyFed = RezessionNyFed.neuere(RezessionNyFed.datei, gespeichert?.substringAfter('\n')?.let(RezessionNyFed::lesen))
    }
    val jetzt = System.currentTimeMillis()
    if (jetzt - sp.nyFedGeprueft < sp.TAG_MS && jetzt >= sp.nyFedGeprueft) return@withLock sp.nyFed
    if (jetzt - sp.nyFedFehler < sp.STUNDE_MS) return@withLock sp.nyFed
    val neu = runCatching {
        sp.netz.datei(RezessionNyFed.ADRESSE, 2_000_000)?.let { b -> withContext(Dispatchers.Default) { RezessionNyFed.ausTabelle(XlsLeser.tabelle(b, 0, 6)) } }
    }.getOrNull()
    if (neu == null) {
        sp.nyFedFehler = jetzt
        return@withLock sp.nyFed
    }
    sp.nyFed = RezessionNyFed.neuere(sp.nyFed, neu)
    sp.nyFedGeprueft = jetzt
    val text = jetzt.toString() + "\n" + RezessionNyFed.schreiben(neu)
    withContext(Dispatchers.IO) { runCatching { datei.schreiben(text.toByteArray(Charsets.UTF_8)) } }
    sp.nyFed
}

// Ohne Abruf: was schon bekannt ist (fuer den ersten Aufbau)
fun nyFedRezessionBekannt(): RezessionNyFed.Reihe? = HistorieSpeicher.nyFed ?: RezessionNyFed.datei

// Positionierung an den Terminmaerkten: Archiv der CFTC aus dem Datenbuendel, dazu in einer Abfrage alle Wochen nach seiner
// letzten Woche (ohne Archiv drei Jahre). nur: nur diese Maerkte.
internal suspend fun positionierungLaden(nur: Set<String>?): List<Positionierung.Lage> {
    val p = Positionierung
    val maerkte = p.MAERKTE.filter { nur == null || it.first in nur }
    if (maerkte.isEmpty()) return emptyList()
    val archiv = p.archiv
    val jetzt = System.currentTimeMillis()
    val seitMs = maerkte.minOf { (k, _) -> archiv[k]?.lastOrNull()?.first ?: (jetzt - 3 * 366 * HistorieSpeicher.TAG_MS) }
    val seit = java.time.LocalDate.ofEpochDay(seitMs / HistorieSpeicher.TAG_MS)
    val neu = runCatching {
        HistorieSpeicher.netz.anfrage(p.adresseSeit(maerkte.map { it.second }, seit), mapOf("Accept" to "application/json")).takeIf { it.ok }?.text
    }.getOrNull()?.let(p::lesenMehrere).orEmpty()
    return withContext(Dispatchers.Default) { maerkte.mapNotNull { (k, code) -> p.lageLang(k, archiv[k].orEmpty(), neu[code].orEmpty(), jetzt) } }
}
