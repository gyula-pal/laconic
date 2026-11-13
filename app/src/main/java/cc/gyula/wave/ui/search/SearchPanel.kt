package cc.gyula.wave.ui.search

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.gyula.wave.AppsViewModel
import cc.gyula.wave.core.HomeList
import cc.gyula.wave.core.MIN_ALLOW_QUERY
import cc.gyula.wave.core.MIN_PEOPLE_QUERY
import cc.gyula.wave.core.Part
import cc.gyula.wave.core.Target
import cc.gyula.wave.core.calculate
import cc.gyula.wave.core.enterTarget
import cc.gyula.wave.core.eventWhen
import cc.gyula.wave.core.hiddenCount
import cc.gyula.wave.core.parseQuery
import cc.gyula.wave.core.partsShown
import cc.gyula.wave.core.ranked
import cc.gyula.wave.core.scopeWord
import cc.gyula.wave.core.shown
import cc.gyula.wave.core.showHeaders
import cc.gyula.wave.core.webAddress
import cc.gyula.wave.core.Scope
import cc.gyula.wave.data.AppEntry
import cc.gyula.wave.data.Happening
import cc.gyula.wave.data.Person
import cc.gyula.wave.data.Reach
import cc.gyula.wave.ui.apps.AppRow
import cc.gyula.wave.ui.theme.Label
import cc.gyula.wave.ui.theme.LocalInk
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Search opened by pulling the home screen down: apps, then contacts and calendar events, as you type. Enter
 * opens the top match (the row is marked), or searches the web when nothing matches. A web address opens in
 * the browser; a sum shows its result, tap to copy. Contacts and events are read when search opens.
 */
@Composable
internal fun SearchPanel(
    vm: AppsViewModel,
    apps: List<AppEntry>,
    favoriteKeys: List<String>,
    openCard: MutableState<String?>,
    onClose: () -> Unit,
) {
    val rows = remember { HashMap<String, Rect>() } // no slide-to-launch here; rows still report bounds
    val contactsOn by vm.settings.searchContacts.flow.collectAsStateWithLifecycle()
    val eventsOn by vm.settings.searchEvents.flow.collectAsStateWithLifecycle()
    var contactsGranted by remember { mutableStateOf(vm.people.canRead()) }
    var eventsGranted by remember { mutableStateOf(vm.agenda.canRead()) }
    // Back from Android's app settings, where a refused permission may have been switched on.
    LifecycleResumeEffect(Unit) {
        contactsGranted = vm.people.canRead()
        eventsGranted = vm.agenda.canRead()
        onPauseOrDispose {}
    }
    var people by remember { mutableStateOf(emptyList<Person>()) }
    var happenings by remember { mutableStateOf(emptyList<Happening>()) }
    var use by remember { mutableStateOf(emptyMap<String, Float>()) }
    LaunchedEffect(contactsOn, contactsGranted) {
        people = if (contactsOn && contactsGranted) vm.people.everyone() else emptyList()
    }
    LaunchedEffect(eventsOn, eventsGranted) {
        happenings = if (eventsOn && eventsGranted) vm.agenda.upcoming() else emptyList()
    }
    LaunchedEffect(Unit) {
        val used = withContext(Dispatchers.IO) { vm.usage.used(HomeList.FREQUENT) }
        use = used?.withIndex()?.associate { (i, pkg) -> pkg to 1f - i.toFloat() / used.size } ?: emptyMap()
    }
    val askContacts = rememberAsk(Manifest.permission.READ_CONTACTS, { contactsGranted = true }, vm.system::openAppDetails)
    val askEvents = rememberAsk(Manifest.permission.READ_CALENDAR, { eventsGranted = true }, vm.system::openAppDetails)
    SearchContent(
        items = apps,
        // Renamed apps are found by either name; hidden ones too, since search is the way back to them.
        names = { listOf(it.label, it.originalLabel) },
        key = { it.key },
        use = { use[it.packageName] ?: 0f },
        contacts = sourceOf(contactsOn, contactsGranted),
        people = people,
        events = sourceOf(eventsOn, eventsGranted),
        happenings = happenings,
        now = remember { System.currentTimeMillis() },
        zone = remember { ZoneId.systemDefault() },
        loadReach = vm.people::reach,
        loadAppIcon = { pkg -> apps.firstOrNull { it.packageName == pkg }?.let { vm.catalog.icon(it) } },
        actions = PeopleActions(
            dial = vm.system::dial,
            message = vm.system::message,
            email = vm.system::email,
            openReach = vm.system::openReach,
            openContact = { vm.system.openContact(vm.people.uri(it)) },
            openEvent = { vm.system.openEvent(it.eventId, it.begin, it.end) },
            allow = { if (it == Part.CONTACTS) askContacts() else askEvents() },
            hide = { if (it == Part.CONTACTS) vm.settings.searchContacts.set(false) else vm.settings.searchEvents.set(false) },
        ),
        onQueryChange = { openCard.value = null },
        onLaunch = vm.catalog::launch,
        onBrowse = vm.system::openUrl,
        onCopy = vm.system::copy,
        onWebSearch = vm.system::webSearch,
        onClose = onClose,
    ) { AppRow(vm, it, false, it.key in favoriteKeys, rows, openCard = openCard, onLaunch = onClose) }
}

