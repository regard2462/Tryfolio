package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Einflussverzeichnis.Art
import de.troetenrepublik.app.engine.Einflussverzeichnis.Typ
import de.troetenrepublik.app.engine.Einflussverzeichnis.Wertbild

// [Grosses Ganzes 0.73] Zeilen der Karte "Abhaengigkeiten und Risiken" auf der Wertpapierseite. Die Rechnung setzt die
// Zeilen aus Einflussverzeichnis, Landdaten, Live-Staenden der Engpaesse und Spannungsgebiete und der Kursempfindlichkeit
// zusammen; die Anzeige formatiert sie. Was es nicht gibt, steht als Luecke da, nicht als Schweigen.
object Abhaengigkeiten {
    enum class Abschnitt { HERKUNFT, ROHSTOFFE, EINFLUESSE, POLITIK, EMPFINDLICHKEIT, LUECKEN }

    enum class Zeilentyp {
        SITZ, TAETIGKEIT, MUTTER, GESAMTBILD, LANDRISIKO, KATASTROPHEN,
        ROHSTOFF, GROESSE,
        EMPF_MARKT, EMPF_FAKTOR, EMPF_OHNE, EMPF_ZU_KURZ,
        LUECKE_SEGMENTE,
        // [0.75] Einordnungen, deren Ablauf vorbei ist: ganz = Anzahl, zahl = spaetester Ablauf (Tage seit 1970)
        LUECKE_ABGELAUFEN,
    }

    // art null: keine Kennzeichnung (Luecken); stand in Tagen seit 1970; grau: Stand aelter als 18 Monate.
    // schluessel: Land (alpha-2), Groesse, Rohstoff oder Faktor; zahl, zahl2, ganz: je nach Typ (siehe unten);
    // liste: Laender, Faktoren oder Titel; ziel: Route, zu der ein Tipp fuehrt (null: keine)
    class Zeile(
        val abschnitt: Abschnitt, val typ: Zeilentyp, val art: Art?, val stand: Long?,
        val schluessel: String = "", val zahl: Double? = null, val zahl2: Double? = null, val ganz: Int? = null,
        val aussage: Empfindlichkeit.Aussage? = null, val liste: List<String> = emptyList(), val ziel: String? = null,
        val grau: Boolean = false, val richtung: Int = 0,
        // [0.76] auffaellig: Stand im Engpass oder Spannungsgebiet ist gerade auffaellig; messung: am Kurs gemessener Zusammenhang
        // mit derselben Groesse (Zins, Oel, Dollar, China, Halbleiter, Kupfer, Gold); fakten: Bausteine des Landrisikos
        val auffaellig: Boolean = false, val messung: Messung? = null, val fakten: LandRisiko.Fakten? = null,
        // [0.76] Rohstoffzeile: Verwendung, Weg und Quelle der Zuordnung zum Unternehmen
        val rohstoff: Rohstoffprofil.Treffer? = null,
        // [0.77] Verbindung, die den Weg der Wirkung auf dieses Unternehmen nennt (Beleg, Branche, Gebiet); null: nur eine Liste
        val weg: Einflussverzeichnis.Kante? = null,
        // [0.76] Foerdermenge des Unternehmens bei diesem Rohstoff, wo eine freie Quelle sie nennt
        val firmaMenge: Einflussverzeichnis.FirmaMenge? = null,
        // [0.76] Kapazitaet der Bergwerke des Unternehmens bei diesem Rohstoff (USGS Minerals Yearbook)
        val anlage: Einflussverzeichnis.Anlage? = null,
    )

    // [0.76] Gemessener Zusammenhang des Kurses mit einer Groesse (wert: Steigung wie in Empfindlichkeit.Koeffizient)
    class Messung(val faktor: Empfindlichkeit.Faktor, val aussage: Empfindlichkeit.Aussage, val wert: Double)

    // Alles, was die Karte ueber den Wert weiss. Nicht Geladenes bleibt null; die Rechnung ist deshalb jederzeit
    // wiederholbar, sobald mehr da ist.
    // gesamtbild: Punkte des Landes 0 bis 100 (Jahr in gesamtbildJahr); landPunkte: Landrisiko 0 bis 100;
    // katastrophen: aktuelle Ereignisse im Land;
    // engpaesse: Lage je Engpass (Schluessel ohne Vorsatz); spannungen: Wert je Gebiet
    // Konzernmutter aus dem Verzeichnis der Rechtstraeger (name, land alpha-2, stand in Tagen seit 1970)
    class Konzernmutter(val name: String, val land: String, val stand: Long)

