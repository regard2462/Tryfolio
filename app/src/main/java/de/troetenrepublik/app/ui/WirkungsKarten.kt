package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Wirkungskette
import de.troetenrepublik.app.engine.Wirkungskette.Aussage
import de.troetenrepublik.app.engine.Wirkungskette.Fall
import de.troetenrepublik.app.engine.Wirkungskette.Lage
import de.troetenrepublik.app.engine.Wirkungskette.Reaktion
import de.troetenrepublik.app.engine.Wirkungskette.ZeilenArt
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.NasdaqKalender
import de.troetenrepublik.app.market.ohneAbbruch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.time.LocalDate
import kotlin.math.abs

// [Wirkungskette] Karten „Was die Zahlen bedeuten“ im Reiter Ausblick, Kurzverweis in der Terminliste und Zeile im
// Tagesrueckblick. Die Rechnung steckt in engine/Wirkungskette.kt, hier nur Darstellung.

private fun deutsch() = I18n.lang == Lang.DE

private fun wert(t: String) = NasdaqKalender.wertText(t, deutsch())

private fun postenText(p: Wirkungskette.Posten): String =
    if (p.lage == Lage.ERWARTET) S.wkWertGleich(p.art.name, wert(p.ist))
    else S.wkWertStatt(p.art.name, wert(p.ist), wert(p.referenz))

// Ueberschrift: Aussage in einer Zeile, danach der wichtigste Wert
private fun kopfText(a: Aussage): String {
    val k = a.kopf
    val satz = when (a.fall) {
        Fall.ZINS_STRAFFER, Fall.ZINS_LOCKERER, Fall.ZINS_ERWARTET -> S.wkZinsKopf(S.wkZentralbank(a.region.name), a.fall.name)
        Fall.GOLDILOCKS, Fall.HEISS, Fall.STAGFLATION, Fall.SCHWACH_ENTSPANNT, Fall.GEMISCHT, Fall.ERWARTET -> S.wkFallKopf(a.fall.name)
        else -> if (k != null) S.wkLage(k.art.thema.name, k.lage.name) else S.wkFallKopf(Fall.ERWARTET.name)
    }
    val zusatz = if (k != null && k.bezug == Wirkungskette.Bezug.VORWERT && a.fall != Fall.ZINS_STRAFFER && a.fall != Fall.ZINS_LOCKERER) S.wkVorwertZusatz else ""
    return satz + zusatz + (k?.let { ": " + postenText(it) }.orEmpty())
}

private fun sektorListe(liste: List<Wirkungskette.Sektor>): String {
    val n = liste.map { S.wkSektor(it.name) }
    return if (n.size <= 1) n.joinToString("") else n.dropLast(1).joinToString(", ") + " " + S.wkUnd + " " + n.last()
}

// Glieder der Kette in Reihenfolge Zins, Rendite, Waehrung, Sektoren
private fun kettenGlieder(a: Aussage): List<String> {
    val k = a.kette ?: return emptyList()
    val glieder = ArrayList<String>()
    k.zins?.let { glieder += S.wkZins(it.name, S.wkZentralbank(a.region.name)) }
    k.rendite?.let { glieder += S.wkRendite(it.name) }
    k.waehrung?.let { glieder += S.wkWaehrung(it.name, S.wkWaehrungName(a.region.name)) }
    val s = ArrayList<String>()
    if (k.belastet.isNotEmpty()) s += S.wkBelastet(sektorListe(k.belastet))
    if (k.gestuetzt.isNotEmpty()) s += S.wkGestuetzt(sektorListe(k.gestuetzt))
    if (s.isNotEmpty()) glieder += s.joinToString(", ")
    return glieder
}

private fun bpText(v: Double): String = (if (abs(v) < 0.05) "" else if (v > 0) "+" else "−") + Fmt.num(abs(v), 1) + " " + S.wkBp

private fun zeilenName(z: Wirkungskette.Zeile): String =
    if (z.art == ZeilenArt.SEKTOR) S.wkSektor(z.schluessel).replaceFirstChar { it.uppercase() } else S.wkMarkt(z.schluessel)

