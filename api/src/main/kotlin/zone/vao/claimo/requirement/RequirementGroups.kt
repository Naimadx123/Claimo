package zone.vao.claimo.requirement

/**
 * The shared semantics of group requirement entries (`all`, `any`, `not`), used by the
 * plugin and available to addons so both sides resolve voucher requirements identically.
 */
object RequirementGroups {

    /** The lowercase type keys that mark a requirement entry as a group. */
    val MODES: Set<String> = setOf("all", "any", "not")

    /** Whether [type] names a group entry (matched case-insensitively). */
    fun isGroup(type: String): Boolean = type.lowercase() in MODES

    /**
     * Builds a [RequirementConfig] from one raw requirement map. Accepts a regular entry
     * carrying a `type` key as well as the group shorthand where the mode itself is the
     * key holding the child list, e.g. `- any:` followed by nested entries. Returns null
     * when the map matches neither form.
     */
    fun fromMap(data: Map<String, Any?>): RequirementConfig? {
        val type = data["type"]?.toString()
        if (!type.isNullOrBlank()) return RequirementConfig(type, data)
        for (mode in MODES) {
            if (data[mode] is List<*>) return RequirementConfig(mode, mapOf("requirements" to data[mode]))
        }
        return null
    }

    /** Builds configs from a list of raw requirement maps, skipping invalid entries. */
    fun toConfigs(entries: List<Map<String, Any?>>): List<RequirementConfig> =
        entries.mapNotNull(::fromMap)

    /** The child configs of a group entry, read from its nested requirement list. */
    fun children(config: RequirementConfig): List<RequirementConfig> =
        toConfigs(config.getMapList("requirements"))

    /**
     * Flattens [configs] by recursively replacing every group entry with its children,
     * so the result contains only concrete requirement types.
     */
    fun flatten(configs: List<RequirementConfig>): List<RequirementConfig> =
        configs.flatMap { config ->
            if (isGroup(config.type)) flatten(children(config)) else listOf(config)
        }
}
