package liric.mistaken.menu.menus

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import liric.mistaken.Mistaken
import liric.mistaken.api.requirements.RequirementEngine
import liric.mistaken.config.engine.core.MessageService
import liric.mistaken.menu.MenuBase
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import java.util.UUID

/**
 * Shared parent menu for role unlock and selection shops ([KillerShop], [SurvivorShop]).
 */
abstract class BaseRoleShop(
    menuName: String,
    protected val roleType: String,
    protected val langFile: String,
    private val buyStateKey: String,
    private val defaultIcon: Material,
    private val requirementFailMessage: String,
    private val defaultBackSlot: Int,
    private val defaultBackName: String,
    private val fallbackToFirstEmpty: Boolean
) : MenuBase(menuName) {

    protected abstract fun getCatalogIds(): Iterable<String>
    protected abstract fun getRoleConfig(roleId: String): FileConfiguration
    protected abstract fun isOwned(uuid: UUID, roleId: String): Boolean
    protected abstract fun getSelected(uuid: UUID): String?
    protected abstract fun selectRole(player: Player, roleId: String)
    protected abstract fun unlockRole(uuid: UUID, roleId: String)

    protected open fun isVisibleToPlayer(player: Player, roleId: String, roleConfig: FileConfiguration): Boolean {
        val perm = roleConfig.getString("permiso")
        return perm == null || player.hasPermission(perm)
    }

    protected open fun appendHeaderLore(lore: MutableList<Component>, player: Player, roleId: String) {}
    protected open fun appendSkillLore(lore: MutableList<Component>, player: Player, roleId: String) {}
    protected open fun purchaseSuccessPitch(): Float = 1.0f
    protected open fun resolveNamePlaceholder(player: Player, roleId: String, visualName: String): TagResolver {
        return Placeholder.parsed("name", roleId)
    }

    override fun setupItems(player: Player, gui: Gui, config: FileConfiguration) {
        val preferredSlots = config.getIntegerList("settings.available-slots").toMutableList()
        val fixedSlots = mutableMapOf<String, Int>()
        config.getConfigurationSection("settings.fixed-slots")?.let { section ->
            for (key in section.getKeys(false)) {
                fixedSlots[key.lowercase()] = section.getInt(key)
            }
        }

        if (!fallbackToFirstEmpty && preferredSlots.isEmpty() && fixedSlots.isEmpty()) {
            addBackButton(player, gui, config)
            return
        }

        val uuid = player.uniqueId
        val selected = getSelected(uuid)

        val labelSelected = MessageService.getComponent(player, "shop.state-selected")
        val labelOwned = MessageService.getComponent(player, "shop.state-owned")
        val labelBuy = MessageService.getComponent(player, buyStateKey)
        val labelAbilities = MessageService.getComponent(player, "shop.abilities-title")

        for (roleId in getCatalogIds()) {
            val roleConfig = getRoleConfig(roleId)
            if (!isVisibleToPlayer(player, roleId, roleConfig)) continue

            val targetSlot = resolveNextSlot(roleId, fixedSlots, preferredSlots, gui) ?: continue

            val visualName = MessageService.getStrictString(player, "$roleType.$roleId.name", langFile)
            val loreShop = MessageService.getStrictStringList(player, "$roleType.$roleId.lore_shop", langFile)
            val price = roleConfig.getInt("precio", 0)
            val matStr = roleConfig.getString("icono_material", defaultIcon.name)!!
            val iconMat = Material.matchMaterial(matStr.uppercase()) ?: defaultIcon

            val fullLore = mutableListOf<Component>()
            appendHeaderLore(fullLore, player, roleId)
            loreShop.forEach { fullLore.add(parseSafe(it)) }
            fullLore.add(Component.empty())
            fullLore.add(labelAbilities)
            appendSkillLore(fullLore, player, roleId)
            fullLore.add(Component.empty())

            val owned = isOwned(uuid, roleId)
            val isSelected = selected.equals(roleId, ignoreCase = true)

            val reqMessages = RequirementEngine.getRequirementMessages(player, roleType, roleId)
            reqMessages.forEach { fullLore.add(parseSafe(it)) }

            when {
                isSelected -> fullLore.add(labelSelected)
                owned -> fullLore.add(labelOwned)
                else -> {
                    fullLore.add(MessageService.getComponent(player, "shop.state-price", Placeholder.parsed("amount", price.toString())))
                    fullLore.add(labelBuy)
                }
            }

            val guiItem = buildRoleGuiItem(iconMat, visualName, fullLore) {
                if (reqMessages.isNotEmpty()) {
                    player.sendMessage(parseSafe(requirementFailMessage))
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f)
                    return@buildRoleGuiItem
                }
                handlePurchaseOrSelect(player, roleId, visualName, price, owned)
            }

            gui.setItem(targetSlot, guiItem)
        }

        addBackButton(player, gui, config)
    }

    private fun resolveNextSlot(
        roleId: String,
        fixedSlots: Map<String, Int>,
        preferredSlots: MutableList<Int>,
        gui: Gui
    ): Int? {
        val slot = when {
            fixedSlots.containsKey(roleId.lowercase()) -> fixedSlots.getValue(roleId.lowercase())
            preferredSlots.isNotEmpty() -> preferredSlots.removeAt(0)
            fallbackToFirstEmpty -> gui.inventory.firstEmpty()
            else -> -1
        }
        return if (slot == -1) null else slot
    }

    protected fun buildRoleGuiItem(
        material: Material,
        displayName: String,
        lore: List<Component>,
        onClick: () -> Unit
    ): GuiItem {
        return ItemBuilder.from(material)
            .name(parseSafe(displayName))
            .lore(lore)
            .asGuiItem { event ->
                event.isCancelled = true
                onClick()
            }.also { it.itemStack.editMeta { meta -> meta.addItemFlags(*ItemFlag.entries.toTypedArray()) } }
    }

    private fun addBackButton(player: Player, gui: Gui, config: FileConfiguration) {
        val backSlot = config.getInt("settings.back-button.slot", defaultBackSlot)
        val backMatStr = config.getString("settings.back-button.material", "ARROW")!!
        val backName = config.getString("settings.back-button.name", defaultBackName)!!
        val backMat = Material.matchMaterial(backMatStr.uppercase()) ?: Material.ARROW

        val backItem = ItemBuilder.from(backMat)
            .name(parseSafe(backName))
            .asGuiItem { event ->
                event.isCancelled = true
                ShopSelector().abrir(player)
            }.also { it.itemStack.editMeta { meta -> meta.addItemFlags(*ItemFlag.entries.toTypedArray()) } }

        gui.setItem(backSlot, backItem)
    }

    private fun handlePurchaseOrSelect(
        player: Player,
        roleId: String,
        visualName: String,
        price: Int,
        owned: Boolean
    ) {
        val uuid = player.uniqueId
        val actual = getSelected(uuid)

        if (roleId.equals(actual, ignoreCase = true)) {
            player.sendMessage(MessageService.getComponent(player, "shop.already-selected"))
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f)
            return
        }

        if (owned) {
            selectRole(player, roleId)
            player.sendMessage(MessageService.getComponent(player, "shop.selected", resolveNamePlaceholder(player, roleId, visualName)))
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f)
            abrir(player)
            return
        }

        val econ = Mistaken.Companion.economy
        if (econ == null) {
            player.sendMessage(parseSafe("<red><b>[!]</b> Error interno: El sistema de economía no está conectado.</red>"))
            plugin.componentLogger.error("Purchase failed due to disconnected Economy: Player ${player.name}, Role $roleId ($roleType)")
            return
        }

        val cost = price.toDouble()
        if (econ.getBalance(player) >= cost) {
            if (econ.withdraw(player, cost)) {
                unlockRole(uuid, roleId)
                player.sendMessage(MessageService.getComponent(player, "shop.purchased", resolveNamePlaceholder(player, roleId, visualName)))
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, purchaseSuccessPitch())
                abrir(player)
            } else {
                player.sendMessage(MessageService.getComponent(player, "shop_errores.error_bancario", Placeholder.parsed("error", "Transacción fallida.")))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f)
            }
        } else {
            player.sendMessage(MessageService.getComponent(player, "errors.no-money"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f)
        }
    }
}
