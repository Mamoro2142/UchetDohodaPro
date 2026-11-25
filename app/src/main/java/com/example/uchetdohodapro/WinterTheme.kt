package com.example.uchetdohodapro

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val WinterOrange = Color(0xFFFF8A24)
private val Ice = Color(0xFFE6F1F9)
private val Warm = Color(0xFFFFC56D)

/** Presentation preference only; does not touch Room or financial backups. */
internal object WinterPreferences {
    private const val FILE = "appearance"
    private const val KEY = "winter_enabled"
    private const val HINT_SEEN = "winter_hint_seen"
    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false)
    fun hintSeen(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(HINT_SEEN, false)
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY, enabled).putBoolean(HINT_SEEN, true).apply()
    }
}

/** A single slow clock for the toggle and, when enabled, seasonal decorations on Home. */
@Composable
internal fun rememberWinterPhase(): State<Float> {
    val clock = rememberInfiniteTransition(label = "winter")
    return clock.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart),
        label = "winter phase"
    )
}

internal fun winterPulse(phase: Float, cycles: Int = 1, offset: Float = 0f): Float =
    (0.5 + 0.5 * sin(2.0 * PI * (phase * cycles + offset))).toFloat()

internal fun showWinterHint(enabled: Boolean, seen: Boolean): Boolean = !enabled && !seen

/** Seamless short bursts of sparkle, four times per sixteen-second loop. */
internal fun winterSparkle(phase: Float, offset: Float = 0f): Float {
    val p = winterPulse(phase, 4, offset)
    return p * p * p * p * p * p
}

