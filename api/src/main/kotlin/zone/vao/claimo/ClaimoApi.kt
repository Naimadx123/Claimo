package zone.vao.claimo

import org.bukkit.entity.Player
import org.jetbrains.annotations.ApiStatus
import zone.vao.claimo.requirement.RequirementFactory
import zone.vao.claimo.requirement.RequirementInput
import zone.vao.claimo.requirement.RequirementRegistry
import zone.vao.claimo.stats.ClaimoStats
import zone.vao.claimo.voucher.Voucher

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

    /** Reloads Claimo's configuration and voucher files from disk. */
    fun reload() {
        service().reload()
    }
}
