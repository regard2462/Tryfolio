package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable

// [Laender Strukturen 0.70]
// Strukturelle Probleme je Land und die Gegenprogramme, gebuendelt in assets/globus/strukturen.json (de und en im selben Eintrag):
// {"v":1,"l":{"CHN":[{"t":{"de":..,"en":..},"d":{..},"p":[{"n":{..},"j":2021,"t":{..}}],"q":[{"n":{..},"u":"https://.."}]}]}}. Schluessel ISO alpha-3.
object LaenderStrukturen {
    const val DATEI = "globus/strukturen.json"

    @Serializable
    class Text(val de: String = "", val en: String = "") {
        fun je(deutsch: Boolean) = if (deutsch) de else en
    }

    // j = Startjahr (0 = unbekannt)
    @Serializable
    class Programm(val n: Text, val j: Int = 0, val t: Text)

    @Serializable
    class Quelle(val n: Text, val u: String)

    @Serializable
    class Problem(val t: Text, val d: Text, val p: List<Programm> = emptyList(), val q: List<Quelle> = emptyList())

    @Serializable
    class Stand(val v: Int = 1, val l: Map<String, List<Problem>> = emptyMap())

    fun lesen(text: String): Stand? = Laenderrisiken.lesen(Stand.serializer(), text)

    fun fuer(s: Stand, land: GlobusLand): List<Problem> = s.l[land.a3].orEmpty()

    // Alterung der Texte nach dem Stand der Datei (Datenbuendel): ab zwoelf Monaten mit Stand am Ende, ab 36 Monaten klar als
    // veraltet gekennzeichnet; die Aussagen bleiben als Hintergrund stehen. Ohne lesbaren Stand unveraendert.
    fun gealtert(s: Stand, stand: String, jetzt: Long): Stand {
        val marke = Alter.marke(stand)
        if (marke.isEmpty()) return s
        val z = Alter.zustand(stand, Alter.STRUKTUREN, jetzt)
        if (z == Alter.Zustand.FRISCH) return s
        fun neu(t: Text): Text =
            if (z == Alter.Zustand.VERALTET) Text("Veraltet (Stand $marke): " + t.de, "Outdated (as of $marke): " + t.en)
            else Text(t.de + " (Stand $marke)", t.en + " (as of $marke)")
        return Stand(s.v, s.l.mapValues { (_, l) -> l.map { p -> Problem(p.t, neu(p.d), p.p, p.q) } })
    }
}
