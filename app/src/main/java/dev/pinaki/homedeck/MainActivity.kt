package dev.pinaki.homedeck

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.format.DateFormat
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pinaki.homedeck.ui.theme.HomeDeckTheme
import java.text.Collator
import java.util.*
import kotlinx.coroutines.delay

data class LauncherApp(val label: String, val component: ComponentName)

internal fun <T> filterAndSortApps(
    apps: List<T>, query: String, locale: Locale = Locale.getDefault(), label: (T) -> String,
): List<T> = apps.filter { label(it).contains(query, ignoreCase = true) }
    .sortedWith { a, b -> Collator.getInstance(locale).compare(label(a), label(b)) }

class MainActivity : ComponentActivity() {
    private var apps by mutableStateOf(emptyList<LauncherApp>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HomeDeckTheme { LauncherScreen(apps, ::launch) } }
    }

    override fun onResume() {
        super.onResume()
        apps = loadApps()
    }

    private fun loadApps(): List<LauncherApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return filterAndSortApps(
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .filter { it.activityInfo.packageName != packageName }
                .mapNotNull {
                    runCatching {
                        LauncherApp(
                            it.loadLabel(packageManager).toString(),
                            ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                        )
                    }.getOrNull()
                },
            "", label = LauncherApp::label,
        )
    }

    private fun launch(app: LauncherApp): String? = try {
        startActivity(Intent.makeMainActivity(app.component))
        null
    } catch (_: Exception) {
        "cannot launch: ${app.label}"
    }
}

@Composable
private fun LauncherScreen(apps: List<LauncherApp>, launch: (LauncherApp) -> String?) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf(TextFieldValue()) }
    var highlighted by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    val results = remember(apps, input.text) {
        filterAndSortApps(
            apps,
            input.text,
            label = LauncherApp::label
        )
    }
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    LaunchedEffect(imeVisible) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(results) {
        highlighted = highlighted.coerceIn(0, (results.size - 1).coerceAtLeast(0))
    }
    LaunchedEffect(highlighted) {
        if (results.isNotEmpty()) listState.animateScrollToItem(
            highlighted
        )
    }
    BackHandler { if (input.text.isNotEmpty()) input = TextFieldValue() }

    fun open(app: LauncherApp) {
        input = TextFieldValue()
        error = launch(app)
    }

    fun insert(text: String) {
        val start = input.selection.min
        val changed = input.text.replaceRange(start, input.selection.max, text)
        input = TextFieldValue(changed, TextRange(start + text.length))
    }

    fun cursor(position: Int) {
        input = input.copy(selection = TextRange(position.coerceIn(0, input.text.length)))
    }

    fun keyCode(key: String) = when (key) {
        "ESC" -> KeyEvent.KEYCODE_ESCAPE; "TAB" -> KeyEvent.KEYCODE_TAB
        "/" -> KeyEvent.KEYCODE_SLASH; "-" -> KeyEvent.KEYCODE_MINUS
        "HOME" -> KeyEvent.KEYCODE_MOVE_HOME; "END" -> KeyEvent.KEYCODE_MOVE_END
        "←" -> KeyEvent.KEYCODE_DPAD_LEFT; "↑" -> KeyEvent.KEYCODE_DPAD_UP
        "→" -> KeyEvent.KEYCODE_DPAD_RIGHT; "↓" -> KeyEvent.KEYCODE_DPAD_DOWN
        "PGUP" -> KeyEvent.KEYCODE_PAGE_UP; else -> KeyEvent.KEYCODE_PAGE_DOWN
    }

    fun consumeModifiers(key: String) {
        if (!ctrl && !alt) return
        val meta =
            (if (ctrl) KeyEvent.META_CTRL_ON else 0) or (if (alt) KeyEvent.META_ALT_ON else 0)
        (context as? ComponentActivity)?.window?.decorView?.dispatchKeyEvent(
            KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode(key), 0, meta),
        )
        (context as? ComponentActivity)?.window?.decorView?.dispatchKeyEvent(
            KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode(key), 0, meta),
        )
        ctrl = false
        alt = false
    }

    Column(Modifier
        .fillMaxSize()
        .background(Color(0xFF050706))
        .statusBarsPadding()
        .imePadding()) {
        Text(
            DateFormat.getTimeFormat(context).format(now),
            fontSize = 46.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp)
        )
        Text(
            DateFormat.getMediumDateFormat(context).format(now),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 22.dp, bottom = 10.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("> ", color = MaterialTheme.colorScheme.primary)
            BasicTextField(
                value = input, onValueChange = { input = it; error = null },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent {
                        if (it.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                        when (it.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> highlighted =
                                (highlighted - 1).coerceAtLeast(0)

                            KeyEvent.KEYCODE_DPAD_DOWN -> highlighted =
                                (highlighted + 1).coerceAtMost((results.size - 1).coerceAtLeast(0))

                            KeyEvent.KEYCODE_DPAD_RIGHT ->
                                results.getOrNull(highlighted)?.let(::open)

                            else -> return@onPreviewKeyEvent false
                        }
                        true
                    },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                singleLine = true, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    if (input.text.isNotEmpty()) results.getOrNull(highlighted)?.let(::open)
                }),
                decorationBox = { inner ->
                    Box {
                        if (input.text.isEmpty()) Text(
                            "_",
                            color = MaterialTheme.colorScheme.primary
                        ); inner()
                    }
                },
            )
        }
        val message = error
            ?: if (input.text.isNotEmpty() && results.isEmpty()) "command not found: ${input.text}" else null
        if (message != null) Text(
            message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        AppList(results, highlighted, listState, Modifier.weight(1f))
        if (imeVisible) ExtraKeys(ctrl, alt) { key ->
            when (key) {
                "CTRL" -> ctrl = !ctrl
                "ALT" -> alt = !alt
                "ESC" -> input = TextFieldValue()
                "TAB" -> results.getOrNull(highlighted)
                    ?.let { input = TextFieldValue(it.label, TextRange(it.label.length)) }

                "/", "-" -> insert(key)
                "HOME" -> cursor(0)
                "END" -> cursor(input.text.length)
                "←" -> cursor(input.selection.start - 1)
                "→" -> results.getOrNull(highlighted)?.let(::open)
                "↑" -> highlighted = (highlighted - 1).coerceAtLeast(0)
                "↓" -> highlighted =
                    (highlighted + 1).coerceAtMost((results.size - 1).coerceAtLeast(0))
            }
            if (key != "CTRL" && key != "ALT") consumeModifiers(key)
        }
        if (!imeVisible) Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun AppList(
    apps: List<LauncherApp>, highlighted: Int, state: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = state, userScrollEnabled = false, modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(apps, key = { _, app -> app.component.flattenToString() }) { index, app ->
            Text(
                app.label,
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(if (index == highlighted) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ExtraKeys(ctrl: Boolean, alt: Boolean, onKey: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
    ) {
        listOf(
            listOf("ESC", "/", "-", "HOME", "↑", "END"),
            listOf("TAB", "CTRL", "ALT", "←", "↓", "→"),
        ).forEach { keys ->
            Row(Modifier.fillMaxWidth()) {
                keys.forEach { key ->
                    val active = (key == "CTRL" && ctrl) || (key == "ALT" && alt)
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable(role = Role.Button) { onKey(key) },
                        contentAlignment = Alignment.Center,
                    ) { Text(key, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp) }
                }
            }
        }
    }
}
