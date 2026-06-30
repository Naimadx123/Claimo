package zone.vao.claimo.requirement

class RequirementConfig(
    val type: String,
    private val data: Map<String, Any?>,
) {
    /** Returns the int at [key], or [default] if missing or not a number. */
    fun getInt(key: String, default: Int = 0): Int =
        (data[key] as? Number)?.toInt() ?: default

    /** Returns the long at [key], or [default] if missing or not a number. */
    fun getLong(key: String, default: Long = 0L): Long =
        (data[key] as? Number)?.toLong() ?: default

    /** Returns the double at [key], or [default] if missing or not a number. */
    fun getDouble(key: String, default: Double = 0.0): Double =
        (data[key] as? Number)?.toDouble() ?: default

    /** Returns the string at [key], or [default] if missing. */
    fun getString(key: String, default: String? = null): String? =
        data[key]?.toString() ?: default

    /** Returns the boolean at [key], or [default] if missing or not a boolean. */
    fun getBoolean(key: String, default: Boolean = false): Boolean =
        data[key] as? Boolean ?: default

    /** Returns the string list at [key], or an empty list if missing or not a list. */
    fun getStringList(key: String): List<String> =
        (data[key] as? List<*>)?.map { it.toString() } ?: emptyList()

    /**
     * Reads [key] as a list of trimmed, non-empty strings, accepting either a YAML
     * list or a single comma-separated string (so values written by the in-game
     * creator's text fields work the same as hand-edited lists).
     */
    fun getStrings(key: String): List<String> = when (val value = data[key]) {
        is List<*> -> value.mapNotNull { it?.toString()?.trim()?.ifEmpty { null } }
        is String -> value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        null -> emptyList()
        else -> listOf(value.toString())
    }

    /** Whether [key] is present in this requirement's config. */
    fun has(key: String): Boolean = data.containsKey(key)

    /** All configured keys, including `type`. */
    val keys: Set<String> get() = data.keys
}
