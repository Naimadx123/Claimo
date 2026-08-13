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
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import zone.vao.claimo.Claimo
import zone.vao.claimo.requirement.RequirementInput
import zone.vao.claimo.requirement.builtin.GroupRequirement
import zone.vao.claimo.util.Durations
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Suppress("UnstableApiUsage")
class DialogVoucherCreator(private val plugin: Claimo) : VoucherCreator, Listener {

    private class TypeSpec(val type: String, val inputs: List<RequirementInput>)

    private class Draft(val reqPages: List<List<TypeSpec>>) {
        var id = ""
        var command = ""
        var redeemCommand = ""
        var console = true
        var hide = false
        var expires = ""
        var uses = 0
        var perPlayer = false
        var price = ""
        var itemEnabled = false
        var itemMaterial = ""
        var itemName = ""
        var itemLore = ""
        var fxFireworks = 0
        var fxParticle = ""
        var fxShape = ""
        var editing = false
        var originalId = ""
        val requirements = LinkedHashMap<String, MutableMap<String, Any>>()
        var page = 0
    }

    private val sessions = ConcurrentHashMap<UUID, Draft>()
    private val pendingDelete = ConcurrentHashMap<UUID, String>()

    override fun open(player: Player) {
        val draft = sessions[player.uniqueId]?.takeIf { !it.editing } ?: newDraft()
        draft.page = 0
        sessions[player.uniqueId] = draft
        player.showDialog(buildPage(draft))
    }

    override fun edit(player: Player, voucherId: String) {
        val messages = plugin.configManager.config.messages
        val safeId = plugin.configManager.sanitizeId(voucherId)
        val yaml = safeId?.let { plugin.configManager.readVoucher(it) }
        if (safeId == null || yaml == null) {
            messages.send(player, "creator-not-found", Placeholder.parsed("voucher", voucherId))
            return
        }
        val draft = newDraft().apply {
            editing = true
            originalId = safeId
            id = safeId
            command = when (val cmd = yaml.get("cmd")) {
                is String -> cmd
                is List<*> -> cmd.firstOrNull()?.toString().orEmpty()
                else -> ""
            }
            redeemCommand = yaml.getString("redeem-command").orEmpty().trim()
            console = yaml.getBoolean("console", true)
            hide = yaml.getBoolean("hide", false)
            expires = yaml.getString("expires").orEmpty().trim()
            val mode = yaml.getString("limit.mode")?.lowercase()?.replace('-', '_')
            if (mode == "global" || mode == "per_player") {
                uses = yaml.getInt("limit.amount", 1)
                perPlayer = mode == "per_player"
            }
            price = yaml.getDouble("price", 0.0).takeIf { it > 0 }?.toString().orEmpty()
            itemEnabled = yaml.isConfigurationSection("item")
            itemMaterial = yaml.getString("item.material").orEmpty().trim()
            itemName = yaml.getString("item.name").orEmpty()
            itemLore = yaml.getStringList("item.lore").joinToString(" | ")
            fxFireworks = yaml.getInt("effects.fireworks", 0)
            fxParticle = yaml.getString("effects.particle").orEmpty().trim()
            fxShape = yaml.getString("effects.shape").orEmpty().trim()
            for (entry in yaml.getMapList("requirements")) {
                val map = entry.entries.associate { (k, v) -> k.toString() to v }
                val type = map["type"]?.toString()?.lowercase() ?: continue
                requirements[type] = LinkedHashMap<String, Any>().apply {
                    map.forEach { (k, v) -> if (k != "type" && v != null) put(k, v) }
                }
            }
        }
        sessions[player.uniqueId] = draft
        player.showDialog(buildPage(draft))
    }

    override fun delete(player: Player, voucherId: String) {
        val messages = plugin.configManager.config.messages
        val safeId = plugin.configManager.sanitizeId(voucherId)
        if (safeId == null || !plugin.configManager.voucherExists(safeId)) {
            messages.send(player, "creator-not-found", Placeholder.parsed("voucher", voucherId))
            return
        }
        pendingDelete[player.uniqueId] = safeId
        player.showDialog(buildDeleteConfirm(safeId))
    }

