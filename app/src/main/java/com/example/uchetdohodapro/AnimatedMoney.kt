package com.example.uchetdohodapro

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Keys align digits by decimal place, not by the position of a thousands separator. */
internal fun moneyGlyphs(value: String): List<Pair<String, Char>> {
    val comma = value.indexOf(',').let { if (it < 0) value.length else it }
    var integerDigits = value.take(comma).count { it.isDigit() }
    var decimals = 0
    return value.mapIndexed { i, char ->
        val key = when {
            char.isDigit() && i < comma -> "int-${--integerDigits}"
            char.isDigit() -> "frac-${decimals++}"
            i < comma && char.isWhitespace() -> "group-$integerDigits"
            else -> "literal-$char-${if (i < comma) "before" else "after"}"
        }
        key to char
    }
}

internal class MoneyDisplayMemory {
    private val values = mutableMapOf<String, String>()
    fun previous(key: String, actual: String) = values[key] ?: actual
    fun shown(key: String, actual: String) { values[key] = actual }
}
private val LocalMoneyDisplayMemory = staticCompositionLocalOf<MoneyDisplayMemory> { error("Missing MoneyAnimationHost") }

@Composable
internal fun MoneyAnimationHost(content: @Composable () -> Unit) {
    val memory = remember { MoneyDisplayMemory() }
    CompositionLocalProvider(LocalMoneyDisplayMemory provides memory, content = content)
}

@Composable
internal fun AnimatedMoney(value: String, color: Color, fontSize: TextUnit, counter: String, fontWeight: FontWeight = FontWeight.Bold) {
    // Retain last displayed value across tabs/forms; animate only an actual change.
    val memory = LocalMoneyDisplayMemory.current
    var target by remember(counter) { mutableStateOf(memory.previous(counter, value)) }
    LaunchedEffect(counter, value) { target = value; memory.shown(counter, value) }
    BoxWithConstraints(Modifier.fillMaxWidth().clearAndSetSemantics { text = AnnotatedString(value) }) {
        val measurer = rememberTextMeasurer()
        val style = TextStyle(fontSize = fontSize, fontWeight = fontWeight, fontFeatureSettings = "tnum", color = color)
        val width = measurer.measure(AnnotatedString(value), style = style, softWrap = false).size.width
        val available = with(LocalDensity.current) { maxWidth.toPx() }
        val ratio = if (width > 0) (available / (width * 1.04f)).coerceIn(.01f, 1f) else 1f
        val sized = style.copy(fontSize = (fontSize.value * ratio).sp)
        val glyphs = remember(target) { moneyGlyphs(target) }
        Row {
            glyphs.forEach { (place, glyph) -> key(place) {
                if (glyph.isDigit()) {
                    AnimatedContent(targetState = glyph, transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (slideInVertically(tween(700)) { direction * it } togetherWith slideOutVertically(tween(700)) { -direction * it }).using(SizeTransform(clip = true))
                    }, label = "money-digit") { c -> Text(c.toString(), style = sized, maxLines = 1, softWrap = false) }
                } else Text(glyph.toString(), style = sized, maxLines = 1, softWrap = false)
            } }
        }
    }
}
