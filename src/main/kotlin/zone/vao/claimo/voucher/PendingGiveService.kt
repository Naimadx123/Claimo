package zone.vao.claimo.voucher

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import zone.vao.claimo.Claimo
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class PendingGiveService(private val plugin: Claimo) : Listener {

    private class Entry(val voucherId: String, val amount: Int)

    private val file = File(plugin.dataFolder, "pending-gives.yml")
    private val pending = ConcurrentHashMap<String, MutableList<Entry>>()

    init {
        val yaml = YamlConfiguration.loadConfiguration(file)
        for (name in yaml.getKeys(false)) {
            val entries = yaml.getMapList(name).mapNotNull { map ->
                val voucherId = map["voucher"]?.toString() ?: return@mapNotNull null
                Entry(voucherId, ((map["amount"] as? Number)?.toInt() ?: 1).coerceAtLeast(1))
            }
            if (entries.isNotEmpty()) pending[name] = entries.toMutableList()
        }
    }

    fun queue(playerName: String, voucherId: String, amount: Int) {
        pending.getOrPut(playerName.lowercase()) { mutableListOf() } += Entry(voucherId, amount)
        save()
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val entries = pending.remove(event.player.name.lowercase()) ?: return
        save()
        val player = event.player
        player.scheduler.run(plugin, {
            if (!player.isOnline) return@run
            val messages = plugin.configManager.config.messages
            for (entry in entries) {
                val voucher = plugin.configManager.config.vouchers[entry.voucherId]
                if (voucher?.item == null) {
                    plugin.actionLog.admin("skipped queued give of ${entry.amount}x '${entry.voucherId}' to ${player.name} (code or its item section is gone)")
                    continue
                }
                plugin.voucherItemService.give(player, voucher, entry.amount)
                plugin.actionLog.admin("delivered queued ${entry.amount}x '${entry.voucherId}' to ${player.name}")
                messages.send(
                    player, "item-received",
                    Placeholder.parsed("voucher", entry.voucherId),
                    Placeholder.parsed("amount", entry.amount.toString()),
                )
            }
        }, null)
    }

    private fun save() {
        val yaml = YamlConfiguration()
        pending.forEach { (name, entries) ->
            yaml.set(name, entries.map { mapOf("voucher" to it.voucherId, "amount" to it.amount) })
        }
        runCatching { yaml.save(file) }.onFailure {
            plugin.logger.warning("Failed to save pending-gives.yml: ${it.message}")
        }
    }
}
