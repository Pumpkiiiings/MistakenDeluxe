


# 🔪 Mistaken API v2.0 - Documentación Oficial

Bienvenido a la documentación oficial de la API de **Mistaken 2.0**. Esta API permite a los desarrolladores interactuar con el ciclo de juego, consultar estados de los jugadores y registrar contenido personalizado (como nuevos Asesinos) sin necesidad de modificar el código fuente original del minijuego.

---

## 🚀 Empezando

Para utilizar la API de Mistaken en tu plugin (Addon), debes asegurarte de añadir `Mistaken` como dependencia en tu archivo `plugin.yml` o `paper-plugin.yml`:

```yaml
name: MiAddonMistaken
version: 1.0
main: org.miaddon.Main
depend: [Mistaken]
```

## 🔌 Obteniendo la Instancia de la API

La API funciona bajo el patrón **Singleton**. Nunca debes intentar instanciar `MistakenAPI` directamente. En su lugar, utiliza el método estático `getInstance()`.

```kotlin
val api = MistakenAPI.getInstance()
```
> **⚠️ Advertencia:** Este método lanzará un `IllegalStateException` si se intenta llamar antes de que Mistaken termine de cargar en el servidor. Asegúrate de llamarlo dentro de tu `onEnable()`.

---

## 📚 Referencia de Métodos

### 🛠️ Registro de Contenido Custom

#### `registerCustomAssassin(asesino: Asesino)`
Permite registrar un Asesino totalmente nuevo y funcional en el catálogo de Mistaken.

* **Parámetros:**
    * `asesino`: Una instancia de una clase que herede de la clase abstracta `liric.mistaken.roles.asesinos.Asesino`.
* **Comportamiento:**
    * El ID del asesino se convertirá a minúsculas automáticamente.
    * Si ya existe un asesino con el mismo ID en el juego base u otro Addon, el registro será rechazado (con una advertencia en consola) para evitar sobrescribir datos de forma accidental.

---

### 🎮 Utilidades de Partida y Jugadores

#### `isGameRunning(): Boolean`
Comprueba el estado del gestor de partidas.
* **Retorna:** `true` si la partida está actualmente en curso (estado `INGAME`). `false` si está en Lobby, Votación, Iniciando o Terminando.

#### `isAssassin(player: Player): Boolean`
Verifica si el jugador proporcionado es el asesino en la partida actual.
* **Parámetros:**
    * `player`: El jugador de Bukkit/Paper a verificar.
* **Retorna:** `true` si es el asesino activo. `false` si es un superviviente, espectador o no está jugando.

#### `getCurrentAssassinPlayer(): Player?`
Obtiene la entidad física del asesino actual de la partida.
* **Retorna:** El objeto `Player` del asesino si está online y en partida. Retorna `null` si no hay partida activa, o si el asesino se ha desconectado.

---

## ⚠️ Consideraciones de Desarrollo

1. **Thread-Safety:** Evita registrar clases de Asesinos de forma asíncrona. Hazlo siempre durante el hilo principal en el `onEnable` de tu plugin.
2. **Archivos de Configuración:** Si registras un asesino custom, asegúrate de instruir a los administradores de los servidores que deben agregar la configuración de tu asesino (`habilidad1_cooldown`, `armadura.casco`, etc.) en el archivo `asesinos.yml` y `asesinos_info.yml` de la carpeta principal de Mistaken, utilizando el ID exacto que le diste a tu clase.

---

## 💻 Ejemplos Prácticos

### Ejemplo 1: Consultar el estado del juego
Cómo usar la API para evitar que un comando se ejecute si la partida ya empezó.

```kotlin
import liric.mistaken.api.MistakenAPI
import org.bukkit.entity.Player

fun castigarSiElJuegoEmpezo(player: Player) {
    val api = MistakenAPI.getInstance()
    
    if (api.isGameRunning()) {
        player.sendMessage("¡No puedes usar este comando mientras Mistaken está en curso!")
        return
    }
}
```

### Ejemplo 2: Crear un Addon con un Asesino Nuevo

**Paso 1: Crear la clase del Asesino**
El desarrollador externo crea la clase de su asesino heredando de `Asesino`.

```kotlin
package org.miaddon.clases

import liric.mistaken.roles.asesinos.Asesino
import org.bukkit.entity.Player

// Hereda de la clase base Asesino, definiendo un ID ("payaso") y un nombre visual con MiniMessage.
class PayasoAsesino : Asesino("payaso", "<red><b>El Payaso</b></red>") {

    override fun equipar(player: Player) {
        // Tu propia lógica de equipamiento usando la API de Bukkit
        player.sendMessage("¡Jajaja! Ahora eres el payaso.")
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        // La API base de Mistaken ya provee checkCooldown y reproducirEfectosHabilidad de forma nativa.
        if (slot == 1 && !checkCooldown(player, 1)) {
            player.sendMessage("¡Usaste la broma pesada!")
            reproducirEfectosHabilidad(player, 1)
        }
    }

    override fun mostrarTrail(player: Player) {
        // Aquí puedes colocar código para generar partículas custom
    }
}
```

**Paso 2: Registrarlo en el Main del Addon**
En el archivo principal del plugin, inyecta la nueva clase usando la API.

```kotlin
package org.miaddon

import org.bukkit.plugin.java.JavaPlugin
import liric.mistaken.api.MistakenAPI
import org.miaddon.clases.PayasoAsesino

class MiAddonMain : JavaPlugin() {

    override fun onEnable() {
        // 1. Obtenemos la API
        try {
            val mistakenApi = MistakenAPI.getInstance()
            
            // 2. Registramos el nuevo asesino en el catálogo de Mistaken
            mistakenApi.registerCustomAssassin(PayasoAsesino())
            
            logger.info("¡PayasoAsesino registrado exitosamente en Mistaken!")
        } catch (e: IllegalStateException) {
            logger.severe("¡No se encontró la API de Mistaken! Asegúrate de tener el plugin base instalado.")
        }
    }
}
```
```
