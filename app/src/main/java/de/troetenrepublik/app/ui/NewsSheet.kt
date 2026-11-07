package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.engine.Lesemodus
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Rss
import de.troetenrepublik.app.market.Sonderleser
import de.troetenrepublik.app.plattform.ArtikelLeser
import de.troetenrepublik.app.plattform.Plattform
import de.troetenrepublik.app.plattform.WebBaustein
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Nachricht in der App lesen: Schlagzeile, Datum, Quelle und den aus der Artikelseite herausgeloesten Text.
// Liest die App eine Seite automatisch mit (Lesemodus.automatisch), laedt sie unsichtbar im Hintergrund und wird nur
// mit „Originalseite anzeigen“ sichtbar. Ausgelesen wird dann schon waehrend des Ladens und jede halbe Sekunde, damit
// Werbung und Nachladeteile nicht abgewartet werden muessen.
// Klappt es auch nach dem Laden nicht, etwa wegen einer Einwilligungsseite, erscheint ein Hinweis.
// ArtikelLeser liest auf Android in der Web-Ansicht, unter Windows und Linux direkt aus dem HTML.
// Pressemitteilungen der EU-Kommission kommen ueber deren Schnittstelle (Sonderleser.eu). Bleibt eine Seite ohne
// Text, wird das verlinkte Dokument gelesen (Sonderleser.dokument), sonst auch ein kurzer Text gezeigt.
// Lesemodus: Die Originalseite fuellt das ganze Blatt, darueber bleibt nur eine schmale Leiste. „Nur Text
// anzeigen“ erscheint nach dem Laden fuer alle Quellen. Liegt noch kein Text vor, liest der Tipp nur den sichtbaren
// Text aus der schon geladenen Seite, ohne neuen Abruf. Seiten aus Rss.ohneLesemodus liest die App
// nur auf diesen Tipp, alle anderen liest sie automatisch mit. Ohne Web-Ansicht liest die App den Text, sobald er
// gewuenscht ist.
// MarketWatch oeffnet am Rechner im Fenster des eingebetteten Browsers (WebBaustein); „Nur Text anzeigen“ liest nach dem
// Laden auf Tipp aus dieser Seite.
// Seiten aus Rss.keinLesemodus (5.95) liest die App nie aus: kein Text-Knopf und kein
// Auslesen; zu sehen bleibt die Originalseite im eingebetteten Browser.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsSheet(
    item: MarketApi.NewsItem,
    onDismiss: () -> Unit,
    actionsTop: Boolean = false,
    wikipedia: Boolean = false,
    // Vorhandener Text steht sofort da, die Seite laedt erst auf Wunsch.
    text: List<String>? = null,
    // Der vorhandene Text kommt noch.
    textLaedt: Boolean = false,
    // Hinweis statt „kein Text“.
    ohneText: String? = null,
    // Unter dem Text.
    fuss: (@Composable () -> Unit)? = null,
) {
    // Im Browser oeffnen geht immer nach draussen, auch wenn die App Links selbst zeigt (AppLinks)
    val uriHandler = externeLinks()
    var paragraphs by remember(item.link, text) { mutableStateOf(text.orEmpty()) }
    // Kurzer Text, der waehrend des Ladens nicht reicht, am Ende aber besser ist als keiner.
    var kurz by remember(item.link) { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val ueberSchnittstelle = remember(item.link, text) { text == null && Sonderleser.euAdresse(item.link) != null }
    // MarketWatch-Artikel oeffnen am Rechner im Fenster des eingebetteten Browsers (WebBaustein).
    val mitBaustein = remember(item.link, text, item.source) { text == null && !Rss.keinLesemodus(item.link, item.source) && WebBaustein.noetig(item.link) }
    val seiteZuerst = remember(item.link, text, wikipedia) {
        text == null && item.link.startsWith("http") && !wikipedia && !ueberSchnittstelle && !mitBaustein && !Rss.textErlaubt(item.link)
    }
    // 5.95: Bei Seiten aus Rss.keinLesemodus bleibt jeder Ausleseversuch aus (keinText)
    val keinText = remember(item.link, item.source) { Rss.keinLesemodus(item.link, item.source) }
    val lesemodus = remember(item.link, item.source) { Lesemodus.automatisch(item.link, item.source) }
    var showOriginal by remember(item.link) { mutableStateOf(seiteZuerst && Plattform.webAnsicht) }
    // Ohne Web-Ansicht liest die App den Text erst, wenn er gewuenscht ist
    var textGewollt by remember(item.link) { mutableStateOf(!seiteZuerst || Plattform.webAnsicht) }
    // Ohne Adresse oder Lesemodus gibt es nichts zu lesen
    var loading by remember(item.link, textLaedt) { mutableStateOf((text == null && item.link.startsWith("http") && lesemodus && textGewollt) || textLaedt) }
    var bausteinDa by remember { mutableStateOf(WebBaustein.installiert) }
    var bausteinFortschritt by remember { mutableStateOf<Float?>(null) }
    var bausteinFehler by remember { mutableStateOf(false) }
    // Artikelfenster am Rechner: offen und fertig geladen.
    var imFenster by remember(item.link) { mutableStateOf(false) }
    var fensterGeladen by remember(item.link) { mutableStateOf(false) }
    // Lesemodus auf Tipp: Ladezustand der Seite, laufende und fertige Auslese des sichtbaren Textes.
    var geladen by remember(item.link) { mutableStateOf(false) }
    var leseAnfrage by remember(item.link) { mutableStateOf(0) }
    var liest by remember(item.link) { mutableStateOf(false) }
    var gelesen by remember(item.link) { mutableStateOf(false) }
    val hasText = paragraphs.isNotEmpty()
    // Bei Wikipedia laedt in der App die mobile Seite, der Browser bekommt die Adresse des Artikels.
    val ladeAdresse = remember(item.link, wikipedia) { if (wikipedia) mobileWikipedia(item.link) else item.link }
    val textScroll = rememberScrollState()
    val seiteSichtbar by rememberUpdatedState(showOriginal)
    // Solange die Originalseite zu sehen ist, schliessen Wischen und Tipp daneben das Blatt nicht; Zurueck und der
    // Knopf schon (beide rufen hide()).
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !seiteSichtbar })

    // Erst uebernehmen, wenn es genug Text ist und mehr als zuvor.
    fun aufnehmen(list: List<String>) {
        if (text != null) return
        if (list.size >= 2 && list.sumOf { it.length } > 300 && list.size > paragraphs.size) {
            paragraphs = list
            loading = false
        } else if (list.sumOf { it.length } > kurz.sumOf { it.length }) {
            kurz = list.filter { it.length >= 40 }
        }
    }

    // Seite fertig, aber zu wenig Text: verlinktes Dokument lesen, sonst den kurzen Text zeigen.
    fun seiteFertig() {
        if (paragraphs.isNotEmpty() || text != null || !lesemodus) { loading = false; return }
        scope.launch {
            // Auf der Zwischenseite von Google steht kein Artikel der Zeitung.
            val doku = if (Lesemodus.zwischenseite(ladeAdresse)) null
                else runCatching { Sonderleser.dokument(ladeAdresse) }.getOrNull()
            if (paragraphs.isEmpty()) paragraphs = doku ?: kurz.takeIf { l -> l.sumOf { it.length } >= 80 }.orEmpty()
            loading = false
        }
    }

    // Am Rechner oeffnet ein MarketWatch-Artikel im Fenster; gelesen wird nur auf Tipp.
    LaunchedEffect(item.link, mitBaustein, bausteinDa) {
        if (!mitBaustein) return@LaunchedEffect
        loading = false
        if (!bausteinDa) return@LaunchedEffect
        imFenster = runCatching { WebBaustein.artikelOeffnen(item.link, item.title) }.getOrDefault(false)
    }
    // Ladezustand des Fensters fuer den Knopf „Nur Text anzeigen“
    LaunchedEffect(item.link, imFenster) {
        if (!imFenster) return@LaunchedEffect
        while (true) {
            fensterGeladen = WebBaustein.artikelGeladen
            // Fenster geschlossen: der Knopf „Seite im Fenster oeffnen“ oeffnet es wieder
            if (!WebBaustein.artikelOffen) { imFenster = false; fensterGeladen = false; break }
            delay(500)
        }
    }

    LaunchedEffect(item.link, ueberSchnittstelle) {
        if (!ueberSchnittstelle) return@LaunchedEffect
        val eu = runCatching { Sonderleser.eu(item.link) }.getOrNull()
        if (eu != null) { paragraphs = eu; loading = false } else seiteFertig()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = if (showOriginal) null else { { BottomSheetDefaults.DragHandle() } },
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        // Lesemodus-Knopf und Im Browser oeffnen, in der Leiste und bei den Aktionen gleich.
        val knoepfe: @Composable () -> Unit = {
            when (Lesemodus.knopf(Plattform.webAnsicht, item.link.startsWith("http"), showOriginal, geladen, hasText, lesemodus, loading, liest, gelesen, erlaubt = !keinText)) {
                Lesemodus.Knopf.NUR_TEXT -> {
                    Text(
                        S.showText, style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
                        modifier = Modifier.clickable {
                            if (Lesemodus.ausSeiteLesen(hasText, geladen)) { liest = true; leseAnfrage++ } else showOriginal = false
                        }.padding(vertical = 8.dp),
                    )
                    Spacer(Modifier.padding(horizontal = 10.dp))
                }
                Lesemodus.Knopf.ORIGINAL -> {
                    Text(
                        S.showOriginal, style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
                        modifier = Modifier.clickable { showOriginal = true }.padding(vertical = 8.dp),
                    )
                    Spacer(Modifier.padding(horizontal = 10.dp))
                }
                Lesemodus.Knopf.LAEDT -> {
                    // Die Seite ist zu sehen, der Text wird noch gelesen
                    Text(S.articleLoading, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                    Spacer(Modifier.padding(horizontal = 10.dp))
                }
                Lesemodus.Knopf.KEINER -> {}
            }
            if (!Plattform.webAnsicht && lesemodus && seiteZuerst && !textGewollt) {
                Text(
                    S.showText, style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
                    modifier = Modifier.clickable { textGewollt = true; loading = true }.padding(vertical = 8.dp),
                )
                Spacer(Modifier.padding(horizontal = 10.dp))
            }
            if (item.link.startsWith("http")) Text(
                S.openInBrowser, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { runCatching { uriHandler.openUri(item.link) } }.padding(vertical = 8.dp),
            )
        }
        // Schmale Leiste ueber der Originalseite: Quelle und Titel in einer Zeile, Schliessen, darunter die Knoepfe.
        val leiste: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (item.source.isNotBlank() && item.source != item.title) {
                        Text(
                            item.source, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, softWrap = false,
                        )
                        Spacer(Modifier.padding(horizontal = 4.dp))
                    }
                    AuslaufText(item.title, maxLines = 1, style = MaterialTheme.typography.titleSmall.schlagzeile(), modifier = Modifier.weight(1f))
                    IconButton(onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }) {
                        Icon(Icons.Filled.Close, contentDescription = S.close)
                    }
                }
                Row { knoepfe() }
            }
        }
        // Unten bei Nachrichten, sonst gleich unter der Ueberschrift.
        val aktionen: @Composable () -> Unit = {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) { knoepfe() }
        }
        Column(Modifier.fillMaxWidth().then(if (showOriginal) Modifier.fillMaxHeight() else Modifier)) {
            if (showOriginal) leiste()
            if (!showOriginal) Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(textScroll).padding(bottom = 30.dp)) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(item.title, style = MaterialTheme.typography.headlineMedium.schlagzeile(), lineHeight = 32.sp)
                    Spacer(Modifier.height(10.dp))
                    if (item.published > 0) Text(Fmt.weekdayDateTime(item.published), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.source.isNotBlank()) Text(S.newsSource(item.source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (actionsTop) aktionen()
                Column(Modifier.padding(horizontal = 20.dp)) {
                    if (item.summary.isNotBlank() && paragraphs.none { it.contains(item.summary.take(40)) }) {
                        Spacer(Modifier.height(14.dp))
                        MitBlende(item.summaryGekuerzt, klein = false) {
                            StichpunktText("• " + item.summary, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (loading && !hasText && !showOriginal && textGewollt) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        Text(S.articleLoading, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (hasText && !showOriginal) {
                        Spacer(Modifier.height(12.dp))
                        paragraphs.forEach { p ->
                            Text(p, style = MaterialTheme.typography.bodyLarge, lineHeight = 26.sp, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                    // Eingebetteter Browser fehlt: einmal laden.
                    if (mitBaustein && !bausteinDa && !hasText) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            if (bausteinFehler) S.webBausteinFehler else S.webBausteinAngebot(WebBaustein.groesseMb),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val f = bausteinFortschritt
                        if (f == null) {
                            Text(
                                S.webBausteinLaden, style = MaterialTheme.typography.labelLarge, color = LocalSignal.current.link,
                                modifier = Modifier.clickable {
                                    bausteinFortschritt = 0f
                                    bausteinFehler = false
                                    scope.launch {
                                        val ok = WebBaustein.installieren { bausteinFortschritt = it }
                                        bausteinFortschritt = null
                                        if (ok) bausteinDa = true else bausteinFehler = true
                                    }
                                }.padding(vertical = 10.dp),
                            )
                        } else {
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                            Text(S.webBausteinLaedt(Fmt.pct(f * 100.0, 0)), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                    } else if (mitBaustein && !hasText) {
                        Spacer(Modifier.height(14.dp))
                        if (gelesen || imFenster) Text(if (gelesen) S.webBausteinLeer else S.webBausteinImFenster, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (liest) Text(S.articleLoading, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp))
                            else if (fensterGeladen) Text(
                                S.showText, style = MaterialTheme.typography.labelLarge, color = LocalSignal.current.link,
                                modifier = Modifier.clickable {
                                    liest = true
                                    scope.launch {
                                        val l = runCatching { WebBaustein.artikelText() }.getOrNull()
                                        if (!l.isNullOrEmpty()) paragraphs = l
                                        if (l != null) gelesen = true
                                        liest = false
                                    }
                                }.padding(vertical = 10.dp),
                            )
                            if (liest || fensterGeladen) Spacer(Modifier.padding(horizontal = 10.dp))
                            Text(
                                S.webBausteinFenster, style = MaterialTheme.typography.labelLarge, color = LocalSignal.current.link,
                                modifier = Modifier.clickable {
                                    scope.launch { imFenster = runCatching { WebBaustein.artikelOeffnen(item.link, item.title) }.getOrDefault(false) }
                                }.padding(vertical = 10.dp),
                            )
                        }
                    } else if (gelesen && !hasText && !showOriginal) {
                        Spacer(Modifier.height(14.dp))
                        Text(S.lesemodusLeer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (!loading && !hasText && !showOriginal && (textGewollt || !lesemodus)) {
                        Spacer(Modifier.height(14.dp))
                        Text(ohneText ?: if (lesemodus) S.articleNoText else S.newsOhneLesemodus, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // MarketWatch zeigt ohne Abo nur den Anfang; der Text ist das, was die Seite im Fenster zeigt.
                    if (mitBaustein && hasText && !showOriginal && paragraphs.sumOf { it.length } < 4000) {
                        Spacer(Modifier.height(12.dp))
                        Text(S.webBausteinNurAnfang, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (fuss != null && !showOriginal) {
                        Spacer(Modifier.height(12.dp))
                        fuss()
                    }
                }
                if (!actionsTop) aktionen()
            }
            // Die Web-Ansicht bleibt an derselben Stelle der Komposition, damit die Seite beim Umschalten nicht neu laedt.
            Box(if (showOriginal) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(1.dp).graphicsLayer { alpha = 0f }) {
                // Mit vorhandenem Text laedt die Seite erst, wenn sie gezeigt werden soll.
                if (item.link.startsWith("http") && ((text == null && !ueberSchnittstelle && !mitBaustein && textGewollt) || showOriginal)) {
                    ArtikelLeser(
                        adresse = ladeAdresse,
                        wikipedia = wikipedia,
                        auslesen = text == null && !ueberSchnittstelle && lesemodus,
                        modifier = Modifier.fillMaxSize(),
                        textDa = { paragraphs.size >= 2 && !loading },
                        onAbsaetze = { aufnehmen(it) },
                        onEnde = { seiteFertig() },
                        onGeladen = { geladen = it; if (!it) liest = false },
                        leseAnfrage = leseAnfrage,
                        onLesemodus = { l ->
                            if (paragraphs.isEmpty() && l.isNotEmpty()) paragraphs = l
                            liest = false
                            gelesen = true
                            loading = false
                            showOriginal = false
                        },
                    )
                }
            }
        }
    }
}

// de.wikipedia.org wird zu de.m.wikipedia.org.
internal fun mobileWikipedia(url: String): String =
    url.replaceFirst(Regex("^(https?://[a-z]{2,3})\\.wikipedia\\.org/"), "$1.m.wikipedia.org/")
