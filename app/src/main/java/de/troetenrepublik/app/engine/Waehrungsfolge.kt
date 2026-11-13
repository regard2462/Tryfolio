package de.troetenrepublik.app.engine

// Waehrungen, zwischen denen ein Tipp auf einen Betrag oder Kurs weiterschaltet (seit 5.92). Die Folge ist eine Liste
// aus Waehrungskuerzeln und ORIGINAL (die Originalwaehrung: Heimatboerse eines Werts, Waehrung einer fremden Meldung).
// Gespeichert wird sie als Text mit Komma; leer heisst Vorgabe: gewaehlte Waehrung, US-Dollar, Euro, Originalwaehrung.
object Waehrungsfolge {

    const val ORIGINAL = "ORIG"

    // Hoechstens so viele Eintraege, damit das Durchtippen kurz bleibt.
    const val MAX = 6

    fun vorgabe(basis: String): List<String> = listOf(basis, "USD", "EUR", ORIGINAL).distinct()

    // Gespeicherten Text lesen: unbekannte und doppelte Eintraege fallen weg.
    fun lesen(text: String): List<String> =
        text.split(',').map { it.trim().uppercase() }.filter { it == ORIGINAL || it in Regionen.WAEHRUNGEN }.distinct().take(MAX)

    fun schreiben(folge: List<String>): String = folge.distinct().joinToString(",")

    // Wirksame Folge: die eigene, ohne echte Waehrung darin oder leer die Vorgabe.
    fun wirksam(folge: List<String>, basis: String): List<String> =
        if (folge.none { it != ORIGINAL }) vorgabe(basis) else folge.distinct()

    // Echte Waehrungen in der Reihenfolge der Folge (die Stufen der Anzeige).
    fun stufen(folge: List<String>, basis: String): List<String> = wirksam(folge, basis).filter { it != ORIGINAL }

    // Stelle der Originalwaehrung zwischen den Stufen: Zahl der echten Waehrungen davor. Ohne Original: hinter allen.
    fun originalNach(folge: List<String>, basis: String): Int {
        val w = wirksam(folge, basis)
        val i = w.indexOf(ORIGINAL)
        return if (i < 0) w.count { it != ORIGINAL } else w.take(i).count { it != ORIGINAL }
    }

    fun mitOriginal(folge: List<String>, basis: String): Boolean = ORIGINAL in wirksam(folge, basis)

    // Eintrag an Stelle i um schritt verschieben (-1 nach oben, +1 nach unten); ausserhalb der Liste bleibt alles.
    fun verschieben(folge: List<String>, i: Int, schritt: Int): List<String> {
        val j = i + schritt
        if (i !in folge.indices || j !in folge.indices) return folge
        return folge.toMutableList().apply { val x = this[i]; this[i] = this[j]; this[j] = x }
    }

    // Eintrag an- oder abwaehlen. Die letzte echte Waehrung bleibt, angefuegt wird hinten.
    fun umschalten(folge: List<String>, eintrag: String): List<String> = when {
        eintrag in folge -> {
            val ohne = folge - eintrag
            if (eintrag != ORIGINAL && ohne.none { it != ORIGINAL }) folge else ohne
        }
        folge.size >= MAX -> folge
        else -> folge + eintrag
    }

    // Neue gewaehlte Waehrung: Fehlt sie in einer eigenen Folge, kommt sie nach vorn. Die Vorgabe bleibt leer.
    fun mitBasis(folge: List<String>, basis: String): List<String> =
        if (folge.isEmpty() || basis in folge) folge else (listOf(basis) + folge).take(MAX)
}
