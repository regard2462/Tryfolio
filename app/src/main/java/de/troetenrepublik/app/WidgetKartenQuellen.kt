package de.troetenrepublik.app

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.engine.DepotCheck
import de.troetenrepublik.app.engine.Kategorien
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.ui.classOf
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.Side
import de.troetenrepublik.app.data.Valuation
import de.troetenrepublik.app.data.WidgetKarte
import de.troetenrepublik.app.data.WidgetKarten
import de.troetenrepublik.app.data.WidgetKartenBau
import de.troetenrepublik.app.data.WidgetVorgabe
import de.troetenrepublik.app.data.WidgetZeile
import de.troetenrepublik.app.data.WidgetZiel
import de.troetenrepublik.app.engine.Aktienfinder
import de.troetenrepublik.app.engine.Ausblickgesamt
import de.troetenrepublik.app.engine.Durchbrueche
import de.troetenrepublik.app.engine.Fruehindikatoren
import de.troetenrepublik.app.engine.Fruehwarnung
import de.troetenrepublik.app.engine.Blasen
import de.troetenrepublik.app.engine.Lagebild
import de.troetenrepublik.app.market.Quartalszahlen
import de.troetenrepublik.app.market.Strategien
import de.troetenrepublik.app.ui.CrashData
import de.troetenrepublik.app.ui.REFERENCE
import de.troetenrepublik.app.ui.blNameAlle
import de.troetenrepublik.app.ui.blWert
import de.troetenrepublik.app.ui.depotWertReihe
import de.troetenrepublik.app.ui.lbWertText
import de.troetenrepublik.app.ui.moveIn
import de.troetenrepublik.app.ui.shocked
import de.troetenrepublik.app.engine.Performanceanalyse
import de.troetenrepublik.app.engine.Rueckgang
import de.troetenrepublik.app.engine.Trading
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.Politik
import de.troetenrepublik.app.market.Prognose
import de.troetenrepublik.app.market.Region
import de.troetenrepublik.app.market.ohneAbbruch
import de.troetenrepublik.app.plattform.SichereDatei
import de.troetenrepublik.app.ui.Fmt
import de.troetenrepublik.app.ui.PmRouten
import de.troetenrepublik.app.ui.ausblickBeitraege
import de.troetenrepublik.app.ui.kurzName
import de.troetenrepublik.app.ui.pmText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.abs

// Daten der Kategorie-Widgets (0.73) aus dem, was die App ohnehin hat: gespeicherte Rangliste, Speicher des
// Aktienfinders, Konto und die schon zwischengespeicherten Abrufe der Seiten. Jede Quelle ist eine kleine Funktion, damit
// sich eine Aenderung an der Seite dahinter an einer Stelle nachziehen laesst. Der Aufbau der Karte steht in WidgetKartenBau.

private const val STUNDE = 3_600_000L
private const val TAG_HALB = 12 * STUNDE

// Frist, nach der eine Karte frueher nicht neu gerechnet wird. Der Aktienfinder kostet nichts (Speicher der App) und steht
// deshalb nur in KARTEN_ABLAGE und wird bei jedem Lauf gerechnet.
private val KARTEN_FRIST = mapOf(
    WidgetKarten.AUSBLICK to 3 * STUNDE, WidgetKarten.DURCHBRUECHE to 3 * STUNDE, WidgetKarten.COPYTRADING to 3 * STUNDE,
    WidgetKarten.PROGNOSE to STUNDE / 2, WidgetKarten.TRENDS to STUNDE / 2, WidgetKarten.BEWEGUNGEN to STUNDE / 4,
    WidgetKarten.LAGE to STUNDE, WidgetKarten.FRUEHWARNUNG to STUNDE, WidgetKarten.MARKTPHASE to STUNDE, WidgetKarten.BLASEN to 3 * STUNDE,
    WidgetKarten.TERMINE to 3 * STUNDE,
    // Seit 0.78: Themenkoerbe, Index, Sektoren (Kurse), Kongress-Flow und Signale (viele Abrufe, nur alle sechs Stunden)
    WidgetKarten.THEMEN to STUNDE / 2, WidgetKarten.INDEXBEWEGER to STUNDE / 2, WidgetKarten.SEKTOREN to STUNDE / 2,
    WidgetKarten.KONGRESSFLOW to 6 * STUNDE, WidgetKarten.SIGNALE to 6 * STUNDE, WidgetKarten.VOLATILITAET to 3 * STUNDE,
    // Aus mitgelieferten Dateien, ohne Netz: nur nach einem Update der App anders
    WidgetKarten.MEISTGEKAUFT to STUNDE, WidgetKarten.HAEUSER to STUNDE,
    // Abschnitte von 0.77: Insider-Stand, Kongress und Federal Register alle sechs Stunden, 8-K-Meldungen alle zwoelf, Buendel und
    // Speicher des Aktienfinders stuendlich
    WidgetKarten.KONVERGENZ to 6 * STUNDE, WidgetKarten.INSIDERSELTEN to 6 * STUNDE, WidgetKarten.PRAESIDIAL to 6 * STUNDE,
    WidgetKarten.WARNZEICHEN to TAG_HALB, WidgetKarten.INSIDERSIM to STUNDE, WidgetKarten.VORSCHLAG to STUNDE, WidgetKarten.BRANCHE to STUNDE,
)

// Karten aus dem Konto, die auf Netzabrufen beruhen (Performance, Crash-Test): hoechstens alle sechs Stunden, nur im Speicher.
private const val KONTO_FRIST = 6 * STUNDE

// Karten, die auf dem Geraet liegen bleiben: nur Inhalte aus oeffentlichen Quellen und der Wahl des Nutzers; Orders und
// alles aus dem Konto werden bei jedem Zeichnen gerechnet und nirgends abgelegt.
// Der Stillhalter kommt vom Werkzeug in Analytics (widgetStillhalterMerken), nicht aus einem Lauf im Hintergrund.
private val KARTEN_ABLAGE = KARTEN_FRIST.keys + WidgetKarten.AKTIENFINDER + WidgetKarten.STILLHALTER

private val kartenJson = Json { ignoreUnknownKeys = true }
private val kartenSerializer = MapSerializer(String.serializer(), WidgetKarte.serializer())

// Die Ablage traegt die Fassung der App: Karten einer anderen Fassung haben einen anderen Aufbau und werden neu gerechnet,
// statt bis zum Ablauf ihrer Frist stehen zu bleiben.
private const val KARTEN_STAND = "_fassung"
private val KARTEN_AUS_DER_APP = setOf(WidgetKarten.AKTIENFINDER, WidgetKarten.STILLHALTER, WidgetKarten.VORSCHLAG, WidgetKarten.BRANCHE)

internal fun widgetKartenBytes(ablage: Map<String, WidgetKarte>, fassung: String = de.troetenrepublik.app.market.Aktualisierung.FASSUNG): ByteArray =
    kartenJson.encodeToString(kartenSerializer, ablage + (KARTEN_STAND to WidgetKarte(fassung))).toByteArray(Charsets.UTF_8)

