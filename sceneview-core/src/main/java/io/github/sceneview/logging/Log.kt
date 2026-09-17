// Native View/XML port of SceneView 4.35.0; modified 2026-09-16. See PORTING.md.
package io.github.sceneview.logging

import java.util.logging.Level
import java.util.logging.Logger

fun logWarning(tag: String, message: String) {
    Logger.getLogger(tag).log(Level.WARNING, message)
}