    @EventHandler
    fun onCustomClick(event: PlayerCustomClickEvent) {
        val key = event.identifier
        if (key.namespace() != NAMESPACE) return
        val connection = event.commonConnection
        if (connection !is PlayerGameConnection) return
        val player = connection.player

        if (key == CANCEL) {
            sessions.remove(player.uniqueId)
            pendingDelete.remove(player.uniqueId)
            return
        }
        if (key == DELETE_OK) {
            val id = pendingDelete.remove(player.uniqueId) ?: return
            player.scheduler.run(plugin, { finishDelete(player, id) }, null)
            return
        }

        val draft = sessions[player.uniqueId] ?: return
        event.dialogResponseView?.let { readPage(draft, it) }
        when (key) {
            NEXT -> reopen(player, draft, draft.page + 1)
            BACK -> reopen(player, draft, draft.page - 1)
            CREATE -> player.scheduler.run(plugin, { finalize(player, draft) }, null)
        }
    }

    private fun newDraft(): Draft {
        val specs = plugin.requirementRegistry.types().sorted()
            .filterNot { it in GroupRequirement.MODES }
            .map { TypeSpec(it, plugin.requirementRegistry.inputs(it)) }
        return Draft(specs.chunked(REQUIREMENTS_PER_PAGE))
    }

    private fun reopen(player: Player, draft: Draft, page: Int) {
        draft.page = page.coerceIn(0, draft.reqPages.size + 1)
        player.scheduler.run(plugin, { player.showDialog(buildPage(draft)) }, null)
    }

