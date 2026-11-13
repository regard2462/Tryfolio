package de.troetenrepublik.app.ui

import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.StandRegel

// Zeitangabe eines gespeicherten Stands: „Stand 14:05“ am selben Tag, sonst „Stand 09.11. 14:05“.
fun standText(abgerufen: Long, jetzt: Long = System.currentTimeMillis()): String =
    S.widgetStand(StandRegel.beschriftung(abgerufen, jetzt, { Fmt.time(it) }, { Fmt.dayMonthNum(it) + " " + Fmt.time(it) }, { Fmt.date(it) }))
