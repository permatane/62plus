package com.KazefuriStream

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class KazefuriDirect : ExtractorApi() {
    override var name = "Kazefuri Direct"
    override var mainUrl = "https://sv3.kazefuri.cloud"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer ?: mainUrl)
        
        // Try to find video sources in various formats
        val html = response.text
        
        // Pattern 1: Direct video links
        val videoPatterns = listOf(
            """src\s*=\s*["'](https?://[^"']+\.(?:mp4|m3u8)[^"']*)["']""",
            """file\s*:\s*["'](https?://[^"']+\.(?:mp4|m3u8)[^"']*)["']""",
            """(https?://[^"'\s]+\.(?:mp4|m3u8))"""
        )
        
        for (pattern in videoPatterns) {
            val matches = Regex(pattern).findAll(html)
            matches.forEach { match ->
                val videoUrl = match.groupValues[1].replace("\\/", "/")
                callback.invoke(
                    newExtractorLink(
                        name,
                        "Direct Video",
                        url = videoUrl,
                        ExtractorLinkType.DIRECT
                    ) {
                        this.referer = url
                        this.quality = getQualityFromName(videoUrl)
                    }
                )
            }
        }
        
        // Pattern 2: JSON embedded data
        val jsonPattern = """(\{.*?"sources".*?\})""".toRegex(RegexOption.DOT_MATCHES_ALL)
        jsonPattern.find(html)?.groupValues?.get(1)?.let { jsonString ->
            try {
                val response = app.parseJson<KazefuriVideoResponse>(jsonString)
                response.data?.sources?.forEach { source ->
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
                
                // Subtitle tracks
                response.data?.tracks?.forEach { track ->
                    subtitleCallback.invoke(
                        newSubtitleFile(
                            track.label,
                            track.file
                        )
                    )
                }
            } catch (e: Exception) {
                // Try alternative JSON parsing
                try {
                    val sources = app.parseJson<List<Map<String, Any>>>(jsonString)
                    sources.forEach { source ->
                        val file = source["file"] as? String
                        val label = source["label"] as? String ?: "Unknown"
                        if (file != null && file.isNotEmpty()) {
                            callback.invoke(
                                newExtractorLink(
                                    name,
                                    label,
                                    url = file,
                                    ExtractorLinkType.DIRECT
                                ) {
                                    this.referer = mainUrl
                                    this.quality = getQualityFromName(label)
                                }
                            )
                        }
                    }
                } catch (e2: Exception) {
                    // JSON parsing failed, continue
                }
            }
        }
        
        // Pattern 3: Data-value attributes
        val doc = Jsoup.parse(html)
        doc.select("[data-value]").forEach { element ->
            val dataValue = element.attr("data-value")
            try {
                val decoded = base64Decode(dataValue)
                val iframeMatch = Regex("""src=["'](https?://[^"']+)["']""").find(decoded)
                iframeMatch?.groupValues?.get(1)?.let { iframeUrl ->
                    loadExtractor(iframeUrl, mainUrl, subtitleCallback, callback)
                }
            } catch (e: Exception) {
                // Not base64, try direct URL
                if (dataValue.startsWith("http")) {
                    loadExtractor(dataValue, mainUrl, subtitleCallback, callback)
                }
            }
        }
    }
}

class KazefuriEmbed : ExtractorApi() {
    override var name = "Kazefuri Embed"
    override var mainUrl = "https://sv3.kazefuri.cloud"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Extract from embedded iframe
        val response = app.get(url, referer = referer ?: mainUrl)
        val doc = response.document
        
        // Try to extract player script
        val script = doc.select("script:containsData(player)").html()
        
        // Common player configurations
        val patterns = listOf(
            """sources\s*:\s*\[([^\]]+)\]""",
            """file\s*:\s*["']([^"']+)["']""",
            """(https?://[^"']+\.(?:mp4|m3u8)[^"']*)"""
        )
        
        for (pattern in patterns) {
            val matches = Regex(pattern).findAll(script)
            matches.forEach { match ->
                val potentialUrl = match.groupValues[1].replace("\\/", "/")
                if (potentialUrl.startsWith("http")) {
                    if (potentialUrl.contains(".m3u8")) {
                        M3u8Helper.generateM3u8(
                            name,
                            potentialUrl,
                            mainUrl
                        ).forEach(callback)
                    } else {
                        callback.invoke(
                            newExtractorLink(
                                name,
                                "Embedded Video",
                                url = potentialUrl,
                                ExtractorLinkType.DIRECT
                            ) {
                                this.referer = url
                                this.quality = getQualityFromName(potentialUrl)
                            }
                        )
                    }
                }
            }
        }
    }
}