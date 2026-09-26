package dev.pinaki.homedeck

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import java.text.Collator
import java.util.Locale

internal fun actionQuery(input: String): String? = when {
    input == "/launch" -> ""
    input.startsWith("/launch ") -> input.removePrefix("/launch ")
    else -> null
}

internal fun shortcutQuery(input: String): String? =
    input.takeIf { it.startsWith("/shorcut ") }?.removePrefix("/shorcut ")

internal val launcherCommands = listOf("launch" to "/launch", "shorcut" to "/shorcut", "help" to "/help")

internal val launcherHelp = listOf(
    "/help" to "Command reference",
    "/launch <query>" to "Search actions",
    "/shorcut [query]" to "Manage or search shortcuts",
    "Alt+D" to "Uninstall selected app or delete selected shortcut",
    "Alt+S" to "Open settings for selected app",
)

internal fun <T> filterAndSortApps(
    apps: List<T>, query: String, locale: Locale = Locale.getDefault(), label: (T) -> String,
): List<T> = apps.filter { label(it).contains(query, ignoreCase = true) }
    .sortedWith { a, b -> Collator.getInstance(locale).compare(label(a), label(b)) }

internal sealed interface LauncherTarget {
    data class App(val value: LauncherApp) : LauncherTarget
    data class Action(val value: LauncherAction) : LauncherTarget
    data class SavedShortcut(val value: Shortcut) : LauncherTarget
    data object AddShortcut : LauncherTarget
    data class ShortcutPreset(val value: dev.pinaki.homedeck.ShortcutPreset) : LauncherTarget
    data object CustomShortcut : LauncherTarget
    data class IntentPackage(val packageName: String?) : LauncherTarget
    data object SaveShortcut : LauncherTarget
    data class Command(val completion: String) : LauncherTarget
    data object Reference : LauncherTarget
}

internal data class LauncherEntry(
    val key: String, val label: String, val completion: String? = null,
    val target: LauncherTarget, val description: String? = null,
)

internal sealed interface LauncherEffect {
    data class LaunchApp(val app: LauncherApp) : LauncherEffect
    data class ExecuteAction(val action: LauncherAction) : LauncherEffect
    data class ExecuteShortcut(val shortcut: Shortcut) : LauncherEffect
    data class SaveShortcut(val shortcut: Shortcut, val shortcuts: List<Shortcut>) : LauncherEffect
    data class UninstallApp(val packageName: String) : LauncherEffect
    data class OpenAppSettings(val packageName: String) : LauncherEffect
}

internal data class LauncherState(
    val input: TextFieldValue = TextFieldValue(),
    val highlighted: Int = 0,
    val results: List<LauncherEntry> = emptyList(),
    val message: String? = null,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shortcutPage: ShortcutPage? = null,
    val draft: Shortcut = Shortcut(name = ""),
    val effect: LauncherEffect? = null,
)

