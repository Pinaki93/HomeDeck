package dev.pinaki.homedeck

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutTest {
    @Test fun validationCoversRequiredDuplicateAndComponent() {
        assertEquals("name is required", shortcutError(Shortcut(""), emptyList()))
        assertEquals("shortcut already exists: NEWS", shortcutError(Shortcut("NEWS"), listOf(Shortcut("news"))))
        assertEquals("action is required", shortcutError(Shortcut("News", action = ""), emptyList()))
        assertEquals("component must be package/class", shortcutError(Shortcut("News", component = "bad"), emptyList()))
        assertEquals(null, shortcutError(Shortcut("News", component = "com.example/.Main"), emptyList()))
    }

    @Test fun formNavigationMovesOneStepAtATime() {
        assertEquals(ShortcutPage.TYPE, ShortcutPage.NAME.previous())
        assertEquals(ShortcutPage.ACTION, ShortcutPage.NAME.next())
        assertEquals(ShortcutPage.COMPONENT, ShortcutPage.CONFIRM.previous())
        assertEquals(null, ShortcutPage.MENU.previous())
    }

    @Test fun intentValuesCoverOptionalFieldsAndComponentPrecedence() {
        assertEquals(
            ShortcutIntent("view", "https://example.com", null, "com.example/.Main"),
            Shortcut("News", "view", "https://example.com", "ignored", "com.example/.Main").intentValues(),
        )
        assertEquals(
            ShortcutIntent("view", null, "com.example", null),
            Shortcut("News", "view", packageName = "com.example").intentValues(),
        )
    }

    @Test fun storeRoundTripsAndDoesNotReplaceMalformedFile() {
        val file = Files.createTempDirectory("homedeck").resolve("shortcuts.json").toFile()
        val store = ShortcutStore(file)
        val shortcuts = listOf(Shortcut("News", data = "https://example.com", packageName = "com.example"))
        assertTrue(store.save(shortcuts).isSuccess)
        assertEquals(shortcuts, store.load().getOrThrow())

        file.writeText("not json")
        assertTrue(store.load().isFailure)
        assertEquals("not json", file.readText())
    }
}
