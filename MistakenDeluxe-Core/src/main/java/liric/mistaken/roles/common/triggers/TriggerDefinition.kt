package liric.mistaken.roles.common.triggers

data class TriggerDefinition(
    val triggerId: String,
    val input: InputTrigger,
    val cooldownSeconds: Int
)
