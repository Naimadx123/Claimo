package zone.vao.claimo.voucher

import org.bukkit.Color
import org.bukkit.Material

data class VoucherItem(
    val material: Material? = null,
    val name: String? = null,
    val lore: List<String> = emptyList(),
    val itemModel: String? = null,
    val customModelData: Int? = null,
    val cmdFloats: List<Float> = emptyList(),
    val cmdFlags: List<Boolean> = emptyList(),
    val cmdStrings: List<String> = emptyList(),
    val cmdColors: List<Color> = emptyList(),
    val nexoItem: String? = null,
    val iaItem: String? = null,
    val ceItem: String? = null,
) {

    val hash: Int
        get() = listOf(
            material?.name, name, lore, itemModel, customModelData,
            cmdFloats, cmdFlags, cmdStrings, cmdColors,
            nexoItem, iaItem, ceItem,
        )
            .toString()
            .hashCode()
}
