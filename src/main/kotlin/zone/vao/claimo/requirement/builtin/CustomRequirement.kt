package zone.vao.claimo.requirement.builtin

import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import zone.vao.claimo.config.Messages
import zone.vao.claimo.requirement.Requirement
import zone.vao.claimo.requirement.RequirementContext
import zone.vao.claimo.requirement.RequirementResult
import java.util.concurrent.CompletableFuture

class CustomRequirement(
    private val messages: Messages,
    private val placeholder: String,
    private val operator: String,
    private val value: String,
) : Requirement {

    override fun check(context: RequirementContext): CompletableFuture<RequirementResult> {
        val papi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
        val actual = resolve(context, placeholder, papi)
        val expected = resolve(context, value, papi)
        val description = messages.line(
            "requirement-custom",
            Placeholder.unparsed("placeholder", placeholder),
            Placeholder.unparsed("placeholder_pretty", placeholder.replace("%", "").replace("_", " ").trim()),
            Placeholder.unparsed("placeholder_parsed", actual),
            Placeholder.unparsed("operator", operator),
            Placeholder.unparsed("value", value),
            Placeholder.unparsed("value_parsed", expected),
        )
        val satisfied = papi && placeholder.isNotBlank() && compare(actual, expected, operator.trim().lowercase())
        val result = if (satisfied) {
            RequirementResult.satisfied(description)
        } else {
            RequirementResult.unsatisfied(description)
        }
        return CompletableFuture.completedFuture(result)
    }

    private fun resolve(context: RequirementContext, text: String, papi: Boolean): String =
        if (papi) PlaceholderAPI.setPlaceholders(context.player, text).trim() else text.trim()

    private fun compare(actual: String, expected: String, op: String): Boolean = when (op) {
        "", "==", "=", "equals" -> actual.equals(expected, ignoreCase = true)
        "!=", "<>" -> !actual.equals(expected, ignoreCase = true)
        "contains" -> actual.contains(expected, ignoreCase = true)
        "regex" -> runCatching { Regex(expected).matches(actual) }.getOrDefault(false)
        ">", ">=", "<", "<=" -> {
            val a = actual.toDoubleOrNull()
            val b = expected.toDoubleOrNull()
            if (a == null || b == null) false
            else when (op) {
                ">" -> a > b
                ">=" -> a >= b
                "<" -> a < b
                else -> a <= b
            }
        }
        else -> false
    }
}
