# 🏗️ HealthAPI

The `HealthAPI` interface acts as the master contract for managing the vital state of `Player` entities. It is imperative to use this interface for any health manipulation, avoiding the use of native Bukkit methods that might conflict with the plugin's persistence logic.

### Method Definition

| Method | Return | Description |
| :--- | :--- | :--- |
| `getHealth(Player)` | `Int` | Retrieves the integer health value (defined range: 0-6). |
| `setHealth(Player, Int)` | `Unit` | Imperatively sets the subject's health state. |
| `takeDamage(Player)` | `Unit` | Triggers the damage pipeline: life reduction, visual, and auditory triggers. |
| `isFrozen(Player)` | `Boolean` | Queries the subject's immobilization (Freeze Tag) state. |
| `unfreeze(Player, Player)`| `Unit` | Executes the rescue transaction between a victim and a rescuer. |
| `resetPlayer(Player)` | `Unit` | Resets the player profile to the environment's initial values. |

---

## 🛠️ Examples

```kotlin
import org.bukkit.entity.Player
import liric.mistaken.api.HealthAPI

/**
 * Manages the restoration of vitality points in a controlled manner.
 * Execution via a Scheduler with 30s+ intervals is recommended.
 */
fun processPassiveRecovery(player: Player, healthAPI: HealthAPI) {
    val currentHealth = healthAPI.getHealth(player)
    val MAX_HEALTH = 6

    if (currentHealth in 1 until MAX_HEALTH) {
        // Atomic health increment
        healthAPI.setHealth(player, currentHealth + 1)
        player.sendMessage("§9[System] §7One vitality point has been passively restored.")
    }
}
```

```kotlin
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import liric.mistaken.api.HealthAPI

/**
 * Listener responsible for intercepting physical interactions for rescues.
 */
class RescueListener(private val healthAPI: HealthAPI) : Listener {

    @EventHandler
    fun onSurvivorInteract(event: PlayerInteractEntityEvent) {
        val rescuer = event.player
        val target = event.rightClicked

        if (target is Player && healthAPI.isFrozen(target)) {
            // Execution of the rescue transaction through the HealthAPI contract
            healthAPI.unfreeze(target, rescuer)
            
            // Status confirmation log
            target.sendMessage("§b[System] §fYou have been brought back into the game by ${rescuer.name}.")
        }
    }
}
```

```kotlin
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import liric.mistaken.api.events.MistakenDeathEvent

/**
 * Reward orchestrator based on confirmed kill events.
 */
class RewardProcessor : Listener {

    @EventHandler
    fun onEliminationConfirmed(event: MistakenDeathEvent) {
        val killer = event.killer
        val victim = event.victim

        // Telemetry logic and asset distribution
        val experienceReward = 250
        killer.giveExp(experienceReward)

        killer.sendMessage("§6[Global] §fKill confirmed on §e${victim.name} §f| §a+$experienceReward XP")
    }
}
```
