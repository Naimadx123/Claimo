package zone.vao.claimo.storage

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

class YamlUsageStorage(private val file: File) : UsageStorage {

    private val lock = Any()
    private val yaml = YamlConfiguration()

    init {
        synchronized(lock) {
            if (file.exists()) runCatching { yaml.load(file) }
        }
    }

    override fun loadGlobal(): Map<String, Int> = synchronized(lock) {
        readSection("global")
    }

    override fun loadPlayer(uuid: UUID): Map<String, Int> = synchronized(lock) {
        readSection("players.$uuid")
    }

    override fun saveGlobal(voucherId: String, uses: Int): Unit = synchronized(lock) {
        yaml.set("global.$voucherId", uses)
        persist()
    }

    override fun savePlayer(uuid: UUID, voucherId: String, uses: Int): Unit = synchronized(lock) {
        yaml.set("players.$uuid.$voucherId", uses)
        persist()
    }

    override fun close(): Unit = synchronized(lock) {
        persist()
    }

    private fun readSection(path: String): Map<String, Int> {
        val section = yaml.getConfigurationSection(path) ?: return emptyMap()
        return section.getKeys(false).associateWith { section.getInt(it) }
    }

    private fun persist() {
        runCatching { yaml.save(file) }
    }
}
