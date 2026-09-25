package dev.pinaki.homedeck

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherFilterTest {
    private val apps = listOf("Zulu", "alpha", "Calculator")

    @Test fun emptyQueryReturnsLocalizedOrder() =
        assertEquals(listOf("alpha", "Calculator", "Zulu"), filterAndSortApps(apps, "", Locale.US) { it })

    @Test fun matchingIgnoresCase() =
        assertEquals(listOf("Calculator"), filterAndSortApps(apps, "CALC", Locale.US) { it })

    @Test fun noMatchReturnsEmptyList() =
        assertEquals(emptyList<String>(), filterAndSortApps(apps, "terminal", Locale.US) { it })

    @Test fun launcherActionCommandExtractsItsQueryOnly() {
        assertEquals("", actionQuery("/l"))
        assertEquals("default", actionQuery("/l default"))
        assertEquals(null, actionQuery("/launch"))
    }

    @Test fun actionFilteringIgnoresCase() {
        val actions = listOf(LauncherAction("Make HomeDeck the default app"))
        assertEquals(actions, filterAndSortApps(actions, actionQuery("/l HOME")!!) { it.name })
    }
}
