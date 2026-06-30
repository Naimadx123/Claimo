package zone.vao.claimo.storage

data class StorageConfig(
    val type: StorageType,
    val uri: String,
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String,
    val tablePrefix: String,
    val poolSize: Int,
)
