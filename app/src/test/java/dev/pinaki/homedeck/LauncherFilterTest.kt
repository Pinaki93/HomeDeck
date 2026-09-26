package dev.pinaki.homedeck

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherFilterTest {
    @Test fun installedBrowsersAreAddedOnlyForWebShortcuts() {
        val chrome = LauncherPackage("Chrome", "com.android.chrome")
        assertEquals(
            listOf(LauncherPackage("Chrome", "com.android.chrome")),
            addInstalledBrowsers(Shortcut("Web", data = "https://example.com"), emptyList(), listOf(chrome)),
        )
        assertEquals(
            emptyList<LauncherPackage>(),
            addInstalledBrowsers(Shortcut("Phone", data = "tel:123"), emptyList(), listOf(chrome)),
        )
    }

    private val apps = listOf("Zulu", "alpha", "Calculator")

    @Test fun emptyQueryReturnsLocalizedOrder() =
        assertEquals(listOf("alpha", "Calculator", "Zulu"), filterAndSortApps(apps, "", Locale.US) { it })

    @Test fun matchingIgnoresCase() =
        assertEquals(listOf("Calculator"), filterAndSortApps(apps, "CALC", Locale.US) { it })

    @Test fun noMatchReturnsEmptyList() =
        assertEquals(emptyList<String>(), filterAndSortApps(apps, "terminal", Locale.US) { it })

    @Test fun launcherActionCommandExtractsItsQueryOnly() {
        assertEquals("", actionQuery("/launch"))
        assertEquals("default", actionQuery("/launch default"))
        assertEquals(null, actionQuery("/l"))
    }

    @Test fun actionFilteringIgnoresCase() {
        val actions = listOf(LauncherAction("Make HomeDeck the default app"))
        assertEquals(actions, filterAndSortApps(actions, actionQuery("/launch HOME")!!) { it.name })
    }

    @Test fun slashShowsLaunchCommand() =
        assertEquals(listOf("launch" to "/launch", "shorcut" to "/shorcut", "help" to "/help"), launcherCommands)
}
