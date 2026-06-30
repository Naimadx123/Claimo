package zone.vao.claimo.requirement

import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

class RequirementRegistry(private val logger: Logger) {

    private val factories = ConcurrentHashMap<String, RequirementFactory>()
    private val inputs = ConcurrentHashMap<String, List<RequirementInput>>()

    /** Registers (or replaces) the factory for [type], matched case-insensitively. */
    fun register(type: String, factory: RequirementFactory) {
        register(type, factory, emptyList())
    }

    /**
     * Registers (or replaces) the factory for [type] together with the [inputs] the in-game
     * creator should offer for it. [type] is matched case-insensitively.
     */
    fun register(type: String, factory: RequirementFactory, inputs: List<RequirementInput>) {
        val key = type.lowercase()
        this.inputs[key] = inputs
        if (factories.putIfAbsent(key, factory) != null) {
            factories[key] = factory
            logger.warning("Requirement type '$key' was registered more than once; the previous factory was replaced.")
        }
    }

    /** Removes the factory for [type]. No-op if it wasn't registered. */
    fun unregister(type: String) {
        val key = type.lowercase()
        factories.remove(key)
        inputs.remove(key)
    }

    /** Whether a factory is registered for [type]. */
    fun isRegistered(type: String): Boolean = factories.containsKey(type.lowercase())

    /** All currently registered type keys. */
    fun types(): Set<String> = factories.keys.toSet()

    /** The creator inputs declared for [type], or an empty list if none/unknown. */
    fun inputs(type: String): List<RequirementInput> = inputs[type.lowercase()] ?: emptyList()

    /**
     * Builds a [Requirement] from [config], or `null` if the type is unregistered
     * or the factory threw while building.
     */
    fun create(config: RequirementConfig): Requirement? {
        val factory = factories[config.type.lowercase()] ?: return null
        return runCatching { factory.create(config) }
            .onFailure { logger.warning("Failed to build requirement '${config.type}': ${it.message}") }
            .getOrNull()
    }
}
