package cc.gyula.wave.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CornersTest {
    @Test
    fun everyChoiceSurvivesSavingAndReading() {
        listOf(
            CornerAction.Torch, CornerAction.Camera, CornerAction.Off,
            CornerAction.App("com.x/.Main#0"), CornerAction.App("a:b#10"),
        ).forEach { assertEquals(it, parseCorner(formatCorner(it), CornerAction.Off)) }
    }

    @Test
    fun anAppIsSavedWithItsKey() {
        assertEquals("app:com.x/.Main#0", formatCorner(CornerAction.App("com.x/.Main#0")))
    }

    @Test
    fun garbageAndMissingFallBackToTheDefault() {
        listOf(null, "", "flash", "app:", "APP:x", "Torch").forEach {
            assertEquals(CornerAction.Camera, parseCorner(it, CornerAction.Camera))
        }
    }
}
