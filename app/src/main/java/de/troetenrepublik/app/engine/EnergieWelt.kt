package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Oel und Erdgas je Land (Foerderung, Verbrauch) aus den Jahreswerten der U.S. Energy Information Administration
// (International Energy Data, gemeinfrei) und Gasfackeln je Land als Schaetzung der Earth Observation Group (Colorado
// School of Mines, VIIRS Nightfire, CC BY-NC 4.0). Mitgeliefert als assets/welt/energie.json (tools/daten/energie_welt.py).
// Oel in Tausend Barrel am Tag, Erdgas und Fackeln in Milliarden Kubikmetern im Jahr. Jahre ab "ab", eine Reihe je Land.
object EnergieWelt {

    const val DATEI = "welt/energie.json"
    const val EIA_SEITE = "https://www.eia.gov/international/data/world"
    const val EOG_SEITE = "https://eogdata.mines.edu/products/vnf/global_gas_flare.html"
    const val EIA_BEDINGUNGEN = "https://www.eia.gov/about/copyrights_reuse.php"
    const val EOG_BEDINGUNGEN = "https://creativecommons.org/licenses/by-nc/4.0/"

    // c: Landescode (ISO-3), f: Foerderung (Fackelvolumen im Block fackel), v: Verbrauch; Luecken als null
    @Serializable
    class Land(val c: String, val en: String, val de: String = "", val f: List<Double?> = emptyList(), val v: List<Double?> = emptyList()) {
        fun name(deutsch: Boolean) = if (deutsch && de.isNotEmpty()) de else en
    }

    @Serializable
    class Block(val ab: Int, val laender: List<Land> = emptyList()) {
        val jahre: Int get() = laender.maxOfOrNull { it.f.size } ?: 0
        val bis: Int get() = ab + jahre - 1
    }

    @Serializable
    class Datei(val stand: String, val quelle: String = "", val oel: Block? = null, val gas: Block? = null, val fackel: Block? = null)

    private val json = Json { ignoreUnknownKeys = true }

    // Datei lesen; null, wenn sie nicht passt (kein JSON, unsinniges Jahr, Reihen eines Blocks verschieden lang, Verbrauch
    // und Foerderung verschieden lang). Eine Datei ohne Laender ist gueltig, ihr Abschnitt bleibt unsichtbar.
    fun lesen(text: String): Datei? = runCatching {
        val d = json.decodeFromString(Datei.serializer(), text)
        if (Alter.tag(d.stand) == null) return null
        listOfNotNull(d.oel, d.gas, d.fackel).forEach { b ->
            if (b.ab !in 1900..2100) return null
            val n = b.jahre
            if (b.laender.any { it.f.size != n || (it.v.isNotEmpty() && it.v.size != n) || it.c.isEmpty() }) return null
        }
        d
    }.getOrNull()

    val datei: Datei? by lazy { runCatching { lesen(de.troetenrepublik.app.market.Daten.text(DATEI)) }.getOrNull() }

    // Ein Land in einer Rangfolge: Wert im Jahr, Wert im Jahr davor und Anteil an der Summe aller Laender in Prozent
    class Eintrag(val land: Land, val wert: Double, val vorjahr: Double?, val anteil: Double) {
        val aenderung: Double? get() = vorjahr?.takeIf { it > 0 }?.let { (wert / it - 1) * 100 }
    }

    // Rangfolge: absteigend nach Wert im letzten Jahr, in dem mindestens "mindestens" Laender einen Wert haben (so verschiebt ein
    // Jahr mit wenigen Meldungen die Rangfolge nicht). summe: Summe aller Laender in diesem Jahr; jahr: das Jahr.
    class Rang(val jahr: Int, val summe: Double, val eintraege: List<Eintrag>) {
        // Anteil der ersten n Laender an der Summe in Prozent
        fun spitze(n: Int): Double = eintraege.take(n).sumOf { it.anteil }
    }

    fun rang(b: Block, verbrauch: Boolean = false, mindestens: Int = 10): Rang? {
        fun reihe(l: Land) = if (verbrauch) l.v else l.f
        val n = b.jahre
        // Ein Jahr zaehlt erst, wenn fast alle Laender gemeldet haben (die EIA liefert den Verbrauch des Vorjahrs zuerst nur fuer
        // die OECD; ohne China und Indien laege der Anteil der USA bei 45 % statt 20 %)
        val zahl = (0 until n).map { k -> b.laender.count { reihe(it).getOrNull(k) != null } }
        val schwelle = maxOf(minOf(mindestens, b.laender.size).coerceAtLeast(1), ((zahl.maxOrNull() ?: 0) * 0.8).toInt())
        val i = (n - 1 downTo 0).firstOrNull { k -> zahl[k] >= schwelle } ?: return null
        val summe = b.laender.sumOf { reihe(it).getOrNull(i) ?: 0.0 }
        if (summe <= 0) return null
        val liste = b.laender.mapNotNull { l ->
            val w = reihe(l).getOrNull(i)?.takeIf { it > 0 } ?: return@mapNotNull null
            Eintrag(l, w, reihe(l).getOrNull(i - 1), w / summe * 100)
        }.sortedByDescending { it.wert }
        return Rang(b.ab + i, summe, liste)
    }

    // Netto je Land im Jahr der Rangfolge: Foerderung minus Verbrauch; positiv: Ausfuhrland, negativ: Einfuhrland. Laender ohne
    // beide Werte fehlen.
    class Netto(val land: Land, val saldo: Double)

    fun netto(b: Block, jahr: Int): List<Netto> {
        val i = jahr - b.ab
        return b.laender.mapNotNull { l ->
            val f = l.f.getOrNull(i) ?: return@mapNotNull null
            val v = l.v.getOrNull(i) ?: return@mapNotNull null
            Netto(l, f - v)
        }.sortedByDescending { it.saldo }
    }

    // Groesste Ausfuhr- und Einfuhrlaender, je n
    fun ausfuhr(netto: List<Netto>, n: Int): List<Netto> = netto.filter { it.saldo > 0 }.take(n)
    fun einfuhr(netto: List<Netto>, n: Int): List<Netto> = netto.filter { it.saldo < 0 }.sortedBy { it.saldo }.take(n)

    // Gibt es im Block Daten zum Anzeigen?
    fun hat(b: Block?, verbrauch: Boolean = false): Boolean = b != null && b.laender.any { l -> (if (verbrauch) l.v else l.f).any { it != null } }
}
