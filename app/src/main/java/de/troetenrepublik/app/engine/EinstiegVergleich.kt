package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

// Sparplan-Rueckblick (0.78): zwei Fragen zum Einstieg, auf Monatsende-Kursen gerechnet.
// 1. Ein Betrag sofort ganz oder in gleichen Monatsraten, ueber alle Startmonate der Kursreihe.
// 2. Jeden Monat kaufen oder die Rate sammeln und erst bei einem Abstand zum Hoch kaufen.
// Wartendes Geld bleibt unverzinst. Kosten wie beim Handel in der App: jeder Kauf zum Briefkurs, die einzelne Order
// kostet die Ordergebuehr, die Ausfuehrung eines Sparplans nichts.
object EinstiegVergleich {

    // Weniger Startmonate ergeben keine Verteilung.
    const val MIN_ZEITRAEUME = 12

    // spreadBps: ganzer Abstand von Geld- und Briefkurs in Basispunkten, wie in den Einstellungen.
    data class Kosten(val spreadBps: Double = 0.0, val order: Double = Trading.FEE, val sparplan: Double = 0.0)

    data class Monat(val t: Long, val kurs: Double)

    // Letzter Schlusskurs je Kalendermonat, nach Zeit sortiert. Ein Monat ohne Kurs bekommt den Stand des Vormonats,
    // damit jeder Schritt genau ein Monat ist. Der laufende Monat steht mit seinem letzten Kurs am Ende.
    fun monatsenden(daily: List<Candle>, zone: ZoneId = ZoneId.systemDefault()): List<Monat> {
        val aus = ArrayList<Monat>()
        var letzter = Int.MIN_VALUE
        for (c in daily.filter { it.close > 0 && it.close.isFinite() }.sortedBy { it.t }) {
            val ym = YearMonth.from(Instant.ofEpochMilli(c.t).atZone(zone))
            val nr = ym.year * 12 + ym.monthValue
            if (nr == letzter) { aus[aus.size - 1] = Monat(c.t, c.close); continue }
            if (aus.isNotEmpty()) repeat(nr - letzter - 1) { aus += aus.last() }
            aus += Monat(c.t, c.close)
            letzter = nr
        }
        return aus
    }

    // Stueckzahl fuer einen Betrag: Gebuehr vorab, Kauf zum Briefkurs.
    fun stueck(betrag: Double, kurs: Double, gebuehr: Double, spreadBps: Double): Double {
        val brief = Pricing.ask(kurs, spreadBps)
        return if (betrag <= gebuehr || brief <= 0) 0.0 else (betrag - gebuehr) / brief
    }

