package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

// Kurzfristige Kursmuster auf Tagesschlusskursen (0.78), fuer den Scan im Aktienfinder. Jedes Muster ist eine feste
// Regel auf den Schlusskursen bis zum geprueften Tag; spaetere Kurse gehen nicht ein. Dazu der Rueckblick: wie die Werte
// nach dem ersten Tag eines Musters weiterliefen, gegen alle Werte desselben Tages.
object KursScan {

    enum class Muster { HOCH20, FLAGGE, UEBER_SCHNITT20, RSI_UEBERVERKAUFT, RSI_UMKEHR }

    // Neues Hoch: Schluss ueber allen Schluessen der 20 Handelstage davor.
    const val HOCH_TAGE = 20
    // Flagge: mehr als 10 % Anstieg in 10 Handelstagen, danach 5 Schluesse in einer Spanne von hoechstens 5 %.
    const val FLAGGE_ANSTIEG_TAGE = 10
    const val FLAGGE_ANSTIEG = 10.0
    const val FLAGGE_RUHE_TAGE = 5
    const val FLAGGE_SPANNE = 5.0
    const val SCHNITT_TAGE = 20
    const val RSI_TAGE = 14
    const val RSI_GRENZE = 30.0
    // Rueckblick: Rendite ueber so viele Handelstage nach dem Muster; darunter keine Aussage.
    const val HORIZONT = 20
    const val MIN_FAELLE = 30
    // So viele Schlusskurse braucht eine Reihe mindestens, damit der RSI eingeschwungen ist.
    const val MIN_KURSE = 60

    // Eine Kursreihe mit den Hilfsreihen, einmal gerechnet. Nur Reihen mit durchweg positiven, endlichen Schluessen.
    class Reihe(val schluss: List<Double>) {
        val gueltig: Boolean = schluss.all { it.isFinite() && it > 0 }
        val rsi: List<Double?> by lazy { Indikatoren.rsi(schluss, RSI_TAGE) }
        val schnitt: List<Double?> by lazy { Indikatoren.sma(schluss, SCHNITT_TAGE) }
    }

    // Kennzahl des Musters am Tag i, null ohne Treffer:
    // HOCH20 Abstand zum alten Hoch in Prozent, FLAGGE Anstieg davor in Prozent, UEBER_SCHNITT20 Abstand zum
    // Durchschnitt in Prozent, die RSI-Muster den RSI des Tages.
    fun treffer(m: Muster, r: Reihe, i: Int): Double? {
        val c = r.schluss
        if (!r.gueltig || i < 0 || i >= c.size) return null
        return when (m) {
            Muster.HOCH20 -> {
                if (i < HOCH_TAGE) return null
                var hoch = 0.0
                for (k in i - HOCH_TAGE until i) if (c[k] > hoch) hoch = c[k]
                if (c[i] > hoch) (c[i] / hoch - 1.0) * 100.0 else null
            }
            Muster.FLAGGE -> {
                val ende = i - FLAGGE_RUHE_TAGE
                val start = ende - FLAGGE_ANSTIEG_TAGE
                if (start < 0) return null
                val anstieg = (c[ende] / c[start] - 1.0) * 100.0
                if (!(anstieg > FLAGGE_ANSTIEG)) return null
                var hoch = c[ende + 1]
                var tief = c[ende + 1]
                for (k in ende + 1..i) { if (c[k] > hoch) hoch = c[k]; if (c[k] < tief) tief = c[k] }
                if ((hoch / tief - 1.0) * 100.0 <= FLAGGE_SPANNE) anstieg else null
            }
            Muster.UEBER_SCHNITT20 -> {
                if (i < 1) return null
                val gestern = r.schnitt[i - 1] ?: return null
                val heute = r.schnitt[i] ?: return null
                if (c[i - 1] < gestern && c[i] > heute) (c[i] / heute - 1.0) * 100.0 else null
            }
            Muster.RSI_UEBERVERKAUFT -> r.rsi[i]?.takeIf { it < RSI_GRENZE }
            Muster.RSI_UMKEHR -> {
                if (i < 1) return null
                val gestern = r.rsi[i - 1] ?: return null
                if (gestern < RSI_GRENZE && c[i] > c[i - 1]) r.rsi[i] else null
            }
        }
    }

    // Spanne der letzten fuenf Schluesse in Prozent (Zusatz zur Flagge).
    fun ruheSpanne(c: List<Double>, i: Int): Double? {
        if (i - FLAGGE_RUHE_TAGE + 1 < 0 || i >= c.size) return null
        val teil = c.subList(i - FLAGGE_RUHE_TAGE + 1, i + 1)
        val tief = teil.min()
        return if (tief > 0) (teil.max() / tief - 1.0) * 100.0 else null
    }

