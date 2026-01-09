ackage com.Jasek

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.StreamTape

@CloudstreamPlugin
class JavsekPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Javsek())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(Stbturbo())
        registerExtractorAPI(Turbovid())
        registerExtractorAPI(MyCloudZ())
        registerExtractorAPI(Cloudwish())
    }
}
