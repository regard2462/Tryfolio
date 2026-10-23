package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.Candle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.TreeMap

// Vorberechneter Verlauf des Praesidenten, beim Bau aus allen gemeldeten Geschaeften gerechnet (tools/praesident):
// Jahresmeldung 2025 (Teil 7, ueber Namen zugeordnet) und alle Einzelmeldungen (278-T) mit Kursen aller Aktien und ETFs.
// Rechnung wie Portfolioverlauf.kongress, die App haengt ab dem Stichtag nur die Kurse des Bestands an.
object Praesident {

    @Serializable
    data class Datei(
        val format: Int = 0,
        val stand: String = "",
        // Letzter Tag mit gemeldeten Geschaeften (JJJJ-MM-TT).
        val stichtag: String = "",
        // Handelstage als Tage seit 1970 (UTC) und der Index (Beginn 100) dazu.
        val tage: List<Long> = emptyList(),
        val werte: List<Double> = emptyList(),
        // Kaeufe und Verkaeufe je Tag mit den groessten Werten (Kuerzel und geschaetzter Betrag in Dollar).
        val marken: List<Marke> = emptyList(),
        // Bestand am Stichtag: Kuerzel und Anteil am Wert der Aktien und ETFs (Summe 1).
        val bestand: List<Anteil> = emptyList(),
        // Anteil der Anleihen ohne Kurs am ganzen Depot am Stichtag (0 bis 1), nach den gemeldeten Spannen.
        val anleihen: Double = 0.0,
        val zahlen: Zahlen = Zahlen(),
    )

    @Serializable
    data class Marke(val t: Long, val kauf: Boolean, val n: Int, val w: List<Anteil> = emptyList())

    @Serializable
    data class Anteil(val k: String, val g: Double)

    @Serializable
    data class Zahlen(
        val geschaefte: Int = 0,
        val jahresmeldung: Int = 0,
        val einzelmeldungen: Int = 0,
        // Davon im Verlauf: Aktien, ETFs und Optionen (als Basiswert) mit Kurs.
        val eingerechnet: Int = 0,
        // Anleihen, Vorzugsaktien, Einlagen: ohne Tageskurs, als eigener Block neben dem Verlauf.
        val anleihen: Int = 0,
        // Aktien und Fonds, deren Name sich keinem Kuerzel zuordnen liess.
        val ohneKuerzel: Int = 0,
        // Mit Kuerzel, aber ohne Kurse bei Yahoo (etwa nicht mehr gehandelt).
        val ohneKurs: Int = 0,
        // Verkaeufe ohne vorherigen Kauf im Zeitraum.
        val ohneBestand: Int = 0,
        val werte: Int = 0,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun lesen(text: String): Datei? = runCatching { json.decodeFromString<Datei>(text) }.getOrNull()
        ?.takeIf { it.format == 1 && it.tage.size >= 2 && it.tage.size == it.werte.size }

    fun reihe(d: Datei): Reihe = d.tage.indices.map { Portfolioverlauf.ms(d.tage[it]) to d.werte[it] }

    // Die Werte des Bestands, die zusammen mindestens deckung des Werts ausmachen (groesste zuerst), hoechstens max.
    fun fortschreibWerte(d: Datei, deckung: Double = 0.95, max: Int = 300): List<String> {
        var summe = 0.0
        val aus = ArrayList<String>()
        for (a in d.bestand.sortedByDescending { it.g }) {
            if (summe >= deckung || aus.size >= max) break
            aus += a.k
            summe += a.g
        }
        return aus
    }

    // Vom Stichtag bis heute fortschreiben: Der Bestand am Stichtag bleibt gehalten, der Index aendert sich mit dem
    // gewichteten Kursverhaeltnis (Tag zu Stichtag). Werte ohne Kurse fallen heraus, die uebrigen zaehlen anteilig.
    // Liefert nur die Punkte nach dem Stichtag.
    fun fortschreiben(d: Datei, kurse: Map<String, List<Candle>>): Reihe {
        val stich = d.tage.last()
        val basis = d.werte.last()
        val start = HashMap<String, Double>()
        val karten = HashMap<String, TreeMap<Long, Double>>()
        for (a in d.bestand) {
            val m = kurse[a.k]?.let { Portfolioverlauf.karte(it) } ?: continue
            val s = Portfolioverlauf.kursAm(m, stich) ?: continue
            start[a.k] = s
            karten[a.k] = m
        }
        val mit = d.bestand.filter { it.k in start }
        val gesamt = mit.sumOf { it.g }
        if (gesamt <= 0.0) return emptyList()
        val tage = karten.values.flatMap { it.tailMap(stich, false).keys }.toSortedSet()
        return tage.map { t ->
            val summe = mit.sumOf { a -> a.g * (Portfolioverlauf.kursAm(karten.getValue(a.k), t) ?: start.getValue(a.k)) / start.getValue(a.k) }
            Portfolioverlauf.ms(t) to basis * summe / gesamt
        }
    }

    // Zeitraum fuer den Kursabruf, der den Stichtag sicher enthaelt.
    fun zeitraum(stichtag: Long, heute: Long): String {
        val tage = heute - stichtag
        return when {
            tage <= 80 -> "3mo"
            tage <= 170 -> "6mo"
            tage <= 350 -> "1y"
            else -> "2y"
        }
    }

    // Je Marke die groessten Werte mit ihrem Betrag.
    fun handel(d: Datei): List<Portfolioverlauf.Handel> = d.marken.flatMap { m ->
        m.w.map { a -> Portfolioverlauf.Handel(Portfolioverlauf.ms(m.t), a.k, a.k, m.kauf, a.g, ganz = false, option = false) }
    }
}
