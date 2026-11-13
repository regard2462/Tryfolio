package de.troetenrepublik.app

import de.troetenrepublik.app.data.Range
import de.troetenrepublik.app.data.WidgetKarte
import de.troetenrepublik.app.data.WidgetKartenBau
import de.troetenrepublik.app.data.WidgetVorgabe
import de.troetenrepublik.app.engine.Branchentrends
import de.troetenrepublik.app.engine.HeatmapBeschriftung
import de.troetenrepublik.app.engine.IndexBeitrag
import de.troetenrepublik.app.engine.InsiderTrend
import de.troetenrepublik.app.engine.IvHistorie
import de.troetenrepublik.app.engine.KongressKennzahlen
import de.troetenrepublik.app.engine.OptionsVola
import de.troetenrepublik.app.engine.Optionsanalyse
import de.troetenrepublik.app.engine.Signale
import de.troetenrepublik.app.engine.Themenkoerbe
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.ui.Fmt
import de.troetenrepublik.app.ui.heatmapQuelleTitel
import de.troetenrepublik.app.ui.istUsAktie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Daten der Widgets von 0.78 (Themenkoerbe, Beitrag zum Index, Sektoren, Kongress-Flow, Signale und Volatilitaet eines Werts).
// Gleiche Rechnung wie die Abschnitte der App, nur ohne Oberflaeche; Fristen und Ablage stehen in WidgetKartenQuellen.kt.

// Der Wert eines Widgets ohne gewaehlten Wert: der Standardwert der Einzelwert-Widgets (erste Watchlist, sonst groesste Position).
internal fun AppState.widgetWertOderStandard(variante: String?): String? =
    variante?.takeIf { it.isNotBlank() } ?: widgetEinzelStandard().takeIf { it.isNotBlank() }

// Themenkoerbe: die Aenderung des Tages aus der Sammelabfrage, je Korb der Median und die Zahl steigender Werte.
internal suspend fun AppState.quelleThemen(stand: String, jetzt: Long): WidgetKarte? {
    val kurse = market.snapshot(Themenkoerbe.ALLE_SYMBOLE)
    val aenderung = kurse.mapNotNull { (s, q) -> q.takeIf { it.prevClose > 0 && it.price > 0 }?.let { s to it.sitzungPct } }.toMap()
    if (aenderung.isEmpty()) return null
    val koerbe = Themenkoerbe.KOERBE.mapIndexedNotNull { i, k ->
        val e = Themenkoerbe.auswerten(k, aenderung)
        e.median?.let { WidgetKartenBau.Korb(S.korbName(i), it, e.anzahlSteigend, e.anzahl) }
    }
    return if (koerbe.isEmpty()) null else WidgetKartenBau.themen(koerbe, stand, jetzt)
}

// Beitrag zum Index: dieselbe Heatmap wie auf der Seite (Tagesaenderung, Gewicht nach Boersenwert), die Treiber nach oben und unten.
internal suspend fun AppState.quelleIndexBeweger(variante: String?, stand: String, jetzt: Long): WidgetKarte? {
    val quelle = variante ?: HeatmapQuelle.MARKT
    val werte = heatmap(quelle).takeIf { it.isNotEmpty() } ?: return null
    // Kalt gestartet bringt der Sammelabruf manchmal nur die Kurse des Depots; aus wenigen Werten keinen Index rechnen
    val erwartet = if (quelle == HeatmapQuelle.MARKT) MARKT_KACHELN else Catalog.get().aktien.count { quelle in it.x }
    if (werte.count { it.aenderung != null } < erwartet * 0.6) return null
    val e = IndexBeitrag.berechnen(werte.map { IndexBeitrag.Eingabe(it.symbol, it.name, it.gewicht, it.aenderung) }) ?: return null
    // Statt eines Zahlenkuerzels steht der Name (bei langem Namen sein erstes Wort); ohne Namen das Symbol
    fun treiber(l: List<IndexBeitrag.Beitrag>) = l.map {
        val kandidaten = HeatmapBeschriftung.kandidaten(it.symbol, it.name)
        WidgetKartenBau.Treiber(kandidaten.firstOrNull() ?: it.symbol, it.symbol, it.punkte, kandidaten.getOrNull(1).orEmpty())
    }
    return WidgetKartenBau.indexBeweger(heatmapQuelleTitel(quelle), quelle, e.gesamt, treiber(e.hoch), treiber(e.runter), stand, jetzt)
}

// Sektoren: die Tagesaenderung der Branchen-ETFs aus den Schlusskursen des Reiters Trends (15 Minuten gespeichert).
internal suspend fun AppState.quelleSektoren(stand: String, jetzt: Long): WidgetKarte? {
    val kurse = branchenKurse().takeIf { it.isNotEmpty() } ?: return null
    val deutsch = settingsFlow.value.sprache.code == "de"
    val l = Branchentrends.BRANCHEN.mapNotNull { b ->
        kurse[b.symbol]?.let { Branchentrends.veraenderung(it, 1) }?.let { WidgetKartenBau.Sektor(if (deutsch) b.de else b.en, it) }
    }
    return if (l.isEmpty()) null else WidgetKartenBau.sektoren(l, stand, jetzt)
}

