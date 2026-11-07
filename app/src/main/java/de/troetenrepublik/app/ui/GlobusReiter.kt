package de.troetenrepublik.app.ui

import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.engine.Boersenzeiten
import de.troetenrepublik.app.engine.Fruehindikator
import de.troetenrepublik.app.engine.Konjunkturphase
import de.troetenrepublik.app.engine.GlobusEbenen
import de.troetenrepublik.app.engine.Engpaesse
import de.troetenrepublik.app.engine.WeltRezession
import de.troetenrepublik.app.engine.GlobusAnsicht
import de.troetenrepublik.app.engine.GlobusFarbe
import de.troetenrepublik.app.engine.GlobusKarte
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.engine.GlobusModus
import de.troetenrepublik.app.engine.GlobusPfad
import de.troetenrepublik.app.engine.GlobusZeichner
import de.troetenrepublik.app.engine.Imf
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.GlobusIndizes
import de.troetenrepublik.app.market.ohneAbbruch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt

// Compose-Path als Ziel der gemeinsamen Zeichenfunktionen
private class ComposePfad(val pfad: Path) : GlobusPfad {
    override fun nach(x: Float, y: Float) = pfad.moveTo(x, y)
    override fun linie(x: Float, y: Float) = pfad.lineTo(x, y)
    override fun schliessen() = pfad.close()
}

private const val ZOOM_MIN = 0.85f
private const val ZOOM_MAX = 8f
private const val BREITE_MAX = 85.0

private fun modusTitel(m: GlobusModus) = when (m) {
    GlobusModus.BOERSE -> S.globusBoerse
    GlobusModus.WACHSTUM -> S.globusWachstum
    GlobusModus.AUSBLICK -> S.globusAusblick
    GlobusModus.INFLATION -> S.globusInflation
    GlobusModus.ARBEITSLOS -> S.globusArbeitslos
    GlobusModus.SCHULDEN -> S.globusSchulden
    GlobusModus.LEISTUNG -> S.globusLeistung
    GlobusModus.PHASE -> S.globusPhase
    GlobusModus.BOERSENZEIT -> S.globusBoerseZeit
    GlobusModus.DEPOT -> S.globusDepot
    GlobusModus.ENGPAESSE -> S.globusEngpaesse
}

