package dev.pinaki.homedeck

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable

internal fun execute(activity: ComponentActivity, effect: LauncherEffect): String? =
    with(activity) {
        when (effect) {
            is LauncherEffect.LaunchApp -> launch(effect.app)
            is LauncherEffect.ExecuteAction -> NativeActionExecutor(this).execute(effect.action)
            is LauncherEffect.ExecuteShortcut -> executeShortcut(effect.shortcut)
            is LauncherEffect.SaveShortcut -> null
            is LauncherEffect.UninstallApp -> openPackage(Intent.ACTION_DELETE, effect.packageName)
            is LauncherEffect.OpenAppSettings -> openPackage(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, effect.packageName)
        }
    }

private fun ComponentActivity.openPackage(action: String, packageName: String): String? = try {
    startActivity(Intent(action, Uri.parse("package:$packageName")))
    null
} catch (_: Exception) {
    "cannot open package: $packageName"
}

interface ActionExecutor {
    fun execute(action: LauncherAction): String?
}

class NativeActionExecutor(private val activity: ComponentActivity) : ActionExecutor {
    override fun execute(action: LauncherAction): String? {
        return try {
            if (action.name != DEFAULT_APP_ACTION) error("unsupported action")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roles = activity.getSystemService(RoleManager::class.java)
                if (roles.isRoleHeld(RoleManager.ROLE_HOME)) return null
                activity.startActivityForResult(
                    roles.createRequestRoleIntent(RoleManager.ROLE_HOME), HOME_ROLE_REQUEST,
                )
            } else {
                val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                val currentHome = activity.packageManager.resolveActivity(
                    home, PackageManager.MATCH_DEFAULT_ONLY,
                )
                if (currentHome?.activityInfo?.packageName == activity.packageName) return null
                activity.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            }
            null
        } catch (_: Exception) {
            "cannot launch: ${action.name}"
        }
    }

    private companion object {
        const val DEFAULT_APP_ACTION = "Make HomeDeck the default app"
        const val HOME_ROLE_REQUEST = 1
    }
}

private fun ComponentActivity.launch(app: LauncherApp): String? = try {
    startActivity(Intent.makeMainActivity(app.component))
    null
} catch (_: Exception) {
    "cannot launch: ${app.label}"
}

private fun ComponentActivity.executeShortcut(shortcut: Shortcut): String? = try {
    startActivity(shortcut.toIntent())
    null
} catch (_: Exception) {
    "cannot launch: ${shortcut.name}"
}
