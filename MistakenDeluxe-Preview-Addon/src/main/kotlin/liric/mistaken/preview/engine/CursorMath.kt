package liric.mistaken.preview.engine

import org.bukkit.Location
import kotlin.math.tan

object CursorMath {

    fun normalizeAngle(angle: Float): Float {
        var a = angle
        while (a <= -180) a += 360f
        while (a > 180) a -= 360f
        return a
    }

    /**
     * Calcula las coordenadas 2D (X, Y) en un plano a [distance] bloques de distancia,
     * basado en hacia donde mira el jugador ([playerYaw], [playerPitch]) 
     * respecto a la rotación base de la cámara ([baseYaw], [basePitch]).
     */
    fun getCursorXY(baseYaw: Float, basePitch: Float, playerYaw: Float, playerPitch: Float, distance: Double): Pair<Double, Double> {
        val dYaw = normalizeAngle(playerYaw - baseYaw)
        val dPitch = normalizeAngle(playerPitch - basePitch)
        
        val cx = tan(Math.toRadians(dYaw.toDouble())) * distance
        val cy = -tan(Math.toRadians(dPitch.toDouble())) * distance
        return Pair(cx, cy)
    }

    /**
     * Verifica si las coordenadas (cx, cy) caen dentro de un botón 
     * que está en (btnX, btnY) con ancho [width] y alto [height].
     */
    fun isHovering(cx: Double, cy: Double, btnX: Double, btnY: Double, width: Double, height: Double): Boolean {
        val dx = Math.abs(cx - btnX)
        val dy = Math.abs(cy - btnY)
        return dx <= (width / 2.0) && dy <= (height / 2.0)
    }
}
