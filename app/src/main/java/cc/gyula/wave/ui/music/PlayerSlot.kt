package cc.gyula.wave.ui.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.gyula.wave.core.Dismissal
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Where the player shows under the clock: [playing] until it is swiped away. A player swiped away stays hidden
 * until it plays again after stopping, or another app's shows up. The swipe is recorded in [dismissal], which
 * the caller keeps outside the screen: this view leaves the composition whenever a letter list or search
 * takes its place, and anything remembered here would be forgotten with it.
 *
 * [app] and [isPlaying] read a player's app and state; [onStop] stops it when it is swiped away.
 */
@Composable
internal fun <P : Any> PlayerSlot(
    playing: P?,
    app: (P) -> String,
    isPlaying: (P) -> Boolean,
    dismissal: MutableStateFlow<Dismissal>,
    onStop: (P) -> Unit,
    content: @Composable (player: P, onClose: () -> Unit) -> Unit,
) {
    val dismissed by dismissal.collectAsStateWithLifecycle()
    LaunchedEffect(playing) {
        dismissal.value = dismissal.value.next(playing?.let(app), playing?.let(isPlaying) == true)
    }
    val shown = playing?.takeUnless { dismissed.hides(app(it)) }
    // Opens and closes downwards, as it did in the home column.
    AnimatedVisibility(shown != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        // Keeps showing the last track while it animates away.
        var last by remember { mutableStateOf(shown) }
        if (shown != null) last = shown
        last?.let {
            content(it) {
                dismissal.value = Dismissal(app(it))
                onStop(it)
            }
        }
    }
}
