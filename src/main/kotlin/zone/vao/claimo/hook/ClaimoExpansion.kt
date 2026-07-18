package zone.vao.claimo.hook

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import zone.vao.claimo.Claimo

class ClaimoExpansion(private val plugin: Claimo) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "claimo"

    override fun getAuthor(): String = plugin.pluginMeta.authors.firstOrNull() ?: "Naimad (dc: 4g0)"

    override fun getVersion(): String = plugin.pluginMeta.version

    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? =
        ClaimoPlaceholders.resolve(plugin, player, params)
}
