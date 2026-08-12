package zone.vao.claimo.config

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import zone.vao.claimo.requirement.RequirementConfig
import zone.vao.claimo.storage.StorageConfig
import zone.vao.claimo.storage.StorageType
import zone.vao.claimo.update.UpdateConfig
import zone.vao.claimo.util.Durations
import zone.vao.claimo.voucher.LimitMode
import zone.vao.claimo.voucher.Voucher
import zone.vao.claimo.voucher.VoucherItem
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

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
            update = parseUpdate(main.getConfigurationSection("update-checker")),
            redeemSound = parseSound(main.getConfigurationSection("redeem-sound")),
            logRedeems = main.getBoolean("logging.redeems", true),
            logAdmin = main.getBoolean("logging.admin", true),
            messages = parseMessages(messages),
            gui = parseGui(gui),
            vouchers = loadVouchers(),
            placeholderTrue = main.getString("placeholders.true-value") ?: "true",
            placeholderFalse = main.getString("placeholders.false-value") ?: "false",
        )
    }

    private fun parseSound(section: ConfigurationSection?): SoundConfig {
        if (section == null || !section.getBoolean("enabled", true)) return SoundConfig(null)
        val rawKey = section.getString("key")?.trim().orEmpty()
        if (rawKey.isEmpty()) return SoundConfig(null)
        val key = runCatching { Key.key(rawKey) }.getOrElse {
            plugin.logger.warning("Invalid redeem-sound key '$rawKey'; disabling the redeem sound.")
            return SoundConfig(null)
        }
        val source = runCatching { Sound.Source.valueOf(section.getString("source", "MASTER")!!.uppercase()) }
            .getOrDefault(Sound.Source.MASTER)
        val volume = section.getDouble("volume", 1.0).toFloat()
        val pitch = section.getDouble("pitch", 1.0).toFloat()
        return SoundConfig(Sound.sound(key, source, volume, pitch))
    }

    private fun parseUpdate(section: ConfigurationSection?): UpdateConfig = UpdateConfig(
        enabled = section?.getBoolean("enabled", true) ?: true,
        notifyAdmins = section?.getBoolean("notify-admins", true) ?: true,
        intervalHours = (section?.getLong("interval-hours", 6L) ?: 6L).coerceAtLeast(1L),
    )

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

    fun readVoucher(safeId: String): YamlConfiguration? {
        val file = File(File(plugin.dataFolder, VOUCHERS_DIR), "$safeId.yml")
        if (!file.isFile) return null
        return YamlConfiguration.loadConfiguration(file)
    }

    fun deleteVoucher(safeId: String): Boolean =
        File(File(plugin.dataFolder, VOUCHERS_DIR), "$safeId.yml").delete()


    fun generateCodes(templateId: String, amount: Int): File? {
        val template = readVoucher(templateId) ?: return null
        template.set("hide", true)
        template.set("limit.mode", "global")
        template.set("limit.amount", 1)
        template.set("redeem-command", null)
        template.set("created", System.currentTimeMillis())

        val dir = File(plugin.dataFolder, VOUCHERS_DIR)
        val codes = ArrayList<String>(amount)
        repeat(amount) {
            var code: String
            do {
                code = "$templateId-" + buildString { repeat(6) { append(GEN_CHARS.random()) } }
            } while (voucherExists(code))
            template.save(File(dir, "$code.yml"))
            codes += code
        }

        val out = File(plugin.dataFolder, "generated")
        out.mkdirs()
        val list = File(out, "$templateId-${System.currentTimeMillis()}.txt")
        list.writeText(codes.joinToString(System.lineSeparator()))
        return list
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
        val (commands, chances) = parseCommands(id, section.get("cmd"))
        return Voucher(
            id = id,
            commands = commands,
            console = section.getBoolean("console", true),
            hidden = section.getBoolean("hide", false),
            limitMode = parseLimitMode(limit?.getString("mode")),
            limitAmount = (limit?.getInt("amount", 1) ?: 1).coerceAtLeast(1),
            requirements = parseRequirements(id, section.getMapList("requirements")),
            expiresAt = parseExpiry(id, section, defaultCreatedAt),
            redeemCommand = parseRedeemCommand(section.getString("redeem-command")),
            item = parseItem(id, section.getConfigurationSection("item")),
            startsAt = parseStarts(id, section, defaultCreatedAt),
            cooldownMillis = parseCooldown(id, section),
            random = section.getBoolean("random", false),
            commandChances = chances,
        )
    }

    private fun parseCooldown(id: String, section: ConfigurationSection): Long? {
        val raw = section.getString("cooldown")?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return Durations.parseMillis(raw) ?: run {
            plugin.logger.warning("Voucher '$id' has an invalid 'cooldown' value '$raw'; ignoring it.")
            null
        }
    }

    private fun parseStarts(id: String, section: ConfigurationSection, defaultCreatedAt: Long): Long? {
        val raw = section.getString("starts")?.trim().orEmpty()
        if (raw.isEmpty()) return null
        Durations.parseMillis(raw)?.let { duration ->
            val createdAt = if (section.contains("created")) section.getLong("created") else defaultCreatedAt
            return createdAt + duration
        }
        return parseDateTime(raw) ?: run {
            plugin.logger.warning("Voucher '$id' has an invalid 'starts' value '$raw' (use a duration like 2d or a date like 2026-08-15 18:00); ignoring it.")
            null
        }
    }

    private fun parseDateTime(raw: String): Long? = runCatching {
        val date = if (raw.length <= 10) LocalDate.parse(raw).atStartOfDay() else LocalDateTime.parse(raw.replace(' ', 'T'))
        date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()

    private fun parseItem(id: String, section: ConfigurationSection?): VoucherItem? {
        if (section == null) return null
        val material = section.getString("material")?.let { name ->
            Material.matchMaterial(name.trim()) ?: run {
                plugin.logger.warning("Voucher '$id' has an unknown item material '$name'; falling back to PAPER.")
                null
            }
        }

        var cmdInt: Int? = null
        var cmdFloats = emptyList<Float>()
        var cmdFlags = emptyList<Boolean>()
        var cmdStrings = emptyList<String>()
        var cmdColors = emptyList<Color>()
        when (val cmd = section.get("custom_model_data")) {
            is Number -> cmdInt = cmd.toInt()
            is List<*> -> cmdFloats = cmd.mapNotNull { (it as? Number)?.toFloat() }
            is ConfigurationSection -> {
                cmdFloats = cmd.getFloatList("floats")
                cmdFlags = cmd.getBooleanList("flags")
                cmdStrings = cmd.getStringList("strings")
                cmdColors = cmd.getStringList("colors").mapNotNull { parseColor(id, it) }
            }
        }

        return VoucherItem(
            material = material,
            name = section.getString("name"),
            lore = section.getStringList("lore"),
            itemModel = section.getString("item_model")?.trim()?.ifEmpty { null },
            customModelData = cmdInt,
            cmdFloats = cmdFloats,
            cmdFlags = cmdFlags,
            cmdStrings = cmdStrings,
            cmdColors = cmdColors,
            nexoItem = section.getString("nexo_item")?.trim()?.ifEmpty { null },
            iaItem = section.getString("ia_item")?.trim()?.ifEmpty { null },
            ceItem = section.getString("ce_item")?.trim()?.ifEmpty { null },
        )
    }

    private fun parseColor(id: String, raw: String): Color? =
        runCatching { Color.fromRGB(raw.trim().removePrefix("#").toInt(16)) }.getOrElse {
            plugin.logger.warning("Voucher '$id' has an invalid custom_model_data color '$raw' (expected hex like #FF0000); ignoring it.")
            null
        }

    private fun parseRedeemCommand(raw: String?): String? =
        raw?.removePrefix("/")?.trim()?.substringBefore(' ')?.ifEmpty { null }

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

    private fun parseCommands(id: String, value: Any?): Pair<List<String>, List<Double>> {
        val entries = when (value) {
            is String -> return listOf(value) to emptyList()
            is List<*> -> value
            else -> return emptyList<String>() to emptyList()
        }
        val commands = mutableListOf<String>()
        val chances = mutableListOf<Double>()
        var weighted = false
        for (entry in entries) {
            when (entry) {
                is Map<*, *> -> {
                    val command = (entry["command"] ?: entry["cmd"])?.toString()
                    if (command == null) {
                        plugin.logger.warning("Voucher '$id' has a cmd entry without a 'command'; skipping it.")
                        continue
                    }
                    commands += command
                    val chance = (entry["chance"] as? Number)?.toDouble()
                    if (chance != null) weighted = true
                    chances += (chance ?: 1.0).coerceAtLeast(0.0)
                }
                else -> {
                    entry?.toString()?.let { commands += it; chances += 1.0 }
                }
            }
        }
        return commands to (if (weighted) chances else emptyList())
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

        const val GEN_CHARS = "abcdefghjkmnpqrstuvwxyz23456789"
    }
}
