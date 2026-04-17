
---

# 🧩 PlaceholderAPI Expansion

The `Placeholders` class enables the extraction of real-time data from the Mistaken engine. Being a **Multi-Arena** implementation, most placeholders are contextual: they return information based on the specific session or match the player is currently in.

**Prefix:** `%mistaken_<identifier>%`

---

## 📊 Placeholder Reference

### 1. Game State (Contextual)
These values depend on the arena where the player is currently participating.

| Placeholder | Description | Example Values |
| :--- | :--- | :--- |
| `%mistaken_game_state%` | Current state of the game cycle. | `LOBBY`, `INGAME`, `ENDING` |
| `%mistaken_mode%` | Active game mode in the session. | `CLASSIC`, `HARDCORE` |
| `%mistaken_timer%` | Time remaining (in seconds) for the current phase. | `120`, `45` |
| `%mistaken_map%` | Name of the current map/arena. | `Hospital`, `Forest` |
| `%mistaken_session_id%` | Unique identifier for the match instance. | `NA_1`, `Lobby-1` |

### 2. Objectives (Generators)
Dynamically calculated based on the Minecraft world where the player is located.

| Placeholder | Description |
| :--- | :--- |
| `%mistaken_gens_reparados%` | Amount of completed generators in the current arena. |
| `%mistaken_gens_total%` | Total amount of generators registered in that arena. |

### 3. Roles and Assassins
| Placeholder | Description |
| :--- | :--- |
| `%mistaken_is_asesino%` | Returns "Si" (Yes) if the player is the assassin, "No" otherwise. |
| `%mistaken_asesino_nombre%` | Visual name (formatted) of the assassin the player is using. |
| `%mistaken_asesino_id%` | Technical ID of the assassin (e.g., `trapper`, `clown`). |

### 4. Global Statistics (Persistent)
Cumulative player data stored in the `StatsManager`.

| Placeholder | Description |
| :--- | :--- |
| `%mistaken_kills%` | Total confirmed kills. |
| `%mistaken_deaths%` | Total player deaths. |
| `%mistaken_kdr%` | Formatted Kill/Death Ratio. |
| `%mistaken_wins_total%` | Sum of all victories (Assassin + Survivor). |
| `%mistaken_wins_asesino%` | Victories achieved while playing as the Assassin. |
| `%mistaken_wins_survivor%` | Victories achieved as a Survivor. |
| `%mistaken_games_played%` | Total number of finished games. |
| `%mistaken_stamina%` | Current player energy for physical actions. |

---

## 🛠️ Technical Integration

For these placeholders to function, the plugin utilizes the `onRequest` method. Please note the following:

1.  **Persistence:** The expansion has `persist()` enabled, meaning it will not be unregistered if PlaceholderAPI reloads, unless the server restarts.
2.  **Null Safety:** If a player is offline or not in an active session, game-related placeholders will return default values (e.g., `LOBBY`, `0`, or `N/A`) to prevent visual glitches in interfaces.
3.  **ID Conversion:** All parameters are processed in lowercase via `.lowercase()`, so `%mistaken_GAME_STATE%` and `%mistaken_game_state%` are treated identically.

> **💡 Developer Note:** If you need to add a custom placeholder, you must register it within the `when(param)` block inside the `Placeholders` class.