package de.troetenrepublik.app.engine

// [0.78] Streuung eines Fonds aus der Durchschau: Anteil der groessten Positionen, Einordnung nach festen Schwellen,
// Laenderanteile, Anteil in Laendern mit hohem Landrisiko und Waehrungen. Die Quelle nennt hoechstens die zehn
// groessten Positionen; was nur mit allen Gewichten stimmt (Zahl der Positionen, wirksame Zahl), gibt es nur, wenn
// die genannten Positionen den Fonds fast ganz ausmachen.
object EtfStreuung {
    // Anteil der genannten groessten Positionen, ab dem ein Fonds als konzentriert und als stark konzentriert gilt
    const val KONZENTRIERT_AB = 0.30
    const val STARK_AB = 0.50

    // Ab dieser Summe gelten die genannten Positionen als der ganze Fonds
    const val VOLLSTAENDIG_AB = 0.995

    // Summen ueber diesem Wert sind keine Anteile eines Ganzen (gehebelte Fonds, fehlerhafte Angaben)
    const val SUMME_HOECHSTENS = 1.005

    // Landrisiko-Punkte (LandRisiko.punkte) unter diesem Wert sind die Stufe "hoch" (Kernaussagen.stufe)
    const val RISIKO_UNTER = 50.0

    // Listen bis zu dieser Laenge stehen ganz da, laengere zeigen zuerst LISTE_ZUERST Zeilen
    const val LISTE_GANZ_BIS = 6
    const val LISTE_ZUERST = 5

    enum class Stufe { BREIT, KONZENTRIERT, STARK }

    // top: Summe der genannten Positionen; rest: was der Fonds darueber hinaus haelt (0 bei vollstaendiger Liste);
    // anzahl und wirksam nur bei vollstaendiger Liste.
    data class Kennzahlen(
        val genannt: Int, val top: Double, val rest: Double, val groesste: Durchschau.Position,
        val anzahl: Int?, val wirksam: Double?, val stufe: Stufe,
    )

    fun stufe(top: Double): Stufe = when {
        top >= STARK_AB -> Stufe.STARK
        top >= KONZENTRIERT_AB -> Stufe.KONZENTRIERT
        else -> Stufe.BREIT
    }

    // Wirksame Zahl der Positionen: 1 durch die Summe der quadrierten Gewichte (auf Summe 1 gebracht)
    fun wirksameZahl(gewichte: List<Double>): Double? {
        val g = gewichte.filter { it.isFinite() && it > 0 }
        val s = g.sum()
        if (g.isEmpty() || s <= 0) return null
        return 1.0 / g.sumOf { (it / s) * (it / s) }
    }

    // null ohne Positionen und bei Summen, die kein Anteil eines Ganzen sind
    fun kennzahlen(positionen: List<Durchschau.Position>): Kennzahlen? {
        val p = positionen.filter { it.anteil.isFinite() && it.anteil > 0 }.sortedByDescending { it.anteil }
        if (p.isEmpty()) return null
        val top = p.sumOf { it.anteil }
        if (top > SUMME_HOECHSTENS) return null
        val voll = top >= VOLLSTAENDIG_AB
        return Kennzahlen(
            genannt = p.size, top = top.coerceAtMost(1.0), rest = if (voll) 0.0 else 1.0 - top, groesste = p.first(),
            anzahl = if (voll) p.size else null, wirksam = if (voll) wirksameZahl(p.map { it.anteil }) else null, stufe = stufe(top),
        )
    }

    // Abschnitte einer gestapelten Leiste: je Position ihr Anteil, groesste zuerst, am Ende der Rest. Summe 1.
    fun leiste(k: Kennzahlen, positionen: List<Durchschau.Position>): List<Double> {
        val p = positionen.map { it.anteil }.filter { it.isFinite() && it > 0 }.sortedDescending()
        val summe = p.sum() + k.rest
        if (summe <= 0) return emptyList()
        return (p + listOfNotNull(k.rest.takeIf { it > 0 })).map { it / summe }
    }

    // Wie viele Zeilen einer Liste zuerst zu sehen sind
    fun zuerst(n: Int): Int = if (n <= LISTE_GANZ_BIS) n else LISTE_ZUERST

    // ------------------------------------------------------------ Laender

    data class Land(val a2: String, val anteil: Double)

    // ausIndex: Verteilung des abgebildeten Index (Summe 1). Sonst aus dem Sitz der genannten Positionen, als Anteil
    // am Fonds; abgedeckt ist dann der Anteil des Fonds, dem ein Land zugeordnet wurde.
    data class Laender(val liste: List<Land>, val ausIndex: Boolean, val abgedeckt: Double)

    fun laenderAusIndex(verteilung: Map<String, Double>?): Laender? {
        val l = verteilung.orEmpty().filter { it.value.isFinite() && it.value > 0 }
        val s = l.values.sum()
        if (l.isEmpty() || s <= 0) return null
        return Laender(l.map { Land(it.key, it.value / s) }.sortedWith(compareByDescending<Land> { it.anteil }.thenBy { it.a2 }), true, 1.0)
    }

    // Sitz einer Position: Land aus dem Katalog, sonst das Land der Boerse, wenn das Kuerzel eine Boersenendung traegt.
    // Kuerzel ohne Endung ausserhalb des Katalogs bleiben ohne Land (an US-Boersen stehen auch fremde Firmen).
    fun sitz(symbol: String, katalogLand: String?): String? =
        katalogLand?.takeIf { it.isNotBlank() } ?: if (symbol.contains('.')) Taetigkeit.boerse(symbol) else null

    fun laenderAusPositionen(positionen: List<Durchschau.Position>, land: (String) -> String?): Laender? {
        val je = HashMap<String, Double>()
        for (p in positionen) {
            if (!p.anteil.isFinite() || p.anteil <= 0) continue
            val a2 = land(p.symbol) ?: continue
            je[a2] = (je[a2] ?: 0.0) + p.anteil
        }
        if (je.isEmpty()) return null
        val liste = je.map { Land(it.key, it.value) }.sortedWith(compareByDescending<Land> { it.anteil }.thenBy { it.a2 })
        return Laender(liste, false, liste.sumOf { it.anteil })
    }

    // ------------------------------------------------------------ Landrisiko

    // anteil: Summe der Laender mit hohem Landrisiko; bewertet: Anteil der Laender, zu denen Punkte vorliegen.
    data class Risiko(val anteil: Double, val laender: List<Land>, val bewertet: Double)

    // punkte: Landrisiko-Punkte je Land (0 bis 100, mehr ist besser); null ohne Punkte fuer irgendein Land
    fun risiko(laender: Laender, punkte: (String) -> Double?): Risiko? {
        var bewertet = 0.0
        val hoch = ArrayList<Land>()
        for (l in laender.liste) {
            val p = punkte(l.a2) ?: continue
            bewertet += l.anteil
            if (p < RISIKO_UNTER) hoch += l
        }
        if (bewertet <= 0) return null
        return Risiko(hoch.sumOf { it.anteil }, hoch, bewertet)
    }

    // ------------------------------------------------------------ Waehrungen

    // Anteile je Waehrung aus den Laendern (Land zu Waehrungskuerzel), groesste zuerst; Laender ohne Zuordnung fehlen
    fun waehrungen(laender: Laender, waehrung: (String) -> String?): List<Pair<String, Double>> {
        val je = HashMap<String, Double>()
        for (l in laender.liste) {
            val w = waehrung(l.a2) ?: continue
            je[w] = (je[w] ?: 0.0) + l.anteil
        }
        return je.toList().sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })
    }
}