    data class Fund(val muster: Muster, val kennzahl: Double)

    // Alle Muster am letzten Tag der Reihe; leer bei zu kurzer oder fehlerhafter Reihe.
    fun heute(schluss: List<Double>): List<Fund> {
        if (schluss.size < MIN_KURSE) return emptyList()
        val r = Reihe(schluss)
        val i = schluss.size - 1
        return Muster.entries.mapNotNull { m -> treffer(m, r, i)?.let { Fund(m, it) } }
    }

    // Veraenderung des letzten Schlusses zum Schluss davor in Prozent.
    fun tagesAenderung(schluss: List<Double>): Double? {
        val n = schluss.size
        if (n < 2 || !(schluss[n - 2] > 0) || !schluss[n - 1].isFinite()) return null
        return (schluss[n - 1] / schluss[n - 2] - 1.0) * 100.0
    }

    // Rueckblick eines Musters: Faelle, mittlere Rendite danach und die mittlere Rendite aller Werte ueber dieselben
    // Tage, beide in Prozent.
    @Serializable
    data class Bilanz(val faelle: Int, val mittel: Double, val markt: Double) {
        val mehr: Double get() = mittel - markt
    }

    // Wie es nach dem ersten Tag eines Musters ueber horizont Handelstage weiterging. Je Reihe Zeitstempel (ms) und
    // Schluesse. Ein Fall zaehlt nur am ersten Tag einer Folge von Treffertagen, damit ein Muster, das tagelang gilt,
    // nicht mehrfach zaehlt. Vergleich ist der Durchschnitt aller Reihen mit demselben Kalendertag, Start und Ende gleich.
    // Ohne Kosten, ohne Dividenden, mit den heute gewaehlten Werten (Werte, die es nicht mehr gibt, fehlen).
    fun rueckblick(reihen: Collection<Pair<List<Long>, List<Double>>>, horizont: Int = HORIZONT): Map<Muster, Bilanz> {
        val brauchbar = reihen.filter { (t, c) -> t.size == c.size && c.size >= MIN_KURSE + horizont }.map { (t, c) -> t to Reihe(c) }.filter { it.second.gueltig }
        if (brauchbar.isEmpty()) return emptyMap()
        fun tag(ms: Long) = ms.floorDiv(86_400_000L)
        // Rendite aller Werte je Starttag: Summe und Anzahl
        val markt = HashMap<Long, DoubleArray>()
        for ((t, r) in brauchbar) {
            val c = r.schluss
            for (i in MIN_KURSE - 1 until c.size - horizont) {
                val s = markt.getOrPut(tag(t[i])) { DoubleArray(2) }
                s[0] += c[i + horizont] / c[i] - 1.0
                s[1] += 1.0
            }
        }
        val summe = HashMap<Muster, DoubleArray>()
        for ((t, r) in brauchbar) {
            val c = r.schluss
            for (m in Muster.entries) {
                var vorher = treffer(m, r, MIN_KURSE - 2) != null
                for (i in MIN_KURSE - 1 until c.size - horizont) {
                    val jetzt = treffer(m, r, i) != null
                    if (jetzt && !vorher) {
                        val mk = markt[tag(t[i])]
                        if (mk != null && mk[1] > 0) {
                            val s = summe.getOrPut(m) { DoubleArray(3) }
                            s[0] += c[i + horizont] / c[i] - 1.0
                            s[1] += mk[0] / mk[1]
                            s[2] += 1.0
                        }
                    }
                    vorher = jetzt
                }
            }
        }
        return summe.mapValues { (_, s) -> Bilanz(s[2].toInt(), s[0] / s[2] * 100.0, s[1] / s[2] * 100.0) }
    }

    // Ein Wert fuer die Auswahl: Kurs in Euro, mittleres Tagesvolumen in Stueck, Boersenwert; null = unbekannt.
    data class Kandidat(val symbol: String, val kurs: Double?, val volumen: Double?, val boersenwert: Double?)

    // Standardwerte und Stufen des Liquiditaetsfilters.
    const val MIN_KURS = 5.0
    const val MIN_VOLUMEN = 200_000.0
    val KURS_STUFEN = listOf(0.0, 1.0, 5.0, 10.0, 20.0, 50.0)
    val VOLUMEN_STUFEN = listOf(0.0, 50_000.0, 200_000.0, 500_000.0, 1_000_000.0, 5_000_000.0)
    // So viele Werte des Markts prueft ein Scan hoechstens (20 je Abruf), dazu hoechstens so viele eigene.
    const val HOECHSTENS = 400
    const val EIGENE_HOECHSTENS = 100

