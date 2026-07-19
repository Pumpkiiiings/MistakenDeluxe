package pumpking.lib.scoreboard

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import pumpking.lib.color.ColorTranslator
import kotlin.math.sin

/**
 * Utility for generating animated Adventure Components.
 * Used exclusively by PacketEventsRenderer for advanced visual effects.
 */
object ScoreboardAnimator {

    /**
     * Produces an animated RGB gradient Component that shifts over time.
     *
     * FIX #10: Previous implementation appended Components one char at a time inside the
     * loop: `result = result.append(Component.text(char.toString()).color(...))`.
     * With a 20-char title, 50 players and a 2-tick interval this allocated ~10,000
     * Component objects per second, causing measurable GC pressure.
     * Now uses a single Component.Builder to batch all characters into one allocation.
     *
     * @param text The raw text content (no color codes).
     * @param phase Tick count used to animate the hue cycle (0..359).
     * @param frequency How fast the gradient shifts (default 1.0).
     */
    fun animatedGradient(text: String, phase: Int, frequency: Double = 1.0): Component {
        if (text.isEmpty()) return Component.empty()

        val len = text.length
        val builder = Component.text()

        for (i in 0 until len) {
            val hue = ((phase + (i * 360.0 / len) * frequency) % 360.0).toFloat()
            val color = TextColor.color(hsvToRgb(hue, 1.0f, 1.0f))
            // Component.text(Char) avoids the String allocation of char.toString()
            builder.append(Component.text(text[i]).color(color))
        }

        return builder.build()
    }

    /**
     * Produces a pulsing wave title Component. The brightness oscillates using a sine function.
     *
     * @param text The raw text.
     * @param tick Current tick (0..n), used to calculate wave phase.
     */
    fun pulsatingTitle(text: String, tick: Int): Component {
        val brightness = ((sin(tick * 0.15) + 1.0) / 2.0).coerceIn(0.4, 1.0).toFloat()
        val r = (brightness * 255).toInt()
        val g = (brightness * 160).toInt()
        val b = (brightness * 20).toInt()
        val color = TextColor.color(r, g, b)
        return Component.text(ColorTranslator.translate(text).toString()).color(color)
    }

    /** Converts HSV color model to AWT-style packed int RGB. */
    private fun hsvToRgb(h: Float, s: Float, v: Float): Int {
        val hi = ((h / 60) % 6).toInt()
        val f = h / 60 - hi
        val p = v * (1 - s)
        val q = v * (1 - f * s)
        val t = v * (1 - (1 - f) * s)
        val (r, g, b) = when (hi) {
            0 -> Triple(v, t, p)
            1 -> Triple(q, v, p)
            2 -> Triple(p, v, t)
            3 -> Triple(p, q, v)
            4 -> Triple(t, p, v)
            else -> Triple(v, p, q)
        }
        return (r * 255).toInt().shl(16) or (g * 255).toInt().shl(8) or (b * 255).toInt()
    }
}
