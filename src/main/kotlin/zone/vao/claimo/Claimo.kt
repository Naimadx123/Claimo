package zone.vao.claimo

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bstats.bukkit.Metrics
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import zone.vao.claimo.command.AdminSuggestionFilter
import zone.vao.claimo.command.VoucherCommand
import zone.vao.claimo.config.ConfigManager
import zone.vao.claimo.creator.VoucherCreator
import zone.vao.claimo.gui.VoucherMenu
import zone.vao.claimo.log.ActionLog
import zone.vao.claimo.prompt.CodePrompt
import zone.vao.claimo.prompt.PriceConfirm
import zone.vao.claimo.requirement.RequirementConfig
import zone.vao.claimo.requirement.RequirementGroups
import zone.vao.claimo.requirement.RequirementInput
import zone.vao.claimo.requirement.RequirementGroups
import zone.vao.claimo.requirement.RequirementRegistry
import zone.vao.claimo.requirement.builtin.*
import zone.vao.claimo.reward.RewardAction
import zone.vao.claimo.stats.ClaimoStats
import zone.vao.claimo.stats.MessagePolicy
import zone.vao.claimo.stats.StatsService
import zone.vao.claimo.storage.StorageFactory
import zone.vao.claimo.storage.UsageStorage
import zone.vao.claimo.update.UpdateChecker
import zone.vao.claimo.usage.UsageService
import zone.vao.claimo.util.Durations
import zone.vao.claimo.voucher.*
import java.util.concurrent.CompletableFuture

@Suppress("UnstableApiUsage")
class Claimo : JavaPlugin(), ClaimoService {

    lateinit var requirementRegistry: RequirementRegistry
        private set
    lateinit var statsService: StatsService
        private set
    lateinit var configManager: ConfigManager
        private set
    lateinit var voucherService: VoucherService
        private set
    lateinit var voucherItemService: VoucherItemService
        private set
    lateinit var pendingGiveService: PendingGiveService
        private set
    lateinit var usageService: UsageService
        private set
    private lateinit var usageStorage: UsageStorage
    lateinit var voucherMenu: VoucherMenu
        private set
    var voucherCreator: VoucherCreator? = null
        private set
    var codePrompt: CodePrompt? = null
        private set
    var priceConfirm: PriceConfirm? = null
        private set
    lateinit var actionLog: ActionLog
        private set
    private lateinit var updateChecker: UpdateChecker

    override fun onEnable() {
        requirementRegistry = RequirementRegistry(logger)
        statsService = StatsService(this)
        server.pluginManager.registerEvents(statsService, this)

        configManager = ConfigManager(this)
        configManager.load()

        registerBuiltinRequirements()

        usageStorage = StorageFactory.create(this, configManager.config.storage)
        usageService = UsageService(this, usageStorage)
        server.pluginManager.registerEvents(usageService, this)
        usageService.load()

        voucherService = VoucherService(this)

        voucherItemService = VoucherItemService(this)
        server.pluginManager.registerEvents(voucherItemService, this)

        pendingGiveService = PendingGiveService(this)
        server.pluginManager.registerEvents(pendingGiveService, this)

        voucherMenu = VoucherMenu(this)
        server.pluginManager.registerEvents(voucherMenu, this)

        reload()

        voucherCreator = createDialogComponent("zone.vao.claimo.creator.DialogVoucherCreator", "the in-game code creator")
        codePrompt = createDialogComponent("zone.vao.claimo.prompt.DialogCodePrompt", "the code input dialog")
        priceConfirm = createDialogComponent("zone.vao.claimo.prompt.DialogPriceConfirm", "the price confirmation dialog")

        actionLog = ActionLog(this)
        server.pluginManager.registerEvents(actionLog, this)

        updateChecker = UpdateChecker(this)
        server.pluginManager.registerEvents(updateChecker, this)
        updateChecker.start()

        registerPlaceholders()
        registerMiniPlaceholders()

        ClaimoApi.init(this)

        registerCommand()
        server.pluginManager.registerEvents(AdminSuggestionFilter(this), this)

        Metrics(this, 33426)

        logger.info("Claimo enabled — redeem command: /${configManager.config.commandName}")
    }

