![Mistaken](https://i.ibb.co/6RKZcwQD/mistakendeluxe.png)

![Version](https://img.shields.io/badge/Version-2.0.0-79addc?style=for-the-badge&logo=semver&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Platform](https://img.shields.io/badge/Paper%20%7C%20Folia-Supported-00B7EE?style=for-the-badge&logo=google-cloud&logoColor=white)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-111111?style=for-the-badge&logo=minecraft&logoColor=white)
---
## 📄 Overview

**Mistaken** is an asymmetrical multiplayer horror and survival minigame for Minecraft, heavily inspired by titles like *Dead by Daylight*, *Forsaken (Roblox)*, *Bear Alpha (Roblox)*, and *Die Of Death (Roblox)*.

In Mistaken, players are divided into two teams: **The Survivors**, who must repair generators, outsmart their hunter, and escape to survive; and **The Killer**, an unstoppable entity whose sole objective is to hunt down the survivors before they can escape.

The plugin is built as a highly optimized, feature-rich modern minigame framework for Minecraft 1.21.4+.

---

## ✨ Features

* **Asymmetrical Gameplay:** Thrilling 1v4 (or custom ratio) survival horror mechanics.
* **Roles & Classes:** Play as unique Killers or Survivors, each featuring their own set of custom abilities, speeds, and traits (e.g., *Troll*, *Flashlight abilities*, etc.).
* **Lag-Free Custom UI:** Built-in observer HUDs and custom overhead nametags powered by `TextDisplay` entities, eliminating traditional vanilla nametag wall-hacks.
* **Advanced Mechanics:** Generator repairs, heartbeat terror radius, sprinting/stamina systems, and dynamic player visibility.
* **Modern Architecture:** Built targeting Java 21 and Kotlin 2.1.0, utilizing Paper's native `AsyncScheduler` for multi-threaded performance.

---

## 🏗️ Requirements & Dependencies

To ensure **Mistaken Deluxe** runs flawlessly, your server must have the following plugins installed:

**Required:**
* **[LuckPerms](https://luckperms.net/):** Required for handling rank permissions and killer role access.
* **[Vault](https://github.com/MilkBowl/VaultAPI):** Required for match rewards and in-game economy integration.

**Optional (Recommended):**
* **[CraftEngine](https://github.com/Xiao-MoMi/craft-engine):** Extended support for advanced models and game mechanics.
* **PumpkinEffect:** Custom cinematic death effects.
* **[PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI):** External variable support for scoreboards and menus.

---

## 🛠️ Technologies & APIs

Mistaken is built to be fast, responsive, and developer-friendly. We achieve this by integrating industry-standard APIs:

* **📄 [Paper API](https://papermc.io/):** The core engine of the server, targeting Java 21 and Paper's modern scheduler APIs.
* **📦 [PacketEvents](https://github.com/retrooper/packetevents):** Packet management framework handling low-level, lag-free network operations (custom HUDs, visibility manipulation).
* **✨ [Advanced Slime Paper (ASP)](https://github.com/InfernalSuite/AdvancedSlimePaper):** Ultra-fast world loading system specifically designed for minigame instancing.
* **🎞️ [Triumph GUI](https://github.com/TriumphTeam/triumph-gui):** UI Framework utilizing **Hybrid Cache** optimizations for blazing-fast inventory menus.
* **🔐 [HikariCP](https://github.com/brettwooldridge/HikariCP):** The world's fastest SQL connection pool ensuring zero-hiccup database operations for player statistics.

---

## 🌍 Hybrid arena worlds

The default backend belongs in `plugins/Mistaken/config.yml`:

```yaml
settings:
  arena-worlds:
    backend: arena_api # arena_api or slime
    schematics-path: "/home/container/schematics"
    slime-worlds-path: "/home/container/slime_worlds"
```

- `slime`: arena `hospital` loads `hospital.slime` with AdvancedSlimePaper.
- `arena_api`: arena `hospital` auto-registers `hospital.schem` with ArenaAPI.

Most maps only need gameplay data in `arenas.yml`; both backends use the arena id as their template id:

```yaml
arenas:
  hospital:
    timeMode: night
```

ArenaAPI is an optional Paper dependency. When that backend creates a match, Mistaken stores its UUID-backed instance and calls `destroy()` after returning players to the lobby. ASP worlds continue using the existing fast clone path.

---

## 📢 Community & Support

Have questions about setting up the plugin? Want to stay up to date with the latest **Mistaken** features?

[![Discord](https://invidget.switchblade.xyz/xqKqtgfsfy)](https://discord.gg/xqKqtgfsfy)

---

## 🤝 Credits & Acknowledgements

This project is led and managed by **Pumpkingz**, but it wouldn't be possible without the incredible support of the team:

* **Antigravity:** Core architecture, code writing, and major refactoring.
* **Pumpkingz:** Project management, vision, and bringing Mistaken to life.
* **Minty:** Original creator of the Mistaken concept.

---
© 2026 **Pumpkingz** - Developed with ❤️.
