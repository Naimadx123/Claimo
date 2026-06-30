package zone.vao.claimo.requirement

/**
 * Describes one configurable field of a requirement type so the in-game code creator can
 * render an input for it and write the chosen value back under [key] in the voucher file.
 *
 * Register descriptors alongside the factory via
 * `ClaimoApi.registerRequirement(type, factory, inputs)`; a type with no descriptors still
 * appears in the creator as a simple on/off toggle.
 */
sealed interface RequirementInput {

    /** The voucher-config key this input writes to (e.g. `amount`). */
    val key: String

    /** The label shown next to the input in the creator. */
    val label: String

    /** A numeric slider; the chosen value is written as an integer when whole, otherwise a decimal. */
    data class NumberInput(
        override val key: String,
        override val label: String,
        val min: Double,
        val max: Double,
        val step: Double = 1.0,
        val initial: Double = 0.0,
    ) : RequirementInput

    /** A free-text field. */
    data class TextInput(
        override val key: String,
        override val label: String,
        val maxLength: Int = 64,
        val initial: String = "",
    ) : RequirementInput

    /** A boolean toggle. */
    data class BoolInput(
        override val key: String,
        override val label: String,
        val initial: Boolean = false,
    ) : RequirementInput
}
