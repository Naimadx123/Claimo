package zone.vao.claimo.prompt

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import io.papermc.paper.registry.RegistryKey
import io.papermc.paper.registry.TypedKey
import io.papermc.paper.registry.event.RegistryEvents
import io.papermc.paper.registry.keys.tags.DialogTagKeys
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.configuration.file.YamlConfiguration
import zone.vao.claimo.config.Messages
import java.io.File

@Suppress("UnstableApiUsage")
class PauseScreenButton(private val context: BootstrapContext) {

    fun register() {
        val main = load("config.yml")
        if (!main.getBoolean("pause-screen-button.enabled", true)) return

        val messages = Messages.from(load("messages.yml"))
        val label = MiniMessage.miniMessage()
            .deserialize(main.getString("pause-screen-button.label") ?: DEFAULT_LABEL)

        val manager = context.lifecycleManager
        manager.registerEventHandler(RegistryEvents.DIALOG.compose()) { event ->
            event.registry().register(REDEEM) { builder ->
                builder.base(RedeemDialog.base(messages, label)).type(RedeemDialog.type(messages))
            }
        }
        manager.registerEventHandler(LifecycleEvents.TAGS.postFlatten(RegistryKey.DIALOG)) { event ->
            event.registrar().addToTag(DialogTagKeys.PAUSE_SCREEN_ADDITIONS, setOf(REDEEM))
        }
    }

    private fun load(name: String): YamlConfiguration {
        val defaults = javaClass.classLoader.getResourceAsStream(name)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { YamlConfiguration.loadConfiguration(it) }
        val file = File(context.dataDirectory.toFile(), name)
        if (!file.isFile) return defaults ?: YamlConfiguration()
        return YamlConfiguration.loadConfiguration(file).apply { defaults?.let(::setDefaults) }
    }

    private companion object {
        val REDEEM: TypedKey<Dialog> = TypedKey.create(RegistryKey.DIALOG, Key.key("claimo", "redeem"))

        const val DEFAULT_LABEL = "<aqua>Redeem a code"
    }
}
