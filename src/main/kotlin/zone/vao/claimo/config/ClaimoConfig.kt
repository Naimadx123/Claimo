package zone.vao.claimo.config

import zone.vao.claimo.storage.StorageConfig
import zone.vao.claimo.voucher.Voucher

data class ClaimoConfig(
    val commandName: String,
    val dialogCommandName: String?,
    val guiListEnabled: Boolean,
    val storage: StorageConfig,
    val redeemSound: SoundConfig,
    val logRedeems: Boolean,
    val messages: Messages,
    val gui: GuiConfig,
    val vouchers: Map<String, Voucher>,
)
