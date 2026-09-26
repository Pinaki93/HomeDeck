package dev.pinaki.homedeck

import android.content.ComponentName
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import dev.pinaki.homedeck.ui.theme.HomeDeckTheme
import org.junit.Rule
import org.junit.Test
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

    private fun value(text: String) = TextFieldValue(text, TextRange(text.length))
    private fun model() = LauncherViewModel(
        SavedStateHandle(), ShortcutStore(File.createTempFile("shortcuts", ".json").apply { delete() }),
        ActionStore { "[]" }, PackageStore { Result.success(emptyList()) },
    )
}
