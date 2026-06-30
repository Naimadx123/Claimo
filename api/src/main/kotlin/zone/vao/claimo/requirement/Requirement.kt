package zone.vao.claimo.requirement

import java.util.concurrent.CompletableFuture

fun interface Requirement {

    /**
     * Evaluates whether [context] currently satisfies this requirement.
     *
     * Returns a future so requirements that perform I/O (e.g. a social-media
     * follow check) never block the main server thread. The future may complete
     * on any thread; the caller resumes on the main thread before touching Bukkit.
     */
    fun check(context: RequirementContext): CompletableFuture<RequirementResult>
}
