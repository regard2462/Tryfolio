package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

// Verdichtete Kaufhistorie je Firma (0.77, assets/copytrading/sec_historie.json, gebaut von tools/sec/historie.py aus den
// aelteren Form-345-Datensaetzen der SEC): davor lag der mitgelieferte Stand von 18 Monaten. Je Ticker nur, was
// InsiderSeltenheit braucht: Zahl der Kaeufe, die Kette der Rekordkaeufe und die Kette der Wochen mit mindestens drei
// Kaeufern, jeweils neueste zuerst mit steigendem Mass. Ohne Eintrag eines Tickers gilt das Verhalten ohne Historie.
class InsiderHistorie(val von: String, val bis: String, val firmen: Map<String, Firma>) {

    // tag: Tage seit 1970; wert: Dollar (Kaeufe) oder Zahl der Kaeufer (Wochen)
    class Eintrag(val tag: Long, val wert: Double)

    class Firma(val n: Int, val kaeufe: List<Eintrag>, val wochen: List<Eintrag>) {
        // Letzter Kauf der Historie, null ohne Kauf
        val letzter: Eintrag? get() = kaeufe.firstOrNull()

        // Groesster Kauf der Historie, 0 ohne Kauf
        val groesster: Double get() = kaeufe.lastOrNull()?.wert ?: 0.0

        // Letzter Kauf mit mindestens dem Betrag
        fun letzterMit(betrag: Double): Eintrag? = kaeufe.firstOrNull { it.wert >= betrag }

        // Letzte Woche mit mindestens so vielen Kaeufern
        fun letzteWoche(kaeufer: Int): Eintrag? = wochen.firstOrNull { it.wert >= kaeufer }
    }

    val jahr: Int get() = von.take(4).toIntOrNull() ?: 0

    companion object {
        const val DATEI = "copytrading/sec_historie.json"

        private fun tag(nr: Int): Long = LocalDate.of(nr / 10000, nr / 100 % 100, nr % 100).toEpochDay()

        private fun eintraege(a: JsonArray): List<Eintrag> = a.map { e ->
            val p = e.jsonArray
            Eintrag(tag(p[0].jsonPrimitive.intOrNull ?: error("Tag")), p[1].jsonPrimitive.doubleOrNull ?: error("Wert"))
        }

        // null bei unlesbarem Text oder ohne Zeitraum
        fun lesen(text: String): InsiderHistorie? = runCatching {
            val o = Json.parseToJsonElement(text).jsonObject
            val von = o["von"]?.jsonPrimitive?.content.orEmpty()
            val bis = o["bis"]?.jsonPrimitive?.content.orEmpty()
            if (von.isBlank() || bis.isBlank()) return@runCatching null
            val firmen = (o["firmen"] as? JsonObject).orEmpty().mapValues { (_, v) ->
                val a = v.jsonArray
                Firma(a[0].jsonPrimitive.intOrNull ?: 0, eintraege(a[1].jsonArray), eintraege(a[2].jsonArray))
            }
            InsiderHistorie(von, bis, firmen)
        }.getOrNull()

        @Volatile private var geladen: Pair<String, InsiderHistorie?>? = null

        // Einmal gelesen und gemerkt, solange der Text derselbe bleibt (neuere Fassung aus dem Datenbuendel).
        fun gemerkt(text: () -> String): InsiderHistorie? {
            val t = runCatching(text).getOrNull() ?: return null
            geladen?.let { if (it.first.length == t.length && it.first == t) return it.second }
            val h = lesen(t)
            geladen = t to h
            return h
        }

        // Die Historie der App (eingebaut oder neuere Fassung aus dem Datenbuendel); null, wenn sie fehlt.
        fun app(): InsiderHistorie? = gemerkt { de.troetenrepublik.app.market.Daten.text(DATEI) }
    }
}
