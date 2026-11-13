package de.troetenrepublik.app.ui

import androidx.compose.ui.graphics.Color
import de.troetenrepublik.app.engine.Netz
import de.troetenrepublik.app.engine.NetzAussagen
import de.troetenrepublik.app.i18n.NetzTexte
import java.time.LocalDate

// Darstellung der Verflechtungen: Namen, Betraege, Art und Farbe, Texte der Kernaussagen. Gemeinsam fuer die Karte auf der
// Wertpapierseite und die Vollbildansicht des Netzes.
internal object NetzAnzeige {

    fun deutsch(): Boolean = istDeutsch()

    fun name(g: Netz.Graph, id: String): String = g.knoten[id]?.anzeige(deutsch()) ?: id

    fun heute(): String = LocalDate.now().toString()

    // Datum JJJJ-MM-TT in der Form der App
    fun datum(s: String): String = runCatching { Fmt.date(LocalDate.parse(s.take(10)).toEpochDay() * 86_400_000L) }.getOrDefault(s)

    fun artName(a: Netz.Art): String = NetzTexte.t("art." + a.schluessel)

    fun gruppeName(g: Netz.Gruppe): String = NetzTexte.t("gruppe." + g.name)

    fun statusName(k: Netz.Kante): String = NetzTexte.t("status." + k.status.schluessel)

    // Rolle der Kante aus Sicht eines Knotens (Beteiligung oder Halter, Tochter oder Mutter ...)
    fun rolle(k: Netz.Kante, aus: String): String = NetzTexte.t("r." + k.art.schluessel + (if (k.von == aus) ".aus" else ".ein"))

    // Farben je Gruppe: unterscheidbar und dezent
    fun farbe(g: Netz.Gruppe): Color = when (g) {
        Netz.Gruppe.LIEFERUNG -> Color(0xFF5B95FF)
        Netz.Gruppe.BETEILIGUNG -> Color(0xFF3FB68B)
        Netz.Gruppe.FINANZIERUNG -> Color(0xFFE5A53A)
        Netz.Gruppe.WETTBEWERB -> Color(0xFFD9648A)
        Netz.Gruppe.MANDAT -> Color(0xFF9C7BE0)
        Netz.Gruppe.STAATSGELD -> Color(0xFF3EB6C9)
    }

    fun farbe(a: Netz.Art): Color = farbe(a.gruppe)

    // Betraege ohne unnoetige Nachkommastellen: "30 Mrd. $", "2,5 Mrd. $" statt "30,00" und "2,50"
    private val NULLEN_ENDE = Regex("(?<=\\d)[.,]00(?!\\d)")
    private val NULL_ENDE = Regex("(?<=\\d[.,]\\d)0(?!\\d)")
    internal fun kurz(s: String): String = s.replace(NULLEN_ENDE, "").replace(NULL_ENDE, "")
    private fun usd(v: Double): String = kurz(Fmt.dollarKompakt(v))
    private fun eur(v: Double): String = kurz(Fmt.compactEuro(v))

    private fun zahl(w: Double): String = Fmt.num(w, if (w % 1.0 == 0.0) 0 else if (w < 10) 2 else 1)

    // Kurzer Betrag einer Kante ("30 Mrd. USD", "10 GW", "27,4 % der Stimmen"); null ohne Umfang. "bis" bei Hoechstzusagen.
    fun umfangText(u: Netz.Umfang?): String? {
        u ?: return null
        val roh = when (u.einheit) {
            Netz.Einheit.USD -> usd(u.wert)
            Netz.Einheit.EUR -> eur(u.wert)
            Netz.Einheit.GW -> NetzTexte.t("u.gw", zahl(u.wert))
            Netz.Einheit.AKTIEN -> NetzTexte.t("u.aktien", Fmt.compact(u.wert))
            Netz.Einheit.PCT -> {
                val p = Fmt.pct(u.wert, if (u.wert >= 10 && u.wert % 1.0 == 0.0) 0 else 1)
                if (u.bezug != null) NetzTexte.t("bezug." + u.bezug.name, p) else p
            }
        }
        return if (u.hoechstens) NetzTexte.t("bis", roh) else roh
    }

    // Text einer Kante im Netz: Art und Betrag
    fun kantenText(k: Netz.Kante): String = listOfNotNull(artName(k.art), umfangText(k.umfang)).joinToString(" ")

    fun hinweis(k: Netz.Kante): String? = (if (deutsch()) k.hinweis else k.hinweisEn ?: k.hinweis)

    fun programm(k: Netz.Kante): String? = (if (deutsch()) k.programm else k.programmEn ?: k.programm)

