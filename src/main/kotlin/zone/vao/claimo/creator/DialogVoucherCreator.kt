package zone.vao.claimo.creator

import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.dialog.DialogResponseView
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
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import zone.vao.claimo.Claimo
import zone.vao.claimo.requirement.RequirementInput
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Suppress("UnstableApiUsage")
class DialogVoucherCreator(private val plugin: Claimo) : VoucherCreator, Listener {

    private class TypeSpec(val type: String, val inputs: List<RequirementInput>)

    private class Draft(val reqPages: List<List<TypeSpec>>) {
        var id = ""
        var command = ""
        var console = true
        var hide = false
        var uses = 0
        var perPlayer = false
        val requirements = LinkedHashMap<String, MutableMap<String, Any>>()
        var page = 0
    }

    private val sessions = ConcurrentHashMap<UUID, Draft>()

    override fun open(player: Player) {
        val specs = plugin.requirementRegistry.types().sorted()
            .map { TypeSpec(it, plugin.requirementRegistry.inputs(it)) }
        val draft = Draft(specs.chunked(REQUIREMENTS_PER_PAGE))
        sessions[player.uniqueId] = draft
        player.showDialog(buildPage(draft))
    }

    @EventHandler
    fun onCustomClick(event: PlayerCustomClickEvent) {
        val key = event.identifier
        if (key.namespace() != NAMESPACE) return
        val connection = event.commonConnection
        if (connection !is PlayerGameConnection) return
        val player = connection.player
        val draft = sessions[player.uniqueId] ?: return

        if (key == CANCEL) {
            sessions.remove(player.uniqueId)
            return
        }

        event.dialogResponseView?.let { readPage(draft, it) }
        when (key) {
            NEXT -> reopen(player, draft, draft.page + 1)
            BACK -> reopen(player, draft, draft.page - 1)
            CREATE -> plugin.server.globalRegionScheduler.run(plugin) { _ -> finalize(player, draft) }
        }
    }

    private fun reopen(player: Player, draft: Draft, page: Int) {
        draft.page = page.coerceIn(0, draft.reqPages.size)
        player.scheduler.run(plugin, { player.showDialog(buildPage(draft)) }, null)
    }

    private fun readPage(draft: Draft, view: DialogResponseView) {
        if (draft.page == 0) {
            draft.id = view.getText("id").orEmpty()
            draft.command = view.getText("command").orEmpty().trim()
            draft.console = view.getBoolean("console") ?: true
            draft.hide = view.getBoolean("hide") ?: false
            draft.uses = view.getFloat("uses")?.toInt() ?: 0
            draft.perPlayer = view.getBoolean("per_player") ?: false
            return
        }
        for (spec in draft.reqPages[draft.page - 1]) {
            if (view.getBoolean(enableKey(spec.type)) != true) {
                draft.requirements.remove(spec.type)
                continue
            }
            val params = LinkedHashMap<String, Any>()
            for (input in spec.inputs) {
                val dk = inputKey(spec.type, input.key)
                when (input) {
                    is RequirementInput.NumberInput -> {
                        val f = view.getFloat(dk) ?: input.initial.toFloat()
                        params[input.key] = if (f % 1f == 0f) f.toInt() else f.toDouble()
                    }
                    is RequirementInput.TextInput -> {
                        val text = view.getText(dk).orEmpty()
                        if (text.isNotBlank()) params[input.key] = text
                    }
                    is RequirementInput.BoolInput -> {
                        params[input.key] = view.getBoolean(dk) ?: input.initial
                    }
                }
            }
            draft.requirements[spec.type] = params
        }
    }

    private fun finalize(player: Player, draft: Draft) {
        val messages = plugin.configManager.config.messages
        val safeId = plugin.configManager.sanitizeId(draft.id)
        if (safeId == null || draft.command.isBlank()) {
            messages.send(player, "creator-invalid-id")
            reopen(player, draft, 0)
            return
        }
        if (plugin.configManager.voucherExists(safeId)) {
            messages.send(player, "creator-exists", Placeholder.parsed("voucher", safeId))
            reopen(player, draft, 0)
            return
        }

        try {
            plugin.configManager.saveVoucher(safeId) { yaml ->
                yaml.set("cmd", draft.command)
                yaml.set("console", draft.console)
                yaml.set("hide", draft.hide)
                if (draft.uses > 0) {
                    yaml.set("limit.mode", if (draft.perPlayer) "per-player" else "global")
                    yaml.set("limit.amount", draft.uses)
                }
                val requirements = draft.requirements.map { (type, params) ->
                    LinkedHashMap<String, Any>().apply {
                        put("type", type)
                        putAll(params)
                    }
                }
                if (requirements.isNotEmpty()) yaml.set("requirements", requirements)
            }
        } catch (ex: Exception) {
            plugin.logger.warning("Failed to write voucher '$safeId': ${ex.message}")
            messages.send(player, "creator-failed")
            sessions.remove(player.uniqueId)
            return
        }

        plugin.reload()
        messages.send(player, "creator-created", Placeholder.parsed("voucher", safeId))
        sessions.remove(player.uniqueId)
    }

