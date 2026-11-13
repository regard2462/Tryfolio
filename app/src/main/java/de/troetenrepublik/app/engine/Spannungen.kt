package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.time.LocalDate

// Spannungsgebiete: wie stark englischsprachige Medien in den letzten sieben Tagen ueber Truppen, Manoever, Mobilmachung
// und Angriffe in einer Region berichten, gegen den Schnitt der Wochen davor (GDELT 2.0 DOC API, Modus timelinevolraw,
// 30 Tage, Tageswerte). Verglichen wird der Anteil der Treffer an allen erfassten Artikeln, damit Luecken im Index die
// Zahl nicht verzerren. Ein Anstieg zeigt, dass die Lage mehr Aufmerksamkeit bekommt; er sagt nichts ueber den Ausgang.
// GDELT erlaubt eine Anfrage je fuenf Sekunden.
object Spannungen {
    const val ADRESSE = "https://api.gdeltproject.org/api/v2/doc/doc"
    const val SEITE = "https://www.gdeltproject.org"
    const val GUELTIG_MS = 6 * 3_600_000L
    const val ABSTAND_MS = 5_500L
    const val TAGE = 7
    // Mindestzahl Tageswerte in der letzten Woche und davor, damit ein Vergleich gilt
    const val MIND_NEU = 3
    const val MIND_DAVOR = 8
    // Mindestzahl Treffer in der letzten Woche: bei weniger schwankt der Anteil zu stark fuer einen Vergleich
    const val MIND_ARTIKEL = 30

    // suche: Abfrage in GDELT-Schreibweise (OR nur in Klammern, Woerter ab drei Buchstaben); laender: ISO alpha-3 der
    // beteiligten Staaten (fuer Konflikt- und Sanktionsdaten); werte: Kuerzel im Katalog der App
    class Gebiet(val id: String, val suche: String, val laender: List<String>, val werte: List<String>)

    val GEBIETE: List<Gebiet> = listOf(
        Gebiet("UKR", "(Ukraine OR Russia) (troops OR military OR mobilization OR offensive)", listOf("UKR", "RUS", "BLR"), listOf("BZ=F", "NG=F", "ZW=F", "ZC=F", "DFEN.DE", "EUDF.DE", "GC=F")),
        Gebiet("TWN", "Taiwan (military OR drills OR blockade OR warships OR invasion)", listOf("TWN", "CHN"), listOf("TSM", "VVSM.DE", "SEC0.DE", "DFEN.DE", "GC=F")),
        Gebiet("NAO", "(Iran OR Israel OR Lebanon) (troops OR military OR airstrike OR missile)", listOf("IRN", "ISR", "LBN", "SYR"), listOf("BZ=F", "CL=F", "GC=F", "DFEN.DE", "EUDF.DE")),
        Gebiet("GOL", "(Hormuz OR \"Persian Gulf\") (tanker OR navy OR blockade OR attack)", listOf("IRN", "ARE", "OMN", "SAU"), listOf("BZ=F", "NG=F", "FRO.OL", "HAFNI.OL", "XOM")),
        Gebiet("ROT", "(Houthi OR \"Red Sea\" OR Yemen) (attack OR missile OR ship OR drone)", listOf("YEM", "ERI", "DJI"), listOf("MAERSK-B.CO", "HLAG.DE", "BZ=F")),
        Gebiet("KOR", "(\"North Korea\" OR Pyongyang) (missile OR troops OR military OR launch)", listOf("PRK", "KOR"), listOf("005930.KS", "DFEN.DE")),
        Gebiet("SCS", "\"South China Sea\" (Philippines OR \"coast guard\" OR warships OR navy)", listOf("PHL", "CHN", "VNM"), listOf("TSM", "MAERSK-B.CO", "HLAG.DE")),
        Gebiet("KAS", "(India OR Pakistan) (Kashmir OR border OR troops OR military)", listOf("IND", "PAK"), listOf("DFEN.DE", "GC=F")),
        Gebiet("BAL", "(Belarus OR Poland OR Lithuania OR Estonia OR Latvia) (troops OR military OR border OR drones)", listOf("BLR", "POL", "LTU", "EST", "LVA"), listOf("EUDF.DE", "DFEN.DE", "RHM.DE")),
        Gebiet("VEN", "Venezuela (military OR troops OR navy OR warships)", listOf("VEN", "GUY"), listOf("BZ=F", "CL=F", "XOM")),
        Gebiet("SDN", "Sudan (fighting OR offensive OR \"Rapid Support Forces\" OR troops)", listOf("SDN", "SSD"), listOf("GC=F")),
        Gebiet("SAH", "(Mali OR \"Burkina Faso\" OR Niger) (attack OR troops OR jihadists OR military)", listOf("MLI", "BFA", "NER"), listOf("GC=F")),
    )

