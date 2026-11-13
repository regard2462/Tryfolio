package de.troetenrepublik.app.engine

// "Was das Netz zeigt": Kernaussagen, regelbasiert aus den Kanten des sichtbaren Ausschnitts. Sieben Regeln: Kreislauf,
// Kundenkonzentration, Lieferantenabhaengigkeit, gegenseitige Abhaengigkeit, bedingte Zusagen, Klumpen, Kontrolle. Die
// Aussagen nennen Zahlen aus den Kanten und keine Vermutung ueber Absichten. Kanten der Guete B und C zaehlen mit,
// die Aussage ist dann als gemischt gekennzeichnet (Warnzeile in der Anzeige).
object NetzAussagen {

    // In dieser Reihenfolge stehen die Aussagen
    enum class Typ { KREISLAUF, KONZENTRATION, LIEFERANT, KONTROLLE, GEGENSEITIG, WETTBEWERB, MANDATE, STAATSGELD, BEDINGT, KLUMPEN }

    // knoten: beteiligte Knoten (Reihenfolge je nach Typ), zahl bis zahl3 und n je nach Typ (siehe Regeln), teil: Schluesselteil
    class Aussage(
        val typ: Typ, val knoten: List<String>, val kanten: List<Netz.Kante>,
        val zahl: Double = 0.0, val zahl2: Double = 0.0, val zahl3: Double = 0.0, val n: Int = 0, val teil: String? = null, val zahl4: Double = 0.0,
        val kreis: NetzKreislauf.Kreislauf? = null, val bedingt: Boolean = false,
    ) {
        // Mindestens eine Kante mit Guete B oder C
        val gemischt: Boolean get() = kanten.any { it.guete != Netz.Guete.A }
    }

    // Schwellen
    const val KONZENTRATION_EINZEL = 10.0
    const val KONZENTRATION_DREI = 30.0
    const val KONTROLLE_MEHRHEIT = 50.0
    const val KLUMPEN_AB = 5
    const val MAX_AUSSAGEN = 5

    private fun usd(k: Netz.Kante): Double = k.umfang?.takeIf { it.einheit == Netz.Einheit.USD }?.wert ?: 0.0

    fun ableiten(g: Netz.Graph, sichtbar: Set<String>, heute: String, fokus: String? = null, maxAnzahl: Int = MAX_AUSSAGEN): List<Aussage> {
        val kanten = g.kanten.filter { it.aktiv(heute) && it.von in sichtbar && it.nach in sichtbar }
        if (kanten.isEmpty()) return emptyList()
        val alle = ArrayList<Aussage>()
        alle += kreislauf(g, sichtbar, heute, fokus)
        alle += konzentration(kanten, fokus)
        alle += lieferant(kanten, fokus)
        alle += kontrolle(kanten, fokus)
        alle += gegenseitig(kanten)
        alle += wettbewerb(kanten, fokus)
        alle += mandate(kanten)
        alle += staatsgeld(kanten)
        alle += bedingt(kanten)
        alle += klumpen(kanten, fokus)
        // Nach Typ geordnet, hoechstens maxAnzahl. Die Aussage zu bedingten Zusagen faellt nie heraus, wenn es bedingte Betraege gibt.
        val geordnet = alle.sortedBy { it.typ.ordinal }
        val auswahl = geordnet.take(maxAnzahl).toMutableList()
        val bedingt = geordnet.firstOrNull { it.typ == Typ.BEDINGT }
        if (bedingt != null && bedingt !in auswahl && auswahl.isNotEmpty()) {
            val raus = auswahl.lastOrNull { it.typ != Typ.KREISLAUF && it.typ != Typ.KONZENTRATION } ?: auswahl.last()
            auswahl.remove(raus)
            auswahl += bedingt
        }
        return auswahl.sortedBy { it.typ.ordinal }
    }