    override fun reload() {
        configManager.load()
        statsService.trackMaterials(trackedBlockMaterials())
        statsService.configureMessagePolicies(messagePolicies())
        if (::updateChecker.isInitialized) updateChecker.start()
        refreshClientCommands()
    }

    private fun refreshClientCommands() {
        for (player in server.onlinePlayers) {
            player.scheduler.run(this, { player.updateCommands() }, null)
        }
    }

    private fun messagePolicies(): Set<MessagePolicy> =
        configManager.config.vouchers.values
            .flatMap { it.flattenedRequirements() }
            .filter { it.type.equals("messages_sent", ignoreCase = true) }
            .mapTo(HashSet()) { MessagePolicy.from(it) }

    override val requirements: RequirementRegistry get() = requirementRegistry
    override val stats: ClaimoStats get() = statsService
    override fun vouchers(): Collection<Voucher> = configManager.config.vouchers.values
    override fun voucher(id: String): Voucher? = configManager.config.vouchers[id]

    override fun redeem(player: Player, voucherId: String) {
        voucherService.redeem(player, voucherId)
    }

    override fun redeemWithResult(player: Player, voucherId: String): CompletableFuture<RedeemResult> =
        voucherService.redeem(player, voucherId)

    override fun globalUses(voucherId: String): Int = usageService.globalUses(voucherId)

    override fun playerUses(player: Player, voucherId: String): Int = usageService.playerUses(player, voucherId)

    override fun cooldownRemaining(player: Player, voucherId: String): Long =
        voucherService.cooldownRemaining(player, voucherId)

    override fun clearCooldown(player: Player, voucherId: String) {
        voucherService.clearCooldowns(player, listOf(voucherId))
    }

    override fun buildVoucherItem(voucherId: String, amount: Int): ItemStack? {
        val voucher = configManager.config.vouchers[voucherId] ?: return null
        return voucherItemService.build(voucher, amount)
    }

    override fun giveVoucherItem(player: Player, voucherId: String, amount: Int): Boolean {
        val voucher = configManager.config.vouchers[voucherId] ?: return false
        return voucherItemService.give(player, voucher, amount)
    }

    override fun queueVoucherItem(playerName: String, voucherId: String, amount: Int): Boolean {
        val voucher = configManager.config.vouchers[voucherId]
        if (voucher?.item == null) return false
        val online = server.getPlayerExact(playerName)
        if (online != null) {
            online.scheduler.run(this, { voucherItemService.give(online, voucher, amount) }, null)
        } else {
            pendingGiveService.queue(playerName, voucherId, amount)
        }
        return true
    }

    override fun createVoucher(id: String, settings: Map<String, Any>): Boolean {
        val safeId = configManager.sanitizeId(id) ?: return false
        if (configManager.voucherExists(safeId)) return false
        configManager.saveVoucher(safeId) { yaml ->
            settings.forEach { (key, value) ->
                if (value is Map<*, *>) yaml.createSection(key, value) else yaml.set(key, value)
            }
        }
        reload()
        return true
    }

    override fun deleteVoucher(id: String): Boolean {
        val safeId = configManager.sanitizeId(id) ?: return false
        if (!configManager.deleteVoucher(safeId)) return false
        reload()
        return true
    }

    override fun generateCodes(templateId: String, amount: Int): List<String>? {
        val safeId = configManager.sanitizeId(templateId) ?: return null
        if (!configManager.voucherExists(safeId)) return null
        val generated = runCatching { configManager.generateCodes(safeId, amount.coerceIn(1, 500)) }
            .onFailure { logger.warning("Failed to generate codes from '$safeId': ${it.message}") }
            .getOrNull() ?: return null
        reload()
        return generated.second
    }

    override fun registerRewardAction(name: String, action: RewardAction) {
        voucherService.registerRewardAction(name, action)
    }

    override fun unregisterRewardAction(name: String) {
        voucherService.unregisterRewardAction(name)
    }

