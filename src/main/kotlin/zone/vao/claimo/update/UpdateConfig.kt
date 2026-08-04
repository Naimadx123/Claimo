package zone.vao.claimo.update

data class UpdateConfig(
    val enabled: Boolean,
    val notifyAdmins: Boolean,
    val intervalHours: Long,
)
