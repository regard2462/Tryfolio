package de.troetenrepublik.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSteuerIdTest {

    @Test fun exportEnthaeltKeineSteuerId() {
        val text = Backup.export(Account(), Settings(ownerName = "Alex", taxId = "12345678901"), 1L)
        assertFalse(text.contains("12345678901"))
        val p = (Backup.parse(text) as Backup.Outcome.Ok).payload
        assertEquals("", p.settings.taxId)
        assertEquals("Alex", p.settings.ownerName)
    }

    @Test fun importAlterDateiMitSteuerIdFunktioniertWeiter() {
        val alt = "{\"app\":\"troetenrepublik\",\"format\":2,\"account\":{\"cash\":5.0},\"settings\":{\"taxId\":\"12345678901\"}}"
        val parsed = Backup.parse(alt)
        assertTrue(parsed is Backup.Outcome.Ok)
        assertEquals("12345678901", (parsed as Backup.Outcome.Ok).payload.settings.taxId)
    }
}
