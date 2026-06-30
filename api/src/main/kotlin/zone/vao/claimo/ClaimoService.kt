package zone.vao.claimo

import org.bukkit.entity.Player
import zone.vao.claimo.requirement.RequirementRegistry
import zone.vao.claimo.stats.ClaimoStats
import zone.vao.claimo.voucher.Voucher

/**
 * The operations Claimo exposes to addons, implemented by the running plugin and reached
 * through [ClaimoApi]. Obtain it via [ClaimoApi], not by depending on the plugin directly.
 */
interface ClaimoService {

    /** Registry of requirement types — register, remove, or query them. */
    val requirements: RequirementRegistry

    /** Read-only per-player progress used by the built-in requirements. */
    val stats: ClaimoStats

    /** All currently loaded vouchers, including hidden ones. */
    fun vouchers(): Collection<Voucher>

    /** The voucher with [id], or null if none is loaded. */
    fun voucher(id: String): Voucher?

    /** Runs the full redeem flow for [voucherId] as [player] (requirement checks, events, commands). */
    fun redeem(player: Player, voucherId: String)

    /** Reloads configuration and voucher files from disk. */
    fun reload()
}
