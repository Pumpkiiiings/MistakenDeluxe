package liric.mistaken.preview.menus

import liric.mistaken.preview.engine.CursorMath
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent

class SelectorInteractionListener : Listener {

    private val lastClick = mutableMapOf<java.util.UUID, Long>()

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        val menu = CharacterSelectorMenu.activeMenus[player.uniqueId] ?: return
        
        event.isCancelled = true
        if (event.action == Action.PHYSICAL) return

        val now = System.currentTimeMillis()
        if (lastClick.containsKey(player.uniqueId) && now - lastClick[player.uniqueId]!! < 250) return
        lastClick[player.uniqueId] = now

        // 1. Obtener la rotación actual
        val pYaw = player.location.yaw
        val pPitch = player.location.pitch

        // NOTA: Para un cálculo perfecto como Aurus, necesitaríamos la rotación base de la cámara 
        // (es decir, el yaw/pitch que usamos para fijar el cerdito/cámara).
        // Por ahora asumiremos que la cámara mira hacia el spawnLoc.
        // basePitch = 0f, baseYaw = angleToSpawn
        val baseYaw = 0f // Deberíamos calcular el ángulo entre cameraLoc y spawnLoc
        val basePitch = 0f
        val distance = 3.0 // Distancia desde la cámara hasta los hologramas

        val (cx, cy) = CursorMath.getCursorXY(baseYaw, basePitch, pYaw, pPitch, distance)

        // 2. Verificar colisiones 2D (Hitboxes virtuales)
        // Ejemplo: Flecha Izquierda está en X=-2.0, Y=0.0
        if (CursorMath.isHovering(cx, cy, btnX = -2.0, btnY = 0.0, width = 1.0, height = 1.0)) {
            menu.cycleLeft()
        } 
        // Flecha Derecha está en X=2.0, Y=0.0
        else if (CursorMath.isHovering(cx, cy, btnX = 2.0, btnY = 0.0, width = 1.0, height = 1.0)) {
            menu.cycleRight()
        }
        // Botón Seleccionar en X=0.0, Y=-1.5
        else if (CursorMath.isHovering(cx, cy, btnX = 0.0, btnY = -1.5, width = 2.0, height = 1.0)) {
            menu.select()
        }
    }
}
