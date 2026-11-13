package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Uhr
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Fahrzeugrueckrufe
import de.troetenrepublik.app.market.Politik
import de.troetenrepublik.app.market.Produktrueckrufe
import de.troetenrepublik.app.market.Studien

// Offene US-Register je Firma im Abschnitt „Hintergrund“ der Wertpapierseite (0.77): klinische Studien
// (ClinicalTrials.gov), Fahrzeugrueckrufe und Maengeluntersuchungen (NHTSA), Produktrueckrufe (CPSC). Die Karten laden
// erst beim Aufklappen, der Abruf wird einen Tag gemerkt; ohne Treffer erscheint kein Abschnitt.

private fun registerDatum(iso: String): String = when {
    iso.length == 7 && de.troetenrepublik.app.i18n.I18n.lang == de.troetenrepublik.app.data.Lang.DE -> "${iso.substring(5)}.${iso.take(4)}"
    iso.length == 7 -> iso
    else -> Politik.tag(iso).takeIf { it > 0 }?.let { Fmt.date(it) } ?: iso
}

// Zeile einer Liste: Haupttext, darunter optional ein Zusatz und eine Punktzeile; ein Tipp oeffnet die Seite des Eintrags.
@Composable
private fun RegisterZeile(link: String?, oben: String, zusatz: String? = null, unten: String, untenFarbe: Color? = null) {
    val uri = appLinks()
    val m = if (!link.isNullOrBlank()) Modifier.clickable { runCatching { uri.openUri(link) } } else Modifier
    Column(Modifier.fillMaxWidth().then(m).padding(horizontal = 20.dp, vertical = 6.dp)) {
        // Studien-, Rückruf- und Produkttitel sind Bezeichnungen: ganz ausgeschrieben
        Text(oben, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        if (!zusatz.isNullOrBlank()) AufklappText(zusatz, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PunktText(unten, style = MaterialTheme.typography.bodySmall, color = untenFarbe ?: MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Ein Balken je Phase: Bezeichnung links, Balken proportional zur groessten Zahl, Zahl rechts.
@Composable
private fun PhasenBalken(zahlen: List<Pair<String, Int>>) {
    val groesste = zahlen.maxOf { it.second }.coerceAtLeast(1)
    // Je Phase eine eigene dezente Farbe; Namensbreite aus dem längsten Namen gemessen
    val farben = zahlen.indices.map { AnteilsFarben.folge(it) }
    val stil = MaterialTheme.typography.bodySmall
    val messer = androidx.compose.ui.text.rememberTextMeasurer()
    val dichte = androidx.compose.ui.platform.LocalDensity.current
    val namenBreite = with(dichte) { zahlen.maxOf { messer.measure(it.first, stil).size.width }.toDp() } + 2.dp
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        zahlen.forEachIndexed { i, (name, n) ->
            val farbe = farben[i]
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false, modifier = Modifier.width(namenBreite))
                Box(Modifier.weight(1f)) {
                    if (n > 0) Box(Modifier.fillMaxWidth(n / groesste.toFloat()).height(8.dp).clip(RoundedCornerShape(4.dp)).background(farbe))
                }
                Spacer(Modifier.width(10.dp))
                Text(Fmt.num(n.toDouble(), 0), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        }
    }
}

// ------------------------------------------------------------ Klinische Studien

@Composable
internal fun StudienKarte(state: AppState, symbol: String, firma: String, merker: SeitenMerker? = null) {
    val zustand = gemerkt<Studien.Ergebnis?>(merker, "reg/studien") { null }
    LaunchedEffect(symbol, firma) {
        if (zustand.value == null) zustand.value = runCatching { state.register.studien(firma) }.getOrNull()
    }
    val e = zustand.value
    val modelle = remember(e) { e?.takeIf { !it.leer }?.let { studienModelle(it) } }
    if (modelle != null) QuellenMelden(NennQuellen.CLINICALTRIALS)
    ModellAusgabe(merker, "stu-kopf", modelle?.get(0))
    ModellAusgabe(merker, "stu-bald", modelle?.get(1))
    ModellAusgabe(merker, "stu-fertig", modelle?.get(2))
}

private fun studienModelle(e: Studien.Ergebnis): List<ListenModell?> {
    val heute = Uhr.heute().toString()
    val bald = Studien.naechste(e.laufend, heute)
    val beendet = Studien.beendet(e.fertig, heute)
    val (p1, p2, p3) = Studien.jePhase(e.laufend)
    val kopf = ListenModell(
        titel = "", gross = true, kern = null, zeilen = emptyList(), immerOffen = true,
        kopf = {
            Column {
                SectionHeader(S.rgStudienTitel)
                if (e.laufend.isNotEmpty()) {
                    Text(
                        S.rgStudienLaufend(e.laufend.size), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp), maxLines = 1, softWrap = false,
                    )
                    if (p1 + p2 + p3 > 0) PhasenBalken(listOf(S.rgPhase(1) to p1, S.rgPhase(2) to p2, S.rgPhase(3) to p3))
                }
            }
        },
    )
    val baldModell = if (bald.isEmpty()) null else ListenModell(
        titel = S.rgStudienBald, gross = false, info = S.rgStudienInfo,
        kern = KernZeile(listOf(KernTeil(S.rgStudienBaldKern(registerDatum(bald.first().abschluss))))),
        zeilen = bald.map { s ->
            ZeilenDaten("b-${s.nct}") {
                RegisterZeile(
                    Studien.SEITE + s.nct, s.mittel.ifBlank { s.titel }, s.gebiet,
                    S.rgStudieEnde(s.phase, registerDatum(s.abschluss)),
                )
            }
        },
    )
    val fertigModell = if (beendet.isEmpty()) null else {
        val abgebrochen = beendet.count { it.status == "TERMINATED" }
        ListenModell(
            titel = S.rgStudienFertig, gross = false, info = if (baldModell == null) S.rgStudienInfo else null,
            kern = KernZeile(listOf(KernTeil(S.rgStudienFertigKern(beendet.size - abgebrochen, abgebrochen)))),
            zeilen = beendet.map { s ->
                val ab = s.status == "TERMINATED"
                ZeilenDaten("f-${s.nct}") {
                    RegisterZeile(
                        Studien.SEITE + s.nct, s.mittel.ifBlank { s.titel }, listOf(s.gebiet, s.grund).filter { it.isNotBlank() }.joinToString(" · "),
                        S.rgStudieBeendet(ab, s.phase, registerDatum(s.abschluss)), if (ab) LocalSignal.current.warn else null,
                    )
                }
            },
        )
    }
    return listOf(kopf, baldModell, fertigModell)
}

// ------------------------------------------------------------ Fahrzeugrueckrufe und Untersuchungen

@Composable
internal fun FahrzeugKarte(state: AppState, symbol: String, firma: String, merker: SeitenMerker? = null) {
    val zustand = gemerkt<Fahrzeugrueckrufe.Ergebnis?>(merker, "reg/fahrzeuge") { null }
    LaunchedEffect(symbol, firma) {
        if (zustand.value == null) zustand.value = runCatching { state.register.fahrzeuge(firma) }.getOrNull()
    }
    val e = zustand.value
    val modelle = remember(e) { e?.takeIf { !it.leer }?.let { fahrzeugModelle(it) } }
    ModellAusgabe(merker, "fz-kopf", modelle?.get(0))
    ModellAusgabe(merker, "fz-rueck", modelle?.get(1))
    ModellAusgabe(merker, "fz-unter", modelle?.get(2))
}

private fun fahrzeugModelle(e: Fahrzeugrueckrufe.Ergebnis): List<ListenModell?> {
    val kopf = ListenModell(titel = "", gross = true, kern = null, zeilen = emptyList(), immerOffen = true, kopf = { SectionHeader(S.rgFahrzeugTitel) })
    val rueck = if (e.rueckrufe.isEmpty()) null else ListenModell(
        titel = S.rgRueckrufeTitel, gross = false, info = S.rgFahrzeugInfo,
        kern = KernZeile(listOf(KernTeil(S.rgRueckrufeKern(e.rueckrufe.size)), KernTeil(S.rgFahrzeuge(Fmt.num(e.fahrzeuge.toDouble(), 0))))),
        zeilen = Fahrzeugrueckrufe.groesste(e.rueckrufe).map { r ->
            ZeilenDaten("r-${r.nummer}") {
                RegisterZeile(
                    Fahrzeugrueckrufe.rueckrufSeite(r.nummer), r.thema.ifBlank { r.nummer },
                    unten = listOfNotNull(registerDatum(r.tag), r.fahrzeuge.takeIf { it > 0 }?.let { S.rgFahrzeuge(Fmt.num(it.toDouble(), 0)) }).joinToString(" · "),
                )
            }
        },
    )
    val unter = if (e.untersuchungen.isEmpty()) null else ListenModell(
        titel = S.rgUntersuchungenTitel, gross = false, info = if (rueck == null) S.rgFahrzeugInfo else null,
        kern = KernZeile(listOf(KernTeil(S.rgUntersuchungenKern(e.untersuchungen.size)))),
        zeilen = e.untersuchungen.sortedByDescending { it.tag }.map { u ->
            ZeilenDaten("u-${u.nummer}") {
                RegisterZeile(Fahrzeugrueckrufe.untersuchungSeite(u.nummer), u.thema.ifBlank { u.nummer }, unten = S.rgUntersuchungSeit(u.art, registerDatum(u.tag)))
            }
        },
    )
    return listOf(kopf, rueck, unter)
}

// ------------------------------------------------------------ Produktrueckrufe

@Composable
internal fun ProduktKarte(state: AppState, symbol: String, firma: String, merker: SeitenMerker? = null) {
    val zustand = gemerkt<List<Produktrueckrufe.Rueckruf>?>(merker, "reg/produkte") { null }
    LaunchedEffect(symbol, firma) {
        if (zustand.value == null) zustand.value = runCatching { state.register.produkte(firma) }.getOrNull()
    }
    val e = zustand.value
    val modell = remember(e) { e?.takeIf { it.isNotEmpty() }?.let { produktModell(it) } }
    ModellAusgabe(merker, "pr-liste", modell)
}

private fun produktModell(r: List<Produktrueckrufe.Rueckruf>) = ListenModell(
    titel = S.rgProdukteTitel, gross = true, info = S.rgProdukteInfo,
    kern = KernZeile(listOf(KernTeil(S.rgProdukteKern(r.size)))),
    zeilen = r.map { x ->
        ZeilenDaten("p-${x.id}") {
            RegisterZeile(x.adresse, x.produkt, unten = listOf(registerDatum(x.tag), x.gefahr).filter { it.isNotBlank() }.joinToString(" · "))
        }
    },
)

// ------------------------------------------------------------ Verbraucherbeschwerden (nur Link)

@Composable
internal fun BeschwerdenKarte(firma: String, merker: SeitenMerker? = null) {
    val adresse = remember(firma) { de.troetenrepublik.app.market.Verbraucherbeschwerden.adresse(firma) }
    val modell = remember(adresse) {
        adresse?.let { a ->
            ListenModell(
                titel = "", gross = true, kern = null, immerOffen = true,
                zeilen = listOf(ZeilenDaten("link") {
                    val uri = appLinks()
                    NavRow(S.rgBeschwerdenTitel) { runCatching { uri.openUri(a) } }
                }),
            )
        }
    }
    ModellAusgabe(merker, "bs-link", modell)
}

internal fun LazyListScope.registerAbschnitte(merker: SeitenMerker, studien: Boolean, fahrzeuge: Boolean, produkte: Boolean, beschwerden: Boolean = false) {
    if (beschwerden) listenAbschnitt(merker, "bs-link")
    if (studien) {
        listenAbschnitt(merker, "stu-kopf")
        listenAbschnitt(merker, "stu-bald")
        listenAbschnitt(merker, "stu-fertig")
    }
    if (fahrzeuge) {
        listenAbschnitt(merker, "fz-kopf")
        listenAbschnitt(merker, "fz-rueck")
        listenAbschnitt(merker, "fz-unter")
    }
    if (produkte) listenAbschnitt(merker, "pr-liste")
}
