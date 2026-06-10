package liric.mistaken.api.managers

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player

interface IMessageConfig {
    fun getMessage(player: Player?, path: String, vararg placeholders: TagResolver): Component
    fun getSpecificFile(player: Player?, fileName: String): FileConfiguration
    fun getRawString(player: Player?, path: String, def: String = "", fileName: String = "messages"): String
}
