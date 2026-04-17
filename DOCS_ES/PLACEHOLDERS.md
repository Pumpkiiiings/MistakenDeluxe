---

# 🧩 PlaceholderAPI Expansion

La clase `Placeholders` permite extraer datos en tiempo real del motor de Mistaken. Al ser una implementación **Multi-Arena**, la mayoría de los placeholders son contextuales: devolverán información basada en la sesión/partida específica en la que se encuentre el jugador.

**Prefijo:** `%mistaken_<identificador>%`

---

## 📊 Referencia de Placeholders

### 1. Estado de Partida (Contextuales)
Estos valores dependen de la arena donde el jugador esté participando actualmente.

| Placeholder | Descripción | Valores de Ejemplo |
| :--- | :--- | :--- |
| `%mistaken_game_state%` | Estado actual del ciclo de juego. | `LOBBY`, `INGAME`, `ENDING` |
| `%mistaken_mode%` | Modo de juego activo en la sesión. | `CLASSIC`, `HARDCORE` |
| `%mistaken_timer%` | Tiempo restante (en segundos) de la fase actual. | `120`, `45` |
| `%mistaken_map%` | Nombre del mapa/arena actual. | `Hospital`, `Forest` |
| `%mistaken_session_id%` | Identificador único de la instancia de partida. | `NA_1`, `Lobby-1` |

### 2. Objetivos (Generadores)
Calculados dinámicamente según el mundo de Minecraft donde se encuentra el jugador.

| Placeholder | Descripción |
| :--- | :--- |
| `%mistaken_gens_reparados%` | Cantidad de generadores completados en la arena actual. |
| `%mistaken_gens_total%` | Cantidad total de generadores registrados en esa arena. |

### 3. Roles y Asesinos
| Placeholder | Descripción |
| :--- | :--- |
| `%mistaken_is_asesino%` | Devuelve "Si" si el jugador es el asesino, "No" en caso contrario. |
| `%mistaken_asesino_nombre%` | Nombre visual (con formato) del asesino que el jugador está usando. |
| `%mistaken_asesino_id%` | ID técnica del asesino (ej: `trampero`, `payaso`). |

### 4. Estadísticas Globales (Persistentes)
Datos acumulativos del jugador almacenados en el `StatsManager`.

| Placeholder | Descripción |
| :--- | :--- |
| `%mistaken_kills%` | Total de bajas confirmadas. |
| `%mistaken_deaths%` | Total de veces que el jugador ha muerto. |
| `%mistaken_kdr%` | Ratio de Kills/Deaths formateado. |
| `%mistaken_wins_total%` | Suma de todas las victorias (Asesino + Superviviente). |
| `%mistaken_wins_asesino%` | Victorias conseguidas jugando como Asesino. |
| `%mistaken_wins_survivor%` | Victorias conseguidas como Superviviente. |
| `%mistaken_games_played%` | Total de partidas finalizadas. |
| `%mistaken_stamina%` | Energía actual del jugador para acciones físicas. |

---

## 🛠️ Integración Técnica

Para que estos placeholders funcionen, el plugin utiliza el método `onRequest`. Es importante notar que:

1.  **Persistencia:** La expansión tiene activado `persist()`, lo que significa que no se descargará si PlaceholderAPI se recarga, a menos que el servidor se reinicie.
2.  **Seguridad de Nulos:** Si un jugador está offline o no se encuentra en una sesión activa, los placeholders de partida devolverán valores por defecto (ej: `LOBBY`, `0` o `N/A`) para evitar errores visuales en las interfaces.
3.  **Conversión de IDs:** Todos los parámetros se procesan en minúsculas (`lowercase()`), por lo que no importa si usas `%mistaken_GAME_STATE%` o `%mistaken_game_state%`.

> **💡 Nota para Desarrolladores:** Si necesitas añadir un placeholder personalizado, debes registrarlo en el bloque `when(param)` dentro de la clase `Placeholders`.