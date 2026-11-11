package liric.mistaken.roles.killers.classes

import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound

/** Inferno-themed Charlie killer variant. */
class CharlieInferno : AbstractCharlieKiller(
    id = "charlie",
    defaultMusic = "mistaken:charlieinferno",
    theme = CharlieTheme(
        orbitMaterials = listOf(Material.MAGMA_BLOCK, Material.PACKED_ICE),
        novaHitbox = Material.ORANGE_STAINED_GLASS,
        novaTargetParticle = Particle.LAVA,
        novaTintR = 255,
        novaTintG = 30,
        novaTintB = 0,
        novaSelfPrimaryParticle = Particle.FLAME,
        novaSelfSecondaryParticle = Particle.CAMPFIRE_COSY_SMOKE,
        demonHitbox = Material.GRAY_STAINED_GLASS,
        demonTargetParticle = Particle.SOUL_FIRE_FLAME,
        demonTintR = 0,
        demonTintG = 0,
        demonTintB = 0,
        projectileItem = Material.PACKED_ICE,
        projectileHitbox = Material.LIGHT_BLUE_STAINED_GLASS,
        projectileTrailParticle = Particle.SNOWFLAKE,
        projectileBreakBlock = Material.ICE,
        projectileBreakSound = Sound.BLOCK_GLASS_BREAK,
        fangsPrimaryParticle = Particle.FLAME,
        fangsSecondaryParticle = Particle.SMOKE,
        fangsHitbox = Material.RED_STAINED_GLASS,
        trailParticle = Particle.FLAME
    )
)
