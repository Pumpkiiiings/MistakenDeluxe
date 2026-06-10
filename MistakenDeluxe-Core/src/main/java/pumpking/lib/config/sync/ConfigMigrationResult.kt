package pumpking.lib.config.sync

data class ConfigMigrationResult(
    val fileTarget: String,
    val missingPathsAdded: Int,
    val migratedFromVersion: Int,
    val migratedToVersion: Int,
    val isUpdated: Boolean
)
