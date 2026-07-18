package zone.vao.claimo.hook

import io.github.miniplaceholders.api.Expansion
import io.github.miniplaceholders.api.utils.Tags
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.Tag
import org.bukkit.entity.Player
import zone.vao.claimo.Claimo

/**
 * Exposes the same `claimo` placeholders as [ClaimoExpansion], but as MiniMessage tags through
 * MiniPlaceholders — e.g. `<claimo_uses:'test'>`, `<claimo_redeemed:'test'>`, `<claimo_total_codes>`.
 *
 * Loaded reflectively from [Claimo] only when the MiniPlaceholders plugin is present, so the
 * MiniPlaceholders API classes are never linked on servers without it.
 */
@Suppress("UnstableApiUsage", "unused")
class ClaimoMiniExpansion(private val plugin: Claimo) {

    fun register() {
        val builder = Expansion.builder("claimo")
            .author(plugin.pluginMeta.authors.firstOrNull() ?: "Naimad (dc: 4g0)")
            .version(plugin.pluginMeta.version)
            .globalPlaceholder("total_codes") { _, _ -> tag(ClaimoPlaceholders.totalCodes(plugin)) }
        for (prefix in ClaimoPlaceholders.PREFIXES) {
            builder.audiencePlaceholder(Player::class.java, prefix) { audience, queue, _ ->
                if (!queue.hasNext()) return@audiencePlaceholder Tags.NULL_TAG
                tag(ClaimoPlaceholders.value(plugin, audience, prefix, queue.pop().value()))
            }
        }
        builder.build().register()
    }

    private fun tag(value: String?): Tag =
        if (value == null) Tags.NULL_TAG else Tag.selfClosingInserting(Component.text(value))
}
