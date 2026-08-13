package zone.vao.claimo.requirement.builtin

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import zone.vao.claimo.config.Messages
import zone.vao.claimo.requirement.Requirement
import zone.vao.claimo.requirement.RequirementConfig
import zone.vao.claimo.requirement.RequirementContext
import zone.vao.claimo.requirement.RequirementRegistry
import zone.vao.claimo.requirement.RequirementResult
import java.util.concurrent.CompletableFuture

class GroupRequirement(
    private val messages: Messages,
    private val mode: Mode,
    private val children: List<RequirementConfig>,
    private val registry: RequirementRegistry,
) : Requirement {

    enum class Mode { ALL, ANY, NOT }

    override fun check(context: RequirementContext): CompletableFuture<RequirementResult> {
        val checks = children.map { spec ->
            val requirement = registry.create(spec)
            if (requirement == null) {
                CompletableFuture.completedFuture(
                    RequirementResult.unsatisfied(
                        messages.line("requirement-unavailable", Placeholder.parsed("type", spec.type))
                    )
                )
            } else {
                requirement.check(context).exceptionally {
                    RequirementResult.unsatisfied(messages.line("requirement-error"))
                }
            }
        }
        return CompletableFuture.allOf(*checks.toTypedArray()).thenApply {
            val results = checks.map { it.join() }
            val satisfied = when (mode) {
                Mode.ALL -> results.all { it.satisfied }
                Mode.ANY -> results.any { it.satisfied }
                Mode.NOT -> results.none { it.satisfied }
            }
            val description = messages.line(
                "requirement-group-${mode.name.lowercase()}",
                Placeholder.component(
                    "requirements",
                    Component.join(JoinConfiguration.separator(Component.text(", ")), results.map { it.description }),
                ),
            )
            if (satisfied) RequirementResult.satisfied(description) else RequirementResult.unsatisfied(description)
        }
    }

    companion object {
        val MODES: Set<String> = Mode.entries.mapTo(HashSet()) { it.name.lowercase() }

        fun fromMap(data: Map<String, Any?>): RequirementConfig? {
            val type = data["type"]?.toString()
            if (!type.isNullOrBlank()) return RequirementConfig(type, data)
            for (mode in MODES) {
                if (data[mode] is List<*>) return RequirementConfig(mode, mapOf("requirements" to data[mode]))
            }
            return null
        }

        fun toConfigs(entries: List<Map<String, Any?>>): List<RequirementConfig> =
            entries.mapNotNull(::fromMap)
    }
}