// Reiter „Globus“ der Maerkte: drehbarer Globus, eingefaerbt nach Boerse heute, Wachstum, Ausblick oder Inflation, mit
// Laenderblatt beim Antippen und einer Weltuebersicht darunter. Geladen wird erst, wenn der Reiter gewaehlt ist (aktiv).
@Composable
fun GlobusReiter(state: AppState, liste: LazyListState, aktiv: Boolean, onOpen: (String) -> Unit, zuStimmung: () -> Unit, startEbene: String? = null) {
    var modus by rememberSaveable { mutableIntStateOf(GlobusModus.entries.indexOfFirst { it.name.equals(startEbene, ignoreCase = true) }.coerceAtLeast(0)) }
    val m = GlobusModus.entries[modus]
    var karte by remember { mutableStateOf<GlobusKarte?>(null) }
    var imf by remember { mutableStateOf(state.imfGespeichert()) }
    var indizes by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var fehlt by remember { mutableStateOf(false) }
    var land by remember { mutableStateOf<GlobusLand?>(null) }
    val jahr = remember { java.time.LocalDate.now().year }
    // [Globus Trend und Ebenen] gewaehltes Jahr der IWF-Ebenen, Fruehindikator, Rezessionszeichen, Depot, Uhrzeit
    var waehlJahr by rememberSaveable { mutableIntStateOf(jahr) }
    var cli by remember { mutableStateOf(state.fruehindikatorGespeichert()) }
    var rez by remember { mutableStateOf(state.rezessionGespeichert()) }
    // Weltbank-Schätzung der Arbeitslosigkeit (ILO) als Ersatz für Länder ohne IWF-Wert
    var wbAlos by remember { mutableStateOf<de.troetenrepublik.app.engine.Weltbank.Reihe?>(null) }
    var jetztMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // [Engpaesse] Lage der Engpaesse (erst geladen, wenn die Ebene gewaehlt ist) und der geoeffnete Engpass
    var engpass by remember { mutableStateOf<Engpaesse.Uebersicht?>(null) }
    var stelle by rememberSaveable { mutableStateOf<String?>(null) }
    val bewertung by state.valuation.collectAsStateWithLifecycle()
    val katalog = rememberCatalog()
    val monat = remember { java.time.YearMonth.now() }

    LaunchedEffect(aktiv) {
        if (!aktiv) return@LaunchedEffect
        if (karte == null) karte = ohneAbbruch { state.globusKarte() }.getOrNull()
        val d = ohneAbbruch { state.imfDaten() }.getOrNull()
        if (d != null) imf = d
        fehlt = d == null && imf == null
        indizes = ohneAbbruch { state.globusIndizes() }.getOrDefault(emptyMap())
        ohneAbbruch { state.fruehindikatorDaten() }.getOrNull()?.let { cli = it }
        ohneAbbruch { state.rezessionsDaten() }.getOrNull()?.let { rez = it }
        wbAlos = ohneAbbruch { state.weltbank("SL.UEM.TOTL.ZS") }.getOrNull()
    }
    // [Engpaesse] erst der gespeicherte Stand, dann der Abruf
    LaunchedEffect(aktiv, m == GlobusModus.ENGPAESSE) {
        if (!aktiv || m != GlobusModus.ENGPAESSE) return@LaunchedEffect
        if (engpass == null) engpass = ohneAbbruch { state.engpassGespeichert() }.getOrNull()
        ohneAbbruch { state.engpassUebersicht() }.getOrNull()?.let { engpass = it }
    }
    // Die Boersenzeiten rechnen mit der Uhr: jede Minute neu, solange die Ebene offen ist
    LaunchedEffect(aktiv, m) {
        if (!aktiv || m != GlobusModus.BOERSENZEIT) return@LaunchedEffect
        while (true) { jetztMs = System.currentTimeMillis(); delay(60_000) }
    }

    val jahre = remember(imf, jahr) { imfJahre(imf, jahr) }
    val wj = waehlJahr.coerceIn(jahre)
    val laenderDepot = remember(katalog) {
        val aus = HashMap<String, String>()
        katalog?.aktien?.forEach { if (it.c.isNotBlank()) aus[it.s] = it.c }
        katalog?.basiswerte?.forEach { if (it.c.isNotBlank()) aus.putIfAbsent(it.s, it.c) }
        katalog?.ipos?.abgeschlossen?.forEach { if (it.c.isNotBlank()) aus.putIfAbsent(it.s, it.c) }
        aus
    }
    val depot = remember(bewertung.positions, laenderDepot) {
        GlobusEbenen.anteile(bewertung.positions.map { it.position.symbol to it.value }) { laenderDepot[it] }
    }
    val minute = jetztMs / 60_000L

    val signal = LocalSignal.current
    val ocker = Kategorie.ocker
    val werte = remember(karte, m, indizes, imf, wj, cli, depot, monat, wbAlos) {
        val k = karte
        if (k == null) emptyList() else k.laender.map { wertVon(m, it, indizes, imf, wj, cli, monat, depot, wbAlos, jahr) }
    }
    val farben = remember(karte, m, werte, cli, imf, jahr, depot, monat, minute) {
        val k = karte
        val up = signal.up.toArgb(); val down = signal.down.toArgb(); val neutral = ocker.toArgb()
        val jetzt = java.time.Instant.ofEpochMilli(minute * 60_000L)
        if (k == null) emptyArray<Color>() else Array(k.laender.size) { i ->
            val l = k.laender[i]
            Color(when (m) {
                GlobusModus.PHASE -> GlobusFarbe.phase(Konjunkturphase.von(cli, imf, l.a3, monat, jahr)?.phase, up, down, neutral)
                GlobusModus.BOERSENZEIT -> GlobusFarbe.handel(Boersenzeiten.offen(l.a2, jetzt), up)
                GlobusModus.DEPOT -> GlobusFarbe.anteil(depot.nachLand[l.a2], neutral)
                GlobusModus.ENGPAESSE -> GlobusFarbe.GEDIMMT
                else -> GlobusFarbe.farbe(m, werte[i], up, down, neutral)
            })
        }
    }
    val punkte = remember(engpass, m, signal) {
        if (m != GlobusModus.ENGPAESSE) emptyList() else {
            val up = signal.up.toArgb(); val down = signal.down.toArgb()
            Engpaesse.STELLEN.map { GlobusEbenen.Punkt(it.id, it.lon, it.lat, GlobusFarbe.engpass(engpass?.lage?.get(it.id)?.abw, up, down)) }
        }
    }
    val sonne = remember(m, minute) {
        if (m == GlobusModus.BOERSENZEIT) Boersenzeiten.sonne(java.time.Instant.ofEpochMilli(minute * 60_000L)) else null
    }

    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        item(key = "globus-wahl") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlobusModus.entries.forEachIndexed { i, x -> Chip(modusTitel(x), i == modus) { modus = i } }
            }
        }
        item(key = "globus-kugel") {
            val k = karte
            if (k == null) Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color.Black), contentAlignment = Alignment.Center) {
                Text(S.loading, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF9A9AA0))
            } else GlobusAnsichtFlaeche(k, farben, sonne, punkte, { stelle = it }) { land = it }
        }
        item(key = "globus-legende") { GlobusLegende(m, Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) }
        if (m == GlobusModus.ENGPAESSE) {
            item(key = "globus-engpass-liste") { EngpassListe(engpass) { stelle = it } }
            item(key = "globus-weltlage") { WeltlageKasten(state, onOpen) }
        }
        if (fehlt && m.mitJahr) item(key = "globus-fehlt") { EmptyHint(S.globusFehlt) }
        val d = imf
        val k = karte
        if (m.mitJahr && d != null) item(key = "globus-jahr") { JahrRegler(wj, jahre, jahr) { waehlJahr = it } }
        if (m == GlobusModus.DEPOT) item(key = "globus-depot") {
            DepotHinweis(depot, k, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        if (k != null && m != GlobusModus.BOERSENZEIT && m != GlobusModus.PHASE && m != GlobusModus.ENGPAESSE) item(key = "globus-rang") {
            val alle = werte.mapIndexedNotNull { i, w -> w?.takeIf { it.isFinite() }?.let { GlobusEbenen.Rang(k.laender[i].a3, it) } }
            Rangliste(modusTitel(m), alle, k, { wertText(m, it) }) { land = it }
        }
        if (d != null && k != null) item(key = "globus-welt") { WeltUebersicht(d, k, jahr, cli, rez, monat, zuStimmung) }
        item(key = "globus-quelle") {
            Text(
                S.globusQuelle(jahr), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            Spacer(Modifier.height(40.dp))
        }
    }
    land?.let { LaenderBlatt(state, it, indizes, imf, jahr, onOpen) { land = null } }
    stelle?.let { id -> EngpassBlatt(state, id, engpass, karte, onOpen) { stelle = null } }
}

private fun wertVon(
    m: GlobusModus, l: GlobusLand, indizes: Map<String, Double>, d: Imf.Datensatz?, jahr: Int,
    cli: Fruehindikator.Datensatz?, monat: java.time.YearMonth, depot: GlobusEbenen.Anteile,
    alos: de.troetenrepublik.app.engine.Weltbank.Reihe?, heute: Int,
): Double? = when (m) {
    GlobusModus.BOERSE -> indizes[l.a2]
    // Phase und Börsenzeiten haben keine Zahl für eine Rangliste
    GlobusModus.PHASE, GlobusModus.BOERSENZEIT, GlobusModus.ENGPAESSE -> null
    GlobusModus.DEPOT -> depot.nachLand[l.a2]?.times(100.0)
    GlobusModus.ARBEITSLOS -> d?.let { Imf.land(it, l.a3, jahr) }?.arbeitslosigkeit
        // Ersatz ohne IWF-Wert: letzte Weltbank-Schätzung, im laufenden Jahr oder im Jahr der Schätzung
        ?: alos?.werte?.get(l.a3)?.takeIf { jahr == heute || it.jahr == jahr }?.wert
    else -> d?.let { Imf.land(it, l.a3, jahr) }?.let {
        when (m) {
            GlobusModus.WACHSTUM -> it.wachstum
            GlobusModus.AUSBLICK -> it.ausblick
            GlobusModus.ARBEITSLOS -> it.arbeitslosigkeit
            GlobusModus.SCHULDEN -> it.schulden
            GlobusModus.LEISTUNG -> it.leistungsbilanz
            else -> it.inflation
        }
    }
}

// [Globus Trend und Ebenen] Jahre des Reglers: ab 2000 (soweit der IWF-Datensatz so weit zurückreicht) bis zum letzten Prognosejahr.
private fun imfJahre(d: Imf.Datensatz?, jahr: Int): IntRange {
    val alle = d?.werte?.get(Imf.WACHSTUM)?.values?.flatMap { it.keys }.orEmpty()
    if (alle.isEmpty()) return jahr..jahr
    return maxOf(2000, alle.min())..alle.max()
}

// Zahl einer Ebene für die Rangliste
private fun wertText(m: GlobusModus, v: Double): String = when (m) {
    GlobusModus.BOERSE, GlobusModus.WACHSTUM, GlobusModus.AUSBLICK, GlobusModus.LEISTUNG -> Fmt.signedPct(v, 1)
    GlobusModus.PHASE -> Fmt.num(v, 1)
    GlobusModus.SCHULDEN -> Fmt.pct(v, 0)
    else -> Fmt.pct(v, 1)
}

// Der Globus selbst: Ziehen dreht (mit Schwung), zwei Finger oder Mausrad zoomen, ein Tipp waehlt ein Land.
@Composable
private fun GlobusAnsichtFlaeche(
    karte: GlobusKarte, farben: Array<Color>, sonne: Boersenzeiten.Sonne?, punkte: List<GlobusEbenen.Punkt>, onPunkt: (String) -> Unit, onLand: (GlobusLand) -> Unit,
) {
    var lon by remember { mutableDoubleStateOf(10.0) }
    var lat by remember { mutableDoubleStateOf(28.0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var groesse by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    var schwung by remember { mutableStateOf<Job?>(null) }
    val dichte = LocalDensity.current.density
    val ansicht = remember { GlobusAnsicht() }
    val pfade = remember(karte) { Array(karte.laender.size) { Path().also { p -> p.fillType = PathFillType.EvenOdd } } }
    val adapter = remember(pfade) { Array(pfade.size) { ComposePfad(pfade[it]) } }
    val grenzen = remember(karte) { Path() }
    val grenzAdapter = remember(grenzen) { ComposePfad(grenzen) }
    val tmp = remember { DoubleArray(2) }
    val bild = remember { FloatArray(2) }
    val nachtPfad = remember { Path() }
    val nachtAdapter = remember(nachtPfad) { ComposePfad(nachtPfad) }

    fun radius(): Float = min(groesse.width, groesse.height) / 2f * 0.94f * zoom

    fun drehen(dx: Float, dy: Float) {
        val r = radius()
        if (r <= 1f) return
        val grad = Math.toDegrees((1.0 / r))
        var l = lon - dx * grad
        if (l > 180.0) l -= 360.0 else if (l < -180.0) l += 360.0
        lon = l
        lat = (lat + dy * grad).coerceIn(-BREITE_MAX, BREITE_MAX)
    }

    fun antippen(p: Offset) {
        if (groesse.width == 0) return
        ansicht.setze(lon, lat, groesse.width / 2f, groesse.height / 2f, radius())
        // Ebene Engpaesse: der naechste Punkt im Umkreis von etwa einem Daumen gewinnt vor dem Land
        if (punkte.isNotEmpty()) {
            var bester: String? = null
            var abstand = 34f * dichte
            for (pt in punkte) {
                if (!ansicht.bildpunkt(pt.lon, pt.lat, bild)) continue
                val d = kotlin.math.hypot(bild[0] - p.x, bild[1] - p.y)
                if (d < abstand) { abstand = d; bester = pt.id }
            }
            if (bester != null) { onPunkt(bester); return }
        }
        if (!ansicht.rueck(p.x, p.y, tmp)) return
        // Etwa ein Fingerbreit Spielraum fuer kleine Staaten
        val tol = Math.toDegrees(10.0 * dichte / radius().toDouble()).coerceAtMost(4.0)
        karte.landBei(tmp[0], tmp[1], tol)?.let(onLand)
    }

    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
    Canvas(
        Modifier.fillMaxSize().clipToBounds().background(Color.Black).onSizeChanged { groesse = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == PointerEventType.Scroll) {
                            val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            zoom = (zoom * exp(-dy * 0.12f)).coerceIn(ZOOM_MIN, ZOOM_MAX)
                            e.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    schwung?.cancel()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var bewegt = false
                    var gesamt = Offset.Zero
                    while (true) {
                        val ev = awaitPointerEvent()
                        val offen = ev.changes.filter { it.pressed }
                        if (offen.isEmpty()) {
                            val up = ev.changes.firstOrNull()
                            if (!bewegt && up != null && up.changedToUp()) antippen(down.position)
                            else if (bewegt) {
                                val v = tracker.calculateVelocity()
                                if (abs(v.x) + abs(v.y) > 200f) {
                                    schwung = scope.launch {
                                        var vx = v.x; var vy = v.y
                                        var zuletzt = System.nanoTime()
                                        while (abs(vx) + abs(vy) > 25f) {
                                            delay(16)
                                            val jetzt = System.nanoTime()
                                            val dt = (jetzt - zuletzt) / 1e9f
                                            zuletzt = jetzt
                                            drehen(vx * dt, vy * dt)
                                            val f = exp(-3.2f * dt)
                                            vx *= f; vy *= f
                                        }
                                    }
                                }
                            }
                            break
                        }
                        if (offen.size >= 2) {
                            bewegt = true
                            val a = offen[0].position; val b = offen[1].position
                            val abstand = (a - b).getDistance()
                            val pa = offen[0].previousPosition; val pb = offen[1].previousPosition
                            val vorher = (pa - pb).getDistance()
                            if (vorher > 1f && abstand > 1f) zoom = (zoom * abstand / vorher).coerceIn(ZOOM_MIN, ZOOM_MAX)
                            offen.forEach { it.consume() }
                            continue
                        }
                        val c = offen[0]
                        tracker.addPosition(c.uptimeMillis, c.position)
                        if (c.positionChanged()) {
                            val delta = c.position - c.previousPosition
                            gesamt += delta
                            if (!bewegt && gesamt.getDistance() > slop) bewegt = true
                            if (bewegt) { drehen(delta.x, delta.y); c.consume() }
                        }
                    }
                }
            },
    ) {
        val r = min(size.width, size.height) / 2f * 0.94f * zoom
        ansicht.setze(lon, lat, size.width / 2f, size.height / 2f, r)
        val mitte = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color(GlobusFarbe.KUGEL), r, mitte)
        val mindest = 0.8f
        for (i in pfade.indices) {
            val p = pfade[i]
            p.rewind()
            if (GlobusZeichner.flaeche(karte.laender[i], ansicht, adapter[i], mindest)) {
                drawPath(p, farben.getOrElse(i) { Color(GlobusFarbe.OHNE_DATEN) })
            }
        }
        // Nachtseite (Ebene Börsenzeiten): dunklere Halbkugel aus dem Sonnenstand
        if (sonne != null) {
            nachtPfad.rewind()
            if (Boersenzeiten.nacht(sonne, ansicht, nachtAdapter)) drawPath(nachtPfad, Color(0x8C000000))
        }
        grenzen.rewind()
        GlobusZeichner.linien(karte.linien, ansicht, grenzAdapter, mindest)
        drawPath(
            grenzen, Color(GlobusFarbe.GRENZE).copy(alpha = 0.75f),
            style = Stroke(width = 0.6f * dichte, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // Punkte der Ebene Engpaesse: Ring und gefuellter Punkt
        for (pt in punkte) {
            if (!ansicht.bildpunkt(pt.lon, pt.lat, bild)) continue
            val o = Offset(bild[0], bild[1])
            drawCircle(Color(pt.farbe).copy(alpha = 0.3f), 11f * dichte, o)
            drawCircle(Color(pt.farbe), 6f * dichte, o)
            drawCircle(Color.White.copy(alpha = 0.85f), 6f * dichte, o, style = Stroke(width = 1.2f * dichte))
        }
        drawCircle(Color(0xFF2C2C30), r, mitte, style = Stroke(width = 1f * dichte))
    }
    }
}

// Kleine Legende je Ebene: Verlauf von schwach nach stark, Farbfelder (Phasen, Börse) und „Keine Daten“.
@Composable
private fun GlobusLegende(m: GlobusModus, modifier: Modifier) {
    val signal = LocalSignal.current
    val grau = Color(0xFF3A3A3E)
    val schrift = MaterialTheme.typography.labelSmall
    val keine = Color(GlobusFarbe.OHNE_DATEN)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (m) {
            GlobusModus.PHASE -> {
                val phasen = Fruehindikator.Phase.entries
                val farbe = { p: Fruehindikator.Phase ->
                    Color(GlobusFarbe.phase(p, signal.up.toArgb(), signal.down.toArgb(), Kategorie.ocker.toArgb()))
                }
                for (zeile in phasen.chunked(2)) Row(Modifier.fillMaxWidth()) {
                    for (p in zeile) Box(Modifier.weight(1f)) { Farbfeld(farbe(p), S.trendPhase(p)) }
                }
                Farbfeld(keine, S.globusKeineDaten)
                Hinweis(S.globusLegPhase)
            }
            GlobusModus.BOERSENZEIT -> {
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { Farbfeld(signal.up, S.globusOffen) }
                    Box(Modifier.weight(1f)) { Farbfeld(grau, S.globusGeschlossen) }
                    Box(Modifier.weight(1f)) { Farbfeld(keine, S.globusKeineDaten) }
                }
                Hinweis(S.globusLegBoerse)
            }
            GlobusModus.ENGPAESSE -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.signedPct(-Engpaesse.VOLL_PROZENT, 0), style = schrift, maxLines = 1, softWrap = false)
                    Box(Modifier.padding(horizontal = 8.dp).weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(listOf(signal.down, Color(GlobusFarbe.PUNKT_OHNE), signal.up))))
                    Text(Fmt.signedPct(Engpaesse.VOLL_PROZENT, 0), style = schrift, maxLines = 1, softWrap = false)
                }
                Farbfeld(Color(GlobusFarbe.PUNKT_OHNE), S.engOhneVergleich)
                Hinweis(S.globusLegEngpaesse)
            }
            GlobusModus.DEPOT -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.pct(0.0, 0), style = schrift, maxLines = 1, softWrap = false)
                    Box(Modifier.padding(horizontal = 8.dp).weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(listOf(grau, Kategorie.ocker))))
                    Text(Fmt.pct(GlobusFarbe.ANTEIL_VOLL * 100, 0), style = schrift, maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(16.dp))
                    Farbfeld(keine, S.globusKeineDaten)
                }
                Hinweis(S.globusLegDepot)
            }
            else -> {
                val voll = GlobusFarbe.voll(m)
                val mitte = GlobusFarbe.mitte(m)
                val farben = when {
                    m == GlobusModus.INFLATION -> listOf(grau, Kategorie.ocker)
                    GlobusFarbe.niedrigGut(m) -> listOf(signal.up, grau, signal.down)
                    else -> listOf(signal.down, grau, signal.up)
                }
                val vorzeichen = m == GlobusModus.BOERSE || m == GlobusModus.WACHSTUM || m == GlobusModus.AUSBLICK || m == GlobusModus.LEISTUNG
                fun text(v: Double) = if (vorzeichen) Fmt.signedPct(v, 0) else Fmt.pct(v, 0)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (m == GlobusModus.INFLATION) text(0.0) else text(mitte - voll), style = schrift, maxLines = 1, softWrap = false)
                    Box(Modifier.padding(horizontal = 8.dp).weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(farben)))
                    Text(if (m == GlobusModus.INFLATION) text(voll) else text(mitte + voll), style = schrift, maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(16.dp))
                    Farbfeld(keine, S.globusKeineDaten)
                }
                when (m) {
                    GlobusModus.BOERSE -> Hinweis(S.globusLegBoerseHeute)
                    GlobusModus.ARBEITSLOS -> Hinweis(S.globusLegArbeitslos)
                    GlobusModus.SCHULDEN -> Hinweis(S.globusLegSchulden)
                    GlobusModus.LEISTUNG -> Hinweis(S.globusLegLeistung)
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun Farbfeld(farbe: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(12.dp).height(12.dp).clip(RoundedCornerShape(3.dp)).background(farbe))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun Hinweis(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// Zeitregler der IWF-Ebenen: Jahr von 2000 (soweit vorhanden) bis zum letzten Prognosejahr, Prognosejahre in der Beschriftung markiert.
@Composable
private fun JahrRegler(wahl: Int, jahre: IntRange, aktuell: Int, onWahl: (Int) -> Unit) {
    if (jahre.first >= jahre.last) return
    val schrift = MaterialTheme.typography.labelSmall
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val bez = S.globusJahrRegler
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(S.globusJahr(wahl, wahl >= aktuell), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
        Slider(
            value = wahl.toFloat(), onValueChange = { onWahl(it.roundToInt().coerceIn(jahre)) },
            valueRange = jahre.first.toFloat()..jahre.last.toFloat(), steps = (jahre.last - jahre.first - 1).coerceAtLeast(0),
            modifier = Modifier.semantics { contentDescription = bez },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${jahre.first}", style = schrift, color = grau, maxLines = 1, softWrap = false)
            Text(S.globusJahr(jahre.last, jahre.last >= aktuell), style = schrift, color = grau, maxLines = 1, softWrap = false)
        }
    }
}

// Zur Ebene Mein Depot: Anteil ohne Land und Hinweis bei einem Land über der Hälfte.
@Composable
private fun DepotHinweis(depot: GlobusEbenen.Anteile, karte: GlobusKarte?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (depot.leer) {
            Hinweis(S.globusDepotLeer)
            return@Column
        }
        if (depot.unzuordenbar > 0.0) Hinweis(S.globusDepotNichtZuordenbar(Fmt.pct(depot.unzuordenbar * 100, 0)))
        val g = depot.groesstes
        if (g != null && g.value > GlobusEbenen.KLUMPEN) {
            val name = karte?.nachA2(g.key)?.name(I18n.locale.language == "de") ?: g.key
            Text(S.globusKlumpen(name, Fmt.pct(g.value * 100, 0)), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// Die fünf höchsten und fünf niedrigsten Länder der aktiven Ebene, Tippen öffnet das Länderblatt.
@Composable
private fun Rangliste(ebene: String, alle: List<GlobusEbenen.Rang>, karte: GlobusKarte, text: (Double) -> String, onLand: (GlobusLand) -> Unit) {
    if (alle.isEmpty()) return
    val (oben, unten) = remember(alle) { GlobusEbenen.rangliste(alle) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(S.globusRangliste(ebene), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        for ((titel, liste) in listOf(S.globusHoechste to oben, S.globusNiedrigste to unten)) {
            if (liste.isEmpty()) continue
            Text(titel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 6.dp))
            for (r in liste) {
                val l = karte.nachA3(r.code) ?: continue
                KeyValueRow(l.name(I18n.locale.language == "de"), text(r.wert), onClick = { onLand(l) })
            }
        }
    }
}

private fun landName(k: GlobusKarte, code: String) = k.nachA3(code)?.name(I18n.locale.language == "de") ?: code

// Blatt zu einem Land: Leitindex, dann die Abschnitte Wirtschaft, Trend, Risiken und Unternehmen (je eigene Datei).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LaenderBlatt(state: AppState, land: GlobusLand, indizes: Map<String, Double>, imf: Imf.Datensatz?, jahr: Int, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val signal = LocalSignal.current
    val z = remember(land, imf) { imf?.let { Imf.land(it, land.a3, jahr) } }
    val index = GlobusIndizes.NACH_LAND[land.a2]
    val aend = indizes[land.a2]
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(land.name(I18n.locale.language == "de"), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 20.dp))
            if (index != null) KeyValueRow(
                if (index.etf) S.globusIndexEtf(index.name) else S.globusIndex + PUNKT + index.name, aend?.let { Fmt.signedPct(it) } ?: "–",
                valueColor = aend?.let { if (it >= 0) signal.up else signal.down },
            )
            LaenderWirtschaft(state, land, z, jahr, onOpen)
            LaenderTrend(state, land, imf, jahr)
            LaenderRisiken(state, land)
            LaenderUnternehmen(state, land, onOpen)
            LaenderMehr(state, land)
            Text(
                S.globusQuelle(jahr), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

// Einklappbare Weltuebersicht: Weltwachstum mit Pfad, Rezessionen, Aufsteiger und Abschwaecher, Risiken.
@Composable
private fun WeltUebersicht(
    d: Imf.Datensatz, karte: GlobusKarte, jahr: Int, cli: Fruehindikator.Datensatz?, rez: WeltRezession.Daten?,
    monat: java.time.YearMonth, zuStimmung: () -> Unit,
) {
    var offen by rememberSaveable { mutableStateOf(true) }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    val welt = remember(cli, d, monat, jahr) { Konjunkturphase.welt(cli, d, monat, jahr) }
    val u = remember(d, jahr) { Imf.ueberblick(d, jahr) }
    val signal = LocalSignal.current
    Row(
        Modifier.fillMaxWidth().clickable { offen = !offen }.padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(S.globusWelt, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        AufklappPfeil(offen)
    }
    if (!offen) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (u.welt != null && u.weltNaechstes != null) Text(S.globusWeltWachstum(jahr, Fmt.pct(u.welt, 1), Fmt.pct(u.weltNaechstes, 1)), style = MaterialTheme.typography.bodyMedium)
        if (u.pfad.size >= 2) Pfadlinie(u.pfad, jahr, Modifier.fillMaxWidth().height(44.dp), signal.neutral)
        if (u.laender > 0) Text(
            S.globusRezession(u.rezession, u.laender, u.rezessionAnteil?.let { Fmt.pct(it * 100, 0) }),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clickable { info = S.rezTitelWachstum to S.rezInfoWachstum },
        )
        if (u.aufsteiger.isNotEmpty()) RangZeile(S.globusAufsteiger, u.aufsteiger.map { rangWeg(landName(karte, it.code), it) }, signal.up)
        if (u.abschwaecher.isNotEmpty()) RangZeile(S.globusAbschwaecher, u.abschwaecher.map { rangWeg(landName(karte, it.code), it) }, signal.down)
        val risiken = ArrayList<String>()
        if (u.hoheInflationAnzahl > 0) risiken += S.globusHoheInflation(u.hoheInflationAnzahl, u.hoheInflation.joinToString(", ") { landName(karte, it.code) })
        if (u.schuldenSchwachAnzahl > 0) risiken += S.globusSchuldenSchwach(u.schuldenSchwachAnzahl, u.schuldenSchwach.joinToString(", ") { landName(karte, it.code) })
        if (risiken.isNotEmpty()) {
            Text(S.globusRisiken, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
            for (r in risiken) Text("• $r", style = MaterialTheme.typography.bodyMedium)
        }
    }
    RezessionsZeichen(cli, rez, welt, monat) { info = it }
    NavRow(S.globusFruehwarn, "", zuStimmung)
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
}

// [Globus Trend und Ebenen] Rezession Welt: vier Zahlen mit Quelle und Stand, jede mit Erklärung, ohne Gesamturteil.
@Composable
private fun RezessionsZeichen(cli: Fruehindikator.Datensatz?, rez: WeltRezession.Daten?, welt: Konjunkturphase.Welt, monat: java.time.YearMonth, onInfo: (Pair<String, String>) -> Unit) {
    val signal = LocalSignal.current
    val anteil = Fruehindikator.anteilAbschwung(cli, monat)
    if (anteil.mitDaten == 0 && welt.mitPhase == 0 && (rez == null || rez.leer)) return
    fun punkte(v: Double, vorzeichen: Boolean): String =
        (if (vorzeichen) (if (v >= 0) "+" else "−") else "") + Fmt.num(kotlin.math.abs(v), 2) + " " + S.fwPunkte
    fun tag(w: WeltRezession.Wert) = Fmt.date(w.tag * 86_400_000L)
    fun monatText(w: WeltRezession.Wert) = java.time.LocalDate.ofEpochDay(w.tag).let { "%02d/%d".format(it.monthValue, it.year) }
    Text(S.rezTitel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp))
    if (anteil.mitDaten > 0) KeyValueRow(
        S.rezAbschwung, S.rezAbschwungWert(anteil.abschwung, anteil.mitDaten, Fmt.pct(anteil.abschwung * 100.0 / anteil.mitDaten, 0)),
        onClick = { onInfo(S.rezAbschwung to S.rezInfoAbschwung) },
    )
    if (welt.mitPhase > 0) KeyValueRow(
        S.rezPhaseWelt,
        S.rezPhaseWeltWert(welt.abschwung, welt.mitPhase, Fmt.pct(welt.abschwung * 100.0 / welt.mitPhase, 0), welt.bipAnteil?.let { Fmt.pct(it * 100, 0) }),
        onClick = { onInfo(S.rezPhaseWelt to S.rezInfoPhaseWelt) },
    )
    rez?.zinskurve?.let { KeyValueRow(S.rezZins, punkte(it.wert, true) + " (" + tag(it) + ")", onClick = { onInfo(S.rezZins to S.rezInfoZins) }) }
    rez?.sahm?.let {
        KeyValueRow(
            S.rezSahm, punkte(it.wert, false) + " (" + monatText(it) + ")",
            valueColor = if (it.wert >= WeltRezession.SCHWELLE_SAHM) signal.down else null,
            onClick = { onInfo(S.rezSahm to S.rezInfoSahm) },
        )
    }
    rez?.wahrscheinlichkeit?.let { KeyValueRow(S.rezWahrsch, Fmt.pct(it.wert, 1) + " (" + monatText(it) + ")", onClick = { onInfo(S.rezWahrsch to S.rezInfoWahrsch) }) }
    Text(S.rezQuelle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
}

// Wachstum im Jahr und im Folgejahr, etwa „Irak −20,0 % → +15,0 %“. Der Weg haelt mit geschuetzten Leerzeichen zusammen.
private fun rangWeg(land: String, r: Imf.Rang): String =
    if (r.von != null && r.bis != null) land + " " + Fmt.signedPct(r.von, 1) + " → " + Fmt.signedPct(r.bis, 1)
    else land + " " + Fmt.signedPct(r.wert, 1)

@Composable
private fun RangZeile(titel: String, eintraege: List<String>, farbe: Color) {
    Column {
        Text(titel, style = MaterialTheme.typography.labelLarge, color = farbe)
        Text(eintraege.joinToString(PUNKT), style = MaterialTheme.typography.bodyMedium)
    }
}

// Kleine Linie des Weltwachstums ueber die Jahre, mit Anfangs- und Endjahr.
@Composable
private fun Pfadlinie(werte: List<Double>, jahr: Int, modifier: Modifier, farbe: Color) {
    val text = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val lo = werte.min(); val hi = werte.max()
            val spanne = (hi - lo).coerceAtLeast(0.5)
            val pad = 4f
            fun pos(i: Int) = Offset(pad + (size.width - 2 * pad) * i / (werte.size - 1), pad + (size.height - 2 * pad) * (1f - ((werte[i] - lo) / spanne).toFloat()))
            val p = Path()
            werte.indices.forEach { i -> val o = pos(i); if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
            drawPath(p, farbe, style = Stroke(width = 2f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
            werte.indices.forEach { i -> drawCircle(farbe, 2.5f * density, pos(i)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$jahr", style = MaterialTheme.typography.labelSmall, color = text, maxLines = 1, softWrap = false)
            Text("${jahr + werte.size - 1}", style = MaterialTheme.typography.labelSmall, color = text, maxLines = 1, softWrap = false)
        }
    }
}