    data class Eingabe(
        val wert: Wertbild,
        val taetigkeit: String? = null,
        val gesamtbild: Double? = null, val gesamtbildJahr: Int = 0,
        val landPunkte: Double? = null,
        val katastrophen: Int? = null, val katastrophenRot: Int = 0,
        val engpaesse: Map<String, Engpaesse.Lage> = emptyMap(),
        val spannungen: Map<String, Spannungen.Wert> = emptyMap(),
        val empfindlichkeit: Empfindlichkeit.Ergebnis? = null,
        val empfindlichkeitZuKurz: Boolean = false,
        val usWert: Boolean = false,
        val landStand: Long? = null,
        val konzernmutter: Konzernmutter? = null,
        // [0.76] Fakten hinter dem Landrisiko, damit die Karte die Bausteine zeigen kann
        val landFakten: LandRisiko.Fakten? = null,
        // [0.76] Angaben aus Einreichungen bei der SEC (Form SD, Rohstoffabschnitt des Jahresberichts)
        val sec: Rohstoffprofil.SecAngaben? = null,
    )

    // Engpass mit Rueckgang ab dieser Abweichung (Prozent) und Gebiet mit mindestens diesem Faktor gelten als auffaellig
    const val AUFFAELLIG_ENGPASS = Engpaesse.STOERUNG_PROZENT
    const val AUFFAELLIG_SPANNUNG = 1.5

    private fun auffaellig(g: Einflussverzeichnis.Groesse, e: Eingabe): Boolean = when (g.typ) {
        Typ.ENGPASS -> e.engpaesse[g.kurz]?.let { (it.abw ?: 0.0) <= AUFFAELLIG_ENGPASS && (it.vn ?: 0.0) >= Engpaesse.MIND_VORJAHR } ?: false
        Typ.SPANNUNG -> (e.spannungen[g.kurz]?.faktor ?: 0.0) >= AUFFAELLIG_SPANNUNG
        else -> false
    }

    // [0.76] Welche Kursreihe der Empfindlichkeit zu einer Einflussgroesse passt (null: keine)
    private fun faktorFuer(groesseId: String): Empfindlichkeit.Faktor? = when (groesseId) {
        "zins" -> Empfindlichkeit.Faktor.ZINS10J
        "oel" -> Empfindlichkeit.Faktor.OEL
        "dollar" -> Empfindlichkeit.Faktor.DOLLAR
        "china" -> Empfindlichkeit.Faktor.CHINA
        "halbleiter" -> Empfindlichkeit.Faktor.HALBLEITER
        else -> null
    }

    private fun messung(e: Eingabe, f: Empfindlichkeit.Faktor?): Messung? {
        if (f == null) return null
        val k = e.empfindlichkeit?.koeffizienten?.firstOrNull { it.faktor == f } ?: return null
        return Messung(f, Empfindlichkeit.aussage(k), k.wert)
    }

    private fun abschnitt(t: Typ) = if (t == Typ.PROGRAMM) Abschnitt.POLITIK else Abschnitt.EINFLUESSE

    // Richtung der Wirkung auf den Wert, wenn sie belegt ist: am Kurs gemessen (+1 laeuft mit, -1 laeuft gegen), sonst die der
    // Verbindung; 0 = keine
    internal fun richtungVon(messung: Messung?, kante: Einflussverzeichnis.Kante?, weg: Einflussverzeichnis.Kante?): Int = when (messung?.aussage) {
        Empfindlichkeit.Aussage.LAEUFT_MIT -> 1
        Empfindlichkeit.Aussage.LAEUFT_GEGEN -> -1
        else -> weg?.richtung?.takeIf { it != 0 } ?: kante?.richtung ?: 0
    }

