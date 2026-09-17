// Native View/XML port of SceneView 4.35.0; modified 2026-09-16. See PORTING.md.
package io.github.sceneview.node

/** Attaches the upstream physics integrator without replacing the node's existing callbacks. */
class PhysicsBinding(val body: PhysicsBody) : AutoCloseable {
    private var previous: Long? = null
    private val subscription = body.node.addFrameListener { time ->
        // Avoid integrating an entire background interval on resume.
        val last = previous?.takeIf { time - it < 250_000_000L }
        body.step(time, last)
        previous = time
    }
    init { body.node.ownBinding(this) }
    override fun close() { subscription.close(); previous = null }
}
