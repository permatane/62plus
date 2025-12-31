package com.kazefuri

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class KazefuriExtractor : ExtractorApi() {
    override var name = "Kazefuri Extractor"
    override var mainUrl = "https://sv3.kazefuri.cloud"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer ?: mainUrl)
        val document = response.document
        
        // Pattern matching for various player types
        val html = response.text
        
        // Method 1: Direct video links in JSON
        val jsonPattern = """(\{.*?"sources".*?\})""".toRegex(RegexOption.DOT_MATCHES_ALL)
        jsonPattern.find(html)?.groupValues?.get(1)?.let { jsonStr ->
            try {
                // Try to parse as player JSON
                val sources = extractSourcesFromJson(jsonStr)
                sources.forEach { source ->
                    if (source.file.isNotEmpty()) {
                        if (source.file.contains(".m3u8")) {
                            M3u8Helper.generateM3u8(
                                name,
                                source.file,
                                mainUrl,
                                headers = mapOf("Referer" to mainUrl)
                            ).forEach(callback)
                        } else {
                            callback.invoke(
                                newExtractorLink(
                                    name,
                                    source.label,
                                    url = source.file,
                                    ExtractorLinkType.DIRECT
                                ) {
                                    this.referer = mainUrl
                                    this.quality = getQualityFromName(source.label)
                                }
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Try alternative JSON parsing
            }
        }
        
        // Method 2: Data-em attribute (common in anime sites)
        document.select("[data-em]").forEach { element ->
            val dataEm = element.attr("data-em")
            try {
                val decoded = base64Decode(dataEm)
                val iframeDoc = Jsoup.parse(decoded)
                val iframeSrc = iframeDoc.selectFirst("iframe")?.attr("src")
                if (iframeSrc != null && iframeSrc.isNotEmpty()) {
                    loadExtractor(fixUrl(iframeSrc), mainUrl, subtitleCallback, callback)
                }
            } catch (e: Exception) {
                // Handle decode error
            }
        }
        
        // Method 3: JavaScript unpacked sources
        val scriptTags = document.select("script:containsData(eval)")
        for (script in scriptTags) {
            val packed = script.data()
            val unpacked = JsUnpacker(packed).unpack()
            unpacked?.let {
                // Extract m3u8 URLs
                val m3u8Matches = Regex("""(https?://[^\s"']+\.m3u8[^\s"']*)""").findAll(it)
                m3u8Matches.forEach { match ->
                    val m3u8Url = match.value
                    M3u8Helper.generateM3u8(name, m3u8Url, mainUrl).forEach(callback)
                }
                
                // Extract subtitle URLs
                val vttMatches = Regex("""(https?://[^\s"']+\.vtt[^\s"']*)""").findAll(it)
                vttMatches.forEach { match ->
                    val vttUrl = match.value
                    subtitleCallback.invoke(newSubtitleFile("Subtitles", vttUrl))
                }
            }
        }
    }
    
    private fun extractSourcesFromJson(jsonStr: String): List<VideoSource> {
        // Simple JSON extraction for video sources
        val sources = mutableListOf<VideoSource>()
        
        // Pattern for source objects
        val sourcePattern = """\{"file":"([^"]+)","label":"([^"]+)","type":"([^"]+)"(?:,"default":(true|false))?\}""".toRegex()
        val matches = sourcePattern.findAll(jsonStr)
        
        matches.forEach { match ->
            val file = match.groupValues[1].replace("\\/", "/")
            val label = match.groupValues[2]
            val type = match.groupValues[3]
            val isDefault = match.groupValues.getOrNull(4) == "true"
            
            sources.add(VideoSource(file, label, type, isDefault))
        }
        
        return sources
    }
}

data class VideoSource(
    val file: String,
    val label: String,
    val type: String,
    val isDefault: Boolean = false
)