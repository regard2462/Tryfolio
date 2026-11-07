package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Prognose
import kotlin.math.ln
import kotlin.math.max

// Auffaellige Wetten erfolgreicher Polymarket-Haendler (seit 5.92). Erfolg zaehlt nur in Politik, Wirtschaft und
// Finanzen (Ranglisten der Boerse nach Gewinn, gesamt und im letzten Monat); Sport bleibt bei der Erfolgsmessung aussen
// vor, ein Haendler darf aber nebenbei Sport handeln. Auffaellig ist eine neue Wette eines solchen Haendlers ausserhalb
// des Sports, die gross ist, auf einen unwahrscheinlichen Ausgang setzt oder fuer ihn ungewoehnlich gross ist.
// Das ist ein Hinweis auf Ueberzeugung oder Wissen, kein Beleg; die App zeigt weder Namen noch Wallets.
object Wettprofis {

    // Kategorien der Ranglisten bei Polymarket, in denen Erfolg zaehlt.
    val KATEGORIEN = listOf("POLITICS", "ECONOMICS", "FINANCE")

    // Zeitraeume der Ranglisten.
    val ZEITEN = listOf("ALL", "MONTH")

    // Mindestgewinn in Dollar, damit ein Platz in der Rangliste zaehlt (kleine Ranglisten haben Plaetze mit wenig Gewinn).
    const val MINDEST_GEWINN = 25_000.0

    // Ein Platz in einer Rangliste.
    data class Platz(val wallet: String, val kategorie: String, val zeit: String, val rang: Int, val gewinn: Double, val umsatz: Double)

    // Bester Platz eines Haendlers ueber alle Kategorien und Zeitraeume.
    data class Profi(val wallet: String, val kategorie: String, val zeit: String, val rang: Int, val gewinn: Double) {
        // 1 fuer Platz 1, faellt mit dem Rang bis 0,3
        val gewicht: Double get() = (1.0 - (rang - 1) * 0.015).coerceIn(0.3, 1.0)
    }

    enum class Grund { AUSSENSEITER, GROSS, UNGEWOEHNLICH }

    // Ueber diesem Preis gilt eine Wette nicht als auffaellig.
    const val HOECHSTPREIS = 0.85

    data class Auffaellig(val wette: Prognose.GrosseWette, val profi: Profi, val gruende: List<Grund>, val punkte: Double)

    // Je Wallet der beste Platz (kleinster Rang, bei Gleichstand mehr Gewinn); Plaetze unter dem Mindestgewinn fallen weg.
    fun profis(plaetze: List<Platz>, mindestGewinn: Double = MINDEST_GEWINN): Map<String, Profi> =
        plaetze.filter { it.gewinn >= mindestGewinn && it.wallet.isNotBlank() && it.kategorie in KATEGORIEN }
            .groupBy { it.wallet.lowercase() }
            .mapValues { (w, l) ->
                val b = l.sortedWith(compareBy<Platz> { it.rang }.thenByDescending { it.gewinn }).first()
                Profi(w, b.kategorie, b.zeit, b.rang, l.maxOf { it.gewinn })
            }

    // Auffaellige Wetten: nur Kaeufe ab mindestens Dollar seit ab (ms), nicht im Sport (thema), von Profis, nicht ueber
    // HOECHSTPREIS und nicht von Haendlern, die beide Seiten desselben Markts kaufen. Gruende:
    // Aussenseiter (Preis hoechstens 35 %), gross (ab 50.000 $), ungewoehnlich (mindestens dreimal so gross wie der
    // Median der Kaeufe dieses Haendlers in der Liste, bei mindestens drei Kaeufen). Ohne Grund nicht auffaellig.
    // Punkte: Einsatz (logarithmisch) mal Chance auf Ueberraschung mal Gewicht des Profis, dazu je Grund ein Aufschlag.
    fun auffaellig(
        wetten: List<Prognose.GrosseWette>,
        profis: Map<String, Profi>,
        ab: Long,
        mindestens: Double = 5_000.0,
    ): List<Auffaellig> {
        // Dieselbe Wette in mehreren Teilen zusammenfassen: Einsatz addiert, Preis gewichtet, Zeit des letzten Teils
        val kaeufe = wetten.filter { it.kauf && it.wallet.isNotBlank() && it.zeit >= ab && it.thema != "sports" }
            .groupBy { it.wallet.lowercase() + "|" + it.titel + "|" + it.ausgang }
            .map { (_, l) ->
                val summe = l.sumOf { it.betrag }
                l.maxBy { it.zeit }.copy(betrag = summe, preis = if (summe > 0) l.sumOf { it.preis * it.betrag } / summe else l.first().preis)
            }
            .filter { it.betrag >= mindestens }
        // Wer im Zeitraum beide Seiten desselben Markts kauft, sichert ab oder stellt Kurse: keine Ueberzeugung
        val beideSeiten = kaeufe.groupBy { it.wallet.lowercase() + "|" + it.titel }.filterValues { l -> l.map { it.ausgang }.distinct().size > 1 }.keys
        val median = kaeufe.groupBy { it.wallet.lowercase() }.mapValues { (_, l) ->
            if (l.size < 3) null else l.map { it.betrag }.sorted().let { s -> s[s.size / 2] }
        }
        return kaeufe.mapNotNull { w ->
            val p = profis[w.wallet.lowercase()] ?: return@mapNotNull null
            // Fast sichere Ausgaenge (ueber 85 %) bringen wenig und verraten kein Wissen
            if (w.wallet.lowercase() + "|" + w.titel in beideSeiten || w.preis > HOECHSTPREIS) return@mapNotNull null
            val gruende = buildList {
                if (w.preis in 0.01..0.35) add(Grund.AUSSENSEITER)
                if (w.betrag >= 50_000.0) add(Grund.GROSS)
                median[w.wallet.lowercase()]?.let { m -> if (w.betrag >= 3 * m) add(Grund.UNGEWOEHNLICH) }
            }
            if (gruende.isEmpty()) return@mapNotNull null
            val punkte = ln(max(w.betrag, 1.0)) * (1.0 - w.preis.coerceIn(0.0, 0.99)) * p.gewicht * (1.0 + 0.25 * gruende.size)
            Auffaellig(w, p, gruende, punkte)
        }.sortedByDescending { it.punkte }
    }
}
