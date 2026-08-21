package liric.mistaken.api.roles

interface ISurvivor : GameRole {
    fun useSkill(player: org.bukkit.entity.Player, slot: Int)
}