    // Besteht ein Wert den Filter: eine Grenze ueber 0 verlangt eine bekannte Zahl.
    fun liquide(k: Kandidat, minKurs: Double, minVolumen: Double): Boolean =
        (minKurs <= 0 || (k.kurs != null && k.kurs >= minKurs)) && (minVolumen <= 0 || (k.volumen != null && k.volumen >= minVolumen))

    // Werte eines Scans: die eigenen (Depot und Watchlist, ohne Filter) zuerst, dann die liquiden Werte des Markts, die
    // groessten nach Boersenwert zuerst, bis zur Obergrenze.
    fun auswahl(
        markt: List<Kandidat>, eigene: List<String>, minKurs: Double, minVolumen: Double,
        hoechstens: Int = HOECHSTENS, eigeneHoechstens: Int = EIGENE_HOECHSTENS,
    ): List<String> {
        val vorn = eigene.distinct().take(eigeneHoechstens)
        val schon = vorn.toHashSet()
        val rest = markt.asSequence().filter { it.symbol !in schon && liquide(it, minKurs, minVolumen) }
            .sortedByDescending { it.boersenwert ?: 0.0 }.map { it.symbol }.distinct().take(hoechstens).toList()
        return vorn + rest
    }

    // Abrufe eines Scans: 20 Werte je Abruf, schon gemerkte Reihen kosten keinen.
    fun abrufe(werte: Int, gemerkt: Int = 0, jeAbruf: Int = 20): Int = ((werte - gemerkt).coerceAtLeast(0) + jeAbruf - 1) / jeAbruf

    // Ein Treffer des Scans. kurs in Euro (null = unbekannt), tag: Veraenderung des letzten Schlusses in Prozent,
    // zusatz: bei der Flagge die Spanne der letzten fuenf Schluesse.
    @Serializable
    data class Treffer(
        val symbol: String, val name: String, val muster: String, val kennzahl: Double, val zusatz: Double? = null,
        val kurs: Double? = null, val tag: Double? = null,
    )

    // Ergebnis eines Scans: Markt, Zeitpunkt, Filter, wie viele Werte geprueft wurden und wie viele ohne Kurse blieben.
    @Serializable
    data class Stand(
        val universum: String, val zeit: Long, val minKurs: Double, val minVolumen: Double,
        val geprueft: Int = 0, val ohneKurse: Int = 0, val treffer: List<Treffer> = emptyList(),
        val bilanz: Map<String, Bilanz> = emptyMap(),
    ) {
        fun fuer(m: Muster): List<Treffer> = treffer.filter { it.muster == m.name }
        fun bilanzFuer(m: Muster): Bilanz? = bilanz[m.name]?.takeIf { it.faelle >= MIN_FAELLE }
    }

    // Treffer eines Werts aus seiner Reihe.
    fun trefferVon(symbol: String, name: String, schluss: List<Double>, kursEuro: Double?): List<Treffer> {
        val funde = heute(schluss)
        if (funde.isEmpty()) return emptyList()
        val tag = tagesAenderung(schluss)
        return funde.map { f ->
            Treffer(
                symbol, name, f.muster.name, f.kennzahl,
                zusatz = if (f.muster == Muster.FLAGGE) ruheSpanne(schluss, schluss.size - 1) else null, kurs = kursEuro, tag = tag,
            )
        }
    }

    // Reihenfolge in der Liste: das staerkste Zeichen zuerst (tiefster RSI, groesster Abstand oder Anstieg).
    fun sortiert(m: Muster, l: List<Treffer>): List<Treffer> = when (m) {
        Muster.RSI_UEBERVERKAUFT, Muster.RSI_UMKEHR -> l.sortedBy { it.kennzahl }
        else -> l.sortedByDescending { it.kennzahl }
    }

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // Gespeicherte Ergebnisse je Markt; aelter als sieben Tage faellt beim Schreiben weg.
    fun lesen(text: String): Map<String, Stand> =
        runCatching { json.decodeFromString<Map<String, Stand>>(text) }.getOrDefault(emptyMap())

    fun schreiben(staende: Map<String, Stand>, jetzt: Long): String =
        json.encodeToString(MapSerializer(String.serializer(), Stand.serializer()), staende.filterValues { jetzt - it.zeit < 7 * 86_400_000L })
}
