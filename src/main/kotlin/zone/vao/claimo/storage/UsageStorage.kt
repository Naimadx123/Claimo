package zone.vao.claimo.storage

import java.util.UUID

interface UsageStorage {

    fun loadGlobal(): Map<String, Int>

    fun loadPlayer(uuid: UUID): Map<String, Int>

    fun saveGlobal(voucherId: String, uses: Int)

    fun savePlayer(uuid: UUID, voucherId: String, uses: Int)

    fun deleteVoucher(voucherId: String)

    fun close()
}
