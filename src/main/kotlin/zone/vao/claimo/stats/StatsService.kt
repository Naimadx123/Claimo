package zone.vao.claimo.stats

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Statistic
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import zone.vao.claimo.requirement.RequirementConfig

data class MessagePolicy(val minLength: Int, val delaySeconds: Int) {
    companion object {
        fun from(config: RequirementConfig): MessagePolicy = MessagePolicy(
            config.getInt("min-length", 10).coerceAtLeast(1),
            config.getInt("delay-seconds", 20).coerceAtLeast(0),
        )
    }
}

class StatsService(private val plugin: Plugin) : Listener, ClaimoStats {

    private val totalKey = NamespacedKey(plugin, "blocks_mined")

    @Volatile
    private var tracked: Set<Material> = emptySet()

    @Volatile
    private var messagePolicies: Set<MessagePolicy> = emptySet()

    fun trackMaterials(materials: Set<Material>) {
        tracked = materials
    }

    fun configureMessagePolicies(policies: Set<MessagePolicy>) {
        messagePolicies = policies
    }

    private fun materialKey(material: Material): NamespacedKey =
        NamespacedKey(plugin, "blocks_mined/${material.key.value()}")

    override fun blocksMined(player: Player): Int =
        player.persistentDataContainer.getOrDefault(totalKey, PersistentDataType.INTEGER, 0)

    override fun playtimeSeconds(player: Player): Long =
        player.getStatistic(Statistic.PLAY_ONE_MINUTE).toLong() / 20L

    override fun messagesSent(player: Player, minLength: Int, delaySeconds: Int): Int =
        player.persistentDataContainer.getOrDefault(messagesKey(minLength, delaySeconds), PersistentDataType.INTEGER, 0)

    override fun blocksMined(player: Player, whitelist: Set<Material>, blacklist: Set<Material>): Int {
        val pdc = player.persistentDataContainer
        if (whitelist.isEmpty() && blacklist.isEmpty()) {
            return pdc.getOrDefault(totalKey, PersistentDataType.INTEGER, 0)
        }
        if (whitelist.isNotEmpty()) {
            return whitelist.asSequence()
                .filter { it !in blacklist }
                .sumOf { pdc.getOrDefault(materialKey(it), PersistentDataType.INTEGER, 0) }
        }
        val total = pdc.getOrDefault(totalKey, PersistentDataType.INTEGER, 0)
        val excluded = blacklist.sumOf { pdc.getOrDefault(materialKey(it), PersistentDataType.INTEGER, 0) }
        return (total - excluded).coerceAtLeast(0)
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val pdc = event.player.persistentDataContainer
        increment(pdc, totalKey)
        val type = event.block.type
        if (type in tracked) increment(pdc, materialKey(type))
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerChat(event: AsyncChatEvent) {
        val policies = messagePolicies
        if (policies.isEmpty()) return
        val length = PlainTextComponentSerializer.plainText().serialize(event.message()).trim().length
        val matching = policies.filter { length >= it.minLength }
        if (matching.isEmpty()) return
        val player = event.player
        player.scheduler.run(plugin, { _ -> matching.forEach { countMessage(player, it) } }, null)
    }

    private fun countMessage(player: Player, policy: MessagePolicy) {
        val pdc = player.persistentDataContainer
        val now = System.currentTimeMillis()
        val lastKey = lastMessageKey(policy.minLength, policy.delaySeconds)
        if (now - pdc.getOrDefault(lastKey, PersistentDataType.LONG, 0L) < policy.delaySeconds.toLong() * 1000L) return
        pdc.set(lastKey, PersistentDataType.LONG, now)
        increment(pdc, messagesKey(policy.minLength, policy.delaySeconds))
    }

    private fun messagesKey(minLength: Int, delaySeconds: Int): NamespacedKey =
        NamespacedKey(plugin, "messages_sent/${minLength}_$delaySeconds")

    private fun lastMessageKey(minLength: Int, delaySeconds: Int): NamespacedKey =
        NamespacedKey(plugin, "messages_sent/${minLength}_$delaySeconds/last")

    private fun increment(pdc: PersistentDataContainer, key: NamespacedKey) {
        val current = pdc.getOrDefault(key, PersistentDataType.INTEGER, 0)
        pdc.set(key, PersistentDataType.INTEGER, current + 1)
    }
}
