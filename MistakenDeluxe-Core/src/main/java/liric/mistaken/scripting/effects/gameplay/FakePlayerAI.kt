package liric.mistaken.scripting.effects.gameplay

import liric.mistaken.Mistaken
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import java.util.UUID

/**
 * Motor genérico de navegación y física (Movement/AI).
 * Maneja gravedad, saltos, evasión de obstáculos y huida de enemigos.
 */
class FakePlayerAI(
    private val ownerUuid: UUID,
    initialLoc: Location,
    private val fleeRadiusSq: Double?,
    private val catchRadiusSq: Double?,
    private val onCaught: (() -> Unit)?
) {
    private val plugin = JavaPlugin.getPlugin(Mistaken::class.java)
    
    val currentLoc = initialLoc.clone()
    var currentYaw = initialLoc.yaw
    
    private var targetYaw = initialLoc.yaw
    private var velocityY = 0.0
    private var lastJumpTick = 0
    private var stuckCheckLoc = initialLoc.clone()
    var isFleeing = false
        private set
    var isOnGround = false
        private set

    fun tick(tickCount: Int) {
        if (tickCount > 0 && tickCount % 20 == 0) {
            if (currentLoc.distanceSquared(stuckCheckLoc) < 0.8) {
                targetYaw += 180f + (Math.random() * 90 - 45).toFloat()
                if (velocityY <= 0) velocityY = 0.5
            }
            stuckCheckLoc = currentLoc.clone()
        }

        val isSolidBlock = { b: org.bukkit.block.Block ->
            if (!b.type.isSolid) false
            else {
                val data = b.blockData
                if (data is org.bukkit.block.data.Openable) {
                    !data.isOpen
                } else {
                    !b.type.name.contains("SIGN")
                }
            }
        }

        isFleeing = false
        if (fleeRadiusSq != null && catchRadiusSq != null) {
            for (viewer in plugin.server.onlinePlayers) {
                if (viewer.world != currentLoc.world) continue
                
                val owner = plugin.server.getPlayer(ownerUuid) ?: continue
                if (GameplayFunctions.isEnemy(owner, viewer)) {
                    val dist = viewer.location.distanceSquared(currentLoc)
                    if (dist < catchRadiusSq) {
                        onCaught?.invoke()
                        return
                    } else if (dist < fleeRadiusSq) {
                        val fleeDir = currentLoc.clone().subtract(viewer.location).toVector().setY(0).normalize()
                        if (fleeDir.lengthSquared() > 0) {
                            targetYaw = Math.toDegrees(Math.atan2(-fleeDir.x, fleeDir.z)).toFloat()
                            isFleeing = true
                            if (Math.random() > 0.85 && velocityY <= 0.0) velocityY = 0.42
                        }
                    }
                }
            }
        }

        if (!isFleeing && tickCount % 10 == 0 && Math.random() > 0.2) {
            targetYaw += (Math.random() * 40 - 20).toFloat()
        }

        var yawDiff = (targetYaw - currentYaw) % 360
        if (yawDiff > 180) yawDiff -= 360
        if (yawDiff < -180) yawDiff += 360
        currentYaw += yawDiff * (if (isFleeing) 0.3f else 0.15f)

        val direction = Vector(
            -Math.sin(Math.toRadians(currentYaw.toDouble())),
            0.0,
            Math.cos(Math.toRadians(currentYaw.toDouble()))
        ).normalize()

        isOnGround = currentLoc.clone().subtract(0.0, 0.1, 0.0).block.type.isSolid || currentLoc.y <= Math.floor(currentLoc.y) + 0.1

        if (isOnGround && tickCount - lastJumpTick > 15) {
            val jumpChance = if (isFleeing) 0.40 else 0.85
            if (Math.random() > jumpChance) {
                velocityY = 0.42
                lastJumpTick = tickCount
            }
        }

        val eyeLoc = currentLoc.clone().add(0.0, 1.0, 0.0)
        val getFreeDistance = { dirVector: Vector ->
            var dist = 0.0
            while (dist < 4.0) {
                val b = eyeLoc.clone().add(dirVector.clone().multiply(dist)).block
                if (dist < 1.5 && b.blockData is org.bukkit.block.data.Openable) {
                    val openable = b.blockData as org.bukkit.block.data.Openable
                    if (!openable.isOpen) {
                        plugin.server.regionScheduler.execute(plugin, b.location) {
                            openable.isOpen = true
                            b.blockData = openable
                            b.world.playSound(b.location, Sound.BLOCK_WOODEN_DOOR_OPEN, 1f, 1f)
                        }
                    }
                }
                if (isSolidBlock(b)) break
                dist += 0.5
            }
            dist
        }

        val frontDist = getFreeDistance(direction)
        val leftDir = direction.clone().rotateAroundY(Math.toRadians(45.0))
        val rightDir = direction.clone().rotateAroundY(Math.toRadians(-45.0))
        val leftDist = getFreeDistance(leftDir)
        val rightDist = getFreeDistance(rightDir)

        if (frontDist < 1.5) {
            if (leftDist > rightDist && leftDist > 1.5) {
                targetYaw -= 35f
            } else if (rightDist > leftDist && rightDist > 1.5) {
                targetYaw += 35f
            } else {
                targetYaw += 60f
            }
            
            if (frontDist < 0.8 && isOnGround) {
                val footBlock = currentLoc.clone().add(direction.clone().multiply(0.8)).block
                val headBlock = footBlock.getRelative(0, 1, 0)
                if (isSolidBlock(footBlock) && !isSolidBlock(headBlock)) {
                    velocityY = 0.42
                }
            }
        } else {
            if (leftDist < 1.2) targetYaw += 20f
            if (rightDist < 1.2) targetYaw -= 20f
        }

        velocityY -= 0.08
        if (velocityY < -0.8) velocityY = -0.8
        
        val baseSpeed = if (isFleeing) 0.32 else 0.26
        val speed = if (!isOnGround) baseSpeed + 0.05 else baseSpeed
        
        val moveX = direction.x * speed
        val moveZ = direction.z * speed
        
        val nextXLoc = currentLoc.clone().add(moveX, 0.2, 0.0)
        if (!isSolidBlock(nextXLoc.block) && !isSolidBlock(nextXLoc.clone().add(0.0, 1.0, 0.0).block)) {
            currentLoc.x += moveX
        } else {
            targetYaw += if (Math.random() > 0.5) 50f else -50f
        }

        val nextZLoc = currentLoc.clone().add(0.0, 0.2, moveZ)
        if (!isSolidBlock(nextZLoc.block) && !isSolidBlock(nextZLoc.clone().add(0.0, 1.0, 0.0).block)) {
            currentLoc.z += moveZ
        } else {
            targetYaw += if (Math.random() > 0.5) 50f else -50f
        }

        currentLoc.y += velocityY
        if (velocityY < 0 && currentLoc.block.type.isSolid) {
            currentLoc.y = currentLoc.block.y + 1.0
            velocityY = 0.0
        } else if (velocityY > 0 && currentLoc.clone().add(0.0, 1.8, 0.0).block.type.isSolid) {
            velocityY = -0.08
        }

        currentLoc.yaw = currentYaw
    }
}
