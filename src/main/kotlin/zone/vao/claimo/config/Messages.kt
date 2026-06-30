package zone.vao.claimo.config

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver

class Messages(
    private val prefix: String,
    private val raw: Map<String, String>,
) {
    private val mm = MiniMessage.miniMessage()

    fun component(key: String, vararg resolvers: TagResolver): Component {
        val template = raw[key] ?: key
        return mm.deserialize(prefix + template, *resolvers)
    }

    fun line(key: String, vararg resolvers: TagResolver): Component {
        val template = raw[key] ?: key
        return mm.deserialize(template, *resolvers)
    }

    fun send(audience: Audience, key: String, vararg resolvers: TagResolver) {
        audience.sendMessage(component(key, *resolvers))
    }
}
