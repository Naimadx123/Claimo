package zone.vao.claimo.log

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import zone.vao.claimo.Claimo
import zone.vao.claimo.event.VoucherRedeemedEvent
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

class RedeemLog(private val plugin: Claimo) : Listener {

    private val file = File(File(plugin.dataFolder, "logs"), "redeems.log")
    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "claimo-redeem-log").apply { isDaemon = true }
    }

    @EventHandler
    fun onRedeem(event: VoucherRedeemedEvent) {
        if (!plugin.configManager.config.logRedeems) return
        val player = event.player
        val line = "[${OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}] " +
            "${player.name} (${player.uniqueId}) -> ${event.voucher.id}\n"
        io.execute {
            runCatching {
                file.parentFile.mkdirs()
                file.appendText(line)
            }.onFailure { plugin.logger.warning("Failed to write redeem log: ${it.message}") }
        }
    }

    fun shutdown() {
        io.shutdown()
    }
}
