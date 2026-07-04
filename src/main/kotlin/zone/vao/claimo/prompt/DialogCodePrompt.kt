package zone.vao.claimo.prompt

import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.event.player.PlayerCustomClickEvent
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
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
        if (event.identifier != SUBMIT) return
        val connection = event.commonConnection
        if (connection !is PlayerGameConnection) return
        val player = connection.player
        val code = event.dialogResponseView?.getText("code")?.trim().orEmpty()
        if (code.isEmpty()) return
        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            if (player.isOnline) plugin.voucherService.redeem(player, code)
        }
    }

    private fun build(): Dialog =
        Dialog.create { factory ->
            factory.empty()
                .base(
                    DialogBase.builder(Component.text("Redeem a code"))
                        .body(listOf(DialogBody.plainMessage(Component.text("Enter your reward code below."))))
                        .inputs(
                            listOf(
                                DialogInput.text("code", Component.text("Code")).maxLength(64).width(300).build(),
                            ),
                        )
                        .build(),
                )
                .type(
                    DialogType.confirmation(
                        ActionButton.create(
                            Component.text("Redeem", NamedTextColor.GREEN),
                            null,
                            100,
                            DialogAction.customClick(SUBMIT, null),
                        ),
                        ActionButton.create(Component.text("Cancel", NamedTextColor.RED), null, 100, null),
                    ),
                )
        }

    private companion object {
        val SUBMIT: Key = Key.key("claimo", "redeem_submit")
    }
}
