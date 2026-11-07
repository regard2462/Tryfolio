package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
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
    val katastrophen: List<Katastrophen.Ereignis> = emptyList(),
    val wahl: Wahlen.Wahl? = null,
    val struktur: List<Laenderrisiken.Hinweis> = emptyList(),
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

// Abschnitt Risiken im Laenderblatt: Regierungsfuehrung, Reisehinweise, Sanktionen, Kreditratings, Konflikte,
// Naturkatastrophen, Wahlen und strukturelle Hinweise. Jede Quelle laedt fuer sich, die Zeilen erscheinen nach und nach.
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
            launch { ohneAbbruch { state.risikoKonflikte() }.getOrNull()?.let { d = d.copy(konflikte = it) } }
            launch { ohneAbbruch { state.risikoKatastrophen()?.let { Katastrophen.fuer(it, land.a3, heute) } }.getOrNull()?.let { d = d.copy(katastrophen = it) } }
            launch { ohneAbbruch { state.risikoWahlen()?.let { Wahlen.fuer(it, land, heute) } }.getOrNull()?.let { d = d.copy(wahl = it) } }
            launch {
                val h = ohneAbbruch {
                    val r = state.weltbankViele(listOf(Laenderrisiken.KENN_RESERVEN, Laenderrisiken.KENN_BRENNSTOFF, Laenderrisiken.KENN_ERZE,
                        Laenderrisiken.KENN_JUGEND, Laenderrisiken.KENN_ALTER, Laenderrisiken.KENN_AUSLAND))
                    Laenderrisiken.struktur(
                        land.a3,
                        r[Laenderrisiken.KENN_RESERVEN], r[Laenderrisiken.KENN_BRENNSTOFF], r[Laenderrisiken.KENN_ERZE],
                        r[Laenderrisiken.KENN_JUGEND], r[Laenderrisiken.KENN_ALTER], r[Laenderrisiken.KENN_AUSLAND],
                    )
                }.getOrNull()
                if (!h.isNullOrEmpty()) d = d.copy(struktur = h)
            }
        }
    }

    val zeile: @Composable (String, String, String) -> Unit = { label, wert, text -> KeyValueRow(label, wert, onClick = { info = label to text }) }
    val deutsch = deutsch()

    Text(S.globusRisiken, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp))

    if (d.wgi.isNotEmpty()) {
        RisikoTitel(S.rkRegierung)
        for ((k, r) in d.wgi) zeile(S.rkWgiName(k), S.rkPerzentil(r.perzentil, r.jahr), S.rkWgiInfo(k))
        RisikoQuelle(S.rkQuelleWgi)
    }

    RisikoTitel(S.rkReise)
    d.us?.let { zeile(S.rkUsLabel, S.rkUsStufe(it), S.rkUsInfo) }
    // Das Auswaertige Amt oeffnet in der Ansicht der App (AppLinks), ohne Info-Blatt; Warnstufe nur, wenn die Schnittstelle sie liefert.
    val aaWert = when {
        d.aa?.warnung?.contains(land.a2) == true -> S.rkAaWarnung
        d.aa?.teilwarnung?.contains(land.a2) == true -> S.rkAaTeil
        else -> S.rkAaLink
    }
    // Fuer Deutschland selbst gibt es keine Reise- und Sicherheitshinweise.
    if (land.a2 != "DE") KeyValueRow(S.rkAaLabel, aaWert, onClick = { runCatching { uri.openUri(Reisehinweise.AA_SEITE) } })
    RisikoQuelle(S.rkQuelleReise)

    val eu = d.liste?.eu?.get(land.a2).orEmpty()
    val us = d.liste?.us?.get(land.a2).orEmpty()
    if (eu.isNotEmpty() || us.isNotEmpty() || d.os != null) {
        RisikoTitel(S.rkSanktionen)
        val stand = d.liste?.stand.orEmpty()
        if (eu.isNotEmpty()) {
            val namen = eu.map { if (deutsch) it.de else it.en }
            zeile(S.rkEuLabel, if (eu.size == 1) namen[0] else S.rkRegimeAnzahl(eu.size), S.rkEuInfo(namen, stand))
        }
        if (us.isNotEmpty()) zeile(S.rkUsProgrammLabel, if (us.size == 1) us[0] else S.rkProgrammeAnzahl(us.size), S.rkUsProgrammInfo(us, stand))
        d.os?.let { zeile(S.rkOsLabel, Fmt.num(it.toDouble(), 0), S.rkOsInfo) }
        RisikoQuelle(S.rkQuelleSanktionen)
    }

    if (d.ratings.isNotEmpty()) {
        RisikoTitel(S.rkRatings)
        val grenze = LocalDate.now().year - 1
        for (n in d.ratings) {
            val wert = listOf(n.note, S.rkAusblick(n.ausblick)).filter { it.isNotEmpty() }.joinToString(", ")
            zeile(n.agentur, mitJahr(wert, n.jahr.takeIf { it in 1 until grenze }), S.rkRatingInfo)
        }
        RisikoQuelle(S.rkQuelleRatings)
    }

    val konflikte = d.konflikte?.let { Konflikte.fuer(it, land) }.orEmpty()
    if (konflikte.isNotEmpty()) {
        val jahr = d.konflikte?.jahr ?: 0
        RisikoTitel(S.rkKonflikte)
        for (k in konflikte) zeile(
            S.rkKonfliktArt(k.stufe), S.rkKonfliktGegner(Konflikte.seite(k.seiteA, deutsch), Konflikte.seite(k.seiteB, deutsch)), S.rkKonfliktInfo(k.stufe, jahr),
        )
        RisikoQuelle(S.rkQuelleKonflikte(jahr, d.konflikte?.version.orEmpty()))
    }

    if (d.katastrophen.isNotEmpty()) {
        RisikoTitel(S.rkKatastrophen)
        for (e in d.katastrophen.take(5)) zeile(S.rkKatArt(e.typ), S.rkKatStufe(e.rot, tagText(e.von)), S.rkKatInfo)
        RisikoQuelle(S.rkQuelleKat)
    }

    d.wahl?.let { w ->
        RisikoTitel(S.rkWahlen)
        val p = w.parlament
        val r = w.praesident
        if (p != null && r != null && p.jahr == r.jahr && p.monat == r.monat && p.tag == r.tag) zeile(S.rkWahlBeide, datumText(p), S.rkWahlInfo)
        else {
            p?.let { zeile(S.rkWahlParlament, datumText(it), S.rkWahlInfo) }
            r?.let { zeile(S.rkWahlPraesident, datumText(it), S.rkWahlInfo) }
        }
        RisikoQuelle(S.rkQuelleWahl)
    }

    if (d.struktur.isNotEmpty()) {
        RisikoTitel(S.rkStruktur)
        for (h in d.struktur) {
            val zahl = Fmt.num(h.wert, if (h.art == Laenderrisiken.Art.ROHSTOFF) 0 else 1)
            val extra = when (h.art) {
                Laenderrisiken.Art.ROHSTOFF -> S.rkRohstoffAufteilung(Fmt.num(h.a ?: 0.0, 0), Fmt.num(h.b ?: 0.0, 0))
                Laenderrisiken.Art.JUGEND -> h.b?.let { Fmt.pct(it, 1) }
                else -> null
            }
            zeile(S.rkStrukturLabel(h.art), mitJahr(S.rkStrukturWert(h.art, zahl), h.jahr), S.rkStrukturInfo(h.art, extra))
        }
        RisikoQuelle(S.rkQuelleStruktur)
    }

    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
}

@Composable
private fun RisikoTitel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 2.dp))
}

@Composable
private fun RisikoQuelle(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}
