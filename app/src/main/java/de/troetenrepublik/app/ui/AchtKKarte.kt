package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.AchtK
import de.troetenrepublik.app.market.AchtKAbruf
import de.troetenrepublik.app.market.Politik
import de.troetenrepublik.app.market.SecInsider
import de.troetenrepublik.app.plattform.SichereDatei
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset

// Warnzeichen und Übernahmen aus den 8-K-Meldungen bei der SEC [0.77]: zwei Abschnitte im Hintergrund einer US-Aktie,
// jeder nur mit mindestens einem Eintrag. Die Karte lädt erst beim Öffnen des Hintergrunds (einmal je Seite, das Ergebnis
// bleibt zwölf Stunden gemerkt) und rechnet; die Zeilen kommen als Einträge der Seite (achtkAbschnitte).
@Composable
internal fun AchtKKarte(state: AppState, symbol: String, merker: SeitenMerker? = null) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val sec by state.sec.collectAsStateWithLifecycle()
    var erg by gemerkt<AchtKAbruf.Ergebnis?>(merker, "achtk/erg") { AchtKAbruf.gemerkt(symbol) }
    var kerzen by gemerkt<List<Candle>>(merker, "achtk/kerzen") { emptyList() }
    var geladen by gemerkt(merker, "achtk/geladen") { false }
    LaunchedEffect(symbol, settings.secKontakt) {
        if (geladen) return@LaunchedEffect
        val kennung = SecInsider.kennung(settings.secKontakt) ?: return@LaunchedEffect
        try {
            val cik = sec?.geschaefte?.firstOrNull { it.k.equals(symbol, true) }?.c?.ifBlank { null }
            val r = withContext(Dispatchers.Default) {
                val datei = runCatching { SichereDatei(java.io.File(state.store.ordner, "achtk_gelesen.json")) }.getOrNull()
                runCatching { AchtKAbruf.laden(symbol, kennung, cik, LocalDate.now(ZoneOffset.UTC), datei) }.getOrNull()
            }
            erg = r
            if (r != null && r.vorgaenge.isNotEmpty()) kerzen = runCatching { state.gesamtHistorie(symbol) }.getOrDefault(emptyList())
        } finally {
            if (isActive) geladen = true
        }
    }
    val uri = LocalUriHandler.current
    val geschaefte = sec?.geschaefte
    val warn = remember(erg, geschaefte) { erg?.takeIf { it.anzahl > 0 }?.let { warnModell(it, geschaefte.orEmpty(), symbol, uri, state) } }
    val deal = remember(erg, kerzen) { erg?.takeIf { it.vorgaenge.isNotEmpty() }?.let { dealModell(it, kerzen, uri) } }
    // Das Thema erscheint in der Zusammenfassung des Hintergrunds, sobald Daten da sind
    val warnDa = merker?.zustand<Boolean>("achtk/warn") { false }
    val dealDa = merker?.zustand<Boolean>("achtk/deal") { false }
    SideEffect {
        if (warnDa != null && warnDa.value != (warn != null)) warnDa.value = warn != null
        if (dealDa != null && dealDa.value != (deal != null)) dealDa.value = deal != null
    }
    ModellAusgabe(merker, "achtk-warn", warn)
    ModellAusgabe(merker, "achtk-deal", deal)
}

internal fun LazyListScope.achtkAbschnitte(merker: SeitenMerker) {
    listenAbschnitt(merker, "achtk-warn")
    listenAbschnitt(merker, "achtk-deal")
}

private fun tagText(iso: String): String = Politik.tag(iso).takeIf { it > 0 }?.let { Fmt.date(it) } ?: iso

private fun adresse(cik: String, accession: String, datei: String) = SecInsider.archivAdresse(cik, accession, datei)

private fun warnModell(erg: AchtKAbruf.Ergebnis, geschaefte: List<SecInsider.Geschaeft>, symbol: String, uri: androidx.compose.ui.platform.UriHandler, state: AppState): ListenModell {
    val liste = erg.warnzeichen
    // Eine Erklaerung je Einordnung: bei Notierungsmeldungen die gelesene Unterart, sonst die Art
    val schluessel = liste.map { it.detail?.unterart?.name ?: it.art.name }.distinct()
    return ListenModell(
        titel = S.achtWarnTitel(erg.anzahl), gross = true, kern = null,
        widgetState = state, widget = de.troetenrepublik.app.data.WidgetVorgabe.warnzeichen(symbol),
        info = S.achtInfoWarn + schluessel.joinToString("") { k -> "\n\n" + S.achtArt(k) + ": " + S.achtErkl(k) },
        zeilen = liste.map { w ->
            val davor = if (AchtK.zaehlt(w)) AchtK.insiderDavor(geschaefte, symbol, w.tag) else null
            val unter = w.detail?.unterart
            ZeilenDaten(w.accession + "-" + w.art.name) {
                AchtKZeile(
                    titel = when {
                        unter != null -> S.achtArt(unter.name)
                        w.art == AchtK.Art.FUEHRUNG -> S.achtRolle(w.rolle.name)
                        else -> S.achtArt(w.art.name)
                    },
                    ton = when {
                        unter == AchtK.Unterart.WECHSEL -> Ton.NEUTRAL
                        unter == AchtK.Unterart.ENTWARNUNG -> Ton.GUT
                        w.art.schwer -> Ton.WARNUNG
                        else -> Ton.NORMAL
                    },
                    angaben = listOfNotNull(
                        listOf(tagText(w.tag)),
                        detailText(w)?.let { listOf(it) },
                        detailFrist(w)?.let { listOf(it) },
                        davor?.let { listOf(S.achtInsider(it.verkaeufer), Fmt.dollarKompakt(it.summe)) },
                    ),
                    onClick = { runCatching { uri.openUri(adresse(erg.cik, w.accession, w.datei)) } },
                )
            }
        },
    )
}

