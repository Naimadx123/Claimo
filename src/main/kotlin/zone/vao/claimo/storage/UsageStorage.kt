package zone.vao.claimo.storage

import java.util.UUID

interface UsageStorage {

    fun loadGlobal(): Map<String, Int>

    fun loadPlayer(uuid: UUID): Map<String, Int>

    /**
     * Atomically increments the counter, but only while its current value is below [max].
     * Returns true when the use was reserved — this is the authoritative limit check, safe
     * against concurrent redeems from other threads and other servers on a shared backend.
     */
    fun incrementGlobal(voucherId: String, max: Int): Boolean

    fun incrementPlayer(uuid: UUID, voucherId: String, max: Int): Boolean

    /** Rolls back a reservation made by the matching increment. Never drops below zero. */
    fun decrementGlobal(voucherId: String)

    fun decrementPlayer(uuid: UUID, voucherId: String)

    fun recordHistory(entry: RedeemHistoryEntry)

    fun voucherHistory(voucherId: String, limit: Int): List<RedeemHistoryEntry>

    fun playerHistory(uuid: UUID, limit: Int): List<RedeemHistoryEntry>

    fun uniquePlayers(voucherId: String): Int

    fun deleteVoucher(voucherId: String)

    fun close()
}
