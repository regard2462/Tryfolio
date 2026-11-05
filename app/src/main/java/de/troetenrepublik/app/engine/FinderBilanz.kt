package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Bilanzqualitaet fuer den Aktienfinder (5.92): Jahresabschluesse aller Werte eines Markts, auf dem Geraet 30 Tage
// gespeichert, daraus Piotroski-F-Score, Altman-Z-Zone und Bilanzampel wie auf der Wertpapierseite. Reine Rechnung,
// Abruf und Datei stehen in AktienfinderAbruf.kt.
object FinderBilanz {

    // Jahresabschluesse aendern sich einmal im Jahr: 30 Tage gueltig. Fuehrt Yahoo keine, wird nach 7 Tagen neu gefragt.
    const val GUELTIG_MS = 30 * 86_400_000L
    const val LEER_MS = 7 * 86_400_000L

    // Fuer Piotroski reichen die letzten drei Jahre (Bilanzsumme zu Beginn des Vorjahres), Altman braucht nur das letzte.
    const val JAHRE = 3

    // Ein gespeicherter Abruf: Zeitpunkt und Jahre (leer, wenn Yahoo keine fuehrt).
    @Serializable
    data class Eintrag(val t: Long, val j: List<Qualitaet.Jahr> = emptyList())

    @Serializable
    data class Datei(val eintraege: Map<String, Eintrag> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // Gueltig, solange juenger als 30 Tage (ohne Jahre 7 Tage); ein Zeitpunkt in der Zukunft (verstellte Uhr) gilt nicht.
    fun gueltig(e: Eintrag, jetzt: Long): Boolean {
        val alter = jetzt - e.t
        return alter >= 0 && alter < if (e.j.isEmpty()) LEER_MS else GUELTIG_MS
    }

    fun eintrag(jahre: List<Qualitaet.Jahr>, jetzt: Long): Eintrag = Eintrag(jetzt, jahre.takeLast(JAHRE))

    fun lesen(text: String): Map<String, Eintrag> =
        runCatching { json.decodeFromString(Datei.serializer(), text).eintraege }.getOrDefault(emptyMap())

    // Nur gueltige Eintraege kommen in die Datei, damit sie nicht ueber die Zeit waechst.
    fun schreiben(eintraege: Map<String, Eintrag>, jetzt: Long): String =
        json.encodeToString(Datei.serializer(), Datei(eintraege.filterValues { gueltig(it, jetzt) }))

    // Ergebnis je Wert: F-Score auf neun Pruefungen hochgerechnet (null mit weniger als sechs pruefbaren), Altman-Zone
    // und die Bilanzampel mit ihren Gruenden.
    data class Werte(val fScore: Double?, val zone: Qualitaet.Zone?, val bilanz: Bewertungsblick.Bilanz?)

    // boersenwert in der Berichtswaehrung; ohne ihn fehlt bei Herstellern nur Altman. fassung null: ohne Altman.
    fun werte(jahre: List<Qualitaet.Jahr>, boersenwert: Double?, fassung: Qualitaet.Fassung? = Qualitaet.Fassung.HERSTELLER): Werte? {
        if (jahre.isEmpty()) return null
        val f = Qualitaet.piotroski(jahre)
        val fScore = f?.takeIf { Qualitaet.stufe(it) != null }?.let { it.score * 9.0 / it.geprueft }
        val z = fassung?.let { Qualitaet.altman(jahre.last(), boersenwert, it) }
        val bilanz = Bewertungsblick.bilanz(f, z)
        if (fScore == null && z == null) return null
        return Werte(fScore, z?.zone, bilanz)
    }
}
