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
)
