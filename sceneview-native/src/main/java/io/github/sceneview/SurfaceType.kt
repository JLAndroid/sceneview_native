// Native View/XML port of SceneView 4.35.0; modified 2026-09-16. See PORTING.md.
package io.github.sceneview

/** Rendering surface chosen when constructing or inflating SceneView. */
enum class SurfaceType {
    /** Separate Android surface; its stacking rules differ from ordinary Views. */
    Surface,
    /** Ordinary View composition, supporting alpha and UI above/below the 3D content. */
    TextureSurface
}
