package zone.vao.claimo.voucher

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import zone.vao.claimo.Claimo
import java.lang.reflect.Modifier

class VoucherItemService(private val plugin: Claimo) : Listener {

    private val voucherKey = NamespacedKey(plugin, "voucher")
    private val hashKey = NamespacedKey(plugin, "item-hash")

    fun voucherId(item: ItemStack?): String? {
        if (item == null || item.type.isAir || !item.hasItemMeta()) return null
        return item.itemMeta.persistentDataContainer.get(voucherKey, PersistentDataType.STRING)
    }

    fun build(voucher: Voucher, amount: Int = 1): ItemStack? {
        val spec = voucher.item ?: return null
        val item = hookItem(voucher.id, spec) ?: ItemStack(spec.material ?: Material.PAPER)
        item.amount = amount.coerceIn(1, item.maxStackSize)
        item.editMeta { meta ->
            val resolver = Placeholder.parsed("voucher", voucher.id)
            spec.name?.let { meta.displayName(text(it, resolver)) }
            if (spec.lore.isNotEmpty()) meta.lore(spec.lore.map { text(it, resolver) })
            spec.customModelData?.let { meta.setCustomModelData(it) }
            spec.itemModel?.let { model ->
                runCatching { meta.itemModel = NamespacedKey.fromString(model) }.onFailure {
                    plugin.logger.warning("Voucher '${voucher.id}': could not apply item_model '$model' (requires 1.21.2+): ${it.message}")
                }
            }
            meta.persistentDataContainer.set(voucherKey, PersistentDataType.STRING, voucher.id)
            meta.persistentDataContainer.set(hashKey, PersistentDataType.INTEGER, spec.hash)
        }
        return item
    }

    fun give(player: Player, voucher: Voucher, amount: Int): Boolean {
        val template = build(voucher) ?: return false
        var remaining = amount
        while (remaining > 0) {
            val stack = template.clone()
            stack.amount = remaining.coerceAtMost(template.maxStackSize)
            remaining -= stack.amount
            player.inventory.addItem(stack).values.forEach {
                player.world.dropItemNaturally(player.location, it)
            }
        }
        return true
    }

    @EventHandler
    fun onUse(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val id = voucherId(event.item) ?: return
        event.isCancelled = true
        val player = event.player
        plugin.voucherService.redeem(player, id) {
            val hand = player.inventory.itemInMainHand
            if (voucherId(hand) == id) hand.subtract()
        }
    }


    @EventHandler
    fun onJoin(event: PlayerJoinEvent) = refreshInventory(event.player)

    @EventHandler
    fun onOpen(event: InventoryOpenEvent) {
        (event.player as? Player)?.let(::refreshInventory)
    }

    @EventHandler
    fun onDrop(event: PlayerDropItemEvent) {
        refreshed(event.itemDrop.itemStack)?.let { event.itemDrop.itemStack = it }
    }

    private fun refreshInventory(player: Player) {
        val contents = player.inventory.contents
        for (slot in contents.indices) {
            val updated = refreshed(contents[slot] ?: continue) ?: continue
            player.inventory.setItem(slot, updated)
        }
    }

    private fun refreshed(item: ItemStack): ItemStack? {
        val id = voucherId(item) ?: return null
        val voucher = plugin.configManager.config.vouchers[id] ?: return null
        val spec = voucher.item ?: return null
        val stored = item.itemMeta.persistentDataContainer.get(hashKey, PersistentDataType.INTEGER)
        if (stored == spec.hash) return null
        return build(voucher, item.amount)
    }


    private fun hookItem(voucherId: String, spec: VoucherItem): ItemStack? {
        val nexo = spec.nexoItem
        val ia = spec.iaItem
        val ce = spec.ceItem
        val (hook, id, resolve) = when {
            nexo != null -> Triple("Nexo", nexo, ::nexoItem)
            ia != null -> Triple("ItemsAdder", ia, ::itemsAdderItem)
            ce != null -> Triple("CraftEngine", ce, ::craftEngineItem)
            else -> return null
        }
        val item = runCatching { resolve(id) }.getOrNull()
        if (item == null) {
            plugin.logger.warning("Voucher '$voucherId': could not resolve $hook item '$id'; using the fallback material.")
        }
        return item
    }

    private fun nexoItem(id: String): ItemStack? {
        val builder = invoke("com.nexomc.nexo.api.NexoItems", "itemFromId", id) ?: return null
        return builder.javaClass.getMethod("build").invoke(builder) as? ItemStack
    }

    private fun itemsAdderItem(id: String): ItemStack? {
        val stack = invoke("dev.lone.itemsadder.api.CustomStack", "getInstance", id) ?: return null
        return stack.javaClass.getMethod("getItemStack").invoke(stack) as? ItemStack
    }

    private fun craftEngineItem(id: String): ItemStack? {
        val key = invoke("net.momirealms.craftengine.core.util.Key", "of", id) ?: return null
        val custom = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems")
            .methods.first { it.name == "byId" && it.parameterCount == 1 }
            .invoke(null, key) ?: return null
        return custom.javaClass.methods
            .firstOrNull { it.name == "buildItemStack" && it.parameterCount == 0 }
            ?.invoke(custom) as? ItemStack
    }

    private fun invoke(className: String, methodName: String, arg: String): Any? {
        val clazz = Class.forName(className)
        val method = clazz.methods.first {
            it.name == methodName && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java
        }
        val target = if (Modifier.isStatic(method.modifiers)) null else clazz.getField("INSTANCE").get(null)
        return method.invoke(target, arg)
    }

    private fun text(raw: String, vararg resolvers: net.kyori.adventure.text.minimessage.tag.resolver.TagResolver): Component =
        MM.deserialize(raw, *resolvers).decoration(TextDecoration.ITALIC, false)

    private companion object {
        val MM: MiniMessage = MiniMessage.miniMessage()
    }
}
