package de.troetenrepublik.app.market.broker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IbkrAdresseTest {
    @Test fun adresseMitHttpsUndPort() {
        assertEquals("https://localhost:5000", IbkrBroker.adresse("localhost"))
        assertEquals("https://localhost:5000", IbkrBroker.adresse(" https://localhost:5000/ "))
        assertEquals("https://192.168.178.20:5001", IbkrBroker.adresse("http://192.168.178.20:5001"))
        assertEquals("https://[::1]:5000", IbkrBroker.adresse("[::1]"))
        assertEquals("https://gateway.local:5000", IbkrBroker.adresse("gateway.local:5000/sso/Login"))
        assertNull(IbkrBroker.adresse(""))
        assertNull(IbkrBroker.adresse("local host"))
    }

    @Test fun hostOhnePort() {
        assertEquals("localhost", IbkrBroker.host("https://localhost:5000"))
        assertEquals("192.168.178.20", IbkrBroker.host("https://192.168.178.20:5001"))
        assertEquals("::1", IbkrBroker.host("https://[::1]:5000"))
    }
}
