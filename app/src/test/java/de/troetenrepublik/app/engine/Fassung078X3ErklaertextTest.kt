package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

// Fassung 0.78: Auszeichnung der Erklaertexte. Die Zerlegung stimmt, jede deutsche Auszeichnung hat ihr englisches
// Gegenstueck und kein Text traegt Gedankenstriche.
class Fassung078X3ErklaertextTest {

    @Test fun absaetzeStichpunkteUndKoepfe() {
        val b = Erklaertext.zerlegen("Was es ist.\n\nSo liest man es:\n• Rot: hoch\n• ohne Begriff\n\n\nGrenzen stehen hier.\n")
        assertEquals(
            listOf(
                Erklaertext.Absatz("Was es ist."), Erklaertext.Luecke, Erklaertext.Kopf("So liest man es:"),
                Erklaertext.Punkt("Rot", "hoch"), Erklaertext.Punkt(null, "ohne Begriff"), Erklaertext.Luecke,
                Erklaertext.Absatz("Grenzen stehen hier."),
            ),
            b,
        )
        assertEquals(Erklaertext.Zahl(absaetze = 2, punkte = 2, koepfe = 1, skalen = 0, luecken = 2), Erklaertext.zaehlen("Was es ist.\n\nSo liest man es:\n• Rot: hoch\n• ohne Begriff\n\nGrenzen stehen hier."))
    }

    @Test fun kopfNurKurzUndMitFolgetext() {
        // Am Textende ist eine Zeile mit Doppelpunkt ein gewoehnlicher Absatz
        assertEquals(listOf(Erklaertext.Absatz("Die Phasen:")), Erklaertext.zerlegen("Die Phasen:"))
        val lang = "Diese Zeile ist deutlich zu lang für eine Zwischenüberschrift:"
        assertTrue(lang.length > Erklaertext.KOPF_MAX)
        assertEquals(Erklaertext.Absatz(lang), Erklaertext.zerlegen("$lang\n• a").first())
        // Zwei Saetze in einer Zeile sind keine Ueberschrift
        assertEquals(Erklaertext.Absatz("Erst das. Dann das:"), Erklaertext.zerlegen("Erst das. Dann das:\n• a").first())
        assertEquals(Erklaertext.Kopf("Ablauf:"), Erklaertext.zerlegen("Ablauf:\n1. eins").first())
    }

    @Test fun begriffNurKurzUndOhneSatzzeichen() {
        fun punkt(t: String) = Erklaertext.zerlegen("• $t").single() as Erklaertext.Punkt
        assertEquals("VaR 95 %", punkt("VaR 95 %: An 95 von 100 Tagen war der Verlust kleiner.").begriff)
        assertEquals("Positiv heißt", punkt("Positiv heißt: mehr Arbeitskräfte.").begriff)
        // Uhrzeit, Satz vor dem Doppelpunkt, zu viele Woerter, nichts dahinter
        assertNull(punkt("Handel ab 9:30 Uhr").begriff)
        assertNull(punkt("Erst lesen. Dann: handeln").begriff)
        assertNull(punkt("Arbeitsmarktdaten und Aktien je nach Lage: Boyd").begriff)
        assertNull(punkt("Folgendes gilt:").begriff)
        assertEquals("Folgendes gilt:", punkt("Folgendes gilt:").text)
    }

