package zone.vao.claimo.requirement.builtin

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import zone.vao.claimo.config.Messages
import zone.vao.claimo.requirement.Requirement
import zone.vao.claimo.requirement.RequirementContext
import zone.vao.claimo.requirement.RequirementResult
import zone.vao.claimo.stats.StatsService
import zone.vao.claimo.util.Durations
import java.util.concurrent.CompletableFuture

class PlaytimeRequirement(
    private val stats: StatsService,
    private val messages: Messages,
    private val requiredSeconds: Long,
) : Requirement {

    override fun check(context: RequirementContext): CompletableFuture<RequirementResult> {
        val played = stats.playtimeSeconds(context.player)
        val description = messages.line(
            "requirement-playtime",
            Placeholder.parsed("played", Durations.humanize(played * 1000L)),
            Placeholder.parsed("required", Durations.humanize(requiredSeconds * 1000L)),
        )
        val result = if (played >= requiredSeconds) {
            RequirementResult.satisfied(description)
        } else {
            RequirementResult.unsatisfied(description)
        }
        return CompletableFuture.completedFuture(result)
    }
}
