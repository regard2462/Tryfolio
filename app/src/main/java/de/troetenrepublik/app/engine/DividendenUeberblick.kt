package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.MarketApi
import java.time.Instant
import java.time.ZoneId
import kotlin.math.pow

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
        val jahreErhoeht: Int = 0,       // volle Jahre in Folge, in denen die Jahressumme gestiegen ist (0.76)
        val wachstum5: Double? = null,   // Wachstum pro Jahr ueber genau fuenf volle Jahre in % (0.76)
        val kauftag: Long? = null,       // letzter Handelstag vor dem naechsten Ex-Tag in ms (0.76)
    )

    // Volle Jahre in Folge mit hoeherer Jahressumme als im Jahr davor, vom juengsten volle Jahr rueckwaerts. Luecken
    // im Kalender oder gleiche Summen beenden die Folge. full: volle Jahre aufsteigend.
    fun jahreInFolge(full: List<Pair<Int, Double>>): Int {
        var n = 0
        for (i in full.indices.reversed()) {
            if (i == 0) break
            val (jahr, summe) = full[i]
            val (vorJahr, vorSumme) = full[i - 1]
            if (jahr == vorJahr + 1 && vorSumme > 0 && summe > vorSumme * 1.001) n++ else break
        }
        return n
    }

    // Erhoehung einer angekuendigten Dividende gegen die letzte davor in Prozent; null bei gleicher oder kleinerer
    // Zahlung (Schwelle 0,5 %) oder ohne frueheren Ex-Tag in der Historie.
    fun erhoehung(betrag: Double, exMs: Long, historie: List<MarketApi.DividendEvent>): Double? {
        val davor = historie.filter { it.exDate < exMs - 5 * 86_400_000L && it.amount > 0 }.maxByOrNull { it.exDate } ?: return null
        if (betrag <= davor.amount * 1.005) return null
        return (betrag / davor.amount - 1.0) * 100.0
    }

    // Wachstum pro Jahr der letzten fuenf Intervalle (sechs volle Jahre in Folge); sonst null
    fun wachstumFuenf(full: List<Pair<Int, Double>>): Double? {
        val w = full.takeLast(6)
        if (w.size < 6 || w.last().first - w.first().first != 5 || w.first().second <= 0 || w.last().second <= 0) return null
        return ((w.last().second / w.first().second).pow(1.0 / 5.0) - 1.0) * 100.0
    }

    fun werte(
        events: List<MarketApi.DividendEvent>, renditeProfil: Double, quoteProfil: Double, exTag: Long, zahltag: Long,
        kursEuro: Double, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault(),
        jeAktieProfilEuro: Double = 0.0, land: String = "US",
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
        val volle = d.byYear.filter { it.first < laufendesJahr }
        val naechster = exTag.takeIf { it > now - 86_400_000L }
        return Werte(
            rendite = rendite,
            jeAktieEuro = jeAktieEuro,
            wachstum = d.growth,
            quote = quoteProfil.takeIf { it > 0 },
            naechsterExTag = exTag.takeIf { it > now - 86_400_000L },
            naechsteZahlung = zahltag.takeIf { it > now - 86_400_000L },
            jahre = d.byYear.filter { it.first < laufendesJahr && it.second > 0 }.takeLast(8),
            jahreErhoeht = jahreInFolge(volle),
            wachstum5 = wachstumFuenf(volle),
            kauftag = naechster?.let { Alarmarten.letzterKauftag(it / 86_400_000L, land) * 86_400_000L + 43_200_000L },
        )
    }

    // Jaehrlicher Bruttoertrag eines Bestands in EUR, vor Steuer
    fun jahresErtrag(jeAktieEuro: Double, stueck: Double): Double = jeAktieEuro * stueck
}
