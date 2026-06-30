package zone.vao.claimo.stats

import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * Read-only view of the per-player progress Claimo tracks, exposed to addons.
 *
 * All methods read live player data and should be called from the main server thread.
 */
interface ClaimoStats {

    /** Total blocks the player has broken while Claimo was tracking. */
    fun blocksMined(player: Player): Int

    /**
     * Blocks mined by the player, filtered by material type. A non-empty [whitelist]
     * counts only those materials (minus any in [blacklist]); otherwise a non-empty
     * [blacklist] counts everything except those materials; both empty is the total.
     */
    fun blocksMined(player: Player, whitelist: Set<Material>, blacklist: Set<Material>): Int

    /** Total time the player has spent on the server, in seconds. */
    fun playtimeSeconds(player: Player): Long

    /**
     * Number of qualifying chat messages the player has sent under the given policy:
     * messages of at least [minLength] characters, counted at most once per
     * [delaySeconds]. Counting only runs for (minLength, delaySeconds) pairs actually
     * used by a `messages_sent` requirement; other pairs always read 0.
     */
    fun messagesSent(player: Player, minLength: Int, delaySeconds: Int): Int
}
