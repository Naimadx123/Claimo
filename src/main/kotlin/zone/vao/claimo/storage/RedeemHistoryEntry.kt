package zone.vao.claimo.storage

import java.util.UUID

data class RedeemHistoryEntry(
    val voucherId: String,
    val uuid: UUID,
    val playerName: String,
    val timestamp: Long,
    val price: Double,
)
