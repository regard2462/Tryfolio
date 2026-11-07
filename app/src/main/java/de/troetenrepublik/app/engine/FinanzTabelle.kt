package de.troetenrepublik.app.engine

// Reine Hilfsfunktionen fuer Quartalsbeschriftung und Tabellenaufbau im Blatt Finanzen (0.68)
object FinanzTabelle {

    private val datumMuster = Regex("""(\d{4})-(\d{2})-(\d{2})""")
    private val quartalMuster = Regex("""^([1-4])Q(\d{4})$""")

    // Macht aus einem Label eine kurze Periode, z. B. "2026-09-30" -> "Q3 2026"
    fun periode(label: String): String {
        val text = label.trim()
        val datum = datumMuster.find(text)
        if (datum != null) {
            val jahr = datum.groupValues[1]
            val monat = datum.groupValues[2].toInt()
            if (monat in 1..12) return "Q${(monat - 1) / 3 + 1} $jahr"
            return text
        }
        val quartal = quartalMuster.find(text)
        if (quartal != null) return "Q${quartal.groupValues[1]} ${quartal.groupValues[2]}"
        return text
    }

    // Prueft, ob alle Spalten mit Abstand in die verfuegbare Breite passen
    fun spaltenPassen(breiten: List<Int>, abstand: Int, verfuegbar: Int): Boolean {
        if (breiten.isEmpty()) return true
        return breiten.sum() + abstand * (breiten.size - 1) <= verfuegbar
    }

    // Bestimmt, wie viele Rasterzellen nebeneinander passen (mindestens 1)
    fun rasterSpalten(zellBreite: Int, abstand: Int, verfuegbar: Int, max: Int = 2): Int {
        var beste = 1
        for (k in 1..max) {
            if (k * zellBreite + (k - 1) * abstand <= verfuegbar) beste = k
        }
        return beste
    }
}
