package cc.gyula.wave.ui.search

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import cc.gyula.wave.core.Part
import cc.gyula.wave.data.Happening
import cc.gyula.wave.data.Person
import cc.gyula.wave.data.Reach
import cc.gyula.wave.data.ReachKind
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** What Enter and the taps do in the search panel, seen through the callbacks they fire. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchContentTest {
    @get:Rule
    val compose = createComposeRule()

    /** Everything search handed on, in order, as "kind: value". */
    private val calls = mutableListOf<String>()

    private val anna = Person(1, "k1", "Anna Kovacs")
    private val reaches = mapOf(
        1L to listOf(
            Reach(ReachKind.PHONE, "+36 30 111 2222", "mobile"),
            Reach(ReachKind.EMAIL, "anna@example.com", "work"),
            Reach(ReachKind.APP, "Voice call +36301112222", "", dataId = 9, mime = "vnd.x/call", pkg = "com.viber"),
        ),
    )
    private val zone = ZoneId.of("UTC")
    // Wed 2026-11-11 12:00 UTC
    private val now = 1_794_398_400_000L
    private val dinner = Happening(7, "Dinner with Anna", now + 6 * 3_600_000L, now + 8 * 3_600_000L, false)

    private fun actions() = PeopleActions(
        dial = { calls += "dial: $it" },
        message = { calls += "message: $it" },
        email = { calls += "email: $it" },
        openReach = { calls += "reach: ${it.value}" },
        openContact = { calls += "contact: ${it.name}" },
        openEvent = { calls += "event: ${it.title}" },
        allow = { calls += "allow: $it" },
        hide = { calls += "hide: $it" },
    )

    private fun show(
        apps: List<String> = listOf("Firefox", "Fossify Calendar", "Maps"),
        contacts: Source = Source.OFF,
        people: List<Person> = emptyList(),
        events: Source = Source.OFF,
        happenings: List<Happening> = emptyList(),
    ) = compose.setContent {
        SearchContent(
            items = apps,
            names = { listOf(it) },
            key = { it },
            onQueryChange = {},
            onLaunch = { calls += "launch: $it" },
            onBrowse = { calls += "browse: $it" },
            onCopy = { calls += "copy: $it" },
            onWebSearch = { calls += "web: $it" },
            onClose = { calls += "close" },
            contacts = contacts,
            people = people,
            events = events,
            happenings = happenings,
            now = now,
            zone = zone,
            loadReach = { reaches[it.id].orEmpty() },
            actions = actions(),
        ) { Text(it) }
    }

    private fun type(text: String) = compose.onNode(hasSetTextAction()).performTextInput(text)

    private fun typeAndEnter(text: String) {
        type(text)
        compose.onNode(hasSetTextAction()).performImeAction()
    }

    private fun showPeople() = show(
        apps = listOf("Anna Banana Shop", "Annex"),
        contacts = Source.READY,
        people = listOf(anna),
        events = Source.READY,
        happenings = listOf(dinner),
    )

    @Test
    fun enterLaunchesTheTopMatchAndCloses() {
        show()
        typeAndEnter("fi")
        assertEquals(listOf("launch: Firefox", "close"), calls)
    }

    @Test
    fun enterOnASumCopiesItAndStaysOpen() {
        show()
        typeAndEnter("12*7")
        assertEquals(listOf("copy: 84"), calls)
    }

    @Test
    fun enterOnATypedAddressOpensItInTheBrowser() {
        show()
        typeAndEnter("example.com")
        assertEquals(listOf("browse: https://example.com", "close"), calls)
    }

    @Test
    fun enterWithNoMatchSearchesTheWebForTheTrimmedQuery() {
        show()
        typeAndEnter("  zebra crossing ")
        assertEquals(listOf("web: zebra crossing", "close"), calls)
    }

    @Test
    fun enterOnAnEmptyFieldDoesNothing() {
        show()
        compose.onNode(hasSetTextAction()).performImeAction()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun tappingTheSumCopiesIt() {
        show()
        type("12*7")
        compose.onNodeWithText("= 84").performClick()
        assertEquals(listOf("copy: 84"), calls)
    }

    @Test
    fun tappingTheWebLineSearchesTheWeb() {
        show()
        type("zebra")
        compose.onNodeWithText("Search the web for “zebra”").performClick()
        assertEquals(listOf("web: zebra", "close"), calls)
    }

    @Test
    fun theRowEnterWouldOpenIsMarked() {
        show()
        type("fi")
        compose.onAllNodesWithTag("enter:app").assertCountEquals(1)
        compose.onAllNodesWithTag("enter:web", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun theWebRowIsMarkedWhenNothingElseFits() {
        show()
        type("zebra")
        compose.onAllNodesWithTag("enter:web", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun oneSectionHasNoHeader() {
        show()
        type("fo")
        compose.onAllNodesWithText("apps").assertCountEquals(0)
    }

    @Test
    fun twoSectionsGetHeadersInOrder() {
        showPeople()
        type("anna")
        compose.onNodeWithText("apps").assertIsDisplayed()
        compose.onNodeWithText("contacts").assertIsDisplayed()
        compose.onNodeWithText("events").assertIsDisplayed()
    }

    @Test
    fun peopleAreOnlyLookedUpFromTwoLetters() {
        showPeople()
        type("a")
        compose.onAllNodesWithTag("contact:1").assertCountEquals(0)
        compose.onAllNodesWithTag("event:7").assertCountEquals(0)
    }

    @Test
    fun aSectionShowsFiveAppsThenMoreExpandsIt() {
        show(apps = (1..8).map { "Note $it" })
        type("note")
        compose.onAllNodesWithText("Note", substring = true).assertCountEquals(5)
        compose.onNodeWithText("+3 more").performClick()
        compose.onAllNodesWithText("Note", substring = true).assertCountEquals(8)
        compose.onAllNodesWithText("+3 more").assertCountEquals(0)
    }

    @Test
    fun expansionGoesBackWhenTheTextChanges() {
        show(apps = (1..8).map { "Note $it" })
        type("note")
        compose.onNodeWithText("+3 more").performClick()
        type(" ")
        compose.onAllNodesWithText("Note", substring = true).assertCountEquals(5)
    }

    @Test
    fun contactsAreCappedAtThree() {
        show(
            contacts = Source.READY,
            people = (1..5).map { Person(it.toLong(), "k$it", "Zed $it") },
            apps = emptyList(),
        )
        type("zed")
        compose.onAllNodesWithText("Zed", substring = true).assertCountEquals(3)
        compose.onNodeWithText("+2 more").assertIsDisplayed()
    }

    @Test
    fun aPrefixNarrowsToOneSectionAndNamesIt() {
        showPeople()
        type("c anna")
        compose.onNodeWithText("contacts").assertIsDisplayed() // the scope word, no header with one section
        compose.onAllNodesWithText("Anna Banana Shop").assertCountEquals(0)
        compose.onAllNodesWithTag("event:7").assertCountEquals(0)
        compose.onNodeWithTag("contact:1").assertIsDisplayed()
    }

    @Test
    fun theEventsPrefixShowsOnlyEvents() {
        showPeople()
        type("e anna")
        compose.onNodeWithText("events").assertIsDisplayed()
        compose.onAllNodesWithTag("contact:1").assertCountEquals(0)
        compose.onNodeWithTag("event:7").assertIsDisplayed()
    }

    @Test
    fun theAppsPrefixShowsOnlyApps() {
        showPeople()
        type("a anna")
        compose.onNodeWithText("apps").assertIsDisplayed()
        compose.onAllNodesWithTag("contact:1").assertCountEquals(0)
        compose.onNodeWithText("Anna Banana Shop").assertIsDisplayed()
    }

    @Test
    fun theWebPrefixHasOnlyTheWebRow() {
        showPeople()
        type("w anna")
        compose.onNodeWithText("web").assertIsDisplayed()
        compose.onAllNodesWithText("Anna Banana Shop").assertCountEquals(0)
        compose.onNodeWithText("Search the web for “anna”").assertIsDisplayed()
        typeAndEnterAgain()
        assertEquals(listOf("web: anna", "close"), calls)
    }

    private fun typeAndEnterAgain() = compose.onNode(hasSetTextAction()).performImeAction()

    @Test
    fun enterOnAContactExpandsItWithoutCalling() {
        show(contacts = Source.READY, people = listOf(anna), apps = emptyList())
        typeAndEnter("anna")
        assertEquals(emptyList<String>(), calls)
        compose.onNodeWithText("anna@example.com").assertIsDisplayed()
    }

    @Test
    fun enterOnAnEventOpensIt() {
        show(events = Source.READY, happenings = listOf(dinner), apps = emptyList())
        typeAndEnter("dinner")
        assertEquals(listOf("event: Dinner with Anna", "close"), calls)
    }

    @Test
    fun enterPrefersAnAppOverAContact() {
        showPeople()
        typeAndEnter("anna")
        assertEquals(listOf("launch: Anna Banana Shop", "close"), calls)
    }

    @Test
    fun tappingAContactShowsItsReachLines() {
        show(contacts = Source.READY, people = listOf(anna), apps = emptyList())
        type("anna")
        compose.onAllNodesWithText("+36 30 111 2222").assertCountEquals(0)
        compose.onNodeWithTag("contact:1").performClick()
        compose.onNodeWithText("+36 30 111 2222").assertIsDisplayed()
        compose.onNodeWithText("mobile").assertIsDisplayed()
        compose.onNodeWithText("anna@example.com").assertIsDisplayed()
        compose.onNodeWithText("Voice call +36301112222").assertIsDisplayed()
        compose.onNodeWithText("open contact ›").assertIsDisplayed()
    }

    @Test
    fun onlyOneContactIsOpenAtATime() {
        val bea = Person(2, "k2", "Anna Bea")
        show(contacts = Source.READY, people = listOf(anna, bea), apps = emptyList())
        type("anna")
        compose.onNodeWithTag("contact:1").performClick()
        compose.onNodeWithTag("contact:2").performClick()
        compose.onAllNodesWithText("open contact ›").assertCountEquals(1)
    }

    @Test
    fun theTrailingPhoneGlyphDialsTheFirstNumber() {
        show(contacts = Source.READY, people = listOf(anna), apps = emptyList())
        type("anna")
        compose.waitForIdle()
        compose.onNodeWithTag("dial:1").performClick()
        assertEquals(listOf("dial: +36 30 111 2222", "close"), calls)
    }

    @Test
    fun tappingAPhoneLineDialsAndTheMessageGlyphMessages() {
        show(contacts = Source.READY, people = listOf(anna), apps = emptyList())
        type("anna")
        compose.onNodeWithTag("contact:1").performClick()
        compose.onNodeWithText("+36 30 111 2222").performClick()
        compose.onNodeWithTag("message:+36 30 111 2222").performClick()
        assertEquals(listOf("dial: +36 30 111 2222", "close", "message: +36 30 111 2222", "close"), calls)
    }

    @Test
    fun anEmailLineWritesAnEmail() {
        show(contacts = Source.READY, people = listOf(anna), apps = emptyList())
        type("anna")
        compose.onNodeWithTag("contact:1").performClick()
        compose.onNodeWithText("anna@example.com").performClick()
        assertEquals(listOf("email: anna@example.com", "close"), calls)
    }

    @Test
    fun anAppLineOpensThatAppsOwnRow() {
        show(contacts = Source.READY, people = listOf(anna), apps = emptyList())
        type("anna")
        compose.onNodeWithTag("contact:1").performClick()
        compose.onNodeWithText("Voice call +36301112222").performClick()
        assertEquals(listOf("reach: Voice call +36301112222", "close"), calls)
    }

    @Test
    fun openContactLineOpensTheContact() {
        show(contacts = Source.READY, people = listOf(anna), apps = emptyList())
        type("anna")
        compose.onNodeWithTag("contact:1").performClick()
        compose.onNodeWithText("open contact ›").performClick()
        assertEquals(listOf("contact: Anna Kovacs", "close"), calls)
    }

    @Test
    fun anEventShowsWhenItHappensAndOpensOnTap() {
        show(events = Source.READY, happenings = listOf(dinner), apps = emptyList())
        type("dinner")
        compose.onNodeWithText("Dinner with Anna · 18:00").assertIsDisplayed()
        compose.onNodeWithTag("event:7").performClick()
        assertEquals(listOf("event: Dinner with Anna", "close"), calls)
    }

    @Test
    fun aSourceNotAllowedShowsOneLineToAskAndOneToHide() {
        show(contacts = Source.ASK, apps = emptyList())
        type("a")
        compose.onAllNodesWithText("contacts · allow").assertCountEquals(0) // one letter is too little
        type("n")
        compose.onNodeWithText("contacts · allow").performClick()
        compose.onNodeWithText("hide").performClick()
        assertEquals(listOf("allow: CONTACTS", "hide: CONTACTS"), calls)
    }

    @Test
    fun theCalendarAsksInItsOwnWords() {
        show(events = Source.ASK, apps = emptyList())
        type("an")
        compose.onNodeWithText("calendar · allow").performClick()
        compose.onNodeWithText("hide").performClick()
        assertEquals(listOf("allow: ${Part.EVENTS}", "hide: ${Part.EVENTS}"), calls)
    }

    @Test
    fun aSourceSwitchedOffShowsNothing() {
        show(contacts = Source.OFF, events = Source.OFF, apps = emptyList())
        type("anna")
        compose.onAllNodesWithText("contacts · allow").assertCountEquals(0)
        compose.onAllNodesWithText("calendar · allow").assertCountEquals(0)
    }
}
