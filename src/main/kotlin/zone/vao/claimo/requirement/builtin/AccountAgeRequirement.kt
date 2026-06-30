package zone.vao.claimo.requirement.builtin

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import zone.vao.claimo.config.Messages
import zone.vao.claimo.requirement.Requirement
import zone.vao.claimo.requirement.RequirementContext
import zone.vao.claimo.requirement.RequirementResult
import java.util.concurrent.CompletableFuture

class AccountAgeRequirement(
    private val messages: Messages,
    private val requiredDays: Long,
) : Requirement {

    override fun check(context: RequirementContext): CompletableFuture<RequirementResult> {
        val firstPlayed = context.player.firstPlayed
        val daysPlayed = if (firstPlayed > 0L) {
            (System.currentTimeMillis() - firstPlayed) / MILLIS_PER_DAY
        } else {
            0L
        }
        val description = messages.line(
            "requirement-account-age",
            Placeholder.parsed("current", daysPlayed.toString()),
            Placeholder.parsed("required", requiredDays.toString()),
        )
        val result = if (daysPlayed >= requiredDays) {
            RequirementResult.satisfied(description)
        } else {
            RequirementResult.unsatisfied(description)
        }
        return CompletableFuture.completedFuture(result)
    }

    private companion object {
        const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1000L
    }
}
