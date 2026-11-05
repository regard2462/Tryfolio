package de.troetenrepublik.app.engine

import java.time.Instant
import java.time.ZoneOffset

// Vorlagen und harte Grenzwerte im Aktienfinder (5.94), angelehnt an die Screener-Vorlagen von finviz. Jede Grenze
// prueft ein Feld gegen Unter- und Obergrenze (beide eingeschlossen). Die Felder kommen aus drei Stufen: dem
// Sammelabruf fuer den ganzen Markt, dem Einzelabruf (Profil) und den Tagesschlusskursen eines Jahres. Fehlt ein Wert
// einer Stufe, die fuer die Aktie noch nicht geladen ist, gilt die Aktie als ungeprueft statt als ausgeschieden; so
// laedt die Oberflaeche die zweite und dritte Stufe nur fuer Werte, die die erste schon bestanden haben.
// Grenzen in Euro (Kurs, Boersenwert) entsprechen den Dollargrenzen der Vorlagen ohne Umrechnung.
object FinderVorlagen {

    enum class Stufe { SAMMEL, EINZEL, KERZEN }

    enum class Einheit { PROZENT, FAKTOR, EURO, STUECK, TAGE, PUNKTE, JA }

    enum class Feld(val stufe: Stufe, val einheit: Einheit) {
        BOERSENWERT(Stufe.SAMMEL, Einheit.EURO), KURS(Stufe.SAMMEL, Einheit.EURO),
        KGV(Stufe.SAMMEL, Einheit.FAKTOR), KGV_ERWARTET(Stufe.SAMMEL, Einheit.FAKTOR), KBV(Stufe.SAMMEL, Einheit.FAKTOR),
        DIVIDENDE(Stufe.SAMMEL, Einheit.PROZENT), NOTE(Stufe.SAMMEL, Einheit.PUNKTE),
        JAHR(Stufe.SAMMEL, Einheit.PROZENT), LINIE200(Stufe.SAMMEL, Einheit.PROZENT), LINIE50(Stufe.SAMMEL, Einheit.PROZENT),
        // 50-Tage-Linie gegen 200-Tage-Linie in Prozent
        SMA50_200(Stufe.SAMMEL, Einheit.PROZENT),
        TAG(Stufe.SAMMEL, Einheit.PROZENT),
        // Eroeffnung gegen Vortagesschluss
        LUECKE(Stufe.SAMMEL, Einheit.PROZENT),
        VOLUMEN(Stufe.SAMMEL, Einheit.STUECK), VOLUMEN_SCHNITT(Stufe.SAMMEL, Einheit.STUECK),
        // heutiges Volumen durch den Schnitt dreier Monate
        REL_VOLUMEN(Stufe.SAMMEL, Einheit.FAKTOR),
        // Abstand zum 52-Wochen-Hoch (0 oder negativ) und zum 52-Wochen-Tief (0 oder positiv)
        HOCH52(Stufe.SAMMEL, Einheit.PROZENT), TIEF52(Stufe.SAMMEL, Einheit.PROZENT),
        // Tage bis zum naechsten Zahlentermin
        ZAHLEN_TAGE(Stufe.SAMMEL, Einheit.TAGE),
        PEG(Stufe.EINZEL, Einheit.FAKTOR), ROE(Stufe.EINZEL, Einheit.PROZENT), ROA(Stufe.EINZEL, Einheit.PROZENT),
        VERSCHULDUNG(Stufe.EINZEL, Einheit.FAKTOR), LEER(Stufe.EINZEL, Einheit.PROZENT), BRUTTOMARGE(Stufe.EINZEL, Einheit.PROZENT),
        // Gewinn und Umsatz des letzten Quartals gegen das Vorjahresquartal, erwartetes Gewinnwachstum im laufenden und
        // im folgenden Geschaeftsjahr und je Jahr ueber fuenf Jahre (aus KGV durch PEG, wo Yahoo es nicht nennt)
        GEWINN_QUARTAL(Stufe.EINZEL, Einheit.PROZENT), UMSATZ_QUARTAL(Stufe.EINZEL, Einheit.PROZENT),
        GEWINN_JAHR(Stufe.EINZEL, Einheit.PROZENT), GEWINN_FOLGEJAHR(Stufe.EINZEL, Einheit.PROZENT), GEWINN_FUENF(Stufe.EINZEL, Einheit.PROZENT),
        INSTITUTIONEN(Stufe.EINZEL, Einheit.PROZENT), BETA(Stufe.EINZEL, Einheit.FAKTOR),
        LIQUIDITAET(Stufe.EINZEL, Einheit.FAKTOR), LIQUIDITAET_SCHNELL(Stufe.EINZEL, Einheit.FAKTOR), AUSSCHUETTUNG(Stufe.EINZEL, Einheit.PROZENT),
        RSI(Stufe.KERZEN, Einheit.PUNKTE), LINIE20(Stufe.KERZEN, Einheit.PROZENT), WOCHE(Stufe.KERZEN, Einheit.PROZENT),
        JAHRESANFANG(Stufe.KERZEN, Einheit.PROZENT),
        // Abstand des Schlusskurses zum hoechsten Schluss der letzten 20 bzw. 50 Handelstage (0 = neues Hoch)
        HOCH20(Stufe.KERZEN, Einheit.PROZENT), HOCH50(Stufe.KERZEN, Einheit.PROZENT),
        // 1, wenn die 20-Tage-Linie in den letzten fuenf Handelstagen die 50-Tage-Linie nach oben gekreuzt hat
        KREUZUNG(Stufe.KERZEN, Einheit.JA),
    }

