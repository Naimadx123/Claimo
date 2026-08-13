package zone.vao.claimo.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.entity.Player
import zone.vao.claimo.Claimo
import zone.vao.claimo.creator.VoucherCreator
import zone.vao.claimo.util.Durations
import zone.vao.claimo.voucher.LimitMode
import java.util.concurrent.ConcurrentHashMap

@Suppress("UnstableApiUsage")
object VoucherCommand {

    fun build(plugin: Claimo, commandName: String): LiteralCommandNode<CommandSourceStack> =
        Commands.literal(commandName)
            .executes { ctx ->
                val sender = ctx.source.sender
                if (!sender.hasPermission("claimo.use")) {
                    plugin.configManager.config.messages.send(sender, "no-permission")
                    return@executes Command.SINGLE_SUCCESS
                }
                if (sender is Player && plugin.configManager.config.guiListEnabled) {
                    plugin.voucherMenu.open(sender, 0)
                } else {
                    plugin.configManager.config.messages.send(
                        sender,
                        "usage",
                        Placeholder.parsed("command", commandName),
                    )
                }
                Command.SINGLE_SUCCESS
            }
            .then(
                adminLiteral("reload")
                    .executes { ctx ->
                        plugin.reload()
                        plugin.configManager.config.messages.send(ctx.source.sender, "reloaded")
                        Command.SINGLE_SUCCESS
                    }
            )
            .then(
                adminLiteral("purge")
                    .executes { ctx ->
                        val messages = plugin.configManager.config.messages
                        val purged = plugin.usageService.purgeExcept(plugin.configManager.config.vouchers.keys)
                        if (purged.isNotEmpty()) {
                            for (player in plugin.server.onlinePlayers) {
                                player.scheduler.run(plugin, { plugin.voucherService.clearCooldowns(player, purged) }, null)
                            }
                            plugin.actionLog.admin("${ctx.source.sender.name} purged ${purged.size} deleted code(s): ${purged.joinToString(", ")}")
                        }
                        messages.send(ctx.source.sender, "purged", Placeholder.parsed("amount", purged.size.toString()))
                        Command.SINGLE_SUCCESS
                    }
            )
            .then(
                adminLiteral("create")
                    .executes { ctx ->
                        val sender = ctx.source.sender
                        val messages = plugin.configManager.config.messages
                        when {
                            sender !is Player -> messages.send(sender, "players-only")
                            plugin.voucherCreator == null -> messages.send(sender, "creator-unavailable")
                            else -> plugin.voucherCreator?.open(sender)
                        }
                        Command.SINGLE_SUCCESS
                    }
            )
            .then(
                adminLiteral("give")
                    .then(
                        Commands.argument("players", ArgumentTypes.players())
                            .then(
                                Commands.argument("voucher", StringArgumentType.word())
                                    .suggests { _, builder ->
                                        val input = builder.remaining.lowercase()
                                        plugin.configManager.config.vouchers.values
                                            .filter { it.item != null && it.id.lowercase().startsWith(input) }
                                            .forEach { builder.suggest(it.id) }
                                        builder.buildFuture()
                                    }
                                    .executes { ctx -> giveItems(plugin, ctx, 1) }
                                    .then(
                                        Commands.argument("amount", IntegerArgumentType.integer(1, 2304))
                                            .executes { ctx ->
                                                giveItems(plugin, ctx, IntegerArgumentType.getInteger(ctx, "amount"))
                                            }
                                    )
                            )
                    )
            )
            .then(
                adminLiteral("giveoffline")
                    .then(
                        Commands.argument("player", StringArgumentType.word())
                            .then(
                                Commands.argument("voucher", StringArgumentType.word())
                                    .suggests { _, builder ->
                                        val input = builder.remaining.lowercase()
                                        plugin.configManager.config.vouchers.values
                                            .filter { it.item != null && it.id.lowercase().startsWith(input) }
                                            .forEach { builder.suggest(it.id) }
                                        builder.buildFuture()
                                    }
                                    .executes { ctx -> giveOffline(plugin, ctx, 1) }
                                    .then(
                                        Commands.argument("amount", IntegerArgumentType.integer(1, 2304))
                                            .executes { ctx ->
                                                giveOffline(plugin, ctx, IntegerArgumentType.getInteger(ctx, "amount"))
                                            }
                                    )
                            )
                    )
            )
            .then(
                adminLiteral("info")
                    .then(
                        Commands.argument("voucher", StringArgumentType.word())
                            .suggests { _, builder ->
                                val input = builder.remaining.lowercase()
                                plugin.configManager.config.vouchers.keys
                                    .filter { it.lowercase().startsWith(input) }
                                    .forEach(builder::suggest)
                                builder.buildFuture()
                            }
                            .executes { ctx -> sendInfo(plugin, ctx) }
                    )
            )
            .then(
                adminLiteral("generate")
                    .then(
                        Commands.argument("voucher", StringArgumentType.word())
                            .suggests { _, builder ->
                                val input = builder.remaining.lowercase()
                                plugin.configManager.config.vouchers.keys
                                    .filter { it.lowercase().startsWith(input) }
                                    .forEach(builder::suggest)
                                builder.buildFuture()
                            }
                            .then(
                                Commands.argument("amount", IntegerArgumentType.integer(1, 500))
                                    .executes { ctx -> generateCodes(plugin, ctx) }
                            )
                    )
            )
            .then(voucherAdminCommand(plugin, "edit") { creator, player, id -> creator.edit(player, id) })
            .then(voucherAdminCommand(plugin, "delete") { creator, player, id -> creator.delete(player, id) })
            .then(
                Commands.argument("voucher", StringArgumentType.word())
                    .suggests { ctx, builder ->
                        if (ctx.source.sender.hasPermission("claimo.use")) {
                            val input = builder.remaining.lowercase()
                            plugin.configManager.config.vouchers.values
                                .filter { !it.hidden && it.isAvailable() && it.id.lowercase().startsWith(input) }
                                .forEach { builder.suggest(it.id) }
                        }
                        builder.buildFuture()
                    }
                    .executes { ctx ->
                        val sender = ctx.source.sender
                        if (!sender.hasPermission("claimo.use")) {
                            plugin.configManager.config.messages.send(sender, "no-permission")
                            return@executes Command.SINGLE_SUCCESS
                        }
                        if (sender !is Player) {
                            plugin.configManager.config.messages.send(sender, "players-only")
                            return@executes Command.SINGLE_SUCCESS
                        }
                        val voucherId = StringArgumentType.getString(ctx, "voucher")
                        plugin.voucherService.redeem(sender, voucherId)
                        Command.SINGLE_SUCCESS
                    }
            )
            .build()

