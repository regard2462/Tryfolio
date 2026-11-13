package de.troetenrepublik.app.engine

import kotlin.math.abs
import kotlin.math.ln

// Aktienfinder (5.92): ordnet Aktien des Katalogs nach allem ein, was die App ueber sie weiss. Acht Bereiche mit
// sichtbaren Gewichten, je Bereich eine Punktzahl von 0 bis 100 (50 = unauffaellig), daraus die Gesamtpunktzahl, die
// Einordnung guenstig, fair oder teuer und die drei Kennzahlen, die das Ergebnis am staerksten bewegen.
// Baut auf dem Depotfinder auf (gleiches Universum, Ergebnis laesst sich als Depot nachbilden); reine Rechnung.
object Aktienfinder {

    // Standardgewichte in Prozent; die Oberflaeche laesst sie verschieben. Seit dem Rueckblick-Test (FinderRueckblick)
    // zaehlen die Analysten 10 statt 15: ihre Hoch- und Abstufungen ordneten die spaetere Rendite von 2012 bis 2025 nicht
    // erkennbar. Die 5 Punkte gingen an den Kurstrend, den Bereich mit der laengsten Messreihe und positiver Rang-Korrelation.
    // [0.73] Der Bereich Land wird berechnet und gezeigt, zaehlt aber nur mit, wenn ihm jemand Gewicht gibt (Standard 0);
    // ohne ihn bleibt es bei den gemessenen Gewichten, die Summe der uebrigen ist 100. Sein Rueckblick-Test fehlt.
    // [0.77] Der Marktmacht zaehlt 15; dafuer geben Bewertung 5, Qualitaet 5 und Kurstrend 5 ab. Er ist aus Kennzahlen nur
    // genaehert und ueberschneidet sich mit der Qualitaet, deshalb nicht mehr. Sein Rueckblick-Test fehlt: Die
    // Jahresabschluesse der frueheren Stichtage liegen nicht vor.
    enum class Bereich(val gewicht: Int) { BEWERTUNG(25), QUALITAET(15), MARKTMACHT(15), LAND(0), WELT(0), ANALYSTEN(10), TREND(15), INSIDER(10), LEERVERKAUF(5), STIMMUNG(5) }

    // Kennzahlen mit ihrem Bereich und ihrem Gewicht darin. Die Reihenfolge ist die der Erklaerung. In der Qualitaet
    // zaehlt der F-Score doppelt (neun Pruefungen), die Altman-Zone einfach; zusammen ist die Bilanz 3 von 7 Teilen.
    // BILANZ ist die Ampel allein, nur wenn F-Score und Zone fehlen.
    enum class Kennzahl(val bereich: Bereich, val gewicht: Double = 1.0) {
        KGV(Bereich.BEWERTUNG), KGV_ERWARTET(Bereich.BEWERTUNG), KBV(Bereich.BEWERTUNG), PEG(Bereich.BEWERTUNG),
        IM_KURS(Bereich.BEWERTUNG), KUV(Bereich.BEWERTUNG), EV_EBITDA(Bereich.BEWERTUNG),
        RENDITE(Bereich.QUALITAET), WACHSTUM(Bereich.QUALITAET), MARGE(Bereich.QUALITAET), SCHULDEN(Bereich.QUALITAET), BILANZ(Bereich.QUALITAET, 3.0),
        PIOTROSKI(Bereich.QUALITAET, 2.0), ALTMAN(Bereich.QUALITAET, 1.0),
        // [0.77] Marktmacht (Marktmacht.kt): Umsatzanteil an der Branche, Bruttomarge oder operative Marge gegen die Branche,
        // niedrigste Kapitalrendite mit ruhiger Marge. Je Wert steht nur eine der beiden Preismacht-Kennzahlen.
        STELLUNG(Bereich.MARKTMACHT), PREIS_BRUTTO(Bereich.MARKTMACHT), PREIS_BETRIEB(Bereich.MARKTMACHT), BESTAENDIG(Bereich.MARKTMACHT),
        // [0.73] Politisches und strukturelles Risiko des Sitzlands (LandRisiko), 0 bis 100
        LAND(Bereich.LAND),
        // [0.73] Rohstoffbindung der Branche (abgeleitet aus USGS-Foerderanteilen), 0 bis 100; gerechnet und gezeigt, Gewicht 0
        ROHSTOFFE(Bereich.WELT),
        NOTE(Bereich.ANALYSTEN), KURSZIEL(Bereich.ANALYSTEN), BEREINIGT(Bereich.ANALYSTEN),
        JAHR(Bereich.TREND), LINIE200(Bereich.TREND),
        // [0.73] RSI (Trendstaerke: hoher Wert gut) und Abstand zur 50-Tage-Linie: gerechnet und gezeigt, Gewicht 0. Der
        // Rueckblick-Test (2012 bis 2025, S&P 500) misst beide ohne tragenden Zusammenhang (IC bei zwoelf Monaten unter zwei
        // Standardfehlern), deshalb gehen sie nicht in die Punkte des Kurstrends ein.
        RSI(Bereich.TREND, 0.0), LINIE50(Bereich.TREND, 0.0),
        INSIDER(Bereich.INSIDER),
        LEER(Bereich.LEERVERKAUF),
        ERWAEHNUNGEN(Bereich.STIMMUNG),
    }