    private fun buildPage(draft: Draft): Dialog =
        if (draft.page == 0) buildSettingsPage(draft) else buildRequirementPage(draft, draft.page - 1)

    private fun buildSettingsPage(draft: Draft): Dialog {
        val inputs = listOf(
            DialogInput.text("id", Component.text("Code name")).maxLength(32).width(300).initial(draft.id).build(),
            DialogInput.text("command", Component.text("Command (placeholder supported)"))
                .maxLength(256).width(300).initial(draft.command).build(),
            DialogInput.bool("console", Component.text("Run as console")).initial(draft.console).build(),
            DialogInput.bool("hide", Component.text("Hide from list")).initial(draft.hide).build(),
            DialogInput.numberRange("uses", Component.text("Max uses (0 = unlimited)"), 0f, 1000f)
                .step(1f).initial(draft.uses.toFloat()).width(300).build(),
            DialogInput.bool("per_player", Component.text("Limit is per player (off = shared)"))
                .initial(draft.perPlayer).build(),
        )
        val advance = if (draft.reqPages.isEmpty()) {
            button("Create", NamedTextColor.GREEN, CREATE)
        } else {
            button("Next »", NamedTextColor.YELLOW, NEXT)
        }
        return dialog(
            "Create a code — settings",
            "Fill in the code, then continue to its requirements.",
            inputs,
            listOf(advance),
        )
    }

    private fun buildRequirementPage(draft: Draft, index: Int): Dialog {
        val specs = draft.reqPages[index]
        val inputs = specs.flatMap { spec -> requirementInputs(draft, spec) }
        val last = index == draft.reqPages.lastIndex
        val actions = listOf(
            button("« Back", NamedTextColor.GRAY, BACK),
            if (last) button("Create", NamedTextColor.GREEN, CREATE) else button("Next »", NamedTextColor.YELLOW, NEXT),
        )
        return dialog(
            "Requirements (${index + 1}/${draft.reqPages.size})",
            "Toggle a requirement to include it, then set its values.",
            inputs,
            actions,
        )
    }

    private fun requirementInputs(draft: Draft, spec: TypeSpec): List<DialogInput> {
        val saved = draft.requirements[spec.type]
        val controls = mutableListOf<DialogInput>(
            DialogInput.bool(enableKey(spec.type), Component.text("Require: ${spec.type}"))
                .initial(saved != null).build(),
        )
        for (input in spec.inputs) {
            val dk = inputKey(spec.type, input.key)
            controls += when (input) {
                is RequirementInput.NumberInput -> {
                    val initial = (saved?.get(input.key) as? Number)?.toFloat() ?: input.initial.toFloat()
                    DialogInput.numberRange(dk, Component.text(input.label), input.min.toFloat(), input.max.toFloat())
                        .step(input.step.toFloat()).initial(initial).width(300).build()
                }
                is RequirementInput.TextInput -> {
                    val initial = saved?.get(input.key) as? String ?: input.initial
                    DialogInput.text(dk, Component.text(input.label))
                        .maxLength(input.maxLength).width(300).initial(initial).build()
                }
                is RequirementInput.BoolInput -> {
                    val initial = saved?.get(input.key) as? Boolean ?: input.initial
                    DialogInput.bool(dk, Component.text(input.label)).initial(initial).build()
                }
            }
        }
        return controls
    }

    private fun dialog(title: String, body: String, inputs: List<DialogInput>, actions: List<ActionButton>): Dialog =
        Dialog.create { factory ->
            factory.empty()
                .base(
                    DialogBase.builder(Component.text(title))
                        .body(listOf(DialogBody.plainMessage(Component.text(body))))
                        .inputs(inputs)
                        .build(),
                )
                .type(DialogType.multiAction(actions, button("Cancel", NamedTextColor.RED, CANCEL), actions.size))
        }

    private fun button(label: String, color: NamedTextColor, key: Key): ActionButton =
        ActionButton.create(Component.text(label, color), null, 100, DialogAction.customClick(key, null))

    private fun slug(value: String): String = value.lowercase().replace(Regex("[^a-z0-9_]"), "_")
    private fun enableKey(type: String): String = "en_${slug(type)}"
    private fun inputKey(type: String, key: String): String = "in_${slug(type)}_${slug(key)}"

    private companion object {
        const val NAMESPACE = "claimo"
        const val REQUIREMENTS_PER_PAGE = 4

        val NEXT: Key = Key.key(NAMESPACE, "create_next")
        val BACK: Key = Key.key(NAMESPACE, "create_back")
        val CREATE: Key = Key.key(NAMESPACE, "create_finish")
        val CANCEL: Key = Key.key(NAMESPACE, "create_cancel")
    }
}
