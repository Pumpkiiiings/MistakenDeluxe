# Comandos de Mistaken Deluxe

Esta es la lista completa de todos los comandos registrados en el plugin, incluyendo los comandos administrativos, comandos para jugadores y los comandos ocultos/debug.

## Comandos para Jugadores

Estos comandos están disponibles para todos los usuarios.

| Comando Principal | Alias | Descripción |
| :--- | :--- | :--- |
| `/join` | `/play`, `/jugar` | Te une a una partida o a la cola de emparejamiento. |
| `/leave` | `/quit`, `/salir` | Te saca de la partida actual y te devuelve al lobby. |
| `/votar` | `/vote` | Abre el menú para votar por la arena que se jugará. |
| `/espectear` | `/spectate` | Te permite entrar al modo espectador en una partida en curso. |
| `/link` | *(Ninguno)* | Te permite vincular tu cuenta de Discord con el juego. |
| `/unlink` | *(Ninguno)* | Desvincula tu cuenta de Discord. |

---

## Comando Principal (`/mistaken`)

El comando base del plugin. Sus alias son `/ms` y `/mt`. Algunos subcomandos son públicos, pero la mayoría requieren permisos administrativos (`mistaken.admin`).

### Subcomandos Públicos
- `/mistaken shop` (o `tienda`): Abre el menú de la tienda de clases (asesino/superviviente).
- `/mistaken langs <idioma>` (o `language`): Te permite cambiar el idioma en el que recibes los mensajes.
- `/mistaken stats [jugador]` (o `estadisticas`): Muestra tus estadísticas (o las de otro jugador si eres Admin).
- `/mistaken afk`: Activa/Desactiva tu estado AFK.

### Subcomandos de Administración (`mistaken.admin`)
- `/mistaken start`: Fuerza el inicio rápido de la partida actual.
- `/mistaken stop`: Detiene la partida actual y la cancela forzosamente.
- `/mistaken setmode <modo>`: Fuerza un modo específico en la próxima partida (Ej. DOUBLE_KILLER, INFECTION).
- `/mistaken setstamina <cantidad>`: Modifica los niveles de estamina de un jugador.
- `/mistaken setasesino <clase>`: Asigna forzosamente una clase de asesino específica.
- `/mistaken setsuperviviente <clase>`: Asigna forzosamente una clase de superviviente específica.
- `/mistaken removekiller [jugador]`: Remueve los poderes de asesino a un jugador.
- `/mistaken edit`: Activa o desactiva el modo edición (Staff Edit Mode).
- `/mistaken reload`: Recarga todas las configuraciones, tiendas, menús e idiomas desde los archivos `.yml`.

---

## Gestión de Arenas (`/arena`)

Comandos dedicados a la configuración y revisión del estado de los mapas. Requieren permiso `mistaken.admin`.

- `/arena create <nombre>`: Crea el registro de una arena (asegúrate de tener `<nombre>.slime` listo).
- `/arena delete <nombre>`: Elimina una arena por completo.
- `/arena setspawn <nombre> asesino`: Define la ubicación de aparición del asesino.
- `/arena setspawn <nombre> survivor`: Añade un punto de aparición para los supervivientes.
- `/arena setgenerator <nombre>`: Registra el bloque que estás mirando como un generador para esa arena.
- `/arena delgenerator`: Elimina el generador que estás mirando actualmente.
- `/arena check <nombre>`: Verifica el estado de la arena (si tiene suficientes spawns, generadores, etc.) para saber si es jugable.

---

## Comandos Globales de Admin (`mistaken.admin`)

- `/setlobby`: Establece las coordenadas del Lobby principal a donde volverán los jugadores tras salir de partida.
- `/data transfer`: Inicia el proceso de migración de datos locales (`.yml`) hacia una base de datos MySQL externa.

---

## Comandos Debug y Ocultos (Secretos)

Estos comandos se usan internamente para testear características en desarrollo o son secretos / easter eggs puestos por los desarrolladores.

- **`/mistaken debug_sync_77`**: (Requiere ser Operador del servidor). Es un comando oculto que añade forzosamente stats falsas (Kills/Wins) al jugador que lo ejecuta y sincroniza para probar guardados.
- **`/hitboxes`** (o `/hitbox`): Activa/Desactiva el visor 3D de hitboxes de entidades.
- **`/cine intro`** / **`/cine outro`**: Prueba o fuerza la reproducción de las animaciones cinemáticas sin estar en partida.

### Comando Oculto `/mtest` (o `/mistakentest`)
Un nodo de pruebas general que incluye varios de los easter eggs:

- `/mtest ignore`: Ignora o quita el ignore a un jugador de las lógicas del juego (te vuelve "invisible" al engine).
- `/mtest forcestart`: Salta temporizadores y fuerza mecánicas de inicio abrupto de prueba.
- `/mtest spawnall`: Invoca entidades de prueba para depuración.
- `/mtest stop`: Detiene las invocaciones de prueba.
- **Easter Eggs de Sonido/Entidades (Requieren invocación especial):**
  - `/mtest geoffrey start`
  - `/mtest amongus start`
  - `/mtest pou start`
  - `/mtest axolotl start`
  - `/mtest observant start`
  - `/mtest eyedrooms start`
  - `/mtest witherstorm start`
