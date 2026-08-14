package zone.vao.claimo.update

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import zone.vao.claimo.Claimo
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@Suppress("UnstableApiUsage")
class UpdateChecker(private val plugin: Claimo) : Listener {

    private val currentVersion = plugin.pluginMeta.version
    private val latest = AtomicReference<String?>(null)
    private var task: io.papermc.paper.threadedregions.scheduler.ScheduledTask? = null

    private val http: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }

    val latestVersion: String? get() = latest.get()

    fun start() {
        stop()
        val config = plugin.configManager.config.update
        if (!config.enabled) return
        val periodMinutes = (config.intervalHours * 60L).coerceAtLeast(30L)
        task = plugin.server.asyncScheduler.runAtFixedRate(
            plugin,
            { check() },
            1L,
            periodMinutes,
            TimeUnit.MINUTES,
        )
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    private fun check() {
        val request = HttpRequest.newBuilder(URI.create(API_URL))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Claimo/$currentVersion")
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build()

        val response = runCatching { http.send(request, HttpResponse.BodyHandlers.ofString()) }
            .getOrElse {
                plugin.logger.warning("Update check failed: ${it.message}")
                return
            }

        if (response.statusCode() != 200) {
            plugin.logger.warning("Update check failed: GitHub responded with HTTP ${response.statusCode()}.")
            return
        }

        val tag = TAG_PATTERN.find(response.body())?.groupValues?.get(1)
        if (tag.isNullOrBlank()) {
            plugin.logger.warning("Update check failed: no release tag in the GitHub response.")
            return
        }

        if (!isNewer(tag, currentVersion)) {
            latest.set(null)
            return
        }

        if (latest.getAndSet(tag) == tag) return
        plugin.logger.info("A new Claimo version is available: ${normalize(tag)} (running $currentVersion). Download: $RELEASES_URL")
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onJoin(event: PlayerJoinEvent) {
        val config = plugin.configManager.config.update
        if (!config.enabled || !config.notifyAdmins) return
        val player = event.player
        if (!player.hasPermission(PERMISSION)) return
        player.scheduler.runDelayed(plugin, { notify(player) }, null, NOTIFY_DELAY_TICKS)
    }

    private fun notify(player: Player) {
        val version = latest.get() ?: return
        if (!player.hasPermission(PERMISSION)) return
        plugin.configManager.config.messages.send(
            player,
            "update-available",
            Placeholder.parsed("latest", normalize(version)),
            Placeholder.parsed("current", currentVersion),
            Placeholder.parsed("url", RELEASES_URL),
        )
    }

    private companion object {
        const val API_URL = "https://api.github.com/repos/Naimadx123/Claimo/releases/latest"
        const val RELEASES_URL = "https://github.com/Naimadx123/Claimo/releases"
        const val PERMISSION = "claimo.update"
        const val NOTIFY_DELAY_TICKS = 40L
        val TAG_PATTERN = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"")

        fun normalize(version: String): String = version.trim().removePrefix("v").removePrefix("V")

        fun isNewer(latest: String, current: String): Boolean {
            val a = numbers(latest)
            val b = numbers(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        fun numbers(version: String): List<Int> =
            normalize(version)
                .substringBefore('-')
                .substringBefore('+')
                .split('.')
                .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    }
}
