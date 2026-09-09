package zone.vao.claimo

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap

@Suppress("UnstableApiUsage")
class ClaimoBootstrap : PluginBootstrap {

    override fun bootstrap(context: BootstrapContext) {
        val supported = runCatching {
            Class.forName("io.papermc.paper.dialog.Dialog", false, javaClass.classLoader)
        }.isSuccess
        if (!supported) {
            context.logger.info("Dialog API not available (server < 1.21.6); the pause screen button is disabled.")
            return
        }
        runCatching {
            val button = Class.forName("zone.vao.claimo.prompt.PauseScreenButton")
                .getConstructor(BootstrapContext::class.java)
                .newInstance(context)
            button.javaClass.getMethod("register").invoke(button)
        }.onFailure {
            context.logger.warn("Failed to initialise the pause screen button: ${it.message}")
        }
    }
}
