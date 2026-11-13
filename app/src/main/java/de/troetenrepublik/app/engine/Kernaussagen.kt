package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Abhaengigkeiten.Abschnitt
import de.troetenrepublik.app.engine.Abhaengigkeiten.Zeile
import de.troetenrepublik.app.engine.Abhaengigkeiten.Zeilentyp
import de.troetenrepublik.app.market.SecBericht

// [0.76] Zwei bis vier Kernaussagen zu einer Aktie, abgeleitet aus den Zeilen der Karte "Abhaengigkeiten und Risiken" und
// der Gliederung des Umsatzes (Segmente, Regionen, Grosskunden, gleiche Datenform aus jeder Quelle). Eigene Angaben der
// Firma und eigene Messungen kommen vor den Ableitungen aus Branche und Land. Was nicht belegt ist, entfaellt; erfunden
// wird nichts. Die Anzeige setzt die Texte; hier stehen nur Art und Zahlen.
object Kernaussagen {
    enum class Typ { SEGMENT, KURS, REGION, FOERDERT, ROHSTOFFE, KRITISCH, LAND, AUFFAELLIG, EINFLUESSE, KUNDE }

    // Landrisiko in Stufen; die Punkte laufen von 0 (schlecht) bis 100 (gut)
    enum class Stufe { NIEDRIG, MITTEL, HOCH }

    // schluessel: Rohstoff, Faktor oder Einflussgroesse; zahl: Anteil in Prozent bzw. Steigung
    class Posten(val schluessel: String, val zahl: Double? = null)

    // posten: Eintrag der Gliederung (Segment, Region, Kunde); liste: Rohstoffe, Faktoren oder Groessen; land: Foerderland
    // (alpha-2) bzw. Land der Aktie; sitz: Sitzland (alpha-2) oder leer; zahl: Anteil (0 bis 1) oder Anzahl
    class Aussage(
        val typ: Typ,
        val posten: SecBericht.Posten? = null,
        val liste: List<Posten> = emptyList(),
        val land: String = "",
        val sitz: String = "",
        val stufe: Stufe? = null,
        val zahl: Double = 0.0,
        // [0.76] Rohstoffaussagen: true, wenn sie aus Angaben zum Unternehmen stammen (nicht nur aus Industrie oder Branche)
        val firma: Boolean = false,
    )

    const val MAX_AUSSAGEN = 4

    // So viele selbst gesuchte oder gefoerderte Rohstoffe nennt die Aussage
    const val MAX_EIGENE = 4

    // Ab diesem Anteil an der Foerderung eines Landes hat die Branche eine Abhaengigkeit von diesem Land (Prozent)
    const val ROHSTOFF_ANTEIL = 50

    // Eine Region ab diesem Anteil am Umsatz ist eine Aussage wert, ein Grosskunde ab 20 Prozent (Anteile 0 bis 1)
    const val REGION_ANTEIL = 0.5
    const val KUNDE_ANTEIL = 0.2

    // Ab so vielen kritischen Rohstoffen der Branche steht die Zahl als Aussage
    const val MIND_KRITISCH = 2

    fun stufe(punkte: Double): Stufe = when {
        punkte >= 70.0 -> Stufe.NIEDRIG
        punkte >= 50.0 -> Stufe.MITTEL
        else -> Stufe.HOCH
    }

