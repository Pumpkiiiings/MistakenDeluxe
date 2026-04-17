# 🔪 Mistaken API v2.0 - Official Documentation

Welcome to the official documentation for the **Mistaken 2.0** API. This API allows developers to interact with the game cycle, query player states, and register custom content (such as new Assassins) without needing to modify the original source code of the minigame.

---

## 🚀 Getting Started

To use the Mistaken API in your plugin (Addon), ensure you add `Mistaken` as a dependency in your `plugin.yml` or `paper-plugin.yml` file:

```yaml
name: MyMistakenAddon
version: 1.0
main: org.myaddon.Main
depend: [Mistaken]
```

## 🔌 Obtaining the API Instance

The API operates under the **Singleton** pattern. You should never attempt to instantiate `MistakenAPI` directly. Instead, use the static method `getInstance()`.

```kotlin
val api = MistakenAPI.getInstance()
```
> **⚠️ Warning:** This method will throw an `IllegalStateException` if called before Mistaken finishes loading on the server. Make sure to call it within your `onEnable()`.

---

## 📚 Method Reference

### 🛠️ Custom Content Registration

#### `registerCustomAssassin(assassin: Assassin)`
Allows you to register a completely new and functional Assassin into the Mistaken catalog.

* **Parameters:**
    * `assassin`: An instance of a class that inherits from the abstract class `liric.mistaken.roles.assassins.Assassin`.
* **Behavior:**
    * The assassin's ID will be automatically converted to lowercase.
    * If an assassin with the same ID already exists in the base game or another Addon, the registration will be rejected (with a console warning) to prevent accidental data overwrites.

---

### 🎮 Game and Player Utilities

#### `isGameRunning(): Boolean`
Checks the state of the game manager.
* **Returns:** `true` if the game is currently in progress (`INGAME` state). `false` if it is in Lobby, Voting, Starting, or Ending.

#### `isAssassin(player: Player): Boolean`
Verifies if the provided player is the assassin in the current match.
* **Parameters:**
    * `player`: The Bukkit/Paper player to verify.
* **Returns:** `true` if they are the active assassin. `false` if they are a survivor, spectator, or not playing.

#### `getCurrentAssassinPlayer(): Player?`
Retrieves the physical entity of the current match's assassin.
* **Returns:** The `Player` object of the assassin if they are online and in-game. Returns `null` if there is no active game or if the assassin has disconnected.

---

## ⚠️ Development Considerations

1. **Thread-Safety:** Avoid registering Assassin classes asynchronously. Always do this on the primary thread during your plugin's `onEnable`.
2. **Configuration Files:** If you register a custom assassin, ensure you instruct server administrators to add your assassin's configuration (`ability1_cooldown`, `armor.helmet`, etc.) into the `assassins.yml` and `assassins_info.yml` files in the main Mistaken folder, using the exact ID you gave to your class.

---

## 💻 Practical Examples

### Example 1: Checking Game Status
How to use the API to prevent a command from executing if the game has already started.

```kotlin
import liric.mistaken.api.MistakenAPI
import org.bukkit.entity.Player

fun punishIfGameStarted(player: Player) {
    val api = MistakenAPI.getInstance()
    
    if (api.isGameRunning()) {
        player.sendMessage("You cannot use this command while Mistaken is in progress!")
        return
    }
}
```

### Example 2: Creating an Addon with a New Assassin

**Step 1: Create the Assassin Class**
The external developer creates their assassin class by inheriting from `Assassin`.

```kotlin
package org.myaddon.classes

import liric.mistaken.roles.assassins.Assassin
import org.bukkit.entity.Player

// Inherits from the base Assassin class, defining an ID ("clown") and a visual name using MiniMessage.
class ClownAssassin : Assassin("clown", "<red><b>The Clown</b></red>") {

    override fun equip(player: Player) {
        // Your own equipment logic using the Bukkit API
        player.sendMessage("Hahaha! You are now the clown.")
    }

    override fun useAbility(player: Player, slot: Int) {
        // The base Mistaken API already provides checkCooldown and playAbilityEffects natively.
        if (slot == 1 && !checkCooldown(player, 1)) {
            player.sendMessage("You used the prank!")
            playAbilityEffects(player, 1)
        }
    }

    override fun showTrail(player: Player) {
        // You can place code here to generate custom particles
    }
}
```

**Step 2: Register it in the Addon Main**
In the plugin's main file, inject the new class using the API.

```kotlin
package org.myaddon

import org.bukkit.plugin.java.JavaPlugin
import liric.mistaken.api.MistakenAPI
import org.myaddon.classes.ClownAssassin

class MyAddonMain : JavaPlugin() {

    override fun onEnable() {
        // 1. Get the API
        try {
            val mistakenApi = MistakenAPI.getInstance()
            
            // 2. Register the new assassin in the Mistaken catalog
            mistakenApi.registerCustomAssassin(ClownAssassin())
            
            logger.info("ClownAssassin successfully registered in Mistaken!")
        } catch (e: IllegalStateException) {
            logger.severe("Mistaken API not found! Ensure you have the base plugin installed.")
        }
    }
}
```