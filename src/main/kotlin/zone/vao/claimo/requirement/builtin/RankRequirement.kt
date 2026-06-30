package zone.vao.claimo.requirement.builtin

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.milkbowl.vault.permission.Permission
import org.bukkit.Bukkit
import zone.vao.claimo.config.Messages
import zone.vao.claimo.requirement.Requirement
import zone.vao.claimo.requirement.RequirementContext
import zone.vao.claimo.requirement.RequirementResult
import java.util.concurrent.CompletableFuture

class RankRequirement(
    private val messages: Messages,
    private val required: List<String>,
    private val denied: List<String>,
) : Requirement {

    override fun check(context: RequirementContext): CompletableFuture<RequirementResult> {
        val player = context.player
        val permission = vaultPermission()

        val inRequired = required.isEmpty() ||
            (permission != null && required.any { it.isNotBlank() && permission.playerInGroup(player, it) })
        val inDenied = permission != null && denied.any { it.isNotBlank() && permission.playerInGroup(player, it) }

        val description = messages.line("requirement-rank", Placeholder.parsed("rank", describe(required, denied)))
        val result = if (inRequired && !inDenied) {
            RequirementResult.satisfied(description)
        } else {
            RequirementResult.unsatisfied(description)
        }
        return CompletableFuture.completedFuture(result)
    }

    private fun vaultPermission(): Permission? {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) return null
        return Bukkit.getServicesManager().getRegistration(Permission::class.java)?.provider
    }
}
