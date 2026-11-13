package de.troetenrepublik.app.engine

// Liest Zahlen aus alten Excel-Dateien (.xls, BIFF8 in einer OLE2-Huelle), wie sie die EIA fuer ihre Zeitreihen
// anbietet. Gebraucht werden nur Zahlenzellen (NUMBER, RK, MULRK, Formelergebnisse); Texte, Formate und Formeln
// selbst bleiben aussen vor. Ohne Bibliothek, damit die App klein bleibt.
object XlsLeser {

    private const val FREI = -1
    private const val ENDE = -2

    private fun u16(b: ByteArray, i: Int): Int = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
    private fun i32(b: ByteArray, i: Int): Int = u16(b, i) or (u16(b, i + 2) shl 16)
    private fun f64(b: ByteArray, i: Int): Double {
        var l = 0L
        for (k in 7 downTo 0) l = (l shl 8) or (b[i + k].toLong() and 0xFF)
        return Double.fromBits(l)
    }

    // Datenstrom "Workbook" (aeltere Dateien: "Book") aus der OLE2-Huelle; null, wenn die Datei keine ist.
    fun arbeitsmappe(d: ByteArray): ByteArray? {
        if (d.size < 512 || i32(d, 0) != 0xE011CFD0.toInt() || i32(d, 4) != 0xE11AB1A1.toInt()) return null
        val sektor = 1 shl u16(d, 0x1E)
        val mini = 1 shl u16(d, 0x20)
        val grenze = i32(d, 0x38)
        fun ort(s: Int) = 512 + s * sektor
        fun sektorOk(s: Int) = s >= 0 && ort(s) + sektor <= d.size

        // Liste der FAT-Sektoren: 109 im Kopf, weitere in der DIFAT-Kette
        val fatSektoren = ArrayList<Int>()
        for (k in 0 until 109) i32(d, 0x4C + k * 4).takeIf { it >= 0 }?.let(fatSektoren::add)
        var difat = i32(d, 0x44)
        var schutz = 0
        while (difat >= 0 && sektorOk(difat) && schutz++ < 10_000) {
            for (k in 0 until sektor / 4 - 1) i32(d, ort(difat) + k * 4).takeIf { it >= 0 }?.let(fatSektoren::add)
            difat = i32(d, ort(difat) + sektor - 4)
        }
        val fat = IntArray(fatSektoren.size * sektor / 4)
        fatSektoren.forEachIndexed { n, s -> if (sektorOk(s)) for (k in 0 until sektor / 4) fat[n * sektor / 4 + k] = i32(d, ort(s) + k * 4) }

        // Kette ab start lesen, hoechstens laenge Bytes
        fun kette(start: Int, laenge: Int): ByteArray? {
            val aus = java.io.ByteArrayOutputStream()
            var s = start
            var n = 0
            while (s >= 0 && n++ <= fat.size) {
                if (!sektorOk(s)) return null
                aus.write(d, ort(s), sektor)
                s = fat.getOrElse(s) { ENDE }
            }
            val b = aus.toByteArray()
            return if (laenge in 0..b.size) b.copyOf(laenge) else b
        }

        val verzeichnis = kette(i32(d, 0x30), -1) ?: return null
        data class Eintrag(val name: String, val typ: Int, val start: Int, val groesse: Int)
        val eintraege = (0 until verzeichnis.size / 128).map { k ->
            val o = k * 128
            val nl = u16(verzeichnis, o + 64).coerceIn(0, 64)
            val name = String(verzeichnis, o, (nl - 2).coerceAtLeast(0), Charsets.UTF_16LE)
            Eintrag(name, verzeichnis[o + 66].toInt(), i32(verzeichnis, o + 116), i32(verzeichnis, o + 120))
        }
        val buch = eintraege.firstOrNull { it.typ == 2 && (it.name == "Workbook" || it.name == "Book") } ?: return null
        if (buch.groesse >= grenze) return kette(buch.start, buch.groesse)

        // Kleiner Strom: liegt im Ministrom der Wurzel, verkettet ueber die Mini-FAT
        val wurzel = eintraege.firstOrNull { it.typ == 5 } ?: return null
        val ministrom = kette(wurzel.start, wurzel.groesse) ?: return null
        val minifatRoh = kette(i32(d, 0x3C), -1) ?: return null
        val minifat = IntArray(minifatRoh.size / 4) { i32(minifatRoh, it * 4) }
        val aus = java.io.ByteArrayOutputStream()
        var s = buch.start
        var n = 0
        while (s >= 0 && n++ <= minifat.size) {
            if ((s + 1) * mini > ministrom.size) return null
            aus.write(ministrom, s * mini, mini)
            s = minifat.getOrElse(s) { ENDE }
        }
        return aus.toByteArray().let { if (buch.groesse <= it.size) it.copyOf(buch.groesse) else it }
    }

