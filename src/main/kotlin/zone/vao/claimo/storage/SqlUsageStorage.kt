package zone.vao.claimo.storage

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.util.UUID

class SqlUsageStorage(hikariConfig: HikariConfig, tablePrefix: String) : UsageStorage {

    private val globalTable = "${tablePrefix}global_usage"
    private val playerTable = "${tablePrefix}player_usage"
    private val historyTable = "${tablePrefix}history"
    private val dataSource = HikariDataSource(hikariConfig)

    init {
        dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS $globalTable " +
                        "(voucher_id VARCHAR(64) PRIMARY KEY, uses INT NOT NULL)"
                )
                st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS $playerTable " +
                        "(uuid VARCHAR(36) NOT NULL, voucher_id VARCHAR(64) NOT NULL, uses INT NOT NULL, " +
                        "PRIMARY KEY (uuid, voucher_id))"
                )
                st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS $historyTable " +
                        "(voucher_id VARCHAR(64) NOT NULL, uuid VARCHAR(36) NOT NULL, " +
                        "player VARCHAR(16) NOT NULL, ts BIGINT NOT NULL, price DOUBLE PRECISION NOT NULL)"
                )
            }
        }
    }

    override fun loadGlobal(): Map<String, Int> {
        val result = HashMap<String, Int>()
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT voucher_id, uses FROM $globalTable").use { ps ->
                ps.executeQuery().use { rs ->
                    while (rs.next()) result[rs.getString(1)] = rs.getInt(2)
                }
            }
        }
        return result
    }

    override fun loadPlayer(uuid: UUID): Map<String, Int> {
        val result = HashMap<String, Int>()
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT voucher_id, uses FROM $playerTable WHERE uuid = ?").use { ps ->
                ps.setString(1, uuid.toString())
                ps.executeQuery().use { rs ->
                    while (rs.next()) result[rs.getString(1)] = rs.getInt(2)
                }
            }
        }
        return result
    }

    override fun incrementGlobal(voucherId: String, max: Int): Boolean =
        incrementIfBelow(
            update = "UPDATE $globalTable SET uses = uses + 1 WHERE voucher_id = ? AND uses < ?",
            exists = "SELECT 1 FROM $globalTable WHERE voucher_id = ?",
            insert = "INSERT INTO $globalTable (voucher_id, uses) VALUES (?, 1)",
            keys = listOf(voucherId),
            max = max,
        )

    override fun incrementPlayer(uuid: UUID, voucherId: String, max: Int): Boolean =
        incrementIfBelow(
            update = "UPDATE $playerTable SET uses = uses + 1 WHERE uuid = ? AND voucher_id = ? AND uses < ?",
            exists = "SELECT 1 FROM $playerTable WHERE uuid = ? AND voucher_id = ?",
            insert = "INSERT INTO $playerTable (uuid, voucher_id, uses) VALUES (?, ?, 1)",
            keys = listOf(uuid.toString(), voucherId),
            max = max,
        )

    override fun decrementGlobal(voucherId: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE $globalTable SET uses = uses - 1 WHERE voucher_id = ? AND uses > 0").use { ps ->
                ps.setString(1, voucherId)
                ps.executeUpdate()
            }
        }
    }

    override fun decrementPlayer(uuid: UUID, voucherId: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE $playerTable SET uses = uses - 1 WHERE uuid = ? AND voucher_id = ? AND uses > 0"
            ).use { ps ->
                ps.setString(1, uuid.toString())
                ps.setString(2, voucherId)
                ps.executeUpdate()
            }
        }
    }

    private fun incrementIfBelow(update: String, exists: String, insert: String, keys: List<String>, max: Int): Boolean {
        if (max <= 0) return false
        dataSource.connection.use { conn ->
            repeat(2) {
                conn.prepareStatement(update).use { ps ->
                    keys.forEachIndexed { i, key -> ps.setString(i + 1, key) }
                    ps.setInt(keys.size + 1, max)
                    if (ps.executeUpdate() > 0) return true
                }
                val rowExists = conn.prepareStatement(exists).use { ps ->
                    keys.forEachIndexed { i, key -> ps.setString(i + 1, key) }
                    ps.executeQuery().use { it.next() }
                }
                if (rowExists) return false
                val inserted = runCatching {
                    conn.prepareStatement(insert).use { ps ->
                        keys.forEachIndexed { i, key -> ps.setString(i + 1, key) }
                        ps.executeUpdate()
                    }
                }.isSuccess
                if (inserted) return true
            }
        }
        return false
    }

    override fun recordHistory(entry: RedeemHistoryEntry) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO $historyTable (voucher_id, uuid, player, ts, price) VALUES (?, ?, ?, ?, ?)"
            ).use { ps ->
                ps.setString(1, entry.voucherId)
                ps.setString(2, entry.uuid.toString())
                ps.setString(3, entry.playerName)
                ps.setLong(4, entry.timestamp)
                ps.setDouble(5, entry.price)
                ps.executeUpdate()
            }
        }
    }

    override fun voucherHistory(voucherId: String, limit: Int): List<RedeemHistoryEntry> =
        queryHistory("voucher_id", voucherId, limit)

    override fun playerHistory(uuid: UUID, limit: Int): List<RedeemHistoryEntry> =
        queryHistory("uuid", uuid.toString(), limit)

    override fun uniquePlayers(voucherId: String): Int {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM $playerTable WHERE voucher_id = ? AND uses > 0").use { ps ->
                ps.setString(1, voucherId)
                ps.executeQuery().use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
            }
        }
    }

    private fun queryHistory(column: String, value: String, limit: Int): List<RedeemHistoryEntry> {
        val result = ArrayList<RedeemHistoryEntry>()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT voucher_id, uuid, player, ts, price FROM $historyTable WHERE $column = ? ORDER BY ts DESC LIMIT ?"
            ).use { ps ->
                ps.setString(1, value)
                ps.setInt(2, limit)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        result += RedeemHistoryEntry(
                            voucherId = rs.getString(1),
                            uuid = UUID.fromString(rs.getString(2)),
                            playerName = rs.getString(3),
                            timestamp = rs.getLong(4),
                            price = rs.getDouble(5),
                        )
                    }
                }
            }
        }
        return result
    }

    override fun deleteVoucher(voucherId: String) {
        dataSource.connection.use { conn ->
            for (table in arrayOf(globalTable, playerTable, historyTable)) {
                conn.prepareStatement("DELETE FROM $table WHERE voucher_id = ?").use { ps ->
                    ps.setString(1, voucherId)
                    ps.executeUpdate()
                }
            }
        }
    }

    override fun close() {
        dataSource.close()
    }
}
