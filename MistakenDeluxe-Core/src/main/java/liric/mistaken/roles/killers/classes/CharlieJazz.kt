package liric.mistaken.roles.killers.classes

import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound

/** Jazz-themed Charlie killer variant. */
class CharlieJazz : AbstractCharlieKiller(
    id = "charliejazz",
    defaultMusic = "mistaken:charliejazz",
    theme = CharlieTheme(
        orbitMaterials = listOf(Material.AMETHYST_BLOCK, Material.GOLD_BLOCK),
        novaHitbox = Material.PURPLE_STAINED_GLASS,
        novaTargetParticle = Particle.FIREWORK,
        novaTintR = 150,
        novaTintG = 0,
        novaTintB = 255,
        novaSelfPrimaryParticle = Particle.END_ROD,
        novaSelfSecondaryParticle = Particle.PORTAL,
        demonHitbox = Material.YELLOW_STAINED_GLASS,
        demonTargetParticle = Particle.WAX_ON,
        demonTintR = 200,
        demonTintG = 150,
        demonTintB = 0,
        projectileItem = Material.AMETHYST_BLOCK,
        projectileHitbox = Material.PURPLE_STAINED_GLASS,
        projectileTrailParticle = Particle.END_ROD,
        projectileBreakBlock = Material.AMETHYST_BLOCK,
        projectileBreakSound = Sound.BLOCK_AMETHYST_BLOCK_BREAK,
        fangsPrimaryParticle = Particle.END_ROD,
        fangsSecondaryParticle = Particle.END_ROD,
        fangsHitbox = Material.MAGENTA_STAINED_GLASS,
        trailParticle = Particle.END_ROD
    )
)
