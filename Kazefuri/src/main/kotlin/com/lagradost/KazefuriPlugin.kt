package com.lagradost

import android.content.Context
import com.lagradost.cloudstream3.plugin.CloudstreamPlugin
import com.lagradost.cloudstream3.plugin.PluginData

@CloudstreamPlugin
class KazefuriPlugin {

    private lateinit var pluginData: PluginData

    fun registerMainAPI(pluginData: PluginData) {
        this.pluginData = pluginData
        pluginData.mainApiList.add(Kazefuri())
    }

    fun load(context: Context) {
        // Kosong untuk provider sederhana
    }
}