package zone.vao.claimo.storage

import com.zaxxer.hikari.HikariConfig
import org.bukkit.plugin.Plugin
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object StorageFactory {

    fun create(plugin: Plugin, config: StorageConfig): UsageStorage = when (config.type) {
        StorageType.YAML -> YamlUsageStorage(File(plugin.dataFolder, "usage.yml"))
        StorageType.MONGODB -> MongoUsageStorage(mongoConnectionString(config), config.database, config.tablePrefix)
        else -> SqlUsageStorage(buildHikari(plugin, config), config.tablePrefix)
    }

    private fun mongoConnectionString(config: StorageConfig): String {
        if (config.uri.isNotEmpty()) return config.uri
        val credentials = if (config.username.isNotEmpty()) {
            "${encode(config.username)}:${encode(config.password)}@"
        } else {
            ""
        }
        return "mongodb://$credentials${config.host}:${config.port}"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private fun buildHikari(plugin: Plugin, config: StorageConfig): HikariConfig {
        val hikari = HikariConfig()
        hikari.poolName = "claimo-pool"

        when (config.type) {
            StorageType.SQLITE -> {
                val file = File(plugin.dataFolder, "data.db")
                file.parentFile?.mkdirs()
                hikari.jdbcUrl = "jdbc:sqlite:${file.absolutePath}"
                hikari.driverClassName = "org.sqlite.JDBC"
                hikari.maximumPoolSize = 1
                hikari.connectionInitSql = "PRAGMA busy_timeout=5000"
            }

            StorageType.MYSQL -> {
                hikari.jdbcUrl = "jdbc:mysql://${config.host}:${config.port}/${config.database}"
                hikari.driverClassName = "com.mysql.cj.jdbc.Driver"
                hikari.username = config.username
                hikari.password = config.password
                hikari.maximumPoolSize = config.poolSize
            }

            StorageType.POSTGRESQL -> {
                hikari.jdbcUrl = "jdbc:postgresql://${config.host}:${config.port}/${config.database}"
                hikari.driverClassName = "org.postgresql.Driver"
                hikari.username = config.username
                hikari.password = config.password
                hikari.maximumPoolSize = config.poolSize
            }

            StorageType.YAML, StorageType.MONGODB -> error("${config.type} storage does not use a JDBC connection pool")
        }

        runCatching { Class.forName(hikari.driverClassName) }
            .onFailure { throw IllegalStateException("JDBC driver ${hikari.driverClassName} is unavailable", it) }
        return hikari
    }
}
