package zone.vao.claimo.config

import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import zone.vao.claimo.requirement.RequirementConfig
import zone.vao.claimo.storage.StorageConfig
import zone.vao.claimo.storage.StorageType
import zone.vao.claimo.util.Durations
import zone.vao.claimo.voucher.LimitMode
import zone.vao.claimo.voucher.Voucher
import java.io.File

class ConfigManager(private val plugin: JavaPlugin) {

    @Volatile
    lateinit var config: ClaimoConfig
        private set

    fun load() {
        saveDefaults()

        val main = YamlConfiguration.loadConfiguration(file("config.yml"))
        val messages = YamlConfiguration.loadConfiguration(file("messages.yml"))
        val gui = YamlConfiguration.loadConfiguration(file("gui.yml"))

        val commandName = (main.getString("command") ?: "code")
            .removePrefix("/")
            .trim()
            .ifEmpty { "code" }
        val dialogCommandName = main.getString("dialog_code_input")
            ?.removePrefix("/")
            ?.trim()
            ?.ifEmpty { null }
        val guiListEnabled = main.getBoolean("gui-list-enabled", true)

        config = ClaimoConfig(
            commandName = commandName,
            dialogCommandName = dialogCommandName,
            guiListEnabled = guiListEnabled,
            storage = parseStorage(main.getConfigurationSection("storage")),
            messages = parseMessages(messages),
            gui = parseGui(gui),
            vouchers = loadVouchers(),
        )
    }

    private fun parseStorage(section: ConfigurationSection?): StorageConfig = StorageConfig(
        type = StorageType.from(section?.getString("type")),
        uri = section?.getString("uri") ?: "",
        host = section?.getString("host") ?: "localhost",
        port = section?.getInt("port", 3306) ?: 3306,
        database = section?.getString("database") ?: "claimo",
        username = section?.getString("username") ?: "",
        password = section?.getString("password") ?: "",
        tablePrefix = section?.getString("table-prefix") ?: "claimo_",
        poolSize = (section?.getInt("pool-size", 10) ?: 10).coerceAtLeast(1),
    )

    private fun saveDefaults() {
        for (name in DEFAULT_FILES) syncDefaults(name)
        if (!File(plugin.dataFolder, VOUCHERS_DIR).isDirectory) {
            plugin.saveResource("$VOUCHERS_DIR/$DEFAULT_VOUCHER", false)
        }
    }

    private fun syncDefaults(name: String) {
        val target = file(name)
        if (!target.exists()) {
            plugin.saveResource(name, false)
            return
        }

        val resource = plugin.getResource(name) ?: return
        val defaults = resource.bufferedReader(Charsets.UTF_8).use { YamlConfiguration.loadConfiguration(it) }
        val current = YamlConfiguration.loadConfiguration(target)

        val missing = defaults.getKeys(true)
            .filterNot { defaults.isConfigurationSection(it) }
            .filterNot { current.contains(it) }
        if (missing.isEmpty()) return

        for (key in missing) {
            current.set(key, defaults.get(key))
            current.setComments(key, defaults.getComments(key))
            current.setInlineComments(key, defaults.getInlineComments(key))
        }
        runCatching { current.save(target) }
            .onSuccess { plugin.logger.info("Added ${missing.size} new default value(s) to $name.") }
            .onFailure { plugin.logger.warning("Failed to update $name with new defaults: ${it.message}") }
    }

    private fun file(name: String) = File(plugin.dataFolder, name)

