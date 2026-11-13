package de.troetenrepublik.app.engine

// Kreislaeufe im Netz: Wege der Laenge 2 bis 4, die ueber die Zahlrichtung der Kanten zur Ausgangsfirma zurueckfuehren, mit
// mindestens einer Kapitalkante (Beteiligung, Finanzierung, Optionsschein, Garantie) und einer Geschaeftskante (Lieferung,
// Kauf, Cloud, Lizenz). Beispiel: Nvidia beteiligt sich an OpenAI, OpenAI kauft Nvidia-Systeme.
object NetzKreislauf {

    // knoten: Ausgangsfirma zuerst, ohne Wiederholung am Ende; schritte: je Schritt alle parallelen Kanten
    class Kreislauf(val knoten: List<String>, val schritte: List<List<Netz.Kante>>) {
        val kanten: List<Netz.Kante> get() = schritte.flatten()
        val laenge: Int get() = knoten.size

        private fun fest(k: Netz.Kante) = k.status == Netz.Status.DEFINITIV && !k.bedingt

        private fun usd(k: Netz.Kante) = k.umfang?.takeIf { u -> u.einheit == Netz.Einheit.USD }?.wert ?: 0.0

        // Betrag eines Schritts in USD, getrennt: groesste feste Kante und groesste bedingte Kante (Absichtserklaerung, bedingt,
        // Hoechstbetrag). Parallele Kanten desselben Wegs zaehlen nicht doppelt.
        private fun betragFest(schritt: List<Netz.Kante>): Double = schritt.filter { fest(it) }.maxOfOrNull { usd(it) } ?: 0.0
        private fun betragBedingt(schritt: List<Netz.Kante>): Double = schritt.filter { !fest(it) }.maxOfOrNull { usd(it) } ?: 0.0

        // Leistung in GW: die feste, sonst die bedingte Angabe
        private fun leistung(schritt: List<Netz.Kante>): Double {
            fun gw(k: Netz.Kante) = k.umfang?.takeIf { u -> u.einheit == Netz.Einheit.GW }?.wert ?: 0.0
            val f = schritt.filter { fest(it) }.maxOfOrNull { gw(it) } ?: 0.0
            return if (f > 0.0) f else schritt.maxOfOrNull { gw(it) } ?: 0.0
        }

        val kapitalSchritte: List<List<Netz.Kante>> get() = schritte.filter { s -> s.any { it.art.kapital } }
        val handelSchritte: List<List<Netz.Kante>> get() = schritte.filter { s -> s.any { it.art.handel } }

        // Summe der festen Kapitalfluesse und Umsaetze in USD; dazu die nur bedingten (Absichtserklaerung, bedingt, Hoechstbetrag)
        val kapitalUsd: Double get() = kapitalSchritte.sumOf { betragFest(it) }
        val handelUsd: Double get() = handelSchritte.sumOf { betragFest(it) }
        val kapitalBedingtUsd: Double get() = kapitalSchritte.sumOf { betragBedingt(it) }
        val handelBedingtUsd: Double get() = handelSchritte.sumOf { betragBedingt(it) }
        val handelGw: Double get() = handelSchritte.sumOf { leistung(it) }

        // Angekuendigt: mindestens ein Schritt hat keine feste Kante, der Kreislauf steht nur auf Absichtserklaerungen oder Bedingungen
        val angekuendigt: Boolean get() = schritte.any { s -> s.none { fest(it) } }
    }

    // Zahlrichtung: zahler -> empfaenger -> Kanten (nur aktive Geschaefts- und Kapitalkanten, nur zwischen erlaubten Knoten)
    private fun richtung(g: Netz.Graph, heute: String, erlaubt: Set<String>?): Map<String, Map<String, List<Netz.Kante>>> {
        val m = HashMap<String, HashMap<String, MutableList<Netz.Kante>>>()
        for (k in g.kanten) {
            if (!(k.art.handel || k.art.kapital) || !k.aktiv(heute)) continue
            if (erlaubt != null && (k.von !in erlaubt || k.nach !in erlaubt)) continue
            m.getOrPut(k.zahler()) { HashMap() }.getOrPut(k.empfaenger()) { ArrayList() }.add(k)
        }
        return m
    }

    private fun gueltig(schritte: List<List<Netz.Kante>>): Boolean =
        schritte.any { s -> s.any { it.art.kapital } } && schritte.any { s -> s.any { it.art.handel } }

    // Kreislaeufe durch start (Laenge 2 bis maxLaenge), hoechstens "hoechstens" Stueck; sichtbar: nur Kanten zwischen diesen Knoten.
    fun suche(
        g: Netz.Graph, start: String, heute: String, maxLaenge: Int = 4, hoechstens: Int = 400, sichtbar: Set<String>? = null,
    ): List<Kreislauf> {
        val r = richtung(g, heute, sichtbar)
        val aus = ArrayList<Kreislauf>()
        val pfad = ArrayList<String>()
        pfad += start
        fun gehe(aktuell: String) {
            if (aus.size >= hoechstens) return
            for ((ziel, kanten) in r[aktuell].orEmpty()) {
                if (ziel == start) {
                    if (pfad.size >= 2) {
                        val knoten = ArrayList(pfad)
                        val schritte = ArrayList<List<Netz.Kante>>()
                        for (i in knoten.indices) {
                            val a = knoten[i]; val b = if (i + 1 < knoten.size) knoten[i + 1] else start
                            schritte += r[a]?.get(b).orEmpty()
                        }
                        if (gueltig(schritte)) aus += Kreislauf(knoten, schritte)
                    }
                } else if (ziel !in pfad && pfad.size < maxLaenge) {
                    pfad += ziel
                    gehe(ziel)
                    pfad.removeAt(pfad.size - 1)
                }
            }
        }
        gehe(start)
        return aus.sortedWith(compareBy({ it.laenge }, { -it.kapitalUsd }))
    }

    // Alle Kreislaeufe zwischen den sichtbaren Knoten, je Knotenfolge einmal (gleicher Kreis von verschiedenen Startpunkten zaehlt einmal)
    fun alle(g: Netz.Graph, sichtbar: Set<String>, heute: String, maxLaenge: Int = 4, hoechstens: Int = 400): List<Kreislauf> {
        val gesehen = HashSet<String>()
        val aus = ArrayList<Kreislauf>()
        for (id in sichtbar.sorted()) {
            for (k in suche(g, id, heute, maxLaenge, hoechstens, sichtbar)) {
                val schluessel = kanonisch(k.knoten)
                if (gesehen.add(schluessel)) aus += k
                if (aus.size >= hoechstens) return aus
            }
        }
        return aus
    }

    // Folge, gedreht auf den kleinsten Knoten (gleicher Kreis, gleicher Schluessel)
    private fun kanonisch(knoten: List<String>): String {
        val i = knoten.indices.minByOrNull { knoten[it] } ?: return ""
        return (knoten.drop(i) + knoten.take(i)).joinToString(">")
    }
}
