package de.troetenrepublik.app.market

// [Globus Boersen 0.73] Zusaetzliche direkte Boersenquellen. In dieser Fassung leer; eine andere Fassung kann diese Datei durch
// eine Liste ersetzen, ohne BoersenDirekt oder den Lader anzufassen. Die Eintraege folgen demselben Aufbau wie
// BoersenDirekt.QUELLEN (Laender, Adresse, Methode, Parser, Zeitlimit); sie stehen in der Reihenfolge nach den
// eingebauten und ersetzen keine eingebaute Quelle eines Landes, solange diese frische Werte liefert.
object BoersenZusatz {
    val QUELLEN: List<BoersenDirekt.Quelle> = emptyList()
}
