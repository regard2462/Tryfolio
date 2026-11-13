package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.firmaGeschichte
import de.troetenrepublik.app.firmaGeschichteGespeichert
import de.troetenrepublik.app.i18n.GeschichteTexte
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.AchtK
import de.troetenrepublik.app.market.AchtKAbruf
import de.troetenrepublik.app.market.FirmaGeschichte
import de.troetenrepublik.app.market.FirmaGeschichte.Art
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.time.YearMonth

// Firmengeschichte als Zeitleiste (Wertpapierseite, Hintergrund): Gruendung, Boersengang, Uebernahmen, Namen, Kuerzel,
// Sitz, Splits, CEO und Insolvenz. Die Karte laedt erst, wenn der Hintergrund offen ist; ohne Ereignisse entfaellt der
// Abschnitt. Die neuesten Eintraege stehen da, aeltere sind eingeklappt. Ereignisse mit genauem Tag zeigen die
// Kursreaktion dieses Tages aus den vorhandenen Kursdaten.
@Composable
internal fun GeschichteKarte(state: AppState, symbol: String, name: String, merker: SeitenMerker? = null) {
    var daten by gemerkt<FirmaGeschichte.Daten?>(merker, "geschichte/daten") { null }
    var kerzen by gemerkt<List<Candle>>(merker, "geschichte/kerzen") { emptyList() }
    var geladen by gemerkt(merker, "geschichte/geladen") { false }
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    // Die 8-K-Meldungen kommen aus dem Abruf der Karte „Uebernahmen“; mit ihrem Eintreffen rechnet die Zeitleiste neu
    val achtK by AchtKAbruf.schwere.collectAsStateWithLifecycle()
    val achtStand = achtK[symbol]
    val deutsch = I18n.lang == Lang.DE
    LaunchedEffect(symbol, settings.secKontakt, deutsch) {
        val lade: suspend CoroutineScope.() -> Unit = {
            try {
                if (daten == null) daten = withContext(Dispatchers.IO) { state.firmaGeschichteGespeichert(symbol) }
                val neu = state.firmaGeschichte(symbol, name)
                if (neu != null) daten = neu
                if (daten?.ereignisse?.any { it.taggenau } == true) kerzen = state.gesamtHistorie(symbol)
            } finally {
                if (isActive) geladen = true
            }
        }
        if (geladen) return@LaunchedEffect
        if (merker != null) merker.abrufBisFertig("geschichte", { geladen }, lade) else lade()
    }
    val alle = remember(daten, achtStand) {
        val d = daten
        val acht = AchtKAbruf.gemerkt(symbol)?.let { FirmaGeschichte.ausAchtK(it) }.orEmpty()
        if (d == null && acht.isEmpty()) emptyList() else FirmaGeschichte.zusammenfuehren(d?.ereignisse.orEmpty() + acht, emptyList())
    }
    val modell = remember(alle, kerzen, deutsch) { if (alle.isEmpty()) null else geschichteModell(alle, kerzen) }
    ModellAusgabe(merker, "geschichte", modell)
    if (modell != null) {
        val quellen = buildList {
            if (alle.any { it.link.contains("wikidata.org") }) add(QuelleLink(GeschichteTexte.t("quelleWikidata"), "https://www.wikidata.org/wiki/Wikidata:Licensing"))
            if (alle.any { it.link.contains("sec.gov") }) add(QuelleLink(GeschichteTexte.t("quelleSec"), "https://www.sec.gov/edgar/search/"))
        }
        QuellenMelden(*quellen.toTypedArray())
    }
}

private fun datumText(tag: String): String = when (tag.length) {
    4 -> tag
    7 -> runCatching { Fmt.monthYear(YearMonth.parse(tag)) }.getOrDefault(tag)
    else -> de.troetenrepublik.app.market.Politik.tag(tag).takeIf { it > 0 }?.let { Fmt.date(it) } ?: tag
}