private fun zeilenWert(z: Wirkungskette.Zeile): String = if (z.bp) bpText(z.aenderung) else Fmt.signedPct(z.aenderung, 1)

private fun zinsText(zp: Wirkungskette.ZinsPfad): String = S.wkZinsFutures(Fmt.num(zp.vorher, 2) + " %", Fmt.num(zp.nachher, 2) + " %")

// Herleitung als Text des Info-Blatts
private fun blattText(a: Aussage, r: Reaktion?, zp: Wirkungskette.ZinsPfad?): String {
    val sb = StringBuilder()
    sb.append(S.wkBlattMeldungen).append('\n')
    for (p in a.posten) {
        val kopf = if (p.art == Wirkungskette.Art.ZINSENTSCHEID) S.wkZinsKopf(S.wkZentralbank(p.region.name), when (p.lage) {
            Lage.STAERKER -> Fall.ZINS_STRAFFER.name
            Lage.SCHWAECHER -> Fall.ZINS_LOCKERER.name
            else -> Fall.ZINS_ERWARTET.name
        }) else S.wkLage(p.art.thema.name, p.lage.name)
        sb.append("• ").append(kopf).append(": ").append(postenText(p))
        if (p.bezug == Wirkungskette.Bezug.VORWERT) sb.append(S.wkVorwertZusatz)
        sb.append('\n')
    }
    sb.append('\n').append(S.wkBlattHerleitung).append('\n')
    val k = a.kette
    if (k == null) sb.append(S.wkOhneFolge(a.fall.name)).append('\n')
    else {
        k.zins?.let { sb.append("• ").append(S.wkWarumZins(it.name)).append('\n') }
        k.rendite?.let { sb.append("• ").append(S.wkWarumRendite(it.name)).append('\n') }
        k.waehrung?.let { sb.append("• ").append(S.wkWarumWaehrung(it.name)).append('\n') }
        for (s in k.belastet + k.gestuetzt) sb.append("• ").append(S.wkWarumSektor(s.name)).append('\n')
    }
    if (r != null && r.zeilen.isNotEmpty()) {
        sb.append('\n').append(S.wkBlattReaktion).append('\n')
        for (z in r.zeilen) sb.append("• ").append(zeilenName(z)).append(": ").append(zeilenWert(z)).append('\n')
        zp?.let { sb.append("• ").append(zinsText(it)).append('\n') }
        if (r.sektoren.isNotEmpty()) {
            sb.append('\n').append(S.wkBlattSektoren).append('\n')
            for (z in r.sektoren) sb.append("• ").append(zeilenName(z)).append(": ").append(zeilenWert(z)).append('\n')
        }
        sb.append(S.wkUrteil(r.urteil.name).replaceFirstChar { it.uppercase() }).append(". ").append(S.wkReaktionErklaerung).append('\n')
    }
    sb.append('\n').append(S.wkBlattGrenzen).append('\n').append(S.wkGrenzen).append('\n')
    sb.append('\n').append(S.wkBlattBelege).append('\n').append(S.wkBelege).append('\n')
    sb.append('\n').append(S.wkQuelle)
    return sb.toString()
}

// Abschnitt im Ausblick; zeigt nichts, solange es keine verwertbaren Meldungen gibt
@Composable
internal fun WirkungAbschnitt(state: AppState) {
    var aussagen by remember { mutableStateOf<List<Aussage>>(emptyList()) }
    var kurse by remember { mutableStateOf<Map<String, List<Candle>>>(emptyMap()) }
    LaunchedEffect(Unit) {
        val a = ohneAbbruch { state.wirkungAussagen() }.getOrNull().orEmpty()
        currentCoroutineContext().ensureActive()
        // hoechstens sechs Karten, US zuerst innerhalb des Tages (Reihenfolge der Engine)
        aussagen = a.take(6)
        if (aussagen.isEmpty()) return@LaunchedEffect
        kurse = ohneAbbruch { state.wirkungKurse(aussagen.map { it.region }.toSet()) }.getOrNull().orEmpty()
    }
    if (aussagen.isEmpty()) return
    val heute = remember { java.time.LocalDate.now(java.time.ZoneId.of("America/New_York")) }
    Column {
        MarktInfoKopf(S.wkTitel, S.wkInfo)
        for (a in aussagen) WirkungKarte(a, kurse, heute)
        QuellenZeile(S.wkQuelle)
    }
}

