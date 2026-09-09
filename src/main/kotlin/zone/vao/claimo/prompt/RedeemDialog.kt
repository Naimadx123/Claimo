package zone.vao.claimo.prompt

import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import zone.vao.claimo.config.Messages

@Suppress("UnstableApiUsage")
object RedeemDialog {

    val SUBMIT: Key = Key.key("claimo", "redeem_submit")

    fun base(messages: Messages, externalTitle: Component?): DialogBase =
        DialogBase.builder(messages.line("prompt-title"))
            .externalTitle(externalTitle)
            .body(listOf(DialogBody.plainMessage(messages.line("prompt-body"))))
            .inputs(
                listOf(
                    DialogInput.text("code", messages.line("prompt-code-label")).maxLength(64).width(300).build(),
                ),
            )
            .build()

    fun type(messages: Messages): DialogType = DialogType.confirmation(
        ActionButton.create(messages.line("prompt-redeem"), null, 100, DialogAction.customClick(SUBMIT, null)),
        ActionButton.create(messages.line("prompt-cancel"), null, 100, null),
    )
}
