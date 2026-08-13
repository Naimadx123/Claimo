package zone.vao.claimo.reward

import org.bukkit.entity.Player

/**
 * A custom reward executed by a voucher `cmd` line of the form `action:<name> <args>`,
 * instead of dispatching the line as a command.
 *
 * ```yaml
 * cmd:
 *   - "action:discord-role vip"
 * ```
 *
 * Register implementations through `ClaimoApi.registerRewardAction`. The action runs on
 * the redeeming player's scheduler thread (Folia-safe); [args] is everything after the
 * action name, with `%player%` and PlaceholderAPI placeholders already resolved.
 */
fun interface RewardAction {

    fun execute(player: Player, args: String)
}
