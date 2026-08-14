package zone.vao.claimo

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import zone.vao.claimo.requirement.RequirementRegistry
import zone.vao.claimo.reward.RewardAction
import zone.vao.claimo.stats.ClaimoStats
import zone.vao.claimo.voucher.RedeemResult
import zone.vao.claimo.voucher.Voucher
import java.util.concurrent.CompletableFuture

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

    /** Like [redeem], but reports the outcome once the (possibly async) flow finishes. */
    fun redeemWithResult(player: Player, voucherId: String): CompletableFuture<RedeemResult>

    /** How many times [voucherId] has been redeemed in total, across all players. */
    fun globalUses(voucherId: String): Int

    /** How many times [player] has redeemed [voucherId]. Only online players are tracked. */
    fun playerUses(player: Player, voucherId: String): Int

    /** Milliseconds until [player] may redeem [voucherId] again, or 0 when not on cooldown. */
    fun cooldownRemaining(player: Player, voucherId: String): Long

    /** Clears [player]'s stored cooldown for [voucherId]. */
    fun clearCooldown(player: Player, voucherId: String)

    /** Builds the voucher's item form, or null if the voucher or its `item` section is missing. */
    fun buildVoucherItem(voucherId: String, amount: Int = 1): ItemStack?

    /** Gives [player] the voucher's item form (overflow drops at their feet). False if it has none. */
    fun giveVoucherItem(player: Player, voucherId: String, amount: Int = 1): Boolean

    /**
     * Gives the voucher's item form to the player named [playerName]: immediately when they
     * are online, otherwise queued and delivered on their next join. False if the voucher
     * or its `item` section is missing.
     */
    fun queueVoucherItem(playerName: String, voucherId: String, amount: Int = 1): Boolean

    /**
     * Writes a new voucher file from [settings] — the same keys as a `vouchers/<id>.yml`
     * file, with nested sections as nested maps — then reloads. False if [id] is invalid
     * or taken.
     */
    fun createVoucher(id: String, settings: Map<String, Any>): Boolean

    /** Deletes the voucher file for [id] and reloads. False if no such file exists. */
    fun deleteVoucher(id: String): Boolean

    /**
     * Clones the voucher [templateId] into [amount] hidden one-time codes (like
     * `/code generate`) and reloads. Returns the generated code ids, or null on failure.
     */
    fun generateCodes(templateId: String, amount: Int): List<String>?

    /** Registers a reward [action] for `action:<name> <args>` cmd lines (case-insensitive). */
    fun registerRewardAction(name: String, action: RewardAction)

    /** Removes the reward action registered under [name]. No-op if absent. */
    fun unregisterRewardAction(name: String)

    /** Reloads configuration and voucher files from disk. */
    fun reload()
}
