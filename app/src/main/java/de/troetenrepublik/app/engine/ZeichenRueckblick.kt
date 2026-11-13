package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Fruehwarnung.Ampel
import de.troetenrepublik.app.engine.WarnRueckblick.Quellen
import de.troetenrepublik.app.engine.WarnRueckblick.Reihe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

// Rueckblick fuer Bodenzeichen und Blasen-Detektor (0.78): Wie standen die Zeichen an den grossen Tiefs des S&P 500, wie
// die Messgroessen des Detektors an den Hochs vor seinen Einbruechen? Gerechnet wird mit den Reihen und dem
// Veroeffentlichungsverzug des Warnzeichen-Rueckblicks (WarnRueckblick.Reihe). Die Datei
// assets/indikatoren/zeichen_rueckblick.json traegt nur die damaligen Werte; die Einordnung (erfuellt, nahe,
// angeschlagen) rechnet die App beim Lesen mit den Schwellen aus engine/Boden.kt und engine/Blasen.kt.
// Gebaut wird die Datei mit :desktop:zeichenRueckblick. Alle Tage sind Tage seit 1970.
object ZeichenRueckblick {

    const val DATEI = "indikatoren/zeichen_rueckblick.json"

    // Grosser Boden: Tief nach einem Rueckgang ab 20 % vom Hoch, von dem aus der Index um mindestens 50 % stieg, ohne
    // vorher tiefer zu fallen. Die zweite Bedingung trennt Zwischentiefs (November 1929) vom Tief des Baerenmarkts.
    const val RUECKGANG_AB = 20.0
    const val ERHOLUNG_AB = 50.0

    // Messgroessen des Detektors: Hoechststand in diesem Fenster vor dem Hoch (Tage)
    const val FENSTER = 365

    // Phasen eines Bodenzeichens: Pausen bis RUHE Tage zaehlen mit; ein Boden gehoert zur Phase, wenn er hoechstens
    // NAEHE Tage vor ihrem Beginn oder nach ihrem Ende liegt.
    const val RUHE = 183
    const val NAEHE = 183

    // Zusaetzliche Eingangsreihen neben denen des Warnzeichen-Rueckblicks: lange Zinsreihe (Monatswerte) fuer die Jahre
    // vor den Tageswerten, S&P 500 nach Boersenwert und gleichgewichtet, Boersengaenge
    const val R_ZINS_LANG = "zinslang"
    const val R_SPY = "spy"
    const val R_RSP = "rsp"
    const val R_IPO = "ipo"
    const val VERZUG_ZINS_LANG = 30

    // Shiller-KGV: Fehlt der Monat des Tages in der Quelle noch, gilt der Monat davor (Tage seit dessen Monatsbeginn)
    const val CAPE_ALTER = 62

    // Kennung der Zaehlung „viele Bodenzeichen“ (Boden.Stufe.VIELE) neben den einzelnen Zeichen
    const val VIELE = "viele"

    // Messgroessen des Detektors mit historischer Reihe, in der Reihenfolge der Anzeige
    val BLASEN_MIT_REIHE = (Blasen.DAFUER + Blasen.DAGEGEN).filter {
        it in setOf(Blasen.CAPE, Blasen.KGV, Blasen.BUFFETT, Blasen.MARGIN, Blasen.TREND, Blasen.PRAEMIE, Blasen.BREITE, Blasen.IPO)
    }
    val BLASEN_OHNE_REIHE = (Blasen.DAFUER + Blasen.DAGEGEN) - BLASEN_MIT_REIHE.toSet()

    // Schwellen der Bodenzeichen in fester Reihenfolge; die Datei traegt sie mit, damit die Zaehlung der Phasen nur gilt,
    // solange sie zu engine/Boden.kt passt.
    fun bodenSchwellen(): List<Double> = listOf(
        Boden.KGV_ERFUELLT, Boden.KGV_NAHE, Boden.PRAEMIE_ERFUELLT, Boden.PRAEMIE_NAHE, Boden.JAHR_ERFUELLT,
        Boden.ZWEI_JAHRE_ERFUELLT, Boden.RUECKGANG_NAHE, Boden.VIX_ERFUELLT, Boden.VIX_NAHE,
    )

