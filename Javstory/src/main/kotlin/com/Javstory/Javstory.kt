package com.Javstory

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Javstory : MainAPI() {
    override var mainUrl = "https://javstory1.com"
    override var name = "JavStory"
    override val supportedTypes = setOf(TvType.NSFW)
    override var lang = "id"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "/category/indosub/" to "Sub Indonesia",
        "/category/engsub/" to "Sub English"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) "$mainUrl${request.data}" else "$mainUrl${request.data}page/$page/"
        val document = app.get(url).document
        val home = document.select("article").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst(".entry-title a")?.text() ?: return null
        val href = fixUrl(this.selectFirst(".entry-title a")?.attr("href") ?: return null)
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst(".entry-title")?.text() ?: return null
        val poster = fixUrlNull(document.selectFirst(".content-thumb img")?.attr("src"))
        val description = document.selectFirst(".entry-content p")?.text()

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // 1. Ekstraksi dari Iframe Langsung
        document.select("iframe.player-iframe").forEach { iframe ->
            val src = fixUrl(iframe.attr("src"))
            loadExtractor(src, data, subtitleCallback, callback)
        }

        // 2. Ekstraksi dari Button onclick (loadStream)
        document.select("button.server-button").forEach { button ->
            val onClick = button.attr("onclick")
            if (onClick.contains("loadStream")) {
                val matches = """'([^']*)'""".toRegex().findAll(onClick).map { it.groupValues[1] }.toList()
                if (matches.size >= 2) {
                    val baseUrl = matches[0]
                    val id = matches[1]
                    val fullUrl = if (baseUrl.endsWith("/")) "$baseUrl$id" else "$baseUrl/$id"
                    loadExtractor(fullUrl, data, subtitleCallback, callback)
                }
            }
        }

        // 3. Ekstraksi dari Script Obfuscated (rndmzr = reverse)
        document.select("script").forEach { script ->
            val scriptData = script.data()
            if (scriptData.contains("rndmzr")) {
                // Regex untuk mencari ID di dalam tanda kutip sebelum .rndmzr()
                val regex = """"(.*?)".rndmzr\(\)""".toRegex()
                regex.findAll(scriptData).forEach { match ->
                    val obfuscatedId = match.groupValues[1]
                    val realId = obfuscatedId.reversed() // Logika rndmzr di JS hanyalah reverse

                    // Cek jenis server berdasarkan konten script
                    when {
                        scriptData.contains("streamtape") -> {
                            loadExtractor("https://streamtape.com/e/$realId", data, subtitleCallback, callback)
                        }
                        scriptData.contains("sbembed") || scriptData.contains("sbvideo") -> {
                            loadExtractor("https://sbembed2.com/e/$realId.html", data, subtitleCallback, callback)
                        }
                    }
                }
            }
        }

        return true
    }
}
