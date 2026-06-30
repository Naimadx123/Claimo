package zone.vao.claimo.requirement

import org.bukkit.entity.Player

data class RequirementContext(
    val player: Player,
    val voucherId: String,
)