    private val NACH_ID = GEBIETE.associateBy { it.id }
    fun gebiet(id: String): Gebiet? = NACH_ID[id]

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun adresse(g: Gebiet): String = ADRESSE + "?query=" + enc(g.suche + " sourcelang:eng") + "&mode=timelinevolraw&format=json&timespan=30d"

    // Treffer und alle erfassten Artikel eines Tages
    class Tagwert(val t: Int, val treffer: Int, val gesamt: Int)

    // Antwort: {"timeline":[{"series":"Article Count","data":[{"date":"20261010T000000Z","value":66,"norm":1836}, ...]}]}.
    // Leer ({}) oder die Bitte um Abstand (Text) ergeben null.
    fun lesen(text: String): List<Tagwert>? = runCatching {
        val w = Laenderrisiken.json.parseToJsonElement(text).jsonObject
        val daten = ((w["timeline"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("data") as? JsonArray ?: return null
        val aus = ArrayList<Tagwert>(daten.size)
        for (e in daten) {
            val o = e as? JsonObject ?: continue
            val d = o["date"]?.jsonPrimitive?.contentOrNull ?: continue
            val v = o["value"]?.jsonPrimitive?.doubleOrNull ?: continue
            val n = o["norm"]?.jsonPrimitive?.doubleOrNull ?: continue
            val tag = LocalDate.of(d.substring(0, 4).toInt(), d.substring(4, 6).toInt(), d.substring(6, 8).toInt()).toEpochDay().toInt()
            if (n > 0.0) aus += Tagwert(tag, v.toInt(), n.toInt())
        }
        if (aus.isEmpty()) null else aus
    }.getOrNull()

    // artikel: Treffer der letzten sieben Tage (bei Luecken im Index entsprechend weniger); faktor: Anteil an allen
    // Artikeln in dieser Woche geteilt durch den Anteil in den Tagen davor (1 = unveraendert); bis: letzter Tag
    @Serializable
    class Wert(val abgerufen: Long, val id: String, val artikel: Int, val faktor: Double, val bis: Int)

    fun bewerten(id: String, tage: List<Tagwert>, jetzt: Long): Wert? {
        val bis = tage.maxOf { it.t }
        val neu = tage.filter { it.t > bis - TAGE }
        val davor = tage.filter { it.t <= bis - TAGE }
        if (neu.size < MIND_NEU || davor.size < MIND_DAVOR) return null
        val rn = neu.sumOf { it.treffer }.toDouble() / neu.sumOf { it.gesamt }
        val rd = davor.sumOf { it.treffer }.toDouble() / davor.sumOf { it.gesamt }
        if (rd <= 0.0 || neu.sumOf { it.treffer } < MIND_ARTIKEL) return null
        return Wert(jetzt, id, neu.sumOf { it.treffer }, rn / rd, bis)
    }

    // Groesster Anstieg zuerst
    fun sortiert(werte: List<Wert>): List<Wert> = werte.sortedByDescending { it.faktor }
}
