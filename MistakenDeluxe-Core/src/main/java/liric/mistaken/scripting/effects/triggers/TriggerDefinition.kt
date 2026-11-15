package liric.mistaken.scripting.effects.triggers

data class TriggerDefinition(
    val triggerId: String,
    val input: InputTrigger,
    val cooldownSeconds: Int
)
