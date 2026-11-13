package cc.gyula.wave.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home opening in a fresh process: `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest`.
 *
 * Not a cold start: Wave never really gets one. Android brings its process back for the notification
 * listener as soon as it dies, even after a force-stop, so what you wait for is home opening in a process that
 * has just started and run none of its screen code yet. That is what this times, after a force-stop.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun withoutProfile() = startup(CompilationMode.None())

    @Test
    fun withProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        iterations = 10,
        setupBlock = {
            // Behind Settings, so it is the listener that brings the process back, not home.
            device.executeShellCommand("am start -W -a android.settings.SETTINGS")
            device.executeShellCommand("am force-stop $PACKAGE")
            while (device.executeShellCommand("pidof $PACKAGE").isBlank()) Thread.sleep(50)
            Thread.sleep(1000)
        },
    ) {
        startActivityAndWait()
    }
}
