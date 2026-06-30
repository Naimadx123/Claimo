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

    fun record(player: Player, voucher: Voucher) {
        val uuid = player.uniqueId
        when (voucher.limitMode) {
            LimitMode.NONE -> return
            LimitMode.GLOBAL -> {
                val globalCount = global.merge(voucher.id, 1, Int::plus) ?: 1
                val playerCount = incrementPlayer(uuid, voucher.id)
                io.execute {
                    storage.saveGlobal(voucher.id, globalCount)
                    storage.savePlayer(uuid, voucher.id, playerCount)
                }
            }
            LimitMode.PER_PLAYER -> {
                val playerCount = incrementPlayer(uuid, voucher.id)
                io.execute { storage.savePlayer(uuid, voucher.id, playerCount) }
            }
        }
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

    private fun incrementPlayer(uuid: UUID, voucherId: String): Int =
        players.getOrPut(uuid) { ConcurrentHashMap() }.merge(voucherId, 1, Int::plus) ?: 1

    private fun loadPlayer(uuid: UUID): MutableMap<String, Int> =
        ConcurrentHashMap<String, Int>().apply { putAll(storage.loadPlayer(uuid)) }
}
