package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.engine.Abhaengigkeiten
import de.troetenrepublik.app.engine.Abhaengigkeiten.Abschnitt
import de.troetenrepublik.app.engine.Abhaengigkeiten.Zeile
import de.troetenrepublik.app.engine.Abhaengigkeiten.Zeilentyp
import de.troetenrepublik.app.engine.Einflussverzeichnis
import de.troetenrepublik.app.engine.Einflussverzeichnis.Wertbild
import de.troetenrepublik.app.engine.Empfindlichkeit
import de.troetenrepublik.app.engine.Kernaussagen
import de.troetenrepublik.app.engine.Rohstoffmengen
import de.troetenrepublik.app.engine.Rohstoffprofil
import de.troetenrepublik.app.engine.LandRisikoBausteine
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.abhaengigkeitenEingabe
import de.troetenrepublik.app.etfPositionen
import de.troetenrepublik.app.firmenbericht
import de.troetenrepublik.app.konzernmutter
import de.troetenrepublik.app.market.Firmenberichte
import de.troetenrepublik.app.market.SecBericht
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.landNamen
import de.troetenrepublik.app.wertHerkunft
import de.troetenrepublik.app.wertbildVon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

// [Grosses Ganzes 0.73, 0.76] Karte "Abhaengigkeiten und Risiken" auf der Wertpapierseite. Zu beim Oeffnen. Aufgeklappt
// steht oben eine Zusammenfassung fuer genau dieses Unternehmen (Kernaussagen), darunter die Unterabschnitte: erst, was die
// Firma selbst meldet oder was am Kurs gemessen ist (Wovon sie lebt, Land und Sitz, Kursempfindlichkeit), dann die
// Ableitungen aus der Branche (Rohstoffe, Einfluesse, Politik). Details klappen auf der Seite auf; ein Weg zu anderen
// Seiten bleibt als eine leise Zeile am Ende. Die Art jeder Angabe (gemeldet, gemessen, abgeleitet) zeigt eine kleine
// Marke, erklaert wird sie einmal in der Legende. Aktien zeigen alle Abschnitte, ETFs die Einfluesse ihrer groessten
// Positionen, Rohstoffe am Terminmarkt Foerderlaender und Einfluesse.

private const val SICHTBAR = 4

// Reihenfolge der Unterabschnitte einer Aktie: Eigenes vor den Ableitungen aus der Branche
private val ABSCHNITTE_AKTIE = listOf(
    Abschnitt.HERKUNFT, Abschnitt.EMPFINDLICHKEIT, Abschnitt.ROHSTOFFE, Abschnitt.EINFLUESSE, Abschnitt.POLITIK, Abschnitt.LUECKEN,
)

// Aus dem Wertbild, was ohne Abrufe feststeht (Index und Katalog): Zeilen fuer die Zusammenfassung unter der Ueberschrift
private fun zusammenfassung(z: List<Zeile>, mitLand: Boolean, mitEmpf: Boolean): String = S.abhZusammen(
    mitLand, z.count { it.typ == Zeilentyp.ROHSTOFF },
    z.count { it.typ == Zeilentyp.GROESSE && it.abschnitt == Abschnitt.EINFLUESSE },
    z.any { it.abschnitt == Abschnitt.POLITIK }, mitEmpf,
)

// Geladene Daten der Karte, ueber dem Listeneintrag gehalten (siehe AbhaengigkeitenKarte)
internal class AbhaengigkeitenZustand {
    var wert by mutableStateOf<Wertbild?>(null)
    var herkunft by mutableStateOf<de.troetenrepublik.app.engine.Taetigkeit.Herkunft?>(null)
    var eingabe by mutableStateOf<Abhaengigkeiten.Eingabe?>(null)
    var etf by mutableStateOf<Pair<List<Pair<Wertbild, Double>>, Double>?>(null)
    var namen by mutableStateOf<Map<String, Pair<String, String>>>(emptyMap())
    var bericht by mutableStateOf<Firmenberichte.Ergebnis?>(null)
    var mutter by mutableStateOf<Abhaengigkeiten.Konzernmutter?>(null)
}