internal fun widgetKartenLesen(bytes: ByteArray, fassung: String = de.troetenrepublik.app.market.Aktualisierung.FASSUNG): Map<String, WidgetKarte> {
    val roh = runCatching { kartenJson.decodeFromString(kartenSerializer, bytes.toString(Charsets.UTF_8)) }.getOrDefault(emptyMap())
    if (roh[KARTEN_STAND]?.titel == fassung) return roh - KARTEN_STAND
    // Andere Fassung: Karten, die nur ein Lauf in der App fuellt (Aktienfinder, Stillhalter), bleiben mit abgelaufener
    // Frist stehen, bis die App sie neu rechnet; alle anderen holt der naechste Lauf im Hintergrund frisch.
    return (roh - KARTEN_STAND).filterKeys { WidgetVorgabe.art(it) in KARTEN_AUS_DER_APP }.mapValues { it.value.copy(am = 1L) }
}

private object WidgetKartenSpeicher {
    @Volatile var karten: Map<String, WidgetKarte>? = null
    // Aus dem Konto gerechnet, nur im Speicher
    @Volatile var konto: Map<String, WidgetKarte> = emptyMap()
    // Zuletzt vom Werkzeug gerechnete Stillhalter-Werte, damit die Karte einen Sprachwechsel mitmacht
    @Volatile var stillhalt: List<WidgetKartenBau.Stillhalt> = emptyList()
}

internal fun widgetStillhaltMerken(werte: List<WidgetKartenBau.Stillhalt>) { WidgetKartenSpeicher.stillhalt = werte }

private fun AppState.kartenDatei() = SichereDatei(File(store.ordner, "widget-karten.json"))

// Zuletzt gerechnete Karten: im Speicher, sonst von der Platte.
internal fun AppState.widgetKartenGespeichert(): Map<String, WidgetKarte> {
    WidgetKartenSpeicher.karten?.let { return it }
    val gelesen = runCatching { widgetKartenLesen(kartenDatei().lesen()) }.getOrDefault(emptyMap())
    WidgetKartenSpeicher.karten = gelesen
    return gelesen
}

// Eine Karte, die die App selbst gerechnet hat (Stillhalter), zu den zuletzt gerechneten legen und auf dem Geraet merken.
internal suspend fun AppState.widgetKarteAblegen(schluessel: String, gerechnet: WidgetKarte) {
    val karte = gerechnet.copy(sprache = de.troetenrepublik.app.i18n.I18n.lang.name)
    val alt = widgetKartenGespeichert()
    if (alt[schluessel] == karte) return
    val neu = HashMap(alt)
    neu[schluessel] = karte
    WidgetKartenSpeicher.karten = neu
    val ablage = neu.filterKeys { WidgetVorgabe.art(it) in KARTEN_ABLAGE }
    withContext(Dispatchers.IO) { runCatching { kartenDatei().schreiben(widgetKartenBytes(ablage)) } }
}

// Karten aus dem Konto, die ein Lauf im Hintergrund gerechnet hat (Performance, Crash-Test).
internal fun widgetKartenKonto(): Map<String, WidgetKarte> = WidgetKartenSpeicher.konto

// Mit App-Sperre bleibt nichts liegen.
internal fun AppState.widgetKartenVergessen() {
    WidgetKartenSpeicher.konto = emptyMap()
    WidgetKartenSpeicher.stillhalt = emptyList()
    if (WidgetKartenSpeicher.karten.isNullOrEmpty() && !File(store.ordner, "widget-karten.json").exists()) return
    WidgetKartenSpeicher.karten = emptyMap()
    runCatching { kartenDatei().schreiben(widgetKartenBytes(emptyMap())) }
}

// Karten der Schluessel, die auf dem Startbildschirm liegen (Art, bei einer Variante mit ihr dahinter), neu rechnen, soweit
// ihre Frist um ist. Scheitert eine Quelle, bleibt die letzte Karte stehen.
internal suspend fun AppState.widgetKartenLaden(schluessel: Set<String>, jetzt: Long) {
    val alt = widgetKartenGespeichert()
    val neu = HashMap(alt)
    val stand = S.widgetStand(Fmt.time(jetzt))
    val sprache = de.troetenrepublik.app.i18n.I18n.lang.name
    for (key in schluessel) {
        val art = WidgetVorgabe.art(key)
        val variante = WidgetVorgabe.variante(key)
        if (art !in WidgetKarten.ARTEN || art !in KARTEN_ABLAGE) continue
        val vorher = alt[key]
        if (vorher != null && vorher.am > 0 && vorher.sprache == sprache && jetzt - vorher.am < (KARTEN_FRIST[art] ?: 0L)) continue
        val karte = ohneAbbruch {
            when (art) {
                WidgetKarten.AUSBLICK -> quelleAusblick(stand, jetzt)
                WidgetKarten.BEWEGUNGEN -> quelleBewegungen(stand, jetzt)
                WidgetKarten.TRENDS -> quelleTrends(stand, jetzt)
                WidgetKarten.DURCHBRUECHE -> quelleDurchbrueche(stand, jetzt)
                WidgetKarten.AKTIENFINDER -> quelleAktienfinder(stand, jetzt)?.let { (code, k) ->
                    // Jedes geladene Universum behaelt seine Karte; ein Widget mit anderem Universum zeigt die zuletzt gemerkte
                    neu[WidgetVorgabe.kartenSchluessel(WidgetKarten.AKTIENFINDER, code)] = k
                    if (variante == null || variante == code) k else null
                }
                WidgetKarten.COPYTRADING -> quelleCopytrading(variante, stand, jetzt)
                WidgetKarten.PROGNOSE -> quellePrognose(variante, stand, jetzt)
                WidgetKarten.LAGE -> quelleLage(stand, jetzt)
                WidgetKarten.FRUEHWARNUNG -> quelleFruehwarnung(stand, jetzt)
                WidgetKarten.MARKTPHASE -> quelleMarktphase(stand, jetzt)
                WidgetKarten.BLASEN -> quelleBlasen(stand, jetzt)
                WidgetKarten.TERMINE -> quelleTermine(variante ?: WidgetVorgabe.TERMIN_DIVIDENDEN, stand, jetzt)
                WidgetKarten.THEMEN -> quelleThemen(stand, jetzt)
                WidgetKarten.INDEXBEWEGER -> quelleIndexBeweger(variante, stand, jetzt)
                WidgetKarten.SEKTOREN -> quelleSektoren(stand, jetzt)
                WidgetKarten.KONGRESSFLOW -> quelleKongressFlow(stand, jetzt)
                WidgetKarten.SIGNALE -> widgetWertOderStandard(variante)?.let { quelleSignale(it, vorher, stand, jetzt) }
                WidgetKarten.VOLATILITAET -> widgetWertOderStandard(variante)?.let { quelleVolatilitaet(it, vorher, stand, jetzt) }
                WidgetKarten.MEISTGEKAUFT -> quelleMeistgekauft(jetzt)
                WidgetKarten.HAEUSER -> quelleHaeuser(jetzt)
                WidgetKarten.KONVERGENZ -> quelleKonvergenz(vorher, jetzt)
                WidgetKarten.INSIDERSELTEN -> quelleInsiderSelten(vorher, jetzt)
                WidgetKarten.INSIDERSIM -> quelleInsiderSim(jetzt)
                WidgetKarten.VORSCHLAG -> quelleVorschlag(vorher, jetzt)
                WidgetKarten.BRANCHE -> quelleBranche(variante, vorher, jetzt)
                WidgetKarten.WARNZEICHEN -> widgetWertOderStandard(variante)?.let { quelleWarnzeichen(it, vorher, stand, jetzt) }
                WidgetKarten.PRAESIDIAL -> quellePraesidial(vorher, stand, jetzt)
                // Rechnet nur das Werkzeug; bis dahin der Hinweis, wo es liegt (statt "Lädt")
                WidgetKarten.STILLHALTER -> when {
                    vorher == null -> WidgetKartenBau.stillhalter(emptyList(), stand, jetzt)
                    vorher.sprache == sprache -> null
                    vorher.zeilen.isEmpty() -> WidgetKartenBau.stillhalter(emptyList(), stand, jetzt)
                    WidgetKartenSpeicher.stillhalt.isNotEmpty() ->
                        WidgetKartenBau.stillhalter(WidgetKartenSpeicher.stillhalt, S.widgetStand(Fmt.time(vorher.am)), vorher.am)
                    else -> null
                }
                else -> null
            }
        }.getOrNull()
        if (karte != null) neu[key] = karte.copy(sprache = sprache)
        else if ((vorher == null || vorher.istHinweisInAndererSprache(sprache)) && widgetOhneDatenKarte(art, variante)) neu[key] = ohneDatenKarte(art, jetzt, KARTEN_FRIST[art] ?: 0L).copy(sprache = sprache)
    }
    // Karten zu Werten, die kein Widget mehr zeigt (anderer Wert gewaehlt), fallen weg
    for (a in listOf(WidgetKarten.SIGNALE, WidgetKarten.VOLATILITAET, WidgetKarten.INDEXBEWEGER, WidgetKarten.WARNZEICHEN, WidgetKarten.BRANCHE)) {
        neu.keys.filter { WidgetVorgabe.art(it) == a && it !in schluessel }.forEach { neu.remove(it) }
    }
    if (neu != alt) {
        WidgetKartenSpeicher.karten = neu
        val ablage = neu.filterKeys { WidgetVorgabe.art(it) in KARTEN_ABLAGE }
        withContext(Dispatchers.IO) { runCatching { kartenDatei().schreiben(widgetKartenBytes(ablage)) } }
    }
    widgetKontoKartenLaden(schluessel, jetzt)
}

