package zone.vao.claimo.requirement

fun interface RequirementFactory {

    /**
     * Builds a [Requirement] from its [config]. Read type-specific parameters off
     * [config] here. Throwing (e.g. on a missing field) makes the requirement
     * report as failed for that redeem attempt rather than crashing the command.
     */
    fun create(config: RequirementConfig): Requirement
}