    // Zeile einer Groesse mit Live-Stand (zahl: Abweichung gegen das Vorjahr in Prozent bzw. Faktor gegen die Vorwoche)
    private fun groessenZeile(i: Einflussverzeichnis.Index, v: Einflussverzeichnis.Verweis, e: Eingabe, heute: Long): Zeile {
        val g = v.groesse
        val weg = Einflussverzeichnis.wegKante(i, e.wert, g.id, heute)
        val messung = messung(e, faktorFuer(g.id))
        val live = when (g.typ) {
            Typ.ENGPASS -> e.engpaesse[g.kurz]?.abw
            Typ.SPANNUNG -> e.spannungen[g.kurz]?.faktor
            else -> null
        }
        val ziel = when (g.typ) {
            Typ.ENGPASS, Typ.SPANNUNG -> "globus/engpaesse"
            else -> "wirkung/" + g.id
        }
        return Zeile(
            abschnitt(g.typ), Zeilentyp.GROESSE, v.kante.art, v.kante.stand, g.id, zahl = live, ziel = ziel,
            grau = Einflussverzeichnis.alterMonate(v.kante.stand, heute) >= Einflussverzeichnis.GRAU_AB_MONATE,
            richtung = richtungVon(messung, v.kante, weg), auffaellig = auffaellig(g, e), messung = messung, weg = weg,
        )
    }

