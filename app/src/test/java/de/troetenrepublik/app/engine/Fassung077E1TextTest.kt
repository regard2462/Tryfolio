package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

// Textdurchgang 0.77: In den deutschen Texten stehen keine Gedankenstriche und nicht die gesperrten Woerter.
// Geprueft werden alle Texte von De: Eigenschaften direkt, Funktionen mit Beispielwerten.
class Fassung077E1TextTest {
    private val strich = listOf(0x2013.toChar(), 0x2014.toChar())
    private val gesperrt = listOf("Burggraben", "Griechen")
    private val gesperrtDe = Regex("Burggr(a|ä)b|(?<![A-Za-z])Grieche(n)?(?![a-zA-Z])")
    private val gesperrtEn = Regex("(?i)greeks")

    // Texte aus der Zeit vor 0.77 mit Strich als Bereichsangabe (1 bis 3 Tage, Uhrzeiten).
    private val aelter = setOf("getBankDays", "withdrawalArrives", "getWithdrawalOptionSub", "regVonBis", "sectorName", "marketHours", "getZgPolyUngueltig", "regZeitenLandHint")

    private fun texte(obj: Any = De): List<Pair<String, String>> {
        val aus = ArrayList<Pair<String, String>>()
        fun sammeln(name: String, wert: Any?) {
            when (wert) {
                is String -> aus.add(name to wert)
                is Iterable<*> -> wert.forEach { sammeln(name, it) }
                is Pair<*, *> -> { sammeln(name, wert.first); sammeln(name, wert.second) }
            }
        }
        for (m in obj.javaClass.methods) {
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
            try { sammeln(m.name, m.invoke(obj, *args.toTypedArray())) } catch (_: Throwable) { }
        }
        return aus
    }

    @Test fun keineGedankenstricheUndKeineGesperrtenWoerter() {
        val alle = texte()
        assertTrue("zu wenige Texte gelesen: ${alle.size}", alle.size > 2000)
        val fehler = alle.filter { (name, t) ->
            gesperrt.any { t.contains(it) } || (name !in aelter && strich.any { t.contains(it) })
        }
        assertTrue(fehler.joinToString("\n") { (n, t) -> n + ": " + t.take(80) }, fehler.isEmpty())
    }

    @Test fun keineGesperrtenWoerterInEnglisch() {
        val fehler = texte(En).filter { (_, t) -> gesperrtEn.containsMatchIn(t) }
        assertTrue(fehler.joinToString("\n") { (n, t) -> n + ": " + t.take(80) }, fehler.isEmpty())
    }

    // Finanzwissen-Daten (Deutsch und Englisch, Lektionen, Glossar, Suchindex).
    @Test fun finanzwissenOhneGesperrteWoerter() {
        val wurzel = listOf(java.io.File("src/main/assets/finanzwissen/data"), java.io.File("../app/src/main/assets/finanzwissen/data")).first { it.exists() }
        assertTrue("Ordner fehlt: ${wurzel.absolutePath}", wurzel.isDirectory)
        val dateien = wurzel.walkTopDown().filter { it.isFile && it.extension == "json" }.toList()
        assertTrue(dateien.size > 100)
        val fehler = ArrayList<String>()
        for (f in dateien) {
            val en = f.path.replace('\\', '/').contains("/data/en/")
            val muster = if (en) gesperrtEn else gesperrtDe
            val text = f.readText()
            if (muster.containsMatchIn(text)) fehler.add(f.name)
        }
        assertTrue(fehler.joinToString("\n"), fehler.isEmpty())
    }
}