// Lader der Karte: holt die Daten, rechnet die Zeilen einmal (Ergebnis im Zustand und im Merker der Seite) und gibt sie als
// Modell an die LazyColumn der Seite (abhaengigkeitenAbschnitt): Kopf, aufgeklappt je Zeile ein Eintrag. Zeichnet selbst nichts.
@Composable
internal fun AbhaengigkeitenKarte(
    state: AppState, symbol: String, art: AssetType, profile: MarketApi.Profile?, usWert: Boolean,
    merker: SeitenMerker,
    zustand: AbhaengigkeitenZustand = remember(symbol) { AbhaengigkeitenZustand() },
    // Liegt der Wert im Depot: Verweis zum Depot-Werkzeug „Was waere, wenn“
    onWasWaere: (() -> Unit)? = null,
    // Routen der App: Gesamtbild und Landrisiko fuehren aufgeklappt zum Laenderblatt
    oeffnen: ((String) -> Unit)? = null,
) {
    if (art != AssetType.STOCK && art != AssetType.ETF && art != AssetType.COMMODITY) return
    val index = remember { Einflussverzeichnis.geladen }
    val heute = remember { LocalDate.now().toEpochDay() }
    val offenZustand = merker.zustand("abh/offen") { false }
    val offen = offenZustand.value
    // Geladene Daten liegen im Zustand der Seite, nicht im Eintrag der Liste: Wird der Eintrag beim Wegscrollen verworfen,
    // kommt er mit denselben Daten und derselben Hoehe zurueck, und die Seite springt nicht.
    var wert by zustand::wert
    var herkunft by zustand::herkunft
    var eingabe by zustand::eingabe
    var etf by zustand::etf
    var namen by zustand::namen
    var bericht by zustand::bericht
    var mutter by zustand::mutter

    // [0.76] Die Zuordnung der Rohstoffe arbeitet mit der Beschreibung, die die Seite unter "Informationen" zeigt: Yahoo und
    // Wikipedia (englisch und deutsch)
    val infos by state.companies.collectAsState()
    val zusatz = infos[symbol]?.let { state.beschreibungenFuer(symbol) }.orEmpty()

    // Stufe 1: Sitz und Branche; leicht, damit die Ueberschrift sofort eine Zusammenfassung zeigt
    LaunchedEffect(symbol, profile, zusatz) {
        namen = state.landNamen()
        when (art) {
            AssetType.STOCK -> {
                val h = state.wertHerkunft(symbol, profile)
                herkunft = h
                val neu = wertbildVon(symbol, h, profile, zusatz)
                wert = neu
                // Hat sich nur die Beschreibung geaendert, rechnet die Karte mit dem neuen Wertbild weiter
                eingabe = eingabe?.copy(wert = neu)
            }
            AssetType.ETF -> { etf = withContext(Dispatchers.Default) { state.etfPositionen(symbol) } }
            else -> wert = Wertbild(symbol, null, null)
        }
    }
    // Stufe 2: erst beim Aufklappen die Abrufe fuer Land, Staende und Kursempfindlichkeit
    LaunchedEffect(wert, offen) {
        val w = wert ?: return@LaunchedEffect
        if (offen && eingabe == null) eingabe = state.abhaengigkeitenEingabe(w, herkunft, usWert)
    }

    // Angaben der Firma zur Gliederung des Umsatzes (Jahresbericht, Konzernmutter bei GLEIF): erst beim Aufklappen, Stand vom
    // Geraet. Weitere Quellen fuer Segmente liefern ihr Ergebnis ueber state.firmenbericht in derselben Form.
    LaunchedEffect(symbol, offen) {
        if (!offen || art != AssetType.STOCK) return@LaunchedEffect
        val name = Symbols.find(symbol)?.name ?: profile?.longName?.ifBlank { null } ?: symbol
        if (bericht == null || bericht?.status == SecBericht.Status.FEHLER) bericht = state.firmenbericht(symbol, name)
        if (mutter == null) mutter = state.konzernmutter(symbol)?.let { m ->
            Abhaengigkeiten.Konzernmutter(m.name, m.land, LocalDate.now().toEpochDay())
        }
    }

    // [0.76] Angaben der SEC zu Rohstoffen (Form SD, Rohstoffabschnitt), sobald der Bericht geladen ist
    val secRohstoffe = bericht?.takeIf { it.sd.isNotBlank() || it.roh.isNotEmpty() }?.let { Rohstoffprofil.SecAngaben(it.sd, it.roh) }
    val zeilen: List<Zeile> = merker.gerechnet("abh/zeilen", listOf(art, wert, eingabe, etf, herkunft, mutter, secRohstoffe)) {
        when (art) {
            AssetType.STOCK -> wert?.let {
                Abhaengigkeiten.karte(index, (eingabe ?: Abhaengigkeiten.Eingabe(it, taetigkeit = herkunft?.taetig, usWert = usWert, empfindlichkeitZuKurz = false)).copy(konzernmutter = mutter, sec = secRohstoffe), heute)
            }
            AssetType.ETF -> etf?.let { (p, erfasst) -> Abhaengigkeiten.etfKarte(index, p, erfasst, heute) }
            else -> wert?.let { Abhaengigkeiten.rohstoffKarte(index, eingabe ?: Abhaengigkeiten.Eingabe(it), heute) }
        }.orEmpty()
    }
    // Ohne Daten (ETF ohne Bestand) bleibt die Karte weg
    if (zeilen.none { it.typ == Zeilentyp.GROESSE || it.typ == Zeilentyp.ROHSTOFF || it.abschnitt == Abschnitt.HERKUNFT || it.abschnitt == Abschnitt.EMPFINDLICHKEIT } && art != AssetType.STOCK) {
        ModellMelden(merker, "abh", null)
        return
    }
    if (art == AssetType.STOCK && wert == null) {
        ModellMelden(merker, "abh", null)
        return
    }

    val aktie = art == AssetType.STOCK
    val unter = if (aktie) zusammenfassung(zeilen, wert?.land != null, true) else zusammenfassung(zeilen, false, false)
    val kopf: @Composable () -> Unit = { AufklappKopf(S.abhTitel, unter, offen) { offenZustand.value = !offenZustand.value } }
    if (!offen) {
        ModellMelden(merker, "abh", ListenModell(titel = "", gross = true, kern = null, zeilen = emptyList(), kopf = kopf, immerOffen = true))
        return
    }

    val gliederung = bericht?.takeIf { it.status == SecBericht.Status.OK }?.bericht
    val saetze = if (aktie) merker.gerechnet("abh/saetze", listOf(zeilen, gliederung, namen)) { Kernaussagen.ableiten(index, zeilen, gliederung).map { kernText(it, index, namen) } } else emptyList()
    val bericht0 = bericht
    val namen0 = namen
    val reihenfolge = if (aktie) ABSCHNITTE_AKTIE else Abschnitt.entries
    val rows = ArrayList<ZeilenDaten>()
    if (aktie) {
        rows.add(ZeilenDaten("kern") { KernBlock(saetze) })
        rows.add(ZeilenDaten("legende") { LegendeZeile() })
        rows.add(ZeilenDaten("wovon") { WovonSieLebt(bericht0, namen0) })
    } else rows.add(ZeilenDaten("legende") { LegendeZeile() })
    // [0.77] Erklaer-Assistent: die Aussagen der Karte und die Namen der angezeigten Zeilen
    val titelName = Symbols.find(symbol)?.name ?: profile?.longName?.ifBlank { null } ?: symbol
    rows.add(ZeilenDaten("erklaeren") {
        ErklaerKnopf(state, {
            de.troetenrepublik.app.engine.Erklaerer.kontextAbhaengigkeiten(
                titelName, symbol, saetze,
                reihenfolge.mapNotNull { a ->
                    val z = zeilen.filter { it.abschnitt == a }
                    if (z.isEmpty() || a == Abschnitt.LUECKEN) null else abschnittTitel(a, aktie, z) to z.mapNotNull { zl -> zeilenKurz(zl, index, namen0) }
                },
            )
        })
    })
    for (a in reihenfolge) {
        val z = zeilen.filter { it.abschnitt == a }
        if (z.isEmpty()) continue
        if (a == Abschnitt.LUECKEN) {
            z.forEachIndexed { i, l -> rows.add(ZeilenDaten("luecke-$i") { Luecke(l) }) }
            continue
        }
        val titel = abschnittTitel(a, aktie, z)
        val zusatzText = abschnittZusatz(a, z)
        rows.add(ZeilenDaten("kopf-${a.name}") { AbschnittKopf(titel, zusatzText) })
        val alleZustand = merker.zustand("abh/alle/${a.name}") { false }
        val sichtbar = if (alleZustand.value) z else z.take(SICHTBAR)
        sichtbar.forEachIndexed { i, zl -> rows.add(ZeilenDaten("z-${a.name}-$i") { Zeilenanzeige(zl, index, namen0, offenStart = art == AssetType.COMMODITY, oeffnen = oeffnen) }) }
        if (z.size > SICHTBAR) rows.add(ZeilenDaten("mehr-${a.name}") { ListenPfeil(alleZustand.value, label = if (alleZustand.value) S.less else S.bbAlle(z.size)) { alleZustand.value = !alleZustand.value } })
    }
    if (zeilen.any { it.typ == Zeilentyp.ROHSTOFF }) {
        val usgsJahr = if (index.rohstoffQuelle.contains("2026")) "2026" else "2025"
        QuellenMelden(QuelleLink("U.S. Geological Survey, Mineral Commodity Summaries $usgsJahr", "https://pubs.usgs.gov/periodicals/mcs$usgsJahr/mcs$usgsJahr.pdf"))
        // Foerdermengen je Land und Kapazitaet der Bergwerke (gemeinfrei, CC0)
        val mengenQuellen = Rohstoffmengen.mengenQuellen(zeilen.filter { it.typ == Zeilentyp.ROHSTOFF }.mapNotNull { index.rohstoffe[it.schluessel] })
        if (Rohstoffmengen.QUELLE_MYB in mengenQuellen || zeilen.any { it.anlage != null }) {
            QuellenMelden(QuelleLink("U.S. Geological Survey, Minerals Yearbook 2024, Volume III (CC0)", "https://doi.org/10.5066/P1KEQASH"))
        }
        // Jahresmittel der Preise aus dem Pink Sheet der Weltbank (CC BY 4.0)
        if (zeilen.filter { it.typ == Zeilentyp.ROHSTOFF }.any { index.rohstoffe[it.schluessel]?.mengen?.preisQuelle == "wb" }) {
            QuellenMelden(QuelleLink("World Bank, Commodity Price Data (The Pink Sheet)", "https://www.worldbank.org/en/research/commodity-markets"))
        }
        // Rohstoffe mit eigener Quelle der Foerderanteile (Uran)
        zeilen.filter { it.typ == Zeilentyp.ROHSTOFF }.mapNotNull { index.rohstoffe[it.schluessel] }.filter { it.quelle != null && it.quelleUrl != null }
            .distinctBy { it.quelle }.forEach { QuellenMelden(QuelleLink(it.quelle.orEmpty(), it.quelleUrl.orEmpty())) }
    }
    if (onWasWaere != null) rows.add(ZeilenDaten("was-waere") { NavRow(S.ptTitle("SCENARIO"), S.abhWasWaereDepot, onClick = onWasWaere) })
    ModellMelden(merker, "abh", ListenModell(titel = "", gross = true, kern = null, zeilen = rows, kopf = kopf, immerOffen = true))
}

