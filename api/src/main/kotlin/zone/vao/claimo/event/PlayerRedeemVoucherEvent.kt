package zone.vao.claimo.event

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import zone.vao.claimo.voucher.Voucher

/**
 * Fired when a player has met all of a voucher's requirements, just before its commands
 * run. Cancelling stops the redemption (no commands run, no success message).
 */
class PlayerRedeemVoucherEvent(
    val player: Player,
    val voucher: Voucher,
) : Event(), Cancellable {

    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
