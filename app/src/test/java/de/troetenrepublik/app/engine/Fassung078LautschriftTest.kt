package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test

// Lautschrift faellt aus Beschreibungen heraus (0.78), andere Klammern bleiben.
class Fassung078LautschriftTest {
    @Test fun eckigeKlammernMitLautschrift() {
        assertEquals(
            "Apple Inc. ist ein US-amerikanischer Hard- und Softwareentwickler.",
            Lautschrift.ohne("Apple Inc. [\u02C8\u00E6p\u0259l\u02CC\u026A\u014Bk] ist ein US-amerikanischer Hard- und Softwareentwickler."),
        )
        assertEquals("Nvidia Corporation ist ein Hersteller.", Lautschrift.ohne("Nvidia Corporation [\u025Bn\u02C8v\u026Adi\u0259] ist ein Hersteller."))
    }

    @Test fun schraegstricheUndRundeKlammern() {
        assertEquals("Nvidia Corporation is an American company.", Lautschrift.ohne("Nvidia Corporation ( /\u025Bn\u02C8v\u026Adi\u0259/ ) is an American company."))
        assertEquals("Huawei (Chinese name) is a company.", Lautschrift.ohne("Huawei ( /\u02C8hw\u0251\u02D0we\u026A/; Chinese name) is a company."))
    }

    @Test fun andereKlammernBleiben() {
        val t = "Der Umsatz stieg 2025 [Anm. 1] auf 10 Mrd. Euro (Vorjahr: 9 Mrd.), Kurs/Gewinn 12."
        assertEquals(t, Lautschrift.ohne(t))
        val u = "Frankreich [amtlich R\u00E9publique fran\u00E7aise] liegt in Europa; Verh\u00E4ltnis 1/2 und 3/4."
        assertEquals(u, Lautschrift.ohne(u))
    }
}
