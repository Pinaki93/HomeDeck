package dev.pinaki.homedeck

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel

internal object Dependencies {
    @Composable
    fun launcherViewModel(): LauncherViewModel {
        val context = LocalContext.current.applicationContext
        return viewModel {
            LauncherViewModel(
                createSavedStateHandle(),
                ShortcutStore(context.filesDir.resolve("shortcuts.json")),
                ActionStore(context.assets),
                PackageStoreImpl(context),
            )
        }
    }
}