// Die Karte als Eintraege der LazyColumn: Kopf, aufgeklappt Kernaussagen, Legende und je Zeile ein Eintrag.
internal fun LazyListScope.abhaengigkeitenAbschnitt(merker: SeitenMerker) {
    listenAbschnitt(merker, "abh")
}

// Titel eines Unterabschnitts; die Ableitungen einer Aktie aus der Branche tragen es im Titel
// [0.76] Rohstoffe heissen "des Unternehmens", sobald eine Zeile aus Angaben zum Unternehmen stammt, sonst "der Branche"
private fun abschnittTitel(a: Abschnitt, aktie: Boolean, z: List<Zeile>): String = S.abhAbschnitt(
    when {
        aktie && a == Abschnitt.EINFLUESSE -> "EINFLUESSE_BRANCHE"
        a == Abschnitt.ROHSTOFFE && z.any { it.rohstoff?.firmenbezogen == true } -> "ROHSTOFFE_FIRMA"
        else -> a.name
    },
)

// Kleine Zeile unter dem Titel: Stand, bei der Kursempfindlichkeit zuerst R² und Zeitraum (einmal fuer alle Zeilen)
private fun abschnittZusatz(a: Abschnitt, z: List<Zeile>): String? {
    val stand = z.mapNotNull { it.stand }.filter { it > 0 }.minOrNull()?.let { S.abhStand(monatJahr(it)) }
    val empf = if (a == Abschnitt.EMPFINDLICHKEIT) z.firstOrNull { it.zahl2 != null && it.ganz != null }
        ?.let { S.abhEmpfHinweis(Fmt.num(it.zahl2 ?: 0.0, 2), it.ganz ?: 0) } else null
    return listOfNotNull(empf, stand).joinToString(PUNKT).ifEmpty { null }
}