internal class LauncherViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val shortcutStore: ShortcutStore,
    actionStore: ActionStore,
    private val packageStore: PackageStore,
) : ViewModel() {
    var state by mutableStateOf(LauncherState(input = textValue(savedStateHandle.get<String>(INPUT).orEmpty())))
        private set

    private var apps = emptyList<LauncherApp>()
    private val actions = actionStore.load()
    private var shortcutLoadError: String? = null
    private var shortcuts = shortcutStore.load().getOrElse {
        shortcutLoadError = "cannot load shortcuts: ${it.message}"
        emptyList()
    }
    private var compatiblePackages = emptyList<LauncherPackage>()

    init { refresh() }

    fun updateSources(
        apps: List<LauncherApp> = this.apps,
        shortcuts: List<Shortcut> = this.shortcuts,
        shortcutLoadError: String? = this.shortcutLoadError,
    ) {
        this.apps = apps
        this.shortcuts = shortcuts
        this.shortcutLoadError = shortcutLoadError
        refresh()
    }

    fun edit(value: TextFieldValue) {
        savedStateHandle[INPUT] = value.text
        state = state.copy(
            input = value,
            shortcutPage = ShortcutPage.MENU.takeIf { value.text == "/shorcut" }
                ?: state.shortcutPage.takeUnless { it == ShortcutPage.MENU },
            message = null,
        )
        refresh()
    }

    fun moveSelection(delta: Int) {
        state = state.copy(highlighted = (state.highlighted + delta).coerceInResults())
    }

    fun tab() = state.results.getOrNull(state.highlighted)?.let {
        setInput(it.completion ?: it.label)
    } ?: Unit

    fun moveCursor(position: Int) {
        state = state.copy(input = state.input.copy(
            selection = TextRange(position.coerceIn(0, state.input.text.length)),
        ))
    }

    fun insert(text: String) {
        val selection = state.input.selection
        val changed = state.input.text.replaceRange(selection.min, selection.max, text)
        edit(TextFieldValue(changed, TextRange(selection.min + text.length)))
    }

    fun back() {
        val draft = state.shortcutPage?.takeIf { it in ShortcutPage.NAME..ShortcutPage.COMPONENT }
            ?.let { state.draft.withField(it, state.input.text) } ?: state.draft
        val previous = state.shortcutPage?.previous()
        state = state.copy(
            shortcutPage = previous,
            draft = draft,
            input = textValue(previous?.let { fieldValue(it, draft) }.orEmpty()),
            message = null,
        )
        if (previous == ShortcutPage.PACKAGE) loadPackages(draft)
        refresh()
    }

    fun advance() {
        val page = state.shortcutPage
        if (page == null || page == ShortcutPage.MENU || page == ShortcutPage.TYPE ||
            page == ShortcutPage.PACKAGE || page == ShortcutPage.CONFIRM
        ) {
            state.results.getOrNull(state.highlighted)?.let(::open)
            return
        }
        val draft = state.draft.withField(page, state.input.text)
        val error = when (page) {
            ShortcutPage.NAME -> shortcutError(draft, shortcuts)?.takeIf { it.startsWith("name") || it.startsWith("shortcut") }
            ShortcutPage.ACTION -> shortcutError(draft, shortcuts)?.takeIf { it.startsWith("action") }
            ShortcutPage.COMPONENT -> shortcutError(draft, shortcuts)?.takeIf { it.startsWith("component") }
            else -> null
        }
        state = if (error != null) state.copy(draft = draft, message = error) else {
            val next = page.next()
            state.copy(draft = draft, shortcutPage = next, input = textValue(fieldValue(next, draft)), message = null)
        }
        if (state.shortcutPage == ShortcutPage.PACKAGE) loadPackages(state.draft)
        refresh()
    }

    fun toggleModifier(ctrl: Boolean) {
        state = if (ctrl) state.copy(ctrl = !state.ctrl) else state.copy(alt = !state.alt)
    }

    fun resetModifiers() {
        state = state.copy(ctrl = false, alt = false)
    }

    fun deleteSelected() {
        when (val target = state.results.getOrNull(state.highlighted)?.target) {
            is LauncherTarget.App -> state = state.copy(
                effect = LauncherEffect.UninstallApp(target.value.component.packageName),
            )
            is LauncherTarget.SavedShortcut -> {
                val remaining = shortcuts - target.value
                shortcutStore.save(remaining).fold(
                    onSuccess = { shortcuts = remaining; state = state.copy(message = null) },
                    onFailure = { state = state.copy(message = "cannot delete shortcut: ${it.message}") },
                )
                refresh()
            }
            else -> Unit
        }
    }

    fun openSelectedAppSettings() {
        val app = (state.results.getOrNull(state.highlighted)?.target as? LauncherTarget.App)?.value ?: return
        state = state.copy(effect = LauncherEffect.OpenAppSettings(app.component.packageName))
    }

    fun completeEffect(error: String?) {
        val effect = state.effect
        val completionError = error ?: (effect as? LauncherEffect.SaveShortcut)?.let {
            shortcutStore.save(it.shortcuts).exceptionOrNull()?.let { cause ->
                "cannot save shortcut: ${cause.message}"
            }
        }
        if (completionError == null && effect is LauncherEffect.SaveShortcut) {
            shortcuts = shortcuts + effect.shortcut
            shortcutLoadError = null
            state = state.copy(shortcutPage = ShortcutPage.MENU, message = null, effect = null)
        } else state = state.copy(message = completionError, effect = null)
        refresh()
    }

    private fun open(entry: LauncherEntry) {
        when (val target = entry.target) {
            is LauncherTarget.Command -> {
                savedStateHandle[INPUT] = target.completion
                state = state.copy(
                    input = textValue(target.completion),
                    shortcutPage = ShortcutPage.MENU.takeIf { target.completion == "/shorcut" },
                    message = null,
                )
                refresh()
            }
            LauncherTarget.AddShortcut -> state = state.copy(
                draft = Shortcut(name = ""), shortcutPage = ShortcutPage.TYPE,
                input = TextFieldValue(), message = null,
            )
            is LauncherTarget.ShortcutPreset -> state = state.copy(
                draft = state.draft.copy(action = target.value.action, data = target.value.data),
                shortcutPage = ShortcutPage.NAME, input = TextFieldValue(), message = null,
            )
            LauncherTarget.CustomShortcut -> state = state.copy(shortcutPage = ShortcutPage.NAME, input = TextFieldValue(), message = null)
            is LauncherTarget.IntentPackage -> state = state.copy(
                draft = state.draft.copy(packageName = target.packageName.orEmpty()),
                shortcutPage = ShortcutPage.COMPONENT,
                input = textValue(state.draft.component), message = null,
            )
            LauncherTarget.SaveShortcut -> state = state.copy(
                effect = LauncherEffect.SaveShortcut(state.draft, shortcuts + state.draft), input = TextFieldValue(),
            )
            is LauncherTarget.App -> state = state.copy(effect = LauncherEffect.LaunchApp(target.value), input = TextFieldValue())
            is LauncherTarget.Action -> state = state.copy(effect = LauncherEffect.ExecuteAction(target.value), input = TextFieldValue())
            is LauncherTarget.SavedShortcut -> state = state.copy(effect = LauncherEffect.ExecuteShortcut(target.value))
            LauncherTarget.Reference -> Unit
        }
        refresh()
    }

    private fun setInput(text: String) {
        savedStateHandle[INPUT] = text
        state = state.copy(input = textValue(text), message = null)
        refresh()
    }

    private fun loadPackages(shortcut: Shortcut) {
        packageStore.load(shortcut).fold(
            onSuccess = {
                compatiblePackages = it
                state = state.copy(message = null)
            },
            onFailure = {
                compatiblePackages = emptyList()
                state = state.copy(message = "cannot load packages: ${it.message}")
            },
        )
    }

    private fun refresh() {
        val results = results()
        val highlighted = state.highlighted.coerceIn(0, (results.size - 1).coerceAtLeast(0))
        val automaticMessage = when {
            state.shortcutPage == ShortcutPage.MENU -> shortcutLoadError
            state.shortcutPage == null && state.input.text.isNotEmpty() && results.isEmpty() ->
                "command not found: ${state.input.text}"
            else -> null
        }
        state = state.copy(results = results, highlighted = highlighted, message = state.message ?: automaticMessage)
    }

    private fun results(): List<LauncherEntry> = when (state.shortcutPage) {
        ShortcutPage.MENU -> listOf(LauncherEntry("shortcut:add", "Add shortcut", target = LauncherTarget.AddShortcut)) +
            shortcuts.map { LauncherEntry("shortcut:${it.name}", it.name, target = LauncherTarget.SavedShortcut(it)) }
        ShortcutPage.TYPE -> shortcutPresets.map {
            LauncherEntry("shortcut:preset:${it.label}", it.label, target = LauncherTarget.ShortcutPreset(it))
        } + LauncherEntry("shortcut:custom", "Custom intent", target = LauncherTarget.CustomShortcut)
        ShortcutPage.PACKAGE -> listOf(
            LauncherEntry("package:any", "Any compatible app", target = LauncherTarget.IntentPackage(null)),
        ) + compatiblePackages.map {
            LauncherEntry(
                "package:${it.packageName}", "${it.label} (${it.packageName})",
                target = LauncherTarget.IntentPackage(it.packageName),
            )
        }
        ShortcutPage.CONFIRM -> listOf(LauncherEntry("shortcut:save", "Save shortcut", target = LauncherTarget.SaveShortcut))
        null -> {
            val query = actionQuery(state.input.text)
            val shortcutQuery = shortcutQuery(state.input.text)
            when {
                state.input.text == "/help" -> launcherHelp.mapIndexed { index, (label, description) ->
                    LauncherEntry("help:$index", label, description = description, target = LauncherTarget.Reference)
                }
                state.input.text.startsWith("/") && query == null && shortcutQuery == null ->
                    launcherCommands.filter { (_, completion) -> completion.startsWith(state.input.text) }
                        .map { (label, completion) ->
                            LauncherEntry("command:$label", label, completion, LauncherTarget.Command(completion))
                        }
                query != null -> filterAndSortApps(actions, query, label = LauncherAction::name).map {
                    LauncherEntry("action:${it.name}", it.name, target = LauncherTarget.Action(it))
                }
                shortcutQuery != null -> filterAndSortApps(shortcuts, shortcutQuery, label = Shortcut::name).map {
                    LauncherEntry("shortcut:${it.name}", it.name, target = LauncherTarget.SavedShortcut(it))
                }
                else -> filterAndSortApps(shortcuts, state.input.text, label = Shortcut::name).map {
                    LauncherEntry("shortcut:${it.name}", it.name, target = LauncherTarget.SavedShortcut(it))
                } + filterAndSortApps(apps, state.input.text, label = LauncherApp::label).map {
                    LauncherEntry(it.component.flattenToString(), it.label, target = LauncherTarget.App(it))
                }
            }
        }
        else -> emptyList()
    }

    private fun Int.coerceInResults() = coerceIn(0, (state.results.size - 1).coerceAtLeast(0))
    private companion object {
        const val INPUT = "input"
        fun textValue(text: String) = TextFieldValue(text, TextRange(text.length))
    }
}