// Eine Karte nur mit Hinweis, in einer anderen Sprache gerechnet: sie wird in der neuen Sprache ersetzt.
private fun WidgetKarte.istHinweisInAndererSprache(sprache: String): Boolean = this.sprache != sprache && zeilen.isEmpty() && haupt.isBlank()

// Arten, die ohne Ergebnis eine Karte mit Hinweis bekommen, statt dauernd "Laedt" zu zeigen. Signale, Volatilitaet und
// Warnzeichen brauchen einen gewaehlten Wert und haben ihren eigenen Hinweis; die eigenen Wetten kommen aus dem Konto.
private fun widgetOhneDatenKarte(art: String, variante: String?): Boolean =
    art !in setOf(WidgetKarten.SIGNALE, WidgetKarten.VOLATILITAET, WidgetKarten.WARNZEICHEN) && variante != WidgetVorgabe.PM_WETTEN

// Karte ohne Zeilen mit dem Grund. Sie gilt als schon gerechnet, bis zehn Minuten vor Ablauf der Frist: der naechste Versuch
// kommt bald, ohne bei jedem Zeichnen das Netz zu fragen.
private fun ohneDatenKarte(art: String, jetzt: Long, frist: Long, hinweis: String? = null): WidgetKarte = WidgetKarte(
    WidgetKarten.titel(art),
    hinweis = hinweis ?: when (art) {
        WidgetKarten.AKTIENFINDER -> S.widgetFinderLeer
        WidgetKarten.PROGNOSE -> S.pmUnreachable
        else -> S.widgetKeineDaten
    },
    ziel = WidgetKarten.ziel(art), am = if (frist > 0L) jetzt - frist + 10 * 60_000L else 1L,
)

// Karten aus dem Konto mit Netzabrufen: nur im Speicher, bei App-Sperre geleert.
private suspend fun AppState.widgetKontoKartenLaden(schluessel: Set<String>, jetzt: Long) {
    val arten = schluessel.map { WidgetVorgabe.art(it) }.toSet()
    // Der Depot-Check rechnet die Fremdwaehrung mit den Laendern der Fonds
    if (WidgetKarten.DEPOTCHECK in arten) ohneAbbruch { etfLaender() }
    val neu = HashMap(WidgetKartenSpeicher.konto)
    for (art in listOf(WidgetKarten.PERFORMANCE, WidgetKarten.CRASHTEST)) {
        if (art !in arten) continue
        val vorher = neu[art]
        if (vorher != null && vorher.am > 0 && vorher.sprache == de.troetenrepublik.app.i18n.I18n.lang.name && jetzt - vorher.am < KONTO_FRIST) continue
        val karte = ohneAbbruch {
            if (art == WidgetKarten.PERFORMANCE) quellePerformance(jetzt) else quelleCrashtest(jetzt)
        }.getOrNull()
        if (karte != null) neu[art] = karte.copy(sprache = de.troetenrepublik.app.i18n.I18n.lang.name)
        else if (vorher == null || vorher.istHinweisInAndererSprache(de.troetenrepublik.app.i18n.I18n.lang.name)) neu[art] = ohneDatenKarte(
            art, jetzt, KONTO_FRIST, if (account.value.positions.isEmpty()) S.widgetKeinePositionen else S.widgetKeineDaten,
        ).copy(sprache = de.troetenrepublik.app.i18n.I18n.lang.name)
    }
    WidgetKartenSpeicher.konto = neu
}

// ------------------------------------------------------------ Quellen je Kategorie

// Ausblick: dieselben zwischengespeicherten Abrufe wie der Reiter Ausblick, daraus die Gesamtaussage fuer Aktien.
private suspend fun AppState.quelleAusblick(stand: String, jetzt: Long): WidgetKarte? = coroutineScope {
    val fruehe = async { ohneAbbruch { fruehSignale(Fruehindikatoren.AUSBLICK) }.getOrNull() }
    val ersatz = async { ohneAbbruch { ersatzSignale() }.getOrNull() }
    val weitere = async { ohneAbbruch { weitereSignale() }.getOrNull() }
    val zins = async { ohneAbbruch { zinsErwartung() }.getOrNull() }
    val erwartet = async { ohneAbbruch { marktErwartungen() }.getOrNull() }
    val blasen = async { ohneAbbruch { blasen() }.getOrNull() }
    val f = fruehe.await(); val e = ersatz.await(); val w = weitere.await(); val z = zins.await(); val x = erwartet.await(); val b = blasen.await()
    if (f == null && e == null && w == null && z == null && x == null && b == null) return@coroutineScope null
    val beitraege = ausblickBeitraege(z, x, f, e, w, java.time.LocalDate.now().toEpochDay())
    val einheiten = Ausblickgesamt.einheiten(beitraege)
    val aktien = Ausblickgesamt.Markt.AKTIEN
    val wochen = Ausblickgesamt.auswerten(einheiten, aktien, Ausblickgesamt.Horizont.KURZ)
    val monate = Ausblickgesamt.auswerten(einheiten, aktien, Ausblickgesamt.Horizont.MITTEL)
    val gesamt = Ausblickgesamt.auswerten(einheiten, aktien)
    val gruende = (gesamt.treiberPositiv + gesamt.treiberNegativ).sortedByDescending { it.gewicht * abs(it.wert) }.take(4).map { S.widgetGrundName(it.schluessel, it.name) to (it.wert > 0) }
    WidgetKartenBau.ausblick(wochen.urteil.ordinal, monate.urteil.ordinal, gruende, stand, jetzt)
}

