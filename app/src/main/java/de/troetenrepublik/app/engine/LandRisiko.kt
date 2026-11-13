package de.troetenrepublik.app.engine

// [0.73] Politisches und strukturelles Risiko eines Landes als Punktzahl von 0 bis 100 fuer den Aktienfinder
// (50 = unauffaellig, mehr = besser). Grundlage ist der Durchschnitt der sechs Weltbank-Indikatoren fuer
// Regierungsfuehrung (Stimmrechte, Politische Stabilitaet, Wirksamkeit, Regulierungsqualitaet, Rechtsstaat,
// Korruptionsbekaempfung) als Perzentil aller Laender; Abzuege geben Sanktionen, bewaffnete Konflikte,
// Reisehinweise des US-Aussenministeriums und strukturelle Probleme aus der Weltkarte. Alles Zahlen, die die
// App fuer die Laenderblaetter ohnehin laedt; eine Bewertung, keine Anlageberatung.
object LandRisiko {

    // Was ueber das Land bekannt ist; null oder 0 heisst keine Angabe.
    data class Fakten(
        // Durchschnitt der sechs WGI-Perzentile 0 bis 100
        val wgi: Double? = null,
        // Perzentil „Politische Stabilitaet“ allein. Es misst Gewalt und Konflikte schon mit; mit Konfliktabzug zaehlt der
        // Durchschnitt ohne diesen Indikator, damit ein Konflikt nicht doppelt wirkt.
        val stabilitaet: Double? = null,
        // Anzahl der Sanktionsregime der EU und der Programme der USA gegen das Land
        val sanktionenEu: Int = 0,
        val sanktionenUs: Int = 0,
        // Treffer im OpenSanctions-Register mit Sitz im Land
        val openSanctions: Int? = null,
        // Schwerster bewaffneter Konflikt, der im Land stattfindet oder das Land angreift (UCDP: 1 bewaffnet, 2 und mehr
        // Krieg); siehe konfliktStufe
        val konflikt: Int? = null,
        // Reisehinweis des US-Aussenministeriums 1 bis 4 (4 = nicht einreisen)
        val reise: Int? = null,
        // Zahl der strukturellen Probleme aus strukturen.json
        val strukturen: Int = 0,
    )

    // Hoechste Stufe der Konflikte, die das Land selbst betreffen: sie finden im Land statt oder das Land wird angegriffen.
    // Steht die Regierung des Landes nur als eine von mehreren in einer Koalition auf einer Seite (die USA und das
    // Vereinigte Koenigreich gegen den Jemen), ist das Beteiligung an einem fremden Konflikt und zaehlt nicht.
    // namen: die Schreibweisen des Landes in Laenderrisiken.norm (Laenderrisiken.schluessel); null ohne Konflikte.
    fun konfliktStufe(konflikte: List<Konflikte.Konflikt>, namen: List<String>): Int? {
        fun regierungen(seite: String): List<String> = seite.split("Government of ").map { it.trim(' ', ',') }.filter { it.isNotEmpty() }
        fun gemeint(r: String) = Laenderrisiken.norm(r) in namen || Laenderrisiken.norm(r.substringBefore('(').trim()) in namen
        return konflikte.filter { k ->
            val seiten = listOf(regierungen(k.seiteA), regierungen(k.seiteB))
            val eigene = seiten.firstOrNull { s -> s.any { gemeint(it) } }
            eigene == null || eigene.size == 1
        }.maxOfOrNull { it.stufe }
    }

    // Punkte aus den Fakten; null, wenn ueber nichts bekannt ist. Die Abzuege:
    // 10 je Sanktionsliste (EU, USA), bis 10 fuer Sanktionstreffer im Register, 10 fuer bewaffnete Konflikte
    // und 20 fuer Krieg, 10 fuer die Reisewarnung der hoechsten Stufe und 5 fuer die darunter, 3 je strukturellem
    // Problem bis zu drei.
    fun punkte(f: Fakten): Double? {
        if (f.wgi == null && f.sanktionenEu == 0 && f.sanktionenUs == 0 && f.openSanctions == null &&
            f.konflikt == null && f.reise == null && f.strukturen == 0
        ) return null
        var p = f.wgi ?: 50.0
        if ((f.konflikt ?: 0) > 0 && f.wgi != null && f.stabilitaet != null) p = (f.wgi * 6.0 - f.stabilitaet) / 5.0
        if (f.sanktionenEu > 0) p -= 10.0
        if (f.sanktionenUs > 0) p -= 10.0
        val os = f.openSanctions ?: 0
        if (os >= 50) p -= 10.0 else if (os >= 5) p -= 5.0
        when (f.konflikt ?: 0) {
            0 -> {}
            1 -> p -= 10.0
            else -> p -= 20.0
        }
        when (f.reise) {
            4 -> p -= 10.0
            3 -> p -= 5.0
            else -> {}
        }
        p -= 3.0 * f.strukturen.coerceIn(0, 3)
        return p.coerceIn(0.0, 100.0)
    }

    // [Standorte] Klar riskantes Land: nur harte Befunde, nicht die Punktzahl. Die Punktzahl ist durch Abzuege, die viele
    // Laender treffen (Register-Treffer, strukturelle Probleme), eng zusammengedrueckt und trennt Schwellenlaender wie
    // Indien, Brasilien oder Mexiko nicht von Sanktions- und Kriegslaendern. Hoch ist ein Land, wenn
    // - die Regierungsfuehrung (WGI-Durchschnitt) unter 25 liegt, also im untersten Viertel der Welt,
    // - Krieg im Land herrscht (Konflikt der Stufe 2) und die Regierungsfuehrung unter dem Weltmedian liegt,
    // - der Reisehinweis der USA die hoechste Stufe 4 hat oder
    // - mindestens drei Sanktionsregime der EU und Programme der USA gegen das Land laufen.
    // Ohne jede Angabe zum Land null.
    const val HOCH_WGI_UNTER = 25.0
    const val HOCH_KRIEG_WGI_UNTER = 50.0
    const val HOCH_SANKTIONEN_AB = 3

    fun hoch(f: Fakten): Boolean? {
        if (punkte(f) == null) return null
        val w = f.wgi
        return (w != null && w < HOCH_WGI_UNTER) ||
            ((f.konflikt ?: 0) >= 2 && (w == null || w < HOCH_KRIEG_WGI_UNTER)) ||
            f.reise == 4 ||
            f.sanktionenEu + f.sanktionenUs >= HOCH_SANKTIONEN_AB
    }
}
