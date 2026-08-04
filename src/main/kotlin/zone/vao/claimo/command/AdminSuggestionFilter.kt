package zone.vao.claimo.command

import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent
import com.mojang.brigadier.suggestion.Suggestions
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import zone.vao.claimo.Claimo

class AdminSuggestionFilter(private val plugin: Claimo) : Listener {

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onSendSuggestions(event: AsyncPlayerSendSuggestionsEvent) {
        if (event.player.hasPermission("claimo.admin")) return
        if (!isClaimoCommand(event.buffer)) return

        val admin = VoucherCommand.adminSubcommands
        if (admin.isEmpty()) return
        val vouchers = plugin.configManager.config.vouchers
        val suggestions = event.suggestions.list
        val kept = suggestions.filterNot { it.text.lowercase() in admin && !vouchers.containsKey(it.text) }
        if (kept.size == suggestions.size) return
        event.suggestions = Suggestions(event.suggestions.range, kept)
    }

    private fun isClaimoCommand(buffer: String): Boolean {
        val label = buffer
            .removePrefix("/")
            .substringBefore(' ')
            .substringAfterLast(':')
            .lowercase()
        return label == "claimo" || label == plugin.configManager.config.commandName.lowercase()
    }
}
