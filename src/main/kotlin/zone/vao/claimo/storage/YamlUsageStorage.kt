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

    override fun incrementGlobal(voucherId: String, max: Int): Boolean = synchronized(lock) {
        incrementIfBelow("global.$voucherId", max)
    }

    override fun incrementPlayer(uuid: UUID, voucherId: String, max: Int): Boolean = synchronized(lock) {
        incrementIfBelow("players.$uuid.$voucherId", max)
    }

    override fun decrementGlobal(voucherId: String): Unit = synchronized(lock) {
        decrement("global.$voucherId")
    }

    override fun decrementPlayer(uuid: UUID, voucherId: String): Unit = synchronized(lock) {
        decrement("players.$uuid.$voucherId")
    }

    private fun incrementIfBelow(path: String, max: Int): Boolean {
        val current = yaml.getInt(path, 0)
        if (current >= max) return false
        yaml.set(path, current + 1)
        persist()
        return true
    }

    private fun decrement(path: String) {
        val current = yaml.getInt(path, 0)
        if (current <= 0) return
        yaml.set(path, current - 1)
        persist()
    }

    override fun deleteVoucher(voucherId: String): Unit = synchronized(lock) {
        yaml.set("global.$voucherId", null)
        val players = yaml.getConfigurationSection("players")
        players?.getKeys(false)?.forEach { uuid -> yaml.set("players.$uuid.$voucherId", null) }
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
