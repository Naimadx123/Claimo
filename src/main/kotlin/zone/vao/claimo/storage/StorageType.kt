package zone.vao.claimo.storage

enum class StorageType {
    YAML,
    SQLITE,
    MYSQL,
    POSTGRESQL,
    MONGODB;

    companion object {
        fun from(value: String?): StorageType {
            val normalized = value?.trim()?.uppercase()?.replace('-', '_')
            return when (normalized) {
                "MARIADB" -> MYSQL
                "MONGO" -> MONGODB
                else -> entries.firstOrNull { it.name == normalized } ?: YAML
            }
        }
    }
}
