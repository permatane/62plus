package com.kazefuri

import android.content.Context
import com.lagradost.cloudstream3.plugin.CloudstreamPlugin
import com.lagradost.cloudstream3.plugin.PluginData

@CloudstreamPlugin
class KazefuriPlugin {

    lateinit var pluginData: PluginData

    fun registerMainAPI(pluginData: PluginData) {
        this.pluginData = pluginData
        pluginData.mainApiList.add(Kazefuri())
    }

    fun load(context: Context) {
        registerMainAPI(pluginData)
    }
}