private fun sourceOf(on: Boolean, granted: Boolean) = if (!on) Source.OFF else if (granted) Source.READY else Source.ASK

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * Asks for [permission]. Once Android has stopped showing its dialog for it (refused for good: no rationale is
 * offered after a refusal), the same tap opens Laconic's page in Android settings instead.
 */
@Composable
private fun rememberAsk(permission: String, onGranted: () -> Unit, openSettings: () -> Unit): () -> Unit {
    val activity = LocalContext.current.activity()
    var blocked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onGranted()
        else if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) blocked = true
    }
    return { if (blocked) openSettings() else launcher.launch(permission) }
}

/** Whether a source of results is off, switched on but not allowed by Android yet, or readable. */
internal enum class Source { OFF, ASK, READY }

/** What a tap on a contact, an event or a permission line hands on. */
internal class PeopleActions(
    val dial: (String) -> Unit = {},
    val message: (String) -> Unit = {},
    val email: (String) -> Unit = {},
    val openReach: (Reach) -> Unit = {},
    val openContact: (Person) -> Unit = {},
    val openEvent: (Happening) -> Unit = {},
    val allow: (Part) -> Unit = {},
    val hide: (Part) -> Unit = {},
)

/**
 * The search field and its results, apart from what the picks do, so a test can supply the data and see which
 * callbacks Enter and the taps end up calling. Anything that opens another app also calls [onClose]; copying a
 * sum, expanding a contact and asking for a permission leave search open.
 */
