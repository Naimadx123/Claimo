package zone.vao.claimo.requirement.builtin

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import zone.vao.claimo.config.Messages
import zone.vao.claimo.requirement.Requirement
import zone.vao.claimo.requirement.RequirementContext
import zone.vao.claimo.requirement.RequirementResult
import java.util.concurrent.CompletableFuture

class PermissionRequirement(
    private val messages: Messages,
    private val required: List<String>,
    private val denied: List<String>,
) : Requirement {

    override fun check(context: RequirementContext): CompletableFuture<RequirementResult> {
        val player = context.player
        val hasRequired = required.isEmpty() || required.any { it.isNotBlank() && player.hasPermission(it) }
        val hasDenied = denied.any { it.isNotBlank() && player.hasPermission(it) }

        val description = messages.line("requirement-permission", Placeholder.parsed("permission", describe(required, denied)))
        val result = if (hasRequired && !hasDenied) {
            RequirementResult.satisfied(description)
        } else {
            RequirementResult.unsatisfied(description)
        }
        return CompletableFuture.completedFuture(result)
    }
}

internal fun describe(required: List<String>, denied: List<String>): String = buildString {
    if (required.isNotEmpty()) append(required.joinToString(", "))
    if (denied.isNotEmpty()) {
        if (isNotEmpty()) append("; ")
        append("not ").append(denied.joinToString(", "))
    }
}
