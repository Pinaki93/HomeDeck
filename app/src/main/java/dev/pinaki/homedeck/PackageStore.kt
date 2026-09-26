package dev.pinaki.homedeck

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class LauncherApp(val label: String, val component: ComponentName)
data class LauncherPackage(val label: String, val packageName: String)

internal fun Context.loadApps(): List<LauncherApp> {
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

internal fun interface PackageStore {
    fun load(shortcut: Shortcut): Result<List<LauncherPackage>>
}

internal class PackageStoreImpl(private val context: Context) : PackageStore {
    override fun load(shortcut: Shortcut) = runCatching {
        val intent = shortcut.toIntent().apply { component = null; setPackage(null) }
        val resolved = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull {
                runCatching {
                    LauncherPackage(
                        it.activityInfo.applicationInfo.loadLabel(context.packageManager).toString(),
                        it.activityInfo.packageName,
                    )
                }.getOrNull()
            }
        filterAndSortApps(
            addInstalledBrowsers(
                shortcut,
                resolved,
                context.loadApps().map { LauncherPackage(it.label, it.component.packageName) },
            ),
            "",
            label = LauncherPackage::label,
        )
    }
}

private val browserPackages = setOf(
    "com.android.chrome", "org.mozilla.firefox", "org.mozilla.focus", "com.microsoft.emmx",
    "com.brave.browser", "com.opera.browser", "com.opera.mini.native",
    "com.sec.android.app.sbrowser", "com.duckduckgo.mobile.android", "com.vivaldi.browser",
    "com.kiwibrowser.browser", "org.torproject.torbrowser", "company.thebrowser.arc",
)

internal fun addInstalledBrowsers(
    shortcut: Shortcut, resolved: List<LauncherPackage>, installed: List<LauncherPackage>,
) = (resolved + if (shortcut.data.startsWith("http://") || shortcut.data.startsWith("https://")) {
    installed.filter { it.packageName in browserPackages }
} else emptyList()).distinctBy(LauncherPackage::packageName)
