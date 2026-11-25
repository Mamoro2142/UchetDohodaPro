package com.example.uchetdohodapro

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatTest {
    @Test fun rublesUseSpacesAndTwoDecimalPlaces() {
        assertEquals("69\u00A0295,03 ₽", formatRubles(69295.03))
        assertEquals("7\u00A0990,71 ₽", formatRubles(7990.71))
        assertEquals("1\u00A0000\u00A0000,00 ₽", formatRubles(1000000.0))
        assertEquals("0,00 ₽", formatRubles(0.0))
        assertEquals("-12\u00A0345,67 ₽", formatRubles(-12345.67))
        assertEquals("999,99 ₽", formatRubles(999.99))
    }
}
