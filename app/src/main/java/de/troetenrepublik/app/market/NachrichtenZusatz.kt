package de.troetenrepublik.app.market

// [Nachrichten 0.77] Zusaetzliche Nachrichtenquellen. Die Listen sind leer und lassen sich fuellen,
// ohne Rss oder den Lader anzufassen. QUELLEN stehen hinter den eingebauten Quellen (gleicher Aufbau wie
// Rss.eingebaut). STANDARD_AUS: Schluessel, die ausgeschaltet beginnen. NUR_ANSICHT: Schluessel, die nur in der Liste
// erscheinen (kein Abruf im Hintergrund, nicht in Widgets, Mitteilungen und Rueckblick). KEIN_LESEMODUS: Rechner, deren
// Artikel nur als Schlagzeile mit Link erscheinen. OHNE_LESEMODUS: Rechner, deren Originalseite zuerst laedt und deren
// Text nur auf Antippen gelesen wird.
object NachrichtenZusatz {
    val QUELLEN: List<Rss.Quelle> = emptyList()
    val STANDARD_AUS: Set<String> = emptySet()
    val NUR_ANSICHT: Set<String> = emptySet()
    val KEIN_LESEMODUS: List<String> = emptyList()
    val OHNE_LESEMODUS: List<String> = emptyList()
}
