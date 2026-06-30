package zone.vao.claimo.voucher

/** How a voucher's redemption count is limited. */
enum class LimitMode {
    /** No limit — the code can be redeemed any number of times. */
    NONE,

    /** A shared limit across all players ("first come, first served"). */
    GLOBAL,

    /** A separate limit for each player. */
    PER_PLAYER,
}
