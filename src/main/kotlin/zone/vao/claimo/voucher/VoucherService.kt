package zone.vao.claimo.voucher

import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import zone.vao.claimo.Claimo
import zone.vao.claimo.util.Durations
import zone.vao.claimo.event.PlayerRedeemVoucherEvent
import zone.vao.claimo.event.VoucherRedeemedEvent
import zone.vao.claimo.requirement.RequirementContext
import zone.vao.claimo.requirement.RequirementResult
import zone.vao.claimo.reward.RewardAction
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class VoucherService(private val plugin: Claimo) {

    private val pendingConfirms = ConcurrentHashMap<UUID, Pair<String, Long>>()
    private val rewardActions = ConcurrentHashMap<String, RewardAction>()

    fun redeem(
        player: Player,
        voucherId: String,
        confirmed: Boolean = false,
        onSuccess: (() -> Unit)? = null,
    ): CompletableFuture<RedeemResult> {
        val result = CompletableFuture<RedeemResult>()
        val config = plugin.configManager.config
        val messages = config.messages

        val voucher = config.vouchers[voucherId]
        if (voucher == null) {
            messages.send(player, "no-such-voucher", Placeholder.parsed("voucher", voucherId))
            return result.apply { complete(RedeemResult.NOT_FOUND) }
        }

        if (voucher.disabled) {
            messages.send(player, "code-disabled", Placeholder.parsed("voucher", voucherId))
            return result.apply { complete(RedeemResult.DISABLED) }
        }

        if (voucher.isExpired()) {
            messages.send(player, "code-expired", Placeholder.parsed("voucher", voucherId))
            return result.apply { complete(RedeemResult.EXPIRED) }
        }

        if (voucher.isNotStarted()) {
            messages.send(
                player, "code-not-started",
                Placeholder.parsed("voucher", voucherId),
                Placeholder.parsed("remaining", Durations.humanize(voucher.startsAt!! - System.currentTimeMillis())),
            )
            return result.apply { complete(RedeemResult.NOT_STARTED) }
        }

        cooldownRemaining(player, voucher)?.let { remaining ->
            messages.send(
                player, "code-cooldown",
                Placeholder.parsed("voucher", voucherId),
                Placeholder.parsed("remaining", Durations.humanize(remaining)),
            )
            return result.apply { complete(RedeemResult.ON_COOLDOWN) }
        }

        if (plugin.usageService.isExhausted(player, voucher)) {
            sendLimitMessage(player, voucher)
            return result.apply { complete(RedeemResult.LIMIT_REACHED) }
        }

        checkPrice(player, voucher)?.let { return result.apply { complete(it) } }
        if (voucher.price > 0.0 && !confirmed && !confirmPrice(player, voucher, onSuccess)) {
            return result.apply { complete(RedeemResult.AWAITING_CONFIRMATION) }
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
            player.scheduler.run(
                plugin,
                { completeRedeem(player, voucher, checks, onSuccess, result) },
                { result.complete(RedeemResult.PLAYER_OFFLINE) },
            )
        }
        return result
    }

    private fun completeRedeem(
        player: Player,
        voucher: Voucher,
        checks: List<CompletableFuture<RequirementResult>>,
        onSuccess: (() -> Unit)?,
        result: CompletableFuture<RedeemResult>,
    ) {
        if (!player.isOnline) {
            result.complete(RedeemResult.PLAYER_OFFLINE)
            return
        }
        val messages = plugin.configManager.config.messages

        if (plugin.usageService.isExhausted(player, voucher)) {
            sendLimitMessage(player, voucher)
            result.complete(RedeemResult.LIMIT_REACHED)
            return
        }

        cooldownRemaining(player, voucher)?.let { remaining ->
            messages.send(
                player, "code-cooldown",
                Placeholder.parsed("voucher", voucher.id),
                Placeholder.parsed("remaining", Durations.humanize(remaining)),
            )
            result.complete(RedeemResult.ON_COOLDOWN)
            return
        }

        val results = checks.map { it.join() }
        if (results.any { !it.satisfied }) {
            messages.send(player, "requirements-not-met", Placeholder.parsed("voucher", voucher.id))
            results.forEach { check ->
                val key = if (check.satisfied) "requirement-met" else "requirement-unmet"
                player.sendMessage(messages.line(key, Placeholder.component("description", check.description)))
            }
            result.complete(RedeemResult.REQUIREMENTS_NOT_MET)
            return
        }

        if (!PlayerRedeemVoucherEvent(player, voucher).callEvent()) {
            result.complete(RedeemResult.CANCELLED)
            return
        }

        if (!plugin.usageService.tryRecord(player, voucher)) {
            sendLimitMessage(player, voucher)
            result.complete(RedeemResult.LIMIT_REACHED)
            return
        }

        chargePrice(player, voucher)?.let {
            plugin.usageService.release(player, voucher)
            result.complete(it)
            return
        }

        execute(player, voucher)
        plugin.usageService.recordHistory(player, voucher)
        if (voucher.cooldownMillis != null) {
            player.persistentDataContainer.set(cooldownKey(voucher.id), PersistentDataType.LONG, System.currentTimeMillis())
        }
        plugin.configManager.config.redeemSound.sound?.let(player::playSound)
        runCatching { RedeemEffects.play(player, voucher.effects) }.onFailure {
            plugin.logger.warning("Failed to play redeem effects for '${voucher.id}': ${it.message}")
        }
        VoucherRedeemedEvent(player, voucher).callEvent()
        messages.send(player, "success", Placeholder.parsed("voucher", voucher.id))
        onSuccess?.invoke()
        result.complete(RedeemResult.SUCCESS)
    }

    private fun sendLimitMessage(player: Player, voucher: Voucher) {
        val poolDepleted = voucher.limitMode == LimitMode.GLOBAL &&
            (plugin.usageService.globalUses(voucher.id) >= voucher.limitAmount ||
                plugin.usageService.playerUses(player, voucher.id) == 0)
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

    fun cooldownRemaining(player: Player, voucherId: String): Long {
        val voucher = plugin.configManager.config.vouchers[voucherId] ?: return 0L
        return cooldownRemaining(player, voucher) ?: 0L
    }

    fun clearCooldowns(player: Player, voucherIds: Collection<String>) {
        for (id in voucherIds) player.persistentDataContainer.remove(cooldownKey(id))
    }

    fun registerRewardAction(name: String, action: RewardAction) {
        rewardActions[name.trim().lowercase()] = action
    }

    fun unregisterRewardAction(name: String) {
        rewardActions.remove(name.trim().lowercase())
    }

    private fun confirmPrice(player: Player, voucher: Voucher, onSuccess: (() -> Unit)?): Boolean {
        val priceText = economy()?.format(voucher.price) ?: voucher.price.toString()
        plugin.priceConfirm?.let {
            it.open(player, voucher.id, priceText, onSuccess)
            return false
        }
        val pending = pendingConfirms[player.uniqueId]
        if (pending != null && pending.first == voucher.id &&
            System.currentTimeMillis() - pending.second < CONFIRM_WINDOW_MS
        ) {
            pendingConfirms.remove(player.uniqueId)
            return true
        }
        pendingConfirms[player.uniqueId] = voucher.id to System.currentTimeMillis()
        plugin.configManager.config.messages.send(
            player, "confirm-price",
            Placeholder.parsed("voucher", voucher.id),
            Placeholder.parsed("price", priceText),
        )
        return false
    }

    private fun checkPrice(player: Player, voucher: Voucher): RedeemResult? {
        if (voucher.price <= 0.0) return null
        val messages = plugin.configManager.config.messages
        val economy = economy()
        if (economy == null) {
            plugin.logger.warning("Voucher '${voucher.id}' has a price but no Vault economy provider is installed.")
            messages.send(player, "price-unavailable", Placeholder.parsed("voucher", voucher.id))
            return RedeemResult.PAYMENT_UNAVAILABLE
        }
        if (!economy.has(player, voucher.price)) {
            messages.send(
                player, "not-enough-money",
                Placeholder.parsed("voucher", voucher.id),
                Placeholder.parsed("price", economy.format(voucher.price)),
            )
            return RedeemResult.CANNOT_AFFORD
        }
        return null
    }

    private fun chargePrice(player: Player, voucher: Voucher): RedeemResult? {
        if (voucher.price <= 0.0) return null
        val messages = plugin.configManager.config.messages
        val economy = economy() ?: run {
            messages.send(player, "price-unavailable", Placeholder.parsed("voucher", voucher.id))
            return RedeemResult.PAYMENT_UNAVAILABLE
        }
        val response = economy.withdrawPlayer(player, voucher.price)
        if (!response.transactionSuccess()) {
            messages.send(
                player, "not-enough-money",
                Placeholder.parsed("voucher", voucher.id),
                Placeholder.parsed("price", economy.format(voucher.price)),
            )
            return RedeemResult.CANNOT_AFFORD
        }
        return null
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
            if (command.startsWith("action:", ignoreCase = true)) {
                runAction(player, voucher, command)
                continue
            }
            plugin.server.dispatchCommand(sender, command)
        }
    }

    private fun runAction(player: Player, voucher: Voucher, command: String) {
        val name = command.substringAfter(':').substringBefore(' ').trim().lowercase()
        val args = command.substringAfter(' ', "").trim()
        val action = rewardActions[name]
        if (action == null) {
            plugin.logger.warning("Voucher '${voucher.id}' uses unknown reward action '$name'.")
            return
        }
        runCatching { action.execute(player, args) }.onFailure {
            plugin.logger.warning("Reward action '$name' on voucher '${voucher.id}' threw: ${it.message}")
        }
    }

    private companion object {
        const val CONFIRM_WINDOW_MS = 10_000L
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
