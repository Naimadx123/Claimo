package zone.vao.claimo

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import zone.vao.claimo.command.VoucherCommand
import zone.vao.claimo.config.ConfigManager
import zone.vao.claimo.creator.VoucherCreator
import zone.vao.claimo.gui.VoucherMenu
import zone.vao.claimo.prompt.CodePrompt
import zone.vao.claimo.requirement.RequirementInput
import zone.vao.claimo.requirement.RequirementRegistry
import zone.vao.claimo.requirement.builtin.AccountAgeRequirement
import zone.vao.claimo.requirement.builtin.BlocksMinedRequirement
import zone.vao.claimo.requirement.builtin.MessagesSentRequirement
import zone.vao.claimo.requirement.builtin.PermissionRequirement
import zone.vao.claimo.requirement.builtin.PlaytimeRequirement
import zone.vao.claimo.requirement.builtin.RankRequirement
import zone.vao.claimo.stats.ClaimoStats
import zone.vao.claimo.stats.MessagePolicy
import zone.vao.claimo.stats.StatsService
import zone.vao.claimo.storage.StorageFactory
import zone.vao.claimo.storage.UsageStorage
import zone.vao.claimo.usage.UsageService
import zone.vao.claimo.voucher.Voucher
import zone.vao.claimo.voucher.VoucherService

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
    lateinit var usageService: UsageService
        private set
    private lateinit var usageStorage: UsageStorage
    lateinit var voucherMenu: VoucherMenu
        private set
    var voucherCreator: VoucherCreator? = null
        private set
    var codePrompt: CodePrompt? = null
        private set

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

        voucherMenu = VoucherMenu(this)
        server.pluginManager.registerEvents(voucherMenu, this)

        reload()

        voucherCreator = createDialogCreatorIfSupported()
        (voucherCreator as? Listener)?.let { server.pluginManager.registerEvents(it, this) }

        codePrompt = createCodePromptIfSupported()
        (codePrompt as? Listener)?.let { server.pluginManager.registerEvents(it, this) }

        ClaimoApi.init(this)

        registerCommand()

        logger.info("Claimo enabled — redeem command: /${configManager.config.commandName}")
    }

    override fun reload() {
        configManager.load()
        statsService.trackMaterials(trackedBlockMaterials())
        statsService.configureMessagePolicies(messagePolicies())
    }

    private fun messagePolicies(): Set<MessagePolicy> =
        configManager.config.vouchers.values
            .flatMap { it.requirements }
            .filter { it.type.equals("messages_sent", ignoreCase = true) }
            .mapTo(HashSet()) { MessagePolicy.from(it) }

    override val requirements: RequirementRegistry get() = requirementRegistry
    override val stats: ClaimoStats get() = statsService
    override fun vouchers(): Collection<Voucher> = configManager.config.vouchers.values
    override fun voucher(id: String): Voucher? = configManager.config.vouchers[id]
    override fun redeem(player: Player, voucherId: String) = voucherService.redeem(player, voucherId)

    override fun onDisable() {
        if (::usageService.isInitialized) usageService.shutdown()
        ClaimoApi.shutdown()
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
            { cfg -> PlaytimeRequirement(statsService, configManager.config.messages, cfg.getLong("seconds", 0L)) },
            listOf(RequirementInput.NumberInput("seconds", "Playtime (seconds)", min = 0.0, max = 604_800.0, step = 60.0)),
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
                RequirementInput.NumberInput("min-length", "Min message length", min = 1.0, max = 256.0, step = 1.0),
                RequirementInput.NumberInput("delay-seconds", "Delay between messages (s)", min = 0.0, max = 3600.0, step = 5.0),
            ),
        )
        requirementRegistry.register(
            "account_age",
            { cfg -> AccountAgeRequirement(configManager.config.messages, cfg.getLong("days", 0L)) },
            listOf(RequirementInput.NumberInput("days", "Account age (days)", min = 0.0, max = 365.0, step = 1.0)),
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
    }

    private fun parseMaterials(names: List<String>): Set<Material> =
        names.mapNotNullTo(LinkedHashSet()) { name ->
            Material.matchMaterial(name.trim()).also {
                if (it == null) logger.warning("Unknown material '$name' in a blocks_mined requirement; ignoring it.")
            }
        }

    private fun trackedBlockMaterials(): Set<Material> =
        configManager.config.vouchers.values
            .flatMap { it.requirements }
            .filter { it.type.equals("blocks_mined", ignoreCase = true) }
            .flatMap { it.getStringList("whitelist") + it.getStringList("blacklist") }
            .mapNotNullTo(HashSet()) { Material.matchMaterial(it.trim()) }

    private fun createDialogCreatorIfSupported(): VoucherCreator? {
        val supported = runCatching { Class.forName("io.papermc.paper.dialog.Dialog") }.isSuccess
        if (!supported) {
            logger.info("Dialog API not available (server < 1.21.7); the in-game code creator is disabled.")
            return null
        }
        return runCatching {
            Class.forName("zone.vao.claimo.creator.DialogVoucherCreator")
                .getConstructor(Claimo::class.java)
                .newInstance(this) as VoucherCreator
        }.onFailure {
            logger.warning("Failed to initialise the dialog code creator: ${it.message}")
        }.getOrNull()
    }

    private fun createCodePromptIfSupported(): CodePrompt? {
        val supported = runCatching { Class.forName("io.papermc.paper.dialog.Dialog") }.isSuccess
        if (!supported) {
            logger.info("Dialog API not available (server < 1.21.7); the code input dialog is disabled.")
            return null
        }
        return runCatching {
            Class.forName("zone.vao.claimo.prompt.DialogCodePrompt")
                .getConstructor(Claimo::class.java)
                .newInstance(this) as CodePrompt
        }.onFailure {
            logger.warning("Failed to initialise the code input dialog: ${it.message}")
        }.getOrNull()
    }

    private fun registerCommand() {
        val commandName = configManager.config.commandName
        val dialogCommandName = configManager.config.dialogCommandName
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(
                VoucherCommand.build(this, commandName),
                "Redeem Claimo voucher codes",
                listOf("claimo"),
            )
            if (dialogCommandName != null) {
                event.registrar().register(
                    VoucherCommand.buildDialogInput(this, dialogCommandName),
                    "Redeem a Claimo voucher code via a dialog",
                )
            }
        }
    }
}
