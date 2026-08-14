package zone.vao.claimo.prompt

import org.bukkit.entity.Player

fun interface PriceConfirm {

    fun open(player: Player, voucherId: String, price: String, onSuccess: (() -> Unit)?)
}
