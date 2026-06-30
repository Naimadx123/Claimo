package zone.vao.claimo.requirement

import net.kyori.adventure.text.Component

class RequirementResult private constructor(
    val satisfied: Boolean,
    val description: Component,
) {
    companion object {
        /** The requirement is met. [description] is shown in the requirement checklist. */
        fun satisfied(description: Component): RequirementResult = RequirementResult(true, description)

        /** The requirement is not met. [description] is shown in the requirement checklist. */
        fun unsatisfied(description: Component): RequirementResult = RequirementResult(false, description)
    }
}
