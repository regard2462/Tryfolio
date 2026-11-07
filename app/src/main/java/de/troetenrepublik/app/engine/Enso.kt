package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable

// ENSO (El Nino und La Nina) nach dem Climate Prediction Center der NOAA: Status aus der monatlichen Diskussion
// (ensodisc.shtml) und der Index RONI (relativer Oceanic Nino Index, Dreimonatsmittel der Meerestemperatur in der
// Nino-3.4-Region gegen den Schnitt aller tropischen Ozeane) aus RONI.ascii.txt. Beide eine Woche gespeichert.
object Enso {
    const val ADRESSE_STATUS = "https://www.cpc.ncep.noaa.gov/products/analysis_monitoring/enso_advisory/ensodisc.shtml"
    const val ADRESSE_INDEX = "https://www.cpc.ncep.noaa.gov/data/indices/RONI.ascii.txt"
    const val GUELTIG_MS = 7 * 24 * 3_600_000L
    // NOAA: ein Ereignis beginnt bei einem Dreimonatsindex ab +0,5 bzw. unter -0,5 Grad; ab 1,5 stark, ab 2,0 sehr stark
    const val SCHWELLE = 0.5
    const val STARK = 1.5
    const val SEHR_STARK = 2.0

    // Werte zum Antippen unter dem Abschnitt: Kaffee, Kakao, Zucker, Weizen, Mais, Agrarhaendler und Duenger (nur, wenn im Katalog)
    val WERTE = listOf("KC=F", "CC=F", "SB=F", "ZW=F", "ZC=F", "ADM", "BG", "MOS", "CF", "YAR.OL")

    enum class Zustand { EL_NINO_ADVISORY, EL_NINO_WATCH, LA_NINA_ADVISORY, LA_NINA_WATCH, NEUTRAL, UNBEKANNT }

    @Serializable
    class Status(val abgerufen: Long, val zustand: String)

    // saison: Dreimonatskuerzel wie "ASO"; jahr: Jahr des mittleren Monats
    @Serializable
    class Punkt(val saison: String, val jahr: Int, val wert: Double)

    @Serializable
    class Index(val abgerufen: Long, val punkte: List<Punkt>)

    private fun klartext(s: String): String =
        s.replace("&ntilde;", "n").replace("&Ntilde;", "N").replace("&nbsp;", " ").replace("&#37;", "%").replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ").trim()

    // Seite der Diskussion: Zeile nach „ENSO Alert System Status“ bis zur Zusammenfassung
    fun statusLesen(html: String, jetzt: Long): Status? = runCatching {
        val i = html.indexOf("ENSO Alert System Status")
        if (i < 0) return null
        val kopf = klartext(html.substring(i, minOf(html.length, i + 700))).lowercase()
        val nach = kopf.substringAfter("status:", kopf).substringBefore("synopsis")
        val nino = "el nino" in nach
        val nina = "la nina" in nach
        val z = when {
            nino && "advisory" in nach -> Zustand.EL_NINO_ADVISORY
            nino && "watch" in nach -> Zustand.EL_NINO_WATCH
            nina && "advisory" in nach -> Zustand.LA_NINA_ADVISORY
            nina && "watch" in nach -> Zustand.LA_NINA_WATCH
            "neutral" in nach || "not active" in nach -> Zustand.NEUTRAL
            else -> return null
        }
        Status(jetzt, z.name)
    }.getOrNull()

    // RONI.ascii.txt: Kopfzeile „SEAS   YR  ANOM“, dann je Zeile Saison, Jahr, Wert; die letzten 24 Dreimonatswerte
    fun indexLesen(text: String, jetzt: Long): Index? = runCatching {
        val alle = ArrayList<Punkt>()
        for (z in text.lineSequence()) {
            val t = z.trim().split(Regex("\\s+"))
            if (t.size < 3) continue
            val j = t[1].toIntOrNull() ?: continue
            val w = t[2].toDoubleOrNull() ?: continue
            if (t[0].length == 3) alle += Punkt(t[0], j, w)
        }
        if (alle.isEmpty()) null else Index(jetzt, alle.takeLast(24))
    }.getOrNull()

    // Staerke eines Wertes: 0 neutral, 1 schwach, 2 maessig, 3 stark, 4 sehr stark; Vorzeichen fuer La Nina negativ
    fun staerke(w: Double): Int {
        val a = kotlin.math.abs(w)
        val s = when {
            a >= SEHR_STARK -> 4
            a >= STARK -> 3
            a >= 1.0 -> 2
            a >= SCHWELLE -> 1
            else -> 0
        }
        return if (w < 0) -s else s
    }
}
