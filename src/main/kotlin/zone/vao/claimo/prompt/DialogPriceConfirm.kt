package zone.vao.claimo.prompt

import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.event.player.PlayerCustomClickEvent
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import zone.vao.claimo.Claimo
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Suppress("UnstableApiUsage")
class DialogPriceConfirm(private val plugin: Claimo) : PriceConfirm, Listener {

    private class Pending(val voucherId: String, val onSuccess: (() -> Unit)?)

    private val pending = ConcurrentHashMap<UUID, Pending>()

    override fun open(player: Player, voucherId: String, price: String, onSuccess: (() -> Unit)?) {
        pending[player.uniqueId] = Pending(voucherId, onSuccess)
        player.showDialog(build(voucherId, price))
    }

    @EventHandler
    fun onCustomClick(event: PlayerCustomClickEvent) {
        if (event.identifier != CONFIRM) return
        val connection = event.commonConnection
        if (connection !is PlayerGameConnection) return
        val player = connection.player
        val request = pending.remove(player.uniqueId) ?: return
        player.scheduler.run(plugin, {
            if (player.isOnline) plugin.voucherService.redeem(player, request.voucherId, confirmed = true, onSuccess = request.onSuccess)
        }, null)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        pending.remove(event.player.uniqueId)
    }

    private fun build(voucherId: String, price: String): Dialog {
        val messages = plugin.configManager.config.messages
        val resolvers = arrayOf(
            Placeholder.parsed("voucher", voucherId),
            Placeholder.parsed("price", price),
        )
        return Dialog.create { factory ->
            factory.empty()
                .base(
                    DialogBase.builder(messages.line("price-confirm-title", *resolvers))
                        .body(listOf(DialogBody.plainMessage(messages.line("price-confirm-body", *resolvers))))
                        .build(),
                )
                .type(
                    DialogType.confirmation(
                        ActionButton.create(messages.line("prompt-redeem"), null, 100, DialogAction.customClick(CONFIRM, null)),
                        ActionButton.create(messages.line("prompt-cancel"), null, 100, null),
                    ),
                )
        }
    }

    private companion object {
        val CONFIRM: Key = Key.key("claimo", "price_confirm")
    }
}
