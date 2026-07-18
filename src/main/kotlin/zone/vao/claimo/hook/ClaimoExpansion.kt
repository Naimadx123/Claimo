package zone.vao.claimo.hook

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import zone.vao.claimo.Claimo
import zone.vao.claimo.voucher.LimitMode
import zone.vao.claimo.voucher.Voucher

class ClaimoExpansion(private val plugin: Claimo) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "claimo"

    override fun getAuthor(): String = plugin.pluginMeta.authors.firstOrNull() ?: "Naimad (dc: 4g0)"

    override fun getVersion(): String = plugin.pluginMeta.version

    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        val config = plugin.configManager.config
        if (params.equals("total_codes", ignoreCase = true)) return config.vouchers.size.toString()

        val (prefix, id) = split(params) ?: return null
        val voucher = findVoucher(id) ?: return null
        val online = player?.player

        return when (prefix) {
            "uses" -> plugin.usageService.globalUses(voucher.id).toString()
            "player_uses" -> (online?.let { plugin.usageService.playerUses(it, voucher.id) } ?: 0).toString()
            "redeemed" -> (online != null && plugin.usageService.playerUses(online, voucher.id) > 0).toString()
            "limit" -> if (voucher.limitMode == LimitMode.NONE) "unlimited" else voucher.limitAmount.toString()
            "remaining" -> remaining(voucher, player)
            "expired" -> voucher.isExpired().toString()
            "can_redeem" -> (online != null && !voucher.isExpired() && !plugin.usageService.isExhausted(online, voucher)).toString()
            else -> null
        }
    }

    private fun remaining(voucher: Voucher, player: OfflinePlayer?): String = when (voucher.limitMode) {
        LimitMode.NONE -> "unlimited"
        LimitMode.GLOBAL -> (voucher.limitAmount - plugin.usageService.globalUses(voucher.id)).coerceAtLeast(0).toString()
        LimitMode.PER_PLAYER -> {
            val used = player?.player?.let { plugin.usageService.playerUses(it, voucher.id) } ?: 0
            (voucher.limitAmount - used).coerceAtLeast(0).toString()
        }
    }

    private fun split(params: String): Pair<String, String>? {
        for (prefix in PREFIXES) {
            if (params.length > prefix.length + 1 && params.regionMatches(0, prefix, 0, prefix.length, ignoreCase = true) &&
                params[prefix.length] == '_'
            ) {
                return prefix to params.substring(prefix.length + 1)
            }
        }
        return null
    }

    private fun findVoucher(id: String): Voucher? {
        val vouchers = plugin.configManager.config.vouchers
        return vouchers[id] ?: vouchers.entries.firstOrNull { it.key.equals(id, ignoreCase = true) }?.value
    }

    private companion object {
        val PREFIXES = listOf("player_uses", "uses", "remaining", "limit", "can_redeem", "redeemed", "expired")
    }
}
