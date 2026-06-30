package zone.vao.claimo.storage

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.util.UUID

class SqlUsageStorage(hikariConfig: HikariConfig, tablePrefix: String) : UsageStorage {

    private val globalTable = "${tablePrefix}global_usage"
    private val playerTable = "${tablePrefix}player_usage"
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

    override fun saveGlobal(voucherId: String, uses: Int) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE $globalTable SET uses = ? WHERE voucher_id = ?").use { update ->
                update.setInt(1, uses)
                update.setString(2, voucherId)
                if (update.executeUpdate() == 0) {
                    conn.prepareStatement("INSERT INTO $globalTable (voucher_id, uses) VALUES (?, ?)").use { insert ->
                        insert.setString(1, voucherId)
                        insert.setInt(2, uses)
                        insert.executeUpdate()
                    }
                }
            }
        }
    }

    override fun savePlayer(uuid: UUID, voucherId: String, uses: Int) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE $playerTable SET uses = ? WHERE uuid = ? AND voucher_id = ?").use { update ->
                update.setInt(1, uses)
                update.setString(2, uuid.toString())
                update.setString(3, voucherId)
                if (update.executeUpdate() == 0) {
                    conn.prepareStatement(
                        "INSERT INTO $playerTable (uuid, voucher_id, uses) VALUES (?, ?, ?)"
                    ).use { insert ->
                        insert.setString(1, uuid.toString())
                        insert.setString(2, voucherId)
                        insert.setInt(3, uses)
                        insert.executeUpdate()
                    }
                }
            }
        }
    }

    override fun close() {
        dataSource.close()
    }
}