    // Die Karte eines Werts (Aktie). Reihenfolge der Abschnitte: Herkunft, Rohstoffe, Einfluesse, Politik, Kursempfindlichkeit,
    // Luecken.
    fun karte(i: Einflussverzeichnis.Index, e: Eingabe, heute: Long): List<Zeile> {
        val aus = ArrayList<Zeile>()
        val w = e.wert
        val land = w.land
        val ls = e.landStand

        // Herkunft: Sitz (gemeldet), Taetigkeitsland (abgeleitet aus der Beschreibung), Land mit Gesamtbild und Risiko
        w.sitz?.let { aus += Zeile(Abschnitt.HERKUNFT, Zeilentyp.SITZ, Art.GEMELDET, null, it, ziel = "land/$it") }
        e.taetigkeit?.takeIf { it != w.sitz }?.let { aus += Zeile(Abschnitt.HERKUNFT, Zeilentyp.TAETIGKEIT, Art.ABGELEITET, null, it, ziel = "land/$it") }
        e.konzernmutter?.let { aus += Zeile(Abschnitt.HERKUNFT, Zeilentyp.MUTTER, Art.GEMELDET, it.stand, it.land, liste = listOf(it.name)) }
        if (land != null) {
            e.gesamtbild?.let { aus += Zeile(Abschnitt.HERKUNFT, Zeilentyp.GESAMTBILD, Art.ABGELEITET, ls, land, zahl = it, ganz = e.gesamtbildJahr, ziel = "land/$land") }
            e.landPunkte?.let { aus += Zeile(Abschnitt.HERKUNFT, Zeilentyp.LANDRISIKO, Art.ABGELEITET, ls, land, zahl = it, ziel = "land/$land", fakten = e.landFakten) }
            e.katastrophen?.takeIf { it > 0 }?.let { aus += Zeile(Abschnitt.HERKUNFT, Zeilentyp.KATASTROPHEN, Art.ABGELEITET, ls, land, ganz = it, zahl = e.katastrophenRot.toDouble(), ziel = "land/$land") }
        }

        // [0.76] Rohstoffe des Unternehmens (sonst der Industrie oder Branche) mit Foerderlaendern und Konzentration (USGS), die
        // staerkste Konzentration zuerst
        val roh = Rohstoffprofil.fuer(i, w, e.sec).treffer.mapNotNull { t -> i.rohstoffe[t.id]?.let { it to t } }
        val alter = Einflussverzeichnis.alterMonate(i.rohstoffStand, heute)
        if (alter < Einflussverzeichnis.AUSBLENDEN_AB_MONATE) {
            roh.sortedWith(compareByDescending<Pair<Einflussverzeichnis.Rohstoff, Rohstoffprofil.Treffer>> { it.first.konzentration ?: -1 }.thenBy { it.first.id }).forEach { (r, t) ->
                aus += Zeile(
                    Abschnitt.ROHSTOFFE, Zeilentyp.ROHSTOFF, if (t.quelle == Rohstoffprofil.Quelle.SEC) Art.GEMELDET else Art.ABGELEITET, i.rohstoffStand, r.id,
                    rohstoff = t, firmaMenge = i.firmenMengen[w.symbol.uppercase()]?.get(r.id), anlage = i.anlagen[w.symbol.uppercase()]?.get(r.id),
                    zahl = r.konzentration?.toDouble(), ganz = if (r.kritisch) 1 else 0,
                    liste = r.laender.take(3).map { it.land + ":" + it.prozent },
                    ziel = r.kurs?.let { k -> "instrument/$k" }, grau = alter >= Einflussverzeichnis.GRAU_AB_MONATE,
                    messung = messung(e, Empfindlichkeit.Faktor.entries.firstOrNull { it.symbol == r.kurs }),
                )
            }
        }

        // Einfluesse und Politik: Groessen, die den Wert treffen; auffaellige Staende zuerst
        val verweise = Einflussverzeichnis.fuerWert(i, w, heute).filter { it.groesse.typ != Typ.ROHSTOFF }
            .filter { Einflussverzeichnis.alterMonate(it.kante.stand, heute) < Einflussverzeichnis.AUSBLENDEN_AB_MONATE }
        val sortiert = verweise.sortedWith(compareBy({ !auffaellig(it.groesse, e) }, { it.groesse.typ.ordinal }, { it.groesse.id }))
        // [0.77] Eine Zeile, die weder einen Weg noch eine Richtung, einen Stand oder eine Messung hat, sagt nichts ueber diese Firma
        for (v in sortiert) aus += groessenZeile(i, v, e, heute).takeIf { it.weg != null || it.richtung != 0 || it.zahl != null || it.messung != null } ?: continue

        // Kursempfindlichkeit: belegte und wechselnde Groessen je eine Zeile (zuerst belegt), alle ohne Beleg zusammen in einer
        // Zeile. R^2 und Zeitraum stehen in zahl2 und ganz jeder Zeile; die Anzeige nennt sie einmal.
        val emp = e.empfindlichkeit
        if (emp != null) {
            val stand = emp.bis
            val jahre = Math.round(emp.jahre).toInt()
            val mitAussage = emp.koeffizienten.filter { it.urteil != Empfindlichkeit.Urteil.NICHT_BELEGT }
                .sortedWith(compareBy({ if (it.urteil == Empfindlichkeit.Urteil.BELEGT) 0 else 1 }, { if (it.faktor == Empfindlichkeit.Faktor.MARKT) 0 else 1 }))
            for (k in mitAussage) {
                val typ = if (k.faktor == Empfindlichkeit.Faktor.MARKT) Zeilentyp.EMPF_MARKT else Zeilentyp.EMPF_FAKTOR
                aus += Zeile(Abschnitt.EMPFINDLICHKEIT, typ, Art.GEMESSEN, stand, k.faktor.name, zahl = k.wert, zahl2 = emp.r2, ganz = jahre, aussage = Empfindlichkeit.aussage(k))
            }
            val ohne = emp.koeffizienten.filter { it.urteil == Empfindlichkeit.Urteil.NICHT_BELEGT }.map { it.faktor } + emp.ohneBeleg
            if (ohne.isNotEmpty()) aus += Zeile(Abschnitt.EMPFINDLICHKEIT, Zeilentyp.EMPF_OHNE, Art.GEMESSEN, stand, liste = ohne.map { it.name }.distinct(), zahl2 = emp.r2, ganz = jahre)
        } else if (e.empfindlichkeitZuKurz) {
            aus += Zeile(Abschnitt.EMPFINDLICHKEIT, Zeilentyp.EMPF_ZU_KURZ, null, null)
        }

        abgelaufenZeile(i, listOf(w), heute)?.let { aus += it }
        return aus
    }

    // [0.75] Abgelaufene Einordnungen als Luecke (siehe Einflussverzeichnis.abgelaufen)
    private fun abgelaufenZeile(i: Einflussverzeichnis.Index, werte: List<Wertbild>, heute: Long): Zeile? =
        Einflussverzeichnis.abgelaufen(i, werte, heute)?.let { (n, bis) ->
            Zeile(Abschnitt.LUECKEN, Zeilentyp.LUECKE_ABGELAUFEN, null, null, ganz = n, zahl = bis.toDouble())
        }