    private fun giveItems(plugin: Claimo, ctx: CommandContext<CommandSourceStack>, amount: Int): Int {
        val messages = plugin.configManager.config.messages
        val sender = ctx.source.sender
        val id = StringArgumentType.getString(ctx, "voucher")
        val voucher = plugin.configManager.config.vouchers[id]
        when {
            voucher == null -> messages.send(sender, "no-such-voucher", Placeholder.parsed("voucher", id))
            voucher.item == null -> messages.send(sender, "item-not-configured", Placeholder.parsed("voucher", id))
            else -> {
                val targets = ctx.getArgument("players", PlayerSelectorArgumentResolver::class.java).resolve(ctx.source)
                for (target in targets) {
                    target.scheduler.run(plugin, { plugin.voucherItemService.give(target, voucher, amount) }, null)
                    plugin.actionLog.admin("${sender.name} gave ${amount}x '$id' to ${target.name}")
                    messages.send(
                        sender,
                        "item-given",
                        Placeholder.parsed("voucher", id),
                        Placeholder.parsed("amount", amount.toString()),
                        Placeholder.parsed("player", target.name),
                    )
                }
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun giveOffline(plugin: Claimo, ctx: CommandContext<CommandSourceStack>, amount: Int): Int {
        val messages = plugin.configManager.config.messages
        val sender = ctx.source.sender
        val id = StringArgumentType.getString(ctx, "voucher")
        val name = StringArgumentType.getString(ctx, "player")
        val voucher = plugin.configManager.config.vouchers[id]
        when {
            voucher == null -> messages.send(sender, "no-such-voucher", Placeholder.parsed("voucher", id))
            voucher.item == null -> messages.send(sender, "item-not-configured", Placeholder.parsed("voucher", id))
            else -> {
                val resolvers = arrayOf(
                    Placeholder.parsed("voucher", id),
                    Placeholder.parsed("amount", amount.toString()),
                    Placeholder.parsed("player", name),
                )
                val online = plugin.server.getPlayerExact(name)
                if (online != null) {
                    online.scheduler.run(plugin, { plugin.voucherItemService.give(online, voucher, amount) }, null)
                    plugin.actionLog.admin("${sender.name} gave ${amount}x '$id' to ${online.name}")
                    messages.send(sender, "item-given", *resolvers)
                } else {
                    plugin.pendingGiveService.queue(name, id, amount)
                    plugin.actionLog.admin("${sender.name} queued ${amount}x '$id' for $name")
                    messages.send(sender, "item-queued", *resolvers)
                }
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun sendInfo(plugin: Claimo, ctx: CommandContext<CommandSourceStack>): Int {
        val messages = plugin.configManager.config.messages
        val sender = ctx.source.sender
        val id = StringArgumentType.getString(ctx, "voucher")
        val voucher = plugin.configManager.config.vouchers[id]
        if (voucher == null) {
            messages.send(sender, "no-such-voucher", Placeholder.parsed("voucher", id))
            return Command.SINGLE_SUCCESS
        }
        val now = System.currentTimeMillis()
        val lines = buildList {
            add("commands" to "${voucher.commands.size}${if (voucher.random) " (one at random)" else ""}")
            add("console" to voucher.console.toString())
            add("hidden" to voucher.hidden.toString())
            if (voucher.limitMode != LimitMode.NONE) {
                add("limit" to "${voucher.limitMode.name.lowercase().replace('_', '-')} ${voucher.limitAmount}")
            }
            voucher.redeemCommand?.let { add("redeem-command" to "/$it") }
            if (voucher.price > 0.0) add("price" to voucher.price.toString())
            voucher.cooldownMillis?.let { add("cooldown" to Durations.humanize(it)) }
            voucher.startsAt?.let {
                add("starts" to if (now < it) "in ${Durations.humanize(it - now)}" else "already active")
            }
            voucher.expiresAt?.let {
                add("expires" to if (now < it) "in ${Durations.humanize(it - now)}" else "expired")
            }
            voucher.item?.let { item ->
                add("item" to (item.material?.name ?: item.nexoItem ?: item.iaItem ?: item.ceItem ?: "PAPER"))
            }
            voucher.effects?.let { fx ->
                val parts = listOfNotNull(
                    if (fx.fireworks > 0) "${fx.fireworks} firework(s)" else null,
                    fx.particle?.let { "${it.name} (${fx.shape.name.lowercase()})" },
                )
                add("effects" to parts.joinToString(", "))
            }
            if (voucher.requirements.isNotEmpty()) {
                add("requirements" to voucher.requirements.joinToString(", ") { it.type })
            }
            add("global uses" to plugin.usageService.globalUses(voucher.id).toString())
        }
        messages.send(sender, "info-header", Placeholder.parsed("voucher", voucher.id))
        for ((key, value) in lines) {
            sender.sendMessage(
                messages.line("info-line", Placeholder.parsed("key", key), Placeholder.parsed("value", value))
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun generateCodes(plugin: Claimo, ctx: CommandContext<CommandSourceStack>): Int {
        val messages = plugin.configManager.config.messages
        val sender = ctx.source.sender
        val id = StringArgumentType.getString(ctx, "voucher")
        val amount = IntegerArgumentType.getInteger(ctx, "amount")
        val safeId = plugin.configManager.sanitizeId(id)
        if (safeId == null || !plugin.configManager.voucherExists(safeId)) {
            messages.send(sender, "creator-not-found", Placeholder.parsed("voucher", id))
            return Command.SINGLE_SUCCESS
        }
        val list = runCatching { plugin.configManager.generateCodes(safeId, amount) }
            .onFailure { plugin.logger.warning("Failed to generate codes from '$safeId': ${it.message}") }
            .getOrNull()?.first
        if (list == null) {
            messages.send(sender, "creator-failed")
            return Command.SINGLE_SUCCESS
        }
        plugin.reload()
        plugin.actionLog.admin("${sender.name} generated $amount code(s) from '$safeId' -> generated/${list.name}")
        messages.send(
            sender,
            "generated",
            Placeholder.parsed("amount", amount.toString()),
            Placeholder.parsed("voucher", safeId),
            Placeholder.parsed("file", "generated/${list.name}"),
        )
        return Command.SINGLE_SUCCESS
    }

    private fun voucherAdminCommand(
        plugin: Claimo,
        literal: String,
        action: (VoucherCreator, Player, String) -> Unit,
    ): LiteralArgumentBuilder<CommandSourceStack> =
        adminLiteral(literal)
            .then(
                Commands.argument("voucher", StringArgumentType.word())
                    .suggests { _, builder ->
                        val input = builder.remaining.lowercase()
                        plugin.configManager.config.vouchers.keys
                            .filter { it.lowercase().startsWith(input) }
                            .forEach(builder::suggest)
                        builder.buildFuture()
                    }
                    .executes { ctx ->
                        val sender = ctx.source.sender
                        val messages = plugin.configManager.config.messages
                        val id = StringArgumentType.getString(ctx, "voucher")
                        when {
                            sender !is Player -> messages.send(sender, "players-only")
                            plugin.voucherCreator == null -> messages.send(sender, "creator-unavailable")
                            else -> plugin.voucherCreator?.let { action(it, sender, id) }
                        }
                        Command.SINGLE_SUCCESS
                    }
            )

    private val adminLiterals: MutableSet<String> = ConcurrentHashMap.newKeySet()

    val adminSubcommands: Set<String> get() = adminLiterals

    private fun adminLiteral(literal: String): LiteralArgumentBuilder<CommandSourceStack> {
        adminLiterals += literal
        return Commands.literal(literal).requires { it.sender.hasPermission("claimo.admin") }
    }

    /** A standalone command that redeems [voucherId] directly, like `/<command> <voucherId>`. */
    fun buildRedeemCommand(plugin: Claimo, commandName: String, voucherId: String): LiteralCommandNode<CommandSourceStack> =
        Commands.literal(commandName)
            .executes { ctx ->
                val sender = ctx.source.sender
                val messages = plugin.configManager.config.messages
                when {
                    !sender.hasPermission("claimo.use") -> messages.send(sender, "no-permission")
                    sender !is Player -> messages.send(sender, "players-only")
                    else -> plugin.voucherService.redeem(sender, voucherId)
                }
                Command.SINGLE_SUCCESS
            }
            .build()

    fun buildDialogInput(plugin: Claimo, commandName: String): LiteralCommandNode<CommandSourceStack> =
        Commands.literal(commandName)
            .executes { ctx ->
                val sender = ctx.source.sender
                val messages = plugin.configManager.config.messages
                when {
                    !sender.hasPermission("claimo.use") -> messages.send(sender, "no-permission")
                    sender !is Player -> messages.send(sender, "players-only")
                    plugin.codePrompt == null -> messages.send(sender, "dialog-unavailable")
                    else -> plugin.codePrompt?.open(sender)
                }
                Command.SINGLE_SUCCESS
            }
            .build()
}
