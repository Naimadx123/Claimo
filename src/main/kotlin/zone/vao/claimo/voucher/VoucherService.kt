package zone.vao.claimo.voucher

import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.milkbowl.vault.economy.Economy
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import zone.vao.claimo.Claimo
import zone.vao.claimo.util.Durations
import zone.vao.claimo.event.PlayerRedeemVoucherEvent
import zone.vao.claimo.event.VoucherRedeemedEvent
import zone.vao.claimo.requirement.RequirementContext
import zone.vao.claimo.requirement.RequirementResult
import java.util.concurrent.CompletableFuture

class VoucherService(private val plugin: Claimo) {

    fun redeem(player: Player, voucherId: String, onSuccess: (() -> Unit)? = null) {
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

        if (voucher.isNotStarted()) {
            messages.send(
                player, "code-not-started",
                Placeholder.parsed("voucher", voucherId),
                Placeholder.parsed("remaining", Durations.humanize(voucher.startsAt!! - System.currentTimeMillis())),
            )
            return
        }

        cooldownRemaining(player, voucher)?.let { remaining ->
            messages.send(
                player, "code-cooldown",
                Placeholder.parsed("voucher", voucherId),
                Placeholder.parsed("remaining", Durations.humanize(remaining)),
            )
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
            player.scheduler.run(plugin, { completeRedeem(player, voucher, checks, onSuccess) }, null)
        }
    }

    private fun completeRedeem(
        player: Player,
        voucher: Voucher,
        checks: List<CompletableFuture<RequirementResult>>,
        onSuccess: (() -> Unit)?,
    ) {
        if (!player.isOnline) return
        val messages = plugin.configManager.config.messages

        if (plugin.usageService.isExhausted(player, voucher)) {
            sendLimitMessage(player, voucher)
            return
        }

        cooldownRemaining(player, voucher)?.let { remaining ->
            messages.send(
                player, "code-cooldown",
                Placeholder.parsed("voucher", voucher.id),
                Placeholder.parsed("remaining", Durations.humanize(remaining)),
            )
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

        if (!chargePrice(player, voucher)) return
        execute(player, voucher)
        plugin.usageService.record(player, voucher)
        if (voucher.cooldownMillis != null) {
            player.persistentDataContainer.set(cooldownKey(voucher.id), PersistentDataType.LONG, System.currentTimeMillis())
        }
        plugin.configManager.config.redeemSound.sound?.let(player::playSound)
        VoucherRedeemedEvent(player, voucher).callEvent()
        messages.send(player, "success", Placeholder.parsed("voucher", voucher.id))
        onSuccess?.invoke()
    }

    private fun sendLimitMessage(player: Player, voucher: Voucher) {
        val poolDepleted = voucher.limitMode == LimitMode.GLOBAL &&
            plugin.usageService.globalUses(voucher.id) >= voucher.limitAmount
        val key = if (poolDepleted) "code-depleted" else "already-used"
        plugin.configManager.config.messages.send(player, key, Placeholder.parsed("voucher", voucher.id))
    }

    private fun cooldownRemaining(player: Player, voucher: Voucher): Long? {
        val cooldown = voucher.cooldownMillis ?: return null
        val lastUsed = player.persistentDataContainer.get(cooldownKey(voucher.id), PersistentDataType.LONG) ?: return null
        val remaining = lastUsed + cooldown - System.currentTimeMillis()
        return if (remaining > 0) remaining else null
    }

    private fun cooldownKey(voucherId: String) = NamespacedKey(plugin, "cooldown-$voucherId")

    private fun checkPrice(player: Player, voucher: Voucher): Boolean {
        if (voucher.price <= 0.0) return true
        val messages = plugin.configManager.config.messages
        val economy = economy()
        if (economy == null) {
            plugin.logger.warning("Voucher '${voucher.id}' has a price but no Vault economy provider is installed.")
            messages.send(player, "price-unavailable", Placeholder.parsed("voucher", voucher.id))
            return false
        }
        if (!economy.has(player, voucher.price)) {
            messages.send(
                player, "not-enough-money",
                Placeholder.parsed("voucher", voucher.id),
                Placeholder.parsed("price", economy.format(voucher.price)),
            )
            return false
        }
        return true
    }

    private fun chargePrice(player: Player, voucher: Voucher): Boolean {
        if (voucher.price <= 0.0) return true
        val messages = plugin.configManager.config.messages
        val economy = economy() ?: run {
            messages.send(player, "price-unavailable", Placeholder.parsed("voucher", voucher.id))
            return false
        }
        val response = economy.withdrawPlayer(player, voucher.price)
        if (!response.transactionSuccess()) {
            messages.send(
                player, "not-enough-money",
                Placeholder.parsed("voucher", voucher.id),
                Placeholder.parsed("price", economy.format(voucher.price)),
            )
            return false
        }
        return true
    }

    private fun economy(): Economy? {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) return null
        return Bukkit.getServicesManager().getRegistration(Economy::class.java)?.provider
    }

    private fun execute(player: Player, voucher: Voucher) {
        val sender = if (voucher.console) plugin.server.consoleSender else player
        val papi = plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")
        val commands = if (voucher.random) listOfNotNull(pickRandom(voucher)) else voucher.commands
        for (rawCommand in commands) {
            var command = rawCommand.removePrefix("/").replace("%player%", player.name)
            if (papi) command = PlaceholderAPI.setPlaceholders(player, command)
            if (command.isBlank()) continue
            plugin.server.dispatchCommand(sender, command)
        }
    }

    private fun pickRandom(voucher: Voucher): String? {
        val commands = voucher.commands
        if (commands.isEmpty()) return null
        val chances = voucher.commandChances
        if (chances.size != commands.size || chances.sum() <= 0.0) return commands.random()
        var roll = Math.random() * chances.sum()
        for (i in commands.indices) {
            roll -= chances[i]
            if (roll <= 0) return commands[i]
        }
        return commands.last()
    }
}
