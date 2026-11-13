package de.troetenrepublik.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.lerp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.GlobusKarte
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.engine.Katastrophen
import de.troetenrepublik.app.engine.Konflikte
import de.troetenrepublik.app.engine.Kreditratings
import de.troetenrepublik.app.engine.Laenderrisiken
import de.troetenrepublik.app.engine.Reisehinweise
import de.troetenrepublik.app.engine.Sanktionen
import de.troetenrepublik.app.engine.Wahlen
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.ohneAbbruch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// Alles, was der Abschnitt zeigt; Teile kommen nacheinander an, fehlende bleiben leer.
private data class Daten(
    val wgi: List<Pair<String, Laenderrisiken.Rang>> = emptyList(),
    val us: Int? = null,
    val aa: Reisehinweise.AaStand? = null,
    val liste: Sanktionen.Liste? = null,
    val os: Int? = null,
    val ratings: List<Kreditratings.Note> = emptyList(),
    val konflikte: Konflikte.Stand? = null,
    val karte: GlobusKarte? = null,
    val katastrophen: List<Katastrophen.Ereignis> = emptyList(),
    val wahl: Wahlen.Wahl? = null,
)

private fun deutsch() = I18n.locale.language == "de"

private fun datumText(d: Wahlen.Datum): String = when {
    d.monat == 0 -> d.jahr.toString()
    d.tag == 0 -> YearMonth.of(d.jahr, d.monat).format(DateTimeFormatter.ofPattern("MMMM yyyy", I18n.locale))
    else -> LocalDate.of(d.jahr, d.monat, d.tag).format(DateTimeFormatter.ofPattern(if (deutsch()) "dd.MM.yyyy" else "d MMM yyyy", I18n.locale))
}

private fun tagText(iso: String): String =
    runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofPattern(if (deutsch()) "dd.MM." else "d MMM", I18n.locale)) }.getOrDefault(iso)

private fun mitJahr(wert: String, jahr: Int?) = if (jahr == null) wert else "$wert ($jahr)"