    // ------------------------------------------------------------ Datei

    // Damaliger Wert eines Zeichens. zusatz: beim Kursrueckgang die Veraenderung ueber zwei Jahre, bei den Messgroessen
    // des Detektors der staerkste Wert im Jahr vor dem Hoch.
    @Serializable
    data class Wert(val id: String, val wert: Double, val zusatz: Double? = null)

    // Ein grosser Boden: Tag des Hochs davor und des Tiefs, Rueckgang in Prozent, Veraenderung zwoelf Monate nach dem
    // Tief in Prozent (null: noch kein Jahr her), Werte der Zeichen am Tief (ohne Daten fehlt das Zeichen).
    @Serializable
    data class Tief(val hoch: Int, val tief: Int, val rueckgang: Double, val danach: Double? = null, val werte: List<Wert> = emptyList())

    // Ein Hoch vor einem Einbruch ab 20 %: Tag des Hochs und des Tiefs danach, Rueckgang in Prozent, Werte der
    // Messgroessen am Hoch.
    @Serializable
    data class Hoch(val hoch: Int, val tief: Int, val rueckgang: Double, val werte: List<Wert> = emptyList())

    // Wie oft ein Bodenzeichen erfuellt war: ab (erster Tag mit Daten), Phasen insgesamt, mit einem grossen Boden in der
    // Naehe, ohne (Fehlalarme) und noch offene; boeden: grosse Boeden mit Daten, erfuellt: an wie vielen davon das Zeichen
    // am Tief erfuellt war; zeit: Anteil der Tage mit erfuelltem Zeichen.
    @Serializable
    data class Alarm(
        val id: String, val ab: Int, val phasen: Int = 0, val mitBoden: Int = 0, val fehlalarme: Int = 0, val offen: Int = 0,
        val boeden: Int = 0, val erfuellt: Int = 0, val zeit: Double = 0.0,
    )

