package dev.pinaki.homedeck

import android.content.ComponentName
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import dev.pinaki.homedeck.ui.theme.HomeDeckTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import java.io.File

class LauncherScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rendersAppsCommandsAndNoResults() {
        val model = model()
        model.updateSources(apps = listOf(LauncherApp("Calculator", ComponentName("test", "Calculator"))))
        compose.setContent { HomeDeckTheme { LauncherScreen(model) { null } } }
        compose.onNodeWithText("Calculator").assertExists()

        compose.runOnIdle { model.edit(value("/")) }
        compose.onNodeWithText("launch").assertExists()
        compose.onNodeWithText("shortcut").assertExists()

        compose.runOnIdle { model.edit(value("missing")) }
        compose.onNodeWithText("command not found: missing").assertExists()
    }

    @Test fun rendersShortcutMenuFormValidationAndConfirmation() {
        val model = model()
        model.updateSources(shortcuts = listOf(Shortcut("Existing")))
        compose.setContent { HomeDeckTheme { LauncherScreen(model) { null } } }

        compose.runOnIdle {
            model.edit(value("/")); model.moveSelection(1); model.advance(); model.moveSelection(-99)
        }
        compose.onNodeWithText("Add shortcut").assertExists()
        compose.onNodeWithText("Existing").assertExists()

        compose.runOnIdle { model.advance(); model.advance(); model.advance() }
        compose.onNodeWithText("name is required").assertExists()
        compose.runOnIdle {
            model.edit(value("News")); model.advance()
        }
        compose.onNodeWithText("action> ").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun physicalAltCommandsUseSelectedApp() {
        val model = model()
        model.updateSources(apps = listOf(LauncherApp("Calculator", ComponentName("com.example.calc", ".Main"))))
        val effects = mutableListOf<LauncherEffect>()
        compose.setContent { HomeDeckTheme { LauncherScreen(model) { effects += it; null } } }

        compose.onNode(hasSetTextAction()).performKeyInput {
            keyDown(Key.AltLeft); keyDown(Key.D); keyUp(Key.D); keyUp(Key.AltLeft)
        }
        compose.runOnIdle { assertEquals(LauncherEffect.UninstallApp("com.example.calc"), effects.single()) }

        compose.onNode(hasSetTextAction()).performKeyInput {
            keyDown(Key.AltLeft); keyDown(Key.S); keyUp(Key.S); keyUp(Key.AltLeft)
        }
        compose.runOnIdle { assertEquals(LauncherEffect.OpenAppSettings("com.example.calc"), effects.last()) }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun physicalAltEditUsesSelectedShortcut() {
        val model = model()
        model.updateSources(shortcuts = listOf(Shortcut("News")))
        compose.setContent { HomeDeckTheme { LauncherScreen(model) { null } } }

        compose.onNode(hasSetTextAction()).performKeyInput {
            keyDown(Key.AltLeft); keyDown(Key.E); keyUp(Key.E); keyUp(Key.AltLeft)
        }
        compose.runOnIdle {
            assertEquals(ShortcutPage.NAME, model.state.shortcutPage)
            assertEquals("News", model.state.input.text)
        }
    }

    @Test fun onScreenAltExposesCommands() {
        val model = model()
        model.updateSources(shortcuts = listOf(Shortcut("News")))
        val alt = mutableStateOf(false)
        compose.setContent { HomeDeckTheme { ExtraKeys(false, alt.value) { key ->
            when (key) {
                "ALT" -> alt.value = !alt.value
                "E" -> { model.editSelected(); alt.value = false }
            }
        } } }

        compose.onNodeWithText("ALT").performClick()
        compose.onNodeWithText("E").performClick()
        compose.runOnIdle { assertEquals(ShortcutPage.NAME, model.state.shortcutPage) }
    }

    private fun value(text: String) = TextFieldValue(text, TextRange(text.length))
    private fun model() = LauncherViewModel(
        SavedStateHandle(), ShortcutStore(File.createTempFile("shortcuts", ".json").apply { delete() }),
        ActionStore { "[]" }, PackageStore { Result.success(emptyList()) },
    )
}
