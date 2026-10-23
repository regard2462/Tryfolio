package de.troetenrepublik.app.market

// Wer einen Wert handelt: Abgeordnete, Senatoren und die Regierung mit ihren gemeldeten Geschaeften der
// letzten zwoelf Monate, die Investoren mit der Veraenderung im letzten gemeldeten Quartal (13F), je Person
// zusammengefasst. Ob eine Person eher kauft oder verkauft, entscheidet die geschaetzte Summe (Mitte der Spannen).
object Handelnde {

    enum class Gruppe { HAUS, SENAT, REGIERUNG, INVESTOR }

    enum class Richtung { KAUF, VERKAUF, GEMISCHT, HALTEN }

    // Eine Person mit allen ihren Geschaeften; schluessel ist die Route ihrer Seite.
    data class Person(val gruppe: Gruppe, val schluessel: String, val name: String, val geschaefte: List<Kongress.Geschaeft>, val kennung: String = "")

    data class Eintrag(
        val gruppe: Gruppe,
        val schluessel: String,
        val name: String,
        val kaeufe: Int = 0,
        val verkaeufe: Int = 0,
        val kaufSumme: Double = 0.0,
        val verkaufSumme: Double = 0.0,
        val letzte: Long = 0L,
        // Investoren: Veraenderung im letzten Quartal; Bestaende (Vermoegensmeldung): GLEICH.
        val wandel: Investoren.Wandel? = null,
        // Investoren: Anteil am gemeldeten Depot (0..1).
        val anteil: Double = 0.0,
        // Investoren und Bestaende: Stichtag (ISO).
        val stichtag: String = "",
        val firma: String = "",
        // Schluessel wie beim Folgen (k:, n:, r: oder i: mit Kennung), fuer das Profilbild.
        val kennung: String = "",
        // Abgeordnete, Senat, Regierung: die einzelnen Geschaefte mit dem Wert, neueste zuerst.
        val geschaefte: List<Kongress.Geschaeft> = emptyList(),
        // Investoren: Stueck und Wert (Dollar) zum Stichtag, Stueck im Quartal davor (null ohne Vorquartal).
        val stueck: Double = 0.0,
        val stueckVorher: Double? = null,
        val wert: Double = 0.0,
    ) {
        val richtung: Richtung get() = when (wandel) {
            Investoren.Wandel.NEU, Investoren.Wandel.ERHOEHT -> Richtung.KAUF
            Investoren.Wandel.VERRINGERT, Investoren.Wandel.VERKAUFT -> Richtung.VERKAUF
            Investoren.Wandel.GLEICH -> Richtung.HALTEN
            null -> when {
                kaufSumme > verkaufSumme * 1.5 -> Richtung.KAUF
                verkaufSumme > kaufSumme * 1.5 -> Richtung.VERKAUF
                else -> Richtung.GEMISCHT
            }
        }
    }

    // Arten, die fuer die Richtung zaehlen: Aktien, Fonds und Sonstiges mit Kuerzel; Optionen und Anleihen nicht.
    private val ARTEN = setOf("ST", "EF", "OT")

    // Geschaefte der letzten tage Tage mit passendem Kuerzel, je Person.
    fun geschaefte(personen: List<Person>, passt: (String) -> Boolean, jetzt: Long, tage: Int = 365): List<Eintrag> {
        val grenze = jetzt - tage * 86_400_000L
        return personen.mapNotNull { p ->
            val l = p.geschaefte.filter { g ->
                g.datum >= grenze && g.typ in ARTEN && g.kuerzel?.let(passt) == true && (g.kauf || g.verkauf)
            }
            if (l.isEmpty()) return@mapNotNull null
            val k = l.filter { it.kauf }
            val v = l.filter { it.verkauf }
            Eintrag(
                p.gruppe, p.schluessel, p.name, k.size, v.size, k.sumOf { it.mitte }, v.sumOf { it.mitte }, l.maxOf { it.datum }, kennung = p.kennung,
                geschaefte = l.sortedByDescending { it.datum },
            )
        }
    }

    // Investoren, die den Wert im letzten oder vorletzten Quartal hielten, mit der Veraenderung.
    fun investoren(liste: List<Investoren.Investor>, passt: (String) -> Boolean): List<Eintrag> = liste.mapNotNull { inv ->
        val q = inv.aktuell ?: return@mapNotNull null
        fun stueck(qu: Investoren.Quartal?) = qu?.positionen.orEmpty().filter { it.art == "aktie" && it.kuerzel?.let(passt) == true }
        val jetzt = stueck(q)
        val vorher = stueck(inv.vorher)
        if (jetzt.isEmpty() && vorher.isEmpty()) return@mapNotNull null
        val n = jetzt.sumOf { it.stueck }
        val a = vorher.sumOf { it.stueck }
        val wandel = when {
            jetzt.isEmpty() -> Investoren.Wandel.VERKAUFT
            inv.vorher == null -> Investoren.Wandel.GLEICH
            vorher.isEmpty() -> Investoren.Wandel.NEU
            n > a * 1.005 -> Investoren.Wandel.ERHOEHT
            n < a * 0.995 -> Investoren.Wandel.VERRINGERT
            else -> Investoren.Wandel.GLEICH
        }
        val summe = q.summe.takeIf { it > 0 } ?: q.positionen.sumOf { it.wert }
        Eintrag(
            Gruppe.INVESTOR, inv.id, inv.name, wandel = wandel, firma = inv.firma, stichtag = q.stichtag, kennung = "i:${inv.id}",
            anteil = if (summe > 0) jetzt.sumOf { it.wert } / summe else 0.0,
            stueck = n, stueckVorher = if (inv.vorher != null) a else null, wert = jetzt.sumOf { it.wert },
        )
    }

    // Bestand laut Vermoegensmeldung (etwa der Vizepraesident): haelt den Wert zum Stichtag.
    fun bestand(gruppe: Gruppe, schluessel: String, name: String, b: Politik.Bestand?, passt: (String) -> Boolean, kennung: String = ""): Eintrag? {
        if (b == null || b.positionen.none { p -> Politik.kuerzel(p.k)?.let(passt) == true }) return null
        return Eintrag(gruppe, schluessel, name, wandel = Investoren.Wandel.GLEICH, stichtag = b.stichtag, kennung = kennung)
    }

    // Zusammenfassung: wie viele Personen kaufen, verkaufen, halten; Tendenz nur bei klarer Mehrheit.
    data class Bild(val kaeufer: Int, val verkaeufer: Int, val halten: Int, val gemischt: Int) {
        val tendenz: Richtung? get() = when {
            kaeufer + verkaeufer == 0 -> null
            kaeufer >= verkaeufer * 1.5 && kaeufer > verkaeufer -> Richtung.KAUF
            verkaeufer >= kaeufer * 1.5 && verkaeufer > kaeufer -> Richtung.VERKAUF
            else -> Richtung.GEMISCHT
        }
    }

    fun bild(e: List<Eintrag>) = Bild(
        e.count { it.richtung == Richtung.KAUF },
        e.count { it.richtung == Richtung.VERKAUF },
        e.count { it.richtung == Richtung.HALTEN },
        e.count { it.richtung == Richtung.GEMISCHT },
    )
}
