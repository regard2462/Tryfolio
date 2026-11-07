package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Vermoegen

// Aufteilung der Vermoegensschaetzung (5.96): Werte und Schulden getrennt, Netto nur mit eindeutigem Vorzeichen. Die
// Rangliste zaehlt nach der Mitte der Werte minus Mitte der Schulden; eine offene Spanne zaehlt mit der Untergrenze.
object VermoegenAnzeige {

    // Spanne in Dollar; bis = null: nach oben offen.
    data class Spanne(val von: Long, val bis: Long?)

    enum class Netto { OHNE_SCHULDEN, PLUS, MINUS, OFFEN }

    data class Teile(
        val werte: Spanne,
        val schulden: Spanne?,
        val nettoVon: Long?,
        val nettoBis: Long?,
        val fall: Netto,
        val posten: Int,
    )

    fun teile(v: Vermoegen.E): Teile {
        val schulden = if (v.sv == 0L && v.sb == 0L && !v.so) null else Spanne(v.sv, if (v.so) null else v.sb)
        val nettoVon = if (v.so) null else v.av - v.sb
        val nettoBis = if (v.ao) null else v.ab - v.sv
        val fall = when {
            schulden == null -> Netto.OHNE_SCHULDEN
            nettoVon != null && nettoVon >= 0 -> Netto.PLUS
            nettoBis != null && nettoBis < 0 -> Netto.MINUS
            else -> Netto.OFFEN
        }
        return Teile(Spanne(v.av, if (v.ao) null else v.ab), schulden, nettoVon, nettoBis, fall, v.z)
    }

    fun mitte(v: Vermoegen.E): Double {
        val werte = if (v.ao) v.av.toDouble() else (v.av + v.ab) / 2.0
        val schulden = when {
            v.sv == 0L && v.sb == 0L && !v.so -> 0.0
            v.so -> v.sv.toDouble()
            else -> (v.sv + v.sb) / 2.0
        }
        return werte - schulden
    }
}
