package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Enso
import de.troetenrepublik.app.engine.LageErklaerungen
import de.troetenrepublik.app.engine.Lagebild
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.lagebildEingabe
import de.troetenrepublik.app.lagebildGemerkt
import de.troetenrepublik.app.lagebildImSpeicher
import de.troetenrepublik.app.lagebildLetzter
import de.troetenrepublik.app.lagebildMerken
import de.troetenrepublik.app.lagebildSpeichern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

// [Lage 0.77] Reiter "Lage" in Maerkte: oben das Gesamtbild (wie viele Kennzahlen auffaellig sind, was sich seit dem letzten
// Oeffnen geaendert hat), darunter je Thema eine Karte mit Grafik aus den Werten der App (LagebildDetail.kt). Keine
// Gesamtnote und keine Prognose; die Stufe einer Kennzahl kommt aus Lagebild.stufe.
// [0.78] Unter jeder Karte eine Tabelle zum Aufklappen mit allem zum Nachlesen (LageTabelle.kt). Laender, Engpaesse,
// Spannungsgebiete und Durchbruch-Themen oeffnen ein Blatt ueber der Lage; nach dem Schliessen steht die Lage unveraendert da.
//
// Laden: der zuletzt bekannte Stand steht sofort da (Speicher, sonst Datei, sonst nur was schon auf dem Geraet liegt); die
// Abrufe laufen danach gleichzeitig mit Zeitgrenze im Hintergrund und legen sich ueber den Stand. Was ein Abruf nicht
// liefert, bleibt vom letzten Stand stehen.

private fun vorzeichen(v: Double, stellen: Int): String =
    (if (v > 0) "+" else if (v < 0) "−" else "") + Fmt.num(abs(v), stellen)

// Wert einer Kennzahl als Text
internal fun lbWertText(k: Lagebild.Kennzahl): String = when (k.schluessel) {
    "ENGPASS_SCHLECHTESTER" -> Fmt.signedPct(k.wert, 0)
    "SPANNUNG", "ENGPASS_GESTOERT" -> S.lbVonGesamt(k.wert.toInt().toString(), k.zusatz.orEmpty())
    "KATASTROPHEN" -> if ((k.zusatz?.toIntOrNull() ?: 0) > 0) S.lbRot(k.wert.toInt().toString(), k.zusatz.orEmpty()) else k.wert.toInt().toString()
    "ENSO_ZUSTAND" -> S.ensoZustand(k.zusatz.orEmpty())
    else -> rohText(k.einheit, k.wert)
}

private fun rohText(e: Lagebild.Einheit, v: Double): String = when (e) {
    Lagebild.Einheit.PROZENT -> Fmt.pct(v, 1)
    Lagebild.Einheit.ANZAHL -> v.toInt().toString()
    Lagebild.Einheit.PUNKTE -> vorzeichen(v, 1)
    Lagebild.Einheit.SCHRITTE -> S.lbSchritte(vorzeichen(v, 1))
    Lagebild.Einheit.KATEGORIE -> S.ensoZustand(Enso.Zustand.entries.getOrNull(v.toInt())?.name.orEmpty())
}

private fun tagText(tag: Long): String = Fmt.date(tag * 86_400_000L)

