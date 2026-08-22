package liric.mistaken.utils

import dev.triumphteam.gui.builder.item.ItemBuilder
import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration

object MenuUtils {

    fun createConfigItem(config: FileConfiguration, basePath: String, defaultMat: Material): ItemBuilder {
        val matStr = config.getString("$basePath.material")
        val mat = if (matStr != null) {
            runCatching { Material.valueOf(matStr.uppercase()) }.getOrDefault(defaultMat)
        } else {
            defaultMat
        }
        
        val builder = ItemBuilder.from(mat)
        
        if (config.contains("$basePath.model_data")) {
            builder.model(config.getInt("$basePath.model_data"))
        }
        
        return builder
    }
}
