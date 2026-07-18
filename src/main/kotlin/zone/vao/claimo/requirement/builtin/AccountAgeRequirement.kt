package zone.vao.claimo.requirement.builtin

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import zone.vao.claimo.config.Messages
import zone.vao.claimo.requirement.Requirement
import zone.vao.claimo.requirement.RequirementContext
import zone.vao.claimo.requirement.RequirementResult
import zone.vao.claimo.util.Durations
import java.util.concurrent.CompletableFuture

class AccountAgeRequirement(
    private val messages: Messages,
    private val requiredMillis: Long,
) : Requirement {

    override fun check(context: RequirementContext): CompletableFuture<RequirementResult> {
        val firstPlayed = context.player.firstPlayed
        val ageMillis = if (firstPlayed > 0L) System.currentTimeMillis() - firstPlayed else 0L
        val description = messages.line(
            "requirement-account-age",
            Placeholder.parsed("current", Durations.humanize(ageMillis)),
            Placeholder.parsed("required", Durations.humanize(requiredMillis)),
        )
        val result = if (ageMillis >= requiredMillis) {
            RequirementResult.satisfied(description)
        } else {
            RequirementResult.unsatisfied(description)
        }
        return CompletableFuture.completedFuture(result)
    }
}