@Composable
private fun WirkungKarte(a: Aussage, kurse: Map<String, List<Candle>>, heute: LocalDate) {
    val signal = LocalSignal.current
    var info by remember { mutableStateOf(false) }
    val reaktion = remember(a, kurse) { if (kurse.isEmpty()) null else Wirkungskette.reaktion(a, kurse) }
    val zp = remember(a, kurse) {
        if (a.region != Wirkungskette.Region.US || kurse.isEmpty()) null
        else {
            val h = LocalDate.now(java.time.ZoneOffset.UTC)
            Wirkungskette.zinsPfad(kurse, Wirkungskette.zinsKontrakte(h.year, h.monthValue), a.tag)
        }
    }
    val tag = LocalDate.ofEpochDay(a.tag)
    val land = a.kopf?.land?.let { nkLandName(it) }.orEmpty()
    val glieder = remember(a, I18n.lang) { kettenGlieder(a) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button) { info = true }.padding(16.dp),
    ) {
        PunktText(
            listOf(land, if (tag == heute) S.today else wochentag(tag) + " " + Fmt.dayMonthNum(mittag(tag))).filter { it.isNotBlank() }.joinToString(PUNKT),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(kopfText(a), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 2.dp))
        Text(
            if (glieder.isEmpty()) S.wkOhneFolge(a.fall.name) else glieder.joinToString(" → "),
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp),
        )
        if (a.posten.size > 1) {
            Column(Modifier.padding(top = 8.dp)) {
                for (p in a.posten.take(4)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(S.wkArt(p.art.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            wert(p.ist) + PUNKT + (if (p.bezug == Wirkungskette.Bezug.ERWARTUNG) S.qzErwartet(wert(p.referenz)) else S.nkVorher(wert(p.referenz))),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false,
                        )
                    }
                }
            }
        }
        if (reaktion != null && reaktion.zeilen.isNotEmpty()) {
            Text(S.wkReaktion, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            for (z in reaktion.zeilen) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(zeilenName(z), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    Text(zeilenWert(z), style = MaterialTheme.typography.bodyMedium, color = signal.forChange(if (abs(z.aenderung) < 0.05) 0.0 else z.aenderung), maxLines = 1, softWrap = false)
                }
            }
            zp?.let { Text(zinsText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)) }
            Text(S.wkUrteil(reaktion.urteil.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
    if (info) MarktInfoBlatt(kopfText(a), blattText(a, reaktion, zp)) { info = false }
}

// Kurzverweis unter einem veroeffentlichten Wirtschaftstermin: erstes Glied der Kette, nur bei Abweichung
@Composable
internal fun WirkungBedeutung(t: NasdaqKalender.Wirtschaftstermin) {
    if (t.ist == null) return
    val a = remember(t) { Wirkungskette.einzel(t) } ?: return
    val folge = a.kette?.zins ?: return
    Text(
        S.wkBedeutung(S.wkZins(folge.name, S.wkZentralbank(a.region.name))),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp),
    )
}

// Zeile im Tagesrueckblick: Gesamtaussage der Wirtschaftsdaten des Tages, wenn es eine gab
@Composable
internal fun WirkungTageszeile(state: AppState, tag: Long) {
    var text by remember(tag, I18n.lang) { mutableStateOf<String?>(null) }
    LaunchedEffect(tag, I18n.lang) {
        val l = ohneAbbruch { state.wirkungAussagenTag(LocalDate.ofEpochDay(tag)) }.getOrNull().orEmpty()
        currentCoroutineContext().ensureActive()
        val a = l.firstOrNull { it.fall != Fall.ERWARTET && it.fall != Fall.GEMISCHT && it.fall != Fall.ZINS_ERWARTET }
        text = a?.let {
            val folge = it.kette?.zins?.let { z -> S.wkZins(z.name, S.wkZentralbank(it.region.name)) }
            S.wkRueckblick(kopfText(it).substringBefore(": "), folge)
        }
    }
    text?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
}