    fun sanitizeId(id: String): String? =
        id.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "").ifEmpty { null }

    fun voucherExists(safeId: String): Boolean =
        File(File(plugin.dataFolder, VOUCHERS_DIR), "$safeId.yml").exists()

    fun saveVoucher(safeId: String, build: (YamlConfiguration) -> Unit) {
        val dir = File(plugin.dataFolder, VOUCHERS_DIR)
        dir.mkdirs()
        val yaml = YamlConfiguration()
        build(yaml)
        yaml.save(File(dir, "$safeId.yml"))
    }

    private fun loadVouchers(): Map<String, Voucher> {
        val dir = File(plugin.dataFolder, VOUCHERS_DIR)
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".yml") }
            ?.sortedBy { it.name }
            ?: return emptyMap()

        return buildMap {
            for (voucherFile in files) {
                val id = voucherFile.nameWithoutExtension
                val yaml = YamlConfiguration.loadConfiguration(voucherFile)
                put(id, parseVoucher(id, yaml, voucherFile.lastModified()))
            }
        }
    }

    private fun parseGui(section: ConfigurationSection?): GuiConfig = GuiConfig(
        title = section?.getString("title") ?: "<dark_gray>Available codes (<page>/<pages>)",
        rows = (section?.getInt("rows", 6) ?: 6).coerceIn(2, 6),
        filler = parseFiller(section?.getString("filler")),
        voucherMaterial = parseMaterial(section?.getString("voucher-material"), Material.PAPER),
        voucherName = section?.getString("voucher-name") ?: "<aqua><voucher>",
        voucherLore = if (section?.isList("voucher-lore") == true) {
            section.getStringList("voucher-lore")
        } else {
            listOf("<gray>Click to redeem this code.")
        },
        previousMaterial = parseMaterial(section?.getString("previous-material"), Material.ARROW),
        previousName = section?.getString("previous-name") ?: "<yellow>« Previous page",
        nextMaterial = parseMaterial(section?.getString("next-material"), Material.ARROW),
        nextName = section?.getString("next-name") ?: "<yellow>Next page »",
    )

    private fun parseMaterial(name: String?, default: Material): Material {
        if (name == null) return default
        return Material.matchMaterial(name.trim()) ?: run {
            plugin.logger.warning("Unknown material '$name' in the gui section; falling back to ${default.name}.")
            default
        }
    }

    private fun parseFiller(name: String?): Material? {
        if (name == null) return Material.GRAY_STAINED_GLASS_PANE
        val trimmed = name.trim()
        if (trimmed.equals("none", ignoreCase = true) || trimmed.equals("air", ignoreCase = true)) return null
        return Material.matchMaterial(trimmed) ?: Material.GRAY_STAINED_GLASS_PANE
    }

    private fun parseMessages(section: ConfigurationSection?): Messages {
        val prefix = section?.getString("prefix") ?: ""
        val raw = buildMap {
            section?.getKeys(false)
                ?.filter { it != "prefix" }
                ?.forEach { key -> section.getString(key)?.let { put(key, it) } }
        }
        return Messages(prefix, raw)
    }

    private fun parseVoucher(id: String, section: ConfigurationSection, defaultCreatedAt: Long): Voucher {
        val limit = section.getConfigurationSection("limit")
        return Voucher(
            id = id,
            commands = parseCommands(section.get("cmd")),
            console = section.getBoolean("console", true),
            hidden = section.getBoolean("hide", false),
            limitMode = parseLimitMode(limit?.getString("mode")),
            limitAmount = (limit?.getInt("amount", 1) ?: 1).coerceAtLeast(1),
            requirements = parseRequirements(id, section.getMapList("requirements")),
            expiresAt = parseExpiry(id, section, defaultCreatedAt),
        )
    }

    private fun parseExpiry(id: String, section: ConfigurationSection, defaultCreatedAt: Long): Long? {
        val raw = section.getString("expires")?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val duration = Durations.parseMillis(raw)
        if (duration == null) {
            plugin.logger.warning("Voucher '$id' has an invalid 'expires' value '$raw'; ignoring it.")
            return null
        }
        val createdAt = if (section.contains("created")) section.getLong("created") else defaultCreatedAt
        return createdAt + duration
    }

    private fun parseLimitMode(value: String?): LimitMode = when (value?.lowercase()?.replace('-', '_')) {
        "global" -> LimitMode.GLOBAL
        "per_player" -> LimitMode.PER_PLAYER
        else -> LimitMode.NONE
    }

    private fun parseCommands(value: Any?): List<String> = when (value) {
        is String -> listOf(value)
        is List<*> -> value.mapNotNull { it?.toString() }
        else -> emptyList()
    }

    private fun parseRequirements(voucherId: String, list: List<Map<*, *>>): List<RequirementConfig> =
        list.mapNotNull { entry ->
            val data = entry.entries.associate { (k, v) -> k.toString() to v }
            val type = data["type"]?.toString()
            if (type.isNullOrBlank()) {
                plugin.logger.warning("Voucher '$voucherId' has a requirement without a 'type'; skipping it.")
                null
            } else {
                RequirementConfig(type, data)
            }
        }

    private companion object {
        val DEFAULT_FILES = listOf("config.yml", "messages.yml", "gui.yml")
        const val VOUCHERS_DIR = "vouchers"
        const val DEFAULT_VOUCHER = "test.yml"
    }
}
