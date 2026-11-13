package de.troetenrepublik.app.engine

import java.time.LocalDate

// Lagerbestaende der COMEX (CME Group). Die Boerse stellt je Metall eine Excel-Datei im alten Format (.xls) bereit, die die
// Person selbst im Browser laedt und mit der App oeffnet. Hier wird nur gelesen und gerechnet (Tabelle kommt aus
// XlsTabelle), Speicherung steht in market/ComexLagerAbruf.kt.
object ComexLager {

    // Seite der Berichte; Dateiadressen stehen nirgends im Code
    const val BERICHT = "https://www.cmegroup.com/clearing/operations-and-deliveries/registrar-reports.html"

    // So viele Berichtstage je Metall bleiben gespeichert
    const val TAGE_MAX = 400

    // Feste Schluessel und Reihenfolge der Anzeige
    val REIHENFOLGE = listOf("GOLD", "SILBER", "KUPFER", "PLATIN", "PALLADIUM", "ALUMINIUM", "ZINK", "BLEI")

    // Einheiten als feste Schluessel: OZ (Feinunzen), SHT (Short Tons), MT (Tonnen)
    const val OZ = "OZ"
    const val SHT = "SHT"
    const val MT = "MT"

    // Ein Berichtstag eines Metalls: Bestand lieferbar gemeldet (Registered), eingelagert (Eligible), verpfaendet (Pledged,
    // nur Gold), Gesamt (TOTAL TODAY) und Veraenderung der Gesamtsumme am Tag (NET CHANGE).
    data class Eintrag(
        val metall: String, val einheit: String, val tag: Long,
        val registered: Double?, val eligible: Double?, val pledged: Double?, val gesamt: Double, val aenderung: Double?,
    )

    // ------------------------------------------------------------ Lesen der Tabelle

    private fun metallSchluessel(name: String): String? {
        val n = name.trim().uppercase()
        return when {
            n.startsWith("GOLD") -> "GOLD"
            n.startsWith("SILVER") -> "SILBER"
            n.startsWith("COPPER") -> "KUPFER"
            n.startsWith("ZINC") -> "ZINK"
            n.startsWith("LEAD") -> "BLEI"
            n.startsWith("ALUMINUM") || n.startsWith("ALUMINIUM") -> "ALUMINIUM"
            n.startsWith("PLATINUM") -> "PLATIN"
            n.startsWith("PALLADIUM") -> "PALLADIUM"
            else -> null
        }
    }

    private fun einheitSchluessel(text: String): String? {
        val t = text.trim().lowercase()
        return when {
            t.startsWith("troy") -> OZ
            t.startsWith("short ton") -> SHT
            t.startsWith("metric ton") -> MT
            else -> null
        }
    }

    private fun standardEinheit(metall: String) = when (metall) {
        "GOLD", "SILBER", "PLATIN", "PALLADIUM" -> OZ
        "KUPFER" -> SHT
        else -> MT
    }

    private const val BERICHTSTAG = "report date:"

    // Datum nach "Report Date:" im Format MM/DD/YYYY als Tag seit 1970-01-01
    private fun berichtstag(zelle: String): Long? {
        val i = zelle.lowercase().indexOf(BERICHTSTAG)
        if (i < 0) return null
        val f = zelle.substring(i + BERICHTSTAG.length).trim().split("/")
        if (f.size != 3) return null
        val m = f[0].trim().toIntOrNull() ?: return null
        val t = f[1].trim().toIntOrNull() ?: return null
        val j = f[2].trim().take(4).toIntOrNull() ?: return null
        return runCatching { LocalDate.of(j, m, t).toEpochDay() }.getOrNull()
    }

    private fun text(z: List<List<Any?>>, r: Int, c: Int): String? = (z.getOrNull(r)?.getOrNull(c) as? String)?.trim()?.takeIf { it.isNotEmpty() }
    private fun zahl(z: List<List<Any?>>, r: Int, c: Int): Double? = (z.getOrNull(r)?.getOrNull(c) as? Double)?.takeIf { it.isFinite() }

    private fun klein(s: String): String = s.trim().lowercase().split(" ").filter { it.isNotEmpty() }.joinToString(" ")