// Kurzform einer Zeile fuer den Erklaer-Assistenten: Name und, wo die Karte sie zeigt, die Zahl
private fun zeilenKurz(z: Zeile, index: Einflussverzeichnis.Index, namen: Map<String, Pair<String, String>>): String? = when (z.typ) {
    Zeilentyp.SITZ, Zeilentyp.TAETIGKEIT, Zeilentyp.MUTTER -> landName(namen, z.schluessel)
    Zeilentyp.GESAMTBILD, Zeilentyp.LANDRISIKO -> z.zahl?.let { S.abhVon100(it.roundToInt().toString()) }
    Zeilentyp.ROHSTOFF -> rohstoffName(index, z.schluessel)
    Zeilentyp.GROESSE -> groesseName(index, z.schluessel)
    Zeilentyp.EMPF_FAKTOR, Zeilentyp.EMPF_MARKT -> z.aussage?.let { a -> S.abhFaktor(z.schluessel) + " " + (z.zahl?.let { aussageText(a, it) } ?: S.abhAussage(a.name)) }
    else -> null
}

// Zahl mit Vorzeichen fuer Steigungen
private fun steigung(v: Double): String {
    val digits = if (abs(v) < 0.1) 2 else 1
    return (if (v >= 0) "+" else "−") + Fmt.num(abs(v), digits)
}

// Menge mit Einheit: ab einer Million in Kurzform (22,98 Mio. t), sonst ausgeschrieben
internal fun mengeText(m: Double, einheit: String): String = (if (m >= 1e6) Fmt.compact(m) else Fmt.num(m, 0)) + " " + S.abhRohEinheit(einheit)