    @Test fun skalaMitGrenzeUndEnden() {
        val s = Erklaertext.zerlegen("[skala] AAA | AA | A | BBB | BB | B | CCC | D ; grenze=BBB ; links=beste Note ; rechts=Zahlungsausfall").single() as Erklaertext.Skala
        assertEquals(listOf("AAA", "AA", "A", "BBB", "BB", "B", "CCC", "D"), s.stufen)
        assertEquals(3, s.grenze)
        assertEquals("beste Note", s.links)
        assertEquals("Zahlungsausfall", s.rechts)
        val ohne = Erklaertext.zerlegen("[skala] schwach | mäßig | stark").single() as Erklaertext.Skala
        assertEquals(-1, ohne.grenze)
        assertNull(ohne.links)
        // Unbekannte oder letzte Stufe als Grenze trennt nichts; eine einzelne Stufe ist keine Skala
        assertEquals(-1, (Erklaertext.zerlegen("[skala] a | b ; grenze=c").single() as Erklaertext.Skala).grenze)
        assertEquals(-1, (Erklaertext.zerlegen("[skala] a | b ; grenze=b").single() as Erklaertext.Skala).grenze)
        assertTrue(Erklaertext.zerlegen("[skala] allein").single() is Erklaertext.Absatz)
    }

    @Test fun ohneAuszeichnungBleibtEinText() {
        assertFalse(Erklaertext.ausgezeichnet("Ein Satz. Noch einer."))
        assertTrue(Erklaertext.ausgezeichnet("Ein Satz.\n\nNoch einer."))
        assertTrue(Erklaertext.ausgezeichnet("• Punkt"))
        assertEquals(listOf(Erklaertext.Absatz("Ein Satz. Noch einer.")), Erklaertext.zerlegen("Ein Satz. Noch einer."))
    }

    // Alle Texte einer Sprache: Eigenschaften direkt, Funktionen mit Beispielwerten (dieselben fuer beide Sprachen).
    private fun texte(obj: Any): Map<String, List<String>> {
        val aus = LinkedHashMap<String, MutableList<String>>()
        fun sammeln(name: String, wert: Any?) {
            when (wert) {
                is String -> aus.getOrPut(name) { ArrayList() }.add(wert)
                is Iterable<*> -> wert.forEach { sammeln(name, it) }
                is Pair<*, *> -> { sammeln(name, wert.first); sammeln(name, wert.second) }
            }
        }
        for (m in obj.javaClass.methods.sortedBy { it.name + it.parameterTypes.joinToString { p -> p.name } }) {
            if (Modifier.isStatic(m.modifiers) || m.declaringClass == Any::class.java) continue
            val args: List<Any?> = m.parameterTypes.map<Class<*>, Any?> {
                when (it) {
                    Int::class.javaPrimitiveType -> 1
                    Long::class.javaPrimitiveType -> 1L
                    Double::class.javaPrimitiveType -> 1.5
                    Boolean::class.javaPrimitiveType -> true
                    String::class.java -> "x"
                    else -> null
                }
            }
            if (m.parameterTypes.indices.any { args[it] == null }) continue
            val schluessel = m.name + "/" + m.parameterTypes.size
            try { sammeln(schluessel, m.invoke(obj, *args.toTypedArray())) } catch (_: Throwable) { }
        }
        return aus
    }

    @Test fun deutschUndEnglischGleichGegliedert() {
        val de = texte(De)
        val en = texte(En)
        assertTrue("zu wenige Texte gelesen: ${de.size}", de.size > 2000)
        var gegliedert = 0
        val fehler = ArrayList<String>()
        for ((name, d) in de) {
            val e = en[name] ?: continue
            if (d.size != e.size) continue
            for (i in d.indices) {
                if (!Erklaertext.ausgezeichnet(d[i]) && !Erklaertext.ausgezeichnet(e[i])) continue
                gegliedert++
                val zd = Erklaertext.zaehlen(d[i])
                val ze = Erklaertext.zaehlen(e[i])
                // Ob eine Zeile Zwischenueberschrift oder Absatz ist, haengt an ihrer Laenge; verglichen wird die Summe.
                val gleich = zd.absaetze + zd.koepfe == ze.absaetze + ze.koepfe && zd.punkte == ze.punkte && zd.skalen == ze.skalen && zd.luecken == ze.luecken
                if (!gleich) fehler.add("$name[$i]: de $zd, en $ze")
            }
        }
        assertTrue("zu wenige gegliederte Texte: $gegliedert", gegliedert > 100)
        assertTrue(fehler.joinToString("\n"), fehler.isEmpty())
    }