// Bewegungen: die Gewinner und Verlierer des US-Markts laut Yahoo (15 Minuten gespeichert) mit Kurs; Werte ohne nennenswerten
// Umsatz zaehlen nicht, wie auf der Seite.
private suspend fun AppState.quelleBewegungen(stand: String, jetzt: Long): WidgetKarte? {
    val symbole = yahooBewegungen().takeIf { it.isNotEmpty() } ?: return null
    val kurse = market.snapshot(symbole)
    val liste = symbole.mapNotNull { s ->
        kurse[s]?.takeIf { it.prevClose > 0 && it.price > 0 && it.price * it.volume >= 2_000_000.0 }?.let { s to it }
    }
    if (liste.isEmpty()) return null
    fun bewegung(p: Pair<String, de.troetenrepublik.app.data.Quote>) = WidgetKartenBau.Bewegung(p.first, kurzName(p.first, p.second.name), p.second.sitzungPct)
    val gewinner = liste.filter { it.second.sitzungPct > 0 }.sortedByDescending { it.second.sitzungPct }.take(3).map(::bewegung)
    val verlierer = liste.filter { it.second.sitzungPct < 0 }.sortedBy { it.second.sitzungPct }.take(3).map(::bewegung)
    return WidgetKartenBau.bewegungen(gewinner, verlierer, stand, jetzt)
}

// Trends: die meistbesprochenen Aktien auf Reddit (ApeWisdom, 20 Minuten gespeichert).
private suspend fun AppState.quelleTrends(stand: String, jetzt: Long): WidgetKarte? {
    val liste = redditTrends().takeIf { it.isNotEmpty() } ?: return null
    val werte = liste.take(6).map { e -> WidgetKartenBau.Trendwert(e.ticker, kurzName(e.ticker, e.name.ifBlank { e.ticker }), e.erwaehnungen, e.erwaehnungenVorher) }
    return WidgetKartenBau.trends(werte, stand, jetzt)
}

// Durchbrueche: Meldungen der letzten 14 Tage (eine Stunde gespeichert) und der kuratierte Inhalt der Themen.
private suspend fun AppState.quelleDurchbrueche(stand: String, jetzt: Long): WidgetKarte? {
    val meldungen = (durchbruecheStand() ?: durchbrueche()).orEmpty()
    val inhalt = durchbruecheInhalt()
    val deutsch = settingsFlow.value.sprache.code == "de"
    val blocks = Durchbrueche.ansicht(inhalt, meldungen.orEmpty(), jetzt)
    val neu = blocks.filter { it.neu.isNotEmpty() }.take(4).map { b ->
        val m = b.neu.first()
        val alter = Durchbrueche.alter(m.zeit, jetzt).let { S.dbAlter(it.wert, it.einheit) }
        WidgetKartenBau.Durchbruch(m.titel, b.thema.name(deutsch), alter)
    }
    // Erwartete Meilensteine, noch nicht abgelaufen, nach Ablaufmonat
    val erwartet = blocks.flatMap { b -> b.erwartet(jetzt).map { e -> Triple(b.thema.name(deutsch), e.marke(deutsch), e) } }
        .filter { it.third.text(deutsch).isNotBlank() }
        .sortedBy { it.third.bis }
        .map { (thema, marke, e) -> WidgetKartenBau.Durchbruch(e.text(deutsch), thema, marke) }
    if (meldungen.isNullOrEmpty() && inhalt == null) return null
    return WidgetKartenBau.durchbrueche(neu, erwartet, stand, jetzt)
}

// Aktienfinder: das zuletzt geladene Ergebnis im Speicher (der Markt, den die App zuletzt gezeigt hat) mit den Standardgewichten.
// Ohne Ergebnis im Speicher bleibt die letzte Karte; der Finder laedt nichts fuers Widget.
private fun AppState.quelleAktienfinder(stand: String, jetzt: Long): Pair<String, WidgetKarte>? {
    val z = FinderLauf.zustand.value
    val code = z.universum ?: AktienfinderSpeicher.universum ?: return null
    val daten = z.daten.ifEmpty { AktienfinderSpeicher.daten }.takeIf { it.isNotEmpty() } ?: return null
    val ergebnisse = Aktienfinder.auswahl(Aktienfinder.bewerten(daten), Aktienfinder.Filter(), Aktienfinder.Sortierung.Gesamt, regionVon = { Region.ofCountry(it) })
    val markt = if (code in FINDER_REGIONEN) S.dfUniversumName(code) else Catalog.indexName(code)
    val werte = ergebnisse.take(6).map { WidgetKartenBau.Fund(it.symbol, kurzName(it.symbol, it.daten.name), it.gesamt) }
    return code to WidgetKartenBau.aktienfinder(markt, werte, stand, if (z.stand > 0) z.stand else jetzt)
}

