package cc.gyula.wave.core

import org.junit.Assert.assertEquals
import org.junit.Test

class WaitingTest {
    @Test fun aPostedNotificationMakesItsAppWait() {
        val w = Waiting()
        assertEquals(setOf("mail"), w.posted("k1", "mail", waiting = true))
    }

    @Test fun anUpdateTurningOngoingStopsTheWait() {
        val w = Waiting()
        w.posted("k1", "player", waiting = true)
        assertEquals(emptySet<String>(), w.posted("k1", "player", waiting = false))
    }

    @Test fun anOngoingPostNeverWaits() {
        assertEquals(emptySet<String>(), Waiting().posted("k1", "nav", waiting = false))
    }

    @Test fun anAppStaysWaitingWhileOneOfItsNotificationsRemains() {
        val w = Waiting()
        w.posted("k1", "chat", waiting = true)
        w.posted("k2", "chat", waiting = true)
        assertEquals(setOf("chat"), w.removed("k1"))
        assertEquals(emptySet<String>(), w.removed("k2"))
    }

    @Test fun removingAnUnknownKeyChangesNothing() {
        val w = Waiting()
        w.posted("k1", "chat", waiting = true)
        assertEquals(setOf("chat"), w.removed("nope"))
    }

    @Test fun aFullListReplacesWhatWasKnown() {
        val w = Waiting()
        w.posted("old", "gone", waiting = true)
        val all = listOf(Triple("k1", "a", true), Triple("k2", "b", true), Triple("k3", "c", false))
        assertEquals(setOf("a", "b"), w.reset(all))
    }

    @Test fun clearForgetsEverything() {
        val w = Waiting()
        w.posted("k1", "a", waiting = true)
        assertEquals(emptySet<String>(), w.clear())
        assertEquals(emptySet<String>(), w.removed("k1"))
    }
}
