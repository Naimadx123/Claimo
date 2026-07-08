package zone.vao.claimo.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import zone.vao.claimo.Claimo
import zone.vao.claimo.config.GuiConfig
import zone.vao.claimo.util.Durations
import zone.vao.claimo.voucher.Voucher
import kotlin.math.ceil
import kotlin.math.max

class VoucherMenu(private val plugin: Claimo) : Listener {

    fun open(player: Player, page: Int) {
        val config = plugin.configManager.config
        val gui = config.gui
        val visible = config.vouchers.values.filter { !it.hidden && !it.isExpired() }

        val size = gui.rows * 9
        val perPage = size - 9
        val totalPages = max(1, ceil(visible.size / perPage.toDouble()).toInt())
        val current = page.coerceIn(0, totalPages - 1)

        val holder = VoucherMenuHolder(current, totalPages)
        val title = MM.deserialize(
            gui.title,
            Placeholder.parsed("page", (current + 1).toString()),
            Placeholder.parsed("pages", totalPages.toString()),
        )
        val inventory = Bukkit.createInventory(holder, size, title)
        holder.attach(inventory)

        gui.filler?.let { material ->
            val filler = icon(material, Component.empty())
            for (slot in size - 9 until size) inventory.setItem(slot, filler)
        }

        val pageItems = visible.drop(current * perPage).take(perPage)
        pageItems.forEachIndexed { index, voucher ->
            inventory.setItem(index, voucherIcon(gui, voucher))
            holder.slots[index] = voucher.id
        }

        if (current > 0) inventory.setItem(size - 9, icon(gui.previousMaterial, text(gui.previousName)))
        if (current < totalPages - 1) inventory.setItem(size - 1, icon(gui.nextMaterial, text(gui.nextName)))

        player.openInventory(inventory)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? VoucherMenuHolder ?: return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        if (event.clickedInventory !== event.inventory) return

        val slot = event.rawSlot
        val size = event.inventory.size
        when (slot) {
            size - 9 -> if (holder.page > 0) reopen(player, holder.page - 1)
            size - 1 -> if (holder.page < holder.totalPages - 1) reopen(player, holder.page + 1)
            else -> {
                val voucherId = holder.slots[slot] ?: return
                player.scheduler.run(plugin, {
                    player.closeInventory()
                    plugin.voucherService.redeem(player, voucherId)
                }, null)
            }
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.inventory.holder is VoucherMenuHolder) {
            event.isCancelled = true
        }
    }

    private fun reopen(player: Player, page: Int) {
        player.scheduler.run(plugin, { open(player, page) }, null)
    }

    private fun voucherIcon(gui: GuiConfig, voucher: Voucher): ItemStack {
        val resolvers = arrayOf(
            Placeholder.parsed("voucher", voucher.id),
            Placeholder.parsed("expires", expiresText(voucher)),
        )
        return ItemStack(gui.voucherMaterial).apply {
            editMeta { meta ->
                meta.displayName(text(gui.voucherName, *resolvers))
                if (gui.voucherLore.isNotEmpty()) {
                    meta.lore(gui.voucherLore.map { text(it, *resolvers) })
                }
            }
        }
    }

    private fun expiresText(voucher: Voucher): String {
        val expiresAt = voucher.expiresAt ?: return "never"
        return Durations.humanize(expiresAt - System.currentTimeMillis())
    }

    private fun icon(material: Material, name: Component): ItemStack =
        ItemStack(material).apply { editMeta { it.displayName(name) } }

    private fun text(raw: String, vararg resolvers: TagResolver): Component =
        MM.deserialize(raw, *resolvers).decoration(TextDecoration.ITALIC, false)

    private class VoucherMenuHolder(val page: Int, val totalPages: Int) : InventoryHolder {
        val slots = HashMap<Int, String>()
        private lateinit var inventory: Inventory

        fun attach(inventory: Inventory) {
            this.inventory = inventory
        }

        override fun getInventory(): Inventory = inventory
    }

    private companion object {
        val MM: MiniMessage = MiniMessage.miniMessage()
    }
}
