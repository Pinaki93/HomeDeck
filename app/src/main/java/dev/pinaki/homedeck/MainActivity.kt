package dev.pinaki.homedeck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.pinaki.homedeck.ui.theme.HomeDeckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val launcher = Dependencies.launcherViewModel()
            DisposableEffect(launcher) {
                val observer = object : DefaultLifecycleObserver {
                    override fun onResume(owner: LifecycleOwner) {
                        launcher.updateSources(apps = loadApps())
                    }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            HomeDeckTheme {

                val activity = LocalActivity.current as ComponentActivity
                LauncherScreen(
                    viewModel = launcher,
                    execute = { effect ->
                        execute(activity, effect)
                    }
                )
            }
        }
    }
}
