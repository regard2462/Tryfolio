package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Watchliste

// Mehrere benannte Watchlists (seit 0.76). Die erste Liste liegt im Konto unter ersteListe (der alte Speicherplatz
// der einen Watchlist), alle weiteren unter weitereListen. Account.watchlist ist die Vereinigung aller Listen.
object Watchlisten {

    const val MAX_NAME = 30

    private fun gueltig(a: Account, i: Int): Boolean = i in 0 until 1 + a.weitereListen.size

    // Die Liste i mit neuen Symbolen.
    fun setze(a: Account, i: Int, symbole: List<String>): Account = when {
        !gueltig(a, i) -> a
        i == 0 -> a.copy(ersteListe = symbole)
        else -> a.copy(weitereListen = a.weitereListen.mapIndexed { k, l -> if (k == i - 1) l.copy(symbole = symbole) else l })
    }

    // Indizes der Listen, auf denen das Symbol steht.
    fun listenMit(a: Account, symbol: String): Set<Int> =
        a.watchlisten.mapIndexedNotNull { i, l -> if (symbol in l.symbole) i else null }.toSet()

    // Neue leere Liste am Ende; ohne Namen keine.
    fun neu(a: Account, name: String): Account {
        val n = name.trim().take(MAX_NAME)
        if (n.isEmpty()) return a
        return a.copy(weitereListen = a.weitereListen + Watchliste(n))
    }

    fun umbenennen(a: Account, i: Int, name: String): Account {
        val n = name.trim().take(MAX_NAME)
        if (n.isEmpty() || !gueltig(a, i)) return a
        return if (i == 0) a.copy(ersteListeName = n)
        else a.copy(weitereListen = a.weitereListen.mapIndexed { k, l -> if (k == i - 1) l.copy(name = n) else l })
    }

    // Die letzte Liste bleibt immer. Wird die erste geloescht, rueckt die naechste an ihren Platz.
    fun loeschen(a: Account, i: Int): Account {
        if (a.weitereListen.isEmpty() || !gueltig(a, i)) return a
        if (i == 0) {
            val n = a.weitereListen.first()
            return a.copy(ersteListe = n.symbole, ersteListeName = n.name, weitereListen = a.weitereListen.drop(1))
        }
        return a.copy(weitereListen = a.weitereListen.filterIndexed { k, _ -> k != i - 1 })
    }

    // Das Symbol steht danach genau auf den Listen aus wahl. Bezugskurs wie beim einfachen Hinzufuegen.
    fun waehlen(a: Account, symbol: String, price: Double, now: Long, wahl: Set<Int>): Account {
        var r = a
        for (i in a.watchlisten.indices) {
            val drin = symbol in r.watchlisten[i].symbole
            if (i in wahl && !drin) r = Trading.watch(r, symbol, price, now, i)
            else if (i !in wahl && drin) r = Trading.unwatch(r, symbol, i)
        }
        return r
    }

    // Zwei Staende zusammenfuehren: erste Listen vereinigt, weitere Listen gleichen Namens vereinigt, die uebrigen dazu.
    fun vereinigen(alt: Account, neu: Account): Account {
        val weitere = alt.weitereListen.toMutableList()
        neu.weitereListen.forEach { n ->
            val k = weitere.indexOfFirst { it.name.equals(n.name, ignoreCase = true) }
            if (k < 0) weitere += n else weitere[k] = weitere[k].copy(symbole = (weitere[k].symbole + n.symbole).distinct())
        }
        return alt.copy(
            ersteListe = (alt.ersteListe + neu.ersteListe).distinct(),
            ersteListeName = alt.ersteListeName.ifBlank { neu.ersteListeName },
            weitereListen = weitere,
        )
    }
}
