package dev.pinaki.homedeck

import android.text.format.DateFormat
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*
import kotlinx.coroutines.delay

@Composable
internal fun LauncherScreen(
    viewModel: LauncherViewModel,
    execute: (LauncherEffect) -> String?,
) {
    val state = viewModel.state
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    LaunchedEffect(imeVisible) { focusRequester.requestFocus(); keyboard?.show() }
    LaunchedEffect(state.highlighted, state.results) {
        if (state.results.isNotEmpty()) listState.animateScrollToItem(state.highlighted)
    }
    LaunchedEffect(state.effect) {
        state.effect?.let { viewModel.completeEffect(execute(it)) }
    }
    BackHandler(state.shortcutPage != null || state.input.text.isNotEmpty(), viewModel::back)

    fun keyCode(key: String) = when (key) {
        "ESC" -> KeyEvent.KEYCODE_ESCAPE; "TAB" -> KeyEvent.KEYCODE_TAB
        "/" -> KeyEvent.KEYCODE_SLASH; "-" -> KeyEvent.KEYCODE_MINUS
        "HOME" -> KeyEvent.KEYCODE_MOVE_HOME; "END" -> KeyEvent.KEYCODE_MOVE_END
        "←" -> KeyEvent.KEYCODE_DPAD_LEFT; "↑" -> KeyEvent.KEYCODE_DPAD_UP
        "→" -> KeyEvent.KEYCODE_DPAD_RIGHT; "↓" -> KeyEvent.KEYCODE_DPAD_DOWN
        "PGUP" -> KeyEvent.KEYCODE_PAGE_UP; else -> KeyEvent.KEYCODE_PAGE_DOWN
    }

    fun consumeModifiers(key: String) {
        if (!state.ctrl && !state.alt) return
        val meta = (if (state.ctrl) KeyEvent.META_CTRL_ON else 0) or
            (if (state.alt) KeyEvent.META_ALT_ON else 0)
        repeat(2) { action -> (context as? ComponentActivity)?.window?.decorView?.dispatchKeyEvent(
            KeyEvent(0, 0, action, keyCode(key), 0, meta),
        ) }
        viewModel.resetModifiers()
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF050706)).statusBarsPadding().imePadding()) {
        Text(
            DateFormat.getTimeFormat(context).format(now), fontSize = 46.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp),
        )
        Text(
            DateFormat.getMediumDateFormat(context).format(now),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 22.dp, bottom = 10.dp),
        )
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.shortcutPage?.takeIf { it in ShortcutPage.NAME..ShortcutPage.COMPONENT }
                    ?.name?.lowercase()?.let { "$it> " } ?: "> ",
                color = MaterialTheme.colorScheme.primary,
            )
            BasicTextField(
                value = state.input, onValueChange = viewModel::edit,
                modifier = Modifier.weight(1f).focusRequester(focusRequester).onPreviewKeyEvent {
                    if (it.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                    when (it.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> viewModel.moveSelection(-1)
                        KeyEvent.KEYCODE_DPAD_DOWN -> viewModel.moveSelection(1)
                        KeyEvent.KEYCODE_DPAD_RIGHT -> viewModel.advance()
                        KeyEvent.KEYCODE_DPAD_LEFT -> viewModel.back()
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                singleLine = true, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { viewModel.advance() }),
                decorationBox = { inner -> Box { if (state.input.text.isEmpty()) Text("_", color = MaterialTheme.colorScheme.primary); inner() } },
            )
        }
        state.message?.let { Text(
            it, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        ) }
        LauncherList(state.results, state.highlighted, listState, Modifier.weight(1f))
        if (imeVisible) ExtraKeys(state.ctrl, state.alt) { key ->
            when (key) {
                "CTRL" -> viewModel.toggleModifier(true)
                "ALT" -> viewModel.toggleModifier(false)
                "ESC" -> viewModel.back()
                "TAB" -> viewModel.tab()
                "/", "-" -> viewModel.insert(key)
                "HOME" -> viewModel.moveCursor(0)
                "END" -> viewModel.moveCursor(state.input.text.length)
                "←" -> if (state.shortcutPage != null) viewModel.back() else viewModel.moveCursor(state.input.selection.start - 1)
                "→" -> viewModel.advance()
                "↑" -> viewModel.moveSelection(-1)
                "↓" -> viewModel.moveSelection(1)
            }
            if (key != "CTRL" && key != "ALT") consumeModifiers(key)
        }
        if (!imeVisible) Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun LauncherList(
    entries: List<LauncherEntry>, highlighted: Int, state: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = state, userScrollEnabled = false, modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
            Text(
                entry.label,
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