    // 1. Kreislauf: Geld fliesst ueber Beteiligung oder Finanzierung zu einem Kunden und kommt als Zusage zurueck. Feste Kreislaeufe
    // stehen als solche da, angekuendigte (Absichtserklaerung oder bedingt) sind gekennzeichnet (bedingt = true).
    // knoten: der Kreis, zahl: Kapital in USD, zahl2: Zusagen der Gegenrichtung in USD, zahl3: dieselben in GW, zahl4: bei festem
    // Kreislauf der nur bedingte Teil in USD. Hoechstens zwei, solche mit Fokus und dem groessten Kapitalfluss zuerst.
    fun kreislauf(g: Netz.Graph, sichtbar: Set<String>, heute: String, fokus: String?): List<Aussage> =
        NetzKreislauf.alle(g, sichtbar, heute)
            .sortedWith(compareBy({ if (fokus != null && fokus in it.knoten) 0 else 1 }, { if (it.angekuendigt) 1 else 0 }, { -(it.kapitalUsd + it.kapitalBedingtUsd) }, { it.laenge }))
            .take(2)
            .map { c ->
                val ang = c.angekuendigt
                val kapital = if (ang && c.kapitalUsd <= 0.0) c.kapitalBedingtUsd else c.kapitalUsd
                val handel = if (ang && c.handelUsd <= 0.0) c.handelBedingtUsd else c.handelUsd
                Aussage(
                    Typ.KREISLAUF, c.knoten, c.kanten, kapital, handel, c.handelGw, c.laenge, zahl4 = if (ang) 0.0 else c.kapitalBedingtUsd + c.handelBedingtUsd,
                    kreis = c, bedingt = ang,
                )
            }

    private fun prozent(k: Netz.Kante, bezug: Netz.Bezug): Double? =
        k.umfang?.takeIf { it.einheit == Netz.Einheit.PCT && it.bezug == bezug }?.wert

    // 2. Kundenkonzentration: ein Abnehmer ueber 10 % des Umsatzes oder die groessten drei zusammen ueber 30 %.
    // knoten: Lieferant, dann die groessten Abnehmer; zahl: groesster Anteil, zahl2: Summe der groessten drei, n: Zahl der Abnehmer
    fun konzentration(kanten: List<Netz.Kante>, fokus: String?): List<Aussage> {
        val je = kanten.filter { prozent(it, Netz.Bezug.UMSATZ) != null && it.lieferant != null && !(it.abnehmer ?: "").startsWith("kunden:") }.groupBy { it.lieferant!! }
        val treffer = ArrayList<Aussage>()
        for ((l, liste) in je) {
            val sortiert = liste.sortedByDescending { prozent(it, Netz.Bezug.UMSATZ) ?: 0.0 }
            val erste = prozent(sortiert.first(), Netz.Bezug.UMSATZ) ?: 0.0
            val drei = sortiert.take(3).sumOf { prozent(it, Netz.Bezug.UMSATZ) ?: 0.0 }
            if (erste > KONZENTRATION_EINZEL || drei > KONZENTRATION_DREI) {
                treffer += Aussage(Typ.KONZENTRATION, listOf(l) + sortiert.take(3).mapNotNull { it.abnehmer }, sortiert, erste, drei, n = sortiert.size)
            }
        }
        return treffer.sortedWith(compareBy({ if (fokus != null && it.knoten.first() == fokus) 0 else 1 }, { -it.zahl })).take(1)
    }

    // 3. Lieferantenabhaengigkeit: ein einziger belegter Zulieferer fuer ein Schluesselteil. knoten: Lieferant, dann die
    // Abnehmer; teil: Schluesselteil, n: Zahl der Abnehmer
    fun lieferant(kanten: List<Netz.Kante>, fokus: String?): List<Aussage> {
        val mitTeil = kanten.filter { it.teil != null && it.lieferant != null && it.abnehmer != null }
        val einzige = mitTeil.groupBy { it.abnehmer to it.teil }.values
            .filter { gruppe -> gruppe.map { it.lieferant }.distinct().size == 1 }
            .flatten().filter { it.schluessel }
        val treffer = ArrayList<Aussage>()
        for ((k, liste) in einzige.groupBy { (it.lieferant ?: "") to (it.teil ?: "") }) {
            val abnehmer = liste.mapNotNull { it.abnehmer }.distinct()
            treffer += Aussage(Typ.LIEFERANT, listOf(k.first) + abnehmer, liste, teil = k.second, n = abnehmer.size)
        }
        return treffer.sortedWith(compareBy({ if (fokus != null && fokus in it.knoten) 0 else 1 }, { -it.n })).take(1)
    }

