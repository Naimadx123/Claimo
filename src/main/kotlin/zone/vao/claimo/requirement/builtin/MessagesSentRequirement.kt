package zone.vao.claimo.requirement.builtin

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import zone.vao.claimo.config.Messages
import zone.vao.claimo.requirement.Requirement
import zone.vao.claimo.requirement.RequirementContext
import zone.vao.claimo.requirement.RequirementResult
import zone.vao.claimo.stats.MessagePolicy
import zone.vao.claimo.stats.StatsService
import java.util.concurrent.CompletableFuture

class MessagesSentRequirement(
    private val stats: StatsService,
    private val messages: Messages,
    private val amount: Int,
    private val policy: MessagePolicy,
) : Requirement {

    override fun check(context: RequirementContext): CompletableFuture<RequirementResult> {
        val sent = stats.messagesSent(context.player, policy.minLength, policy.delaySeconds)
        val description = messages.line(
            "requirement-messages-sent",
            Placeholder.parsed("sent", sent.toString()),
            Placeholder.parsed("amount", amount.toString()),
            Placeholder.parsed("length", policy.minLength.toString()),
        )
        val result = if (sent >= amount) {
            RequirementResult.satisfied(description)
        } else {
            RequirementResult.unsatisfied(description)
        }
        return CompletableFuture.completedFuture(result)
    }
}