    fun teil(key: String): String = NetzTexte.t("teil.$key").ifEmpty { key }

    // Aufzaehlung: bis zu drei Namen, sonst "N Firmen"
    fun namen(g: Netz.Graph, ids: List<String>): String {
        val n = ids.map { name(g, it) }
        return when {
            n.size <= 1 -> n.joinToString("")
            n.size <= 3 -> NetzTexte.t("liste.und", n.dropLast(1).joinToString(", "), n.last())
            else -> NetzTexte.zahl("firmen", n.size)
        }
    }

    private fun undWort(): String = if (deutsch()) "und" else "and"

    private fun betrag(usd: Double, eur: Double): String = listOfNotNull(
        if (usd > 0) usd(usd) else null, if (eur > 0) eur(eur) else null,
    ).joinToString(" " + undWort() + " ")

    // Satz zu einer Kernaussage in der App-Sprache
    fun aussage(g: Netz.Graph, a: NetzAussagen.Aussage): String {
        return when (a.typ) {
            NetzAussagen.Typ.KREISLAUF -> {
                val k = a.kreis
                val kapital = k?.kapitalSchritte?.firstOrNull()
                val arten = kapital?.map { artName(it.art) }?.distinct()?.joinToString(" " + undWort() + " ").orEmpty()
                val an = kapital?.firstOrNull()?.empfaenger()?.let { name(g, it) }.orEmpty()
                val betragKapital = if (a.zahl > 0) " (" + usd(a.zahl) + ")" else ""
                val betragUmsatz = when {
                    a.zahl2 > 0 -> " (" + usd(a.zahl2) + ")"
                    a.zahl3 > 0 -> " (" + NetzTexte.t("u.gw", zahl(a.zahl3)) + ")"
                    else -> ""
                }
                val namenKreis = a.knoten.joinToString(", ") { name(g, it) }
                if (a.bedingt) NetzTexte.t("a.kreis.bedingt", namenKreis, arten, betragKapital, an, betragUmsatz)
                else NetzTexte.t(
                    "a.kreis", namenKreis, arten, betragKapital, an, betragUmsatz,
                    if (a.zahl4 > 0) NetzTexte.t("a.kreis.bedingtanteil", usd(a.zahl4)) else "",
                )
            }
            NetzAussagen.Typ.KONZENTRATION ->
                if (a.n <= 1 || a.zahl2 <= a.zahl) NetzTexte.t("a.konz1", name(g, a.knoten[0]), Fmt.pct(a.zahl, 0), name(g, a.knoten.getOrElse(1) { "" }))
                else NetzTexte.t("a.konz", name(g, a.knoten[0]), Fmt.pct(a.zahl, 0), name(g, a.knoten.getOrElse(1) { "" }), Fmt.pct(a.zahl2, 0))
            NetzAussagen.Typ.LIEFERANT -> NetzTexte.t("a.lief", name(g, a.knoten[0]), teil(a.teil.orEmpty()), namen(g, a.knoten.drop(1)))
            NetzAussagen.Typ.GEGENSEITIG -> NetzTexte.t(if (a.zahl == 0.0) "a.gegen.handel" else "a.gegen.anteil", name(g, a.knoten[0]), name(g, a.knoten[1]))
            NetzAussagen.Typ.WETTBEWERB -> NetzTexte.t("a.wett", a.zahl.toInt().toString(), a.zahl2.toInt().toString())
            NetzAussagen.Typ.MANDATE -> NetzTexte.t("a.mandat", a.n.toString(), a.zahl.toInt().toString())
            NetzAussagen.Typ.STAATSGELD -> NetzTexte.t(
                "a.staat", betrag(a.zahl, a.zahl3), betrag(a.zahl2, a.zahl4).ifEmpty { NetzTexte.t("nichts") },
            )
            NetzAussagen.Typ.BEDINGT ->
                if (a.zahl2 <= 0.0) NetzTexte.t("a.bedingt.nur.opt", a.n.toString())
                else if (a.n > 0) NetzTexte.t("a.bedingt.opt", a.zahl.toInt().toString(), a.zahl2.toInt().toString(), a.n.toString())
                else NetzTexte.t("a.bedingt", a.zahl.toInt().toString(), a.zahl2.toInt().toString())
            NetzAussagen.Typ.KLUMPEN -> NetzTexte.t("a.klumpen", name(g, a.knoten[0]), a.n.toString(), a.zahl.toInt().toString())
            NetzAussagen.Typ.KONTROLLE -> NetzTexte.t("a.kontrolle.mehrheit", name(g, a.knoten[0]), Fmt.pct(a.zahl, 1), name(g, a.knoten[1]))
        }
    }
}
