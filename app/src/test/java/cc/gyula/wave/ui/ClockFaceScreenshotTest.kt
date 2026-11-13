package cc.gyula.wave.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import cc.gyula.wave.core.BandLook
import cc.gyula.wave.core.BandPosition
import cc.gyula.wave.ui.clock.ALARM_MARK
import cc.gyula.wave.ui.clock.ClockFace
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The clock in each band position and look, against reference images in src/test/screenshots. A change to
 * the band's layout or drawing shows up as a failed comparison with a diff image beside it.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-xxhdpi") // a Pixel 10's width
class ClockFaceScreenshotTest(private val position: BandPosition, private val look: BandLook) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun clockFace() {
        compose.setContent {
            Box(Modifier.background(Color.Black).fillMaxWidth().padding(vertical = 24.dp)) {
                ClockFace("TUE", listOf("${ALARM_MARK}06:10", "14°C", "27 OCT", "₿80.3K"), position, look)
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/clock-${position.name.lowercase()}-${look.name.lowercase()}.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1}")
        fun cases() = BandPosition.entries.flatMap { p -> BandLook.entries.map { arrayOf<Any>(p, it) } }
    }
}
