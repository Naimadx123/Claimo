package zone.vao.claimo.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.entity.Player
import zone.vao.claimo.Claimo

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
            .then(
                Commands.argument("voucher", StringArgumentType.word())
                    .suggests { ctx, builder ->
                        if (ctx.source.sender.hasPermission("claimo.use")) {
                            val input = builder.remaining.lowercase()
                            plugin.configManager.config.vouchers.values
                                .filter { !it.hidden && it.id.lowercase().startsWith(input) }
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
}
