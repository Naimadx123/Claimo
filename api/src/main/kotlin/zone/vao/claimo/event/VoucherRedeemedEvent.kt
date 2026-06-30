package zone.vao.claimo.event

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import zone.vao.claimo.voucher.Voucher

/**
 * Fired after a voucher's commands have been dispatched for [player]. Informational
 * (not cancellable) — useful for logging, analytics, or follow-up rewards.
 */
class VoucherRedeemedEvent(
    val player: Player,
    val voucher: Voucher,
) : Event() {

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