private fun rohstoffName(index: Einflussverzeichnis.Index, id: String): String = index.rohstoffe[id]?.name(istDeutsch()) ?: id

// [0.77] Ein Name in einer Aufzaehlung bricht nicht in sich um ("Nachfrage aus China", "Zinsen (10 Jahre)")
private fun fest(s: String): String = s.replace(' ', '\u00A0')

// Satz zu einer Kernaussage in der App-Sprache
private fun kernText(a: Kernaussagen.Aussage, index: Einflussverzeichnis.Index, namen: Map<String, Pair<String, String>>): String = when (a.typ) {
    Kernaussagen.Typ.SEGMENT -> S.abhKernSegment(postenName(a.posten!!, namen, false), anteilText(a.zahl))
    Kernaussagen.Typ.REGION -> S.abhKernRegion(postenName(a.posten!!, namen, true), anteilText(a.zahl))
    Kernaussagen.Typ.KUNDE -> S.abhKernKunde(anteilText(a.zahl))
    Kernaussagen.Typ.KURS -> S.abhKernKurs(
        a.liste.joinToString(", ") { p ->
            val v = p.zahl ?: 0.0
            if (v >= 0) S.abhKernMit(S.abhFaktor(p.schluessel), steigung(v)) else S.abhKernGegen(S.abhFaktor(p.schluessel), steigung(v))
        },
    )
    Kernaussagen.Typ.FOERDERT -> a.liste.joinToString(", ") { fest(rohstoffName(index, it.schluessel)) }.let { if (a.zahl >= 2.0) S.abhKernLizenz(it) else if (a.zahl >= 1.0) S.abhKernFoerdert(it) else S.abhKernErzeugt(it) }
    Kernaussagen.Typ.ROHSTOFFE -> {
        val liste = a.liste.joinToString(", ") { fest(rohstoffName(index, it.schluessel) + " " + Fmt.pct(it.zahl ?: 0.0, 0)) }
        if (a.firma) S.abhKernRohstoffeFirma(landKurz(landName(namen, a.land)), liste) else S.abhKernRohstoffe(landKurz(landName(namen, a.land)), liste)
    }
    Kernaussagen.Typ.KRITISCH -> if (a.firma) S.abhKernKritischFirma(a.zahl.toInt()) else S.abhKernKritisch(a.zahl.toInt())
    Kernaussagen.Typ.LAND -> S.abhKernLand(
        a.sitz.takeIf { it.isNotEmpty() }?.let { landName(namen, it) }.orEmpty(), landName(namen, a.land), S.abhStufe((a.stufe ?: Kernaussagen.Stufe.MITTEL).name),
    )
    Kernaussagen.Typ.AUFFAELLIG -> S.abhKernAuffaellig(a.liste.joinToString(", ") { fest(groesseName(index, it.schluessel)) })
    Kernaussagen.Typ.EINFLUESSE -> S.abhKernEinfluesse(a.liste.joinToString(", ") { fest(groesseName(index, it.schluessel)) })
}

// Farbe eines Balkens nach Punkten (hoch ist gut) bzw. nach Anteil an der Foerderung (hoch ist ein Klumpen)
@Composable
private fun punkteFarbe(p: Double): Color = when (Kernaussagen.stufe(p)) {
    Kernaussagen.Stufe.NIEDRIG -> LocalSignal.current.up
    Kernaussagen.Stufe.MITTEL -> LocalSignal.current.warn
    Kernaussagen.Stufe.HOCH -> LocalSignal.current.down
}

@Composable
private fun klumpenFarbe(prozent: Int): Color = when {
    prozent >= 70 -> LocalSignal.current.down
    prozent >= 50 -> LocalSignal.current.warn
    else -> MaterialTheme.colorScheme.primary
}

// Wort und Steigung eines gemessenen Zusammenhangs ("läuft mit +0,4"); ohne Beleg nur das Wort
private fun aussageText(a: Empfindlichkeit.Aussage, v: Double): String = when (a) {
    Empfindlichkeit.Aussage.LAEUFT_MIT, Empfindlichkeit.Aussage.LAEUFT_GEGEN -> S.abhAussage(a.name) + " " + steigung(v)
    else -> S.abhAussage(a.name)
}

// Luecken stehen als leise Zeilen am Ende, ohne eigenen Unterabschnitt
@Composable
private fun Luecke(z: Zeile) {
    when (z.typ) {
        Zeilentyp.LUECKE_SEGMENTE -> LeiseZeile(S.abhEtfErfasst + ": " + Fmt.pct((z.zahl ?: 0.0) * 100, 0))
        Zeilentyp.LUECKE_ABGELAUFEN -> {
            LeiseZeile(S.abhAbgelaufen + ": " + (z.ganz ?: 0))
            LeiseZeile(S.abhAbgelaufenHinweis(Fmt.date((z.zahl ?: 0.0).toLong() * 86_400_000L)))
        }
        else -> {}
    }
}