    fun ableiten(i: Einflussverzeichnis.Index, zeilen: List<Zeile>, gliederung: SecBericht.Auswertung?): List<Aussage> {
        val aus = ArrayList<Aussage>()

        // Eigene Angaben der Firma: groesster Posten der Gliederung
        val segment = gliederung?.takeIf { it.segmenteStatus == 0 }?.segmente?.maxByOrNull { it.w }
        if (segment != null) aus += Aussage(Typ.SEGMENT, posten = segment, zahl = segment.w)

        // Eigene Messung: belegte Zusammenhaenge des Kurses ausser dem Markt, die staerksten zuerst
        val belegt = zeilen.filter {
            it.typ == Zeilentyp.EMPF_FAKTOR && (it.aussage == Empfindlichkeit.Aussage.LAEUFT_MIT || it.aussage == Empfindlichkeit.Aussage.LAEUFT_GEGEN)
        }.sortedByDescending { kotlin.math.abs(it.zahl ?: 0.0) }.take(2)
        if (belegt.isNotEmpty()) aus += Aussage(Typ.KURS, liste = belegt.map { Posten(it.schluessel, it.zahl) })

        val region = gliederung?.takeIf { it.regionenStatus == 0 }?.regionen?.maxByOrNull { it.w }
        if (region != null && region.w >= REGION_ANTEIL) aus += Aussage(Typ.REGION, posten = region, zahl = region.w)

        // Ableitung aus der Branche: Rohstoffe, die ein einziges Land dominiert
        // [0.77] Eigene Erzeugnisse der Firma sind keine Abhaengigkeit von einem Foerderland, blosse Nennungen in der
        // Beschreibung auch nicht. zahl = 1: alle genannten sucht oder foerdert die Firma (Bergbau), 2: sie haelt nur Lizenzen und Abnahmerechte, sonst 0.
        val alleRohstoffe = zeilen.filter { it.typ == Zeilentyp.ROHSTOFF }
        val eigene = alleRohstoffe.filter { it.rohstoff?.selbst == true }.take(MAX_EIGENE)
        if (eigene.isNotEmpty()) {
            val bergbau = eigene.all { it.rohstoff?.verwendung == Rohstoffprofil.FOERDERUNG || it.rohstoff?.verwendung == Rohstoffprofil.ERKUNDUNG }
            val lizenz = eigene.all { it.rohstoff?.verwendung == Rohstoffprofil.LIZENZ }
            aus += Aussage(Typ.FOERDERT, liste = eigene.map { Posten(it.schluessel) }, firma = true, zahl = if (lizenz) 2.0 else if (bergbau) 1.0 else 0.0)
        }
        val rohstoffe = alleRohstoffe.filter { it.rohstoff == null || it.rohstoff.einsatz }
        val firmenIds = rohstoffe.filter { it.rohstoff?.firmenbezogen == true }.map { it.schluessel }.toSet()
        val nachLand = LinkedHashMap<String, MutableList<Posten>>()
        for (r in rohstoffe) {
            val (land, prozent) = r.liste.firstOrNull()?.split(':')?.let { it[0] to (it.getOrNull(1)?.toIntOrNull() ?: 0) } ?: continue
            if (prozent >= ROHSTOFF_ANTEIL) nachLand.getOrPut(land) { ArrayList() } += Posten(r.schluessel, prozent.toDouble())
        }
        val fuehrend = nachLand.entries.maxWithOrNull(compareBy({ it.value.size }, { it.value.maxOf { p -> p.zahl ?: 0.0 } }))
        if (fuehrend != null) {
            val liste = fuehrend.value.sortedByDescending { it.zahl }.take(3)
            aus += Aussage(Typ.ROHSTOFFE, liste = liste, land = fuehrend.key, firma = liste.any { it.schluessel in firmenIds })
        } else {
            val kritisch = rohstoffe.count { it.ganz == 1 }
            if (kritisch >= MIND_KRITISCH) aus += Aussage(Typ.KRITISCH, zahl = kritisch.toDouble(), firma = rohstoffe.any { it.ganz == 1 && it.schluessel in firmenIds })
        }

        // Land: Landrisiko in Stufen, mit Sitz wenn bekannt
        val risiko = zeilen.firstOrNull { it.typ == Zeilentyp.LANDRISIKO }
        if (risiko != null) {
            val sitz = zeilen.firstOrNull { it.typ == Zeilentyp.SITZ }?.schluessel.orEmpty()
            aus += Aussage(Typ.LAND, land = risiko.schluessel, sitz = sitz, stufe = stufe(risiko.zahl ?: 0.0), zahl = risiko.zahl ?: 0.0)
        }

        // Einfluesse: auffaellige Staende, sonst die ersten Groessen der Branche
        val einfluesse = zeilen.filter { it.typ == Zeilentyp.GROESSE && it.abschnitt == Abschnitt.EINFLUESSE && i.groessen.containsKey(it.schluessel) }
        val auffaellig = einfluesse.filter { it.auffaellig }.take(2)
        if (auffaellig.isNotEmpty()) aus += Aussage(Typ.AUFFAELLIG, liste = auffaellig.map { Posten(it.schluessel) })
        else if (einfluesse.isNotEmpty()) aus += Aussage(Typ.EINFLUESSE, liste = einfluesse.take(3).map { Posten(it.schluessel) })

        // Grosskunde nur bei hohem Anteil
        val kunde = gliederung?.kunden?.filter { it.k == 1 }?.maxByOrNull { it.w }
        if (kunde != null && kunde.w >= KUNDE_ANTEIL) aus += Aussage(Typ.KUNDE, posten = kunde, zahl = kunde.w)

        return aus.take(MAX_AUSSAGEN)
    }
}
