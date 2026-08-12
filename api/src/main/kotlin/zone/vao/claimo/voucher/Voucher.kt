package zone.vao.claimo.voucher

import zone.vao.claimo.requirement.RequirementConfig

data class Voucher(
    val id: String,
    val commands: List<String>,
    val console: Boolean,
    val hidden: Boolean,
    val limitMode: LimitMode,
    val limitAmount: Int,
    val requirements: List<RequirementConfig>,
    val expiresAt: Long? = null,
    val redeemCommand: String? = null,
    val item: VoucherItem? = null,
    val startsAt: Long? = null,
    val cooldownMillis: Long? = null,
    val random: Boolean = false,
    val commandChances: List<Double> = emptyList(),
    val price: Double = 0.0,
    val effects: VoucherEffects? = null,
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean =
        expiresAt != null && now >= expiresAt

    fun isNotStarted(now: Long = System.currentTimeMillis()): Boolean =
        startsAt != null && now < startsAt

    fun isAvailable(now: Long = System.currentTimeMillis()): Boolean =
        !isExpired(now) && !isNotStarted(now)
}
