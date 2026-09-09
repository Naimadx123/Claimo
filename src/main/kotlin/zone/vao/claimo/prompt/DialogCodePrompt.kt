package zone.vao.claimo.prompt

import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.event.player.PlayerCustomClickEvent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import zone.vao.claimo.Claimo

@Suppress("UnstableApiUsage")
class DialogCodePrompt(private val plugin: Claimo) : CodePrompt, Listener {

    override fun open(player: Player) {
        player.showDialog(build())
    }

    @EventHandler
    fun onCustomClick(event: PlayerCustomClickEvent) {
        if (event.identifier != RedeemDialog.SUBMIT) return
        val connection = event.commonConnection
        if (connection !is PlayerGameConnection) return
        val player = connection.player
        val code = event.dialogResponseView?.getText("code")?.trim().orEmpty()
        if (code.isEmpty()) return
        player.scheduler.run(plugin, { if (player.isOnline) plugin.voucherService.redeem(player, code) }, null)
    }

    private fun build(): Dialog {
        val messages = plugin.configManager.config.messages
        return Dialog.create { factory ->
            factory.empty()
                .base(RedeemDialog.base(messages, null))
                .type(RedeemDialog.type(messages))
        }
    }
}
