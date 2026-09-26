package dev.pinaki.homedeck

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LauncherViewModelTest {
    @Test fun commandsFilteringSelectionAndEditing() {
        val model = model()
        model.edit(value("/"))
        assertEquals(listOf("launch", "shorcut", "help"), model.state.results.map { it.label })
        model.edit(value("/lau"))
        assertEquals(listOf("launch"), model.state.results.map { it.label })
        model.moveSelection(99)
        assertEquals(0, model.state.highlighted)
        model.edit(value("/"))
        model.moveSelection(99)
        assertEquals(2, model.state.highlighted)
        model.moveSelection(-99)
        assertEquals(0, model.state.highlighted)
        model.tab()
        assertEquals("/launch", model.state.input.text)

        model.edit(value("/"))
        model.moveSelection(1)
        model.advance()
        model.edit(value("/"))
        assertNull(model.state.shortcutPage)
        assertEquals(listOf("launch", "shorcut", "help"), model.state.results.map { it.label })

        model.edit(value("/launch DEFAULT"))
        assertEquals(listOf("Make default"), model.state.results.map { it.label })
        model.advance()
        assertTrue(model.state.effect is LauncherEffect.ExecuteAction)
        model.completeEffect("failed")
        assertEquals("failed", model.state.message)
        model.edit(value("missing"))
        assertEquals("command not found: missing", model.state.message)

        model.edit(TextFieldValue("ac", TextRange(1)))
        model.insert("-")
        assertEquals("a-c", model.state.input.text)
        model.moveCursor(99)
        assertEquals(3, model.state.input.selection.start)
        model.toggleModifier(true)
        model.toggleModifier(false)
        assertTrue(model.state.ctrl)
        assertTrue(model.state.alt)
        model.resetModifiers()
        assertFalse(model.state.ctrl)
        assertFalse(model.state.alt)
    }

    @Test fun helpShowsNonActionableReference() {
        val model = model()
        model.edit(value("/help"))
        assertEquals(launcherHelp.map { it.first }, model.state.results.map { it.label })
        assertEquals(launcherHelp.map { it.second }, model.state.results.map { it.description })
        model.advance()
        assertNull(model.state.effect)
        assertEquals("/help", model.state.input.text)
    }

    @Test fun altCommandsIgnoreUnsupportedSelections() {
        val model = model()
        model.edit(value("/help"))
        model.deleteSelected()
        model.editSelected()
        model.openSelectedAppSettings()
        assertNull(model.state.effect)
    }

    @Test fun altEditCancelsOrReplacesShortcutWithoutDuplicatingIt() {
        val original = Shortcut("News", data = "https://example.com")
        val weather = Shortcut("Weather")
        val file = File.createTempFile("shortcuts", ".json").apply { delete() }
        val store = ShortcutStore(file)
        store.save(listOf(original, weather)).getOrThrow()
        val model = LauncherViewModel(SavedStateHandle(), store, actions(), FakePackageStore())

        model.edit(value("new"))
        model.editSelected()
        assertEquals(ShortcutPage.NAME, model.state.shortcutPage)
        assertEquals(original, model.state.draft)
        assertEquals("News", model.state.input.text)
        model.edit(value("Draft"))
        model.back()
        assertNull(model.state.shortcutPage)
        assertEquals("new", model.state.input.text)
        assertEquals(listOf("News"), model.state.results.map { it.label })
        assertEquals(listOf(original, weather), store.load().getOrThrow())

        model.editSelected()
        model.edit(value("Weather")); model.advance()
        assertEquals("shortcut already exists: Weather", model.state.message)
        model.edit(value("Latest")); model.advance() // action
        model.advance() // data
        model.advance() // package
        model.advance() // any compatible app
        model.advance() // component
        model.advance() // save

        val effect = model.state.effect as LauncherEffect.SaveShortcut
        assertEquals(listOf(original.copy(name = "Latest"), weather), effect.shortcuts)
        model.completeEffect("disk full")
        assertEquals(listOf(original, weather), store.load().getOrThrow())
        assertEquals(ShortcutPage.CONFIRM, model.state.shortcutPage)

        model.advance()
        model.completeEffect(null)
        assertEquals(listOf(original.copy(name = "Latest"), weather), store.load().getOrThrow())
        assertEquals(listOf("Add shortcut", "Latest", "Weather"), model.state.results.map { it.label })
    }

    @Test fun altDeletePersistsShortcutAndRetainsItWhenSavingFails() {
        val shortcut = Shortcut("News")
        val file = File.createTempFile("shortcuts", ".json").apply { delete() }
        val store = ShortcutStore(file)
        store.save(listOf(shortcut)).getOrThrow()
        val model = LauncherViewModel(SavedStateHandle(), store, actions(), FakePackageStore())
        model.deleteSelected()
        assertEquals(emptyList<Shortcut>(), store.load().getOrThrow())
        assertEquals(emptyList<String>(), model.state.results.map { it.label })

        val blockedParent = File.createTempFile("shortcuts-parent", ".tmp")
        val failingStore = ShortcutStore(File(blockedParent, "shortcuts.json"))
        val failing = LauncherViewModel(SavedStateHandle(), failingStore, actions(), FakePackageStore())
        failing.updateSources(shortcuts = listOf(shortcut))
        failing.deleteSelected()
        assertEquals(listOf("News"), failing.state.results.map { it.label })
        assertTrue(failing.state.message!!.startsWith("cannot delete shortcut:"))
    }

    @Test fun shorcutCommandOpensMenuOrSearchesSavedShortcuts() {
        val model = model()
        model.updateSources(
            shortcuts = listOf(Shortcut("News"), Shortcut("Weather")),
        )

        model.edit(value("/shorcut"))
        assertEquals(listOf("Add shortcut", "News", "Weather"), model.state.results.map { it.label })

        model.edit(value("/shorcut new"))
        assertEquals(listOf("News"), model.state.results.map { it.label })
    }

    @Test fun shortcutFormPreservesFieldsValidatesAndSaves() {
        val store = store()
        val packages = FakePackageStore(Result.success(listOf(LauncherPackage("Example", "com.example"))))
        val model = LauncherViewModel(SavedStateHandle(), store, actions(), packages)
        model.updateSources(shortcuts = listOf(Shortcut("Existing")))
        openShortcutForm(model)

        model.advance()
        assertEquals("name is required", model.state.message)
        model.edit(value("  News  "))
        model.advance()
        assertEquals(ShortcutPage.ACTION, model.state.shortcutPage)
        assertEquals("News", model.state.draft.name)
        assertEquals(android.content.Intent.ACTION_VIEW, model.state.input.text)

        model.edit(value(" "))
        model.advance()
        assertEquals("action is required", model.state.message)
        model.edit(value("view"))
        model.advance() // data
        model.edit(value(" https://example.com "))
        model.advance() // package
        assertEquals(
            listOf("Any compatible app", "Example (com.example)"),
            model.state.results.map { it.label },
        )
        model.moveSelection(1)
        model.advance() // component
        model.edit(value("bad"))
        model.advance()
        assertEquals("component must be package/class", model.state.message)
        model.edit(value("com.example/.Main"))
        model.advance()
        assertEquals(ShortcutPage.CONFIRM, model.state.shortcutPage)
        model.advance()

        val effect = model.state.effect as LauncherEffect.SaveShortcut
        assertEquals("News", effect.shortcut.name)
        assertEquals("com.example", effect.shortcut.packageName)
        assertEquals(2, effect.shortcuts.size)
        model.completeEffect("disk full")
        assertEquals(ShortcutPage.CONFIRM, model.state.shortcutPage)
        assertEquals("disk full", model.state.message)

        model.advance()
        model.completeEffect(null)
        assertEquals(ShortcutPage.MENU, model.state.shortcutPage)
        assertEquals(listOf("Add shortcut", "Existing", "News"), model.state.results.map { it.label })
        assertNull(model.state.message)
        assertEquals(listOf("Existing", "News"), store.load().getOrThrow().map { it.name })
    }

    @Test fun shortcutPresetPopulatesIntentAndAnyAppLeavesPackageEmpty() {
        val model = model(FakePackageStore(Result.success(listOf(LauncherPackage("Dialer", "com.example.dialer")))))
        model.edit(value("/"))
        model.moveSelection(1)
        model.advance() // menu
        model.advance() // add

        assertEquals(
            listOf("Open web page", "Dial phone number", "Open map location", "Custom intent"),
            model.state.results.map { it.label },
        )
        model.moveSelection(1)
        model.advance()
        assertEquals(ShortcutPage.NAME, model.state.shortcutPage)
        assertEquals(android.content.Intent.ACTION_DIAL, model.state.draft.action)
        assertEquals("tel:", model.state.draft.data)

        model.edit(value("Phone")); model.advance() // action
        model.advance() // data
        model.advance() // package
        model.advance() // any compatible app
        assertEquals(ShortcutPage.COMPONENT, model.state.shortcutPage)
        assertEquals("", model.state.draft.packageName)
    }

    @Test fun backPreservesDraftAndLoadErrorIsMenuOnly() {
        val model = model()
        model.updateSources(shortcutLoadError = "cannot load")
        openShortcutForm(model)
        model.edit(value(" Draft "))
        model.back()
        assertEquals(ShortcutPage.TYPE, model.state.shortcutPage)
        assertEquals("Draft", model.state.draft.name)
        assertNull(model.state.message)
        model.back()
        assertEquals(ShortcutPage.MENU, model.state.shortcutPage)
        assertEquals("cannot load", model.state.message)
        model.back()
        assertNull(model.state.shortcutPage)
        assertEquals("", model.state.input.text)
    }

    @Test fun packageLoadingUsesDraftReloadsOnBackAndCanRetry() {
        val packages = FakePackageStore(Result.success(listOf(LauncherPackage("Browser", "com.browser"))))
        val model = model(packages)
        openShortcutForm(model)
        model.edit(value("Web")); model.advance() // action
        model.advance() // data
        model.edit(value(" https://example.com ")); model.advance() // package

        assertEquals(Shortcut("Web", data = "https://example.com"), packages.requests.single())
        assertEquals(listOf("Any compatible app", "Browser (com.browser)"), model.state.results.map { it.label })
        model.moveSelection(1); model.advance() // component
        assertEquals("com.browser", model.state.draft.packageName)

        packages.result = Result.failure(IllegalStateException("resolver unavailable"))
        model.back() // package
        assertEquals(2, packages.requests.size)
        assertEquals(listOf("Any compatible app"), model.state.results.map { it.label })
        assertEquals("cannot load packages: resolver unavailable", model.state.message)

        model.back() // data
        packages.result = Result.success(emptyList())
        model.advance() // package retry
        assertEquals(3, packages.requests.size)
        assertEquals(listOf("Any compatible app"), model.state.results.map { it.label })
        assertNull(model.state.message)
    }

    @Test fun savedShortcutProducesEffectAndSuccessClearsIt() {
        val shortcut = Shortcut("News")
        val handle = SavedStateHandle()
        val store = store()
        val model = LauncherViewModel(handle, store, actions(), FakePackageStore())
        model.updateSources(shortcuts = listOf(shortcut))
        assertEquals(listOf("News"), model.state.results.map { it.label })
        model.edit(value("new"))
        assertEquals(listOf("News"), model.state.results.map { it.label })
        model.edit(value("/"))
        model.moveSelection(1)
        model.advance()
        model.moveSelection(1)
        model.advance()
        assertEquals(LauncherEffect.ExecuteShortcut(shortcut), model.state.effect)
        assertEquals("/shorcut", model.state.input.text)
        model.completeEffect(null)
        assertNull(model.state.effect)
        assertNull(model.state.message)
        assertEquals("/shorcut", LauncherViewModel(handle, store(), actions(), FakePackageStore()).state.input.text)
    }

    private fun openShortcutForm(model: LauncherViewModel) {
        model.edit(value("/"))
        model.moveSelection(1)
        model.advance() // menu
        model.moveSelection(-99)
        model.advance() // add
        model.moveSelection(99)
        model.advance() // custom
        assertEquals(ShortcutPage.NAME, model.state.shortcutPage)
    }

    private fun value(text: String) = TextFieldValue(text, TextRange(text.length))
    private fun model(packages: PackageStore = FakePackageStore()) =
        LauncherViewModel(SavedStateHandle(), store(), actions(), packages)
    private fun actions() = ActionStore { """[{"name":"Zulu"},{"name":"Make default"}]""" }
    private fun store() = ShortcutStore(File.createTempFile("shortcuts", ".json").apply { delete() })
}