@Composable
internal fun WinterToggle(enabled: Boolean, phase: State<Float>, onToggle: () -> Unit) {
    IconButton(
        onClick = onToggle,
        modifier = Modifier.size(48.dp).semantics {
            contentDescription = if (enabled) "Выключить новогоднюю тему" else "Включить новогоднюю тему"
            stateDescription = if (enabled) "Включена" else "Выключена"
        }
    ) {
        Canvas(Modifier.size(44.dp)) {
            val t = phase.value
            val tint = if (enabled) Color(0xFF8ADFFF) else WinterOrange
            val sparkleTint = if (enabled) Color(0xFFD9F5FF) else Warm
            val pulse = winterPulse(t, 4)
            val glow = size.minDimension * 0.49f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(tint.copy(alpha = 0.14f + 0.20f * pulse), tint.copy(alpha = 0f)),
                    center = center, radius = glow
                ), radius = glow, center = center
            )
            val swing = (winterPulse(t, 4) - 0.5f) * 8f
            rotate(swing, pivot = center) {
                snowflake(center, size.minDimension * (0.27f + 0.025f * pulse), tint, 1.8.dp.toPx())
            }
            repeat(4) { i ->
                val angle = PI / 4 + i * PI / 2
                val d = size.minDimension * 0.39f
                val at = center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * d
                val strength = winterSparkle(t, i * 0.19f)
                val r = (1.3f + 1.8f * strength).dp.toPx()
                val color = sparkleTint.copy(alpha = strength * 0.95f)
                drawLine(color, at - Offset(r, 0f), at + Offset(r, 0f), 0.9.dp.toPx(), StrokeCap.Round)
                drawLine(color, at - Offset(0f, r), at + Offset(0f, r), 0.9.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

/** Inline callout anchored to the 48dp toggle; never covers financial data or touch targets. */
@Composable
internal fun WinterHint(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Canvas(Modifier.padding(end = 17.dp).size(width = 14.dp, height = 9.dp)) {
            val tip = Path().apply {
                moveTo(0f, size.height)
                lineTo(size.width * 0.5f, 1.dp.toPx())
                lineTo(size.width, size.height)
            }
            drawPath(tip, WinterOrange, style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
        }
        Surface(
            modifier = Modifier.widthIn(max = 260.dp),
            shape = RoundedCornerShape(13.dp),
            color = Color(0xFF1C2023),
            contentColor = Color(0xFFF4F5F6),
            border = BorderStroke(1.2.dp, WinterOrange)
        ) {
            Text("Нажми, чтобы включить новогоднее оформление",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                fontSize = 14.sp, lineHeight = 19.sp)
        }
    }
}

private fun DrawScope.snowflake(c: Offset, radius: Float, tint: Color, stroke: Float) {
    repeat(6) { i ->
        val angle = PI / 3 * i - PI / 2
        val v = Offset(cos(angle).toFloat(), sin(angle).toFloat())
        val tip = c + v * radius
        drawLine(tint, c, tip, stroke, StrokeCap.Round)
        val joint = c + v * (radius * 0.66f)
        for (side in -1..1 step 2) {
            val a = angle + PI + side * PI / 4
            val end = joint + Offset(cos(a).toFloat(), sin(a).toFloat()) * (radius * 0.31f)
            drawLine(tint, joint, end, stroke, StrokeCap.Round)
        }
    }
}

/** Decorations are in their own header space, never over labels or the snowflake toggle. */
@Composable
internal fun WinterHeader(phase: State<Float>) {
    Canvas(Modifier.fillMaxWidth().height(68.dp)) {
        val t = phase.value
        val w = size.width
        val h = size.height
        val left = 5.dp.toPx()
        val right = w - 5.dp.toPx()
        fun cableY(f: Float): Float = h * (0.10f + 0.23f * sin(PI * f).toFloat())
        val cable = Path().apply {
            moveTo(left, cableY(0f))
            for (i in 1..40) {
                val f = i / 40f
                lineTo(left + (right - left) * f, cableY(f))
            }
        }
        drawPath(cable, Color(0xFF59675B), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
        val colors = listOf(WinterOrange, Warm, Color(0xFF6FD28F), Color(0xFFFFE1A3))
        repeat(8) { i ->
            val f = (i + 0.45f) / 9f
            val x = left + (right - left) * f
            val y = cableY(f)
            val power = 0.45f + 0.55f * winterPulse(t, 3, i * 0.17f)
            val color = colors[i % colors.size]
            val bulb = Offset(x, y + 9.dp.toPx())
            val glow = 14.dp.toPx()
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = power * 0.45f), color.copy(alpha = 0f)), center = bulb, radius = glow), glow, bulb)
            drawRoundRect(Color(0xFF5D655B), Offset(x - 2.dp.toPx(), y), Size(4.dp.toPx(), 5.dp.toPx()), CornerRadius(1.dp.toPx()))
            drawOval(color.copy(alpha = 0.55f + 0.45f * power), bulb - Offset(3.dp.toPx(), 4.dp.toPx()), Size(6.dp.toPx(), 8.dp.toPx()))
            drawCircle(Color(0xFFFFF4D7).copy(alpha = power), 1.35.dp.toPx(), bulb - Offset(0.8.dp.toPx(), 1.7.dp.toPx()))
        }
        // Snow-covered evergreen in the upper-right corner; generated from small vector strokes.
        val branchWidth = minOf(w * 0.31f, 110.dp.toPx())
        val root = Offset(w - 2.dp.toPx(), 3.dp.toPx())
        for (j in 0..2) {
            val start = root + Offset(-branchWidth * 0.09f * j, h * 0.09f * j)
            val end = Offset(w - branchWidth * (0.64f + 0.15f * j), h * (0.15f + 0.16f * j))
            val delta = end - start
            drawLine(Color(0xFF5E5741), start, end, 2.3.dp.toPx(), StrokeCap.Round)
            repeat(15) { k ->
                val f = (k + 1f) / 16f
                val p = start + delta * f
                val length = branchWidth * (0.24f - 0.12f * f)
                val tipA = p + Offset(-length * 0.7f, -length * 0.52f)
                val tipB = p + Offset(-length * 0.85f, length * 0.65f)
                drawLine(if (k % 2 == 0) Color(0xFF285B45) else Color(0xFF39775A), p, tipA, 1.8.dp.toPx(), StrokeCap.Round)
                drawLine(Color(0xFF234C39), p, tipB, 1.9.dp.toPx(), StrokeCap.Round)
                if (k % 3 == 1) drawLine(Ice.copy(alpha = 0.87f), p + Offset(0f, -2.dp.toPx()), p + (tipA - p) * 0.75f, 2.5.dp.toPx(), StrokeCap.Round)
            }
        }
        val anchor = Offset(w - branchWidth * 0.35f, h * 0.28f)
        val swing = (winterPulse(t, 3) - 0.5f) * 8f
        rotate(swing, pivot = anchor) {
            val ball = anchor + Offset(0f, h * 0.34f)
            val r = 10.dp.toPx()
            drawLine(Color(0xFFDFBE83), anchor, ball - Offset(0f, r), 0.8.dp.toPx())
            drawRoundRect(Warm, ball - Offset(2.5.dp.toPx(), r + 2.dp.toPx()), Size(5.dp.toPx(), 4.dp.toPx()), CornerRadius(1.dp.toPx()))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFF7971), Color(0xFFD34043), Color(0xFF731E29)), center = ball - Offset(r * 0.3f, r * 0.35f), radius = r * 1.35f), r, ball)
            drawOval(Color.White.copy(alpha = 0.65f), ball - Offset(r * 0.47f, r * 0.57f), Size(r * 0.42f, r * 0.27f))
        }
        snowflake(Offset(w * 0.19f, h * 0.77f), 4.dp.toPx(), Ice.copy(alpha = 0.55f), 0.7.dp.toPx())
        snowflake(Offset(w * 0.48f, h * 0.66f), 5.dp.toPx(), Ice.copy(alpha = 0.45f + winterPulse(t, 2) * 0.32f), 0.8.dp.toPx())
    }
}

