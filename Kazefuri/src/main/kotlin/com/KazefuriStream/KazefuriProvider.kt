package com.KazefuriStream

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class KazefuriProvider : BasePlugin() {
    override fun load() {
        // Register main API
        registerMainAPI(KazefuriStream())
        
        // Register extractors
        registerExtractorAPI(KazefuriDirect())
        registerExtractorAPI(KazefuriEmbed())
        
        // Register common external extractors
        registerExtractorAPI(StreamSB())
        registerExtractorAPI(StreamWishExtractor())
        registerExtractorAPI(Filesim())
        registerExtractorAPI(Streamtape())
        registerExtractorAPI(Mixdrop())
        registerExtractorAPI(DoodStream())
        registerExtractorAPI(Upstream())
        registerExtractorAPI(VidCloud9())
        registerExtractorAPI(SuperStream())
    }
}