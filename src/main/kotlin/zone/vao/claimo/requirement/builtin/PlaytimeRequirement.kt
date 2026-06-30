package zone.vao.claimo.requirement.builtin

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import zone.vao.claimo.config.Messages
import zone.vao.claimo.requirement.Requirement
import zone.vao.claimo.requirement.RequirementContext
import zone.vao.claimo.requirement.RequirementResult
import zone.vao.claimo.stats.StatsService
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
            Placeholder.parsed("played", formatDuration(played)),
            Placeholder.parsed("required", formatDuration(requiredSeconds)),
        )
        val result = if (played >= requiredSeconds) {
            RequirementResult.satisfied(description)
        } else {
            RequirementResult.unsatisfied(description)
        }
        return CompletableFuture.completedFuture(result)
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return buildString {
            if (h > 0) append("${h}h ")
            if (h > 0 || m > 0) append("${m}m ")
            append("${s}s")
        }
    }
}
