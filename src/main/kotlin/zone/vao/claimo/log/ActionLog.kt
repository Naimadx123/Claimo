package zone.vao.claimo.log

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import zone.vao.claimo.Claimo
import zone.vao.claimo.event.VoucherRedeemedEvent
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

class ActionLog(private val plugin: Claimo) : Listener {

    private val dir = File(plugin.dataFolder, "logs")
    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "claimo-action-log").apply { isDaemon = true }
    }

    @EventHandler
    fun onRedeem(event: VoucherRedeemedEvent) {
        if (!plugin.configManager.config.logRedeems) return
        val player = event.player
        val paid = if (event.voucher.price > 0.0) " (paid ${event.voucher.price})" else ""
        write("redeems.log", "${player.name} (${player.uniqueId}) -> ${event.voucher.id}$paid")
    }

    fun admin(line: String) {
        if (!plugin.configManager.config.logAdmin) return
        write("admin.log", line)
    }

    private fun write(fileName: String, line: String) {
        val stamped = "[${OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}] $line\n"
        io.execute {
            runCatching {
                dir.mkdirs()
                File(dir, fileName).appendText(stamped)
            }.onFailure { plugin.logger.warning("Failed to write $fileName: ${it.message}") }
        }
    }

    fun shutdown() {
        io.shutdown()
    }
}
