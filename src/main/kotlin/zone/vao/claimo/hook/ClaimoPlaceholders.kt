package zone.vao.claimo.hook

import org.bukkit.OfflinePlayer
import zone.vao.claimo.Claimo
import zone.vao.claimo.voucher.LimitMode
import zone.vao.claimo.voucher.Voucher

/**
 * Shared resolution for the `claimo` placeholders, used by both the PlaceholderAPI expansion
 * ([ClaimoExpansion]) and the MiniPlaceholders expansion ([ClaimoMiniExpansion]) so the two
 * always report identical values.
 */
object ClaimoPlaceholders {

    /** Per-voucher placeholder prefixes; each one takes a voucher id. */
    val PREFIXES = listOf("player_uses", "uses", "remaining", "limit", "can_redeem", "redeemed", "expired")

    fun totalCodes(plugin: Claimo): String = plugin.configManager.config.vouchers.size.toString()

    /** Resolves a full PlaceholderAPI parameter, e.g. `total_codes` or `uses_<id>`. */
    fun resolve(plugin: Claimo, player: OfflinePlayer?, params: String): String? {
        if (params.equals("total_codes", ignoreCase = true)) return totalCodes(plugin)
        val (prefix, id) = split(params) ?: return null
        return value(plugin, player, prefix, id)
    }

    /** Resolves a single [prefix] placeholder for the voucher [id]. */
    fun value(plugin: Claimo, player: OfflinePlayer?, prefix: String, id: String): String? {
        val voucher = findVoucher(plugin, id) ?: return null
        val online = player?.player
        return when (prefix.lowercase()) {
            "uses" -> plugin.usageService.globalUses(voucher.id).toString()
            "player_uses" -> (online?.let { plugin.usageService.playerUses(it, voucher.id) } ?: 0).toString()
            "redeemed" -> (online != null && plugin.usageService.playerUses(online, voucher.id) > 0).toString()
            "limit" -> if (voucher.limitMode == LimitMode.NONE) "unlimited" else voucher.limitAmount.toString()
            "remaining" -> remaining(plugin, voucher, player)
            "expired" -> voucher.isExpired().toString()
            "can_redeem" -> (online != null && !voucher.isExpired() && !plugin.usageService.isExhausted(online, voucher)).toString()
            else -> null
        }
    }

    private fun remaining(plugin: Claimo, voucher: Voucher, player: OfflinePlayer?): String = when (voucher.limitMode) {
        LimitMode.NONE -> "unlimited"
        LimitMode.GLOBAL -> (voucher.limitAmount - plugin.usageService.globalUses(voucher.id)).coerceAtLeast(0).toString()
        LimitMode.PER_PLAYER -> {
            val used = player?.player?.let { plugin.usageService.playerUses(it, voucher.id) } ?: 0
            (voucher.limitAmount - used).coerceAtLeast(0).toString()
        }
    }

    private fun split(params: String): Pair<String, String>? {
        for (prefix in PREFIXES) {
            if (params.length > prefix.length + 1 &&
                params.regionMatches(0, prefix, 0, prefix.length, ignoreCase = true) &&
                params[prefix.length] == '_'
            ) {
                return prefix to params.substring(prefix.length + 1)
            }
        }
        return null
    }

    private fun findVoucher(plugin: Claimo, id: String): Voucher? {
        val vouchers = plugin.configManager.config.vouchers
        return vouchers[id] ?: vouchers.entries.firstOrNull { it.key.equals(id, ignoreCase = true) }?.value
    }
}
