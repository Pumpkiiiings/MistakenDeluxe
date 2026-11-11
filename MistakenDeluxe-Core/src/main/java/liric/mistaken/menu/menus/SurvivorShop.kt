package liric.mistaken.menu.menus

import liric.mistaken.config.engine.core.MessageService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

/** Survivor unlock and selection shop GUI. */
class SurvivorShop : BaseRoleShop(
    menuName = "survivors_shop",
    roleType = "survivors",
    langFile = "survivors_info",
    buyStateKey = "shop.state-buy-survivor",
    defaultIcon = Material.IRON_CHESTPLATE,
    requirementFailMessage = "<red>No cumples los requisitos para este survivor.</red>",
    defaultBackSlot = 40,
    defaultBackName = "Back",
    fallbackToFirstEmpty = false
) {

    private val survivorKey by lazy { NamespacedKey(plugin, "selected_survivor") }

    override fun getCatalogIds(): Iterable<String> = plugin.survivorManager.catalogo.keys

    override fun getRoleConfig(roleId: String): FileConfiguration =
        plugin.configManager.getSurvivorConfig(roleId)

    override fun isOwned(uuid: UUID, roleId: String): Boolean =
        plugin.playerDataManager.tieneSurvivor(uuid, roleId)

    override fun getSelected(uuid: UUID): String? =
        plugin.playerDataManager.getSelectedSurvivor(uuid)

    override fun selectRole(player: Player, roleId: String) {
        plugin.playerDataManager.setSelectedSurvivor(player.uniqueId, roleId)
        player.persistentDataContainer.set(survivorKey, PersistentDataType.STRING, roleId)
    }

    override fun unlockRole(uuid: UUID, roleId: String) {
        plugin.playerDataManager.comprarSurvivor(uuid, roleId)
    }

    override fun resolveNamePlaceholder(player: Player, roleId: String, visualName: String): TagResolver {
        return Placeholder.component("name", parseSafe(visualName))
    }

    override fun appendHeaderLore(lore: MutableList<Component>, player: Player, roleId: String) {
        lore.add(MessageService.getComponent(player, "shop.human-class"))
        lore.add(Component.empty())
    }

    override fun appendSkillLore(lore: MutableList<Component>, player: Player, roleId: String) {
        for (i in 1..3) {
            val habName = MessageService.getRawString(player, "survivors.$roleId.skill_names.skill$i", "", "survivors_info")
            if (habName.isNotEmpty()) {
                lore.add(parseSafe(" <dark_gray>•</dark_gray> <white>$habName</white>"))
            }
        }
    }
}
