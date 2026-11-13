package de.troetenrepublik.app.ui

import de.troetenrepublik.app.i18n.S
import java.time.LocalDate

// Sammelstelle „Selbst anstoßen“: alles, was erst da ist, nachdem die Person es selbst geladen, eingelesen oder im
// Browser geholt hat. Hier steht nur die Logik der Zustände; die Seite ist AnstossenScreen.
object Anstossen {

    // Route der Seite
    const val ROUTE = "anstossen"

    // Adressen der Quellen, die es nur im Browser gibt (Liste „Nur zum Ansehen“ im Selbstabruf, Karten der Wertseite)
    const val LME_BERICHT = "https://www.lme.com/en/market-data/reports-and-data/warehouse-and-stocks-reports/stocks-summary"
    const val FBX = "https://www.freightos.com/enterprise/terminal/freightos-baltic-index-global-container-pricing-index/"
    const val BALTIC = "https://www.balticexchange.com/en/index.html"
    const val FINRA_MARGIN = "https://www.finra.org/rules-guidance/key-topics/margin-accounts/margin-statistics"

    enum class Gruppe { LADEN, DATEIEN, BROWSER }

    enum class Eintrag(val gruppe: Gruppe) {
        MELDUNGEN(Gruppe.LADEN), KONGRESS(Gruppe.LADEN), FINDER(Gruppe.LADEN), RADAR(Gruppe.LADEN), RUECKBLICK(Gruppe.LADEN),
        REGISTER(Gruppe.DATEIEN), LME_DATEI(Gruppe.DATEIEN), COMEX_DATEI(Gruppe.DATEIEN), DEPOT(Gruppe.DATEIEN), POSITION(Gruppe.DATEIEN),
        // Die einzelnen Browser-Seiten stehen im Selbstabruf unter „Nur zum Ansehen“
        SELBSTABRUF(Gruppe.BROWSER),
    }

    fun eintraege(g: Gruppe): List<Eintrag> = Eintrag.entries.filter { it.gruppe == g }

    // Zustand eines Eintrags; Keiner heißt: es gibt nichts zu melden, die Zeile zeigt nur den Namen.
    sealed interface Stand {
        data object Keiner : Stand
        data object Nie : Stand
        data object KeineDatei : Stand
        data object Alle : Stand
        data object Heute : Stand
        data object HeuteNie : Stand
        data class Laeuft(val prozent: Int?) : Stand
        data class Am(val ms: Long) : Stand
        data class Bis(val ms: Long) : Stand
        data class Monate(val n: Int) : Stand
        data class Offen(val n: Int) : Stand
    }

    // Text je Zustand; datum formatiert einen Zeitpunkt in Millisekunden.
    fun text(s: Stand, datum: (Long) -> String): String = when (s) {
        Stand.Keiner -> ""
        Stand.Nie -> S.anNie
        Stand.KeineDatei -> S.anKeineDatei
        Stand.Alle -> S.anAlle
        Stand.Heute -> S.anHeute
        Stand.HeuteNie -> S.anHeuteNie
        is Stand.Laeuft -> S.anLaeuft(s.prozent)
        is Stand.Am -> S.anAm(datum(s.ms))
        is Stand.Bis -> S.anBis(datum(s.ms))
        is Stand.Monate -> S.anMonate(s.n)
        is Stand.Offen -> S.anOffen(s.n)
    }

    // Prozent eines Fortschritts von 0 bis 1; ohne Angabe oder außerhalb null.
    fun prozent(anteil: Float?): Int? = anteil?.takeIf { it in 0f..1f }?.let { (it * 100).toInt() }

    // Meldungen der Abgeordneten: läuft das Lesen, zählt gelesen von gesamt; sonst die Zahl der noch ungelesenen
    // (null, solange das Verzeichnis nie geladen wurde).
    fun meldungen(laeuft: Boolean, gelesen: Int, gesamt: Int, offen: Int?): Stand = when {
        laeuft -> Stand.Laeuft(if (gesamt > 0) (gelesen.coerceIn(0, gesamt) * 100L / gesamt).toInt() else null)
        offen == null -> Stand.Nie
        offen <= 0 -> Stand.Alle
        else -> Stand.Offen(offen)
    }

    // Alles, was als Ganzes geladen wird: läuft (Anteil), geladen am Zeitpunkt oder noch nicht geladen.
    fun geladen(anteil: Float?, am: Long?): Stand = when {
        anteil != null -> Stand.Laeuft(prozent(anteil))
        am != null && am > 0 -> Stand.Am(am)
        else -> Stand.Nie
    }

    // Zahl der Kalendermonate, in denen eingelesene Handelstage (Tage seit 1970) liegen.
    fun monate(tage: Collection<Long>): Int = tage.mapTo(HashSet()) { LocalDate.ofEpochDay(it).let { d -> d.year * 12 + d.monthValue } }.size

    fun lme(tage: Collection<Long>): Stand = monate(tage).let { if (it == 0) Stand.KeineDatei else Stand.Monate(it) }

    // COMEX: letzter eingelesener Tag (Tage seit 1970), null ohne Datei.
    fun comex(letzterTag: Long?): Stand = if (letzterTag == null) Stand.KeineDatei else Stand.Bis(letzterTag * 86_400_000L)

    fun rueckblick(laeuft: Boolean, heuteErstellt: Boolean): Stand = when {
        laeuft -> Stand.Laeuft(null)
        heuteErstellt -> Stand.Heute
        else -> Stand.HeuteNie
    }
}