    enum class Einordnung { GUENSTIG, FAIR, TEUER, UNKLAR }

    // Alles, was zu einer Aktie bekannt ist; null, wo eine Angabe fehlt. Prozentwerte in Prozent (12.5 = 12,5 %).
    data class Daten(
        val symbol: String,
        val name: String,
        val land: String = "",
        val branche: String = "",
        // Sammelabruf fuer alle Werte
        val kgv: Double? = null,
        val kgvErwartet: Double? = null,
        val kbv: Double? = null,
        val gewinn: Double? = null,
        val gewinnErwartet: Double? = null,
        val buchwert: Double? = null,
        val dividende: Double? = null,
        val boersenwert: Double? = null,
        // Durchschnittsnote der Analysten, 1 (stark kaufen) bis 5 (stark verkaufen)
        val note: Double? = null,
        val jahr: Double? = null,
        val linie200: Double? = null,
        // Einzelabrufe fuer die besten Kandidaten
        val kuv: Double? = null,
        val evEbitda: Double? = null,
        val peg: Double? = null,
        val marge: Double? = null,
        val eigenkapitalRendite: Double? = null,
        // Fremd- durch Eigenkapital als Verhaeltnis (1.0 = gleich viel)
        val verschuldung: Double? = null,
        val kursziel: Double? = null,
        val bilanz: Bewertungsblick.Ampel? = null,
        // Bilanzqualitaet aus den Jahresabschluessen (FinderBilanz): F-Score 0 bis 9, Altman-Zone, Gruende der Ampel
        val fScore: Double? = null,
        val zone: Qualitaet.Zone? = null,
        val bilanzGruende: List<Bewertungsblick.Grund> = emptyList(),
        // Bereinigte Analystenhaltung aus der Trefferbilanz, -2 bis +2
        val bereinigt: Double? = null,
        // [0.73] Landrisiko-Punktzahl des Taetigkeitslands (sonst des Sitzes), 0 bis 100 (LandRisiko.punkte)
        val landPunkte: Double? = null,
        // [0.73] Rohstoffbindung der Branche, 0 bis 100 (Einflussverzeichnis.rohstoffBindung)
        val rohstoffPunkte: Double? = null,
        // Taetigkeitsland (alpha-2), wo die Firmenbeschreibung eines nennt; null: gilt der Sitz
        val taetig: String? = null,
        // [0.73] Aus dem Profil fuer die Waage wie auf der Wertpapierseite: Kasse, Schulden und freier Cashflow in Euro,
        // Sektor und Branche von Yahoo (fuer „zyklisch“), Zahl der Analysten und Ergebnis je Aktie der letzten Quartale
        val kasse: Double? = null,
        val schulden: Double? = null,
        val freierCashflow: Double? = null,
        val sektor: String = "",
        val industrie: String = "",
        val analysten: Int = 0,
        val ergebnisse: List<Pair<Double, Double>> = emptyList(),
        // Weitere Quellen der App
        val insider: Int? = null,
        val insiderCluster: Boolean = false,
        val leer: Double? = null,
        val leerUs: Boolean = false,
        val erwaehnungen: Int? = null,
        val erwaehnungenVorher: Int? = null,
        val mitDetails: Boolean = false,
        // Felder fuer Vorlagen und harte Grenzen (FinderVorlagen, 5.94): Sammelabruf, Einzelabruf und Tageskurse
        val werte: Map<FinderVorlagen.Feld, Double> = emptyMap(),
        val zahlenTermin: Long? = null,
        val mitKerzen: Boolean = false,
        // [0.75] Der Sammelabruf brachte fuer diesen Wert eine Antwort. Ohne sie (keine Verbindung) gelten fehlende
        // Felder als offen, nicht als nicht erfuellt (FinderVorlagen.geladen).
        val mitSammel: Boolean = true,
        // [0.78] Die Jahresabschluesse liegen mit allen Feldern der Auswahlraster vor (FinderVorlagen.Stufe.BILANZ)
        val mitAbschluss: Boolean = false,
        // [0.77] Marktmacht aus den Jahresabschluessen und dem Katalog; null ohne Grundlage
        val marktmacht: Marktmacht.Ergebnis? = null,
    )

