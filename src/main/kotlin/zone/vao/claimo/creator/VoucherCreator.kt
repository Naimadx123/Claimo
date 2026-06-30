package zone.vao.claimo.creator

import org.bukkit.entity.Player

fun interface VoucherCreator {

    fun open(player: Player)
}