// Kongress-Flow: Kaeufe und Verkaeufe der letzten 90 Tage je Sektor, wie auf der Seite (die Geschaefte sind sechs Stunden im Speicher).
internal suspend fun AppState.quelleKongressFlow(stand: String, jetzt: Long): WidgetKarte? {
    val handel = kongressHandel().takeIf { it.isNotEmpty() } ?: return null
    val fluss = withContext(Dispatchers.Default) {
        Catalog.get()
        KongressKennzahlen.fluss(handel, jetzt, sektor = { k ->
            Catalog.industryOf(k)?.let { S.industryShort(it) } ?: Symbols.find(k)?.sector?.takeIf { it.isNotBlank() }?.let { S.sectorName(it) }
        })
    }
    if (fluss.sektoren.isEmpty()) return null
    return WidgetKartenBau.kongressFlow(
        fluss.sektoren.map { WidgetKartenBau.FlussSektor(it.name, it.seite.kaufSumme, it.seite.verkaufSumme, it.seite.kaeufe, it.seite.verkaeufe) }, stand, jetzt,
    )
}

// Eine Karte ohne Zeilen ersetzt eine vorhandene mit Zeilen nicht: ein Fehler beim Abruf soll sie nicht leeren.
private fun leerOderAlt(vorher: WidgetKarte?, leer: WidgetKarte): WidgetKarte? = if (vorher == null || vorher.zeilen.isEmpty()) leer else null

// Signale eines Werts: dieselben Zeilen wie der Kasten der Wertpapierseite (Insider 90 Tage, Kongress 90 Tage, Hoch- und
// Abstufungen 30 Tage, auffaellige Optionen). Insider aus den Staenden auf dem Geraet, der Rest aus zwischengespeicherten Abrufen.
internal suspend fun AppState.quelleSignale(symbol: String, vorher: WidgetKarte?, stand: String, jetzt: Long): WidgetKarte? {
    runCatching { secOhneNetz() }
    runCatching { bafinLaden(netz = false) }
    val isin = isinFor(symbol)
    if (isin.length == 12) {
        de.troetenrepublik.app.market.EuInsider.landFuerIsin(isin)?.let { runCatching { euLaden(it, netz = false) } }
    }
    val quote = market.snapshot(listOf(symbol))[symbol]
    val usWert = istUsAktie(symbol, quote?.nativeCurrency)
    // Insider und Kongress mit der Rechnung und dem Fenster der Wertpapierseite
    val insider: InsiderTrend.Ergebnis? = de.troetenrepublik.app.ui.signalInsider(sec.value, bafin.value, euInsider.value, weltInsider.value, symbol, isin)
    val kongress = if (!usWert) null else runCatching { de.troetenrepublik.app.ui.signalKongress(this, symbol) }.getOrNull()
    val land = runCatching { profileFor(symbol) }.getOrNull()?.country
    val stufen = runCatching { analystenDaten(symbol, land) }.getOrNull()?.let { Signale.stufungen(it.verlauf.urteile, jetzt) }
    val optionen = if (!usWert) null else runCatching { market.optionskette(symbol) }.getOrNull()
        ?.let { Signale.optionen(Optionsanalyse.auswerten(it.first, it.second)) }

    val zeilen = widgetSignaleZeilen(insider, kongress, stufen, optionen)
    val karte = WidgetKartenBau.signale(symbol, zeilen, stand, jetzt, quote?.name?.takeIf { it.isNotBlank() } ?: de.troetenrepublik.app.market.Symbols.find(symbol)?.name.orEmpty())
    // Fehlt eine Zeile, weil ein Abruf noch keine Antwort hat, bleibt die vollstaendigere Karte bis zu einem Tag stehen
    if (vorher != null && vorher.sprache == de.troetenrepublik.app.i18n.I18n.lang.name && zeilen.size < vorher.zeilen.size &&
        jetzt - vorher.am < 86_400_000L) return null
    return if (zeilen.isNotEmpty()) karte else leerOderAlt(vorher, karte)
}

