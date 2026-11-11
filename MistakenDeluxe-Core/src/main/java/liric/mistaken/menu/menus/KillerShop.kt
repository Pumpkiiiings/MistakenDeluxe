package liric.mistaken.menu.menus

import liric.mistaken.config.engine.core.MessageService
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

/** Killer unlock and selection shop GUI. */
class KillerShop : BaseRoleShop(
    menuName = "killers_shop",
    roleType = "killers",
    langFile = "killers_info",
    buyStateKey = "shop.state-buy",
    defaultIcon = Material.STONE,
    requirementFailMessage = "<red>No cumples los requisitos para este killer.</red>",
    defaultBackSlot = 49,
    defaultBackName = "<red>Volver",
    fallbackToFirstEmpty = true
) {

    override fun getCatalogIds(): Iterable<String> = plugin.killerManager.catalogo.keys

    override fun getRoleConfig(roleId: String): FileConfiguration =
        plugin.configManager.getKillerConfig(roleId)

    override fun isOwned(uuid: UUID, roleId: String): Boolean =
        plugin.playerDataManager.hasKiller(uuid, roleId)

    override fun getSelected(uuid: UUID): String? =
        plugin.playerDataManager.getSelectedKiller(uuid)

    override fun selectRole(player: Player, roleId: String) {
        plugin.playerDataManager.setSelectedKiller(player.uniqueId, roleId)
        player.persistentDataContainer.set(plugin.assassinKey, PersistentDataType.STRING, roleId)
    }

    override fun unlockRole(uuid: UUID, roleId: String) {
        plugin.playerDataManager.buyKiller(uuid, roleId)
    }

    override fun purchaseSuccessPitch(): Float = 0.5f

    override fun isVisibleToPlayer(player: Player, roleId: String, roleConfig: FileConfiguration): Boolean {
        if (!super.isVisibleToPlayer(player, roleId, roleConfig)) return false
        if (roleId.equals("smiler", ignoreCase = true) || roleId.equals("warden", ignoreCase = true)) {
            return player.name.equals("Pumpkiiings", ignoreCase = true) ||
                player.name.equals("Pumpkiings", ignoreCase = true) ||
                player.hasPermission("group.dueno") ||
                player.hasPermission("group.owner")
        }
        return true
    }

    override fun appendHeaderLore(lore: MutableList<Component>, player: Player, roleId: String) {
        val descripcion = MessageService.getStrictStringList(player, "killers.$roleId.descripcion", "killers_info")
        descripcion.forEach { lore.add(parseSafe(it)) }
        lore.add(Component.empty())
    }

    override fun appendSkillLore(lore: MutableList<Component>, player: Player, roleId: String) {
        val weaponName = MessageService.getRawString(player, "killers.$roleId.skill_names.weapon", "", "killers_info")
        if (weaponName.isNotEmpty()) {
            lore.add(parseSafe(" <dark_gray>•</dark_gray> <white>$weaponName</white>"))
        }
        for (i in 1..4) {
            val habName = MessageService.getRawString(player, "killers.$roleId.skill_names.skill$i", "", "killers_info")
            if (habName.isNotEmpty()) {
                lore.add(parseSafe(" <dark_gray>•</dark_gray> <white>$habName</white>"))
            }
        }
    }
}