// Abschnitt Risiken im Laenderblatt: Regierungsfuehrung als Balken, Konflikte, Kreditratings, Reisehinweise und Sanktionen;
// Naturkatastrophen und Wahlen aufklappbar. Jede Quelle laedt fuer sich, die Zeilen erscheinen nach und nach. Die
// strukturellen Hinweise stehen im Abschnitt "Strukturelle Probleme" (LaenderStrukturen.kt).
@Composable
internal fun LaenderRisiken(state: AppState, land: GlobusLand) {
    var d by remember(land) { mutableStateOf(Daten()) }
    var info by remember(land) { mutableStateOf<Pair<String, String>?>(null) }
    val uri = appLinks()

    LaunchedEffect(land) {
        d = Daten()
        val heute = LocalDate.now()
        coroutineScope {
            launch {
                val r = ohneAbbruch {
                    val reihen = state.weltbankViele(Laenderrisiken.WGI.map { Laenderrisiken.wgiKennung(it) }, quelle = 3)
                    Laenderrisiken.WGI.mapNotNull { k ->
                        reihen[Laenderrisiken.wgiKennung(k)]?.let { Laenderrisiken.perzentil(it, land.a3) }?.let { k to it }
                    }
                }.getOrNull()
                if (!r.isNullOrEmpty()) d = d.copy(wgi = r)
            }
            launch { ohneAbbruch { state.risikoUs()?.let { Reisehinweise.usStufe(it, land) } }.getOrNull()?.let { d = d.copy(us = it) } }
            launch { ohneAbbruch { state.risikoAa() }.getOrNull()?.let { d = d.copy(aa = it) } }
            launch { ohneAbbruch { state.risikoSanktionenListe() }.getOrNull()?.let { d = d.copy(liste = it) } }
            launch { ohneAbbruch { state.risikoOpenSanctions()?.laender?.get(land.a2) }.getOrNull()?.let { d = d.copy(os = it) } }
            launch { ohneAbbruch { state.risikoRatings()?.let { Kreditratings.fuer(it, land) } }.getOrNull()?.let { d = d.copy(ratings = it) } }
            launch {
                val karte = ohneAbbruch { state.globusKarte() }.getOrNull()
                ohneAbbruch { state.risikoKonflikte() }.getOrNull()?.let { d = d.copy(konflikte = it, karte = karte) }
            }
            launch { ohneAbbruch { state.risikoKatastrophen()?.let { Katastrophen.fuer(it, land.a3, heute) } }.getOrNull()?.let { d = d.copy(katastrophen = it) } }
            launch { ohneAbbruch { state.risikoWahlen()?.let { Wahlen.fuer(it, land, heute) } }.getOrNull()?.let { d = d.copy(wahl = it) } }
        }
    }

    val zeile: @Composable (String, String, String) -> Unit = { label, wert, text -> LbZeile(label, wert) { info = label to text } }
    val deutsch = deutsch()
    val signal = LocalSignal.current
    val quellen = ArrayList<Pair<String, String>>()

    LaenderAbschnitt(S.globusRisiken, "risiken") {
        if (d.wgi.isNotEmpty()) {
            LbUnterkopf(S.rkRegierung)
            BalkenListe(d.wgi.map { (k, r) ->
                BalkenZeile(S.rkWgiName(k), r.perzentil / 100f, mitJahr(r.perzentil.toString(), r.jahr), lerp(signal.down, signal.up, r.perzentil / 100f)) { info = S.rkWgiName(k) to S.rkWgiInfo(k) }
            })
            LbHinweis(S.lbWgiHinweis)
            quellen += LaenderQuellen.wgi()
        }

        val konflikte = d.konflikte?.let { Konflikte.fuer(it, land) }.orEmpty()
        if (konflikte.isNotEmpty()) {
            val jahr = d.konflikte?.jahr ?: 0
            LbUnterkopf(S.rkKonflikte)
            for (k in konflikte) LbAbsatz(
                S.lbKonfliktZeile(k.stufe, Konflikte.seiteAnzeige(k.seiteA, d.karte, deutsch), Konflikte.seiteAnzeige(k.seiteB, d.karte, deutsch)),
                onClick = { info = S.rkKonfliktArt(k.stufe) to S.rkKonfliktInfo(k.stufe, jahr) },
            )
            quellen += LaenderQuellen.ucdp()
        }

        if (d.ratings.isNotEmpty()) {
            LbUnterkopf(S.rkRatings)
            val grenze = LocalDate.now().year - 1
            for (n in d.ratings) {
                val wert = listOf(n.note, S.rkAusblick(n.ausblick)).filter { it.isNotEmpty() }.joinToString(", ")
                zeile(n.agentur, mitJahr(wert, n.jahr.takeIf { it in 1 until grenze }), S.rkRatingInfo)
            }
            quellen += LaenderQuellen.wikipedia("https://en.wikipedia.org/wiki/List_of_countries_by_credit_rating")
        }

        val aaWert = when {
            d.aa?.warnung?.contains(land.a2) == true -> S.rkAaWarnung
            d.aa?.teilwarnung?.contains(land.a2) == true -> S.rkAaTeil
            else -> S.rkAaLink
        }
        if (d.us != null || land.a2 != "DE") LbUnterkopf(S.rkReise)
        d.us?.let { zeile(S.rkUsLabel, S.rkUsStufe(it), S.rkUsInfo); quellen += LaenderQuellen.usReise() }
        // Das Auswaertige Amt oeffnet in der Ansicht der App (AppLinks), ohne Info-Blatt; Warnstufe nur, wenn die Schnittstelle sie liefert.
        // Fuer Deutschland selbst gibt es keine Reise- und Sicherheitshinweise.
        if (land.a2 != "DE") { LbZeile(S.rkAaLabel, aaWert) { runCatching { uri.openUri(Reisehinweise.AA_SEITE) } }; quellen += LaenderQuellen.auswaertiges() }

        val eu = d.liste?.eu?.get(land.a2).orEmpty()
        val us = d.liste?.us?.get(land.a2).orEmpty()
        if (eu.isNotEmpty() || us.isNotEmpty() || d.os != null) {
            LbUnterkopf(S.rkSanktionen)
            val stand = d.liste?.stand.orEmpty()
            if (eu.isNotEmpty()) {
                val namen = eu.map { if (deutsch) it.de else it.en }
                zeile(S.rkEuLabel, S.rkRegimeAnzahl(eu.size), S.rkEuInfo(namen, stand))
                quellen += LaenderQuellen.euSanktionen()
            }
            if (us.isNotEmpty()) { zeile(S.rkUsProgrammLabel, S.rkProgrammeAnzahl(us.size), S.rkUsProgrammInfo(us, stand)); quellen += LaenderQuellen.ofac() }
            d.os?.let { zeile(S.rkOsLabel, Fmt.num(it.toDouble(), 0), S.rkOsInfo); quellen += LaenderQuellen.openSanctions() }
        }

        val weitere = d.katastrophen.isNotEmpty() || d.wahl != null
        if (d.katastrophen.isNotEmpty()) quellen += LaenderQuellen.gdacs()
        if (d.wahl != null) quellen += LaenderQuellen.wikipedia("https://en.wikipedia.org/wiki/List_of_next_general_elections")
        if (weitere) LaenderAbschnitt(S.lbRisikoWeitere, "risiken-weitere", startOffen = false, tief = true) {
            if (d.katastrophen.isNotEmpty()) {
                LbUnterkopf(S.rkKatastrophen)
                var alleKat by remember(land.a2) { mutableStateOf(false) }
                for (e in if (alleKat) d.katastrophen else d.katastrophen.take(5)) zeile(S.rkKatArt(e.typ), S.rkKatStufe(e.rot, tagText(e.von)), S.rkKatInfo)
                if (d.katastrophen.size > 5) ListenPfeil(alleKat, label = if (alleKat) S.less else S.bbAlle(d.katastrophen.size)) { alleKat = !alleKat }
            }
            d.wahl?.let { w ->
                LbUnterkopf(S.rkWahlen)
                val p = w.parlament
                val r = w.praesident
                if (p != null && r != null && p.jahr == r.jahr && p.monat == r.monat && p.tag == r.tag) zeile(S.rkWahlBeide, datumText(p), S.rkWahlInfo)
                else {
                    p?.let { zeile(S.rkWahlParlament, datumText(it), S.rkWahlInfo) }
                    r?.let { zeile(S.rkWahlPraesident, datumText(it), S.rkWahlInfo) }
                }
            }
        }
        QuellenLeiste(quellen.distinctBy { it.first })
    }

    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
}
