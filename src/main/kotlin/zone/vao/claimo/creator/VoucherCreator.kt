package zone.vao.claimo.creator

import org.bukkit.entity.Player

interface VoucherCreator {

    fun open(player: Player)

    fun edit(player: Player, voucherId: String)

    fun delete(player: Player, voucherId: String)
}