    // RK-Zahl: 30 Bit Ganzzahl oder die oberen 30 Bit einer Gleitkommazahl, wahlweise durch 100 geteilt.
    internal fun rk(r: Int): Double {
        val v = if (r and 2 != 0) (r shr 2).toDouble() else Double.fromBits((r.toLong() and 0xFFFFFFFCL) shl 32)
        return if (r and 1 != 0) v / 100 else v
    }

    // Zahlenzellen je Blatt (in der Reihenfolge der Blaetter): Zeile -> (Spalte -> Wert).
    fun zahlen(buch: ByteArray): List<Map<Int, Map<Int, Double>>> {
        val blaetter = ArrayList<MutableMap<Int, MutableMap<Int, Double>>>()
        var aktuell: MutableMap<Int, MutableMap<Int, Double>>? = null
        var i = 0
        fun setzen(z: Int, s: Int, v: Double) { if (v.isFinite()) aktuell?.getOrPut(z) { HashMap() }?.put(s, v) }
        while (i + 4 <= buch.size) {
            val typ = u16(buch, i)
            val len = u16(buch, i + 2)
            val o = i + 4
            if (o + len > buch.size) break
            when (typ) {
                // BOF: neuer Teilstrom; 0x0010 ist ein Arbeitsblatt
                0x0809 -> aktuell = if (len >= 4 && u16(buch, o + 2) == 0x0010) HashMap<Int, MutableMap<Int, Double>>().also(blaetter::add) else null
                0x000A -> aktuell = null
                0x0203 -> if (len >= 14) setzen(u16(buch, o), u16(buch, o + 2), f64(buch, o + 6))
                0x027E -> if (len >= 10) setzen(u16(buch, o), u16(buch, o + 2), rk(i32(buch, o + 6)))
                0x00BD -> if (len >= 6) {
                    val z = u16(buch, o)
                    val erste = u16(buch, o + 2)
                    val n = (len - 6) / 6
                    for (k in 0 until n) setzen(z, erste + k, rk(i32(buch, o + 4 + k * 6 + 2)))
                }
                // Formel: Ergebnis ist eine Zahl, wenn die letzten beiden Bytes nicht 0xFFFF sind
                0x0006 -> if (len >= 14 && u16(buch, o + 12) != 0xFFFF) setzen(u16(buch, o), u16(buch, o + 2), f64(buch, o + 6))
            }
            i = o + len
        }
        return blaetter
    }

    // Zeitreihe aus einer EIA-Datei: das Blatt mit den meisten Zeilen, die in Spalte 0 ein Datum (Excel-Tageszahl)
    // und in Spalte spalte einen Wert haben. Ergebnis als (Tag seit 1970, Wert), aelteste zuerst.
    fun reihe(datei: ByteArray, spalte: Int = 1): List<Pair<Long, Double>> {
        val buch = arbeitsmappe(datei) ?: return emptyList()
        val blatt = zahlen(buch).maxByOrNull { b -> b.values.count { it.containsKey(0) && it.containsKey(spalte) } } ?: return emptyList()
        return blatt.values.mapNotNull { z ->
            val t = z[0]?.takeIf { it > 1_000 && it < 200_000 } ?: return@mapNotNull null
            val w = z[spalte] ?: return@mapNotNull null
            // Excel zaehlt ab 30.12.1899, 25569 ist der 1.1.1970
            (t.toLong() - 25_569L) to w
        }.distinctBy { it.first }.sortedBy { it.first }
    }

    // Mehrere Spalten eines bestimmten Blatts (0 = erstes Arbeitsblatt, bei der EIA das Inhaltsblatt): je Zeile mit
    // Datum in Spalte 0 die Werte der Spalten 1 bis spalten, NaN wo eine Zahl fehlt. Etwa die Kurse der Kontrakte 1
    // bis 4 aus den Futures-Dateien der EIA (seit 5.92).
    fun tabelle(datei: ByteArray, blatt: Int, spalten: Int = 4): List<Pair<Long, DoubleArray>> {
        val buch = arbeitsmappe(datei) ?: return emptyList()
        val b = zahlen(buch).getOrNull(blatt) ?: return emptyList()
        return b.values.mapNotNull { z ->
            val t = z[0]?.takeIf { it > 1_000 && it < 200_000 } ?: return@mapNotNull null
            (t.toLong() - 25_569L) to DoubleArray(spalten) { z[it + 1] ?: Double.NaN }
        }.distinctBy { it.first }.sortedBy { it.first }
    }
}
