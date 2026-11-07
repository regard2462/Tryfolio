package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.analystenGemerkt
import de.troetenrepublik.app.engine.Analystenbilanz
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.engine.Quartalsueberblick
import de.troetenrepublik.app.engine.Quartalsueberblick.PunktArt
import de.troetenrepublik.app.engine.Quartalsueberblick.Quelle
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Gleichwerte
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Quartalszahlen
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.market.ohneAbbruch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

// Heimatzeitzone der Termine und Konferenzen aus dem Yahoo-Profil.
private val NEW_YORK: ZoneId = ZoneId.of("America/New_York")

// Uebersicht zu den Quartalszahlen einer Aktie als Blatt von unten (5.97): Termin mit Uhrzeit und Konferenz,
// Erwartungen von Kalender und Yahoo, Punkte, die die Zahlen einordnen, und die Quellen der Werte. Ein Tipp auf
// „Zur Aktie“ oeffnet die Notierung aus Depot oder Verzeichnis der App.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuartalsUeberblickBlatt(
    state: AppState,
    termin: Quartalszahlen.Termin,
    eigene: Set<String>,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
) {
    // Profil im Hintergrund holen; daraus die Yahoo-Erwartungen zum Termin
    var profil by remember(termin.symbol) { mutableStateOf<MarketApi.Profile?>(null) }
    var geladen by remember(termin.symbol) { mutableStateOf(false) }
    LaunchedEffect(termin.symbol) {
        profil = ohneAbbruch { state.profileFor(termin.symbol) }.getOrNull()
        geladen = true
    }
    // Urteile der Analystenhaeuser; schon Geholtes zaehlt ohne neuen Abruf
    var urteile by remember(termin.symbol) { mutableStateOf<List<Analystenbilanz.Urteil>>(emptyList()) }
    LaunchedEffect(termin.symbol) {
        urteile = (analystenGemerkt(termin.symbol)?.verlauf
            ?: ohneAbbruch { state.market.analysten(termin.symbol) }.getOrNull())?.urteile.orEmpty()
    }
    val yAlle = profil?.quartal
    val ue = remember(termin, yAlle) { Quartalsueberblick.erstellen(termin, yAlle) }
    // Termin, Konferenz und Quellen von Yahoo nur, wenn sie zu diesem Termin gehoeren
    val y = yAlle?.takeIf { Quartalsueberblick.passt(termin, it) }
    // Notierung, die beim Oeffnen gemeint ist: eigene, sonst eine bekannte derselben Aktie
    val catalog = rememberCatalog()
    val familie = Gleichwerte.familien.collectAsStateWithLifecycle().value[termin.symbol].orEmpty()
    val ziel = remember(termin, eigene, familie, catalog) {
        Quartalsueberblick.notierung(termin.symbol, termin.name, eigene, familie, catalog?.aktien.orEmpty())
    }
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val anbieter = Quartalszahlen.anbieter(settings.keyFinnhub, settings.keyAlphaVantage)
    val name = Quartalszahlen.ohneGattung(Symbols.find(termin.symbol)?.name ?: termin.name)
    val tag = LocalDate.ofEpochDay(termin.tag)
    val uhr = uhrzeitVon(termin)
    val haeuser = remember(urteile) { Quartalsueberblick.haeuser(urteile, System.currentTimeMillis()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        LazyColumn(Modifier.fillMaxWidth()) {
            // Kopf: Name, darunter Kuerzel und Quartal
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(name, style = MaterialTheme.typography.headlineLarge)
                    PunktText(
                        listOfNotNull(termin.symbol, ue.quartal?.let { S.qzbQuartal(it) }).joinToString(PUNKT),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                NavRow(S.qzbZurAktie, name + PUNKT + ziel) {
                    onDismiss()
                    onOpen(ziel)
                }
            }
            // Fuehrt das Kuerzel des Kalenders auf eine andere Notierung, kommt sie zusaetzlich
            if (ziel != termin.symbol) item {
                NavRow(S.qzbKalenderNotierung(termin.symbol), S.qzbKalenderNotierungText) {
                    onDismiss()
                    onOpen(termin.symbol)
                }
            }
            item { SectionHeader(S.qzbTermin) }
            item {
                Column {
                    // Zweittermin von Yahoo als Spanne, wenn er auf einen anderen Kalendertag faellt
                    val von = y?.termin
                    val bis = y?.terminBis
                    val spanne = if (y?.geschaetzt == true && von != null && bis != null && hierTag(bis) != hierTag(von))
                        S.qzbTerminSpanne(Fmt.date(von), Fmt.date(bis)) else null
                    KeyValueRow(S.qzbTag, spanne ?: wochentag(tag) + ", " + Fmt.date(mittag(tag)))
                    if (termin.geschaetzt || y?.geschaetzt == true) {
                        AufklappText(S.qzbGeschaetztText, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                    }
                    KeyValueRow(S.qzbUhrzeit, uhrLang(uhr, tag))
                    // Konferenz nur bei festem Termin und nur, wenn sie am oder neben dem Termin liegt
                    val konferenz = y?.takeIf { it.geschaetzt != true }?.konferenz
                        ?.takeIf { k -> abs(nyTag(k).toEpochDay() - tag.toEpochDay()) <= 1 }
                    if (konferenz != null) KeyValueRow(
                        S.qzbKonferenz,
                        if (hierTag(konferenz) != tag) Fmt.dateTime(konferenz) else Fmt.time(konferenz),
                    )
                    AufklappText(S.qzbUhrText, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                }
            }
            item { QuellenZeile(S.qzZeitzone(ZoneId.systemDefault().id)) }
            item { SectionHeader(S.qzbZahlen) }
            // Nach den Zahlen steht der gemeldete Gewinn ueber den Erwartungen
            if (ue.gewinn.isNotEmpty()) {
                item { Zwischenkopf(S.qzbGewinn) }
                val gemeldet = ue.gemeldet
                if (gemeldet != null) item {
                    KeyValueRow(S.qzbGemeldet, epsWert(gemeldet, "USD"), valueColor = abweichungFarbe(ue.abweichung))
                }
                erwartet(ue.gewinn, ::epsWert)
            }
            if (ue.umsatz.isNotEmpty()) {
                item { Zwischenkopf(S.qzbUmsatz) }
                erwartet(ue.umsatz, ::umsatzWert)
            }
            if (ue.gewinn.isEmpty() && ue.umsatz.isEmpty()) item { EmptyHint(if (geladen) S.qzbKeineErwartung else S.loading) }
            item { Zwischenkopf(S.qzbAusblick) }
            item { AufklappText(S.qzbAusblickText, Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
            item { AufklappText(S.qzbZahlenText, Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
            // Punkte, die die Zahlen einordnen
            item { SectionHeader(S.qzbBewertung) }
            items(ue.punkte) { p ->
                Text(
                    punktSatz(p, ue),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            if (ue.punkte.isEmpty() && geladen) item {
                Text(
                    S.qzbKeineBewertung,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            item { AufklappText(S.qzbBewertungText, Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
            // Gemeldete Quartale gegen die jeweilige Erwartung, neueste zuerst
            if (ue.verlauf.isNotEmpty()) {
                item { SectionHeader(S.qzbVerlauf) }
                val waehrung = yAlle?.gewinnWaehrung ?: "USD"
                items(ue.verlauf.asReversed()) { q ->
                    val ende = runCatching { LocalDate.parse(q.ende) }.getOrNull()
                    val ist = q.ist
                    val soll = q.erwartet
                    KeyValueRow(
                        label = S.qzbQuartalBis(ende?.let { Fmt.date(mittag(it)) } ?: q.ende),
                        value = q.abweichung?.let { Fmt.signedPct(it, 1) } ?: "",
                        valueColor = abweichungFarbe(q.abweichung),
                        hint = if (ist != null && soll != null) S.epsActualVsEstimate(epsWert(ist, waehrung), epsWert(soll, waehrung)) else null,
                    )
                }
            }
            // Quellen der Werte
            item { SectionHeader(S.qzbVonWem) }
            val kalenderDa = ue.gewinn.any { it.quelle == Quelle.KALENDER } || ue.umsatz.any { it.quelle == Quelle.KALENDER } ||
                termin.epsErwartet != null || termin.epsVorjahr != null || termin.umsatz != null
            if (kalenderDa) item {
                Text(
                    S.qzbQuelleKalender(anbieter, termin.schaetzungen),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            if (yAlle != null && (y?.gewinn != null || y?.umsatz != null || yAlle.verlauf.isNotEmpty())) item {
                Text(
                    S.qzbQuelleYahoo(y?.gewinn?.analysten, y?.umsatz?.analysten),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            if (geladen && yAlle == null) item {
                Text(
                    S.qzbYahooFehlt,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            if (haeuser.isNotEmpty()) item {
                Text(
                    S.qzbHaeuser(haeuser.joinToString(", ")),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            item { AufklappText(S.qzbVonWemText, Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

// Die Zeilen einer Erwartungsliste: Mittelwert, Spanne und Vorjahr. Nennen mehrere Erwartungen dasselbe Vorjahr,
// steht es nur einmal.
private fun LazyListScope.erwartet(liste: List<Quartalsueberblick.Erwartung>, betrag: (Double, String) -> String) {
    val gesehen = HashSet<Double>()
    liste.forEach { e ->
        val mittel = e.mittel
        if (mittel != null) item {
            KeyValueRow(S.qzbErwartetVon(e.quelle.ordinal, e.analysten), betrag(mittel, e.waehrung))
        }
        val tief = e.tief
        val hoch = e.hoch
        if (tief != null && hoch != null) item {
            KeyValueRow(S.qzbSpanne, S.qzbVonBis(betrag(tief, e.waehrung), betrag(hoch, e.waehrung)))
        }
        val vorjahr = e.vorjahr
        if (vorjahr != null && gesehen.add(vorjahr)) item { KeyValueRow(S.qzbVorjahr, betrag(vorjahr, e.waehrung)) }
    }
}

// Gewinn je Aktie in der Waehrung der Erwartung, immer mit zwei Nachkommastellen.
private fun epsWert(v: Double, waehrung: String): String =
    (if (v < 0) "−" else "") + Fmt.mitSymbol(Fmt.num(abs(v), 2), Derivatives.currencySymbol(waehrung))

// Umsatz in der Waehrung der Erwartung, in Kurzform.
private fun umsatzWert(v: Double, waehrung: String): String =
    (if (v < 0) "−" else "") + Fmt.mitSymbol(Fmt.compact(abs(v)), Derivatives.currencySymbol(waehrung))

// Farbe der Abweichung einer gemeldeten Zahl gegen die Erwartung, wie bei den Ueberraschungen der Termine.
@Composable
private fun abweichungFarbe(abweichung: Double?): Color? = when {
    (abweichung ?: 0.0) > 2.0 -> LocalSignal.current.up
    (abweichung ?: 0.0) < -2.0 -> LocalSignal.current.down
    else -> null
}

// Kalendertag eines Zeitpunkts in New York, dem Heimatplatz der Termine von Yahoo.
private fun nyTag(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(NEW_YORK).toLocalDate()

// Kalendertag eines Zeitpunkts in der Ortszeit dessen, der die App nutzt.
private fun hierTag(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

// Ein Bewertungspunkt als Satz; die Waehrung kommt aus der Erwartung, aus der die Werte stammen: Spanne und
// Schaetzung von Yahoo, Gewinn aus der Erwartung mit Vorjahr, gemeldete Zahlen vom Kalender.
private fun punktSatz(p: Quartalsueberblick.Punkt, ue: Quartalsueberblick.Ueberblick): String {
    val yahoo = ue.gewinn.firstOrNull { it.quelle == Quelle.YAHOO }?.waehrung ?: "USD"
    val gewinn = ue.gewinn.firstOrNull { it.mittel != null && it.vorjahr != null }?.waehrung ?: "USD"
    val umsatz = ue.umsatz.firstOrNull { it.vorjahr != null }?.waehrung ?: "USD"
    fun eps(v: Double, waehrung: String = gewinn) = epsWert(v, waehrung)
    return when (p.art) {
        PunktArt.GEWINN_WACHSTUM -> S.qzbGewinnWachstum(eps(p.a!!), eps(p.b!!), Fmt.pct(abs(p.p!!), 1))
        PunktArt.GEWINN_RUECKGANG -> S.qzbGewinnRueckgang(eps(p.a!!), eps(p.b!!), Fmt.pct(abs(p.p!!), 1))
        PunktArt.GEWINN_AUS_VERLUST -> S.qzbGewinnAusVerlust(eps(p.a!!), eps(abs(p.b!!)))
        PunktArt.VERLUST_ERWARTET -> S.qzbVerlustErwartet(eps(abs(p.a!!)), p.b?.let { eps(it) })
        PunktArt.UMSATZ_VERAENDERUNG -> S.qzbUmsatzVeraenderung(umsatzWert(p.a!!, umsatz), umsatzWert(p.b!!, umsatz), Fmt.signedPct(p.p!!, 1))
        PunktArt.SPANNE_WEIT -> S.qzbSpanneWeit(eps(p.a!!, yahoo), eps(p.b!!, yahoo), p.n)
        PunktArt.SPANNE_ENG -> S.qzbSpanneEng(eps(p.a!!, yahoo), eps(p.b!!, yahoo), p.n)
        PunktArt.SCHAETZUNG_HOCH -> S.qzbSchaetzungHoch(eps(p.a!!, yahoo), eps(p.b!!, yahoo), Fmt.signedPct(p.p!!, 1), p.n, p.m)
        PunktArt.SCHAETZUNG_RUNTER -> S.qzbSchaetzungRunter(eps(p.a!!, yahoo), eps(p.b!!, yahoo), Fmt.signedPct(p.p!!, 1), p.n, p.m)
        PunktArt.SCHAETZUNG_GLEICH -> S.qzbSchaetzungGleich(eps(p.a!!, yahoo))
        PunktArt.TREFFER -> S.qzbTreffer(p.n!!, p.m!!, Fmt.signedPct(p.p ?: 0.0, 1))
        PunktArt.GEMELDET_UEBER -> S.qzbGemeldetUeber(eps(p.a!!, "USD"), eps(p.b!!, "USD"), Fmt.pct(abs(p.p!!), 1))
        PunktArt.GEMELDET_UNTER -> S.qzbGemeldetUnter(eps(p.a!!, "USD"), eps(p.b!!, "USD"), Fmt.pct(abs(p.p!!), 1))
        PunktArt.GEMELDET_WIE_ERWARTET -> S.qzbGemeldetWie(eps(p.a!!, "USD"), eps(p.b!!, "USD"))
    }
}
