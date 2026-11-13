package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.IpoKalender
import de.troetenrepublik.app.market.IpoKalender.Status

// Sperrfristen, die bald enden (0.78): zurueckliegende Boersengaenge, deren Sperrfrist der Altaktionaere laut Prospekt
// in den naechsten 90 Tagen ablaeuft. Die Frist stammt immer aus dem Prospekt (IpoProspekt.sperrfrist); nennt er keine
// feste Zahl, gibt es keinen Eintrag. Je Boersengang wird der Prospekt einmal gelesen und das Ergebnis gemerkt.
// Reine Funktionen ohne Netz.
object IpoSperrfristen {

    // Enden bis so viele Tage voraus
    const val VORAUS_TAGE = 90

    // Geprueft werden Boersengaenge der letzten so vielen Monate (Kalendermonate des Abrufs) bzw. Tage
    const val MONATE = 7
    const val ZURUECK_TAGE = 200

    // Je Laden hoechstens so viele Prospekte neu lesen, die groessten Angebote zuerst
    const val JE_LADEN = 12

    // Hoechstens so viele Boersengaenge im Blick (nach Volumen)
    const val KANDIDATEN = 40

    // Ohne Ergebnis (kein Prospekt gefunden, keine feste Frist) fruehestens nach so vielen Tagen erneut lesen
    const val ERNEUT_TAGE = 14

    // Was aus dem Prospekt eines Boersengangs gelesen wurde. tage null: keine feste Frist lesbar. anteil in Prozent,
    // nachAngebot wie IpoProspekt.Gesperrt. zeit: Zeitpunkt des Lesens in Millisekunden.
    data class Gelesen(val tage: Int?, val anteil: Double?, val nachAngebot: Boolean, val zeit: Long)

    // Ein Eintrag der Liste. ende: Tag seit 1970, inTagen: Abstand zu heute.
    data class Eintrag(val angebot: IpoKalender.Angebot, val tage: Int, val ende: Long, val inTagen: Long, val anteil: Double?, val nachAngebot: Boolean)

    // Schluessel eines Boersengangs im Speicher: Kuerzel und Tag der Preisfestsetzung
    fun schluessel(a: IpoKalender.Angebot): String = a.symbol.orEmpty().uppercase() + ":" + (a.tag ?: 0L)

    // Gestartete Boersengaenge mit Kuerzel der letzten ZURUECK_TAGE, ohne Mantelgesellschaften, die groessten zuerst.
    fun kandidaten(liste: List<IpoKalender.Angebot>, heute: Long): List<IpoKalender.Angebot> =
        liste.filter { it.status == Status.PREIS && !it.spac && !it.symbol.isNullOrBlank() && it.tag != null && it.tag <= heute && it.tag >= heute - ZURUECK_TAGE }
            .distinctBy { schluessel(it) }
            .sortedWith(compareByDescending<IpoKalender.Angebot> { it.volumen ?: 0.0 }.thenBy { it.name })
            .take(KANDIDATEN)

    // Kandidaten, deren Prospekt jetzt gelesen werden soll: noch nie gelesen oder ohne Ergebnis und lange her.
    fun offen(kandidaten: List<IpoKalender.Angebot>, gelesen: Map<String, Gelesen>, jetzt: Long, max: Int = JE_LADEN): List<IpoKalender.Angebot> =
        kandidaten.filter { a ->
            val g = gelesen[schluessel(a)]
            g == null || (g.tage == null && (jetzt - g.zeit >= ERNEUT_TAGE * 86_400_000L || jetzt < g.zeit))
        }.take(max)

    // Eintraege mit Ende von heute bis VORAUS_TAGE voraus, das naechste Ende zuerst.
    fun eintraege(kandidaten: List<IpoKalender.Angebot>, gelesen: Map<String, Gelesen>, heute: Long): List<Eintrag> =
        kandidaten.mapNotNull { a ->
            val g = gelesen[schluessel(a)] ?: return@mapNotNull null
            val tage = g.tage ?: return@mapNotNull null
            val ende = (a.tag ?: return@mapNotNull null) + tage
            if (ende < heute || ende > heute + VORAUS_TAGE) null else Eintrag(a, tage, ende, ende - heute, g.anteil, g.nachAngebot)
        }.sortedWith(compareBy<Eintrag> { it.ende }.thenBy { it.angebot.name })

    fun ausAuszug(auszug: IpoProspekt.Auszug?, jetzt: Long): Gelesen =
        Gelesen(auszug?.sperrfristTage, auszug?.gesperrt?.anteil?.takeIf { auszug?.sperrfristTage != null }, auszug?.gesperrt?.nachAngebot == true, jetzt)

    // Speicher als Text: je Zeile Schluessel, Tage (leer ohne Frist), Anteil (leer ohne Angabe), 1 fuer nachAngebot, Zeit.
    fun schreiben(gelesen: Map<String, Gelesen>): String = gelesen.entries.sortedBy { it.key }.joinToString("\n") { (k, g) ->
        listOf(k, g.tage?.toString().orEmpty(), g.anteil?.toString().orEmpty(), if (g.nachAngebot) "1" else "0", g.zeit.toString()).joinToString("\t")
    }

    fun lesen(text: String): Map<String, Gelesen> = text.lineSequence().mapNotNull { z ->
        val f = z.split('\t')
        if (f.size < 5 || f[0].isBlank()) return@mapNotNull null
        val zeit = f[4].trim().toLongOrNull() ?: return@mapNotNull null
        f[0] to Gelesen(f[1].toIntOrNull()?.takeIf { it in 30..540 }, f[2].toDoubleOrNull()?.takeIf { it > 0 && it <= 100 }, f[3] == "1", zeit)
    }.toMap()
}