    fun median(werte: List<Double>): Double {
        val s = werte.sorted()
        return if (s.isEmpty()) 0.0 else if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    // 1. Einmalanlage gegen Raten

    // start: Index des Startmonats. vorsprung: Endwert der Einmalanlage gegen den der Raten in Prozent.
    // rueckgang: groesster Rueckgang des Wegs vom eigenen Hoch in Prozent (negativ oder 0), Bargeld zaehlt mit.
    data class Fall(
        val start: Int, val einmal: Double, val raten: Double, val vorsprung: Double,
        val rueckgangEinmal: Double, val rueckgangRaten: Double,
    )

    data class Verteilung(
        val faelle: List<Fall>, val vorn: Int, val mittel: Double, val median: Double,
        val schlechtester: Fall, val bester: Fall, val rueckgangEinmal: Double, val rueckgangRaten: Double,
    ) {
        val anteil: Double get() = vorn * 100.0 / faelle.size
    }

    // betrag sofort ganz (eine Order) oder in raten gleichen Teilen in den Monaten start bis start + raten - 1
    // (Sparplan). Verglichen wird der Stand im Monat start + horizont, horizont mindestens raten. Je Startmonat ein
    // Fall; null bei weniger als MIN_ZEITRAEUME Faellen.
    fun einmalGegenRaten(kurse: List<Double>, betrag: Double, raten: Int, horizont: Int = raten, kosten: Kosten = Kosten()): Verteilung? {
        if (betrag <= 0 || raten < 1 || horizont < raten) return null
        val anzahl = kurse.size - horizont
        if (anzahl < MIN_ZEITRAEUME) return null
        val teil = betrag / raten
        val faelle = ArrayList<Fall>(anzahl)
        val wegEinmal = DoubleArray(horizont + 1)
        val wegRaten = DoubleArray(horizont + 1)
        for (s in 0 until anzahl) {
            val einmalStueck = stueck(betrag, kurse[s], kosten.order, kosten.spreadBps)
            var ratenStueck = 0.0
            var bar = betrag
            for (m in 0..horizont) {
                val k = kurse[s + m]
                if (m < raten) { ratenStueck += stueck(teil, k, kosten.sparplan, kosten.spreadBps); bar -= teil }
                wegEinmal[m] = einmalStueck * k
                wegRaten[m] = ratenStueck * k + if (m < raten - 1) bar else 0.0
            }
            val e = wegEinmal[horizont]
            val r = wegRaten[horizont]
            if (e <= 0 || r <= 0) continue
            faelle += Fall(s, e, r, (e / r - 1.0) * 100.0, Analysis.maxDrawdown(wegEinmal.asList()), Analysis.maxDrawdown(wegRaten.asList()))
        }
        if (faelle.size < MIN_ZEITRAEUME) return null
        val v = faelle.map { it.vorsprung }
        return Verteilung(
            faelle = faelle, vorn = faelle.count { it.vorsprung > 0 }, mittel = v.average(), median = median(v),
            schlechtester = faelle.minBy { it.vorsprung }, bester = faelle.maxBy { it.vorsprung },
            rueckgangEinmal = median(faelle.map { it.rueckgangEinmal }), rueckgangRaten = median(faelle.map { it.rueckgangRaten }),
        )
    }

    // Klassen gleicher Breite fuer die Verteilung; die Grenzen liegen auf Vielfachen der Breite, null ist also immer
    // eine Grenze. von: untere Grenze der Klasse.
    data class Klasse(val von: Double, val anzahl: Int)

    data class Histogramm(val breite: Double, val klassen: List<Klasse>)

    fun histogramm(werte: List<Double>, zielKlassen: Int = 16): Histogramm? {
        val gut = werte.filter { it.isFinite() }
        if (gut.isEmpty()) return null
        // Liegen alle Werte fast gleich, gilt eine Mindestspanne
        val spanne = maxOf(gut.max() - gut.min(), 0.1)
        val roh = spanne / zielKlassen
        val zehner = 10.0.pow(floor(log10(roh)))
        val breite = listOf(1.0, 2.0, 5.0, 10.0).map { it * zehner }.first { it >= roh }
        val erste = floor(gut.min() / breite).toInt()
        val letzte = floor(gut.max() / breite).toInt()
        val zahl = IntArray(letzte - erste + 1)
        gut.forEach { zahl[(floor(it / breite).toInt() - erste).coerceIn(0, zahl.size - 1)]++ }
        return Histogramm(breite, zahl.mapIndexed { i, n -> Klasse((erste + i) * breite, n) })
    }

    // 2. Ruecksetzer kaufen gegen stur sparen

    // Hoechster Kurs bis einschliesslich jedes Monats.
    fun hochs(kurse: List<Double>): List<Double> {
        var h = 0.0
        return kurse.map { if (it > h) h = it; h }
    }

    // stur: kauft jede Rate sofort (Sparplan). warten: sammelt und kauft alles auf einmal (Order). verlauf und bargeld
    // je Monat von..bis (leer ohne mitVerlauf). wartenMittel, wartenLaengste: Monate, die eine Rate zwischen Einzahlung
    // und Kauf auf dem Konto lag; am Ende nicht angelegte Raten zaehlen bis zum letzten Monat.
    data class Lauf(
        val von: Int, val bis: Int, val eingezahlt: Double, val endStur: Double, val endWarten: Double,
        val kaeufe: Int, val wartenMittel: Double, val wartenLaengste: Int, val restBar: Double,
        val verlaufStur: List<Double>, val verlaufWarten: List<Double>, val bargeld: List<Double>,
    ) {
        // Endwert des Wartenden gegen den des sturen Sparers in Prozent
        val vorsprung: Double get() = if (endStur > 0) (endWarten / endStur - 1.0) * 100.0 else 0.0
    }

    // Beide zahlen in den Monaten von bis bis - 1 dieselbe Rate ein, bewertet wird im Monat bis. Der Wartende kauft,
    // wenn der Kurs mindestens schwelle (0,1 = 10 %) unter dem Hoch der ganzen Reihe bis dahin steht, sonst sobald
    // die aelteste Rate hoechstWarten Monate liegt (null = nie).
    fun ruecksetzer(
        kurse: List<Double>, hoch: List<Double>, rate: Double, schwelle: Double, hoechstWarten: Int?, kosten: Kosten = Kosten(),
        von: Int = 0, bis: Int = kurse.size - 1, mitVerlauf: Boolean = true,
    ): Lauf? {
        if (rate <= 0 || von < 0 || bis >= kurse.size || bis <= von || hoch.size != kurse.size) return null
        var stur = 0.0
        var stk = 0.0
        var bar = 0.0
        var liegen = 0
        var kaeufe = 0
        var summe = 0L
        var laengste = 0
        val vs = ArrayList<Double>(); val vw = ArrayList<Double>(); val vb = ArrayList<Double>()
        for (m in von until bis) {
            val k = kurse[m]
            stur += stueck(rate, k, kosten.sparplan, kosten.spreadBps)
            bar += rate
            liegen++
            val tief = k <= hoch[m] * (1.0 - schwelle)
            if (tief || (hoechstWarten != null && liegen - 1 >= hoechstWarten)) {
                stk += stueck(bar, k, kosten.order, kosten.spreadBps)
                bar = 0.0
                kaeufe++
                summe += liegen.toLong() * (liegen - 1) / 2
                laengste = maxOf(laengste, liegen - 1)
                liegen = 0
            }
            if (mitVerlauf) { vs += stur * k; vw += stk * k + bar; vb += bar }
        }
        if (liegen > 0) {
            summe += liegen.toLong() * (liegen + 1) / 2
            laengste = maxOf(laengste, liegen)
        }
        val ende = kurse[bis]
        if (mitVerlauf) { vs += stur * ende; vw += stk * ende + bar; vb += bar }
        val monate = bis - von
        return Lauf(von, bis, rate * monate, stur * ende, stk * ende + bar, kaeufe, summe.toDouble() / monate, laengste, bar, vs, vw, vb)
    }

    data class Rollierend(val horizont: Int, val zeitraeume: Int, val vorn: Int, val mittel: Double, val median: Double) {
        val anteil: Double get() = vorn * 100.0 / zeitraeume
    }

    // Derselbe Vergleich fuer jeden Startmonat ueber horizont Monate; null bei weniger als MIN_ZEITRAEUME Starts.
    fun ruecksetzerRollierend(
        kurse: List<Double>, rate: Double, schwelle: Double, hoechstWarten: Int?, horizont: Int, kosten: Kosten = Kosten(),
    ): Rollierend? {
        val anzahl = kurse.size - horizont
        if (horizont < 1 || anzahl < MIN_ZEITRAEUME) return null
        val hoch = hochs(kurse)
        val v = (0 until anzahl).mapNotNull { s -> ruecksetzer(kurse, hoch, rate, schwelle, hoechstWarten, kosten, s, s + horizont, false)?.vorsprung }
        if (v.size < MIN_ZEITRAEUME) return null
        return Rollierend(horizont, v.size, v.count { it > 1e-9 }, v.average(), median(v))
    }

    // Laengster der Horizonte (in Monaten), fuer den die Reihe MIN_ZEITRAEUME Startmonate hergibt.
    fun horizont(monate: Int, wahl: List<Int> = listOf(120, 60, 36)): Int? = wahl.sortedDescending().firstOrNull { monate - it >= MIN_ZEITRAEUME }
}
