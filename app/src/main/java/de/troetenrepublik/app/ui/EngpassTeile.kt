package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.engine.GlobusKarte
import de.troetenrepublik.app.engine.LaenderZusatz
import de.troetenrepublik.app.engine.Sanktionen
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.MarketApi

// [Engpaesse] Teile, die das Blatt eines Engpasses, das Blatt eines Spannungsgebiets und die Weltlage teilen.

@Composable
internal fun AbschnittKopf(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp))
}

// Werte zum Antippen (Name aus dem Katalog); Kuerzel ohne Katalogeintrag fallen weg.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WerteListe(symbole: List<String>, onOpen: (String) -> Unit) {
    val deutsch = I18n.locale.language == "de"
    val werte = remember(symbole, deutsch) {
        val k = Catalog.get()
        val namen = HashMap<String, String>()
        k.aktien.forEach { namen[it.s] = it.n }
        k.etfs.forEach { namen[it.s] = it.n }
        k.basiswerte.forEach { namen[it.s] = if (deutsch) it.nameDe else it.nameEn }
        symbole.mapNotNull { s -> namen[s]?.let { s to it } }
    }
    if (werte.isEmpty()) return
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        for ((s, n) in werte) Text(
            n, style = MaterialTheme.typography.bodyMedium, color = LocalSignal.current.link, maxLines = 1,
            modifier = Modifier.clickable { onOpen(s) }.padding(vertical = 6.dp),
        )
    }
}

// Zeilen „Land, EU und USA“ fuer Staaten mit Sanktionsregimen (gebuendelte Listen der EU und der USA). Leer, wenn keiner betroffen ist.
@Composable
internal fun SanktionsZeilen(laender: List<String>, karte: GlobusKarte?, sl: Sanktionen.Liste?, onInfo: (Pair<String, String>) -> Unit) {
    if (sl == null || karte == null) return
    val deutsch = I18n.locale.language == "de"
    val rows = laender.mapNotNull { a3 ->
        val l = karte.nachA3(a3) ?: return@mapNotNull null
        val eu = sl.eu[l.a2].orEmpty()
        val us = sl.us[l.a2].orEmpty()
        if (eu.isEmpty() && us.isEmpty()) null else Triple(l.name(deutsch), eu.map { if (deutsch) it.de else it.en }, us)
    }
    if (rows.isEmpty()) return
    AbschnittKopf(S.engSanktionen)
    for ((name, eu, us) in rows) {
        val wer = listOfNotNull(if (eu.isNotEmpty()) "EU" else null, if (us.isNotEmpty()) S.engUsa else null).joinToString(", ")
        KeyValueRow(name, wer, onClick = { onInfo(name to S.engSanktionInfo(eu.joinToString("; "), us.joinToString("; "))) })
    }
}

// Schlagzeilen: ein Tipp oeffnet den Artikel wie in den News
@Composable
internal fun SchlagzeilenListe(meldungen: List<LaenderZusatz.Meldung>, onArtikel: (MarketApi.NewsItem) -> Unit) {
    if (meldungen.isEmpty()) return
    AbschnittKopf(S.engSchlagzeilen)
    meldungen.forEachIndexed { i, m ->
        if (i > 0) Trennlinie()
        Column(Modifier.fillMaxWidth().clickable { onArtikel(MarketApi.NewsItem(title = m.titel, link = m.link, published = m.zeit, source = m.quelle)) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text(m.titel, style = MaterialTheme.typography.titleSmall.schlagzeile(), lineHeight = 22.sp)
            PunktText(
                listOfNotNull(m.zeit.takeIf { it > 0 }?.let { Fmt.relative(it) }, m.quelle.takeIf { it.isNotBlank() }).joinToString(PUNKT),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
