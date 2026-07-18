package zone.vao.claimo.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.entity.Player
import zone.vao.claimo.Claimo
import zone.vao.claimo.creator.VoucherCreator

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
                Commands.literal("reload")
                    .requires { it.sender.hasPermission("claimo.admin") }
                    .executes { ctx ->
                        plugin.reload()
                        plugin.configManager.config.messages.send(ctx.source.sender, "reloaded")
                        Command.SINGLE_SUCCESS
                    }
            )
            .then(
                Commands.literal("create")
                    .requires { it.sender.hasPermission("claimo.admin") }
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
            .then(voucherAdminCommand(plugin, "edit") { creator, player, id -> creator.edit(player, id) })
            .then(voucherAdminCommand(plugin, "delete") { creator, player, id -> creator.delete(player, id) })
            .then(
                Commands.argument("voucher", StringArgumentType.word())
                    .suggests { ctx, builder ->
                        if (ctx.source.sender.hasPermission("claimo.use")) {
                            val input = builder.remaining.lowercase()
                            plugin.configManager.config.vouchers.values
                                .filter { !it.hidden && !it.isExpired() && it.id.lowercase().startsWith(input) }
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

    private fun voucherAdminCommand(
        plugin: Claimo,
        literal: String,
        action: (VoucherCreator, Player, String) -> Unit,
    ): LiteralCommandNode<CommandSourceStack> =
        Commands.literal(literal)
            .requires { it.sender.hasPermission("claimo.admin") }
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
            .build()

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