    // 4. Gegenseitige Abhaengigkeit: zwei Firmen sind fuereinander Kunde und Lieferant oder halten Anteile aneinander.
    // knoten: beide, zahl: 0 Geschaeft, 1 Anteile
    fun gegenseitig(kanten: List<Netz.Kante>): List<Aussage> {
        val aus = ArrayList<Aussage>()
        val paare = HashSet<String>()
        for (k in kanten) {
            if (!(k.art.handel || k.art.kapital)) continue
            val a = k.zahler(); val b = k.empfaenger()
            val handel = k.art.handel
            val gegen = kanten.filter { (it.art.handel || it.art.kapital) && it.zahler() == b && it.empfaenger() == a && it.art.handel == handel }
            if (gegen.isEmpty()) continue
            if (!handel && !(k.art == Netz.Art.HAELT_ANTEIL && gegen.any { it.art == Netz.Art.HAELT_ANTEIL })) continue
            if (!paare.add(listOf(a, b).sorted().joinToString("|") + handel)) continue
            aus += Aussage(Typ.GEGENSEITIG, listOf(a, b), listOf(k) + gegen, zahl = if (handel) 0.0 else 1.0)
        }
        return aus.take(1)
    }

    // 5. Bedingte Zusagen: wie viele der Zusagen in USD nur Absichtserklaerung, bedingt oder Hoechstbetrag sind (gezaehlt, nicht
    // summiert), dazu die Optionsscheine. zahl: Zahl der bedingten, zahl2: Zahl aller Zusagen in USD, n: Zahl der Optionsscheine
    fun bedingt(kanten: List<Netz.Kante>): List<Aussage> {
        val betraege = kanten.filter { usd(it) > 0 && (it.art.handel || it.art.kapital) }
        val optionen = kanten.filter { it.art == Netz.Art.OPTIONSSCHEIN }
        val bed = betraege.filter { it.status == Netz.Status.ABSICHT || it.bedingt || it.umfang?.hoechstens == true }
        if (bed.isEmpty() && optionen.isEmpty()) return emptyList()
        if (betraege.size + optionen.size < 2) return emptyList()
        return listOf(Aussage(Typ.BEDINGT, emptyList(), bed + optionen, bed.size.toDouble(), betraege.size.toDouble(), n = optionen.size, bedingt = true))
    }

    // 6. Verbunden: viele Firmen laufen ueber dieselbe Firma (nie die Aktie im Fokus selbst). knoten: die Firma, n: Zahl der
    // verbundenen Firmen, zahl: Zahl der Verbindungen
    fun klumpen(kanten: List<Netz.Kante>, fokus: String? = null): List<Aussage> {
        val ohneTochter = kanten.filter { it.art.handel || it.art.kapital }
        val nachbarn = HashMap<String, MutableSet<String>>()
        for (k in ohneTochter) {
            nachbarn.getOrPut(k.von) { HashSet() }.add(k.nach)
            nachbarn.getOrPut(k.nach) { HashSet() }.add(k.von)
        }
        val top = nachbarn.entries.filter { it.key != fokus }
            .maxWithOrNull(compareBy<Map.Entry<String, MutableSet<String>>>({ it.value.size }, { it.key })) ?: return emptyList()
        if (top.value.size < KLUMPEN_AB) return emptyList()
        val seine = ohneTochter.filter { it.von == top.key || it.nach == top.key }
        return listOf(Aussage(Typ.KLUMPEN, listOf(top.key), seine, seine.size.toDouble(), n = top.value.size))
    }

