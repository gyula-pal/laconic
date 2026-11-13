package cc.gyula.wave

import android.app.Application
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.gyula.wave.core.BoxTree
import cc.gyula.wave.core.Dismissal
import cc.gyula.wave.core.Polled
import cc.gyula.wave.core.boxes
import cc.gyula.wave.core.pinsForBoxApps
import cc.gyula.wave.core.pinsReordered
import cc.gyula.wave.core.pinsToggled
import cc.gyula.wave.core.reconciled
import cc.gyula.wave.core.renamed
import cc.gyula.wave.core.setBoxApps
import cc.gyula.wave.data.Agenda
import cc.gyula.wave.data.AppCatalog
import cc.gyula.wave.data.AppEntry
import cc.gyula.wave.data.EventState
import cc.gyula.wave.data.People
import cc.gyula.wave.data.SettingsStore
import cc.gyula.wave.data.SystemActions
import cc.gyula.wave.data.Torch
import cc.gyula.wave.data.UsageSource
import cc.gyula.wave.data.WidgetStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The one ViewModel: it builds Laconic's data sources and lives as long as the home screen's activity
 * does, so what's on screen survives the views that come and go. The sources take their Android
 * dependencies through their constructors, so a test can build any of them on its own.
 */
class AppsViewModel(app: Application) : AndroidViewModel(app) {
    val settings = SettingsStore(app.getSharedPreferences("wave", Context.MODE_PRIVATE))
    val catalog = AppCatalog(app, settings.names.flow, viewModelScope)
    val usage = UsageSource(app)
    val widgets = WidgetStore(app, settings)
    val system = SystemActions(app)
    val torch = Torch(app)
    val people = People(app.contentResolver) { granted(app, Manifest.permission.READ_CONTACTS) }
    val agenda = Agenda(app.contentResolver) { granted(app, Manifest.permission.READ_CALENDAR) }

    /** The player swiped away, if any. Kept here: the home view leaves the screen for letter lists and search. */
    val dismissal = MutableStateFlow(Dismissal())

    /**
     * The clock's last weather, price and next event, with when each was fetched. Kept here for the same reason:
     * back from a letter list or search, the clock shows them at once and fetches only when they are due.
     */
    val lastWeather = MutableStateFlow<Polled<String>?>(null)
    val lastPrice = MutableStateFlow<Polled<String>?>(null)
    val lastEvent = MutableStateFlow<Polled<EventState>?>(null)

    /** The boxes on home: the saved tree kept in step with the pins, so switching layouts loses no app. */
    val boxes: StateFlow<BoxTree> = combine(settings.boxes.flow, settings.pins.flow, ::reconciled).stateIn(
        viewModelScope, SharingStarted.Eagerly, reconciled(settings.boxes.value, settings.pins.value),
    )

    init {
        widgets.releaseUnplaced()
    }

    override fun onCleared() {
        catalog.close()
        torch.close()
    }

    fun togglePin(entry: AppEntry) = settings.pins.set(pinsToggled(settings.pins.value, entry.key))

    fun setPinOrder(keys: List<String>) = settings.pins.set(pinsReordered(settings.pins.value, keys))

    fun updateBoxes(change: (BoxTree) -> BoxTree) = settings.boxes.set(change(boxes.value))

    /**
     * Box [boxId] set to exactly [keys]: apps ticked that were not pinned are pinned, apps that left the box are
     * unpinned, then the tree follows. [boxes] catches up with the pins asynchronously, so it may or may not hold
     * a just-pinned key yet; either way [setBoxApps] puts every key of [keys] in the target box (and takes it out
     * of any other), and [reconciled] leaves a key where it is already found, so the end state is the same.
     * The box's current apps are read from a fresh reconciliation, not from the possibly stale [boxes].
     */
    fun setBoxApps(boxId: Int, keys: List<String>) {
        val before = reconciled(settings.boxes.value, settings.pins.value).boxes().find { it.id == boxId }?.apps ?: return
        settings.pins.set(pinsForBoxApps(settings.pins.value, before, keys))
        updateBoxes { setBoxApps(it, boxId, keys) }
    }

    fun setHidden(entry: AppEntry, hide: Boolean) =
        settings.hidden.set(if (hide) settings.hidden.value + entry.key else settings.hidden.value - entry.key)

    /** A blank name goes back to the app's own. */
    fun rename(entry: AppEntry, name: String) =
        settings.names.set(renamed(settings.names.value, entry.key, entry.originalLabel, name))
}

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
