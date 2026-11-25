package com.example.uchetdohodapro

import java.util.Locale

/** Russian ruble display, independent of device language. Never changes stored values. */
internal fun formatRubles(value: Double): String =
    String.format(Locale.US, "%,.2f", value)
        .replace(',', '\u00A0')
        .replace('.', ',') + " ₽"
