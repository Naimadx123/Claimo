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
            .getOrNull()
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