// Eine Zeile der Karte: Bezeichnung, Wert, wo sinnvoll ein Balken; Einzelheiten klappen darunter auf
@Composable
private fun Zeilenanzeige(
    z: Zeile, index: Einflussverzeichnis.Index, namen: Map<String, Pair<String, String>>, offenStart: Boolean, oeffnen: ((String) -> Unit)? = null,
) {
    // Verweiszeile zum Laenderblatt des Landes (Gesamtbild und Landrisiko rechnen verschieden, das Blatt zeigt beides im Einzelnen)
    val zumLand: (@Composable () -> Unit)? = z.ziel?.takeIf { oeffnen != null && it.startsWith("land/") }?.let { ziel ->
        { VerweisText(landName(namen, z.schluessel), Modifier.padding(top = 2.dp)) { oeffnen?.invoke(ziel) } }
    }
    when (z.typ) {
        Zeilentyp.SITZ -> KartenZeile(S.abhSitz, landName(namen, z.schluessel), z.art)
        Zeilentyp.TAETIGKEIT -> KartenZeile(S.abhTaetigkeit, landName(namen, z.schluessel), z.art)
        Zeilentyp.MUTTER -> KartenZeile(S.abhMutter, landName(namen, z.schluessel), z.art, unter = z.liste.firstOrNull())
        Zeilentyp.GESAMTBILD -> {
            val p = z.zahl ?: 0.0
            KartenZeile(
                S.abhGesamtbild, S.abhVon100(p.roundToInt().toString()), z.art, anteil = (p / 100.0).toFloat(), balkenFarbe = punkteFarbe(p),
                details = if (z.ganz == null && zumLand == null) null else {
                    {
                        z.ganz?.let { jahr -> DetailText(jahr.toString()) }
                        zumLand?.invoke()
                    }
                },
            )
        }
        Zeilentyp.LANDRISIKO -> {
            val p = z.zahl ?: 0.0
            val stufe = S.abhStufe(Kernaussagen.stufe(p).name)
            val bausteine = z.fakten?.let { LandRisikoBausteine.zerlegen(it) }.orEmpty()
            KartenZeile(
                S.abhLandrisiko, S.abhVon100(p.roundToInt().toString()), z.art, unter = landName(namen, z.schluessel) + PUNKT + stufe,
                anteil = (p / 100.0).toFloat(), balkenFarbe = punkteFarbe(p),
                details = if (bausteine.isEmpty() && zumLand == null) null else {
                    {
                        bausteine.forEach { b ->
                            DetailZeile(S.abhBaustein(b.key), (if (b.wert < 0) "−" else "") + Fmt.num(abs(b.wert), 0))
                        }
                        zumLand?.invoke()
                    }
                },
            )
        }
        Zeilentyp.KATASTROPHEN -> KartenZeile(S.abhKatastrophen, S.abhKatastrophenWert(z.ganz ?: 0, (z.zahl ?: 0.0).toInt()), z.art)
        Zeilentyp.ROHSTOFF -> Rohstoffzeile(z, index, namen, offenStart)
        Zeilentyp.GROESSE -> Groessenzeile(z, index, namen)
        Zeilentyp.EMPF_MARKT, Zeilentyp.EMPF_FAKTOR -> {
            val a = z.aussage ?: Empfindlichkeit.Aussage.KEIN_BELEG
            KartenZeile(S.abhFaktor(z.schluessel), aussageText(a, z.zahl ?: 0.0), z.art)
        }
        Zeilentyp.EMPF_OHNE -> KartenZeile(S.abhEmpfOhne, S.abhKeinBeleg, z.art, unter = z.liste.joinToString(", ") { fest(S.abhFaktor(it)) })
        Zeilentyp.EMPF_ZU_KURZ -> LeiseZeile(S.abhEmpfZuKurz)
        Zeilentyp.LUECKE_SEGMENTE, Zeilentyp.LUECKE_ABGELAUFEN -> Luecke(z)
    }
}

