package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.PriceAlert

// Glocke auf eine Regel des Strategietests (0.78): meldet, wenn die Regel am letzten Tagesschluss von „nicht
// investiert“ auf „investiert“ wechselt (Kaufsignal) oder umgekehrt (Verkaufssignal). Die Lage kommt aus
// StrategieLabor.ziel, hier wird nichts nachgerechnet. Die Art eines solchen Alarms traegt die Regel in sich
// (REGEL:Art:a:b:n); so gehen neue Regeln des Tests ohne weitere Aenderung mit.
object RegelGlocke {
    const val VORSATZ = "REGEL:"
    const val TAG_KAUF = "ALERT_RULE_BUY"
    const val TAG_VERKAUF = "ALERT_RULE_SELL"

    // Wie im Strategietest: darunter gilt die Reihe als zu kurz.
    const val MIN_KERZEN = 300

    private const val TAG_MS = 86_400_000L

    fun istArt(art: String): Boolean = art.startsWith(VORSATZ)

    fun art(r: StrategieLabor.Regel): String = VORSATZ + r.art.name + ":" + r.a + ":" + r.b + ":" + r.n

    // Bestandteile einer Art fuer die Anzeige des Regelnamens; null, wenn sie keine Regel-Glocke ist.
    data class Teile(val art: String, val a: Int, val b: Int, val n: Int)

    fun teile(art: String): Teile? {
        if (!istArt(art)) return null
        val t = art.removePrefix(VORSATZ).split(':')
        if (t.size != 4 || t[0].isEmpty()) return null
        return Teile(t[0], t[1].toIntOrNull() ?: return null, t[2].toIntOrNull() ?: return null, t[3].toIntOrNull() ?: return null)
    }

    // Die Regel des Tests zu einer Art; null, wenn es sie nicht (mehr) gibt.
    fun regel(art: String, regeln: List<StrategieLabor.Regel> = StrategieLabor.REGELN): StrategieLabor.Regel? =
        if (istArt(art)) regeln.firstOrNull { art(it) == art } else null

    // Ob sich auf die Regel eine Glocke setzen laesst: Kaufen und Halten und die Zielvolatilitaet wechseln nie die Lage.
    fun moeglich(r: StrategieLabor.Regel): Boolean = !StrategieLabor.ohneSignal(r)

    // Lage der Regel nach dem letzten Schluss der Reihe. tag: Tag dieses Schlusses (Tage seit 1970).
    data class Lage(val investiert: Boolean, val tag: Long)

    // kerzen: abgeschlossene Tageskerzen, aufsteigend. null bei zu kurzer Reihe.
    fun lage(kerzen: List<Candle>, r: StrategieLabor.Regel): Lage? {
        if (kerzen.size < MIN_KERZEN) return null
        val z = StrategieLabor.ziel(StrategieLabor.Daten(kerzen), r)
        if (z.isEmpty()) return null
        return Lage(z.last(), Math.floorDiv(kerzen.last().t, TAG_MS))
    }

    // zustand: 1 investiert, -1 nicht, 0 noch nicht bestimmt (die erste Pruefung merkt nur die Lage). merk: Tag des
    // zuletzt ausgewerteten Schlusses, damit derselbe Schluss nicht zweimal meldet. gemeldetAm: Handelstag der letzten
    // Pruefung; der Aufrufer laedt die Kerzen je Handelstag nur einmal.
    fun pruefen(a: PriceAlert, lage: Lage?, heute: Long): Alarmarten.Ergebnis {
        if (lage == null) return Alarmarten.Ergebnis(a, false)
        val seite = if (lage.investiert) 1 else -1
        val bisher = a.merk.toLongOrNull() ?: Long.MIN_VALUE
        val geprueft = a.copy(gemeldetAm = heute)
        if (a.zustand != 0 && lage.tag <= bisher) return Alarmarten.Ergebnis(geprueft, false)
        val neu = geprueft.copy(zustand = seite, merk = lage.tag.toString())
        if (a.zustand == 0 || a.zustand == seite) return Alarmarten.Ergebnis(neu, false)
        return Alarmarten.Ergebnis(neu, true, 0.0, if (seite > 0) TAG_KAUF else TAG_VERKAUF, a.art)
    }
}