// Zeilen des Signale-Kastens aus den vier Staenden; die Wertpapierseite und das Widget bauen sie mit dieser Funktion.
internal fun widgetSignaleZeilen(
    insider: InsiderTrend.Ergebnis?, kongress: Signale.Kongress?, stufen: Signale.Stufen?, optionen: Signale.Optionen?,
): List<WidgetKartenBau.Signal> {
    val zeilen = ArrayList<WidgetKartenBau.Signal>()
    insider?.takeIf { it.kaeufer + it.verkaeufer > 0 }?.let { i ->
        zeilen += WidgetKartenBau.Signal(S.sgInsider, S.sgInsiderWert(i.kaeufer, i.verkaeufer), i.verkaufBetrag, i.kaufBetrag)
    }
    kongress?.let { k -> zeilen += WidgetKartenBau.Signal(S.sgKongress, S.sgKaeufeVerkaeufe(k.kaeufe, k.verkaeufe), k.verkaeufe.toDouble(), k.kaeufe.toDouble()) }
    stufen?.let { s -> zeilen += WidgetKartenBau.Signal(S.sgStufungen, S.sgHochAb(s.hoch, s.ab), s.ab.toDouble(), s.hoch.toDouble()) }
    optionen?.let { o ->
        zeilen += WidgetKartenBau.Signal(S.sgOptionen, S.sgOptionenWert(o.anzahl, o.calls, o.puts), o.puts.toDouble(), o.calls.toDouble())
    }
    return zeilen
}

// Die Wertpapierseite hat die Signale eines Werts gerechnet: liegt dazu ein Signale-Widget auf dem Startbildschirm, zeigt es
// denselben Stand wie die Seite (sonst stuende dort bis zu sechs Stunden lang ein aelterer Optionsstand).
internal suspend fun AppState.widgetSignaleAblegen(symbol: String, zeilen: List<WidgetKartenBau.Signal>) {
    if (zeilen.isEmpty()) return
    val schluessel = de.troetenrepublik.app.plattform.Widgets.kartenSchluessel()
    val art = de.troetenrepublik.app.data.WidgetKarten.SIGNALE
    val key = schluessel.firstOrNull { it.equals(WidgetVorgabe.kartenSchluessel(art, symbol), true) }
        ?: art.takeIf { art in schluessel && widgetWertOderStandard(null).equals(symbol, true) }
        ?: return
    val jetzt = System.currentTimeMillis()
    val name = market.snapshot(listOf(symbol))[symbol]?.name?.takeIf { it.isNotBlank() } ?: Symbols.find(symbol)?.name.orEmpty()
    widgetKarteAblegen(key, WidgetKartenBau.signale(symbol, zeilen, S.widgetStand(Fmt.time(jetzt)), jetzt, name))
    widgetsSchieben()
}

// Volatilitaet eines Werts: IV30 gegen realisierte Volatilitaet, Verhaeltnis und IV-Rang wie in der Optionenkarte. Die IV wird
// dabei wie dort in den Tageswerten auf dem Geraet vermerkt.
internal suspend fun AppState.quelleVolatilitaet(symbol: String, vorher: WidgetKarte?, stand: String, jetzt: Long): WidgetKarte? {
    val name = de.troetenrepublik.app.market.Symbols.find(symbol)?.name.orEmpty()
    val leer = WidgetKartenBau.volatilitaet(symbol, null, null, null, null, stand, jetzt, name)
    val kette = runCatching { market.optionskette(symbol) }.getOrNull() ?: return leerOderAlt(vorher, leer)
    val jetztS = jetzt / 1000
    val schluesse = runCatching { candles(symbol, Range.Y1).map { it.close } }.getOrDefault(emptyList())
    val v = OptionsVola.berechnen(kette.first, kette.second, schluesse, jetztS) ?: return leerOderAlt(vorher, leer)
    var rang: Double? = null
    v.iv30?.let { iv ->
        val alt = market.ivVerlauf(symbol)
        val neu = IvHistorie.merken(alt, (jetztS / 86_400L).toInt(), iv)
        if (neu != alt) market.ivMerken(symbol, neu)
        rang = IvHistorie.lage(IvHistorie.lesen(neu), iv)?.rang
    }
    if (v.iv30 == null && v.rv30 == null) return leerOderAlt(vorher, leer)
    return WidgetKartenBau.volatilitaet(symbol, v.iv30, v.rv30, v.verhaeltnis, rang, stand, jetzt, name)
}

// Stillhalter: das Werkzeug in Analytics legt, sobald es seine Werte gerechnet hat, hier die besten ab (nur wenn ein
// Stillhalter-Widget auf dem Startbildschirm liegt). Das Widget zeigt den zuletzt in der App gerechneten Stand.
internal suspend fun AppState.widgetStillhalterMerken(werte: List<WidgetKartenBau.Stillhalt>) {
    if (werte.isEmpty() || de.troetenrepublik.app.data.WidgetKarten.STILLHALTER !in de.troetenrepublik.app.plattform.Widgets.arten()) return
    widgetStillhaltMerken(werte)
    val jetzt = System.currentTimeMillis()
    val karte = WidgetKartenBau.stillhalter(werte, S.widgetStand(Fmt.time(jetzt)), jetzt)
    widgetKarteAblegen(de.troetenrepublik.app.data.WidgetKarten.STILLHALTER, karte)
    widgetsSchieben()
}