// Ein Rohstoff der Branche: ein Balken mit dem groessten Foerderland und seinem Anteil. Aufgeklappt stehen die weiteren
// Foerderlaender, die Konzentration, ob der Rohstoff in der EU kritisch ist, und was am Kurs gemessen ist.
@Composable
private fun Rohstoffzeile(z: Zeile, index: Einflussverzeichnis.Index, namen: Map<String, Pair<String, String>>, offenStart: Boolean) {
    val r = index.rohstoffe[z.schluessel]
    val laender = z.liste.mapNotNull { s ->
        val teile = s.split(':')
        val p = teile.getOrNull(1)?.toIntOrNull() ?: 0
        if (p > 0) teile[0] to p else null
    }
    val spitze = laender.firstOrNull()
    val name = r?.name(istDeutsch()) ?: z.schluessel
    // [0.76] Zweite Zeile: Verwendung beim Unternehmen und Weg
    // [0.77] Die Herkunft der Zuordnung steht aufgeklappt, damit die Zeile kurz bleibt; ohne Verwendung und Weg steht sie hier.
    // Eigene Suche oder Foerderung ist kein Verarbeitungsweg. Jeder Teil bleibt zusammen, der Umbruch faellt an den Trennpunkt.
    val t = z.rohstoff
    val herkunft = t?.takeIf { it.rolle != Rohstoffprofil.Rolle.GENANNT }?.let { S.abhRohQuelle(it.quelle.name) }
    val teile = t?.let {
        listOfNotNull(
            it.verwendung?.let { v -> S.abhVerwendung(v).let { x -> if (!it.firmenbezogen) S.abhBrancheUeblich(x) else x } },
            it.weg?.takeIf { _ -> !it.selbst && it.firmenbezogen }?.let { w -> S.abhWeg(w.name) },
            if (it.rolle == Rohstoffprofil.Rolle.GENANNT) S.abhRohGenannt else null,
        )
    }.orEmpty()
    val herkunftUnten = teile.isNotEmpty() && t?.quelle != Rohstoffprofil.Quelle.SEC && !(t?.firmenbezogen == false && t.verwendung != null)
    val zuordnung = (if (teile.isEmpty()) listOfNotNull(herkunft) else teile).joinToString(PUNKT) { fest(it) }.ifEmpty { null }
    // [0.77] Ist der Rohstoff ein eigenes Erzeugnis oder nur in der Beschreibung genannt, steht kein Foerderland als Klumpen
    // daneben; aufgeklappt bleiben die Foerderlaender zu sehen
    val selbst = t != null && !t.einsatz
    KartenZeile(
        label = name,
        wert = if (selbst) "" else spitze?.let { landKurz(landName(namen, it.first)) + "\u00A0" + Fmt.pct(it.second.toDouble(), 0) }.orEmpty(),
        art = z.art,
        unter = listOfNotNull(zuordnung, if (spitze == null && !selbst) S.abhAnteileOffen else null).joinToString(PUNKT).ifEmpty { null },
        anteil = if (selbst) null else spitze?.let { it.second / 100f },
        balkenFarbe = spitze?.let { klumpenFarbe(it.second) } ?: MaterialTheme.colorScheme.primary,
        grau = z.grau,
        startOffen = offenStart,
        details = {
            val auf = r?.let { Rohstoffmengen.aufschluesseln(it) }
            if (auf != null) {
                DetailZeile(S.abhRohWelt(auf.jahr), mengeText(auf.welt, auf.einheit))
                auf.weltWert?.let { DetailZeile(S.abhRohWert(auf.preisJahr), Fmt.dollarKompakt(it)) }
                z.firmaMenge?.let { f ->
                    DetailZeile(
                        S.abhRohFirma(f.jahr),
                        listOfNotNull(mengeText(f.menge, auf.einheit), Rohstoffmengen.wert(f.menge, r?.mengen?.preis)?.let { Fmt.dollarKompakt(it) }).joinToString(PUNKT),
                    )
                }
                MengenTabelle(
                    MengenZeile(S.abhRohFoerderland, S.abhRohMenge, S.abhRohAnteil, if (auf.weltWert != null) S.abhRohWertKopf else null),
                    auf.zeilen.map { MengenZeile(it.land?.let { c -> landKurz(landName(namen, c)) } ?: S.abhRohUebrige, mengeText(it.menge, auf.einheit), prozentText(it.anteil), it.wert?.let { w -> Fmt.dollarKompakt(w) }) },
                )
                // Kapazitaet der Bergwerke des Unternehmens bei diesem Rohstoff, gesamt und je Land
                r?.let { Rohstoffmengen.kapazitaet(it, z.anlage) }?.let { kap ->
                    DetailZeile(S.abhRohKapazitaet(kap.jahr), mengeText(kap.gesamt, kap.einheit))
                    if (kap.luecken) DetailZeile(S.abhRohAnlagen, S.abhRohAnlagenWert(kap.verwertbar, kap.anlagen))
                    MengenTabelle(
                        MengenZeile(S.abhRohLand, S.abhRohKapKopf, S.abhRohAnteil, null),
                        kap.zeilen.map { MengenZeile(landKurz(landName(namen, it.land)), mengeText(it.menge, kap.einheit), prozentText(it.anteil), null) },
                    )
                }
            } else laender.forEach { (c, p) -> LandBalken(landKurz(landName(namen, c)), p) }
            z.zahl?.let { DetailText(S.abhTop3(Fmt.pct(it, 0))) }
            if (z.ganz == 1) DetailText(S.abhKritisch)
            if (herkunftUnten && herkunft != null) DetailZeile(S.abhRohZuordnung, herkunft)
            if (t != null && (t.sec and Rohstoffprofil.SEC_SD) != 0) DetailText(S.abhRohSd)
            if (t != null && (t.sec and Rohstoffprofil.SEC_BERICHT) != 0) DetailText(S.abhRohBericht)
            z.messung?.let { DetailText(S.abhMessung(S.abhFaktor(it.faktor.name) + " " + aussageText(it.aussage, it.wert))) }
        },
    )
}

