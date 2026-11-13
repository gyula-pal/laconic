package cc.gyula.wave.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope

const val PACKAGE = "cc.gyula.wave"

/**
 * What using Wave mostly is: a thumb down the letter strip on either edge, then the widget pages either
 * side of home. Swipes stay in the clock's half of the screen, where a sideways swipe can't open an app's shortcuts.
 */
fun MacrobenchmarkScope.browseHome() {
    val w = device.displayWidth
    val h = device.displayHeight
    // The strip sits just inside the system Back-swipe zone; nearer the edge a drag down is a pull-down instead.
    val strip = 45 * w / device.displaySizeDp.x
    device.waitForIdle()
    for (x in listOf(w - strip, strip)) {
        device.swipe(x, h * 11 / 20, x, h * 3 / 4, 40)
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }
    for ((from, to) in listOf(w * 3 / 4 to w / 4, w / 4 to w * 3 / 4)) {
        device.swipe(from, h / 5, to, h / 5, 20)
        device.waitForIdle()
        device.swipe(to, h / 5, from, h / 5, 20)
        device.waitForIdle()
    }
}
