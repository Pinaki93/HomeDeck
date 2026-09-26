package dev.pinaki.homedeck

import android.content.res.AssetManager
import org.json.JSONArray

data class LauncherAction(val name: String)

internal class ActionStore(private val read: () -> String) {
    constructor(assets: AssetManager) : this({
        assets.open("actions.json").bufferedReader().use { it.readText() }
    })

    fun load(): List<LauncherAction> = JSONArray(read()).let { json ->
        List(json.length()) { LauncherAction(json.getJSONObject(it).getString("name")) }
    }
}