@Composable
internal fun LagebildReiter(
    state: AppState, liste: LazyListState, aktiv: Boolean, oeffnen: (String) -> Unit, onEngpass: (String) -> Unit = {}, onOpen: (String) -> Unit = {},
) {
    // [0.78] Was schon im Speicher liegt, steht beim ersten Bild da (kein leerer Aufbau, der beim Zurueckkommen springt)
    var aussagen by remember { mutableStateOf(state.lagebildImSpeicher()?.aussagen) }
    var aenderungen by remember { mutableStateOf<List<Lagebild.Aenderung>>(emptyList()) }
    var seit by remember { mutableStateOf<Long?>(null) }
    var karte by remember { mutableStateOf(state.globusKarteBekannt()) }
    var erklaerungen by remember { mutableStateOf(LageErklaerungen.bekannt()) }
    var beschleunigung by remember { mutableStateOf(state.durchbruecheInhaltBekannt()?.beschleunigung) }
    // Blaetter ueber der Lage und offene Unterabschnitte; alles bleibt beim Schliessen eines Blatts und beim Drehen erhalten
    var spannungId by rememberSaveable { mutableStateOf<String?>(null) }
    var landA3 by rememberSaveable { mutableStateOf<String?>(null) }
    var durchbruchId by rememberSaveable { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    val offen = rememberSaveable(saver = Saver<LageOffen, String>(save = { it.text }, restore = { LageOffen(it) })) { LageOffen("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(aktiv) {
        if (!aktiv) return@LaunchedEffect
        // Namen der Laender und Zuordnung zum Laenderblatt, Erklaerungen der Tabellen
        if (karte == null) karte = runCatching { state.globusKarte() }.getOrNull()
        if (erklaerungen == null) erklaerungen = withContext(Dispatchers.Default) { LageErklaerungen.laden() }
        // 1. Sofort: der zuletzt bekannte Stand; gibt es keinen, was schon auf dem Geraet liegt
        val letzter = state.lagebildLetzter()
        if (aussagen == null) {
            aussagen = letzter?.aussagen
                ?: Lagebild.aussagen(state.lagebildEingabe(gespeichert = true)).takeIf { it.isNotEmpty() }
        }
        // 2. Im Hintergrund: alle Abrufe gleichzeitig, jeder mit Zeitgrenze; Fehlendes bleibt vom letzten Stand
        val alt = state.lagebildGemerkt()
        val neu = Lagebild.aussagen(state.lagebildEingabe())
        if (neu.isEmpty()) {
            if (aussagen == null) aussagen = emptyList()
            return@LaunchedEffect
        }
        val a = Lagebild.zusammenfuehren(aussagen ?: letzter?.aussagen, neu)
        aenderungen = Lagebild.aenderungen(alt, neu)
        seit = alt?.zeit?.takeIf { it > 0 }?.let { it / 86_400_000L }
        aussagen = a
        state.lagebildSpeichern(Lagebild.Stand(System.currentTimeMillis(), a))
        // Beim naechsten Oeffnen gilt dieser Stand als der letzte; was jetzt fehlt, bleibt vom alten Stand
        state.lagebildMerken(Lagebild.Gemerkt(System.currentTimeMillis(), (alt?.werte.orEmpty()) + Lagebild.merken(neu)))
    }
    // Kennzahlreihen der Durchbrueche (Beschleunigt sich selbst) fuer die Tabelle der Technik
    LaunchedEffect(aktiv) {
        if (aktiv && beschleunigung == null) beschleunigung = runCatching { state.durchbruecheInhalt() }.getOrNull()?.beschleunigung
    }
    // Tipps: Blaetter ueber der Lage, Unterabschnitte auf und zu; nur der Weg am Tabellenende wechselt die Seite
    val tippen: (LageTipp) -> Unit = { t ->
        when (t) {
            is LageTipp.Land -> if (karte?.nachA3(t.a3) != null) landA3 = t.a3
            is LageTipp.Engpass -> onEngpass(t.id)
            is LageTipp.Spannung -> spannungId = t.id
            is LageTipp.Durchbruch -> durchbruchId = t.id
            is LageTipp.Abschnitt -> offen.wechseln(t.schluessel)
            is LageTipp.Seite -> oeffnen(t.route)
        }
    }
    val ziele = LageZiele(tippen, karte)
    spannungId?.let { id -> SpannungsBlattOeffnen(state, id, onOpen = onOpen, onDismiss = { spannungId = null }) }
    landA3?.let { a3 -> LaenderBlattUeberLage(state, a3, onOpen) { landA3 = null } }
    durchbruchId?.let { id -> DurchbruchBlattOeffnen(state, id, onOpen) { durchbruchId = null } }
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
    val a = aussagen
    val erk = erklaerungen
    val db = beschleunigung
    val ix = remember { LageIndex() }
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        ix.n = 0
        if (a == null) {
            item { EmptyHint(S.trLaedt) }
            return@LazyColumn
        }
        lageEintrag(ix, "gesamt", "lage-gesamt") {
            Gesamtbild(a, aenderungen, seit) { t -> ix.karten[t.name]?.let { i -> scope.launch { liste.animateScrollToItem(i) } } }
        }
        for (t in Lagebild.Thema.entries) {
            val x = a.firstOrNull { it.thema == t }
            ix.karten[t.name] = ix.n
            if (x == null) {
                lageEintrag(ix, "thema-" + t.name, "lage-karte") { LageKarte(S.lbThema(t.name), 0, null) { LageWert(S.lbThema(t.name), S.lbOffen) } }
                continue
            }
            lageEintrag(ix, "thema-" + t.name, "lage-karte") {
                LageKarte(S.lbThema(t.name), Lagebild.stufe(x), x.stand, unten = false) {
                    when (t) {
                        Lagebild.Thema.KONJUNKTUR -> KonjunkturInhalt(x, ziele)
                        Lagebild.Thema.ZINSEN -> ZinsenInhalt(x, ziele)
                        Lagebild.Thema.GEOPOLITIK -> GeopolitikInhalt(x, ziele)
                        Lagebild.Thema.ROHSTOFFE -> RohstoffeInhalt(x, ziele)
                        Lagebild.Thema.KLIMA -> KlimaInhalt(x, ziele)
                        Lagebild.Thema.TECHNIK -> TechnikInhalt(x, ziele)
                    }
                }
            }
            lageTabelle(ix, t, lageAbschnitte(x, ziele, erk, db) { info = it }, offen, S.lbWeg(t.name)) { tippen(LageWege.weg(t)) }
        }
        lageEintrag(ix, "quellen", "lage-quellen") {
            // Kuerzel des Internationalen Waehrungsfonds folgt der Sprache (IWF, im Englischen IMF)
            val iwf = if (de.troetenrepublik.app.i18n.I18n.lang == de.troetenrepublik.app.data.Lang.DE) "IWF" else "IMF"
            QuellenAnhang(
                listOf(
                    QuelleLink(iwf, "https://www.imf.org/en/Publications/WEO"), QuelleLink("UCDP (CC BY 4.0)", "https://ucdp.uu.se"),
                    QuelleLink("$iwf PortWatch", "https://portwatch.imf.org/"), QuelleLink("GDELT", "https://www.gdeltproject.org"),
                    QuelleLink("GDACS", "https://www.gdacs.org/"),
                ),
            )
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

// Gesamtbild: Zahl der auffaelligen Kennzahlen, je Thema ein Streifen mit einem Feld je Kennzahl (Farbe nach Stufe), darunter
// was sich seit dem letzten Oeffnen geaendert hat. Ein Tipp auf ein Thema springt zu seiner Karte.
@Composable
private fun Gesamtbild(a: List<Lagebild.Aussage>, aenderungen: List<Lagebild.Aenderung>, seit: Long?, zuKarte: (Lagebild.Thema) -> Unit) {
    val signal = LocalSignal.current
    val sv = MaterialTheme.colorScheme.onSurfaceVariant
    val (auffaellig, gesamt) = Lagebild.auffaellige(a)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
    ) {
        Text(
            S.lbAuffaellig(auffaellig.toString(), gesamt.toString()), style = MaterialTheme.typography.titleMedium,
            color = if (auffaellig > 0) signal.warn else MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false,
        )
        Spacer(Modifier.height(12.dp))
        for (reihe in Lagebild.Thema.entries.chunked(3)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (t in reihe) {
                    val x = a.firstOrNull { it.thema == t }
                    Column(Modifier.weight(1f).clickable { zuKarte(t) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ThemaStreifen(x)
                        Text(S.lbThemaKurz(t.name), style = MaterialTheme.typography.labelSmall, color = sv, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
        if (seit != null) {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(S.lbSeit, style = MaterialTheme.typography.bodySmall, color = sv, modifier = Modifier.weight(1f))
                Text(
                    if (aenderungen.isEmpty()) tagText(seit) else S.lbAenderungen(aenderungen.size), style = MaterialTheme.typography.titleSmall,
                    color = if (aenderungen.isEmpty()) sv else MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false,
                )
            }
            for (x in aenderungen) AenderungZeile(x)
        }
    }
}

// Streifen eines Themas: ein Feld je Kennzahl, Farbe nach Stufe; ohne Daten ein graues Feld
@Composable
private fun ThemaStreifen(x: Lagebild.Aussage?) {
    val signal = LocalSignal.current
    val ruhig = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val leer = MaterialTheme.colorScheme.surfaceContainerHighest
    val stufen = x?.kennzahlen?.map { Lagebild.stufe(it) }.orEmpty()
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val n = stufen.size.coerceAtLeast(1)
        val luecke = 2.dp.toPx()
        val w = (size.width - luecke * (n - 1)) / n
        for (i in 0 until n) {
            val f: Color = when (stufen.getOrNull(i)) { null -> leer; 0 -> ruhig; 1 -> signal.warn; else -> signal.down }
            drawRoundRect(f, Offset(i * (w + luecke), 0f), Size(w, size.height), CornerRadius(3.dp.toPx()))
        }
    }
}

// Eine Aenderung: Bezeichnung, bisheriger Wert, neuer Wert mit gezeichnetem Richtungsdreieck
@Composable
private fun AenderungZeile(x: Lagebild.Aenderung) {
    val sv = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(S.lbName(x.schluessel, "").trim(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(rohText(x.einheit, x.von), style = MaterialTheme.typography.labelLarge, color = sv, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
        ChangeValue(x.nach - x.von, rohText(x.einheit, x.nach), style = MaterialTheme.typography.labelLarge, colored = false, nullAusText = false)
    }
}
