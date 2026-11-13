package cc.gyula.wave.core

/** What a bottom-corner button does. */
sealed interface CornerAction {
    data object Torch : CornerAction
    data object Camera : CornerAction
    data object Off : CornerAction

    /** Opens the app with this key (the app module's `AppEntry.key`). */
    data class App(val key: String) : CornerAction
}

/** How a corner is saved: "torch", "camera", "off" or "app:<key>". */
fun formatCorner(action: CornerAction): String = when (action) {
    CornerAction.Torch -> "torch"
    CornerAction.Camera -> "camera"
    CornerAction.Off -> "off"
    is CornerAction.App -> "app:${action.key}"
}

/** The corner saved as [saved]; anything unreadable (or missing) is [default]. */
fun parseCorner(saved: String?, default: CornerAction): CornerAction = when {
    saved == "torch" -> CornerAction.Torch
    saved == "camera" -> CornerAction.Camera
    saved == "off" -> CornerAction.Off
    saved != null && saved.startsWith("app:") && saved.length > 4 -> CornerAction.App(saved.removePrefix("app:"))
    else -> default
}