    // Dasselbe am Quelltext, damit auch jeder Zweig einer Auswahl zaehlt: je Eintrag gleich viele Leerzeilen,
    // Stichpunkte und Skalen in beiden Sprachen.
    private fun quelle(datei: String): Map<String, List<Int>> {
        val pfad = "src/main/java/de/troetenrepublik/app/i18n/$datei"
        val f = listOf(java.io.File(pfad), java.io.File("app/$pfad"), java.io.File("../app/$pfad")).first { it.exists() }
        val kopf = Regex("^\\s*override (?:val|fun) (\\w+)")
        val aus = LinkedHashMap<String, IntArray>()
        var name: String? = null
        fun zahl(zeile: String, teil: String) = zeile.windowed(teil.length) { if (it == teil) 1 else 0 }.sum()
        for (zeile in f.readLines()) {
            kopf.find(zeile)?.let { name = it.groupValues[1] }
            val n = name ?: continue
            val z = aus.getOrPut(n) { IntArray(3) }
            z[0] += zahl(zeile, "\\n\\n"); z[1] += zahl(zeile, Erklaertext.PUNKT); z[2] += zahl(zeile, Erklaertext.SKALA)
        }
        return aus.mapValues { it.value.toList() }
    }

    @Test fun quelltextInBeidenSprachenGleichGegliedert() {
        val de = quelle("De.kt")
        val en = quelle("En.kt")
        assertTrue("zu wenige Eintraege: ${de.size}", de.size > 4000)
        assertTrue("zu wenige gegliederte Eintraege", de.count { it.value[0] + it.value[1] > 0 } > 100)
        val fehler = de.filter { (name, z) -> en[name] != null && en[name] != z }.map { (name, z) -> "$name: de $z, en ${en[name]}" }
        assertTrue(fehler.joinToString("; "), fehler.isEmpty())
    }

    @Test fun skalenInDenTextenSindLesbar() {
        var skalen = 0
        for (obj in listOf<Any>(De, En)) for ((name, liste) in texte(obj)) for (t in liste) {
            for (zeile in t.split('\n')) {
                if (!zeile.contains(Erklaertext.SKALA)) continue
                assertTrue("$name: Skala nicht am Zeilenanfang", zeile.startsWith(Erklaertext.SKALA))
                val s = Erklaertext.zerlegen(zeile).single()
                assertTrue("$name: $zeile", s is Erklaertext.Skala && s.stufen.size >= 2)
                if (zeile.contains("grenze=")) assertTrue("$name: Grenze unbekannt", (s as Erklaertext.Skala).grenze >= 0)
                skalen++
            }
        }
        assertTrue("Skalen: $skalen", skalen >= 4)
    }

    // Texte aus der Zeit vor 0.77 mit Strich als Bereichsangabe (wie in Fassung077E1TextTest).
    private val aelter = setOf("getBankDays", "withdrawalArrives", "getWithdrawalOptionSub", "regVonBis", "sectorName", "marketHours", "getZgPolyUngueltig", "regZeitenLandHint")

    @Test fun gegliederteTexteOhneGedankenstriche() {
        val strich = listOf(0x2013.toChar(), 0x2014.toChar())
        val fehler = ArrayList<String>()
        for (obj in listOf<Any>(De, En)) for ((name, liste) in texte(obj)) {
            if (name.substringBefore('/') in aelter) continue
            for (t in liste) {
                if (obj === En && !Erklaertext.ausgezeichnet(t)) continue
                if (strich.any { t.contains(it) }) fehler.add((if (obj === De) "de " else "en ") + name + ": " + t.take(80))
            }
        }
        assertTrue(fehler.joinToString("\n"), fehler.isEmpty())
    }
}