    data class Grenze(val feld: Feld, val min: Double? = null, val max: Double? = null) {
        fun passt(v: Double): Boolean = (min == null || v >= min) && (max == null || v <= max)
    }

    private const val TAG_MS = 86_400_000L

    // Wert eines Felds; null, wenn er fehlt.
    fun wert(d: Aktienfinder.Daten, f: Feld, jetzt: Long = System.currentTimeMillis()): Double? = when (f) {
        Feld.BOERSENWERT -> d.boersenwert
        Feld.KGV -> d.kgv
        Feld.KGV_ERWARTET -> d.kgvErwartet
        Feld.KBV -> d.kbv
        Feld.DIVIDENDE -> d.dividende ?: if (d.kgv != null || d.boersenwert != null) 0.0 else null
        Feld.NOTE -> d.note
        Feld.JAHR -> d.jahr
        Feld.LINIE200 -> d.linie200
        Feld.REL_VOLUMEN -> {
            val v = d.werte[Feld.VOLUMEN]
            val s = d.werte[Feld.VOLUMEN_SCHNITT]
            if (v != null && s != null && s > 0) v / s else null
        }
        Feld.ZAHLEN_TAGE -> d.zahlenTermin?.let { (it - jetzt).toDouble() / TAG_MS }
        Feld.PEG -> d.peg
        Feld.ROE -> d.eigenkapitalRendite
        Feld.VERSCHULDUNG -> d.verschuldung
        Feld.LEER -> d.leer
        Feld.GEWINN_FUENF -> d.werte[Feld.GEWINN_FUENF] ?: run {
            val k = d.kgv
            val p = d.peg
            if (k != null && p != null && k > 0 && p > 0) k / p else null
        }
        else -> d.werte[f]
    }

    // Ob die Stufe eines Felds fuer diese Aktie schon geladen ist.
    fun geladen(d: Aktienfinder.Daten, s: Stufe): Boolean = when (s) {
        Stufe.SAMMEL -> true
        Stufe.EINZEL -> d.mitDetails
        Stufe.KERZEN -> d.mitKerzen
    }

    enum class Pruefung { JA, NEIN, OFFEN }

    // JA, wenn alle Grenzen passen; NEIN, sobald eine nicht passt oder ein Wert trotz geladener Stufe fehlt; sonst OFFEN.
    fun pruefen(d: Aktienfinder.Daten, grenzen: List<Grenze>, jetzt: Long = System.currentTimeMillis()): Pruefung {
        var offen = false
        for (g in grenzen) {
            val v = wert(d, g.feld, jetzt)
            if (v == null) {
                // Ohne gemeldeten Leerverkauf gilt eine reine Obergrenze als erfuellt: ausserhalb der USA melden die
                // Register nur Positionen ab 0,5 %, keine Meldung heisst wenig Leerverkauf
                if (g.feld == Feld.LEER && g.min == null && geladen(d, g.feld.stufe)) continue
                if (geladen(d, g.feld.stufe)) return Pruefung.NEIN
                offen = true
            } else if (!g.passt(v)) return Pruefung.NEIN
        }
        return if (offen) Pruefung.OFFEN else Pruefung.JA
    }

