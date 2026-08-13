package zone.vao.claimo.usage

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import zone.vao.claimo.storage.UsageStorage
import zone.vao.claimo.voucher.LimitMode
import zone.vao.claimo.voucher.Voucher
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class UsageService(
    private val plugin: JavaPlugin,
    private val storage: UsageStorage,
) : Listener {

    private val global = ConcurrentHashMap<String, Int>()
    private val players = ConcurrentHashMap<UUID, MutableMap<String, Int>>()
    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "claimo-storage").apply { isDaemon = true }
    }

    fun load() {
        global.clear()
        global.putAll(storage.loadGlobal())
        players.clear()
        plugin.server.onlinePlayers.forEach { players[it.uniqueId] = loadPlayer(it.uniqueId) }
    }

    fun globalUses(voucherId: String): Int = global[voucherId] ?: 0

    fun playerUses(player: Player, voucherId: String): Int =
        players[player.uniqueId]?.get(voucherId) ?: 0

    fun isExhausted(player: Player, voucher: Voucher): Boolean = when (voucher.limitMode) {
        LimitMode.NONE -> false
        LimitMode.GLOBAL -> globalUses(voucher.id) >= voucher.limitAmount || playerUses(player, voucher.id) >= 1
        LimitMode.PER_PLAYER -> playerUses(player, voucher.id) >= voucher.limitAmount
    }

    fun tryRecord(player: Player, voucher: Voucher): Boolean {
        val uuid = player.uniqueId
        val playerMax = when (voucher.limitMode) {
            LimitMode.NONE -> Int.MAX_VALUE
            LimitMode.GLOBAL -> 1
            LimitMode.PER_PLAYER -> voucher.limitAmount
        }
        val globalMax = if (voucher.limitMode == LimitMode.GLOBAL) voucher.limitAmount else Int.MAX_VALUE
        if (!storage.incrementPlayer(uuid, voucher.id, playerMax)) return false
        if (!storage.incrementGlobal(voucher.id, globalMax)) {
            storage.decrementPlayer(uuid, voucher.id)
            return false
        }
        global.merge(voucher.id, 1, Int::plus)
        incrementPlayer(uuid, voucher.id)
        return true
    }

    fun release(player: Player, voucher: Voucher) {
        storage.decrementGlobal(voucher.id)
        storage.decrementPlayer(player.uniqueId, voucher.id)
        global.merge(voucher.id, -1) { a, b -> (a + b).coerceAtLeast(0) }
        players[player.uniqueId]?.merge(voucher.id, -1) { a, b -> (a + b).coerceAtLeast(0) }
    }

    fun purgeExcept(validIds: Set<String>): Set<String> {
        val valid = validIds.mapTo(HashSet()) { it.lowercase() }
        val known = global.keys + players.values.flatMap { it.keys }
        val orphaned = known.filterNot { it.lowercase() in valid }.toSet()
        if (orphaned.isEmpty()) return emptySet()
        for (id in orphaned) {
            global.remove(id)
            players.values.forEach { it.remove(id) }
        }
        io.execute { orphaned.forEach { storage.deleteVoucher(it) } }
        return orphaned
    }

    fun shutdown() {
        io.shutdown()
        runCatching { io.awaitTermination(5, TimeUnit.SECONDS) }
        storage.close()
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        if (event.loginResult != AsyncPlayerPreLoginEvent.Result.ALLOWED) return
        players[event.uniqueId] = loadPlayer(event.uniqueId)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        players.remove(event.player.uniqueId)
    }

    private fun incrementPlayer(uuid: UUID, voucherId: String) {
        players.getOrPut(uuid) { ConcurrentHashMap() }.merge(voucherId, 1, Int::plus)
    }

    private fun loadPlayer(uuid: UUID): MutableMap<String, Int> =
        ConcurrentHashMap<String, Int>().apply { putAll(storage.loadPlayer(uuid)) }
}
