package com.example

import com.example.data.config.AppLanguage
import com.example.data.config.CountryRegistry
import com.example.data.model.IuuRiskLevel
import com.example.data.model.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun countryRegistry_resolvesAllPacksAndRtlArabic() {
        val idn = CountryRegistry.byCode("IDN")
        val ecu = CountryRegistry.byCode("ECU")
        val mar = CountryRegistry.byCode("MAR")
        assertEquals("IDR", idn.currencyCode)
        assertEquals("USD", ecu.currencyCode)
        assertEquals("MAD", mar.currencyCode)
        assertTrue(AppLanguage.AR.isRtl)
        assertEquals(TripStatus.AT_SEA, TripStatus.fromRaw("UNKNOWN_VALUE"))
        assertEquals(IuuRiskLevel.NONE, IuuRiskLevel.fromRaw(null))
    }
}
