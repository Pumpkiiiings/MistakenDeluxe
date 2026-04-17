
---

# 🛠️ Development Guide: Command Registration

In **Mistaken 2.0**, command registration is centralized in the `CommandRegistry` class. Because we utilize the **Brigadier** API integrated into Paper, the process varies slightly depending on the complexity of the command.

## 📂 Structure and Location

To maintain clean code, all new commands must follow this hierarchy:

* **Registry Path:** `liric.mistaken.commands.CommandRegistry`
* **Class Path:** `liric.mistaken.commands.<category>.<Name>Command`

> [!IMPORTANT]
> **Where to register it?**
> To add a new command, you must navigate to the `CommandRegistry` class and add your entry inside the `manager.registerEventHandler(LifecycleEvents.COMMANDS)` block.

---

## 🚀 Steps to Register a New Command

### 1. Define the Command Type
There are two ways to implement commands in the system:

#### A. Complex Commands (Brigadier)
If your command requires complex sub-arguments or dynamic suggestions (e.g., `/arena create <name>`):
1.  Create the class implementing a structure that returns a `LiteralCommandNode`.
2.  In `CommandRegistry`, add it to **Group A**:
    ```kotlin
    registrar.register(YourNewCommand.get(plugin), "Short description", listOf("alias1"))
    ```

#### B. Basic Commands (`BasicCommand`)
For simple commands without deeply nested argument logic:
1.  Create a class that extends `BasicCommand`.
2.  In `CommandRegistry`, add it to **Group B**:
    ```kotlin
    registrar.register("name", "Description", listOf("alias"), YourCommandClass(plugin))
    ```

---

## 📝 Practical Example (Subfolder `dev`)

If you are creating a command for development tools, place it in `liric.mistaken.commands.dev`:

```kotlin
// 1. Create the command in liric.mistaken.commands.dev.DevToolCommand
class DevToolCommand(private val plugin: Mistaken) : BasicCommand {
    override fun execute(stack: CommandSourceStack, args: Array<String>) {
        stack.sender.sendMessage("Dev Tool activated!")
    }
}

// 2. Go to CommandRegistry.kt and add:
registrar.register("devtool", "Dev utility command", DevToolCommand(plugin))
```

---

## ⚠️ Technical Considerations

* **Lifecycle:** Commands are registered via the `LifecycleEvents.COMMANDS` event. This ensures that commands are correctly injected into the Paper engine during startup.
* **Kotlin Versioning:** If using Kotlin **1.9.24** or lower, ensure that the `.get(plugin)` method for Brigadier commands explicitly returns a `LiteralCommandNode`. If it returns a `Builder`, remember to call `.build()`.
* **Synchronization:** Command registration is a synchronous operation that occurs once during plugin loading. Do not attempt to register commands asynchronously at runtime.