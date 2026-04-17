
---

# 🛠️ Guía de Desarrollo: Registro de Comandos

En **Mistaken 2.0**, el registro de comandos se centraliza en la clase `CommandRegistry`. Debido a que utilizamos la API de **Brigadier** integrada en Paper, el proceso varía ligeramente dependiendo de la complejidad del comando.

## 📂 Estructura y Ubicación

Para mantener el código limpio, todos los comandos nuevos deben seguir esta jerarquía:

* **Ruta del Registro:** `liric.mistaken.commands.CommandRegistry`
* **Ruta de Clases:** `liric.mistaken.commands.<categoría>.<Nombre>Command`

> [!IMPORTANT]
> **¿Dónde registrarlo?**
> Para añadir un comando nuevo, debes ir a la clase `CommandRegistry` y añadirlo dentro del bloque `manager.registerEventHandler(LifecycleEvents.COMMANDS)`.

---

## 🚀 Pasos para registrar un comando nuevo

### 1. Definir el tipo de comando
Existen dos formas de implementar comandos en el sistema:

#### A. Comandos Complejos (Brigadier)
Si tu comando requiere sub-argumentos complejos o sugerencias dinámicas (ej: `/arena create <name>`):
1.  Crea la clase implementando una estructura que devuelva un `LiteralCommandNode`.
2.  En `CommandRegistry`, añádelo en el **Grupo A**:
    ```kotlin
    registrar.register(TuNuevoComando.get(plugin), "Descripción corta", listOf("alias1"))
    ```

#### B. Comandos Básicos (`BasicCommand`)
Para comandos simples sin una lógica de argumentos profundamente anidada:
1.  Crea una clase que extienda de `BasicCommand`.
2.  En `CommandRegistry`, añádelo en el **Grupo B**:
    ```kotlin
    registrar.register("nombre", "Descripción", listOf("alias"), TuComandoClase(plugin))
    ```

---

## 📝 Ejemplo Práctico (Subcarpeta `dev`)

Si estás creando un comando de herramientas de desarrollo, colócalo en `liric.mistaken.commands.dev`:

```kotlin
// 1. Crear el comando en liric.mistaken.commands.dev.DevToolCommand
class DevToolCommand(private val plugin: Mistaken) : BasicCommand {
    override fun execute(stack: CommandSourceStack, args: Array<String>) {
        stack.sender.sendMessage("¡Herramienta Dev activada!")
    }
}

// 2. Ir a CommandRegistry.kt y añadir:
registrar.register("devtool", "Comando de utilidad dev", DevToolCommand(plugin))
```

---

## ⚠️ Consideraciones Técnicas

* **Lifecycle:** Los comandos se registran mediante el evento `LifecycleEvents.COMMANDS`. Esto garantiza que los comandos sean inyectados correctamente en el motor de Paper durante el arranque.
* **Versionado de Kotlin:** Si se utiliza Kotlin **1.9.24** o inferior, asegúrate de que el método `.get(plugin)` de los comandos de Brigadier devuelva explícitamente un `LiteralCommandNode`. Si devuelve un `Builder`, recuerda invocar `.build()`.
* **Sincronización:** El registro de comandos es una operación síncrona que ocurre una sola vez durante la carga del plugin. No intentes registrar comandos en tiempo de ejecución de forma asíncrona.