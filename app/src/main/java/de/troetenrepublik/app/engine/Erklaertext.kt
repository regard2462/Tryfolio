package de.troetenrepublik.app.engine

// Leichte Auszeichnung der Erklaertexte (0.78). Sie steht im Text selbst, Aufrufer aendern sich nicht:
// - Leerzeile: neuer Absatz mit Abstand
// - Zeile mit „• “: Stichpunkt mit haengendem Einzug; „Begriff: Erklaerung“ hebt den Begriff hervor
// - kurze Zeile mit Doppelpunkt am Ende, gefolgt von weiterem Text: Zwischenueberschrift
// - Zeile „[skala] A | B | C ; grenze=B ; links=… ; rechts=…“: waagrechte Stufenskala mit markierter Grenze
object Erklaertext {
    const val PUNKT = "• "
    const val SKALA = "[skala]"

    // Laengste Zwischenueberschrift und laengster hervorgehobener Begriff, in Zeichen
    const val KOPF_MAX = 40
    const val BEGRIFF_MAX = 32
    private const val BEGRIFF_WOERTER = 4

    sealed interface Block
    data class Absatz(val text: String) : Block
    // begriff ohne Doppelpunkt; text ist der Rest der Zeile ohne Punktzeichen und ohne Begriff
    data class Punkt(val begriff: String?, val text: String) : Block
    data class Kopf(val text: String) : Block
    // grenze: Index der letzten Stufe links der Grenze, -1 ohne Grenze
    data class Skala(val stufen: List<String>, val grenze: Int, val links: String?, val rechts: String?) : Block
    object Luecke : Block

    data class Zahl(val absaetze: Int, val punkte: Int, val koepfe: Int, val skalen: Int, val luecken: Int)

    // Ob ein Text ueberhaupt Auszeichnung traegt; ohne sie bleibt er ein einfacher Text.
    fun ausgezeichnet(text: String): Boolean = text.contains('\n') || text.startsWith(PUNKT) || text.startsWith(SKALA)

    fun zerlegen(text: String): List<Block> {
        val zeilen = text.split('\n')
        val aus = ArrayList<Block>(zeilen.size)
        for ((i, roh) in zeilen.withIndex()) {
            val zeile = roh.trimEnd()
            when {
                zeile.isBlank() -> if (aus.isNotEmpty() && aus.last() !is Luecke) aus.add(Luecke)
                zeile.startsWith(SKALA) -> aus.add(skala(zeile) ?: Absatz(zeile))
                zeile.startsWith(PUNKT) -> aus.add(punkt(zeile.substring(PUNKT.length)))
                istKopf(zeile) && zeilen.drop(i + 1).any { it.isNotBlank() } -> aus.add(Kopf(zeile))
                else -> aus.add(Absatz(zeile))
            }
        }
        while (aus.isNotEmpty() && aus.last() is Luecke) aus.removeAt(aus.size - 1)
        return aus
    }

    fun zaehlen(text: String): Zahl {
        val b = zerlegen(text)
        return Zahl(
            absaetze = b.count { it is Absatz }, punkte = b.count { it is Punkt }, koepfe = b.count { it is Kopf },
            skalen = b.count { it is Skala }, luecken = b.count { it is Luecke },
        )
    }

    private fun istKopf(zeile: String): Boolean =
        zeile.length in 2..KOPF_MAX && zeile.endsWith(":") && zeile.indexOf(". ") < 0

    private fun punkt(inhalt: String): Punkt {
        val p = inhalt.indexOf(": ")
        if (p in 1..BEGRIFF_MAX && p + 2 < inhalt.length) {
            val begriff = inhalt.substring(0, p)
            val ruhig = begriff.none { it == '.' || it == '!' || it == '?' || it == ',' || it == ';' }
            if (ruhig && begriff.trim().split(' ').size <= BEGRIFF_WOERTER) return Punkt(begriff, inhalt.substring(p + 2))
        }
        return Punkt(null, inhalt)
    }

    private fun skala(zeile: String): Skala? {
        val teile = zeile.substring(SKALA.length).split(';').map { it.trim() }
        val stufen = teile.first().split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (stufen.size < 2) return null
        var grenze = -1
        var links: String? = null
        var rechts: String? = null
        for (t in teile.drop(1)) {
            val wert = t.substringAfter('=', "").trim()
            if (wert.isEmpty()) continue
            when (t.substringBefore('=').trim()) {
                "grenze" -> grenze = stufen.indexOf(wert)
                "links" -> links = wert
                "rechts" -> rechts = wert
            }
        }
        // Eine Grenze hinter der letzten Stufe trennt nichts
        if (grenze >= stufen.size - 1) grenze = -1
        return Skala(stufen, grenze, links, rechts)
    }
}
