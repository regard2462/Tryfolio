package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Hinweis "Gerechnet ab" beim groessten Rueckgang: nur, wenn die aeltesten Buchungen fehlen.
class RueckgangUnvollstaendigTest {
    private fun tx(ts: Long, type: TxType, tag: String = "", via: String = "") = Transaction(id = "t$ts", ts = ts, type = type, title = "", tag = tag, via = via)

    @Test(timeout = 20_000)
    fun vollstaendigerBeginnZeigtNichts() {
        val acc = Account(transactions = listOf(tx(200, TxType.BUY), tx(100, TxType.DEPOSIT)))
        assertNull(Rueckgang.unvollstaendigAb(acc))
        assertNull(Rueckgang.unvollstaendigAb(Account()))
    }

    @Test(timeout = 20_000)
    fun ersterVerkaufUndUebertragFehlenDenBeginn() {
        assertEquals(100L, Rueckgang.unvollstaendigAb(Account(transactions = listOf(tx(300, TxType.DEPOSIT), tx(100, TxType.SELL)))))
        assertEquals(50L, Rueckgang.unvollstaendigAb(Account(transactions = listOf(tx(50, TxType.BUY, tag = "TRANSFER_IN", via = "IMPORT"), tx(100, TxType.BUY)))))
        assertEquals(70L, Rueckgang.unvollstaendigAb(Account(transactions = listOf(tx(70, TxType.BUY, via = "IMPORT_X"), tx(100, TxType.SELL)))))
    }
}
