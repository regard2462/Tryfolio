package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Gesamt13f
import de.troetenrepublik.app.market.SecInsider

// Konvergenz (0.77): Aktien, bei denen im selben Zeitfenster mindestens zwei von drei Gruppen kaufen. Insider: Kaeufe
// der letzten 90 Tage (verschiedene Personen, Meldetag). Kongress: Kaeufe der letzten 90 Tage nach Meldedatum
// (verschiedene Abgeordnete und Senatoren). Fonds: im letzten Quartal der 13F-Melder mehr Zugaenge als Abgaenge und
// netto Geld zugeflossen. Reine Rechnung aus Daten, die die App schon hat.
object Konvergenz {

    const val TAGE = 90
    private const val TAG_MS = 86_400_000L

    // Je Wert: Zahl der Insider-Kaeufer, der Abgeordneten mit Kauf und der Netto-Kaeufer unter den Fonds (0: Gruppe kauft nicht).
    class Zeile(val symbol: String, val name: String, val insider: Int, val kongress: Int, val fonds: Int) {
        val gruppen: Int get() = listOf(insider, kongress, fonds).count { it > 0 }
    }

    // Kuerzel wie die Kurslisten der App: Aktienklassen BRK.B oder BRK/B werden zu BRK-B.
    fun kuerzel(k: String): String = k.trim().uppercase().replace(Regex("^([A-Z0-9]+)[./]([A-KM-Z])$"), "$1-$2")

    // Verschiedene Personen mit einem Kauf (Meldetag ab bis bis, JJJJ-MM-TT), je Kuerzel, dazu der Name der Firma.
    fun insiderKaeufer(liste: List<SecInsider.Geschaeft>, ab: String, bis: String): Map<String, Pair<Int, String>> =
        liste.filter { it.kauf && it.wert > 0 && it.f.ifBlank { it.d } in ab..bis }.groupBy { kuerzel(it.k) }
            .mapValues { (_, g) -> g.map { it.p.trim().lowercase() }.toSet().size to g.first().e }

    // Verschiedene Abgeordnete mit einem Kauf, gemeldet zwischen abMs und bisMs, je Kuerzel.
    fun kongressKaeufer(handel: List<KongressAuswertung.Handel>, abMs: Long, bisMs: Long): Map<String, Int> =
        handel.filter { it.kauf && it.gemeldet in abMs..bisMs }.groupBy { kuerzel(it.symbol) }.mapValues { (_, g) -> g.map { it.person }.toSet().size }

    // Netto-Kaeufer je Kuerzel: mehr Zugaenge (Aufstocker und neue Halter) als Abgaenge, und netto Geld zugeflossen.
    fun fondsKaeufer(d: Gesamt13f.Datei?): Map<String, Gesamt13f.Wert> =
        d?.werte?.values?.filter { !it.sonder && it.netto > 0 && it.fluss > 0 }?.associateBy { kuerzel(it.kuerzel) }.orEmpty()

    // Erfuellt eine Gruppe: Zahl gleich 0 heisst, sie kauft nicht.
    fun fonds(w: Gesamt13f.Wert?): Int = w?.takeIf { !it.sonder && it.netto > 0 && it.fluss > 0 }?.netto ?: 0

    // Werte mit mindestens zwei Gruppen, drei von drei zuerst, danach die meisten Kaeufer.
    fun liste(insider: Map<String, Pair<Int, String>>, kongress: Map<String, Int>, fonds: Map<String, Gesamt13f.Wert>): List<Zeile> {
        val symbole = insider.keys + kongress.keys + fonds.keys
        return symbole.map { s ->
            Zeile(s, insider[s]?.second?.takeIf { it.isNotBlank() } ?: fonds[s]?.name ?: s, insider[s]?.first ?: 0, kongress[s] ?: 0, fonds[s]?.netto ?: 0)
        }.filter { it.gruppen >= 2 }
            .sortedWith(compareByDescending<Zeile> { it.gruppen }.thenByDescending { it.insider + it.kongress }.thenByDescending { it.fonds }.thenBy { it.symbol })
    }

    // Fenster der Rechnung: Ende ist der letzte Tag der Insider-Daten (hoechstens heute); Beginn 90 Tage davor.
    class Fenster(val ab: String, val bis: String, val abMs: Long, val bisMs: Long)

    fun fenster(ende: java.time.LocalDate): Fenster {
        val ab = ende.minusDays(TAGE.toLong())
        return Fenster(ab.toString(), ende.toString(), ab.toEpochDay() * TAG_MS, (ende.toEpochDay() + 1) * TAG_MS - 1)
    }

    // Die Zeile eines Werts, wenn er auf der Liste steht; fuer die Wertpapierseite.
    fun fuerWert(symbol: String, insider: List<SecInsider.Geschaeft>, kongressPersonen: Int, fonds: Gesamt13f.Wert?, f: Fenster): Zeile? {
        val k = kuerzel(symbol)
        val i = insiderKaeufer(insider.filter { kuerzel(it.k) == k }, f.ab, f.bis)[k]
        val z = Zeile(k, i?.second ?: fonds?.name ?: k, i?.first ?: 0, kongressPersonen, fonds(fonds))
        return z.takeIf { it.gruppen >= 2 }
    }
}