    // Ein Grund: Kennzahl, ob sie fuer die Aktie spricht, ihr Wert, der Vergleichswert (Branche) und ihr Anteil an der
    // Abweichung der Gesamtpunktzahl von 50.
    data class Grund(val kennzahl: Kennzahl, val gut: Boolean, val wert: Double, val vergleich: Double?, val beitrag: Double)

    data class Ergebnis(
        val daten: Daten,
        val gesamt: Double,
        // Punktzahl je Bereich, null ohne Angabe (zaehlt dann neutral mit 50)
        val bereiche: Map<Bereich, Double?>,
        val einordnung: Einordnung,
        val gruende: List<Grund>,
        // Anteil der Gewichte mit Daten, 0 bis 1
        val abdeckung: Double,
        // Alle Kennzahlen mit Punktzahl, fuer die Erklaerung im Detail
        val kennzahlen: Map<Kennzahl, Double>,
    ) {
        val symbol: String get() = daten.symbol
    }

    // Annahmen fuer „Was im Kurs steckt“ wie auf der Wertpapierseite (Bewertungsblick).
    private const val RENDITE = Bewertungsblick.RENDITE
    private const val JAHRE = Bewertungsblick.JAHRE

    // Grenzen der Einordnung nach der Punktzahl der Bewertung.
    const val GUENSTIG_AB = 62.0
    const val TEUER_BIS = 38.0

    // Hoechstes erwartetes Wachstum pro Jahr fuer „Was im Kurs steckt“.
    const val IM_KURS_HOECHSTENS = 25.0

    // Kennzahlen, die so wenig von 50 abweichen, nennt die Liste der Gruende nicht.
    private const val GRUND_MINDEST = 12.0

    fun begrenzen(v: Double): Double = v.coerceIn(0.0, 100.0)

    // Bewertung relativ zum Mittel der Branche: genau im Mittel 50, halb so teuer 100, doppelt so teuer 0.
    fun relativ(wert: Double?, mittel: Double?): Double? {
        if (wert == null || mittel == null || !(wert > 0) || !(mittel > 0) || !wert.isFinite()) return null
        return begrenzen(50.0 + 50.0 * ln(mittel / wert) / ln(2.0))
    }

