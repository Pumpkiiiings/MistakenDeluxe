package liric.mistaken.roles.killers.triggers

data class TriggerDefinition(
    val triggerId: String,
    val input: InputTrigger,
    val cooldownSeconds: Int
)
