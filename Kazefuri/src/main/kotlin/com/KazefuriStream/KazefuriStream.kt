package com.KazefuriStream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SubtitleFile
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class KazefuriStream : MainAPI() {
    override var mainUrl = "https://sv3.kazefuri.cloud"
    override var name = "KazefuriStream"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "" to "Latest Anime",
        "ongoing/" to "Ongoing Anime",
        "complete/" to "Completed Anime",
        "movie/" to "Anime Movies",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.isEmpty()) {
            "$mainUrl/page/$page/"
        } else {
            "$mainUrl/${request.data}page/$page/"
        }
        
        val document = app.get(url).document
        val home = document.select("div.animepost").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("div.title a") ?: return null
        val title = titleElement.text().trim()
        val href = fixUrl(titleElement.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val type = when {
            href.contains("/movie/") -> TvType.Movie
            else -> TvType.Anime
        }

        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        val encodedQuery = query.replace(" ", "%20")
        
        for (i in 1..3) {
            val document = app.get("$mainUrl/page/$i/?s=$encodedQuery").document
            val results = document.select("div.animepost").mapNotNull { it.toSearchResult() }

            if (results.isNotEmpty()) {
                searchResponse.addAll(results)
            } else {
                break
            }
            
            if (results.size < 20) break
        }

        return searchResponse.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        
        // Extract title
        val title = document.selectFirst("h1.title")?.text()?.trim() ?: "Unknown Title"
        
        // Extract poster
        val poster = document.selectFirst("div.thumb img")?.attr("src")?.let { fixUrl(it) }
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")?.let { fixUrl(it) }
        
        // Extract description
        val description = document.selectFirst("div.desc")?.text()?.trim()
        
        // Extract genres
        val genres = document.select("div.genxed a").map { it.text().trim() }
        
        // Check if it's a series or movie
        val episodeList = document.select("div.episodelist ul li")
        
        return if (episodeList.isNotEmpty()) {
            // TV Series
            val episodes = episodeList.mapNotNull { episodeElement ->
                val episodeLink = episodeElement.selectFirst("a")?.attr("href")?.let { fixUrl(it) }
                val episodeNumber = episodeElement.selectFirst("span.epl-num")?.text()?.trim()
                val episodeTitle = episodeElement.selectFirst("span.epl-title")?.text()?.trim()
                
                if (episodeLink != null) {
                    newEpisode(episodeLink) {
                        this.name = episodeTitle ?: "Episode $episodeNumber"
                        this.episode = episodeNumber?.filter { it.isDigit() }?.toIntOrNull()
                        this.season = 1
                    }
                } else null
            }.reversed()
            
            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genres
                this.year = document.selectFirst("span.year")?.text()?.trim()?.toIntOrNull()
            }
        } else {
            // Movie or single episode
            val iframe = document.selectFirst("iframe[src]")?.attr("src")?.let { fixUrl(it) }
            
            newMovieLoadResponse(title, url, TvType.Movie, iframe ?: url) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genres
                this.year = document.selectFirst("span.year")?.text()?.trim()?.toIntOrNull()
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // Check for direct iframe
        val iframe = document.selectFirst("iframe[src]")?.attr("src")?.let { fixUrl(it) }
        
        if (iframe != null) {
            if (iframe.contains(".mp4") || iframe.contains(".m3u8")) {
                // Direct video link
                callback.invoke(
                    newExtractorLink(
                        "Kazefuri Direct",
                        "Kazefuri Direct",
                        url = iframe,
                        ExtractorLinkType.DIRECT
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
                return true
            } else {
                // Load extractor for embedded players
                return loadExtractor(iframe, mainUrl, subtitleCallback, callback)
            }
        }
        
        // Check for multiple server options
        val serverOptions = document.select("select.serv option")
        if (serverOptions.isNotEmpty()) {
            for (option in serverOptions) {
                val serverUrl = option.attr("value").takeIf { it.isNotEmpty() }?.let { fixUrl(it) }
                if (serverUrl != null) {
                    loadExtractor(serverUrl, mainUrl, subtitleCallback, callback)
                }
            }
            return true
        }
        
        // Check for data-value encoded links
        val encodedLinks = document.select("div[data-value]")
        for (link in encodedLinks) {
            val encodedValue = link.attr("data-value")
            try {
                // Try base64 decode
                val decoded = base64Decode(encodedValue)
                if (decoded.contains("http")) {
                    val extractedUrl = Regex("""(https?://[^\s"']+)""").find(decoded)?.value
                    if (extractedUrl != null) {
                        loadExtractor(fixUrl(extractedUrl), mainUrl, subtitleCallback, callback)
                    }
                }
            } catch (e: Exception) {
                // If not base64, try direct URL
                if (encodedValue.startsWith("http")) {
                    loadExtractor(fixUrl(encodedValue), mainUrl, subtitleCallback, callback)
                }
            }
        }
        
        return true
    }
}