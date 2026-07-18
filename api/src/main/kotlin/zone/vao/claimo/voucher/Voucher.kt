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
    /** Optional standalone command that redeems this voucher directly (e.g. `testodbierz`), or `null`. */
    val redeemCommand: String? = null,
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean =
        expiresAt != null && now >= expiresAt
}