    override fun onDisable() {
        if (::updateChecker.isInitialized) updateChecker.stop()
        if (::actionLog.isInitialized) actionLog.shutdown()
        if (::usageService.isInitialized) usageService.shutdown()
        ClaimoApi.shutdown()
    }

    private fun registerPlaceholders() {
        if (!server.pluginManager.isPluginEnabled("PlaceholderAPI")) return
        runCatching {
            val expansion = Class.forName("zone.vao.claimo.hook.ClaimoExpansion")
                .getConstructor(Claimo::class.java)
                .newInstance(this)
            expansion.javaClass.getMethod("register").invoke(expansion)
        }.onFailure { logger.warning("Failed to register the PlaceholderAPI expansion: ${it.message}") }
    }

    private fun registerMiniPlaceholders() {
        if (!server.pluginManager.isPluginEnabled("MiniPlaceholders")) return
        runCatching {
            val expansion = Class.forName("zone.vao.claimo.hook.ClaimoMiniExpansion")
                .getConstructor(Claimo::class.java)
                .newInstance(this)
            expansion.javaClass.getMethod("register").invoke(expansion)
        }.onFailure { logger.warning("Failed to register the MiniPlaceholders expansion: ${it.message}") }
    }

    private fun registerBuiltinRequirements() {
        requirementRegistry.register(
            "blocks_mined",
            { cfg ->
                BlocksMinedRequirement(
                    statsService,
                    configManager.config.messages,
                    cfg.getInt("amount", 0),
                    parseMaterials(cfg.getStringList("whitelist")),
                    parseMaterials(cfg.getStringList("blacklist")),
                )
            },
            listOf(RequirementInput.NumberInput("amount", "Blocks mined", min = 0.0, max = 100_000.0, step = 10.0)),
        )
        requirementRegistry.register(
            "playtime",
            { cfg -> PlaytimeRequirement(statsService, configManager.config.messages, playtimeSeconds(cfg)) },
            listOf(RequirementInput.TextInput("duration", "Playtime (e.g. 1h 30m)", initial = "1h")),
        )
        requirementRegistry.register(
            "messages_sent",
            { cfg ->
                MessagesSentRequirement(
                    statsService,
                    configManager.config.messages,
                    cfg.getInt("amount", 0),
                    MessagePolicy.from(cfg),
                )
            },
            listOf(
                RequirementInput.NumberInput("amount", "Messages to send", min = 0.0, max = 100_000.0, step = 5.0),
                RequirementInput.NumberInput("min-length", "Min message length", min = 1.0, max = 256.0, step = 1.0, initial = 10.0),
                RequirementInput.NumberInput("delay-seconds", "Delay between messages (s)", min = 0.0, max = 3600.0, step = 5.0, initial = 20.0),
            ),
        )
        requirementRegistry.register(
            "account_age",
            { cfg -> AccountAgeRequirement(configManager.config.messages, accountAgeMillis(cfg)) },
            listOf(RequirementInput.TextInput("duration", "Account age (e.g. 7d, 2w)", initial = "7d")),
        )
        requirementRegistry.register(
            "permission",
            { cfg ->
                PermissionRequirement(
                    configManager.config.messages,
                    cfg.getStrings("permissions") + cfg.getStrings("permission"),
                    cfg.getStrings("denied-permissions") + cfg.getStrings("denied-permission"),
                )
            },
            listOf(
                RequirementInput.TextInput("permissions", "Required permissions (comma-separated)"),
                RequirementInput.TextInput("denied-permissions", "Forbidden permissions (comma-separated)"),
            ),
        )
        requirementRegistry.register(
            "rank",
            { cfg ->
                RankRequirement(
                    configManager.config.messages,
                    cfg.getStrings("ranks") + cfg.getStrings("rank"),
                    cfg.getStrings("denied-ranks") + cfg.getStrings("denied-rank"),
                )
            },
            listOf(
                RequirementInput.TextInput("ranks", "Required ranks (comma-separated)"),
                RequirementInput.TextInput("denied-ranks", "Forbidden ranks (comma-separated)"),
            ),
        )
        for (mode in GroupRequirement.Mode.entries) {
            requirementRegistry.register(mode.name.lowercase(), { cfg ->
                GroupRequirement(
                    configManager.config.messages,
                    mode,
                    RequirementGroups.children(cfg),
                    requirementRegistry,
                )
            })
        }
        requirementRegistry.register(
            "custom",
            { cfg ->
                CustomRequirement(
                    configManager.config.messages,
                    cfg.getString("placeholder", "").orEmpty(),
                    cfg.getString("operator", "==").orEmpty(),
                    cfg.getString("value", "").orEmpty(),
                )
            },
            listOf(
                RequirementInput.TextInput("placeholder", "Placeholder (e.g. %vault_eco_balance%)"),
                RequirementInput.TextInput("operator", "Operator (>=, <=, ==, !=, contains, regex)", initial = ">="),
                RequirementInput.TextInput("value", "Value to compare against (placeholders work too)"),
            ),
        )
    }

