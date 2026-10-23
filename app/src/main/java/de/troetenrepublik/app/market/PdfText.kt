package de.troetenrepublik.app.market

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.Inflater

// Kleiner PDF-Textleser fuer die Transaktionsmeldungen des US-Repraesentantenhauses. Die Meldungen
// entstehen alle mit demselben Werkzeug: Objekte ohne Objektstroeme, Inhalte mit Flate gepackt, Schriften mit
// ToUnicode-Tabelle, verschluesselt nach dem Standardverfahren mit leerem Benutzerpasswort (RC4, nur die Rechte
// sind eingeschraenkt). Gelesen wird der Text in der Reihenfolge der Inhaltsstroeme; jede Textzeile oder
// Textposition beginnt eine neue Zeile. Tabellenzellen kommen so als ganze Zeilen, Blocksatz (Beschreibungen)
// als kurze Schnipsel. Reine JVM-Logik ohne Bibliotheken, auf Android und dem Desktop gleich.
// Gescannte Papiermeldungen enthalten keinen Text; dann ist die Liste leer.
object PdfText {

    private val PAD = "28BF4E5E4E758A4164004E56FFFA01082E2E00B6D0683E802F0CA9FE6453697A".chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private class Obj(val dict: String, val roh: ByteArray?)

    // Textzeilen aller Seiten; leer, wenn die Datei nicht lesbar ist oder keinen Text enthaelt.
    fun zeilen(pdf: ByteArray): List<String> = runCatching { lesen(pdf) }.getOrDefault(emptyList())

    private fun lesen(pdf: ByteArray): List<String> {
        val s = String(pdf, Charsets.ISO_8859_1)
        val objekte = objekte(pdf, s)
        val schluessel = schluessel(s, objekte)
        val aus = StringBuilder()
        for (seite in seiten(objekte)) {
            // Ein Inhaltsstrom oder (seit 5.9, ASX-Formulare) eine Liste, deren Teile nacheinander gelesen werden
            val inhalte = Regex("/Contents\\s+(\\d+)\\s+\\d+\\s+R").find(seite.dict)?.groupValues?.get(1)?.toInt()?.let { listOf(it) }
                ?: Regex("/Contents\\s*\\[([^\\]]*)]").find(seite.dict)?.groupValues?.get(1)
                    ?.let { l -> Regex("(\\d+)\\s+\\d+\\s+R").findAll(l).map { it.groupValues[1].toInt() }.toList() }
                ?: continue
            val ressourcen = ressourcen(seite.dict, objekte, schluessel)
            val teile = inhalte.mapNotNull { strom(objekte, schluessel, it) }
            if (teile.isEmpty()) continue
            val strom = teile.reduce { a, b -> a + "\n".toByteArray() + b }
            lauf(strom, ressourcen, objekte, schluessel, aus, 0)
            aus.append('\n')
        }
        return aus.split('\n').map { it.replace('\u0000', ' ').trim() }.filter { it.isNotEmpty() }
    }