private fun geschichteModell(alle: List<FirmaGeschichte.Ereignis>, kerzen: List<Candle>): ListenModell {
    val kern = FirmaGeschichte.kern(alle)
    val teile = ArrayList<KernTeil>()
    kern.gegruendet?.let { teile += KernTeil(GeschichteTexte.t("kern.gegruendet", it.toString())) }
    kern.boersengang?.let { teile += KernTeil(GeschichteTexte.t("kern.boersengang", it.toString())) }
    if (kern.uebernahmen > 0) teile += KernTeil(if (kern.uebernahmen == 1) GeschichteTexte.t("kern.uebernahme.1") else GeschichteTexte.t("kern.uebernahme.n", kern.uebernahmen.toString()))
    if (teile.isEmpty()) {
        val seit = alle.minByOrNull { FirmaGeschichte.sortTag(it.tag) }?.jahr ?: 0
        teile += KernTeil(GeschichteTexte.t("kern.n", alle.size.toString(), seit.toString()))
    }
    val reaktionen = HashMap<Int, Double>()
    if (kerzen.isNotEmpty()) alle.forEachIndexed { i, e -> if (e.taggenau) AchtK.reaktion(kerzen, e.tag)?.let { reaktionen[i] = it.tag0 } }
    return ListenModell(
        titel = GeschichteTexte.t("titel"), gross = true, kern = KernZeile(teile),
        info = GeschichteTexte.t("info"), immerZu = true, kappe = FirmaGeschichte.ZUERST,
        zeilen = alle.mapIndexed { i, e ->
            ZeilenDaten("${e.art.name}-${e.tag}-$i") { ZeitZeile(e, reaktionen[i]) }
        },
    )
}

private class Zeilentext(val titel: String, val details: List<String>)

private fun zeilentext(e: FirmaGeschichte.Ereignis): Zeilentext = when (e.art) {
    Art.SPLIT -> Zeilentext(GeschichteTexte.t(if (e.b == "REVERSE") "REVERSE" else "SPLIT", e.a), emptyList())
    Art.DEAL_ANGEKUENDIGT, Art.DEAL_ANGEBOT, Art.DEAL_ABGESCHLOSSEN -> Zeilentext(
        GeschichteTexte.t(e.art.name),
        listOfNotNull(
            e.a.takeIf { it.isNotBlank() }?.let { GeschichteTexte.t("gegenseite", it) },
            e.b.toDoubleOrNull()?.let { GeschichteTexte.t("preis", Fmt.price(it, "$")) },
        ),
    )
    Art.FUEHRUNG -> Zeilentext(S.achtRolle(e.b), listOfNotNull(e.a.takeIf { it.isNotBlank() }))
    Art.INSOLVENZ -> Zeilentext(GeschichteTexte.t("INSOLVENZ"), listOfNotNull(e.a.toIntOrNull()?.takeIf { it > 0 }?.let { GeschichteTexte.t("chapter", it.toString()) }))
    else -> Zeilentext(GeschichteTexte.t(e.art.name, e.a, e.b), emptyList())
}

// Eine Zeile der Zeitleiste: Datum links in einer Zeile, Linie mit Punkt, rechts Beschreibung, Kursreaktion und Quelle
@Composable
private fun ZeitZeile(e: FirmaGeschichte.Ereignis, reaktion: Double?) {
    val linie = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    val punkt = MaterialTheme.colorScheme.primary
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val links = appLinks()
    val z = zeilentext(e)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .then(if (e.link.isNotBlank()) Modifier.clickable { links.openUri(e.link) } else Modifier)
            .padding(horizontal = 20.dp),
    ) {
        Text(
            datumText(e.tag), style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false,
            modifier = Modifier.width(84.dp).padding(top = 11.dp),
        )
        Box(
            Modifier.width(18.dp).fillMaxHeight().drawBehind {
                val x = size.width / 2f
                drawLine(linie, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                drawCircle(punkt, 4.dp.toPx(), Offset(x, 17.dp.toPx()))
            },
        )
        Column(Modifier.weight(1f).padding(start = 8.dp, top = 8.dp, bottom = 10.dp)) {
            Text(z.titel, style = MaterialTheme.typography.titleSmall)
            z.details.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = grau) }
            if (reaktion != null) {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(GeschichteTexte.t("kurs"), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.weight(1f))
                    ChangeValue(reaktion, Fmt.signedPct(reaktion, 1), style = MaterialTheme.typography.bodySmall, triangleWidth = 8.dp)
                }
            }
        }
    }
}
