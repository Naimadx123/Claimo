package zone.vao.claimo.voucher

/**
 * The outcome of a redeem attempt, reported by `ClaimoApi.redeemWithResult`.
 *
 * Every value except [SUCCESS] and [AWAITING_CONFIRMATION] means the player has already
 * been sent the matching message — addons don't need to message them again.
 */
enum class RedeemResult {

    /** The reward was executed and the redemption recorded. */
    SUCCESS,

    /** No voucher with that id is loaded. */
    NOT_FOUND,

    /** The voucher's `expires` moment has passed. */
    EXPIRED,

    /** The voucher's `starts` moment hasn't been reached yet. */
    NOT_STARTED,

    /** The player redeemed this voucher more recently than its `cooldown` allows. */
    ON_COOLDOWN,

    /** The voucher's `limit` is exhausted for this player or globally. */
    LIMIT_REACHED,

    /** The voucher has a `price` the player can't pay. */
    CANNOT_AFFORD,

    /** The voucher has a `price` but no Vault economy provider is available. */
    PAYMENT_UNAVAILABLE,

    /**
     * The voucher has a `price` and the player was asked to confirm the payment —
     * a confirmation dialog opened (1.21.7+) or a repeat-redeem prompt was sent.
     * The follow-up attempt reports its own result.
     */
    AWAITING_CONFIRMATION,

    /** At least one requirement is unsatisfied; the player saw the checklist. */
    REQUIREMENTS_NOT_MET,

    /** A `PlayerRedeemVoucherEvent` listener cancelled the redemption. */
    CANCELLED,

    /** The player went offline while requirements were being checked. */
    PLAYER_OFFLINE,
}
