package zone.vao.claimo

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.jetbrains.annotations.ApiStatus
import zone.vao.claimo.requirement.RequirementFactory
import zone.vao.claimo.requirement.RequirementInput
import zone.vao.claimo.requirement.RequirementRegistry
import zone.vao.claimo.reward.RewardAction
import zone.vao.claimo.stats.ClaimoStats
import zone.vao.claimo.voucher.RedeemResult
import zone.vao.claimo.voucher.Voucher
import java.util.concurrent.CompletableFuture

object ClaimoApi {

    @Volatile
    private var service: ClaimoService? = null

    private fun service(): ClaimoService =
        service ?: error("Claimo is not enabled yet; use the API from your plugin's onEnable or later.")

    @ApiStatus.Internal
    fun init(service: ClaimoService) {
        this.service = service
    }

    @ApiStatus.Internal
    fun shutdown() {
        service = null
    }

    /** Registry of all requirement types. Use it to register, remove, or query types. */
    val requirements: RequirementRegistry get() = service().requirements

    /** Read-only per-player progress (blocks mined, playtime) used by the built-in requirements. */
    val stats: ClaimoStats get() = service().stats

    /**
     * Registers a new requirement type under [type] (matched case-insensitively).
     *
     * The [factory] is invoked lazily on every redeem attempt that uses the type,
     * receiving that requirement's config so it can read its own parameters. Safe
     * to call from an addon's `onEnable`, even after Claimo has loaded its vouchers.
     * Registering an already-known [type] replaces the previous factory.
     */
    fun registerRequirement(type: String, factory: RequirementFactory) {
        requirements.register(type, factory)
    }

    /**
     * Registers a requirement type along with the [inputs] the in-game code creator should
     * offer for it (so admins can configure it without editing files). Otherwise behaves
     * like [registerRequirement] above.
     */
    fun registerRequirement(type: String, factory: RequirementFactory, inputs: List<RequirementInput>) {
        requirements.register(type, factory, inputs)
    }

    /** Removes the factory previously registered under [type]. No-op if absent. */
    fun unregisterRequirement(type: String) {
        requirements.unregister(type)
    }

    /** All currently loaded vouchers, including hidden ones. */
    fun vouchers(): Collection<Voucher> = service().vouchers()

    /** The voucher with [id], or null if none is loaded. */
    fun voucher(id: String): Voucher? = service().voucher(id)

    /** Runs the full redeem flow for [voucherId] as [player] (requirement checks, events, commands). */
    fun redeem(player: Player, voucherId: String) {
        service().redeem(player, voucherId)
    }

    /**
     * Like [redeem], but reports the outcome once the (possibly async) flow finishes.
     * See [RedeemResult] for the possible outcomes and their messaging guarantees.
     */
    fun redeemWithResult(player: Player, voucherId: String): CompletableFuture<RedeemResult> =
        service().redeemWithResult(player, voucherId)

    /** How many times [voucherId] has been redeemed in total, across all players. */
    fun globalUses(voucherId: String): Int = service().globalUses(voucherId)

    /** How many times [player] has redeemed [voucherId]. Only online players are tracked. */
    fun playerUses(player: Player, voucherId: String): Int = service().playerUses(player, voucherId)

    /** Milliseconds until [player] may redeem [voucherId] again, or 0 when not on cooldown. */
    fun cooldownRemaining(player: Player, voucherId: String): Long =
        service().cooldownRemaining(player, voucherId)

    /** Clears [player]'s stored cooldown for [voucherId]. */
    fun clearCooldown(player: Player, voucherId: String) {
        service().clearCooldown(player, voucherId)
    }

    /** Builds the voucher's item form, or null if the voucher or its `item` section is missing. */
    fun buildVoucherItem(voucherId: String, amount: Int = 1): ItemStack? =
        service().buildVoucherItem(voucherId, amount)

    /** Gives [player] the voucher's item form (overflow drops at their feet). False if it has none. */
    fun giveVoucherItem(player: Player, voucherId: String, amount: Int = 1): Boolean =
        service().giveVoucherItem(player, voucherId, amount)

    /**
     * Gives the voucher's item form to the player named [playerName]: immediately when they
     * are online, otherwise queued and delivered on their next join. False if the voucher
     * or its `item` section is missing.
     */
    fun queueVoucherItem(playerName: String, voucherId: String, amount: Int = 1): Boolean =
        service().queueVoucherItem(playerName, voucherId, amount)

    /**
     * Writes a new voucher file from [settings] — the same keys as a `vouchers/<id>.yml`
     * file, with nested sections as nested maps — then reloads. False if [id] is invalid
     * or taken.
     *
     * ```kotlin
     * ClaimoApi.createVoucher("event2026", mapOf(
     *     "cmd" to "give %player% diamond 3",
     *     "limit" to mapOf("mode" to "per-player", "amount" to 1),
     * ))
     * ```
     */
    fun createVoucher(id: String, settings: Map<String, Any>): Boolean =
        service().createVoucher(id, settings)

    /** Deletes the voucher file for [id] and reloads. False if no such file exists. */
    fun deleteVoucher(id: String): Boolean = service().deleteVoucher(id)

    /**
     * Clones the voucher [templateId] into [amount] hidden one-time codes (like
     * `/code generate`) and reloads. Returns the generated code ids, or null on failure.
     */
    fun generateCodes(templateId: String, amount: Int): List<String>? =
        service().generateCodes(templateId, amount)

    /**
     * Registers a reward [action] executed by voucher `cmd` lines of the form
     * `action:<name> <args>` (matched case-insensitively) instead of dispatching them
     * as commands. Registering an already-known [name] replaces the previous action.
     */
    fun registerRewardAction(name: String, action: RewardAction) {
        service().registerRewardAction(name, action)
    }

    /** Removes the reward action registered under [name]. No-op if absent. */
    fun unregisterRewardAction(name: String) {
        service().unregisterRewardAction(name)
    }

    /** Reloads Claimo's configuration and voucher files from disk. */
    fun reload() {
        service().reload()
    }
}