    // Kurzform fuer Rohstoffe am Terminmarkt (Kupfer, Gold, Oel ...): Foerderlaender und die Groessen, die den Kontrakt
    // treffen (Engpaesse, Spannungsgebiete, ENSO)
    fun rohstoffKarte(i: Einflussverzeichnis.Index, e: Eingabe, heute: Long): List<Zeile> {
        val aus = ArrayList<Zeile>()
        val r = Einflussverzeichnis.rohstoffFuerKurs(i, e.wert.symbol)
        if (r != null && Einflussverzeichnis.alterMonate(i.rohstoffStand, heute) < Einflussverzeichnis.AUSBLENDEN_AB_MONATE) {
            aus += Zeile(
                Abschnitt.ROHSTOFFE, Zeilentyp.ROHSTOFF, Art.ABGELEITET, i.rohstoffStand, r.id,
                zahl = r.konzentration?.toDouble(), ganz = if (r.kritisch) 1 else 0,
                liste = r.laender.take(5).map { it.land + ":" + it.prozent },
                grau = Einflussverzeichnis.alterMonate(i.rohstoffStand, heute) >= Einflussverzeichnis.GRAU_AB_MONATE,
            )
        }
        val verweise = Einflussverzeichnis.fuerWert(i, e.wert, heute).filter { it.groesse.typ != Typ.ROHSTOFF }
        for (v in verweise.sortedWith(compareBy({ !auffaellig(it.groesse, e) }, { it.groesse.typ.ordinal }, { it.groesse.id }))) aus += groessenZeile(i, v, e, heute)
        abgelaufenZeile(i, listOf(e.wert), heute)?.let { aus += it }
        return aus
    }

    // Karte eines ETFs ueber seine groessten Positionen: Groessen, die mindestens zwei Positionen treffen, nach dem Anteil
    // der getroffenen Positionen am ETF. positionen: Wertbild und Anteil (0,05 = 5 %) der erfassten Positionen.
    // Nur die erfassten Positionen zaehlen; die Luecke wird als Zeile genannt.
    fun etfKarte(i: Einflussverzeichnis.Index, positionen: List<Pair<Wertbild, Double>>, erfasst: Double, heute: Long): List<Zeile> {
        val je = LinkedHashMap<String, Pair<Einflussverzeichnis.Verweis, Double>>()
        val zahl = HashMap<String, Int>()
        for ((w, anteil) in positionen) {
            for (v in Einflussverzeichnis.fuerWert(i, w, heute)) {
                if (v.groesse.typ == Typ.ROHSTOFF) continue
                if (Einflussverzeichnis.alterMonate(v.kante.stand, heute) >= Einflussverzeichnis.AUSBLENDEN_AB_MONATE) continue
                val alt = je[v.groesse.id]
                je[v.groesse.id] = (alt?.first ?: v) to ((alt?.second ?: 0.0) + anteil)
                zahl[v.groesse.id] = (zahl[v.groesse.id] ?: 0) + 1
            }
        }
        val aus = ArrayList<Zeile>()
        for ((id, p) in je.entries.filter { (zahl[it.key] ?: 0) >= 2 }.sortedByDescending { it.value.second }.take(8)) {
            val g = p.first.groesse
            aus += Zeile(
                abschnitt(g.typ), Zeilentyp.GROESSE, p.first.kante.art, p.first.kante.stand, id,
                zahl = null, zahl2 = p.second, ganz = zahl[id],
                ziel = if (g.typ == Typ.ENGPASS || g.typ == Typ.SPANNUNG) "globus/engpaesse" else "wirkung/$id",
                grau = Einflussverzeichnis.alterMonate(p.first.kante.stand, heute) >= Einflussverzeichnis.GRAU_AB_MONATE,
            )
        }
        aus += Zeile(Abschnitt.LUECKEN, Zeilentyp.LUECKE_SEGMENTE, null, null, zahl = erfasst, ganz = 2)
        abgelaufenZeile(i, positionen.map { it.first }, heute)?.let { aus += it }
        return aus
    }

    // Wie viele Zeilen je Abschnitt und Typ, fuer die Zusammenfassung unter der Ueberschrift
    fun zaehlen(z: List<Zeile>, a: Abschnitt): Int = z.count { it.abschnitt == a }
}
