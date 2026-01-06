package com.Javsek

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class JavsekPlugin : Plugin() {
    override fun load(context: Context) {
        Javsek.context = context
        registerMainAPI(Javsek())
    }
}

