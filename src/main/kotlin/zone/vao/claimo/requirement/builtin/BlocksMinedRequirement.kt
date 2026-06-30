package zone.vao.claimo.requirement.builtin

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Material
import zone.vao.claimo.config.Messages
import zone.vao.claimo.requirement.Requirement
import zone.vao.claimo.requirement.RequirementContext
import zone.vao.claimo.requirement.RequirementResult
import zone.vao.claimo.stats.StatsService
import java.util.concurrent.CompletableFuture

class BlocksMinedRequirement(
    private val stats: StatsService,
    private val messages: Messages,
    private val amount: Int,
    private val whitelist: Set<Material> = emptySet(),
    private val blacklist: Set<Material> = emptySet(),
) : Requirement {

    override fun check(context: RequirementContext): CompletableFuture<RequirementResult> {
        val mined = stats.blocksMined(context.player, whitelist, blacklist)
        val description = messages.line(
            "requirement-blocks-mined",
            Placeholder.parsed("mined", mined.toString()),
            Placeholder.parsed("amount", amount.toString()),
        )
        val result = if (mined >= amount) {
            RequirementResult.satisfied(description)
        } else {
            RequirementResult.unsatisfied(description)
        }
        return CompletableFuture.completedFuture(result)
    }
}