private data class SnowParticle(val x: Float, val y: Float, val radius: Float, val speed: Int, val side: Boolean)
private val snowParticles = Random(2026).let { random ->
    List(16) { i -> SnowParticle(random.nextFloat(), random.nextFloat(), 0.7f + random.nextFloat() * 1.4f, 1 + i % 2, i % 2 == 0) }
}

/** Non-interactive foreground decoration, restricted to gutters outside the 20dp content margin. */
@Composable
internal fun WinterEdgeSnow(phase: State<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val t = phase.value
        val gutter = 17.dp.toPx()
        snowParticles.forEachIndexed { index, p ->
            val drift = sin(2.0 * PI * (t * 2 + p.y)).toFloat() * 1.5.dp.toPx()
            val edgeX = (4.dp.toPx() + p.x * 8.dp.toPx() + drift).coerceIn(2.dp.toPx(), gutter)
            val x = if (p.side) edgeX else size.width - edgeX
            val y = ((p.y + t * p.speed) % 1f) * size.height
            val r = p.radius.dp.toPx()
            val color = Ice.copy(alpha = 0.2f + 0.48f * winterPulse(t, 2, p.y))
            if (index % 5 == 0) snowflake(Offset(x, y), r * 1.6f, color, 0.65.dp.toPx())
            else drawCircle(color, r, Offset(x, y))
        }
    }
}

/** Thin snow cap and subtle warm highlight, strictly within the existing card bounds. */
internal fun Modifier.winterCard(phase: State<Float>?): Modifier {
    if (phase == null) return this
    return drawWithContent {
        drawContent()
        val t = phase.value
        val inset = 1.dp.toPx()
        if (size.width <= inset * 2 || size.height <= inset * 2) return@drawWithContent
        drawRoundRect(Warm.copy(alpha = 0.12f + 0.17f * winterPulse(t, 2)),
            topLeft = Offset(inset, inset), size = Size(size.width - 2 * inset, size.height - 2 * inset),
            cornerRadius = CornerRadius(21.dp.toPx()), style = Stroke(1.dp.toPx()))
        val left = 22.dp.toPx()
        val right = size.width - left
        if (right <= left) return@drawWithContent
        val width = right - left
        val snow = Path().apply {
            moveTo(left, inset)
            lineTo(right, inset)
            for (i in 18 downTo 0) {
                val f = i / 18f
                val depth = 2.dp.toPx() + 2.dp.toPx() * sin(i * 1.7).toFloat().let { it * it }
                lineTo(left + width * f, inset + depth)
            }
            close()
        }
        drawPath(snow, Ice.copy(alpha = 0.83f))
    }
}

internal fun Modifier.winterSegment(phase: State<Float>?, index: Int, filled: Boolean): Modifier {
    if (phase == null || !filled) return this
    return drawWithContent {
        drawContent()
        drawRoundRect(Warm.copy(alpha = 0.12f * winterPulse(phase.value, 2, -index * 0.07f)), cornerRadius = CornerRadius(3.dp.toPx()))
    }
}
