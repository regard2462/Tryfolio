package de.troetenrepublik.app

import de.troetenrepublik.app.engine.Marktmacht
import de.troetenrepublik.app.engine.ExchangeRates
import de.troetenrepublik.app.engine.FinderBilanz
import de.troetenrepublik.app.market.Catalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Marktmacht aller Katalogwerte aus den Jahresabschluessen im Speicher des Aktienfinders (FinderBilanzSpeicher). Der
// Vergleich laeuft je Branche ueber alle Werte, deren Abschluss auf dem Geraet liegt, nicht nur ueber den gewaehlten
// Markt: so haengt die Stufe eines Werts nicht davon ab, welcher Markt gerade offen ist. Ohne Abruf; was nicht im Speicher
// steht, fehlt. Das Ergebnis bleibt, bis ein neuer Abschluss oder neue Wechselkurse eintreffen.
internal object MarktmachtQuelle {

    private class Stand(val version: Int, val kurse: Int, val katalog: Int, val ergebnis: Map<String, Marktmacht.Ergebnis>)

    @Volatile private var stand: Stand? = null

    fun ergebnisse(fx: Map<String, Double>): Map<String, Marktmacht.Ergebnis> {
        val sp = FinderBilanzSpeicher
        val version = sp.version.get()
        val kurse = fx.hashCode()
        val katalog = Catalog.loaded()?.aktien.orEmpty()
        stand?.takeIf { it.version == version && it.kurse == kurse && it.katalog == katalog.size }?.let { return it.ergebnis }
        val aktien = katalog.distinctBy { it.s }
        val jetzt = System.currentTimeMillis()
        val eingaben = aktien.mapNotNull { a ->
            if (a.i.isBlank()) return@mapNotNull null
            val e = sp.eintraege[a.s]?.takeIf { FinderBilanz.gueltig(it, jetzt) && it.j.isNotEmpty() } ?: return@mapNotNull null
            val waehrung = e.j.lastOrNull()?.waehrung?.ifBlank { null } ?: a.w
            Marktmacht.eingabe(a.s, a.i, e.j, waehrung.takeIf { it.isNotBlank() }?.let { ExchangeRates.rateHaupt(it, fx) })
        }
        val ergebnis = Marktmacht.auswerten(eingaben, aktien.filter { it.i.isNotBlank() }.groupingBy { it.i }.eachCount())
        stand = Stand(version, kurse, katalog.size, ergebnis)
        return ergebnis
    }

    fun fuer(symbol: String, fx: Map<String, Double>): Marktmacht.Ergebnis? = ergebnisse(fx)[symbol]
}

// Marktmacht eines Werts fuer die Wertpapierseite, aus dem Speicher des Aktienfinders; null ohne Grundlage.
suspend fun AppState.marktmachtFuer(symbol: String): Marktmacht.Ergebnis? {
    finderBilanzVorbereiten()
    return withContext(Dispatchers.Default) { MarktmachtQuelle.fuer(symbol, market.fxKurse.value) }
}
