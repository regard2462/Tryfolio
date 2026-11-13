package de.troetenrepublik.app.engine

// Tabellenleser fuer das alte Excel-Format (.xls): Der Strom "Workbook" kommt aus XlsLeser.arbeitsmappe (OLE2, FAT,
// Mini-Stream), hier folgen die BIFF8-Saetze des ersten Arbeitsblatts. Ergebnis ist eine Tabelle aus Text (String) und
// Zahl (Double), leere Zellen sind null. Bei jedem Formatfehler ist das Ergebnis leer, es wird nie eine Ausnahme
// weitergegeben. Keine Bibliothek.
object XlsTabelle {

    const val DATEI_MAX = 2_000_000
    private const val ZEILEN_MAX = 65_536
    private const val SPALTEN_MAX = 256
    private const val ZELLEN_MAX = 400_000

    // Erkennung am Inhalt: Signatur der Verbunddatei
    fun istVerbunddatei(b: ByteArray): Boolean =
        b.size >= 512 && (b[0].toInt() and 0xFF) == 0xD0 && (b[1].toInt() and 0xFF) == 0xCF && (b[2].toInt() and 0xFF) == 0x11 && (b[3].toInt() and 0xFF) == 0xE0

    fun lesen(bytes: ByteArray): List<List<Any?>> {
        if (bytes.size > DATEI_MAX || !istVerbunddatei(bytes)) return emptyList()
        return try {
            val strom = XlsLeser.arbeitsmappe(bytes) ?: return emptyList()
            Biff(strom).tabelle()
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ------------------------------------------------------------ kleine Lesehelfer (little endian)

    private fun u8(b: ByteArray, p: Int): Int = b[p].toInt() and 0xFF
    private fun u16(b: ByteArray, p: Int): Int = u8(b, p) or (u8(b, p + 1) shl 8)
    private fun u32(b: ByteArray, p: Int): Long = u16(b, p).toLong() or (u16(b, p + 2).toLong() shl 16)
    private fun f64(b: ByteArray, p: Int): Double = Double.fromBits(u32(b, p) or (u32(b, p + 4) shl 32))

    // ------------------------------------------------------------ BIFF8

    private class Satz(val art: Int, val pos: Int, val len: Int)

    private class Biff(private val s: ByteArray) {
        private val saetze = ArrayList<Satz>()
        private val texte = ArrayList<String>()

        init {
            var p = 0
            while (p + 4 <= s.size) {
                val len = u16(s, p + 2)
                if (p + 4 + len > s.size) break
                saetze += Satz(u16(s, p), p + 4, len)
                p += 4 + len
            }
        }

        fun tabelle(): List<List<Any?>> {
            if (saetze.isEmpty() || saetze[0].art != 0x0809 || saetze[0].len < 2 || u16(s, saetze[0].pos) != 0x0600) return emptyList()
            var blatt = -1
            var i = 0
            var tiefe = 0
            // Globale Daten bis zum EOF des Arbeitsmappenteils: Blattliste und Textvorrat
            while (i < saetze.size) {
                val r = saetze[i]
                when (r.art) {
                    0x0809 -> tiefe++
                    0x000A -> { tiefe--; if (tiefe <= 0) break }
                    0x0085 -> if (blatt < 0 && r.len >= 8 && u8(s, r.pos + 5) == 0) blatt = i
                    0x00FC -> {
                        val teile = arrayListOf(r)
                        while (i + 1 < saetze.size && saetze[i + 1].art == 0x003C) { i++; teile += saetze[i] }
                        vorrat(teile)
                    }
                }
                i++
            }
            if (blatt < 0) return emptyList()
            val start = u32(s, saetze[blatt].pos)
            val von = saetze.indexOfFirst { it.pos - 4L == start }
            if (von < 0 || saetze[von].art != 0x0809) return emptyList()
            return blattLesen(von)
        }

        // Textvorrat (SST mit CONTINUE): Zeichenketten koennen an den Satzgrenzen geteilt sein, die Fortsetzung beginnt
        // dann mit einem eigenen Byte fuer die Zeichenbreite.
        private fun vorrat(teile: List<Satz>) {
            var t = 0
            var p = teile[0].pos + 8
            fun ende(k: Int) = teile[k].pos + teile[k].len
            fun weiter(): Boolean {
                while (p >= ende(t)) {
                    if (t + 1 >= teile.size) return false
                    t++
                    p = teile[t].pos
                }
                return true
            }
            fun skip(n: Long) {
                var rest = n
                while (rest > 0) {
                    check(weiter())
                    val k = minOf(rest, (ende(t) - p).toLong()).toInt()
                    p += k
                    rest -= k
                }
            }
            val anzahl = u32(s, teile[0].pos + 4).coerceAtMost(ZELLEN_MAX.toLong()).toInt()
            while (texte.size < anzahl) {
                if (!weiter()) break
                val zeichen = u16(s, p)
                p += 2
                weiter()
                val flags = u8(s, p)
                p += 1
                var breit = (flags and 1) != 0
                var runs = 0
                var ext = 0L
                if ((flags and 8) != 0) { weiter(); runs = u16(s, p); p += 2 }
                if ((flags and 4) != 0) { weiter(); ext = u32(s, p); p += 4 }
                val sb = StringBuilder()
                var rest = zeichen
                while (rest > 0) {
                    if (p >= ende(t)) {
                        check(t + 1 < teile.size)
                        t++
                        p = teile[t].pos
                        breit = (u8(s, p) and 1) != 0
                        p += 1
                    }
                    val b = if (breit) 2 else 1
                    val k = minOf(rest, (ende(t) - p) / b)
                    check(k > 0)
                    for (j in 0 until k) sb.append(if (breit) u16(s, p + 2 * j).toChar() else u8(s, p + j).toChar())
                    p += k * b
                    rest -= k
                }
                skip(runs * 4L + ext)
                texte += sb.toString()
            }
        }

        // Zeichenkette mit eigener Laengenangabe (LABEL, STRING): Laenge u16, Flags u8, Zeichen
        private fun zeichenkette(p0: Int, ende: Int): String {
            var p = p0
            val zeichen = u16(s, p)
            p += 2
            val flags = u8(s, p)
            p += 1
            if ((flags and 8) != 0) p += 2
            if ((flags and 4) != 0) p += 4
            val breit = (flags and 1) != 0
            val sb = StringBuilder()
            for (j in 0 until zeichen) {
                if (breit) {
                    if (p + 2 > ende) break
                    sb.append(u16(s, p).toChar())
                    p += 2
                } else {
                    if (p + 1 > ende) break
                    sb.append(u8(s, p).toChar())
                    p += 1
                }
            }
            return sb.toString()
        }

        private fun rk(w: Long): Double {
            val geteilt = (w and 1L) != 0L
            val v = if ((w and 2L) != 0L) (w.toInt() shr 2).toDouble() else Double.fromBits((w and 0xFFFFFFFCL) shl 32)
            return if (geteilt) v / 100.0 else v
        }

        private fun blattLesen(von: Int): List<List<Any?>> {
            val zellen = HashMap<Int, Any>()
            var maxZ = -1
            var maxS = -1
            var zaehler = 0
            fun setze(z: Int, sp: Int, v: Any) {
                if (z !in 0 until ZEILEN_MAX || sp !in 0 until SPALTEN_MAX) return
                check(++zaehler <= ZELLEN_MAX)
                zellen[z * SPALTEN_MAX + sp] = v
                if (z > maxZ) maxZ = z
                if (sp > maxS) maxS = sp
            }
            var tiefe = 0
            var formelZ = -1
            var formelS = -1
            var i = von
            while (i < saetze.size) {
                val r = saetze[i]
                val p = r.pos
                when (r.art) {
                    0x0809 -> tiefe++
                    0x000A -> { tiefe--; if (tiefe <= 0) break }
                    0x00FD -> if (r.len >= 10) {
                        val k = u32(s, p + 6)
                        if (k < texte.size) setze(u16(s, p), u16(s, p + 2), texte[k.toInt()])
                    }
                    0x0204 -> if (r.len >= 9) setze(u16(s, p), u16(s, p + 2), zeichenkette(p + 6, p + r.len))
                    0x0203 -> if (r.len >= 14) setze(u16(s, p), u16(s, p + 2), f64(s, p + 6))
                    0x027E -> if (r.len >= 10) setze(u16(s, p), u16(s, p + 2), rk(u32(s, p + 6)))
                    0x00BD -> if (r.len >= 6) {
                        val z = u16(s, p)
                        val erste = u16(s, p + 2)
                        val n = (r.len - 6) / 6
                        for (j in 0 until n) setze(z, erste + j, rk(u32(s, p + 4 + 6 * j + 2)))
                    }
                    0x0006 -> if (r.len >= 20) {
                        formelZ = -1
                        val z = u16(s, p)
                        val sp = u16(s, p + 2)
                        if (u8(s, p + 12) == 0xFF && u8(s, p + 13) == 0xFF) {
                            // Sonderergebnis: Typ 0 ist ein Text, der im folgenden STRING-Satz steht
                            if (u8(s, p + 6) == 0) { formelZ = z; formelS = sp }
                        } else setze(z, sp, f64(s, p + 6))
                    }
                    0x0207 -> if (formelZ >= 0 && r.len >= 3) {
                        setze(formelZ, formelS, zeichenkette(p, p + r.len))
                        formelZ = -1
                    }
                }
                i++
            }
            if (maxZ < 0) return emptyList()
            return List(maxZ + 1) { z -> List(maxS + 1) { sp -> zellen[z * SPALTEN_MAX + sp] } }
        }
    }
}
