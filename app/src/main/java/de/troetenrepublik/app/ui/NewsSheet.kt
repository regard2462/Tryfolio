package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Sonderleser
import kotlinx.coroutines.launch
import de.troetenrepublik.app.plattform.ArtikelLeser
import de.troetenrepublik.app.plattform.Plattform
import de.troetenrepublik.app.plattform.WebBaustein

// Nachricht in der App lesen: Schlagzeile, Datum, Quelle und den aus der Artikelseite herausgeloesten Text.
// Die Seite laedt unsichtbar im Hintergrund und wird nur mit „Originalseite anzeigen“ sichtbar. Ausgelesen wird schon
// waehrend des Ladens und dann jede halbe Sekunde, damit Werbung und Nachladeteile nicht abgewartet werden muessen.
// Klappt es auch nach dem Laden nicht, etwa wegen einer Einwilligungsseite, erscheint ein Hinweis.
// ArtikelLeser liest auf Android in der Web-Ansicht, unter Windows und Linux direkt aus dem HTML.
// Pressemitteilungen der EU-Kommission kommen ueber deren Schnittstelle (Sonderleser.eu). Bleibt eine Seite ohne
// Text, wird das verlinkte Dokument gelesen (Sonderleser.dokument), sonst auch ein kurzer Text gezeigt.
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
    val uriHandler = LocalUriHandler.current
    var paragraphs by remember(item.link, text) { mutableStateOf(text.orEmpty()) }
    var loading by remember(item.link, textLaedt) { mutableStateOf(text == null || textLaedt) }
    var showOriginal by remember(item.link) { mutableStateOf(false) }
    // Kurzer Text, der waehrend des Ladens nicht reicht, am Ende aber besser ist als keiner.
    var kurz by remember(item.link) { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val ueberSchnittstelle = remember(item.link, text) { text == null && Sonderleser.euAdresse(item.link) != null }
    // Seiten, die nur ein Browser lesen darf, liest der eingebettete Browser.
    val mitBaustein = remember(item.link, text) { text == null && WebBaustein.noetig(item.link) }
    var bausteinDa by remember { mutableStateOf(WebBaustein.installiert) }
    var bausteinFortschritt by remember { mutableStateOf<Float?>(null) }
    var bausteinFehler by remember { mutableStateOf(false) }
    // Captcha der Seite: die Person erledigt es im Fenster, danach liest die App neu.
    var bestaetigen by remember(item.link) { mutableStateOf(false) }
    var leseVersuch by remember(item.link) { mutableStateOf(0) }
    val hasText = paragraphs.isNotEmpty()
    // Bei Wikipedia laedt in der App die mobile Seite, der Browser bekommt die Adresse des Artikels.
    val ladeAdresse = remember(item.link, wikipedia) { if (wikipedia) mobileWikipedia(item.link) else item.link }

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
        if (paragraphs.isNotEmpty() || text != null) { loading = false; return }
        scope.launch {
            val doku = runCatching { Sonderleser.dokument(ladeAdresse) }.getOrNull()
            if (paragraphs.isEmpty()) paragraphs = doku ?: kurz.takeIf { l -> l.sumOf { it.length } >= 80 }.orEmpty()
            loading = false
        }
    }

    LaunchedEffect(item.link, mitBaustein, bausteinDa, leseVersuch) {
        if (!mitBaustein) return@LaunchedEffect
        if (!bausteinDa) { loading = false; return@LaunchedEffect }
        loading = true
        val l = runCatching { WebBaustein.lesen(item.link) }.getOrNull()
        if (!l.isNullOrEmpty()) paragraphs = l
        bestaetigen = l.isNullOrEmpty() && WebBaustein.bestaetigungNoetig
        loading = false
    }

    LaunchedEffect(item.link, ueberSchnittstelle) {
        if (!ueberSchnittstelle) return@LaunchedEffect
        val eu = runCatching { Sonderleser.eu(item.link) }.getOrNull()
        if (eu != null) { paragraphs = eu; loading = false } else seiteFertig()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        // Unten bei Nachrichten, sonst gleich unter der Ueberschrift.
        val aktionen: @Composable () -> Unit = {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                if (Plattform.webAnsicht && (!loading || hasText)) {
                    Text(
                        if (showOriginal && hasText) S.showText else S.showOriginal,
                        style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
                        modifier = Modifier.clickable { showOriginal = !showOriginal || !hasText }.padding(vertical = 8.dp),
                    )
                    Spacer(Modifier.padding(horizontal = 10.dp))
                }
                Text(
                    S.openInBrowser, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { runCatching { uriHandler.openUri(item.link) } }.padding(vertical = 8.dp),
                )
            }
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 30.dp)) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(item.title, style = MaterialTheme.typography.headlineMedium, lineHeight = 32.sp)
                Spacer(Modifier.height(10.dp))
                if (item.published > 0) Text(Fmt.weekdayDateTime(item.published), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.source.isNotBlank()) Text(S.newsSource(item.source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (actionsTop) aktionen()
            Column(Modifier.padding(horizontal = 20.dp)) {
                if (item.summary.isNotBlank() && paragraphs.none { it.contains(item.summary.take(40)) }) {
                    Spacer(Modifier.height(14.dp))
                    Text("• " + item.summary, style = MaterialTheme.typography.bodyLarge, lineHeight = 26.sp)
                }
                if (loading && !hasText) {
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
                } else if (mitBaustein && bestaetigen && !hasText && !loading) {
                    Spacer(Modifier.height(14.dp))
                    Text(S.webBausteinBestaetigen, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        S.webBausteinFenster, style = MaterialTheme.typography.labelLarge, color = LocalSignal.current.link,
                        modifier = Modifier.clickable {
                            scope.launch {
                                WebBaustein.bestaetigen(item.link, item.title)
                                bestaetigen = false
                                leseVersuch++
                            }
                        }.padding(vertical = 10.dp),
                    )
                } else if (!loading && !hasText && !showOriginal) {
                    Spacer(Modifier.height(14.dp))
                    Text(ohneText ?: S.articleNoText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // MarketWatch zeigt ohne Abo nur den Anfang, die App liest, was die Seite zeigt.
                if (mitBaustein && hasText && !showOriginal && paragraphs.sumOf { it.length } < 4000) {
                    Spacer(Modifier.height(12.dp))
                    Text(S.webBausteinNurAnfang, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (fuss != null && !showOriginal) {
                    Spacer(Modifier.height(12.dp))
                    fuss()
                }
            }
            // Die Seite ist die Quelle des Textes und laedt daher immer im Hintergrund.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (showOriginal) 620.dp else 1.dp)
                    .padding(top = if (showOriginal) 12.dp else 0.dp)
                    .graphicsLayer { alpha = if (showOriginal) 1f else 0f },
            ) {
                // Mit vorhandenem Text laedt die Seite erst, wenn sie gezeigt werden soll.
                if (item.link.startsWith("http") && ((text == null && !ueberSchnittstelle && !mitBaustein) || showOriginal)) {
                    ArtikelLeser(
                        adresse = ladeAdresse,
                        wikipedia = wikipedia,
                        auslesen = text == null && !ueberSchnittstelle,
                        modifier = Modifier.fillMaxWidth().height(if (showOriginal) 620.dp else 1.dp),
                        textDa = { paragraphs.size >= 2 && !loading },
                        onAbsaetze = { aufnehmen(it) },
                        onEnde = { seiteFertig() },
                    )
                }
            }
            if (!actionsTop) aktionen()
        }
    }
}

// de.wikipedia.org wird zu de.m.wikipedia.org.
internal fun mobileWikipedia(url: String): String =
    url.replaceFirst(Regex("^(https?://[a-z]{2,3})\\.wikipedia\\.org/"), "$1.m.wikipedia.org/")