// Satz zu den gelesenen Angaben einer Meldung; null, wenn nichts sicher gelesen ist.
private fun detailText(w: AchtK.Warnzeichen): String? {
    val d = w.detail ?: return null
    fun tag(iso: String) = tagText(iso)
    return when (w.art) {
        AchtK.Art.DELISTING -> {
            if (d.unterart == AchtK.Unterart.WECHSEL || d.grund == null) null
            else {
                val zusatz = d.berichte.joinToString(", ") { b -> b.substringBefore(';') .let { f -> S.achtBericht(f, b.substringAfter(';', "")) } }
                S.achtGrund(d.grund.name, zusatz)
            }
        }
        AchtK.Art.FUEHRUNG -> d.ereignis?.let { S.achtFuehrungDetail(d.person, it.name, d.sofort, if (d.wirksam.isEmpty()) "" else tag(d.wirksam), d.nachfolge) }
        AchtK.Art.PRUEFER -> d.ereignis?.let { S.achtPrueferDetail(d.pruefer, d.neuerPruefer, it.name, d.streit, d.maengel) }
        AchtK.Art.KORREKTUR -> if (d.jahrVon > 0) S.achtKorrekturDetail(d.jahrVon, d.jahrBis) else null
        AchtK.Art.INSOLVENZ -> if (d.kapitel > 0) S.achtInsolvenzDetail(d.kapitel) else null
        AchtK.Art.ABSCHREIBUNG -> if (d.betrag > 0) S.achtAbschreibungDetail(Fmt.dollarKompakt(d.betrag)) else null
        AchtK.Art.VERBINDLICHKEIT -> null
    }
}

// Frist einer Notierungsmeldung (Datum oder Zahl der Tage).
private fun detailFrist(w: AchtK.Warnzeichen): String? {
    val f = w.detail?.frist.orEmpty()
    return when {
        f.isEmpty() -> null
        f.endsWith("d") -> f.dropLast(1).toIntOrNull()?.let { S.achtFristTage(it) }
        else -> S.achtFristDatum(tagText(f))
    }
}

private fun dealModell(erg: AchtKAbruf.Ergebnis, kerzen: List<Candle>, uri: androidx.compose.ui.platform.UriHandler): ListenModell {
    val liste = erg.vorgaenge
    val arten = liste.map { it.art }.distinct().sortedBy { it.ordinal }
    return ListenModell(
        titel = S.achtDealTitel(liste.map { it.gruppe }.distinct().size), gross = true, kern = null,
        info = S.achtInfoDeal + arten.joinToString("") { a -> "\n\n" + S.achtArt(a.name) + ": " + S.achtErkl(a.name) },
        zeilen = liste.map { v ->
            val reaktion = if (kerzen.isEmpty()) null else AchtK.reaktion(kerzen, v.tag)
            ZeilenDaten(v.accession + "-" + v.art.name) {
                AchtKZeile(
                    titel = S.achtArt(v.art.name), ton = Ton.NORMAL,
                    angaben = listOf(
                        listOfNotNull(
                            tagText(v.tag), v.gegenseite?.let { S.achtGegenseite(it) },
                            v.preis?.let { S.achtPreis(Fmt.price(it, "$")) },
                        ),
                    ),
                    onClick = { runCatching { uri.openUri(adresse(erg.cik, v.accession, v.datei)) } },
                ) {
                    if (reaktion != null) {
                        KursZeile(S.achtKursTag, reaktion.tag0)
                        reaktion.tag5?.let { KursZeile(S.achtKurs5, it) }
                    }
                }
            }
        },
    )
}

private enum class Ton { NORMAL, WARNUNG, GUT, NEUTRAL }

// Zeile eines Eintrags: Art oben (schwere Zeichen in der Warnfarbe), darunter je eine Punktzeile mit Datum und
// Angaben, zuletzt die Kursbewegung.
@Composable
private fun AchtKZeile(titel: String, ton: Ton, angaben: List<List<String>>, onClick: () -> Unit, unten: @Composable () -> Unit = {}) {
    val farbe = when (ton) {
        Ton.WARNUNG -> LocalSignal.current.warn
        Ton.GUT -> LocalSignal.current.up
        Ton.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        Ton.NORMAL -> MaterialTheme.colorScheme.onSurface
    }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(titel, style = MaterialTheme.typography.titleSmall, color = farbe)
        angaben.forEach { PunktZeile(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        unten()
    }
}

// Bezeichnung links, Veränderung in Prozent mit Dreieck rechts, beides in einer Zeile.
@Composable
private fun KursZeile(label: String, prozent: Double) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        ChangeValue(prozent, Fmt.signedPct(prozent, 1), style = MaterialTheme.typography.bodySmall, triangleWidth = 8.dp)
    }
}