    @Serializable
    data class Datei(
        val stand: String, val von: Int, val bis: Int, val schwellen: List<Double> = emptyList(),
        val boeden: List<Tief>, val alarme: List<Alarm> = emptyList(), val blasen: List<Hoch>, val version: Int = 1,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun lesen(text: String): Datei? = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull()
        ?.takeIf { it.boeden.isNotEmpty() && it.blasen.isNotEmpty() }

    fun schreiben(d: Datei): String = json.encodeToString(Datei.serializer(), d)

    val datei: Datei? by lazy { runCatching { lesen(de.troetenrepublik.app.market.Daten.text(DATEI)) }.getOrNull() }

    // ------------------------------------------------------------ Einordnung mit den Schwellen der App

    // Lage eines Bodenzeichens aus seinem damaligen Wert, mit den Regeln aus engine/Boden.kt
    fun lage(w: Wert): Boden.Lage? = when (w.id) {
        Boden.KGV -> Boden.kgv(w.wert)?.lage
        Boden.PRAEMIE -> Boden.praemie(w.wert)?.lage
        Boden.RUECKGANG -> Boden.rueckgang(w.wert, w.zusatz)?.lage
        Boden.SCHWANKUNG -> Boden.schwankung(w.wert)?.lage
        else -> null
    }

    // Erfuellte Zeichen und Zeichen mit Daten an einem Boden
    fun zaehlung(t: Tief): Pair<Int, Int> {
        val l = t.werte.mapNotNull { lage(it) }
        return l.count { it == Boden.Lage.ERFUELLT } to l.size
    }

    // Ampel einer Messgroesse des Detektors: der staerkere Stand aus dem Wert am Hoch und dem staerksten Wert im Jahr davor
    fun ampel(w: Wert): Ampel {
        val a = Blasen.ampel(w.id, w.wert)
        val b = w.zusatz?.let { Blasen.ampel(w.id, it) } ?: a
        return if (b.ordinal > a.ordinal) b else a
    }

    // Kam die Ampel nur aus dem Jahr vor dem Hoch (am Hoch selbst stand die Messgroesse schwaecher)?
    fun nurDavor(w: Wert): Boolean = w.zusatz != null && Blasen.ampel(w.id, w.zusatz).ordinal > Blasen.ampel(w.id, w.wert).ordinal

    // Angeschlagene (rote) Messgroessen und Messgroessen mit Daten an einem Hoch
    fun zaehlung(h: Hoch): Pair<Int, Int> {
        val l = h.werte.filter { it.id in BLASEN_MIT_REIHE }
        return l.count { ampel(it) == Ampel.ROT } to l.size
    }

    // Messgroessen mit Reihe, fuer die an diesem Hoch noch keine Daten vorlagen
    fun ohneDaten(h: Hoch): List<String> = BLASEN_MIT_REIHE.filter { k -> h.werte.none { it.id == k } }

    // Bodenzeichen ohne Daten an diesem Boden
    fun ohneDaten(t: Tief): List<String> = Boden.ALLE.filter { k -> t.werte.none { it.id == k } }

    // Die Zaehlung der Phasen gilt nur, wenn die Datei mit den heutigen Schwellen gerechnet wurde
    fun alarme(d: Datei): List<Alarm> = if (d.schwellen == bodenSchwellen()) d.alarme.filter { it.phasen > 0 || it.boeden > 0 } else emptyList()

    fun jahrMonat(tag: Int): Pair<Int, Int> = LocalDate.ofEpochDay(tag.toLong()).let { it.year to it.monthValue }

    // ------------------------------------------------------------ Boeden und Hochs aus der Tagesreihe

    // Ein Abschwung vom Hoch zum Tief als Plaetze in der Reihe
    class Schwung(val hoch: Int, val tief: Int)

    // boeden: bestaetigte grosse Boeden, aelteste zuerst. offenAb: Platz des Hochs eines laufenden Rueckgangs ab 20 %,
    // dessen Tief noch nicht bestaetigt ist (sonst null).
    class Wenden(val boeden: List<Schwung>, val offenAb: Int?)

    fun wenden(kurs: DoubleArray, ab: Double = RUECKGANG_AB, auf: Double = ERHOLUNG_AB): Wenden {
        val aus = ArrayList<Schwung>()
        if (kurs.isEmpty()) return Wenden(aus, null)
        var hoch = 0
        var tief = 0
        var faellt = false
        for (i in 1 until kurs.size) {
            if (!faellt) {
                if (kurs[i] >= kurs[hoch]) hoch = i
                else if (kurs[i] <= kurs[hoch] * (1 - ab / 100)) { faellt = true; tief = i }
            } else {
                if (kurs[i] < kurs[tief]) tief = i
                else if (kurs[i] >= kurs[tief] * (1 + auf / 100)) { aus += Schwung(hoch, tief); faellt = false; hoch = i }
            }
        }
        return Wenden(aus, if (faellt) hoch else null)
    }

    // Veraenderung in Prozent vom Tag am Platz i bis zum letzten Handelstag hoechstens `tage` Tage spaeter; null, wenn die
    // Reihe nicht so weit reicht.
    fun danach(tage: LongArray, kurs: DoubleArray, i: Int, spanne: Int = 365): Double? {
        val ziel = tage[i] + spanne
        if (tage.last() < ziel) return null
        val j = WarnRueckblick.lowerBound(tage, ziel + 1) - 1
        return if (j <= i || kurs[i] <= 0) null else (kurs[j] / kurs[i] - 1) * 100
    }

    // ------------------------------------------------------------ Werte der Zeichen an einem Tag

    // Rendite zehnjaehriger US-Anleihen: Tageswert, davor der Monatswert der langen Reihe
    private fun zins(q: Quellen, t: Long): Double? =
        WarnRueckblick.letzterWert(q.reihen[WarnRueckblick.R_ZINS10], t) ?: WarnRueckblick.letzterWert(q.reihen[R_ZINS_LANG], t)

    private fun praemie(q: Quellen, t: Long): Double? = Blasen.praemie(WarnRueckblick.kgvWert(q, t), zins(q, t))?.wert

    // Shiller-KGV im Monat des Tages t: der Monatswert der Quelle, unveraendert. Das ist die Groesse, die der Blasen-Detektor
    // zeigt und mit der der Warnzeichen-Rueckblick rechnet. Der Wert traegt den ersten Tag seines Monats; er gilt hier fuer
    // den ganzen Monat, auch wenn er erst danach erschien.
    fun cape(q: Quellen, t: Long): Double? {
        val r = q.reihen[WarnRueckblick.R_CAPE] ?: return null
        val n = WarnRueckblick.lowerBound(r.tage, t + 1)
        return if (n == 0 || t - r.tage[n - 1] > CAPE_ALTER) null else r.werte[n - 1]
    }

    // Veraenderung einer Tagesreihe ueber `spanne` Tage bis zum Tag t, mit der Regel der Bodenzeichen (Boden.veraenderung)
    private fun veraenderung(r: Reihe?, t: Long, spanne: Int): Double? {
        val x = r ?: return null
        val n = x.aktuell(t) ?: return null
        val von = WarnRueckblick.lowerBound(x.tage, x.tage[n - 1] - spanne - 12)
        return Boden.veraenderung(x.paare(n, n - von), spanne)
    }

    // Die Bodenzeichen am Tag t in der Reihenfolge der Anzeige; was keine Daten hat, fehlt.
    fun bodenZeichen(q: Quellen, t: Long): List<Boden.Zeichen> {
        val sp = q.reihen[WarnRueckblick.R_SP]
        return listOfNotNull(
            Boden.kgv(WarnRueckblick.kgvWert(q, t)), Boden.praemie(praemie(q, t)),
            Boden.rueckgang(veraenderung(sp, t, 365), veraenderung(sp, t, 730)),
            Boden.schwankung(WarnRueckblick.letzterWert(q.reihen[WarnRueckblick.R_VIX], t)),
        )
    }

    // Die Messgroessen des Detektors am Tag t, soweit Reihen vorliegen: Kennung zu Wert
    fun blasenWerte(q: Quellen, t: Long): Map<String, Double> {
        val aus = LinkedHashMap<String, Double>()
        fun setze(k: String, v: Double?) { if (v != null && v.isFinite()) aus[k] = v }
        setze(Blasen.CAPE, cape(q, t))
        setze(Blasen.KGV, Blasen.kgv(WarnRueckblick.kgvWert(q, t))?.wert)
        setze(Blasen.BUFFETT, WarnRueckblick.buffettWerte(q, t)?.let { (w, b) -> Blasen.buffett(w, b)?.wert })
        setze(Blasen.MARGIN, WarnRueckblick.marginWachstum(q, t))
        setze(Blasen.TREND, WarnRueckblick.einfach(q, WarnRueckblick.R_SP, t)?.let { (r, n) -> WarnRueckblick.abstand200(r, n) })
        setze(Blasen.PRAEMIE, praemie(q, t))
        setze(Blasen.BREITE, Blasen.breite(veraenderung(q.reihen[R_SPY], t, 365), veraenderung(q.reihen[R_RSP], t, 365))?.wert)
        setze(Blasen.IPO, veraenderung(q.reihen[R_IPO], t, 365))
        return aus
    }

    // Der staerkere zweier Werte einer Messgroesse (weiter in Richtung Rot)
    private fun staerker(k: String, a: Double, b: Double): Double =
        if (Blasen.SCHWELLEN[k]?.richtung == Blasen.Richtung.FAELLT) minOf(a, b) else maxOf(a, b)

    private fun runden(v: Double, stellen: Int = 2): Double = WarnRueckblick.runden(v, stellen)

    // ------------------------------------------------------------ Gesamte Rechnung

    // tage, kurs: Tagesreihe des S&P 500; q: Reihen des Warnzeichen-Rueckblicks und die zusaetzlichen von oben
    fun rechnen(tage: LongArray, kurs: DoubleArray, q: Quellen, stand: String): Datei {
        val w = wenden(kurs)
        val boeden = w.boeden.map { s ->
            val z = bodenZeichen(q, tage[s.tief])
            Tief(
                tage[s.hoch].toInt(), tage[s.tief].toInt(), runden((1 - kurs[s.tief] / kurs[s.hoch]) * 100, 1),
                danach(tage, kurs, s.tief)?.let { runden(it, 1) },
                z.map { Wert(it.schluessel, runden(it.wert), it.zusatz?.let { x -> runden(x) }) },
            )
        }
        val blasen = WarnRueckblick.ereignisse(tage, kurs).filter { it.tiefe >= WarnRueckblick.EINBRUCH }.map { e ->
            val amHoch = blasenWerte(q, e.hoch.toLong())
            val spitze = HashMap<String, Double>()
            val von = WarnRueckblick.lowerBound(tage, e.hoch.toLong() - FENSTER)
            val bis = WarnRueckblick.lowerBound(tage, e.hoch.toLong())
            for (i in von..bis) for ((k, v) in blasenWerte(q, tage[i])) spitze[k] = spitze[k]?.let { staerker(k, it, v) } ?: v
            Hoch(
                e.hoch, e.tief, runden(e.tiefe, 1),
                BLASEN_MIT_REIHE.mapNotNull { k ->
                    val v = amHoch[k] ?: return@mapNotNull null
                    val s = spitze[k]?.takeIf { runden(it) != runden(v) }
                    Wert(k, runden(v), s?.let { runden(it) })
                },
            )
        }
        return Datei(
            stand, tage.first().toInt(), tage.last().toInt(), bodenSchwellen(), boeden,
            alarme(tage, q, w.boeden.map { tage[it.tief] }, w.offenAb?.let { tage[it] }), blasen,
        )
    }

    // Phasen aus Tagen mit erfuelltem Zeichen: Paare Beginn, Ende; Pausen bis RUHE Tage zaehlen mit
    fun phasen(tage: LongArray, erfuellt: BooleanArray): List<LongArray> = WarnRueckblick.phasen(tage, erfuellt, RUHE)

    // Zaehlung eines Zeichens. zustand je Handelstag: 0 keine Daten, 1 nicht erfuellt, 2 erfuellt. tiefs: Tage der grossen
    // Boeden; offenAb: Beginn eines laufenden Rueckgangs ohne bestaetigtes Tief. Eine Phase ohne Boden in der Naehe ist ein
    // Fehlalarm; reicht sie in den laufenden Rueckgang oder bis kurz vor das Ende der Reihe, bleibt sie offen.
    fun alarm(id: String, tage: LongArray, zustand: IntArray, tiefs: List<Long>, offenAb: Long?): Alarm? {
        val i0 = zustand.indexOfFirst { it != 0 }
        if (i0 < 0) return null
        val ph = phasen(tage, BooleanArray(tage.size) { zustand[it] == 2 })
        val letzter = tage.last()
        var mit = 0
        var fehl = 0
        var offen = 0
        for (p in ph) {
            when {
                tiefs.any { it >= p[0] - NAEHE && it <= p[1] + NAEHE } -> mit++
                p[1] + NAEHE > letzter || (offenAb != null && p[1] >= offenAb) -> offen++
                else -> fehl++
            }
        }
        val mitDaten = tiefs.filter { t -> zustand[WarnRueckblick.lowerBound(tage, t)] != 0 }
        val tageMit = (i0 until tage.size).count { zustand[it] != 0 }
        return Alarm(
            id, tage[i0].toInt(), ph.size, mit, fehl, offen, mitDaten.size,
            mitDaten.count { t -> zustand[WarnRueckblick.lowerBound(tage, t)] == 2 },
            if (tageMit > 0) runden(zustand.count { it == 2 }.toDouble() / tageMit, 3) else 0.0,
        )
    }

    private fun alarme(tage: LongArray, q: Quellen, tiefs: List<Long>, offenAb: Long?): List<Alarm> {
        val zustand = (Boden.ALLE + VIELE).associateWith { IntArray(tage.size) }
        for (i in tage.indices) {
            val z = bodenZeichen(q, tage[i])
            for (k in Boden.ALLE) zustand.getValue(k)[i] = z.firstOrNull { it.schluessel == k }?.let { if (it.lage == Boden.Lage.ERFUELLT) 2 else 1 } ?: 0
            zustand.getValue(VIELE)[i] = Boden.gesamt(z)?.let { if (it == Boden.Stufe.VIELE) 2 else 1 } ?: 0
        }
        return (Boden.ALLE + VIELE).mapNotNull { alarm(it, tage, zustand.getValue(it), tiefs, offenAb) }
    }
}