    // Metallabschnitte der Tabelle. Ein Abschnitt beginnt in der Zeile mit "Report Date:" (links der Metallname), danach
    // Einheit, Kopfzeile (DEPOSITORY oder DELIVERY POINT) und Summenzeilen. Die Spalten werden ueber die Kopfzeile
    // gefunden. Unbekannte Metalle und unvollstaendige Abschnitte werden uebersprungen.
    fun abschnitte(z: List<List<Any?>>): List<Eintrag> {
        val starts = ArrayList<Pair<Int, Long>>()
        for (r in z.indices) {
            val tag = z[r].firstNotNullOfOrNull { (it as? String)?.let { s -> berichtstag(s) } } ?: continue
            starts += r to tag
        }
        val liste = ArrayList<Eintrag>()
        for ((k, st) in starts.withIndex()) {
            val von = st.first
            val bis = if (k + 1 < starts.size) starts[k + 1].first else z.size
            val metall = z[von].indices.firstNotNullOfOrNull { c -> text(z, von, c)?.takeIf { berichtstag(it) == null } }?.let { metallSchluessel(it) } ?: continue
            val einheit = (von + 1..minOf(von + 4, bis - 1)).firstNotNullOfOrNull { r -> text(z, r, 0)?.let { einheitSchluessel(it) } } ?: standardEinheit(metall)
            val kopf = (von + 1 until bis).firstOrNull { r -> text(z, r, 0)?.uppercase().let { it == "DEPOSITORY" || it == "DELIVERY POINT" } } ?: continue
            val heute = z[kopf].indices.firstOrNull { c -> text(z, kopf, c)?.uppercase() == "TOTAL TODAY" } ?: continue
            val netto = z[kopf].indices.firstOrNull { c -> text(z, kopf, c)?.uppercase() == "NET CHANGE" }
            var registered: Double? = null
            var eligible: Double? = null
            var pledged: Double? = null
            var gesamt: Double? = null
            var gesamtZeile = -1
            for (r in kopf + 1 until bis) {
                val name = text(z, r, 0)?.let { klein(it) } ?: continue
                val wert = zahl(z, r, heute) ?: continue
                when {
                    name.startsWith("total registered") -> if (registered == null) registered = wert
                    name.startsWith("total pledged") -> if (pledged == null) pledged = wert
                    name.startsWith("total eligible") -> if (eligible == null) eligible = wert
                    name.startsWith("combined total") || name.startsWith("total ") -> if (gesamt == null) { gesamt = wert; gesamtZeile = r }
                }
            }
            val reg = registered
            val eli = eligible
            val summe = gesamt ?: if (reg != null && eli != null) reg + eli else null
            if (summe == null || summe < 0) continue
            val aenderung = if (gesamtZeile >= 0 && netto != null) zahl(z, gesamtZeile, netto) else null
            liste += Eintrag(metall, einheit, st.second, registered, eligible, pledged, summe, aenderung)
        }
        return liste.distinctBy { it.metall }
    }

    // Eintraege einer .xls-Datei; leer, wenn sie keine COMEX-Datei ist.
    fun lesen(xls: ByteArray): List<Eintrag> = abschnitte(XlsTabelle.lesen(xls))

    // ------------------------------------------------------------ Gespeicherte Form

    // Eine Zeile je Berichtstag: Tag;Registered;Eligible;Pledged;Gesamt;Aenderung;Einheit, fehlende Werte leer.
    private fun feld(v: Double?) = v?.toString().orEmpty()

    fun zeile(e: Eintrag): String = listOf(e.tag.toString(), feld(e.registered), feld(e.eligible), feld(e.pledged), e.gesamt.toString(), feld(e.aenderung), e.einheit).joinToString(";")

    fun speichern(eintraege: List<Eintrag>): String = eintraege.joinToString(10.toChar().toString()) { zeile(it) }

    fun laden(metall: String, text: String): List<Eintrag> = text.lineSequence().mapNotNull { l ->
        val f = l.split(";")
        if (f.size < 7) return@mapNotNull null
        val tag = f[0].trim().toLongOrNull() ?: return@mapNotNull null
        val gesamt = f[4].toDoubleOrNull() ?: return@mapNotNull null
        Eintrag(metall, f[6].trim(), tag, f[1].toDoubleOrNull(), f[2].toDoubleOrNull(), f[3].toDoubleOrNull(), gesamt, f[5].toDoubleOrNull())
    }.toList()

    // Bestehende Tage mit einem neuen Eintrag: derselbe Tag wird ueberschrieben, die letzten TAGE_MAX bleiben.
    fun zusammenfuehren(bestehend: List<Eintrag>, neu: Eintrag): List<Eintrag> =
        (bestehend.filter { it.tag != neu.tag } + neu).sortedBy { it.tag }.takeLast(TAGE_MAX)

    // ------------------------------------------------------------ Auswertung

    // Ein Metall: letzter Berichtstag, Veraenderung der Gesamtsumme gegen den vorigen eingelesenen Tag in Prozent (null ohne
    // Vergleich) und der Verlauf der Gesamtsumme ueber alle eingelesenen Tage.
    class Metall(val schluessel: String, val letzter: Eintrag, val aenderung: Double?, val verlauf: List<Pair<Long, Double>>)

    class Stand(val tag: Long, val metalle: List<Metall>)

    fun rang(schluessel: String): Int = REIHENFOLGE.indexOf(schluessel).let { if (it < 0) REIHENFOLGE.size else it }

    fun auswerten(tage: Map<String, List<Eintrag>>): Stand? {
        val liste = ArrayList<Metall>()
        for ((k, e) in tage) {
            val t = e.sortedBy { it.tag }.distinctBy { it.tag }
            val letzter = t.lastOrNull() ?: continue
            val vorher = t.getOrNull(t.size - 2)?.gesamt?.takeIf { it > 0 }
            val aenderung = vorher?.let { (letzter.gesamt / it - 1.0) * 100.0 }
            liste += Metall(k, letzter, aenderung, t.map { it.tag to it.gesamt })
        }
        if (liste.isEmpty()) return null
        val sortiert = liste.sortedWith(compareBy({ rang(it.schluessel) }, { it.schluessel }))
        return Stand(sortiert.maxOf { it.letzter.tag }, sortiert)
    }
}
