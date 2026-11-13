package cc.gyula.wave

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import cc.gyula.wave.data.REQUEST_CONFIGURE_WIDGET
import cc.gyula.wave.ui.home.HomeScreen
import cc.gyula.wave.ui.theme.WaveTheme

class MainActivity : ComponentActivity() {
    private val vm: AppsViewModel by viewModels()

    /** Bumped on every Home press while we're already the home app — the list scrolls back to the top. */
    private var homePresses by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Back with nothing open does nothing: left to Android, it would close home and start a fresh one,
        // forgetting what was on screen, e.g. a music player swiped away.
        onBackPressedDispatcher.addCallback(this) {}
        setContent {
            WaveTheme {
                HomeScreen(vm, homePresses)
            }
        }
    }

    // Widgets only update while their host listens, i.e. while the home screen is on screen.
    override fun onStart() {
        super.onStart()
        vm.widgets.host.startListening()
    }

    override fun onStop() {
        super.onStop()
        vm.widgets.host.stopListening()
    }

    // The widget host outlives this activity and would keep the views made in it, and so this activity, alive.
    // The next activity's widget pages make fresh views as they are shown.
    override fun onDestroy() {
        super.onDestroy()
        vm.widgets.dropViews()
    }

    @Deprecated("AppWidgetHost only reports a widget's setup result here")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CONFIGURE_WIDGET) vm.widgets.configured(resultCode == RESULT_OK)
        else super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses++
    }
}