@Composable
internal fun <T> SearchContent(
    items: List<T>,
    names: (T) -> List<String>,
    key: (T) -> Any,
    onQueryChange: () -> Unit,
    onLaunch: (T) -> Unit,
    onBrowse: (String) -> Unit,
    onCopy: (String) -> Unit,
    onWebSearch: (String) -> Unit,
    onClose: () -> Unit,
    use: (T) -> Float = { 0f },
    contacts: Source = Source.OFF,
    people: List<Person> = emptyList(),
    events: Source = Source.OFF,
    happenings: List<Happening> = emptyList(),
    now: Long = 0L,
    zone: ZoneId = ZoneId.of("UTC"),
    loadReach: suspend (Person) -> List<Reach> = { emptyList() },
    loadAppIcon: suspend (String) -> ImageBitmap? = { null },
    actions: PeopleActions = PeopleActions(),
    row: @Composable (T) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // Both reset whenever the text does: what was expanded belonged to the old list.
    var more by remember(query) { mutableStateOf(emptySet<Part>()) }
    var expanded by remember(query) { mutableStateOf<Long?>(null) }
    val reach = remember { mutableStateMapOf<Long, List<Reach>>() }
    val focus = remember { FocusRequester() }

    val q = remember(query) { parseQuery(query) }
    val text = q.text
    val appHits = remember(items, text) { if (text.isBlank()) emptyList() else ranked(items, text, names, use) }
    val contactHits = remember(people, text, contacts) {
        if (contacts == Source.READY && text.length >= MIN_PEOPLE_QUERY) ranked(people, text, { listOf(it.name) }) else emptyList()
    }
    val eventHits = remember(happenings, text, events) {
        if (events == Source.READY && text.length >= MIN_PEOPLE_QUERY) ranked(happenings, text, { listOf(it.title) }) else emptyList()
    }
    val askContacts = contacts == Source.ASK && text.length >= MIN_ALLOW_QUERY
    val askEvents = events == Source.ASK && text.length >= MIN_ALLOW_QUERY
    val parts = partsShown(
        q.scope,
        buildSet {
            if (appHits.isNotEmpty()) add(Part.APPS)
            if (contactHits.isNotEmpty() || askContacts) add(Part.CONTACTS)
            if (eventHits.isNotEmpty() || askEvents) add(Part.EVENTS)
        },
    )
    val all = q.scope == Scope.ALL
    val sum = remember(text, all) { if (all) calculate(text) else null }
    val address = remember(text, all) { if (all) webAddress(text) else null }
    val target = enterTarget(q, appHits.isNotEmpty(), contactHits.isNotEmpty(), eventHits.isNotEmpty())

    fun web() { onWebSearch(text); onClose() }
    fun browse(url: String) { onBrowse(url); onClose() }
    /** Hand something to another app, and leave search. */
    fun act(block: () -> Unit) { block(); onClose() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .windowInsetsPadding(WindowInsets.ime)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 52.dp, end = 24.dp, top = 32.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it; onQueryChange() },
                singleLine = true,
                textStyle = Label.copy(fontSize = 22.sp),
                cursorBrush = SolidColor(LocalInk.current),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    when (target) {
                        Target.ADDRESS -> address?.let(::browse)
                        Target.APP -> { onLaunch(appHits.first()); onClose() }
                        Target.CONTACT -> expanded = contactHits.first().id
                        Target.EVENT -> act { actions.openEvent(eventHits.first()) }
                        Target.SUM -> sum?.let(onCopy)
                        Target.WEB -> web()
                        Target.NONE -> {}
                    }
                }),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { field ->
                    Box {
                        if (query.isEmpty()) {
                            Text("Search", style = Label.copy(color = LocalInk.current.copy(alpha = 0.4f)), fontSize = 22.sp)
                        }
                        field()
                    }
                },
            )
            scopeWord(q.scope)?.let {
                Text(it, style = Label.copy(color = LocalInk.current.copy(alpha = 0.4f)), fontSize = 22.sp, modifier = Modifier.padding(start = 12.dp))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (address != null) {
                Row(Modifier.fillMaxWidth().clickable { browse(address) }.padding(start = 52.dp, end = 24.dp, top = 4.dp, bottom = 16.dp)) {
                    Text(
                        "Open ${address.removePrefix("https://")}",
                        style = Label,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (target == Target.ADDRESS) EnterGlyph("address", Modifier.padding(start = 8.dp))
                }
            }
            if (sum != null) {
                Row(
                    Modifier.fillMaxWidth().clickable { onCopy(sum) }.padding(start = 52.dp, end = 24.dp, top = 4.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("= $sum", style = Label, fontSize = 26.sp, modifier = Modifier.weight(1f))
                    if (target == Target.SUM) EnterGlyph("sum", Modifier.padding(start = 8.dp))
                }
            }
            val headers = showHeaders(parts)
            parts.forEach { part ->
                if (headers) SectionHeader(part.word)
                val open = part in more
                fun expand() { more = more + part }
                when (part) {
                    Part.APPS -> {
                        shown(appHits, open, part.cap).forEachIndexed { i, app ->
                            key(key(app)) {
                                Box(Modifier.fillMaxWidth()) {
                                    row(app)
                                    if (i == 0 && target == Target.APP) {
                                        EnterGlyph("app", Modifier.align(Alignment.CenterEnd).padding(end = 24.dp))
                                    }
                                }
                            }
                        }
                        hiddenCount(appHits.size, open, part.cap).takeIf { it > 0 }?.let { MoreRow(it, ::expand) }
                    }
                    Part.CONTACTS -> if (contactHits.isEmpty()) {
                        AllowRow("contacts", "contacts", { actions.allow(part) }, { actions.hide(part) })
                    } else {
                        shown(contactHits, open, part.cap).forEachIndexed { i, person ->
                            key(person.id) {
                                LaunchedEffect(person.id) { if (person.id !in reach) reach[person.id] = loadReach(person) }
                                ContactRow(
                                    person = person,
                                    reach = reach[person.id],
                                    expanded = expanded == person.id,
                                    enter = i == 0 && target == Target.CONTACT,
                                    loadAppIcon = loadAppIcon,
                                    onToggle = { expanded = if (expanded == person.id) null else person.id },
                                    onOpen = { act { actions.openContact(person) } },
                                    onDial = { act { actions.dial(it) } },
                                    onMessage = { act { actions.message(it) } },
                                    onEmail = { act { actions.email(it) } },
                                    onReach = { act { actions.openReach(it) } },
                                )
                            }
                        }
                        hiddenCount(contactHits.size, open, part.cap).takeIf { it > 0 }?.let { MoreRow(it, ::expand) }
                    }
                    Part.EVENTS -> if (eventHits.isEmpty()) {
                        AllowRow("calendar", "events", { actions.allow(part) }, { actions.hide(part) })
                    } else {
                        shown(eventHits, open, part.cap).forEachIndexed { i, h ->
                            key(h.eventId to h.begin) {
                                EventRow(h, eventWhen(h.begin, h.allDay, now, zone), i == 0 && target == Target.EVENT) {
                                    act { actions.openEvent(h) }
                                }
                            }
                        }
                        hiddenCount(eventHits.size, open, part.cap).takeIf { it > 0 }?.let { MoreRow(it, ::expand) }
                    }
                }
            }
            if (text.isNotBlank() && sum == null && address == null) {
                Row(
                    Modifier.fillMaxWidth().clickable { web() }.padding(start = 52.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Search the web for “$text”",
                        style = Label.copy(color = LocalInk.current.copy(alpha = 0.6f)),
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (target == Target.WEB) EnterGlyph("web", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