    private fun playtimeSeconds(cfg: RequirementConfig): Long {
        val duration = cfg.getString("duration")?.trim().orEmpty()
        if (duration.isNotEmpty()) Durations.parseMillis(duration)?.let { return it / 1000L }
        return cfg.getLong("seconds", 0L)
    }

    private fun accountAgeMillis(cfg: RequirementConfig): Long {
        val duration = cfg.getString("duration")?.trim().orEmpty()
        if (duration.isNotEmpty()) Durations.parseMillis(duration)?.let { return it }
        return cfg.getLong("days", 0L) * 86_400_000L
    }

    private fun parseMaterials(names: List<String>): Set<Material> =
        names.mapNotNullTo(LinkedHashSet()) { name ->
            Material.matchMaterial(name.trim()).also {
                if (it == null) logger.warning("Unknown material '$name' in a blocks_mined requirement; ignoring it.")
            }
        }

    private fun trackedBlockMaterials(): Set<Material> =
        configManager.config.vouchers.values
            .flatMap { it.flattenedRequirements() }
            .filter { it.type.equals("blocks_mined", ignoreCase = true) }
            .flatMap { it.getStringList("whitelist") + it.getStringList("blacklist") }
            .mapNotNullTo(HashSet()) { Material.matchMaterial(it.trim()) }

    @Suppress("UNCHECKED_CAST")
    private fun <T> createDialogComponent(className: String, feature: String): T? {
        val supported = runCatching { Class.forName("io.papermc.paper.dialog.Dialog") }.isSuccess
        if (!supported) {
            logger.info("Dialog API not available (server < 1.21.7); $feature is disabled.")
            return null
        }
        return runCatching {
            val component = Class.forName(className).getConstructor(Claimo::class.java).newInstance(this)
            (component as? Listener)?.let { server.pluginManager.registerEvents(it, this) }
            component as T
        }.onFailure {
            logger.warning("Failed to initialise $feature: ${it.message}")
        }.getOrNull()
    }

    private fun registerCommand() {
        val commandName = configManager.config.commandName
        val dialogCommandName = configManager.config.dialogCommandName
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registrar = event.registrar()
            registrar.register(
                VoucherCommand.build(this, commandName),
                "Redeem Claimo voucher codes",
                listOf("claimo"),
            )
            if (dialogCommandName != null) {
                registrar.register(
                    VoucherCommand.buildDialogInput(this, dialogCommandName),
                    "Redeem a Claimo voucher code via a dialog",
                )
            }
            val reserved = mutableSetOf("claimo", commandName.lowercase())
            dialogCommandName?.let { reserved += it.lowercase() }
            for (voucher in configManager.config.vouchers.values) {
                val cmd = voucher.redeemCommand ?: continue
                if (!reserved.add(cmd.lowercase())) {
                    logger.warning("Voucher '${voucher.id}' redeem-command '/$cmd' clashes with another Claimo command; skipping it.")
                    continue
                }
                registrar.register(
                    VoucherCommand.buildRedeemCommand(this, cmd, voucher.id),
                    "Redeem the Claimo code '${voucher.id}'",
                )
            }
        }
    }
}
