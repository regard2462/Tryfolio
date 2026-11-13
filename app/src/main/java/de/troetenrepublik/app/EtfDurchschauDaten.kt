package de.troetenrepublik.app

import de.troetenrepublik.app.engine.Durchschau
import de.troetenrepublik.app.engine.EtfLaender
import de.troetenrepublik.app.engine.EtfStreuung
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.Daten
import de.troetenrepublik.app.market.Symbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Laender eines Fonds fuer die Durchschau auf seiner Seite, ohne Abruf: Verteilung des abgebildeten Index aus dem Buendel
// (wie im Globus und im Depot-Check), sonst der Sitz der genannten Positionen aus dem Katalog. null ohne beides.
suspend fun AppState.etfLaenderDurchschau(symbol: String, positionen: List<Durchschau.Position>): EtfStreuung.Laender? {
    val daten = withContext(Dispatchers.Default) { EtfLaender.lesen(Daten.text("globus/etf-laender.json")) }
    val katalog = withContext(Dispatchers.IO) { Catalog.get() }
    val c = katalog.etfs.firstOrNull { it.s == symbol }
    val name = c?.n ?: Symbols.find(symbol)?.name.orEmpty()
    EtfStreuung.laenderAusIndex(daten.verteilung(EtfLaender.schluessel(name, c?.x, c?.r, c?.i, daten)))?.let { return it }
    val sitz = katalog.aktien.associate { it.s to it.c }
    return EtfStreuung.laenderAusPositionen(positionen) { s -> EtfStreuung.sitz(s, sitz[s]) }
}

// Landrisiko-Punkte der genannten Laender. Die Fakten kommen aus dem Sammelabruf, den Aktienfinder und Abhaengigkeiten
// schon nutzen (ein halber Tag gueltig, fuer alle Laender zusammen); leer, solange er nichts liefert.
suspend fun AppState.etfLandPunkte(laender: List<String>): Map<String, Double> {
    finderLandRisiko()
    return laender.mapNotNull { a2 -> finderLandPunkte(a2)?.let { a2 to it } }.toMap()
}
