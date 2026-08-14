package zone.vao.claimo.voucher

import org.bukkit.Particle

data class VoucherEffects(
    val fireworks: Int = 0,
    val particle: Particle? = null,
    val shape: Shape = Shape.BURST,
) {
    enum class Shape { BURST, CIRCLE, SPHERE, HELIX }
}
