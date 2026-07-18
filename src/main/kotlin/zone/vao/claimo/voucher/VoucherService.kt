package zone.vao.claimo.voucher

import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.entity.Player
import zone.vao.claimo.Claimo
import zone.vao.claimo.event.PlayerRedeemVoucherEvent
import zone.vao.claimo.event.VoucherRedeemedEvent
import zone.vao.claimo.requirement.RequirementContext
import zone.vao.claimo.requirement.RequirementResult
import java.util.concurrent.CompletableFuture

class VoucherService(private val plugin: Claimo) {

    fun redeem(player: Player, voucherId: String) {
        val config = plugin.configManager.config
        val messages = config.messages

        val voucher = config.vouchers[voucherId]
        if (voucher == null) {
            messages.send(player, "no-such-voucher", Placeholder.parsed("voucher", voucherId))
            return
        }

        if (voucher.isExpired()) {
            messages.send(player, "code-expired", Placeholder.parsed("voucher", voucherId))
            return
        }

        if (plugin.usageService.isExhausted(player, voucher)) {
            sendLimitMessage(player, voucher)
            return
        }

        val context = RequirementContext(player, voucherId)
        val checks = voucher.requirements.map { spec ->
            val requirement = plugin.requirementRegistry.create(spec)
            if (requirement == null) {
                plugin.logger.warning("Voucher '$voucherId' references unknown requirement type '${spec.type}'.")
                CompletableFuture.completedFuture(
                    RequirementResult.unsatisfied(
                        messages.line("requirement-unavailable", Placeholder.parsed("type", spec.type))
                    )
                )
            } else {
                requirement.check(context).exceptionally { ex ->
                    plugin.logger.warning("Requirement '${spec.type}' on voucher '$voucherId' threw: ${ex.message}")
                    RequirementResult.unsatisfied(messages.line("requirement-error"))
                }
            }
        }

        CompletableFuture.allOf(*checks.toTypedArray()).whenComplete { _, _ ->
            player.scheduler.run(plugin, { completeRedeem(player, voucher, checks) }, null)
        }
    }

    private fun completeRedeem(
        player: Player,
        voucher: Voucher,
        checks: List<CompletableFuture<RequirementResult>>,
    ) {
        if (!player.isOnline) return
        val messages = plugin.configManager.config.messages

        if (plugin.usageService.isExhausted(player, voucher)) {
            sendLimitMessage(player, voucher)
            return
        }

        val results = checks.map { it.join() }
        if (results.any { !it.satisfied }) {
            messages.send(player, "requirements-not-met", Placeholder.parsed("voucher", voucher.id))
            results.forEach { result ->
                val key = if (result.satisfied) "requirement-met" else "requirement-unmet"
                player.sendMessage(messages.line(key, Placeholder.component("description", result.description)))
            }
            return
        }

        if (!PlayerRedeemVoucherEvent(player, voucher).callEvent()) return

        execute(player, voucher)
        plugin.usageService.record(player, voucher)
        plugin.configManager.config.redeemSound.sound?.let(player::playSound)
        VoucherRedeemedEvent(player, voucher).callEvent()
        messages.send(player, "success", Placeholder.parsed("voucher", voucher.id))
    }

    private fun sendLimitMessage(player: Player, voucher: Voucher) {
        val poolDepleted = voucher.limitMode == LimitMode.GLOBAL &&
            plugin.usageService.globalUses(voucher.id) >= voucher.limitAmount
        val key = if (poolDepleted) "code-depleted" else "already-used"
        plugin.configManager.config.messages.send(player, key, Placeholder.parsed("voucher", voucher.id))
    }

    private fun execute(player: Player, voucher: Voucher) {
        val sender = if (voucher.console) plugin.server.consoleSender else player
        val papi = plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")
        for (rawCommand in voucher.commands) {
            var command = rawCommand.removePrefix("/").replace("%player", player.name)
            if (papi) command = PlaceholderAPI.setPlaceholders(player, command)
            if (command.isBlank()) continue
            plugin.server.dispatchCommand(sender, command)
        }
    }
}
