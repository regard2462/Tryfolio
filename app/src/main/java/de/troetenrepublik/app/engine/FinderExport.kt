package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.FinderVorlagen.Feld

// CSV der Treffer im Aktienfinder [0.76]: eine Zeile je Wert mit den Kennzahlen der Liste und den Feldern der
// gewaehlten Grenzen. Deutsch mit Semikolon und Dezimalkomma, sonst Komma und Punkt wie die uebrigen Exporte (0.77),
// Anfuehrungszeichen nach RFC 4180.
object FinderExport {

    // Immer dabei, in dieser Reihenfolge; die Felder der Grenzen kommen dahinter.
    val GRUNDFELDER = listOf(Feld.BOERSENWERT, Feld.KGV, Feld.KGV_ERWARTET, Feld.KBV, Feld.DIVIDENDE, Feld.ROE, Feld.VERSCHULDUNG, Feld.LEER)

    fun felder(grenzen: List<FinderVorlagen.Grenze>): List<Feld> = (GRUNDFELDER + grenzen.map { it.feld }).distinct()

    // Nur Werte, die alle Grenzen sicher erfuellen; ohne Grenzen alle.
    fun treffer(liste: List<Aktienfinder.Ergebnis>, grenzen: List<FinderVorlagen.Grenze>, jetzt: Long): List<Aktienfinder.Ergebnis> =
        if (grenzen.isEmpty()) liste else liste.filter { FinderVorlagen.pruefen(it.daten, grenzen, jetzt) == FinderVorlagen.Pruefung.JA }

    // [0.78] Wie oben mit dem Pruefergebnis der Auswahl: Regeln ueber den ganzen Markt (FinderRaster) lassen sich an
    // einem einzelnen Wert nicht nachpruefen. Ohne Pruefergebnis alle.
    fun treffer(liste: List<Aktienfinder.Ergebnis>, pruefung: Map<String, FinderVorlagen.Pruefung>): List<Aktienfinder.Ergebnis> =
        if (pruefung.isEmpty()) liste else liste.filter { pruefung[it.symbol] == FinderVorlagen.Pruefung.JA }

    private fun zelle(t: String, trenner: Char): String =
        if (t.any { it == trenner || it == '"' || it == '\n' || it == '\r' }) "\"" + t.replace("\"", "\"\"") + "\"" else t

    private fun zahl(v: Double?, deutsch: Boolean): String {
        if (v == null || !v.isFinite()) return ""
        val t = if (kotlin.math.abs(v) >= 1000) kotlin.math.round(v).toLong().toString() else (kotlin.math.round(v * 100) / 100.0).toString()
        return t.removeSuffix(".0").let { if (deutsch) it.replace('.', ',') else it }
    }

    // kopf: Spaltennamen der festen Spalten (Symbol, Name, Land, Branche, Punktzahl) und je Feld seine Bezeichnung.
    // branche: Anzeigename der Branche zum Schluessel.
    fun csv(
        liste: List<Aktienfinder.Ergebnis>, felder: List<Feld>, kopf: List<String>, jetzt: Long = System.currentTimeMillis(),
        deutsch: Boolean = false, branche: (String) -> String = { it }, feldName: (Feld) -> String,
    ): String {
        val t = if (deutsch) ';' else ','
        val b = StringBuilder()
        b.append((kopf + felder.map(feldName)).joinToString(t.toString()) { zelle(it, t) }).append("\r\n")
        for (e in liste) {
            val d = e.daten
            val zeile = listOf(d.symbol, d.name, d.land, branche(d.branche), zahl(e.gesamt, deutsch)) + felder.map { zahl(FinderVorlagen.wert(d, it, jetzt), deutsch) }
            b.append(zeile.joinToString(t.toString()) { zelle(it, t) }).append("\r\n")
        }
        return b.toString()
    }
}