    // Seiten in der Reihenfolge des Seitenbaums (/Pages, /Kids). Die Objektnummern folgen ihr nicht immer; teilt
    // ein Seitenumbruch eine Tabellenzeile, muss die erste Haelfte vor der zweiten kommen.
    private fun seiten(o: Map<Int, Obj>): List<Obj> {
        val aus = ArrayList<Obj>()
        val gesehen = HashSet<Int>()
        fun knoten(n: Int, tiefe: Int) {
            if (tiefe > 20 || !gesehen.add(n)) return
            val d = o[n]?.dict ?: return
            if (Regex("/Type\\s*/Pages\\b").containsMatchIn(d)) {
                val kids = Regex("/Kids\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL).find(d)?.groupValues?.get(1).orEmpty()
                Regex("(\\d+)\\s+\\d+\\s+R").findAll(kids).forEach { knoten(it.groupValues[1].toInt(), tiefe + 1) }
            } else if (Regex("/Type\\s*/Page\\b").containsMatchIn(d)) {
                aus += o.getValue(n)
            }
        }
        val katalog = o.values.firstOrNull { Regex("/Type\\s*/Catalog\\b").containsMatchIn(it.dict) }
        Regex("/Pages\\s+(\\d+)\\s+\\d+\\s+R").find(katalog?.dict ?: "")?.groupValues?.get(1)?.toIntOrNull()?.let { knoten(it, 0) }
        if (aus.isNotEmpty()) return aus
        return o.entries.filter { Regex("/Type\\s*/Page\\b").containsMatchIn(it.value.dict) }.sortedBy { it.key }.map { it.value }
    }

    // Objekte

    private fun objekte(pdf: ByteArray, s: String): Map<Int, Obj> {
        val rx = Regex("(\\d+)\\s+(\\d+)\\s+obj\\b")
        val roh = LinkedHashMap<Int, Triple<String, Int, String?>>()   // Nummer -> (Woerterbuch, Datenbeginn, Laenge als Text)
        var pos = 0
        while (true) {
            val m = rx.find(s, pos) ?: break
            val n = m.groupValues[1].toInt()
            val start = m.range.last + 1
            val ende = s.indexOf("endobj", start).takeIf { it >= 0 } ?: s.length
            val stream = Regex("\\bstream(\\r\\n|\\n|\\r)").find(s.substring(start, ende))
            if (stream == null) {
                roh[n] = Triple(s.substring(start, ende), -1, null)
                pos = ende + 6
                continue
            }
            val dict = s.substring(start, start + stream.range.first)
            val daten = start + stream.range.last + 1
            val laenge = Regex("/Length\\s+(\\d+)(\\s+\\d+\\s+R)?").find(dict)
            roh[n] = Triple(dict, daten, laenge?.value)
            // Ende hinter den Daten suchen, damit ein „endobj“ im Strom nicht zaehlt
            val l = laenge?.takeIf { it.groupValues[2].isEmpty() }?.groupValues?.get(1)?.toIntOrNull()
            pos = if (l != null) (s.indexOf("endobj", daten + l).takeIf { it >= 0 } ?: s.length) + 6 else ende + 6
        }
        val aus = HashMap<Int, Obj>()
        for ((n, t) in roh) {
            val (dict, daten, laengeText) = t
            if (daten < 0) { aus[n] = Obj(dict, null); continue }
            val lm = Regex("/Length\\s+(\\d+)(\\s+(\\d+)\\s+\\d+\\s+R)?").find(laengeText ?: "")
            val laenge = when {
                lm == null -> s.indexOf("endstream", daten).let { if (it < 0) 0 else it - daten }
                lm.groupValues[2].isNotEmpty() -> roh[lm.groupValues[1].toInt()]?.first?.trim()?.toIntOrNull()
                    ?: s.indexOf("endstream", daten).let { if (it < 0) 0 else it - daten }
                else -> lm.groupValues[1].toInt()
            }
            aus[n] = Obj(dict, pdf.copyOfRange(daten, minOf(pdf.size, daten + laenge)))
        }
        return aus
    }

    // Verschluesselung (Standard, RC4, Revision 2 und 3)

    private fun hex(h: String): ByteArray = h.chunked(2).filter { it.length == 2 }.map { it.toInt(16).toByte() }.toByteArray()

    // Wert eines Zeichenketten-Eintrags (hex in spitzen oder woertlich in runden Klammern).
    private fun zeichenkette(dict: String, name: String): ByteArray? {
        Regex("/$name\\s*<([0-9A-Fa-f\\s]+)>").find(dict)?.let { return hex(it.groupValues[1].filter { c -> !c.isWhitespace() }) }
        val i = Regex("/$name\\s*\\(").find(dict)?.range?.last ?: return null
        return woertlich(dict, i + 1).first
    }

    private fun schluessel(s: String, o: Map<Int, Obj>): ByteArray? {
        val enc = Regex("/Encrypt\\s+(\\d+)\\s+\\d+\\s+R").find(s)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val d = o[enc]?.dict ?: return null
        if (!d.contains("/Standard")) return null
        val v = Regex("/V\\s+(\\d+)").find(d)?.groupValues?.get(1)?.toInt() ?: 0
        val r = Regex("/R\\s+(\\d+)").find(d)?.groupValues?.get(1)?.toInt() ?: 2
        // AES-256 (V 5, R 5 und 6; seit 5.9 fuer die Formulare der ASX): 32 Byte Schluessel fuer alle Objekte
        if (v == 5) return aes256Schluessel(d, r)
        if (v == 4) return null
        val p = Regex("/P\\s+(-?\\d+)").find(d)?.groupValues?.get(1)?.toLong() ?: return null
        val laenge = if (r >= 3) (Regex("/Length\\s+(\\d+)").find(d)?.groupValues?.get(1)?.toInt() ?: 40) / 8 else 5
        val oWert = zeichenkette(d, "O") ?: return null
        val id = Regex("/ID\\s*\\[\\s*<([0-9A-Fa-f]+)>").find(s)?.groupValues?.get(1)?.let { hex(it) } ?: ByteArray(0)
        val md5 = MessageDigest.getInstance("MD5")
        md5.update(PAD)
        md5.update(oWert.copyOf(32))
        md5.update(byteArrayOf(p.toByte(), (p shr 8).toByte(), (p shr 16).toByte(), (p shr 24).toByte()))
        md5.update(id)
        var h = md5.digest()
        if (r >= 3) repeat(50) { h = MessageDigest.getInstance("MD5").digest(h.copyOf(laenge)) }
        return h.copyOf(laenge)
    }

    private fun objSchluessel(k: ByteArray, n: Int): ByteArray {
        val md5 = MessageDigest.getInstance("MD5")
        md5.update(k)
        md5.update(byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), 0, 0))
        return md5.digest().copyOf(minOf(k.size + 5, 16))
    }

    private fun rc4(key: ByteArray, data: ByteArray): ByteArray {
        val st = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + st[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
            val t = st[i]; st[i] = st[j]; st[j] = t
        }
        var i = 0
        j = 0
        val out = ByteArray(data.size)
        for (x in data.indices) {
            i = (i + 1) and 0xFF
            j = (j + st[i]) and 0xFF
            val t = st[i]; st[i] = st[j]; st[j] = t
            out[x] = (data[x].toInt() xor st[(st[i] + st[j]) and 0xFF]).toByte()
        }
        return out
    }

    // Dateischluessel fuer AES-256 bei leerem Benutzerpasswort: aus dem Schluesselsalz in /U entsteht der
    // Zwischenschluessel, mit dem /UE entschluesselt wird (R 5 ein SHA-256, R 6 das iterierte Verfahren 2.B).
    private fun aes256Schluessel(d: String, r: Int): ByteArray? = runCatching {
        val u = zeichenkette(d, "U") ?: return null
        val ue = zeichenkette(d, "UE") ?: return null
        if (u.size < 48 || ue.size < 32) return null
        val salz = u.copyOfRange(40, 48)
        val zwischen = if (r >= 6) hash2b(salz) else MessageDigest.getInstance("SHA-256").digest(salz)
        val c = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding")
        c.init(javax.crypto.Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(zwischen, "AES"), javax.crypto.spec.IvParameterSpec(ByteArray(16)))
        c.doFinal(ue.copyOf(32))
    }.getOrNull()

    // Verfahren 2.B (ISO 32000-2) fuer ein leeres Passwort ohne Benutzerdaten.
    private fun hash2b(salz: ByteArray): ByteArray {
        var k = MessageDigest.getInstance("SHA-256").digest(salz)
        var i = 0
        while (true) {
            val k1 = ByteArrayOutputStream().apply { repeat(64) { write(k) } }.toByteArray()
            val c = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding")
            c.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(k.copyOf(16), "AES"), javax.crypto.spec.IvParameterSpec(k.copyOfRange(16, 32)))
            val e = c.doFinal(k1)
            val summe = (0 until 16).sumOf { e[it].toInt() and 0xFF } % 3
            k = MessageDigest.getInstance(when (summe) { 0 -> "SHA-256"; 1 -> "SHA-384"; else -> "SHA-512" }).digest(e)
            i++
            if (i >= 64 && (e.last().toInt() and 0xFF) <= i - 32) break
        }
        return k.copyOf(32)
    }

    private fun aes(key: ByteArray, daten: ByteArray): ByteArray? = runCatching {
        if (daten.size < 32) return null
        val c = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(javax.crypto.Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.IvParameterSpec(daten.copyOf(16)))
        c.doFinal(daten, 16, daten.size - 16)
    }.getOrNull()

    private fun strom(o: Map<Int, Obj>, k: ByteArray?, n: Int): ByteArray? {
        val obj = o[n] ?: return null
        var daten = obj.roh ?: return null
        // 32 Byte: AES-256 ohne Schluessel je Objekt; sonst RC4 mit Schluessel je Objekt
        if (k != null) daten = if (k.size == 32) aes(k, daten) ?: return null else rc4(objSchluessel(k, n), daten)
        if (obj.dict.contains("/FlateDecode")) daten = inflate(daten) ?: return null
        return daten
    }

    private fun inflate(d: ByteArray): ByteArray? = runCatching {
        val inf = Inflater()
        inf.setInput(d)
        val out = ByteArrayOutputStream(d.size * 4)
        val buf = ByteArray(8192)
        while (!inf.finished()) {
            val n = inf.inflate(buf)
            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
            out.write(buf, 0, n)
            // Hoechstens 32 MB je Strom, gegen gepackte Bomben in PDFs aus News-Links
            if (out.size() > 32_000_000) { inf.end(); return@runCatching null }
        }
        inf.end()
        out.toByteArray()
    }.getOrNull()

    // Schriften und Ressourcen

    private class Schrift(val zweiByte: Boolean, val tabelle: Map<Int, String>?)

    private class Ressourcen(val schriften: Map<String, Schrift>, val formen: Map<String, Int>)

    // Woerterbuch hinter /Name: direkt in << >> oder als Verweis „n 0 R“.
    private fun unterWoerterbuch(dict: String, name: String, o: Map<Int, Obj>): String? {
        val m = Regex("/$name\\s*(<<|\\d+\\s+\\d+\\s+R)").find(dict) ?: return null
        if (m.groupValues[1] != "<<") {
            val n = m.groupValues[1].substringBefore(' ').trim().toIntOrNull() ?: return null
            return o[n]?.dict
        }
        // Passende schliessende Klammern zaehlen
        var tiefe = 0
        var i = m.range.last - 1
        while (i < dict.length - 1) {
            if (dict.startsWith("<<", i)) { tiefe++; i += 2; continue }
            if (dict.startsWith(">>", i)) { tiefe--; i += 2; if (tiefe == 0) return dict.substring(m.range.last + 1, i - 2); continue }
            i++
        }
        return dict.substring(m.range.last + 1)
    }

    private fun ressourcen(dict: String, o: Map<Int, Obj>, k: ByteArray?): Ressourcen {
        val res = unterWoerterbuch(dict, "Resources", o) ?: ""
        val schriften = HashMap<String, Schrift>()
        unterWoerterbuch(res, "Font", o)?.let { f ->
            for (m in Regex("/([^\\s/<>\\[\\]()]+)\\s+(\\d+)\\s+\\d+\\s+R").findAll(f)) {
                val fd = o[m.groupValues[2].toInt()]?.dict ?: continue
                val tu = Regex("/ToUnicode\\s+(\\d+)\\s+\\d+\\s+R").find(fd)?.groupValues?.get(1)?.toInt()
                schriften[m.groupValues[1]] = Schrift(fd.contains("/Type0"), tu?.let { n -> strom(o, k, n)?.let { cmap(String(it, Charsets.ISO_8859_1)) } })
            }
        }
        val formen = HashMap<String, Int>()
        unterWoerterbuch(res, "XObject", o)?.let { x ->
            for (m in Regex("/([^\\s/<>\\[\\]()]+)\\s+(\\d+)\\s+\\d+\\s+R").findAll(x)) formen[m.groupValues[1]] = m.groupValues[2].toInt()
        }
        return Ressourcen(schriften, formen)
    }

    private fun cmap(t: String): Map<Int, String> {
        val m = HashMap<Int, String>()
        for (blk in Regex("beginbfchar(.*?)endbfchar", RegexOption.DOT_MATCHES_ALL).findAll(t)) {
            for (e in Regex("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>").findAll(blk.groupValues[1])) {
                m[e.groupValues[1].toInt(16)] = utf16(e.groupValues[2])
            }
        }
        for (blk in Regex("beginbfrange(.*?)endbfrange", RegexOption.DOT_MATCHES_ALL).findAll(t)) {
            for (e in Regex("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>").findAll(blk.groupValues[1])) {
                val a = e.groupValues[1].toInt(16)
                val z = e.groupValues[2].toInt(16)
                val u = e.groupValues[3].toInt(16)
                if (z - a in 0..4096) for (c in a..z) m[c] = String(Character.toChars(u + c - a))
            }
        }
        return m
    }

    private fun utf16(h: String): String {
        val b = hex(h)
        return runCatching { String(b, Charsets.UTF_16BE) }.getOrDefault("")
    }

    // Inhaltsstroeme

    private fun lauf(inhalt: ByteArray, res: Ressourcen, o: Map<Int, Obj>, k: ByteArray?, aus: StringBuilder, tiefe: Int) {
        if (tiefe > 4) return
        val t = String(inhalt, Charsets.ISO_8859_1)
        val operanden = ArrayList<Any>()
        var schrift: Schrift? = null
        var i = 0
        while (i < t.length) {
            val c = t[i]
            when {
                c.isWhitespace() -> i++
                c == '%' -> { while (i < t.length && t[i] != '\n' && t[i] != '\r') i++ }
                c == '(' -> { val (b, e) = woertlich(t, i + 1); operanden += b; i = e }
                c == '<' && t.startsWith("<<", i) -> { val e = t.indexOf(">>", i); i = if (e < 0) t.length else e + 2 }
                c == '<' -> { val e = t.indexOf('>', i); operanden += hex(t.substring(i + 1, if (e < 0) t.length else e).filter { !it.isWhitespace() }); i = if (e < 0) t.length else e + 1 }
                c == '[' -> { operanden += Feld(); i++ }
                c == ']' -> {
                    val start = operanden.indexOfLast { it is Feld }
                    if (start >= 0) {
                        val teile = operanden.subList(start + 1, operanden.size).toList()
                        repeat(operanden.size - start) { operanden.removeAt(operanden.size - 1) }
                        operanden += teile
                            .let { l -> Liste(l) }
                    }
                    i++
                }
                c == '/' -> { var e = i + 1; while (e < t.length && !t[e].isWhitespace() && t[e] !in "/[]()<>{}%") e++; operanden += Name(t.substring(i + 1, e)); i = e }
                c.isDigit() || c == '-' || c == '+' || c == '.' -> {
                    var e = i + 1; while (e < t.length && (t[e].isDigit() || t[e] == '.')) e++
                    operanden += t.substring(i, e).toDoubleOrNull() ?: 0.0; i = e
                }
                else -> {
                    var e = i; while (e < t.length && !t[e].isWhitespace() && t[e] !in "/[]()<>{}%") e++
                    if (e == i) e = i + 1
                    val op = t.substring(i, e)
                    i = e
                    when (op) {
                        "Tf" -> (operanden.getOrNull(operanden.size - 2) as? Name)?.let { schrift = res.schriften[it.n] }
                        "Tj", "'", "\"" -> (operanden.lastOrNull() as? ByteArray)?.let { aus.append(text(it, schrift)) }
                        "TJ" -> (operanden.lastOrNull() as? Liste)?.let { l ->
                            for (x in l.teile) when (x) {
                                is ByteArray -> aus.append(text(x, schrift))
                                is Double -> if (x < -250) aus.append(' ')
                            }
                        }
                        "Td", "TD", "Tm", "T*", "ET", "BT" -> aus.append('\n')
                        "Do" -> (operanden.lastOrNull() as? Name)?.let { nm ->
                            val n = res.formen[nm.n] ?: return@let
                            val d = o[n]?.dict ?: return@let
                            if (!d.contains("/Form")) return@let
                            val inner = strom(o, k, n) ?: return@let
                            lauf(inner, ressourcen(d, o, k).let { r -> if (r.schriften.isEmpty()) Ressourcen(res.schriften, r.formen) else r }, o, k, aus, tiefe + 1)
                            aus.append('\n')
                        }
                        "BI" -> { val e2 = t.indexOf("EI", i); i = if (e2 < 0) t.length else e2 + 2 }
                    }
                    operanden.clear()
                }
            }
        }
    }

    private class Feld
    private class Liste(val teile: List<Any>)
    private class Name(val n: String)

    // Woertliche Zeichenkette ab start (hinter der oeffnenden Klammer): Bytes und Position hinter dem Ende.
    private fun woertlich(t: String, start: Int): Pair<ByteArray, Int> {
        val out = ByteArrayOutputStream()
        var tiefe = 1
        var i = start
        while (i < t.length) {
            val c = t[i]
            when {
                c == '\\' && i + 1 < t.length -> {
                    val n = t[i + 1]
                    when (n) {
                        'n' -> out.write(10); 'r' -> out.write(13); 't' -> out.write(9); 'b' -> out.write(8); 'f' -> out.write(12)
                        '(', ')', '\\' -> out.write(n.code)
                        '\r', '\n' -> {}
                        in '0'..'7' -> {
                            var e = i + 1; var v = 0
                            while (e < t.length && e < i + 4 && t[e] in '0'..'7') { v = v * 8 + (t[e] - '0'); e++ }
                            out.write(v and 0xFF); i = e - 2
                        }
                        else -> out.write(n.code)
                    }
                    i += 2
                    continue
                }
                c == '(' -> { tiefe++; out.write(c.code) }
                c == ')' -> { tiefe--; if (tiefe == 0) return out.toByteArray() to i + 1; out.write(c.code) }
                else -> out.write(c.code and 0xFF)
            }
            i++
        }
        return out.toByteArray() to t.length
    }

    private fun text(b: ByteArray, f: Schrift?): String {
        val tab = f?.tabelle
        if (f?.zweiByte == true) {
            val sb = StringBuilder()
            var i = 0
            while (i + 1 < b.size) {
                val c = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
                sb.append(tab?.get(c) ?: "")
                i += 2
            }
            return sb.toString()
        }
        return buildString { for (x in b) { val c = x.toInt() and 0xFF; append(tab?.get(c) ?: c.toChar().toString()) } }
    }
}
