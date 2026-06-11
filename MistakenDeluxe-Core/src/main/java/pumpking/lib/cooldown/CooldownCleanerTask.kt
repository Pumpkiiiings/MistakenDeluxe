package pumpking.lib.cooldown

class CooldownCleanerTask : Runnable {
    override fun run() {
        val now = System.currentTimeMillis()
        var cleaned = 0

        val iterator = CooldownManager.cooldowns.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val playerCooldowns = entry.value

            val cdIterator = playerCooldowns.entries.iterator()
            while (cdIterator.hasNext()) {
                val cdEntry = cdIterator.next()
                if (now >= cdEntry.value.expiresAt) {
                    cdIterator.remove()
                    cleaned++
                }
            }

            if (playerCooldowns.isEmpty()) {
                iterator.remove()
            }
        }

        if (cleaned > 0) {
            // Uncomment if you want to see cleanup in console
            // PumpkingLib.log(PumpkingLib.LogCategory.COOLDOWN, "CLEANUP EVENT OK - Removed $cleaned expired cooldowns.")
        }
    }
}
