package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.InsiderEuropa
import de.troetenrepublik.app.market.Investegate
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.NachrichtenApis
import de.troetenrepublik.app.market.NasdaqNordic
import de.troetenrepublik.app.market.NewsRepository
import de.troetenrepublik.app.market.Politik
import kotlinx.coroutines.launch

// Quellen der Fassung 5.95: Director Dealings Grossbritanniens ueber Investegate, Managers Transactions der Nasdaq
// Nordic und Nachrichten ueber EODHD. Investegate verlangt vor dem ersten Abruf die Bestaetigung, Privatanleger zu
// sein und die Meldungen nur persoenlich zu nutzen. Alle Ergebnisse bleiben im Arbeitsspeicher.

// Adresse im Browser oeffnen; Fehler bleiben ohne Wirkung.
// Investegate verlangt die unveraenderte Wiedergabe der Meldungen.
internal val INVESTEGATE_QUELLE = QuelleLink("Investegate (Meldungen unverändert)", "https://www.investegate.co.uk")

private fun oeffne(uri: androidx.compose.ui.platform.UriHandler, url: String) { runCatching { uri.openUri(url) } }

// Datum und Uhrzeit einer Meldung in einer Zeile; die Uhrzeit in der Zeitzone des Geraets.
private fun zeitZeile(ms: Long): String = Fmt.dateTime(ms)

// ------------------------------------------------------------ Investegate: eine britische Aktie

// Director Dealings einer britischen Aktie ueber Investegate (5.95). Ohne Bestaetigung steht die Abfrage nach der
// Privatanleger-Eigenschaft; mit Nein steht der Hinweis auf die Seite des Landes.
@Composable
internal fun InvestegateKarte(state: AppState, kuerzel: String) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    // Nein blendet die Abfrage nur fuer diese Ansicht aus
    var abgelehnt by remember(kuerzel) { mutableStateOf(false) }
    when {
        !settings.okInvestegate && !abgelehnt -> {
            Column {
                SectionHeader(S.vdKarteTitel)
                Text(S.q5IgPrivat, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                InvestegateBestaetigung(state, onNein = { abgelehnt = true })
            }
        }
        !settings.okInvestegate -> InsiderBrowserHinweis("GB")
        else -> { QuellenMelden(INVESTEGATE_QUELLE); InvestegateFirmaListe(state, kuerzel) }
    }
}

// Die beiden Knoepfe der Investegate-Bestaetigung, aufgebaut wie die Bestaetigung zu 5 U.S.C. 13107.
@Composable
internal fun InvestegateBestaetigung(state: AppState, onNein: () -> Unit) {
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onNein) { Text(S.q5IgNein) }
        TextButton(onClick = { scope.launch { state.bestaetigeInvestegate() } }) { Text(S.q5IgJa) }
    }
}

