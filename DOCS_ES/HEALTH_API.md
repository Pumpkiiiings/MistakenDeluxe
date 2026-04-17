![api](https://i.ibb.co/TM8QzsCk/api2.png)

Esta documentación define los estándares de integración para el ecosistema **Mistaken**. La API ha sido diseñada bajo principios de desacoplamiento y alta cohesión, permitiendo a los desarrolladores extender la funcionalidad del core sin comprometer la integridad del ciclo de vida del juego.

---

## 🏗️ HealthAPI

La interfaz `HealthAPI` actúa como el contrato maestro para la gestión del estado vital de las entidades `Player`. Es imperativo utilizar esta interfaz para cualquier manipulación de salud, evitando el uso de métodos nativos de Bukkit que puedan entrar en conflicto con la lógica de persistencia del plugin.

### Definición de Métodos

| Método | Retorno | Descripción |
| :--- | :--- | :--- |
| `getHealth(Player)` | `Int` | Recupera el valor entero de salud (rango definido: 0-6). |
| `setHealth(Player, Int)` | `Unit` | Establece de forma imperativa el estado de salud del sujeto. |
| `takeDamage(Player)` | `Unit` | Dispara el pipeline de daño: reducción de vida, triggers visuales y auditivos. |
| `isFrozen(Player)` | `Boolean` | Consulta el estado de inmovilización (Freeze Tag) del sujeto. |
| `unfreeze(Player, Player)`| `Unit` | Ejecuta la transacción de rescate entre una víctima y un rescatista. |
| `resetPlayer(Player)` | `Unit` | Restablece el perfil del jugador a los valores iniciales del entorno. |

---

## 🛠️ Ejemplos

```kotlin
import org.bukkit.entity.Player
import liric.mistaken.api.HealthAPI

/**
 * Gestiona la restauración de puntos de vitalidad de forma controlada.
 * Se recomienda su ejecución mediante un Scheduler con intervalos de 30s+.
 */
fun processPassiveRecovery(player: Player, healthAPI: HealthAPI) {
    val currentHealth = healthAPI.getHealth(player)
    val MAX_HEALTH = 6

    if (currentHealth in 1 until MAX_HEALTH) {
        // Incremento atómico de salud
        healthAPI.setHealth(player, currentHealth + 1)
        player.sendMessage("§9[System] §7Se ha restaurado un punto de vitalidad de forma pasiva.")
    }
}
```

```kotlin
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import liric.mistaken.api.HealthAPI

/**
 * Listener encargado de interceptar interacciones físicas para rescates.
 */
class RescueListener(private val healthAPI: HealthAPI) : Listener {

    @EventHandler
    fun onSurvivorInteract(event: PlayerInteractEntityEvent) {
        val rescuer = event.player
        val target = event.rightClicked

        if (target is Player && healthAPI.isFrozen(target)) {
            // Ejecución de la transacción de rescate a través del contrato HealthAPI
            healthAPI.unfreeze(target, rescuer)
            
            // Log de confirmación de estado
            target.sendMessage("§b[System] §fHas sido reincorporado al juego por ${rescuer.name}.")
        }
    }
}
```

```kotlin
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import liric.mistaken.api.events.MistakenDeathEvent

/**
 * Orquestador de recompensas basado en eventos de baja confirmada.
 */
class RewardProcessor : Listener {

    @EventHandler
    fun onEliminationConfirmed(event: MistakenDeathEvent) {
        val killer = event.killer
        val victim = event.victim

        // Lógica de telemetría y distribución de assets
        val experienceReward = 250
        killer.giveExp(experienceReward)

        killer.sendMessage("§6[Global] §fBaja confirmada sobre §e${victim.name} §f| §a+$experienceReward XP")
    }
}
```