    private fun readPage(draft: Draft, view: DialogResponseView) {
        if (draft.page == 0) {
            draft.id = view.getText("id").orEmpty()
            draft.command = view.getText("command").orEmpty().trim()
            draft.redeemCommand = view.getText("redeem_command").orEmpty().removePrefix("/").trim()
            draft.console = view.getBoolean("console") ?: true
            draft.hide = view.getBoolean("hide") ?: false
            draft.expires = view.getText("expires").orEmpty().trim()
            draft.uses = view.getFloat("uses")?.toInt() ?: 0
            draft.perPlayer = view.getBoolean("per_player") ?: false
            draft.price = view.getText("price").orEmpty().trim()
            return
        }
        if (draft.page == 1) {
            draft.itemEnabled = view.getBoolean("item_enabled") ?: false
            draft.itemMaterial = view.getText("item_material").orEmpty().trim()
            draft.itemName = view.getText("item_name").orEmpty().trim()
            draft.itemLore = view.getText("item_lore").orEmpty().trim()
            draft.fxFireworks = view.getFloat("fx_fireworks")?.toInt() ?: 0
            draft.fxParticle = view.getText("fx_particle").orEmpty().trim()
            draft.fxShape = view.getText("fx_shape").orEmpty().trim()
            return
        }
        for (spec in draft.reqPages[draft.page - 2]) {
            if (view.getBoolean(enableKey(spec.type)) != true) {
                draft.requirements.remove(spec.type)
                continue
            }
            val params = LinkedHashMap<String, Any>(draft.requirements[spec.type] ?: emptyMap())
            for (input in spec.inputs) {
                val dk = inputKey(spec.type, input.key)
                when (input) {
                    is RequirementInput.NumberInput -> {
                        val f = view.getFloat(dk) ?: input.initial.toFloat()
                        params[input.key] = if (f % 1f == 0f) f.toInt() else f.toDouble()
                    }
                    is RequirementInput.TextInput -> {
                        val text = view.getText(dk).orEmpty()
                        if (text.isNotBlank()) params[input.key] = text else params.remove(input.key)
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
        val safeId = if (draft.editing) draft.originalId else plugin.configManager.sanitizeId(draft.id)
        if (safeId == null || draft.command.isBlank()) {
            messages.send(player, "creator-invalid-id")
            reopen(player, draft, 0)
            return
        }
        if (!draft.editing && plugin.configManager.voucherExists(safeId)) {
            messages.send(player, "creator-exists", Placeholder.parsed("voucher", safeId))
            reopen(player, draft, 0)
            return
        }
        if (draft.expires.isNotBlank() && Durations.parseMillis(draft.expires) == null) {
            messages.send(player, "creator-invalid-expiry")
            reopen(player, draft, 0)
            return
        }
        val price = draft.price.replace(',', '.').toDoubleOrNull() ?: 0.0
        if (draft.price.isNotBlank() && (draft.price.replace(',', '.').toDoubleOrNull() == null || price < 0)) {
            messages.send(player, "creator-invalid-price")
            reopen(player, draft, 0)
            return
        }
        if (draft.itemEnabled && draft.itemMaterial.isNotBlank() && Material.matchMaterial(draft.itemMaterial) == null) {
            messages.send(player, "creator-invalid-material")
            reopen(player, draft, 1)
            return
        }
        if (draft.fxParticle.isNotBlank() && runCatching { Particle.valueOf(draft.fxParticle.uppercase()) }.isFailure) {
            messages.send(player, "creator-invalid-particle")
            reopen(player, draft, 1)
            return
        }

        try {
            val base = if (draft.editing) plugin.configManager.readVoucher(safeId) else null
            plugin.configManager.saveVoucher(safeId, base) { yaml ->
                yaml.set("cmd", draft.command)
                yaml.set("redeem-command", draft.redeemCommand.ifBlank { null })
                yaml.set("console", draft.console)
                yaml.set("hide", draft.hide)
                if (draft.expires.isNotBlank()) {
                    yaml.set("expires", draft.expires)
                    yaml.set("created", System.currentTimeMillis())
                } else {
                    yaml.set("expires", null)
                    if (yaml.getString("starts").isNullOrBlank()) yaml.set("created", null)
                }
                if (draft.uses > 0) {
                    yaml.set("limit.mode", if (draft.perPlayer) "per-player" else "global")
                    yaml.set("limit.amount", draft.uses)
                } else {
                    yaml.set("limit", null)
                }
                yaml.set("price", price.takeIf { it > 0 })
                if (draft.itemEnabled) {
                    yaml.set("item.material", draft.itemMaterial.ifBlank { null })
                    yaml.set("item.name", draft.itemName.ifBlank { null })
                    yaml.set("item.lore", draft.itemLore.split('|').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { null })
                    if (yaml.getConfigurationSection("item")?.getKeys(false).isNullOrEmpty()) yaml.set("item.material", "PAPER")
                } else {
                    yaml.set("item", null)
                }
                if (draft.fxFireworks > 0 || draft.fxParticle.isNotBlank()) {
                    yaml.set("effects.fireworks", draft.fxFireworks.takeIf { it > 0 })
                    yaml.set("effects.particle", draft.fxParticle.ifBlank { null })
                    yaml.set("effects.shape", draft.fxShape.ifBlank { null })
                } else {
                    yaml.set("effects", null)
                }
                val requirements = draft.requirements.map { (type, params) ->
                    LinkedHashMap<String, Any>().apply {
                        put("type", type)
                        putAll(params)
                    }
                }
                yaml.set("requirements", requirements.ifEmpty { null })
            }
        } catch (ex: Exception) {
            plugin.logger.warning("Failed to write voucher '$safeId': ${ex.message}")
            messages.send(player, "creator-failed")
            sessions.remove(player.uniqueId)
            return
        }

        plugin.reload()
        val key = if (draft.editing) "creator-edited" else "creator-created"
        plugin.actionLog.admin("${player.name} ${if (draft.editing) "edited" else "created"} code '$safeId'")
        messages.send(player, key, Placeholder.parsed("voucher", safeId))
        sessions.remove(player.uniqueId)
    }

    private fun finishDelete(player: Player, safeId: String) {
        val messages = plugin.configManager.config.messages
        if (plugin.configManager.deleteVoucher(safeId)) {
            plugin.reload()
            plugin.actionLog.admin("${player.name} deleted code '$safeId'")
            messages.send(player, "creator-deleted", Placeholder.parsed("voucher", safeId))
        } else {
            messages.send(player, "creator-not-found", Placeholder.parsed("voucher", safeId))
        }
    }

    private fun buildPage(draft: Draft): Dialog = when (draft.page) {
        0 -> buildSettingsPage(draft)
        1 -> buildExtrasPage(draft)
        else -> buildRequirementPage(draft, draft.page - 2)
    }

    private fun buildSettingsPage(draft: Draft): Dialog {
        val inputs = listOf(
            DialogInput.text("id", Component.text("Code name")).maxLength(32).width(300).initial(draft.id).build(),
            DialogInput.text("command", Component.text("Command (placeholder supported)"))
                .maxLength(256).width(300).initial(draft.command).build(),
            DialogInput.text("redeem_command", Component.text("Redeem command (e.g. testget, empty = none)"))
                .maxLength(32).width(300).initial(draft.redeemCommand).build(),
            DialogInput.bool("console", Component.text("Run as console")).initial(draft.console).build(),
            DialogInput.bool("hide", Component.text("Hide from list")).initial(draft.hide).build(),
            DialogInput.text("expires", Component.text("Expires after (e.g. 5d, empty = never)"))
                .maxLength(32).width(300).initial(draft.expires).build(),
            DialogInput.numberRange("uses", Component.text("Max uses (0 = unlimited)"), 0f, 1000f)
                .step(1f).initial(draft.uses.toFloat().coerceIn(0f, 1000f)).width(300).build(),
            DialogInput.bool("per_player", Component.text("Limit is per player (off = shared)"))
                .initial(draft.perPlayer).build(),
            DialogInput.text("price", Component.text("Price (Vault, empty = free)"))
                .maxLength(16).width(300).initial(draft.price).build(),
        )
        val title = if (draft.editing) "Edit ${draft.originalId} — settings" else "Create a code — settings"
        return dialog(
            title,
            "Fill in the code, then continue to its item, effects and requirements.",
            inputs,
            listOf(button("Next »", NamedTextColor.YELLOW, NEXT)),
        )
    }

    private fun buildExtrasPage(draft: Draft): Dialog {
        val inputs = listOf(
            DialogInput.bool("item_enabled", Component.text("Give as an item (/code give)"))
                .initial(draft.itemEnabled).build(),
            DialogInput.text("item_material", Component.text("Item material (e.g. PAPER)"))
                .maxLength(64).width(300).initial(draft.itemMaterial).build(),
            DialogInput.text("item_name", Component.text("Item name (MiniMessage)"))
                .maxLength(128).width(300).initial(draft.itemName).build(),
            DialogInput.text("item_lore", Component.text("Item lore (separate lines with |)"))
                .maxLength(256).width(300).initial(draft.itemLore).build(),
            DialogInput.numberRange("fx_fireworks", Component.text("Fireworks on redeem"), 0f, 10f)
                .step(1f).initial(draft.fxFireworks.toFloat().coerceIn(0f, 10f)).width(300).build(),
            DialogInput.text("fx_particle", Component.text("Particle on redeem (e.g. FLAME, empty = none)"))
                .maxLength(64).width(300).initial(draft.fxParticle).build(),
            DialogInput.text("fx_shape", Component.text("Particle shape (burst, circle, sphere, helix)"))
                .maxLength(16).width(300).initial(draft.fxShape).build(),
        )
        val actions = listOf(
            button("« Back", NamedTextColor.GRAY, BACK),
            if (draft.reqPages.isEmpty()) button("Save", NamedTextColor.GREEN, CREATE) else button("Next »", NamedTextColor.YELLOW, NEXT),
        )
        return dialog(
            "Item & effects",
            "Optional: hand the code out as an item and celebrate the redeem.",
            inputs,
            actions,
        )
    }

    private fun buildRequirementPage(draft: Draft, index: Int): Dialog {
        val specs = draft.reqPages[index]
        val inputs = specs.flatMap { spec -> requirementInputs(draft, spec) }
        val last = index == draft.reqPages.lastIndex
        val actions = listOf(
            button("« Back", NamedTextColor.GRAY, BACK),
            if (last) button("Save", NamedTextColor.GREEN, CREATE) else button("Next »", NamedTextColor.YELLOW, NEXT),
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
                    val min = input.min.toFloat()
                    val max = input.max.toFloat()
                    val initial = ((saved?.get(input.key) as? Number)?.toFloat() ?: input.initial.toFloat())
                        .coerceIn(min, max)
                    DialogInput.numberRange(dk, Component.text(input.label), min, max)
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

    private fun buildDeleteConfirm(safeId: String): Dialog =
        Dialog.create { factory ->
            factory.empty()
                .base(
                    DialogBase.builder(Component.text("Delete code"))
                        .body(listOf(DialogBody.plainMessage(Component.text("Permanently delete the code \"$safeId\"?"))))
                        .build(),
                )
                .type(
                    DialogType.confirmation(
                        button("Delete", NamedTextColor.RED, DELETE_OK),
                        button("Cancel", NamedTextColor.GRAY, CANCEL),
                    ),
                )
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
        val DELETE_OK: Key = Key.key(NAMESPACE, "delete_confirm")
    }
}