    // Welche spaeteren Stufen eine Auswahl braucht (Grenzen und Sortierfeld).
    fun stufen(grenzen: List<Grenze>, sortierfeld: Feld? = null): Set<Stufe> =
        (grenzen.map { it.feld.stufe } + listOfNotNull(sortierfeld?.stufe)).toSet() - Stufe.SAMMEL

    // Felder der dritten Stufe aus Tagesschlusskursen (Zeit in ms, aufsteigend); leer bei weniger als 60 Kursen.
    fun kerzenWerte(zeiten: List<Long>, schluss: List<Double>): Map<Feld, Double> {
        val n = minOf(zeiten.size, schluss.size)
        if (n < 60) return emptyMap()
        val c = schluss.take(n)
        val t = zeiten.take(n)
        val letzter = c.last()
        if (!(letzter > 0)) return emptyMap()
        val aus = HashMap<Feld, Double>()
        Indikatoren.rsi(c).last()?.let { aus[Feld.RSI] = it }
        val sma20 = Indikatoren.sma(c, 20)
        val sma50 = Indikatoren.sma(c, 50)
        sma20.last()?.takeIf { it > 0 }?.let { aus[Feld.LINIE20] = (letzter / it - 1) * 100 }
        c[n - 6].takeIf { it > 0 }?.let { aus[Feld.WOCHE] = (letzter / it - 1) * 100 }
        // Letzter Schluss des Vorjahrs als Basis fuer die Rendite seit Jahresanfang
        val jahr = Instant.ofEpochMilli(t.last()).atZone(ZoneOffset.UTC).year
        val anfang = java.time.LocalDate.of(jahr, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val basis = t.indexOfLast { it < anfang }
        if (basis >= 0 && c[basis] > 0) aus[Feld.JAHRESANFANG] = (letzter / c[basis] - 1) * 100
        aus[Feld.HOCH20] = (letzter / c.takeLast(20).max() - 1) * 100
        aus[Feld.HOCH50] = (letzter / c.takeLast(50).max() - 1) * 100
        val kreuz = (n - 5 until n).any { i ->
            val a0 = sma20[i - 1]; val b0 = sma50[i - 1]; val a1 = sma20[i]; val b1 = sma50[i]
            a0 != null && b0 != null && a1 != null && b1 != null && a0 <= b0 && a1 > b1
        }
        val obenAuf = sma20.last()?.let { a -> sma50.last()?.let { b -> a > b } } == true
        aus[Feld.KREUZUNG] = if (kreuz && obenAuf) 1.0 else 0.0
        return aus
    }

    // Eine Vorlage: Nummer der finviz-Vorlage, Grenzen, Sortierung (null = Gesamtpunktzahl), nur US-Werte und ob
    // Bedingungen fehlen oder angenaehert sind (Text in der Oberflaeche).
    data class Vorlage(
        val id: String, val nummer: Int, val grenzen: List<Grenze>,
        val sortierung: Feld? = null, val aufsteigend: Boolean = false,
        val nurUs: Boolean = false, val unvollstaendig: Boolean = false,
    )

    private fun g(f: Feld, min: Double? = null, max: Double? = null) = Grenze(f, min, max)
    private const val MIO = 1e6
    private const val MRD = 1e9

    // 20 Vorlagen nach finviz; finviz-Vorlage 20 „Breaking Out“ ist dieselbe wie 10 „Ausbruch“ und steht nur einmal.
    // Dazu seit 5.94 der Gap-Scanner als 22 und 23: Eroeffnung mindestens 3 % ueber bzw. unter dem Vortagesschluss.
    val VORLAGEN: List<Vorlage> = listOf(
        Vorlage("LEERVERKAUFT", 1, listOf(
            g(Feld.BOERSENWERT, 300 * MIO), g(Feld.VOLUMEN_SCHNITT, 500_000.0), g(Feld.VOLUMEN, 500_000.0),
            g(Feld.KURS, 3.0), g(Feld.REL_VOLUMEN, 1.0), g(Feld.LEER, 20.0),
        ), Feld.LEER, nurUs = true, unvollstaendig = true),
        Vorlage("SQUEEZE", 2, listOf(
            g(Feld.VOLUMEN_SCHNITT, 100_000.0), g(Feld.INSTITUTIONEN, max = 50.0), g(Feld.KURS, 2.0), g(Feld.LEER, 15.0),
        ), Feld.LEER, nurUs = true),
        Vorlage("ZAHLENLUECKE", 3, listOf(
            g(Feld.ZAHLEN_TAGE, 0.0, 2.0), g(Feld.VOLUMEN_SCHNITT, 400_000.0), g(Feld.VOLUMEN, 50_000.0),
            g(Feld.LEER, max = 25.0), g(Feld.LUECKE, 2.0),
        ), Feld.JAHRESANFANG, unvollstaendig = true),
        Vorlage("INSOLVENZ", 4, listOf(g(Feld.KBV, 0.001, 0.999), g(Feld.LEER, 30.0)), Feld.LEER, nurUs = true),
        Vorlage("WOCHENTIEF", 5, listOf(
            g(Feld.VOLUMEN_SCHNITT, 400_000.0), g(Feld.LINIE200, 0.0), g(Feld.SMA50_200, 0.0), g(Feld.WOCHE, max = -0.01),
        ), Feld.WOCHE, aufsteigend = true, unvollstaendig = true),
        Vorlage("LINIENSPRUNG", 6, listOf(
            g(Feld.VOLUMEN_SCHNITT, 400_000.0), g(Feld.VOLUMEN, 2 * MIO), g(Feld.REL_VOLUMEN, 1.0),
            g(Feld.LINIE20, 0.0), g(Feld.LINIE50, max = 0.0),
        ), Feld.WOCHE),
        Vorlage("UMKEHR", 7, listOf(
            g(Feld.KURS, 5.0), g(Feld.REL_VOLUMEN, 2.0), g(Feld.TAG, 0.01), g(Feld.RSI, max = 30.0),
        ), Feld.KURS, aufsteigend = true),
        Vorlage("UEBERVERKAUFT_ZAHLEN", 8, listOf(
            g(Feld.BOERSENWERT, 300 * MIO), g(Feld.ZAHLEN_TAGE, 0.0, 31.0), g(Feld.GEWINN_QUARTAL, 15.0), g(Feld.BRUTTOMARGE, 20.0),
            g(Feld.VOLUMEN_SCHNITT, 750_000.0), g(Feld.VOLUMEN, 1 * MIO), g(Feld.JAHR, 10.0), g(Feld.RSI, max = 50.0),
        ), Feld.JAHRESANFANG, aufsteigend = true, unvollstaendig = true),
        Vorlage("HOECHSTSTAENDE", 9, listOf(
            g(Feld.NOTE, max = 2.0), g(Feld.KURS, max = 7.0), g(Feld.TAG, 0.01),
            g(Feld.HOCH20, 0.0), g(Feld.HOCH50, 0.0), g(Feld.HOCH52, -1.0),
        ), Feld.WOCHE, unvollstaendig = true),
        Vorlage("AUSBRUCH", 10, listOf(
            g(Feld.VERSCHULDUNG, max = 1.0), g(Feld.ROE, 20.0), g(Feld.VOLUMEN_SCHNITT, 100_000.0),
            g(Feld.HOCH50, 0.0), g(Feld.LINIE20, 0.0), g(Feld.LINIE50, 0.0), g(Feld.LINIE200, 0.0),
        ), Feld.WOCHE, unvollstaendig = true),
        Vorlage("KREUZUNG", 11, listOf(
            g(Feld.KGV, 0.01), g(Feld.VOLUMEN_SCHNITT, 400_000.0), g(Feld.REL_VOLUMEN, 1.0),
            g(Feld.LEER, max = 5.0), g(Feld.BETA, 1.0), g(Feld.KREUZUNG, 1.0),
        )),
        Vorlage("GEWINNWACHSTUM", 12, listOf(
            g(Feld.GEWINN_QUARTAL, 25.0), g(Feld.GEWINN_JAHR, 25.0), g(Feld.GEWINN_FOLGEJAHR, 25.0), g(Feld.UMSATZ_QUARTAL, 25.0),
            g(Feld.VOLUMEN_SCHNITT, 400_000.0), g(Feld.RSI, 50.0), g(Feld.LINIE200, 0.0),
        ), Feld.JAHRESANFANG),
        Vorlage("UMSATZWACHSTUM", 13, listOf(
            g(Feld.VERSCHULDUNG, max = 0.5), g(Feld.ROE, 15.0), g(Feld.UMSATZ_QUARTAL, 20.0), g(Feld.VOLUMEN_SCHNITT, 200_000.0),
            g(Feld.INSTITUTIONEN, 60.0), g(Feld.KURS, 5.0), g(Feld.LEER, max = 5.0),
        ), unvollstaendig = true),
        Vorlage("REL_VOLUMEN", 14, listOf(
            g(Feld.LIQUIDITAET, 1.0), g(Feld.GEWINN_QUARTAL, 15.0), g(Feld.LIQUIDITAET_SCHNELL, 1.0), g(Feld.UMSATZ_QUARTAL, 15.0),
            g(Feld.VOLUMEN_SCHNITT, 400_000.0), g(Feld.KURS, 5.0), g(Feld.REL_VOLUMEN, 1.5), g(Feld.LINIE20, 0.0), g(Feld.SMA50_200, 0.0),
        ), Feld.INSTITUTIONEN, aufsteigend = true),
        Vorlage("STETIG", 15, listOf(
            g(Feld.GEWINN_QUARTAL, 20.0), g(Feld.GEWINN_JAHR, 25.0), g(Feld.GEWINN_FOLGEJAHR, 15.0), g(Feld.GEWINN_FUENF, 0.01),
            g(Feld.ROE, 15.0), g(Feld.INSTITUTIONEN, 10.0), g(Feld.KURS, 15.0), g(Feld.TIEF52, 90.0), g(Feld.RSI, 50.0),
        ), Feld.JAHRESANFANG, unvollstaendig = true),
        Vorlage("HALTEN", 16, listOf(
            g(Feld.BOERSENWERT, 50 * MIO), g(Feld.LIQUIDITAET, 1.5), g(Feld.GEWINN_FUENF, 10.0), g(Feld.PEG, 1.0),
            g(Feld.ROE, 15.0), g(Feld.BETA, 1.5), g(Feld.LINIE20, 0.0),
        ), Feld.KGV_ERWARTET),
        Vorlage("DIVIDENDENWACHSTUM", 17, listOf(
            g(Feld.BOERSENWERT, 10 * MRD), g(Feld.DIVIDENDE, 0.01), g(Feld.GEWINN_FOLGEJAHR, 5.0), g(Feld.GEWINN_FUENF, 5.0),
            g(Feld.AUSSCHUETTUNG, max = 50.0), g(Feld.KGV, max = 20.0), g(Feld.PEG, max = 1.0),
        ), Feld.KGV),
        Vorlage("NIEDRIGES_KGV", 18, listOf(
            g(Feld.BOERSENWERT, max = 2 * MRD), g(Feld.KBV, 0.001, 1.0), g(Feld.KGV, max = 15.0), g(Feld.PEG, max = 1.0),
            g(Feld.ROA, 0.01), g(Feld.ROE, 0.01), g(Feld.KURS, 5.0),
        ), Feld.JAHRESANFANG),
        Vorlage("CANSLIM", 19, listOf(
            g(Feld.GEWINN_QUARTAL, 25.0), g(Feld.HOCH52, -10.0), g(Feld.VOLUMEN_SCHNITT, 100_000.0), g(Feld.REL_VOLUMEN, 1.0),
            g(Feld.JAHR, 10.0), g(Feld.INSTITUTIONEN, 10.0), g(Feld.LINIE200, 0.0),
        ), unvollstaendig = true),
        Vorlage("NEBENWERTE_WACHSTUM", 21, listOf(
            g(Feld.BOERSENWERT, 300 * MIO, 2 * MRD), g(Feld.GEWINN_QUARTAL, 20.0), g(Feld.GEWINN_JAHR, 20.0),
            g(Feld.UMSATZ_QUARTAL, 20.0), g(Feld.VOLUMEN, 200_000.0),
        ), unvollstaendig = true),
        Vorlage("LUECKE_AUF", 22, listOf(
            g(Feld.BOERSENWERT, 300 * MIO), g(Feld.VOLUMEN_SCHNITT, 200_000.0), g(Feld.KURS, 2.0), g(Feld.LUECKE, 3.0),
        ), Feld.LUECKE),
        Vorlage("LUECKE_AB", 23, listOf(
            g(Feld.BOERSENWERT, 300 * MIO), g(Feld.VOLUMEN_SCHNITT, 200_000.0), g(Feld.KURS, 2.0), g(Feld.LUECKE, max = -3.0),
        ), Feld.LUECKE, aufsteigend = true),
    )

    fun vorlage(id: String?): Vorlage? = VORLAGEN.firstOrNull { it.id == id }
}