// Copytrading: das jeweils letzte Geschaeft jeder gefolgten Person (Repraesentantenhaus aus den Meldungen, Senat aus dem
// Spiegel, Investoren nach 13F), sonst die gespeicherte Rangliste.
private suspend fun AppState.quelleCopytrading(stand: String, jetzt: Long): WidgetKarte? {
    val gefolgt = gefolgt().sorted().take(8)
    val repo = copy
    if (repo != null && gefolgt.any { it.startsWith("k:") }) ohneAbbruch { repo.verzeichnis() }
    fun text(g: List<de.troetenrepublik.app.market.Kongress.Geschaeft>): Pair<String, Boolean?>? {
        val e = g.firstOrNull() ?: return null
        val art = if (e.kauf) S.vdKaeufe1 else if (e.verkauf) S.copySell else S.copyExchange
        val mehr = if (g.size > 1) " +" + (g.size - 1) else ""
        return (art + " " + (e.kuerzel ?: e.wert) + mehr) to (if (e.kauf) true else if (e.verkauf) false else null)
    }
    val handel = ArrayList<WidgetKartenBau.Handel>()
    for (key in gefolgt) {
        when {
            key.startsWith("k:") && repo != null -> {
                val person = key.removePrefix("k:")
                val m = repo.stand.value.meldungen.filter { it.person == person }.maxByOrNull { it.datum } ?: continue
                val (t, kauf) = text(ohneAbbruch { repo.geschaefte(m) }.getOrNull().orEmpty()) ?: continue
                handel += WidgetKartenBau.Handel(m.name, t, kauf, Fmt.date(m.datum + TAG_HALB), m.datum)
            }
            key.startsWith("n:") -> {
                val person = key.removePrefix("n:")
                val m = senat?.meldungen?.filter { it.person == person }?.maxByOrNull { Politik.tag(it.eingang) } ?: continue
                val tag = Politik.tag(m.eingang)
                val (t, kauf) = text(m.geschaefte.map { Politik.geschaeft(it, tag) }) ?: continue
                handel += WidgetKartenBau.Handel(m.name, t, kauf, Fmt.date(tag + TAG_HALB), tag)
            }
            key.startsWith("i:") -> {
                val inv = investoren.firstOrNull { it.id == key.removePrefix("i:") } ?: continue
                val tag = runCatching { java.time.LocalDate.parse(inv.aktuell?.stichtag.orEmpty()).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() ?: continue
                handel += WidgetKartenBau.Handel(inv.name, "13F", null, Fmt.date(tag + TAG_HALB), tag)
            }
        }
    }
    val raenge = if (handel.isEmpty()) ranglisteGespeichert()?.second.orEmpty().sortedByDescending { it.vorsprung }.map { WidgetKartenBau.Platz(it.name, it.vorsprung, it.kaeufe) } else emptyList()
    return WidgetKartenBau.copytrading(handel, raenge, stand, jetzt)
}

// Prognosemaerkte: die Ereignisse der ersten Kategorie bei Polymarket wie beim Oeffnen der Seite (zwischengespeichert),
// je mit der fuehrenden Seite.
private suspend fun AppState.quellePrognose(stand: String, jetzt: Long): WidgetKarte? {
    val ereignisse = prognose.liste(Prognose.Quelle.POLYMARKET, Prognose.POLY_KATEGORIEN.first().schluessel)?.ereignisse?.takeIf { it.isNotEmpty() } ?: return null
    val wetten = ereignisse.filter { it.maerkte.isNotEmpty() }.take(5).map { e ->
        val oben = if (e.binaer) e.maerkte.first() else e.maerkte.maxBy { it.chance }
        WidgetKartenBau.Wette(
            pmText(e.titel), oben.chance, oben.aenderung, if (e.binaer) "" else pmText(oben.titel), PmRouten.ereignis(e.quelle, e.id),
        )
    }
    return WidgetKartenBau.prognose(wetten, stand, jetzt)
}

// ------------------------------------------------------------ Karten aus dem Konto (bei jedem Zeichnen)

// Orders, Analytics und die Werkzeuge des Depots aus dem Konto; Betraege nur ohne „Betraege ausblenden“.
// rueckgang: der in AppState.maxRueckgang berechnete Wert wie in Analytics; ohne ihn gilt der aufgezeichnete Verlauf.
// kategorien: gespeicherte Kategorien (Settings.kategorien) fuer Umschichten, quotes fuer die Waehrungen des Depot-Checks.
internal fun widgetKartenAusKonto(
    acc: Account, v: Valuation, verdeckt: Boolean, jetzt: Long, rueckgang: Double? = null,
    kategorien: String = "", quotes: Map<String, de.troetenrepublik.app.data.Quote> = emptyMap(),
    // Laender der Fonds fuer die Fremdwaehrung des Depot-Checks (AppState.etfLaender), leer ohne Durchschau.
    etfLaender: Map<String, Map<String, Double>> = emptyMap(),
): Map<String, WidgetKarte> {
    val offen = acc.orders.filter { it.status == OrderStatus.OPEN }.sortedByDescending { it.createdAt }.map { o ->
        val art = if (o.side == Side.BUY) S.buy else S.sell
        val typ = when (o.type) { OrderType.LIMIT -> S.limit; OrderType.STOP, OrderType.TRAILING_STOP -> S.stop; OrderType.MARKET -> "" }
        WidgetKartenBau.Auftrag(
            kurzName(o.symbol, o.name), o.symbol, listOf(art, Fmt.qty(o.qty) + " " + S.pieces, typ).filter { it.isNotBlank() }.joinToString(" · "),
            if (o.type == OrderType.MARKET) "" else Fmt.priceEuro(o.limitPrice),
        )
    }
    val plaene = acc.plans.filter { !it.paused }.map { p ->
        val naechster = Trading.nextExecution(p, jetzt)
        WidgetKartenBau.Plan(kurzName(p.symbol, p.name), if (verdeckt) "••••" else Fmt.euro(p.amount), naechster, if (naechster > 0) Fmt.dayMonthNum(naechster) else "")
    }
    val aktiv = v.positions.filter { it.value > 0 }
    // Verkaufte Optionen: Ergebnis mit, Praemie als Betrag im Einsatz
    val einsatz = v.positions.sumOf { kotlin.math.abs(it.invested) }
    val rendite = if (aktiv.isNotEmpty() && einsatz > 0) (aktiv.sumOf { it.profit } + v.positions.filter { it.position.verkauft }.sumOf { it.profit }) / einsatz * 100 else null
    val groesste = aktiv.maxByOrNull { it.value }
    val gesamt = v.securities
    val aus = HashMap<String, WidgetKarte>()
    aus[WidgetKarten.ORDERS] = WidgetKartenBau.orders(offen, plaene, "", 0L)
    aus[WidgetVorgabe.kartenSchluessel(WidgetKarten.ORDERS, WidgetVorgabe.ORDERS_ORDERS)] = WidgetKartenBau.orders(offen, plaene, "", 0L, WidgetVorgabe.ORDERS_ORDERS)
    aus[WidgetVorgabe.kartenSchluessel(WidgetKarten.ORDERS, WidgetVorgabe.ORDERS_PLAENE)] = WidgetKartenBau.orders(offen, plaene, "", 0L, WidgetVorgabe.ORDERS_PLAENE)
    aus[WidgetKarten.ANALYTICS] = WidgetKartenBau.analytics(
        rendite, if (aktiv.isNotEmpty()) (rueckgang ?: widgetRueckgang(acc)) else null,
        groesste?.let { if (it.position.isDerivative) it.position.name else kurzName(it.position.symbol, it.position.name) },
        groesste?.takeIf { gesamt > 0 }?.let { it.value / gesamt * 100 }, "", 0L,
    )
    // Eigene Wetten der Prognosemaerkte: offene Wetten mit Wahrscheinlichkeit der gewaehlten Seite
    val wetten = acc.wetten.filter { it.offen }.sortedByDescending { it.eroeffnet }.take(5).map {
        WidgetKartenBau.Wette(pmText(it.titel), it.kurs, null, pmText(it.seitenName.ifBlank { it.frage }), runCatching { PmRouten.ereignis(Prognose.Quelle.valueOf(it.quelle), it.ereignis) }.getOrDefault(""))
    }
    aus[WidgetVorgabe.kartenSchluessel(WidgetKarten.PROGNOSE, WidgetVorgabe.PM_WETTEN)] =
        WidgetKartenBau.prognose(wetten, "", 0L, S.pmMyBets).copy(hinweis = S.widgetWettenLeer)
    // Ohne Position haben Depot-Check, Streuung und Umschichten nichts zu zeigen; der Hinweis steht schon in der Karte
    if (aktiv.isEmpty()) {
        for (a in listOf(WidgetKarten.DEPOTCHECK, WidgetKarten.STREUUNG, WidgetKarten.UMSCHICHTEN)) aus[a] = WidgetKarte(WidgetKarten.titel(a), hinweis = S.widgetKeinePositionen, ziel = WidgetKarten.ziel(a))
        return aus
    }

    // Depot-Check ohne laufende Kosten der Fonds (kein Abruf); verkaufte Optionen mit dem Betrag ihrer Verbindlichkeit
    val werte = v.positions.filter { it.value > 0 || it.position.verkauft }.map { vp ->
        val s = vp.position.symbol
        val typ = Symbols.typeOf(s)
        DepotCheck.Wert(
            de.troetenrepublik.app.ui.positionsName(vp.position), kotlin.math.abs(vp.value),
            when {
                vp.position.isDerivative -> DepotCheck.Art.DERIVAT
                typ == AssetType.ETF -> DepotCheck.Art.ETF
                typ == AssetType.CRYPTO -> DepotCheck.Art.KRYPTO
                typ == AssetType.STOCK -> DepotCheck.Art.AKTIE
                else -> DepotCheck.Art.SONST
            },
            quotes[s]?.nativeCurrency?.takeIf { it.isNotBlank() } ?: Symbols.find(s)?.currency ?: "EUR",
            Catalog.industryOf(s)?.let { S.industryName(it) } ?: Symbols.find(s)?.sector?.takeIf { it.isNotBlank() }?.let { S.sectorName(it) },
            vp.profitPct, symbol = if (vp.position.isDerivative) "" else s,
        )
    }
    val seit = jetzt - 365L * 86_400_000L
    val ordersJahr = acc.transactions.filter { (it.type == TxType.BUY || it.type == TxType.SELL) && it.ts >= seit && !it.via.startsWith("IMPORT") }
    val befunde = DepotCheck.pruefen(werte, acc.cash - acc.gebunden, ordersJahr.sumOf { it.fee }, ordersJahr.sumOf { it.qty * it.price }, ordersJahr.size, acc.plans.size, etf = { s -> etfLaender[s] })
    aus[WidgetKarten.DEPOTCHECK] = WidgetKartenBau.depotcheck(
        befunde.map { b -> WidgetKartenBau.Befund(S.widgetCheckName(b.regel.name), when (b.stufe) { DepotCheck.Stufe.GUT -> 0; DepotCheck.Stufe.ACHTUNG -> 1; DepotCheck.Stufe.KRITISCH -> 2; DepotCheck.Stufe.HINWEIS -> 3 }) }, 0L,
    )

    // Streuung nach Anlageklassen
    val klassen = aktiv.groupBy { classOf(it) }.map { (k, l) -> WidgetKartenBau.Anteil(S.assetClass(k), l.sumOf { it.value } / aktiv.sumOf { it.value }) }
    aus[WidgetKarten.STREUUNG] = WidgetKartenBau.streuung(
        groesste?.let { if (it.position.isDerivative) it.position.name else kurzName(it.position.symbol, it.position.name) },
        groesste?.takeIf { gesamt > 0 }?.let { it.value / aktiv.sumOf { p -> p.value } }, klassen, 0L,
    )

    // Umschichten: Abweichung von den gespeicherten Zielgewichten je Kategorie wie auf der Seite (ohne Derivate)
    val ohneDerivate = aktiv.filter { !it.position.isDerivative }
    if (ohneDerivate.size >= 2) {
        val gespeichert = Kategorien.lesen(kategorien)
        val stand = if (gespeichert.vorgeschlagen) gespeichert else Kategorien.vorschlag(ohneDerivate.map { Kategorien.Wert(it.key, it.value, S.assetClass(classOf(it))) })
        val plan = Kategorien.plan(stand, ohneDerivate.map { it.key to it.value }, 0.0, true, S.katOhne)
        aus[WidgetKarten.UMSCHICHTEN] = WidgetKartenBau.umschichten(plan.zeilen.filter { it.id != null }.map { WidgetKartenBau.Abweichung(it.name.ifBlank { "-" }, it.ist * 100.0, it.ziel * 100.0) }, 0L)
    } else aus[WidgetKarten.UMSCHICHTEN] = WidgetKarte(WidgetKarten.titel(WidgetKarten.UMSCHICHTEN), hinweis = S.widgetZuWenigPositionen, ziel = WidgetKarten.ziel(WidgetKarten.UMSCHICHTEN))
    return aus
}

// Groesster Rueckgang aus dem aufgezeichneten Verlauf, wenn AppState.maxRueckgang noch keinen Wert hat.
private fun widgetRueckgang(acc: Account): Double = Rueckgang.aufgezeichnet(acc)

// ------------------------------------------------------------ Seit 0.77: Unterseiten und Werkzeuge

private fun copyText(g: List<de.troetenrepublik.app.market.Kongress.Geschaeft>): Pair<String, Boolean?>? {
    val e = g.firstOrNull() ?: return null
    val art = if (e.kauf) S.vdKaeufe1 else if (e.verkauf) S.copySell else S.copyExchange
    val mehr = if (g.size > 1) " +" + (g.size - 1) else ""
    return (art + " " + (e.kuerzel ?: e.wert) + mehr) to (if (e.kauf) true else if (e.verkauf) false else null)
}

// Copytrading je Reiter: Politik als Rangliste, Senat mit den neuesten Meldungen, Vorstaende mit den groessten Kaeufen der
// SEC-Meldungen (schon geladen), Investoren mit dem letzten 13F, Strategien mit ihrem Risiko, Gefolgt wie bisher. Die
// Regierung hat keine Zeilen fuers Widget; ein Tipp oeffnet die Seite.
private suspend fun AppState.quelleCopytrading(variante: String?, stand: String, jetzt: Long): WidgetKarte? {
    val ziel = WidgetKarten.ziel(WidgetKarten.COPYTRADING)
    return when (variante) {
        null, WidgetVorgabe.COPY_GEFOLGT -> quelleCopytrading(stand, jetzt)?.let { k ->
            // Mit gewaehltem Reiter „Gefolgt“ ersetzt keine Rangliste die leere Liste
            if (variante == null || k.stand.startsWith(S.rgTitel).not()) k else k.copy(zeilen = emptyList(), hinweis = S.widgetCopyNiemand, stand = stand, titel = S.copyFollowing)
        }
        WidgetVorgabe.COPY_POLITIK -> {
            val raenge = ranglisteGespeichert()?.second.orEmpty().sortedByDescending { it.vorsprung }.map { WidgetKartenBau.Platz(it.name, it.vorsprung, it.kaeufe) }
            WidgetKartenBau.copytrading(emptyList(), raenge, stand, jetzt)
        }
        WidgetVorgabe.COPY_SENAT -> {
            val d = senat ?: return null
            val handel = d.meldungen.sortedByDescending { Politik.tag(it.eingang) }.mapNotNull { m ->
                val tag = Politik.tag(m.eingang)
                val (t, kauf) = copyText(m.geschaefte.map { Politik.geschaeft(it, tag) }) ?: return@mapNotNull null
                WidgetKartenBau.Handel(m.name, t, kauf, Fmt.date(tag + TAG_HALB), tag)
            }.take(6)
            WidgetKarte(S.copySenate, handel.map { WidgetZeile(it.person, it.text, it.kauf, unter = it.datum) }, hinweis = S.widgetCopyLeer, stand = stand, ziel = ziel, am = jetzt)
        }
        WidgetVorgabe.COPY_REGIERUNG -> WidgetKarte(S.copyGovernment, hinweis = S.widgetCopyRegierung, stand = stand, ziel = ziel, am = jetzt)
        WidgetVorgabe.COPY_VORSTAENDE -> {
            val ab = java.time.LocalDate.now().minusDays(30).toString()
            val l = sec.value?.geschaefte.orEmpty().filter { it.kauf && it.d >= ab && it.wert > 0 }.sortedByDescending { it.wert }.take(5)
            WidgetKarte(
                S.copyExecutives,
                l.map { WidgetZeile(it.e, Fmt.dollarKompakt(it.wert), true, unter = it.p + " · " + Fmt.date(java.time.LocalDate.parse(it.d).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() + TAG_HALB), ziel = WidgetZiel.instrument(it.k)) },
                hinweis = S.widgetCopyLeer, stand = stand, ziel = ziel, am = jetzt,
            )
        }
        WidgetVorgabe.COPY_INVESTOREN -> {
            val l = investoren.mapNotNull { inv -> inv.aktuell?.let { q -> Triple(inv.name, q.summe, q.stichtag) } }.sortedByDescending { it.second }.take(5)
            WidgetKarte(
                S.copyInvestors,
                l.map { (name, summe, tag) ->
                    val ms = runCatching { java.time.LocalDate.parse(tag).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() + TAG_HALB }.getOrNull()
                    WidgetZeile(name, Fmt.dollarKompakt(summe), unter = ms?.let { Fmt.date(it) }.orEmpty())
                },
                hinweis = S.widgetCopyLeer, stand = stand, ziel = ziel, am = jetzt,
            )
        }
        WidgetVorgabe.COPY_STRATEGIEN -> {
            val deutsch = settingsFlow.value.sprache.code == "de"
            val l = Strategien.alle.take(5)
            WidgetKarte(
                S.copyStrategies, l.map { WidgetZeile(if (deutsch) it.nameDe else it.nameEn, unter = when (it.risiko) { Strategien.Risiko.DEFENSIV -> S.copyDefensive; Strategien.Risiko.AUSGEWOGEN -> S.copyBalanced; Strategien.Risiko.OFFENSIV -> S.copyOffensive }) },
                hinweis = S.widgetCopyLeer, stand = stand, ziel = ziel, am = jetzt,
            )
        }
        else -> null
    }
}

// Prognosemaerkte je Reiter: Kalshi wie Polymarket, grosse Wetten des Tages mit Betrag; eigene Wetten kommen aus dem Konto.
private suspend fun AppState.quellePrognose(variante: String?, stand: String, jetzt: Long): WidgetKarte? = when (variante) {
    WidgetVorgabe.PM_KALSHI -> {
        val ereignisse = prognose.liste(Prognose.Quelle.KALSHI, Prognose.KALSHI_KATEGORIEN.first().schluessel)?.ereignisse?.takeIf { it.isNotEmpty() }
        ereignisse?.let { WidgetKartenBau.prognose(wettenAus(it), stand, jetzt, "Kalshi") }
    }
    // Ohne Echtgeld: die Unterzeile des Titels sagt es
    WidgetVorgabe.PM_MANIFOLD -> {
        val ereignisse = prognose.liste(Prognose.Quelle.MANIFOLD, de.troetenrepublik.app.market.PrognoseWeitere.MANIFOLD_KATEGORIEN.first().schluessel)?.ereignisse?.takeIf { it.isNotEmpty() }
        ereignisse?.let { WidgetKartenBau.prognose(wettenAus(it), stand, jetzt, "Manifold") }
    }
    WidgetVorgabe.PM_METACULUS -> {
        val ereignisse = prognose.wichtigste(Prognose.Quelle.METACULUS)?.ereignisse?.takeIf { it.isNotEmpty() }
        ereignisse?.let { WidgetKartenBau.prognose(wettenAus(it), stand, jetzt, "Metaculus · " + de.troetenrepublik.app.i18n.PrognoseQuellenTexte.t("art.kurz.METACULUS")) }
    }
    WidgetVorgabe.PM_GROSS -> {
        val l = de.troetenrepublik.app.engine.PrognoseAuswahl.grosse(prognose.grosseWetten(Prognose.WettZeit.TAG).orEmpty()).take(5)
        if (l.isEmpty()) null else WidgetKarte(
            S.pmGross,
            l.map { WidgetZeile(pmText(it.titel), Fmt.dollarKompakt(it.betrag), it.kauf, unter = pmText(it.ausgang)) },
            hinweis = S.pmUnreachable, stand = stand, ziel = WidgetKarten.ziel(WidgetKarten.PROGNOSE), am = jetzt,
        )
    }
    WidgetVorgabe.PM_WETTEN -> null
    else -> quellePrognose(stand, jetzt)
}

private fun wettenAus(ereignisse: List<Prognose.Ereignis>): List<WidgetKartenBau.Wette> = ereignisse.filter { it.maerkte.isNotEmpty() }.take(5).map { e ->
    val oben = if (e.binaer) e.maerkte.first() else e.maerkte.maxBy { it.chance }
    WidgetKartenBau.Wette(pmText(e.titel), oben.chance, oben.aenderung, if (e.binaer) "" else pmText(oben.titel), PmRouten.ereignis(e.quelle, e.id))
}

// Lage: die zuletzt gebildeten Aussagen der Seite (Speicher, sonst Datei, sonst was schon auf dem Geraet liegt); kein Abruf.
private suspend fun AppState.quelleLage(stand: String, jetzt: Long): WidgetKarte? {
    val aussagen = lagebildLetzter()?.aussagen ?: Lagebild.aussagen(lagebildEingabe(gespeichert = true)).takeIf { it.isNotEmpty() } ?: return null
    val ereignisse = aussagen.flatMap { a ->
        a.kennzahlen.map { k ->
            val name = if (k.schluessel == "ENGPASS_SCHLECHTESTER") S.engName(k.zusatz.orEmpty()) else S.widgetLageName(k.schluessel, k.zusatz.orEmpty())
            WidgetKartenBau.Ereignis(S.lbThema(a.thema.name), name, lbWertText(k), k.auffaellig)
        }
    }
    return WidgetKartenBau.lage(ereignisse, stand, jetzt)
}

// Fruehwarnzeichen: dieselben zwischengespeicherten Abrufe wie die Seite Stimmung.
private suspend fun AppState.quelleFruehwarnung(stand: String, jetzt: Long): WidgetKarte? {
    val zeichen = fruehwarnung().takeIf { it.isNotEmpty() } ?: return null
    return WidgetKartenBau.fruehwarnung(zeichen.map { WidgetKartenBau.Warnzeichen(S.fwName(it.schluessel), fwWertText(it), it.ampel.ordinal) }, stand, jetzt)
}

// Marktphase: die Maerkte der Seite Stimmung mit Phase und Abstand zum Hoch.
private suspend fun AppState.quelleMarktphase(stand: String, jetzt: Long): WidgetKarte? {
    val phasen = marktphase().takeIf { it.isNotEmpty() } ?: return null
    return WidgetKartenBau.marktphase(phasen.map { WidgetKartenBau.Phase(S.mpIndex(it.schluessel), it.art.name, it.korrektur, it.vomHoch) }, stand, jetzt)
}

// Blasen-Gefahr: nur die vorhandene Abfrage des Blasen-Detektors (sechs Stunden gespeichert).
private suspend fun AppState.quelleBlasen(stand: String, jetzt: Long): WidgetKarte? {
    val b = blasen()
    val stufe = Blasen.gesamt(b.indikatoren) ?: return null
    val z = Blasen.zaehlung(b.indikatoren)
    val staerkste = Blasen.staerkste(b.indikatoren, 5).map { WidgetKartenBau.Anzeichen(S.widgetBlasenName(it.schluessel, blNameAlle(it.schluessel)), blWert(it), it.ampel == Fruehwarnung.Ampel.ROT) }
    return WidgetKartenBau.blasen(stufe.ordinal, z.rot, z.gesamt, Blasen.anteil(b.indikatoren) ?: 0.0, staerkste, Blasen.GRENZE_ERHOEHT, Blasen.GRENZE_HOCH, stand, jetzt)
}

// Termine: die naechsten Termine der gewaehlten Art aus den Kalendern der Nasdaq (zwischengespeichert), Werte aus Depot und
// Merkliste markiert.
private suspend fun AppState.quelleTermine(art: String, stand: String, jetzt: Long): WidgetKarte? {
    val ny = java.time.ZoneId.of("America/New_York")
    val heute = java.time.LocalDate.now(ny)
    val konto = account.value
    val eigene = (konto.positions.filter { !it.isDerivative }.map { it.symbol } + konto.watchlist).toSet()
    fun datum(tag: Long) = Fmt.dayMonthNum(tag * 86_400_000L + TAG_HALB)
    val termine: List<WidgetKartenBau.Termin> = when (art) {
        WidgetVorgabe.TERMIN_WIRTSCHAFT -> {
            val tage = (0..6).map { heute.plusDays(it.toLong()) }
            val d = nasdaqWirtschaft(tage)
            if (d.values.all { it == null }) return null
            tage.flatMap { t -> d[t].orEmpty() }.filter { it.erwartet != null || it.ist != null }.take(40).map {
                WidgetKartenBau.Termin(it.name, datum(it.tag), it.land, false, it.tag * 1440L + (it.minute ?: 0))
            }
        }
        WidgetVorgabe.TERMIN_QUARTALE -> {
            val tage = (0..6).map { heute.plusDays(it.toLong()) }
            val d = quartalszahlen(tage)
            if (d.values.all { it == null }) return null
            tage.flatMap { t -> d[t].orEmpty() }.map {
                WidgetKartenBau.Termin(Quartalszahlen.name(it.name), datum(it.tag), it.quartal.orEmpty(), it.symbol in eigene || it.eigen, it.tag)
            }
        }
        WidgetVorgabe.TERMIN_IPO -> {
            val l = ipoKalender() ?: return null
            l.filter { (it.tag ?: 0L) >= heute.toEpochDay() }.map {
                WidgetKartenBau.Termin(it.name, datum(it.tag ?: heute.toEpochDay()), listOfNotNull(it.boerse, it.preis?.let { p -> de.troetenrepublik.app.ui.ipoPreisText(p) }).joinToString(" · "), false, it.tag ?: 0L)
            }
        }
        else -> {
            val tage = (0..9).map { heute.plusDays(it.toLong()) }.filter { it.dayOfWeek != java.time.DayOfWeek.SATURDAY && it.dayOfWeek != java.time.DayOfWeek.SUNDAY }
            val d = nasdaqDividenden(tage)
            if (d.values.all { it == null }) return null
            tage.flatMap { t -> d[t].orEmpty() }.map {
                WidgetKartenBau.Termin(
                    Quartalszahlen.name(it.name), datum(it.exTag), it.betrag?.let { b -> Fmt.price(b, "USD") }.orEmpty(),
                    it.symbol in eigene || it.eigen, it.exTag,
                )
            }
        }
    }
    // Eigene Werte zuerst, dann der Rest; bei Dividenden und Quartalen bleiben bei vielen Terminen die eigenen und beobachteten
    val auswahl = if (termine.count { it.eigen } >= 3) termine.filter { it.eigen } else termine
    return WidgetKartenBau.termine(art, auswahl, stand, jetzt)
}

// Wert eines Fruehwarnzeichens in der Einheit der Seite.
private fun fwWertText(z: Fruehwarnung.Zeichen): String = when (z.schluessel) {
    Fruehwarnung.ZINSKURVE, Fruehwarnung.SAHM -> (if (z.wert >= 0) "+" else "−") + Fmt.num(abs(z.wert), 2) + " " + S.fwPunkte
    Fruehwarnung.VIX -> Fmt.num(z.wert, 1)
    Fruehwarnung.VIX_STRUKTUR -> Fmt.num(z.wert, 2)
    Fruehwarnung.STRESS -> (if (z.wert >= 0) "+" else "−") + Fmt.num(abs(z.wert), 2)
    Fruehwarnung.HOCHZINS -> Fmt.num(z.wert, 2) + " " + S.fwPunkte
    else -> (if (z.wert >= 0) "+" else "−") + Fmt.pct(abs(z.wert), 1)
}

// Performance: Zeitgewichtete Rendite seit 1.1. und seit einem Jahr, Zinsfuss und groesster Rueckgang seit Beginn aus der
// Wertreihe des Depots wie im Werkzeug; nur im Speicher.
private suspend fun AppState.quellePerformance(jetzt: Long): WidgetKarte? {
    val acc = account.value
    val bew = valuation.value
    if (acc.positions.isEmpty()) return null
    val reihe = depotWertReihe(this, bew).takeIf { it.size >= 2 } ?: return null
    val zone = java.time.ZoneId.systemDefault()
    val jahrAnfang = java.time.LocalDate.now(zone).withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val (ytd, eins, alles) = withContext(Dispatchers.Default) {
        Triple(
            Performanceanalyse.berechnen(reihe, acc.transactions, acc.cash, jahrAnfang, jetzt, zone),
            Performanceanalyse.berechnen(reihe, acc.transactions, acc.cash, jetzt - de.troetenrepublik.app.engine.PortfolioTools.JAHR_MS, jetzt, zone),
            Performanceanalyse.berechnen(reihe, acc.transactions, acc.cash, 0L, jetzt, zone),
        )
    }
    return WidgetKartenBau.performance(
        ytd?.let { it.ttwror * 100 }, eins?.let { it.ttwror * 100 }, alles?.izf?.let { it * 100 }, alles?.maxRueckgang?.let { it * 100 },
        ytd?.kurve?.map { it.second }.orEmpty().let { WidgetZiel.ausduennen(it, 40) }, jetzt,
    )
}

// Crash-Test: der Wert des Depots in den Krisen der Seite (S&P 500 als Vergleich), schlimmster Fall zuerst; nur im Speicher.
private suspend fun AppState.quelleCrashtest(jetzt: Long): WidgetKarte? {
    val items = valuation.value.positions.filter { it.value > 0 }
    if (items.isEmpty()) return null
    val symbole = items.map { it.position.symbol }.distinct()
    val cd = coroutineScope {
        val refD = async { dailyCloses(REFERENCE) }
        val refL = async { longHistory(REFERENCE) }
        val d = symbole.map { s -> s to async { dailyCloses(s) } }
        val l = symbole.map { s -> s to async { longHistory(s) } }
        val refDaily = refD.await()
        val daily = d.associate { (s, a) -> s to a.await() }
        val long = l.associate { (s, a) -> s to a.await() }
        val seit = jetzt - 3L * 365L * 86_400_000L
        val beta = symbole.mapNotNull { s -> de.troetenrepublik.app.engine.PortfolioTools.beta(daily[s].orEmpty(), refDaily, seit)?.let { s to it } }.toMap()
        CrashData(refDaily, refL.await(), daily, long, beta)
    }
    if (cd.refDaily.isEmpty() && cd.refLong.isEmpty()) return null
    val gesamt = items.sumOf { it.value }
    val krisen = de.troetenrepublik.app.engine.PortfolioTools.CRASHES.mapNotNull { crash ->
        val refMove = de.troetenrepublik.app.engine.PortfolioTools.windowReturn(cd.refDaily, crash.from, crash.to)
            ?: de.troetenrepublik.app.engine.PortfolioTools.windowReturn(cd.refLong, crash.from, crash.to) ?: return@mapNotNull null
        val danach = items.sumOf { vp -> shocked(this, vp, moveIn(vp.position.symbol, crash, cd, refMove).first, jetzt) }
        WidgetKartenBau.Krise(S.ptCrashName(crash.key), if (gesamt > 0) danach / gesamt - 1.0 else 0.0)
    }
    return if (krisen.isEmpty()) null else WidgetKartenBau.crashtest(krisen, jetzt)
}