    // 8. Wettbewerb: Wettbewerber im Netz, davon zugleich Kunde oder Lieferant. Mit Fokus zaehlen die Wettbewerber dieser Firma,
    // sonst alle Paare im Ausschnitt. knoten: beteiligte Firmen, zahl: Zahl der Wettbewerber (Paare), zahl2: davon zugleich Kunde oder Lieferant
    fun wettbewerb(kanten: List<Netz.Kante>, fokus: String?): List<Aussage> {
        val wett = kanten.filter { it.art == Netz.Art.KONKURRIERT }.filter { fokus == null || it.von == fokus || it.nach == fokus }
        if (wett.isEmpty()) return emptyList()
        val geschaeft = kanten.filter { it.art.handel }
        val paarWeise = wett.distinctBy { listOf(it.von, it.nach).sorted() }
        val auch = paarWeise.count { w -> geschaeft.any { g -> (g.von == w.von && g.nach == w.nach) || (g.von == w.nach && g.nach == w.von) } }
        val firmen = paarWeise.flatMap { listOf(it.von, it.nach) }.distinct().filter { it != fokus }
        return listOf(Aussage(Typ.WETTBEWERB, firmen, wett, paarWeise.size.toDouble(), auch.toDouble(), n = firmen.size))
    }

    // 9. Ueberkreuzmandate: Personen mit Mandat in mehreren Firmen des Ausschnitts. knoten: die Firmen, zahl: Zahl der Personen
    fun mandate(kanten: List<Netz.Kante>): List<Aussage> {
        val je = kanten.filter { it.art == Netz.Art.MANDAT }.groupBy { it.von }.filter { (_, l) -> l.map { it.nach }.distinct().size >= 2 }
        if (je.isEmpty()) return emptyList()
        val firmen = je.values.flatten().map { it.nach }.distinct()
        return listOf(Aussage(Typ.MANDATE, firmen, je.values.flatten(), je.size.toDouble(), n = firmen.size))
    }

    // 10. Staatsgeld: Zusagen von Staaten und Behoerden im Ausschnitt, davon ausgezahlt. Zurueckgezogene zaehlen nicht.
    // zahl: USD, zahl2: davon ausgezahlt in USD, zahl3: EUR, zahl4: davon ausgezahlt in EUR
    fun staatsgeld(kanten: List<Netz.Kante>): List<Aussage> {
        val s = kanten.filter { it.art == Netz.Art.STAAT_FINANZIERT && it.umfang != null }
        if (s.isEmpty()) return emptyList()
        fun summe(e: Netz.Einheit, nurGezahlt: Boolean) =
            s.filter { it.umfang?.einheit == e && (!nurGezahlt || it.status == Netz.Status.AUSGEZAHLT) }.sumOf { it.umfang?.wert ?: 0.0 }
        val usd = summe(Netz.Einheit.USD, false); val eur = summe(Netz.Einheit.EUR, false)
        if (usd + eur <= 0.0) return emptyList()
        return listOf(Aussage(Typ.STAATSGELD, s.map { it.nach }.distinct(), s, usd, summe(Netz.Einheit.USD, true), eur, s.size, zahl4 = summe(Netz.Einheit.EUR, true)))
    }

    // 7. Kontrolle: ein Halter mit mehr als 50 % der Stimmen. knoten: Halter, Ziel; zahl: Prozent der Stimmen. Die Sperrminoritaet
    // gilt nach deutschem und oesterreichischem Aktienrecht, nicht allgemein, und steht hier nicht.
    fun kontrolle(kanten: List<Netz.Kante>, fokus: String?): List<Aussage> =
        kanten.filter { it.art == Netz.Art.HAELT_ANTEIL && (prozent(it, Netz.Bezug.STIMMEN) ?: 0.0) > KONTROLLE_MEHRHEIT }
            .sortedWith(compareBy({ if (fokus != null && (it.von == fokus || it.nach == fokus)) 0 else 1 }, { -(prozent(it, Netz.Bezug.STIMMEN) ?: 0.0) }))
            .take(2)
            .map { Aussage(Typ.KONTROLLE, listOf(it.von, it.nach), listOf(it), prozent(it, Netz.Bezug.STIMMEN) ?: 0.0, 1.0) }
}
