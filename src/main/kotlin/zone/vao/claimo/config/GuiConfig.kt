package zone.vao.claimo.config

import org.bukkit.Material

data class GuiConfig(
    val title: String,
    val rows: Int,
    val filler: Material?,
    val voucherMaterial: Material,
    val voucherName: String,
    val voucherLore: List<String>,
    val previousMaterial: Material,
    val previousName: String,
    val nextMaterial: Material,
    val nextName: String,
)