// [0.77] Gebiet, auf das eine Branchenverbindung beschraenkt ist (EU oder ein Land); null: ueberall
private fun gebietText(im: Set<String>?, namen: Map<String, Pair<String, String>>): String? = when {
    im == null -> null
    im == Einflussverzeichnis.EU -> "EU"
    im.size == 1 -> landName(namen, im.first())
    else -> null
}

// [0.77] Beleg ohne den Namen der Datenquelle am Ende
private fun belegOhneQuelle(beleg: String): String = beleg.replace(Regex(",? (IWF|IMF) PortWatch$"), "")

// [0.77] Absaetze unter dem Namen einer Einflussgroesse: der Weg, ueber den sie diese Firma trifft (mit Richtung, wo sie belegt
// ist), bei Programmen zuerst, was das Programm regelt
private fun wegAbsaetze(z: Zeile, g: Einflussverzeichnis.Groesse?, kette: String?, namen: Map<String, Pair<String, String>>): List<String> {
    val w = z.weg
    val programm = g?.typ == Einflussverzeichnis.Typ.PROGRAMM
    val text = when {
        w == null -> null
        programm && w.ziel.art == Einflussverzeichnis.ZielArt.BRANCHE -> S.abhGiltFuer(S.industryName(w.ziel.schluessel), gebietText(w.im, namen))
        else -> belegOhneQuelle(w.beleg(istDeutsch()))
    }
    val zeile = when {
        z.richtung != 0 -> S.abhAnstieg(z.richtung) + (text?.let { ": $it" } ?: "")
        text != null -> S.abhWegPraefix + ": " + text
        else -> null
    }
    return listOfNotNull(if (programm) kette else null, zeile)
}

// Eine Einflussgroesse: Name und Wirkung auf die Firma (stuetzt, belastet), darunter der Weg und, wo Zahlen vorliegen, die
// Staerke: am Kurs der Firma gemessen, sonst fuer die Route oder das Gebiet (so benannt); aufgeklappt, was die Groesse ist
@Composable
private fun Groessenzeile(z: Zeile, index: Einflussverzeichnis.Index, namen: Map<String, Pair<String, String>>) {
    val g = index.groessen[z.schluessel]
    val name = g?.let { groesseName(it) } ?: z.schluessel
    val kette = g?.kette(istDeutsch())?.takeIf { it.isNotBlank() }
    val messung = z.messung?.let { S.abhMessung(S.abhFaktor(it.faktor.name) + " " + aussageText(it.aussage, it.wert)) }
    val absaetze = wegAbsaetze(z, g, kette, namen)
    // Der Weg und die Messung stehen aufgeklappt; unter dem Namen bleibt nur eine kurze Zeile
    val keteOffen = kette?.takeIf { g?.typ != Einflussverzeichnis.Typ.PROGRAMM }
    val weg: (@Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit)? =
        if (absaetze.isEmpty() && keteOffen == null) null else { { absaetze.forEach { DetailText(it) }; keteOffen?.let { DetailText(it) } } }
    when {
        z.zahl2 != null -> KartenZeile(
            name, Fmt.pct(z.zahl2 * 100, 0), z.art, unter = S.abhEtfBetrifft(z.ganz ?: 0), anteil = z.zahl2.toFloat(), balkenFarbe = AnteilsFarben.folge(0), grau = z.grau,
            details = kette?.let { k -> { DetailText(k) } },
        )
        g?.typ == Einflussverzeichnis.Typ.ENGPASS && z.zahl != null -> KartenZeile(
            name, Fmt.signedPct(z.zahl, 0), z.art, unter = S.abhStaerkeRoute,
            anteil = (abs(z.zahl) / 100.0).toFloat(), grau = z.grau,
            details = weg,
        )
        g?.typ == Einflussverzeichnis.Typ.SPANNUNG && z.zahl != null -> KartenZeile(
            name, Fmt.num(z.zahl, 1) + "×", z.art, unter = S.abhStaerkeGebiet, grau = z.grau,
            details = weg,
        )
        else -> KartenZeile(
            name, if (z.richtung != 0) S.abhRichtung(z.richtung) else "", z.art,
            grau = z.grau,
            details = if (weg == null && messung == null) null else { { messung?.let { DetailText(it) }; weg?.invoke(this) } },
        )
    }
}
