package zone.vao.claimo.voucher

import org.bukkit.Color
import org.bukkit.FireworkEffect
import org.bukkit.Particle
import org.bukkit.entity.Firework
import org.bukkit.entity.Player
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object RedeemEffects {

    fun play(player: Player, effects: VoucherEffects?) {
        if (effects == null) return
        repeat(effects.fireworks) { launchFirework(player) }
        effects.particle?.let { spawnParticles(player, it, effects.shape) }
    }

    private fun launchFirework(player: Player) {
        player.world.spawn(player.location, Firework::class.java) { firework ->
            firework.fireworkMeta = firework.fireworkMeta.apply {
                power = 1
                addEffect(
                    FireworkEffect.builder()
                        .with(FireworkEffect.Type.entries.random())
                        .withColor(randomColor(), randomColor())
                        .withFade(randomColor())
                        .flicker(Random.nextBoolean())
                        .trail(true)
                        .build(),
                )
            }
        }
    }

    private fun randomColor(): Color = Color.fromRGB(Random.nextInt(0x1000000))

    private fun spawnParticles(player: Player, particle: Particle, shape: VoucherEffects.Shape) {
        val world = player.world
        val center = player.location.add(0.0, 1.0, 0.0)
        when (shape) {
            VoucherEffects.Shape.BURST -> world.spawnParticle(particle, center, 40, 0.5, 0.5, 0.5, 0.05)
            VoucherEffects.Shape.CIRCLE -> {
                for (i in 0 until 32) {
                    val angle = 2 * PI * i / 32
                    world.spawnParticle(particle, center.clone().add(cos(angle) * 1.3, 0.2, sin(angle) * 1.3), 1, 0.0, 0.0, 0.0, 0.0)
                }
            }
            VoucherEffects.Shape.SPHERE -> {
                for (ring in 1 until 8) {
                    val phi = PI * ring / 8
                    val y = cos(phi) * 1.3
                    val radius = sin(phi) * 1.3
                    for (i in 0 until 16) {
                        val angle = 2 * PI * i / 16
                        world.spawnParticle(particle, center.clone().add(cos(angle) * radius, y, sin(angle) * radius), 1, 0.0, 0.0, 0.0, 0.0)
                    }
                }
            }
            VoucherEffects.Shape.HELIX -> {
                val base = player.location
                for (i in 0 until 60) {
                    val t = i / 60.0
                    val angle = t * 6 * PI
                    world.spawnParticle(particle, base.clone().add(cos(angle) * 1.1, t * 2.2, sin(angle) * 1.1), 1, 0.0, 0.0, 0.0, 0.0)
                }
            }
        }
    }
}
