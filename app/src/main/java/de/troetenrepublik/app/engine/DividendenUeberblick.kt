package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.MarketApi
import java.time.Instant
import java.time.ZoneId

// Kompakte Dividendenangaben fuer Finanzen (Aktien) und Kennzahlen (ETFs) (5.97)
object DividendenUeberblick {
    data class Werte(
        val rendite: Double?,            // Dividendenrendite in %
        val jeAktieEuro: Double?,        // Summe der Ex-Tage der letzten 12 Monate je Aktie in EUR
        val wachstum: Double?,           // Wachstum pro Jahr in % (aus Analysis.dividends(...).growth)
        val quote: Double?,              // Ausschuettungsquote in %
        val naechsterExTag: Long?,       // ms
        val naechsteZahlung: Long?,      // ms
        val jahre: List<Pair<Int, Double>>, // volle Kalenderjahre (ohne das laufende), hoechstens die letzten 8, aufsteigend, EUR je Aktie
    )

    fun werte(
        events: List<MarketApi.DividendEvent>, renditeProfil: Double, quoteProfil: Double, exTag: Long, zahltag: Long,
        kursEuro: Double, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault(),
        jeAktieProfilEuro: Double = 0.0,
    ): Werte? {
        val d = Analysis.dividends(events, now, zone)
        // Ohne Zahlungen in der Historie die jaehrliche Dividende laut Profil (in EUR umgerechnet)
        val jeAktieEuro = d.trailing.takeIf { it > 0 } ?: jeAktieProfilEuro.takeIf { it > 0 }
        val rendite = when {
            renditeProfil > 0 -> renditeProfil
            jeAktieEuro != null && kursEuro > 0 -> jeAktieEuro / kursEuro * 100.0
            else -> null
        }
        // Ohne Zahlung und ohne Rendite im Profil gibt es keinen Abschnitt
        if (jeAktieEuro == null && rendite == null) return null
        val laufendesJahr = Instant.ofEpochMilli(now).atZone(zone).year
        return Werte(
            rendite = rendite,
            jeAktieEuro = jeAktieEuro,
            wachstum = d.growth,
            quote = quoteProfil.takeIf { it > 0 },
            naechsterExTag = exTag.takeIf { it > now - 86_400_000L },
            naechsteZahlung = zahltag.takeIf { it > now - 86_400_000L },
            jahre = d.byYear.filter { it.first < laufendesJahr && it.second > 0 }.takeLast(8),
        )
    }

    // Jaehrlicher Bruttoertrag eines Bestands in EUR, vor Steuer
    fun jahresErtrag(jeAktieEuro: Double, stueck: Double): Double = jeAktieEuro * stueck
}
