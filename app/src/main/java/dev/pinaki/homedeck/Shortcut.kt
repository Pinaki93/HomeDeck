package dev.pinaki.homedeck

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Shortcut(
    val name: String,
    val action: String = Intent.ACTION_VIEW,
    val data: String = "",
    val packageName: String = "",
    val component: String = "",
)

internal data class ShortcutPreset(val label: String, val action: String, val data: String)

internal val shortcutPresets = listOf(
    ShortcutPreset("Open web page", Intent.ACTION_VIEW, "https://example.com"),
    ShortcutPreset("Dial phone number", Intent.ACTION_DIAL, "tel:"),
    ShortcutPreset("Open map location", Intent.ACTION_VIEW, "geo:0,0?q="),
)

internal fun shortcutError(shortcut: Shortcut, existing: List<Shortcut>): String? = when {
    shortcut.name.isBlank() -> "name is required"
    existing.any { it.name.equals(shortcut.name, ignoreCase = true) } -> "shortcut already exists: ${shortcut.name}"
    shortcut.action.isBlank() -> "action is required"
    shortcut.component.isNotBlank() && shortcut.component.split('/').let {
        it.size != 2 || it.any(String::isBlank)
    } -> "component must be package/class"
    else -> null
}

internal data class ShortcutIntent(
    val action: String, val data: String?, val packageName: String?, val component: String?,
)

internal fun Shortcut.intentValues() = ShortcutIntent(
    action,
    data.takeIf(String::isNotBlank),
    packageName.takeIf { component.isBlank() && it.isNotBlank() },
    component.takeIf(String::isNotBlank),
)

internal fun Shortcut.toIntent(): Intent = intentValues().let { values ->
    Intent(values.action).also { intent ->
        if (values.action == Intent.ACTION_VIEW) intent.addCategory(Intent.CATEGORY_BROWSABLE)
        values.data?.let { intent.data = Uri.parse(it) }
        values.component?.let { intent.component = ComponentName.unflattenFromString(it) }
        values.packageName?.let(intent::setPackage)
    }
}

class ShortcutStore(private val file: File) {
    fun load(): Result<List<Shortcut>> = runCatching {
        if (!file.exists()) return@runCatching emptyList()
        val json = JSONArray(file.readText())
        List(json.length()) { index ->
            json.getJSONObject(index).let {
                Shortcut(
                    it.getString("name"),
                    it.optString("action", Intent.ACTION_VIEW),
                    it.optString("data"),
                    it.optString("package"),
                    it.optString("component"),
                )
            }
        }
    }

    fun save(shortcuts: List<Shortcut>): Result<Unit> = runCatching {
        val json = JSONArray()
        shortcuts.forEach { shortcut ->
            json.put(JSONObject().put("name", shortcut.name).put("action", shortcut.action)
                .put("data", shortcut.data).put("package", shortcut.packageName)
                .put("component", shortcut.component))
        }
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        try {
            temporary.writeText(json.toString())
            check(temporary.renameTo(file)) { "cannot replace ${file.name}" }
        } finally {
            temporary.delete()
        }
    }
}

internal enum class ShortcutPage { MENU, TYPE, NAME, ACTION, DATA, PACKAGE, COMPONENT, CONFIRM }

internal fun ShortcutPage.previous() = when (this) {
    ShortcutPage.MENU -> null
    ShortcutPage.TYPE -> ShortcutPage.MENU
    ShortcutPage.NAME -> ShortcutPage.TYPE
    ShortcutPage.ACTION -> ShortcutPage.NAME
    ShortcutPage.DATA -> ShortcutPage.ACTION
    ShortcutPage.PACKAGE -> ShortcutPage.DATA
    ShortcutPage.COMPONENT -> ShortcutPage.PACKAGE
    ShortcutPage.CONFIRM -> ShortcutPage.COMPONENT
}

internal fun ShortcutPage.next() = when (this) {
    ShortcutPage.NAME -> ShortcutPage.ACTION
    ShortcutPage.ACTION -> ShortcutPage.DATA
    ShortcutPage.DATA -> ShortcutPage.PACKAGE
    ShortcutPage.PACKAGE -> ShortcutPage.COMPONENT
    ShortcutPage.COMPONENT -> ShortcutPage.CONFIRM
    else -> this
}

internal fun fieldValue(page: ShortcutPage, shortcut: Shortcut) = when (page) {
    ShortcutPage.ACTION -> shortcut.action
    ShortcutPage.NAME -> shortcut.name
    ShortcutPage.DATA -> shortcut.data
    ShortcutPage.PACKAGE -> shortcut.packageName
    ShortcutPage.COMPONENT -> shortcut.component
    else -> ""
}

internal fun Shortcut.withField(page: ShortcutPage, value: String) = when (page) {
    ShortcutPage.NAME -> copy(name = value.trim())
    ShortcutPage.ACTION -> copy(action = value.trim())
    ShortcutPage.DATA -> copy(data = value.trim())
    ShortcutPage.PACKAGE -> copy(packageName = value.trim())
    ShortcutPage.COMPONENT -> copy(component = value.trim())
    else -> this
}