    // Median ab mindestens min Werten; nur positive, endliche Werte bis grenze.
    fun median(werte: List<Double>, min: Int = 5, grenze: Double = 200.0): Double? {
        val s = werte.filter { it > 0 && it.isFinite() && it <= grenze }.sorted()
        if (s.size < min) return null
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    // Erwartetes Gewinnwachstum in Prozent aus dem Gewinn je Aktie der letzten zwoelf Monate und dem erwarteten.
    // Nur bei positivem Gewinn, auf -50 bis +100 % begrenzt.
    fun wachstum(gewinn: Double?, erwartet: Double?): Double? {
        if (gewinn == null || erwartet == null || !(gewinn > 0) || !erwartet.isFinite()) return null
        return ((erwartet / gewinn - 1.0) * 100.0).coerceIn(-50.0, 100.0)
    }

    // Punktzahl je Kennzahl. Die Schwellen stehen in der Erklaerung der Oberflaeche.
    // PEG 1 gilt als fair (50 Punkte), 0,5 ergibt 70, 2 ergibt 10.
    fun pegPunkte(peg: Double): Double = begrenzen(50.0 - (peg - 1.0) * 40.0)
    fun imKursPunkte(noetig: Double, erwartet: Double): Double = begrenzen(50.0 - (noetig - erwartet) * 5.0)
    fun renditePunkte(rendite: Double): Double = begrenzen(30.0 + rendite * 3.5)
    fun wachstumPunkte(w: Double): Double = begrenzen(50.0 + w * 10.0 / 3.0)
    fun margePunkte(m: Double): Double = begrenzen(30.0 + m * 2.8)
    fun schuldenPunkte(v: Double): Double = begrenzen(100.0 - v * 40.0)
    fun bilanzPunkte(a: Bewertungsblick.Ampel): Double = when (a) { Bewertungsblick.Ampel.GRUEN -> 90.0; Bewertungsblick.Ampel.GELB -> 55.0; Bewertungsblick.Ampel.ROT -> 15.0 }
    // F-Score 0 bis 9 gleichmaessig auf 0 bis 100 (4,5 ist die Mitte); Altman sicher 85, Grauzone 50, gefaehrdet 10.
    fun fScorePunkte(f: Double): Double = begrenzen(f / 9.0 * 100.0)
    fun zonePunkte(z: Qualitaet.Zone): Double = when (z) { Qualitaet.Zone.SICHER -> 85.0; Qualitaet.Zone.GRAU -> 50.0; Qualitaet.Zone.GEFAEHRDET -> 10.0 }
    fun notePunkte(note: Double): Double = begrenzen(50.0 + (2.5 - note) * 50.0)
    fun kurszielPunkte(potenzial: Double): Double = begrenzen(40.0 + potenzial * 2.4)
    fun bereinigtPunkte(b: Double): Double = begrenzen(50.0 + b * 35.0)
    fun liniePunkte(abstand: Double): Double = begrenzen(50.0 + abstand * 2.5)
    // RSI als Trendstaerke: der Wert selbst von 0 bis 100 (50 = unauffaellig)
    fun rsiPunkte(rsi: Double): Double = begrenzen(rsi)
    fun insiderPunkte(wert: Int, cluster: Boolean): Double = begrenzen(50.0 + wert / 2.0 + if (cluster) 10.0 else 0.0)
    fun leerPunkte(anteil: Double, us: Boolean): Double = begrenzen(100.0 - anteil * if (us) 8.0 else 15.0)

    // Steigende Erwaehnungen heben die Stimmung, fallende senken sie; unter fuenf Erwaehnungen keine Angabe.
    fun erwaehnungPunkte(jetzt: Int, vorher: Int?): Double? {
        if (jetzt < 5) return null
        val v = vorher ?: return 55.0
        return begrenzen(50.0 + ln((jetzt + 5.0) / (v + 5.0)) / ln(2.0) * 25.0)
    }

    // Rang von 0 bis 100 innerhalb der Liste (gleiche Werte gleicher Rang).
    fun raenge(werte: Map<String, Double>): Map<String, Double> {
        if (werte.size < 2) return werte.mapValues { 50.0 }
        val sortiert = werte.values.sorted()
        val n = sortiert.size
        return werte.mapValues { (_, v) ->
            val unter = sortiert.count { it < v }
            val gleich = sortiert.count { it == v }
            (unter + (gleich - 1) / 2.0) / (n - 1) * 100.0
        }
    }

    // Banken und Versicherungen arbeiten mit fremdem Geld; Schulden und Bilanzampel sagen dort wenig.
    private val OHNE_SCHULDEN = setOf("BANKEN", "VERSICHERUNG")

    // Ob die Bilanzqualitaet fuer eine Branche zaehlt (nicht bei Banken und Versicherungen).
    fun mitBilanz(branche: String): Boolean = branche !in OHNE_SCHULDEN

    // Teilkennzahlen der Bilanz. Fehlen sie bei einer Firma ausserhalb der Finanzbranche, zaehlen sie in der Qualitaet
    // neutral mit 50, damit Werte mit und ohne geladene Bilanz vergleichbar bleiben.
    val BILANZTEIL = listOf(Kennzahl.PIOTROSKI, Kennzahl.ALTMAN)
    private val BILANZ_GEWICHT = BILANZTEIL.sumOf { it.gewicht }
    private val QUALITAET_SONST = Kennzahl.entries.filter { it.bereich == Bereich.QUALITAET && it !in BILANZTEIL && it != Kennzahl.BILANZ }

    // Gesamtpunktzahl aus den Punkten je Bereich: Schnitt nach den Gewichten, ein Bereich ohne Daten zaehlt mit 50.
    // Auch fuer den Rueckblick-Test mit anderen Gewichten (FinderRueckblickMessung).
    fun gesamt(bereiche: Map<Bereich, Double?>, gewichte: Map<Bereich, Double>): Double {
        val summe = Bereich.entries.sumOf { (gewichte[it] ?: 0.0).coerceAtLeast(0.0) }
        if (!(summe > 0)) return 50.0
        return Bereich.entries.sumOf { (gewichte[it] ?: 0.0).coerceAtLeast(0.0) * (bereiche[it] ?: 50.0) } / summe
    }

    // Kennzahlen, die nur der Einzelabruf liefert (er laeuft zuerst fuer die besten Werte). Sie zaehlen im Bereich erst,
    // wenn sie fuer mindestens die Haelfte der Vergleichsgruppe vorliegen: sonst stuenden die Werte mit Einzelabruf
    // gegen die Mediane der anderen Sieger, und allein das Nachladen verschoebe ihre Punkte und damit die Rangfolge.
    // Bis dahin stehen sie in der aufgeklappten Zeile, ohne die Punktzahl zu bewegen.
    val NUR_EINZELABRUF = setOf(Kennzahl.KUV, Kennzahl.EV_EBITDA, Kennzahl.MARGE, Kennzahl.SCHULDEN, Kennzahl.KURSZIEL)
    const val EINZELABRUF_ABDECKUNG = 0.5

    // Welche der NUR_EINZELABRUF-Kennzahlen fuer diese Liste zaehlen.
    fun zaehlende(liste: List<Daten>): Set<Kennzahl> {
        if (liste.isEmpty()) return emptySet()
        fun anteil(kz: Kennzahl): Double {
            val grundmenge = if (kz == Kennzahl.SCHULDEN) liste.filter { mitBilanz(it.branche) } else liste
            if (grundmenge.isEmpty()) return 0.0
            val da = grundmenge.count { d ->
                when (kz) {
                    Kennzahl.KUV -> d.kuv?.let { it > 0 } == true
                    Kennzahl.EV_EBITDA -> d.evEbitda?.let { it > 0 } == true
                    Kennzahl.MARGE -> d.marge != null
                    Kennzahl.SCHULDEN -> d.verschuldung?.let { it >= 0 } == true
                    else -> d.kursziel != null
                }
            }
            return da.toDouble() / grundmenge.size
        }
        return NUR_EINZELABRUF.filterTo(HashSet()) { anteil(it) >= EINZELABRUF_ABDECKUNG }
    }

    // Bewertet alle Werte der Liste. Die Mittel der Branchen und die Raenge des Kurstrends gelten innerhalb der Liste,
    // deshalb immer das ganze geladene Universum uebergeben und erst danach filtern.
    fun bewerten(liste: List<Daten>, gewichte: Map<Bereich, Double> = Bereich.entries.associateWith { it.gewicht.toDouble() }): List<Ergebnis> {
        val g = Bereich.entries.associateWith { (gewichte[it] ?: 0.0).coerceAtLeast(0.0) }
        val summe = g.values.sum().takeIf { it > 0 } ?: return emptyList()
        // Mittel je Branche, sonst des ganzen Universums
        fun mittel(f: (Daten) -> Double?, grenze: Double): Map<String, Double?> {
            val alle = median(liste.mapNotNull(f), grenze = grenze)
            return liste.groupBy { it.branche }.mapValues { (_, l) -> median(l.mapNotNull(f), grenze = grenze) ?: alle }
        }
        val mKgv = mittel({ it.kgv }, 150.0)
        val mKgvE = mittel({ it.kgvErwartet }, 150.0)
        val mKbv = mittel({ it.kbv }, 50.0)
        val mKuv = mittel({ it.kuv }, 60.0)
        val mEv = mittel({ it.evEbitda }, 100.0)
        val zaehlend = zaehlende(liste)
        fun zaehlt(kz: Kennzahl) = kz !in NUR_EINZELABRUF || kz in zaehlend
        val jahrRang = raenge(liste.mapNotNull { d -> d.jahr?.takeIf { it.isFinite() }?.let { d.symbol to it } }.toMap())

        return liste.map { d ->
            val k = LinkedHashMap<Kennzahl, Double>()
            val werte = HashMap<Kennzahl, Pair<Double, Double?>>()
            fun setze(kz: Kennzahl, punkte: Double?, wert: Double?, vergleich: Double? = null) {
                if (punkte == null || wert == null || !punkte.isFinite()) return
                k[kz] = punkte
                werte[kz] = wert to vergleich
            }
            val b = d.branche
            setze(Kennzahl.KGV, relativ(d.kgv, mKgv[b]), d.kgv, mKgv[b])
            setze(Kennzahl.KGV_ERWARTET, relativ(d.kgvErwartet, mKgvE[b]), d.kgvErwartet, mKgvE[b])
            setze(Kennzahl.KBV, relativ(d.kbv, mKbv[b]), d.kbv, mKbv[b])
            val w = wachstum(d.gewinn, d.gewinnErwartet)
            // Ersatz mit dem KGV der letzten zwoelf Monate: das erwartete KGV enthaelt das Wachstum schon (Damodaran)
            val peg = d.peg?.takeIf { it > 0 && it < 20 } ?: if (w != null && w > 1.0 && d.kgv != null && d.kgv > 0) d.kgv / w else null
            setze(Kennzahl.PEG, peg?.let { pegPunkte(it) }, peg)
            // Was im Kurs steckt: noetiges Wachstum fuer das heutige KGV gegen das erwartete, dieses fuer zehn Jahre
            // hoechstens 25 % pro Jahr (ein einzelnes Jahr mit Erholung ist kein Dauerzustand)
            val noetig = d.kgv?.let { ReverseDcf.wachstum(it, RENDITE, mKgv[b]?.coerceIn(8.0, 35.0) ?: Bewertungsblick.ZIEL_KGV, JAHRE) }?.takeIf { !it.ausserhalb }?.wachstum
            if (noetig != null && w != null) w.coerceAtMost(IM_KURS_HOECHSTENS).let { setze(Kennzahl.IM_KURS, imKursPunkte(noetig, it), noetig, it) }
            setze(Kennzahl.KUV, relativ(d.kuv, mKuv[b]), d.kuv, mKuv[b])
            setze(Kennzahl.EV_EBITDA, relativ(d.evEbitda, mEv[b]), d.evEbitda, mEv[b])

            val rendite = d.eigenkapitalRendite ?: if (d.gewinn != null && d.buchwert != null && d.buchwert > 0) d.gewinn / d.buchwert * 100.0 else null
            setze(Kennzahl.RENDITE, rendite?.let { renditePunkte(it) }, rendite)
            setze(Kennzahl.WACHSTUM, w?.let { wachstumPunkte(it) }, w)
            setze(Kennzahl.MARGE, d.marge?.let { margePunkte(it) }, d.marge)
            val bilanzZaehlt = mitBilanz(b)
            if (bilanzZaehlt) {
                setze(Kennzahl.SCHULDEN, d.verschuldung?.takeIf { it >= 0 }?.let { schuldenPunkte(it) }, d.verschuldung)
                setze(Kennzahl.PIOTROSKI, d.fScore?.takeIf { it in 0.0..9.0 }?.let { fScorePunkte(it) }, d.fScore)
                setze(Kennzahl.ALTMAN, d.zone?.let { zonePunkte(it) }, d.zone?.ordinal?.toDouble())
                if (d.fScore == null && d.zone == null) d.bilanz?.let { setze(Kennzahl.BILANZ, bilanzPunkte(it), it.ordinal.toDouble()) }
            }

            d.marktmacht?.let { bg ->
                val a = bg.angaben
                bg.teile.forEach { (teil, p) ->
                    when (teil) {
                        Marktmacht.Teil.STELLUNG -> setze(Kennzahl.STELLUNG, p, a.stellung?.anteil, a.stellung?.rang?.toDouble())
                        Marktmacht.Teil.PREISMACHT -> setze(if (a.brutto) Kennzahl.PREIS_BRUTTO else Kennzahl.PREIS_BETRIEB, p, a.marge, a.branchenMarge)
                        Marktmacht.Teil.BESTAENDIGKEIT -> setze(Kennzahl.BESTAENDIG, p, a.renditeMin, a.renditeJahre.toDouble())
                    }
                }
            }
            setze(Kennzahl.LAND, d.landPunkte, d.landPunkte)
            setze(Kennzahl.ROHSTOFFE, d.rohstoffPunkte, d.rohstoffPunkte)
            setze(Kennzahl.NOTE, d.note?.takeIf { it in 1.0..5.0 }?.let { notePunkte(it) }, d.note)
            setze(Kennzahl.KURSZIEL, d.kursziel?.let { kurszielPunkte(it) }, d.kursziel)
            setze(Kennzahl.BEREINIGT, d.bereinigt?.let { bereinigtPunkte(it) }, d.bereinigt)

            setze(Kennzahl.JAHR, jahrRang[d.symbol], d.jahr)
            setze(Kennzahl.LINIE200, d.linie200?.let { liniePunkte(it) }, d.linie200)
            // Ohne Gewicht: nur gerechnet, fuer Anzeige und Rueckblick-Test
            d.werte[FinderVorlagen.Feld.RSI]?.takeIf { it.isFinite() }?.let { setze(Kennzahl.RSI, rsiPunkte(it), it) }
            d.werte[FinderVorlagen.Feld.LINIE50]?.takeIf { it.isFinite() }?.let { setze(Kennzahl.LINIE50, liniePunkte(it), it) }
            setze(Kennzahl.INSIDER, d.insider?.let { insiderPunkte(it, d.insiderCluster) }, d.insider?.toDouble())
            setze(Kennzahl.LEER, d.leer?.takeIf { it >= 0 }?.let { leerPunkte(it, d.leerUs) }, d.leer)
            setze(Kennzahl.ERWAEHNUNGEN, d.erwaehnungen?.let { erwaehnungPunkte(it, d.erwaehnungenVorher) }, d.erwaehnungen?.toDouble(), d.erwaehnungenVorher?.toDouble())

            // Fehlende Bilanzteile in der Qualitaet neutral, sofern der Bereich ueberhaupt Daten hat
            val neutral = if (bilanzZaehlt && Kennzahl.BILANZ !in k && k.keys.any { it.bereich == Bereich.QUALITAET && zaehlt(it) }) BILANZTEIL.filter { it !in k } else emptyList()
            // Kennzahlen ohne Gewicht zaehlen nicht zum Bereich
            val teile = Bereich.entries.associateWith { br -> k.keys.filter { it.bereich == br && it.gewicht > 0 && zaehlt(it) } + neutral.filter { it.bereich == br } }
            val bereiche = Bereich.entries.associateWith { br ->
                val l = teile.getValue(br)
                if (l.isEmpty()) null else l.sumOf { it.gewicht * (k[it] ?: 50.0) } / l.sumOf { it.gewicht }
            }
            val gesamt = gesamt(bereiche, g)
            // Datenlage: ein Bereich mit Daten zaehlt voll; in der Qualitaet zaehlt die Bilanz mit ihrem Anteil (3 von 7)
            fun anteil(br: Bereich): Double = when {
                bereiche[br] == null -> 0.0
                br != Bereich.QUALITAET || !bilanzZaehlt -> 1.0
                else -> {
                    val sonst = QUALITAET_SONST.sumOf { it.gewicht }
                    val bilanz = if (Kennzahl.BILANZ in k) BILANZ_GEWICHT else BILANZTEIL.filter { it in k }.sumOf { it.gewicht }
                    ((if (QUALITAET_SONST.any { it in k && zaehlt(it) }) sonst else 0.0) + bilanz) / (sonst + BILANZ_GEWICHT)
                }
            }
            val abdeckung = Bereich.entries.sumOf { g.getValue(it) * anteil(it) } / summe
            // Anteil jeder Kennzahl an der Abweichung von 50: Gewicht des Bereichs mal Anteil der Kennzahl darin
            val gruende = k.mapNotNull { (kz, p) ->
                if (kz.gewicht <= 0.0 || !zaehlt(kz) || abs(p - 50.0) < GRUND_MINDEST) return@mapNotNull null
                val beitrag = (p - 50.0) * g.getValue(kz.bereich) / summe * kz.gewicht / teile.getValue(kz.bereich).sumOf { it.gewicht }
                if (beitrag == 0.0) return@mapNotNull null
                val (wert, vergleich) = werte.getValue(kz)
                Grund(kz, p > 50.0, wert, vergleich, beitrag)
            }.sortedByDescending { abs(it.beitrag) }.take(3)
            val bew = k.keys.count { it.bereich == Bereich.BEWERTUNG && zaehlt(it) }
            val einordnung = when {
                bew < 2 -> Einordnung.UNKLAR
                bereiche[Bereich.BEWERTUNG]!! >= GUENSTIG_AB -> Einordnung.GUENSTIG
                bereiche[Bereich.BEWERTUNG]!! <= TEUER_BIS -> Einordnung.TEUER
                else -> Einordnung.FAIR
            }
            Ergebnis(d, gesamt, bereiche, einordnung, gruende, abdeckung, k)
        }
    }

    // Groessenklassen nach Boersenwert in Euro: klein unter 2 Mrd., mittel bis 10 Mrd., gross darueber.
    enum class Groesse { KLEIN, MITTEL, GROSS }

    fun groesse(boersenwert: Double?): Groesse? = when {
        boersenwert == null || !(boersenwert > 0) -> null
        boersenwert < 2e9 -> Groesse.KLEIN
        boersenwert < 10e9 -> Groesse.MITTEL
        else -> Groesse.GROSS
    }

    // Wonach die Liste sortiert: Gesamtpunktzahl, ein Bereich, Dividende oder Boersenwert (jeweils absteigend).
    sealed interface Sortierung {
        data object Gesamt : Sortierung
        data class Teil(val bereich: Bereich) : Sortierung
        data object Dividende : Sortierung
        data object Boersenwert : Sortierung
        // Nach einem Feld der Vorlagen (5.94); Werte ohne Angabe ans Ende
        data class NachFeld(val feld: FinderVorlagen.Feld, val aufsteigend: Boolean = false) : Sortierung
        // [0.78] Nach der Rangsumme ueber mehrere Felder, kleinste Summe zuerst; Werte ohne alle Felder ans Ende
        data class Rang(val felder: List<FinderRaster.Rangfeld>) : Sortierung
    }

    data class Filter(
        val branche: String? = null,
        val land: String? = null,
        val region: String? = null,
        val groesse: Groesse? = null,
        // Mindestdividende in Prozent
        val dividende: Double? = null,
        val einordnung: Einordnung? = null,
        // Mindestpunktzahl in dem Bereich, nach dem sortiert wird (sonst gesamt)
        val mindest: Double = 0.0,
        // Nur Werte mit gruener Bilanzampel
        val solideBilanz: Boolean = false,
        // Harte Grenzen und die Vorlage, aus der sie stammen (5.94)
        val grenzen: List<FinderVorlagen.Grenze> = emptyList(),
        val vorlage: String? = null,
    )

    // Filtern und sortieren; region bildet das Land ueber regionVon auf einen Regionsschluessel ab.
    // Mit Grenzen stehen geprueft passende Werte vor denen, die noch auf den Einzelabruf oder die Tageskurse warten.
    fun auswahl(
        liste: List<Ergebnis>, f: Filter, s: Sortierung, jetzt: Long = System.currentTimeMillis(), regionVon: (String) -> String = { it },
    ): List<Ergebnis> = auswahlGeprueft(liste, f, s, jetzt, regionVon).liste

    // Die Auswahl mit dem Pruefergebnis je Wert (leer ohne Grenzen und Vorlage).
    class Auswahl(val liste: List<Ergebnis>, val pruefung: Map<String, FinderVorlagen.Pruefung>)

    // [0.78] Erst die einfachen Filter, dann die Grenzen und Regeln der Vorlage unter den uebrigen Werten: Anteilsgrenzen,
    // Spitze und Rangsumme beziehen sich so auf den gefilterten Markt (FinderRaster).
    fun auswahlGeprueft(
        liste: List<Ergebnis>, f: Filter, s: Sortierung, jetzt: Long = System.currentTimeMillis(), regionVon: (String) -> String = { it },
    ): Auswahl {
        val vorlage = FinderVorlagen.vorlage(f.vorlage)
        val einfach = liste.filter { e ->
            val d = e.daten
            (f.branche == null || d.branche == f.branche) &&
                (f.land == null || d.land == f.land) &&
                (f.region == null || regionVon(d.land) == f.region) &&
                (f.groesse == null || groesse(d.boersenwert) == f.groesse) &&
                (f.dividende == null || (d.dividende ?: 0.0) >= f.dividende) &&
                (f.einordnung == null || e.einordnung == f.einordnung) &&
                (!f.solideBilanz || d.bilanz == Bewertungsblick.Ampel.GRUEN) &&
                (f.mindest <= 0.0 || (when (s) { is Sortierung.Teil -> e.bereiche[s.bereich]; else -> e.gesamt } ?: -1.0) >= f.mindest)
        }
        val pruefung = if (f.grenzen.isEmpty() && vorlage?.ohneBranchen.isNullOrEmpty()) emptyMap()
        else FinderRaster.pruefen(einfach.map { it.daten }, f.grenzen, vorlage, jetzt)
        val passend = einfach.filter { pruefung[it.symbol] != FinderVorlagen.Pruefung.NEIN }
        val rang = (s as? Sortierung.Rang)?.let { r ->
            FinderRaster.rangsumme(r.felder.map { rf -> passend.mapNotNull { e -> FinderVorlagen.wert(e.daten, rf.feld, jetzt)?.let { e.symbol to it } }.toMap() to rf.oben })
        }
        fun wert(e: Ergebnis): Double? = when (s) {
            Sortierung.Gesamt -> e.gesamt
            is Sortierung.Teil -> e.bereiche[s.bereich]
            Sortierung.Dividende -> e.daten.dividende ?: 0.0
            Sortierung.Boersenwert -> e.daten.boersenwert ?: 0.0
            is Sortierung.NachFeld -> FinderVorlagen.wert(e.daten, s.feld, jetzt)?.let { if (s.aufsteigend) -it else it }
            is Sortierung.Rang -> rang?.get(e.symbol)?.let { -it }
        }
        return Auswahl(
            passend.sortedWith(
                compareBy<Ergebnis> { pruefung[it.symbol] == FinderVorlagen.Pruefung.OFFEN }
                    .thenByDescending { wert(it) ?: Double.NEGATIVE_INFINITY }.thenByDescending { it.gesamt }.thenBy { it.symbol },
            ),
            pruefung,
        )
    }

    // Reihenfolge halten, waehrend Daten eintreffen: bekannte Werte an ihrem alten Platz, neu passende hinten an.
    // Werte, die nicht mehr passen, fallen heraus.
    fun ordnungHalten(neu: List<Ergebnis>, alt: List<String>): List<Ergebnis> {
        val nach = neu.associateBy { it.symbol }
        val vorne = alt.mapNotNull { nach[it] }
        val dabei = vorne.mapTo(HashSet()) { it.symbol }
        return vorne + neu.filter { it.symbol !in dabei }
    }

    // Werte, deren Bilanz noch fehlt, die besten zuerst; Banken und Versicherungen brauchen keine.
    fun fuerBilanz(liste: List<Ergebnis>, vorhanden: (String) -> Boolean): List<String> =
        liste.filter { mitBilanz(it.daten.branche) && !vorhanden(it.symbol) }.sortedByDescending { it.gesamt }.map { it.symbol }

    // Kandidaten fuer die Einzelabrufe: die besten n nach Gesamtpunktzahl, die noch keine Details haben.
    fun fuerDetails(liste: List<Ergebnis>, n: Int): List<String> =
        liste.filter { !it.daten.mitDetails }.sortedByDescending { it.gesamt }.take(n).map { it.symbol }

    // Insider-Trend fuer den Finder: bei weniger als drei Personen zur Mitte gezogen (eine Person zaehlt ein Drittel).
    fun insiderWert(e: InsiderTrend.Ergebnis): Int = Math.round(e.wert * minOf(1.0, (e.kaeufer + e.verkaeufer) / 3.0)).toInt()

    // Die Note der Analysten steht bei Yahoo als Text wie „2.3 - Buy“; die Zahl davor, sonst null.
    fun noteAusText(text: String): Double? = text.trim().substringBefore(' ').replace(',', '.').toDoubleOrNull()?.takeIf { it in 1.0..5.0 }
}
