package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Daten

// [Grosses Ganzes 0.73] Einzige Zugriffsstelle fuer die gepflegten Dateien des Einflussverzeichnisses (assets/welt).
// Die Dateien kommen ueber den gemeinsamen Zugriff auf gepflegte Dateien (Daten): die neuere von eingebauter Datei und
// geprueftem Stand aus dem signierten Datenbuendel.
object WeltDaten {
    const val EINFLUSS = "welt/einfluss.json"
    const val ROHSTOFFE = "welt/rohstoffe.json"
    const val ANLAGEN = "welt/anlagen.json"

    // Text der Datei; null, wenn sie fehlt oder nicht lesbar ist
    fun text(name: String): String? = runCatching { Daten.text(name) }.getOrNull()
}