// Meldungen einer Firma nach der Bestaetigung: die neuesten fuenf, der Pfeil darunter zeigt bis zwanzig.
@Composable
private fun InvestegateFirmaListe(state: AppState, kuerzel: String) {
    val uri = appLinks()
    var liste by remember(kuerzel) { mutableStateOf<List<Investegate.Meldung>?>(null) }
    var geladen by remember(kuerzel) { mutableStateOf(false) }
    var alle by remember(kuerzel) { mutableStateOf(false) }
    var offen by remember { mutableStateOf<Investegate.Meldung?>(null) }
    LaunchedEffect(kuerzel) {
        liste = try { state.investegateFirma(kuerzel) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
        geladen = true
    }
    Column {
        val l = liste
        when {
            l == null && !geladen -> { SectionHeader(S.vdKarteTitel); EmptyHint(S.trLaedt) }
            l == null -> {
                InsiderBrowserHinweis("GB")
                Text(S.q5Nicht, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            else -> {
                SectionHeader(S.vdKarteTitel)
                if (l.isEmpty()) {
                    Text(S.q5IgKeine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                } else {
                    l.take(if (alle) 20 else 5).forEach { m ->
                        Column(Modifier.fillMaxWidth().clickable { offen = m }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                            Text(m.titel, style = MaterialTheme.typography.titleSmall)
                            PunktText(listOf(zeitZeile(m.zeit), m.quelle).filter { it.isNotBlank() }.joinToString(" · "))
                        }
                    }
                    if (l.size > 5) ListenPfeil(alle) { alle = !alle }
                    QuellenZeile(S.q5IgSeite, Modifier.padding(horizontal = 20.dp), onClick = { oeffne(uri, Investegate.firmaAdresse(kuerzel)) })
                }
            }
        }
    }
    offen?.let { m -> InvestegateMeldungBlatt(state, m, onDismiss = { offen = null }) }
}

// ------------------------------------------------------------ Investegate: Blatt einer Meldung

// Der Bogen einer Meldung von Investegate (5.95): jede Zeile unveraendert, Werte je in eigener Zeile, so bricht
// keiner in einer schmalen Spalte um. Kleine Abstaende, keine Kuerzung.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InvestegateMeldungBlatt(state: AppState, meldung: Investegate.Meldung, onDismiss: () -> Unit) {
    var bogen by remember(meldung.link) { mutableStateOf<Investegate.Bogen?>(null) }
    var geladen by remember(meldung.link) { mutableStateOf(false) }
    LaunchedEffect(meldung.link) {
        bogen = try { state.investegateMeldung(meldung.link) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
        geladen = true
    }
    val uri = appLinks()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            Text(meldung.titel, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 20.dp))
            PunktText(listOf(meldung.firma, Fmt.date(meldung.zeit), meldung.quelle).filter { it.isNotBlank() }.joinToString(" · "), Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            val b = bogen
            when {
                b == null && !geladen -> EmptyHint(S.trLaedt)
                b == null -> Text(S.q5Nicht, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                else -> Column(Modifier.padding(horizontal = 20.dp)) {
                    b.zeilen.forEach { zeile ->
                        if (zeile.size == 1) {
                            Text(zeile[0], style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                        } else {
                            Column(Modifier.padding(vertical = 2.dp)) {
                                Text(zeile.first(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                zeile.drop(1).forEach { wert -> Text(wert, style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
            }
            Text(
                if (meldung.quelle.isBlank()) S.q5IgQuelleOhne else S.q5IgQuelleMeldung(meldung.quelle),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            QuellenZeile(S.q5IgOeffnen, Modifier.padding(horizontal = 20.dp), onClick = { oeffne(uri, meldung.link) })
        }
    }
}

// ------------------------------------------------------------ Investegate: alle Meldungen

// Director Dealings Grossbritanniens als Blatt (5.95): Seite fuer Seite, „Mehr“ laedt die naechste bis hoechstens
// Seite drei. Ohne Bestaetigung steht zuerst die Abfrage; Nein schliesst das Blatt.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InvestegateListeBlatt(state: AppState, onDismiss: () -> Unit) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val uri = appLinks()
    var liste by remember { mutableStateOf<List<Investegate.Meldung>?>(null) }
    var geladen by remember { mutableStateOf(false) }
    var seite by remember { mutableStateOf(1) }
    var fehler by remember { mutableStateOf(false) }
    var laedtMehr by remember { mutableStateOf(false) }
    var offen by remember { mutableStateOf<Investegate.Meldung?>(null) }
    LaunchedEffect(settings.okInvestegate) {
        if (!settings.okInvestegate || geladen) return@LaunchedEffect
        liste = try { state.investegateListe(1) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
        geladen = true
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            Text(S.q5IgAlleTitel, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 20.dp))
            if (!settings.okInvestegate) {
                Text(S.q5IgPrivat, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                InvestegateBestaetigung(state, onNein = onDismiss)
            } else {
                val l = liste
                when {
                    l == null && !geladen -> EmptyHint(S.trLaedt)
                    l == null -> Text(S.q5Nicht, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    else -> {
                        l.forEach { m ->
                            Column(Modifier.fillMaxWidth().clickable { offen = m }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                                NamenText(m.firma, style = MaterialTheme.typography.titleSmall)
                                AuslaufText(m.titel, maxLines = 3, style = MaterialTheme.typography.bodyMedium)
                                PunktText(listOf(zeitZeile(m.zeit), m.quelle).filter { it.isNotBlank() }.joinToString(" · "))
                            }
                        }
                        if (seite < 3) Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                            FilterPill(S.trMehr) {
                                if (!laedtMehr) {
                                    laedtMehr = true
                                    scope.launch {
                                        val ziel = seite + 1
                                        try {
                                            val n = try { state.investegateListe(ziel) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
                                            if (n == null) fehler = true
                                            else { fehler = false; liste = (liste.orEmpty() + n).distinctBy { it.link }; seite = ziel }
                                        } finally {
                                            laedtMehr = false
                                        }
                                    }
                                }
                            }
                        }
                        if (fehler) Text(S.q5Nicht, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                        QuellenZeile(S.q5IgKategorie, Modifier.padding(horizontal = 20.dp), onClick = { oeffne(uri, Investegate.SEITE_LISTE) })
                        QuellenAnhang(listOf(INVESTEGATE_QUELLE))
                    }
                }
            }
        }
    }
    offen?.let { m -> InvestegateMeldungBlatt(state, m, onDismiss = { offen = null }) }
}

// ------------------------------------------------------------ Nasdaq Nordic

// Managers Transactions der Nasdaq Nordic (5.95): Unternehmensmeldungen aus Daenemark, Finnland, Island und dem
// Baltikum zur eigenen Aktie. Antippen oeffnet die Meldung im Browser, Anhaenge stehen darunter mit Dateinamen.
@Composable
internal fun NasdaqNordicKarte(state: AppState, land: String, name: String) {
    var liste by remember(land, name) { mutableStateOf<List<NasdaqNordic.Meldung>?>(null) }
    var geladen by remember(land, name) { mutableStateOf(false) }
    var alle by remember(land, name) { mutableStateOf(false) }
    LaunchedEffect(land, name) {
        liste = try { state.nordicMeldungen(land, name) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
        geladen = true
    }
    val uri = appLinks()
    Column {
        val l = liste
        when {
            l == null && !geladen -> { SectionHeader(S.vdKarteTitel); EmptyHint(S.trLaedt) }
            l == null -> {
                InsiderBrowserHinweis(land)
                Text(S.q5Nicht, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            l.isEmpty() -> {
                InsiderBrowserHinweis(land)
                Text(S.q5NnKeine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            else -> {
                SectionHeader(S.vdKarteTitel)
                l.take(if (alle) 20 else 5).forEach { m ->
                    Column(Modifier.fillMaxWidth().clickable { oeffne(uri, m.link) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        AuslaufText(m.titel, maxLines = 3, style = MaterialTheme.typography.titleSmall.schlagzeile())
                        PunktText(listOf(Fmt.date(m.zeit), m.markt).filter { it.isNotBlank() }.joinToString(" · "))
                    }
                    m.anhaenge.forEach { (datei, adresse) ->
                        QuellenZeile(datei, Modifier.padding(horizontal = 20.dp), onClick = { oeffne(uri, adresse) })
                    }
                }
                if (l.size > 5) ListenPfeil(alle) { alle = !alle }
            }
        }
    }
}

// Ungarn (MNB): Meldungen von Fuehrungskraeften als Links; die Meldung mit ihren PDFs oeffnet die Person selbst.
@Composable
internal fun MnbKarte(state: AppState, name: String) {
    var liste by remember(name) { mutableStateOf<List<InsiderEuropa.MnbMeldung>?>(null) }
    var geladen by remember(name) { mutableStateOf(false) }
    var alle by remember(name) { mutableStateOf(false) }
    LaunchedEffect(name) {
        liste = try { state.mnbMeldungen(name) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
        geladen = true
    }
    val uri = appLinks()
    Column {
        SectionHeader(S.vdKarteTitel)
        val l = liste
        when {
            l == null && !geladen -> EmptyHint(S.trLaedt)
            l == null -> Text(S.q5Nicht, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            l.isEmpty() -> Text(S.q5MnbKeine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            else -> {
                l.take(if (alle) 20 else 5).forEach { m ->
                    Column(Modifier.fillMaxWidth().clickable { oeffne(uri, m.link) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Text(m.emittent, style = MaterialTheme.typography.titleSmall)
                        PunktText(listOf(Politik.tag(m.tag).takeIf { it > 0 }?.let { Fmt.date(it) } ?: m.tag, m.id).joinToString(" · "))
                    }
                }
                if (l.size > 5) ListenPfeil(alle) { alle = !alle }
            }
        }
    }
}

// ------------------------------------------------------------ EODHD

// Nachrichten ueber EODHD (5.95): eigener Schluessel. Der Abschnitt bleibt eingeklappt und laedt erst beim
// Aufklappen; jeder Abruf kostet einen Teil des Tageskontingents des Anbieters.
@Composable
internal fun EodhdAbschnitt(state: AppState, symbol: String, oeffnen: (MarketApi.NewsItem, List<String>) -> Unit, merker: SeitenMerker? = null) {
    val offenZustand = gemerkt(merker, "eodhd/offen") { false }
    val offen = offenZustand.value
    var erg by gemerkt<NachrichtenApis.Ergebnis?>(merker, "eodhd/erg") { null }
    var geladen by gemerkt(merker, "eodhd/geladen") { false }
    val alleZustand = gemerkt(merker, "eodhd/alle") { false }
    LaunchedEffect(offen, symbol) {
        if (!offen || geladen) return@LaunchedEffect
        erg = try { state.eodhdNews(symbol) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
        geladen = true
    }
    val rows = ArrayList<ZeilenDaten>()
    if (offen) {
        val e = erg
        val meldung = e?.meldung
        val grau = MaterialTheme.colorScheme.onSurfaceVariant
        when {
            e == null && !geladen -> rows.add(ZeilenDaten("laedt") { EmptyHint(S.trLaedt) })
            e == null -> rows.add(ZeilenDaten("nicht") { Text(S.q5Nicht, style = MaterialTheme.typography.bodyMedium, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) })
            meldung != null -> rows.add(ZeilenDaten("meldung") {
                Text(
                    if (meldung == NewsRepository.LIMIT) S.q5EodLimit else dienstMeldung(meldung),
                    style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            })
            e.eintraege.isEmpty() -> rows.add(ZeilenDaten("keine") { Text(S.q5EodKeine, style = MaterialTheme.typography.bodyMedium, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) })
            else -> {
                e.eintraege.take(if (alleZustand.value) e.eintraege.size else 10).forEachIndexed { i, ein ->
                    rows.add(ZeilenDaten("n$i") {
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                oeffnen(
                                    MarketApi.NewsItem(
                                        title = ein.titel, link = ein.link, published = ein.zeit,
                                        summary = ein.zusammenfassung, source = "EODHD",
                                    ),
                                    ein.absaetze,
                                )
                            }.padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            Text(ein.titel, style = MaterialTheme.typography.titleSmall)
                            PunktText(
                                listOfNotNull(
                                    ein.zeit.takeIf { it > 0 }?.let { Fmt.relative(it) },
                                    ein.herausgeber.ifBlank { ein.quelle }.ifBlank { null },
                                ).joinToString(" · ")
                            )
                        }
                    })
                }
                if (e.eintraege.size > 10) rows.add(ZeilenDaten("mehr") {
                    ListenPfeil(alleZustand.value, label = if (alleZustand.value) S.less else S.bbAlle(e.eintraege.size)) { alleZustand.value = !alleZustand.value }
                })
                // Kontingent hinter dem Info-Symbol; bei erreichtem Limit steht der Satz oben (q5EodLimit)
                rows.add(ZeilenDaten("quelle") { InfoZeile(null, S.q5EodTitel, S.q5EodQuelle) })
            }
        }
    }
    ModellAusgabe(
        merker, "eodhd",
        ListenModell(titel = "", gross = true, kern = null, zeilen = rows, immerOffen = true,
            kopf = { SectionHeader(S.q5EodTitel, chevron = true, onClick = { offenZustand.value = !offenZustand.value }) }),
    )
}

internal fun LazyListScope.eodhdAbschnitt(merker: SeitenMerker) {
    listenAbschnitt(merker, "eodhd")
}
