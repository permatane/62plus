package com.kazefuri

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.nicehttp.NiceResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import kotlin.text.Regex

class KazefuriStream : MainAPI() {
    override var mainUrl = "https://sv3.kazefuri.cloud"
    override var name = "Kazefuri"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    companion object {
        fun getType(t: String): TvType {
            return when {
                t.contains("OVA", true) || t.contains("Special") -> TvType.OVA
                t.contains("Movie", true) -> TvType.AnimeMovie
                else -> TvType.Anime
            }
        }

        fun getStatus(t: String): ShowStatus {
            return when {
                t.contains("Completed", true) -> ShowStatus.Completed
                t.contains("Ongoing", true) -> ShowStatus.Ongoing
                t.contains("Upcoming", true) -> ShowStatus.ComingSoon
                else -> ShowStatus.Completed
            }
        }

        fun cleanTitle(title: String): String {
            return title
                .replace("Subtitle Indonesia", "")
                .replace("Streaming", "")
                .replace("Anime", "")
                .replace("[Batch]", "")
                .trim()
        }
    }

    private suspend fun request(url: String, ref: String? = null): NiceResponse {
        return app.get(
            url,
            headers = mapOf(
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
                "Cache-Control" to "no-cache",
                "Pragma" to "no-cache",
                "Sec-Fetch-Dest" to "document",
                "Sec-Fetch-Mode" to "navigate",
                "Sec-Fetch-Site" to "same-origin",
                "Upgrade-Insecure-Requests" to "1"
            ),
            cookies = mapOf("_as_ipin_ct" to "ID"),
            referer = ref ?: mainUrl
        )
    }

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Latest Updates",
        "$mainUrl/anime/ongoing/" to "Ongoing Anime",
        "$mainUrl/anime/complete/" to "Completed Anime",
        "$mainUrl/anime/movie/" to "Anime Movies",
        "$mainUrl/genre/action/" to "Action",
        "$mainUrl/genre/adventure/" to "Adventure",
        "$mainUrl/genre/comedy/" to "Comedy",
        "$mainUrl/genre/drama/" to "Drama",
        "$mainUrl/genre/fantasy/" to "Fantasy",
        "$mainUrl/genre/horror/" to "Horror",
        "$mainUrl/genre/isekai/" to "Isekai",
        "$mainUrl/genre/magic/" to "Magic",
        "$mainUrl/genre/martial-arts/" to "Martial Arts",
        "$mainUrl/genre/mystery/" to "Mystery",
        "$mainUrl/genre/romance/" to "Romance",
        "$mainUrl/genre/school/" to "School",
        "$mainUrl/genre/sci-fi/" to "Sci-Fi",
        "$mainUrl/genre/shoujo/" to "Shoujo",
        "$mainUrl/genre/shounen/" to "Shounen",
        "$mainUrl/genre/slice-of-life/" to "Slice of Life",
        "$mainUrl/genre/supernatural/" to "Supernatural"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1 && request.data == "$mainUrl/") {
            request.data
        } else {
            "${request.data}page/$page/"
        }
        
        val document = request(url).document
        val home = document.select("article.anime, div.animepost, div.list-item").mapNotNull { 
            it.toSearchResult() 
        }
        
        return newHomePageResponse(request.name, home)
    }

    private fun getProperAnimeLink(uri: String): String {
        return if (uri.contains("/anime/")) {
            uri
        } else {
            var title = uri.substringAfter("$mainUrl/")
            title = when {
                title.contains("-episode-") -> 
                    title.substringBeforeLast("-episode-")
                        .replace("-episode-", "-")
                title.contains("-movie-") -> 
                    title.substringBefore("-movie-")
                title.endsWith("/") -> 
                    title.removeSuffix("/")
                else -> title
            }
            
            // Clean title for anime page URL
            val cleanTitle = title
                .replace(Regex("-\\d+$"), "") // Remove trailing numbers
                .replace("-episode-", "-")
                .replace("-ova-", "-")
                .replace("-special-", "-")
            
            "$mainUrl/anime/$cleanTitle/"
        }
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        try {
            val anchor = this.selectFirst("a") ?: return null
            val href = fixUrlNull(anchor.attr("href")) ?: return null
            val properUrl = getProperAnimeLink(href)
            
            val title = this.select("h2, h3, .title, .entry-title")
                .firstOrNull()
                ?.text()
                ?.trim()
                ?: anchor.attr("title")
                ?.trim()
                ?: anchor.text().trim()
            
            val cleanTitle = cleanTitle(title)
            
            // Extract poster/image
            val posterElement = this.selectFirst("img")
            val posterUrl = fixUrl(posterElement?.attr("src") ?: posterElement?.attr("data-src") ?: "")
            
            // Extract episode number if available
            val epNum = this.selectFirst(".episode, .eps, .ep")
                ?.text()
                ?.let {
                    Regex("(?:Episode|Eps?|Ep)\\s*:?\\s*(\\d+)")
                        .find(it)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                }
            
            // Determine type based on URL or title
            val type = when {
                href.contains("/movie/") || title.contains("Movie", true) -> TvType.AnimeMovie
                href.contains("/ova/") || title.contains("OVA", true) || title.contains("Special", true) -> TvType.OVA
                else -> TvType.Anime
            }
            
            return newAnimeSearchResponse(cleanTitle, properUrl, type) {
                this.posterUrl = posterUrl
                addSub(epNum)
            }
        } catch (e: Exception) {
            return null
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = query.replace(" ", "+")
        val link = "$mainUrl/?s=$encodedQuery"
        val document = request(link).document

        return document.select("article, div.animepost, div.search-item")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = request(url).document

        // Extract main title
        val rawTitle = document.selectFirst("h1.entry-title, h1.title, h1")
            ?.text()
            ?.trim()
            ?: "Unknown Title"
        
        val title = cleanTitle(rawTitle)
        
        // Extract poster image
        val poster = document.selectFirst("div.thumb img, div.poster img, img.attachment-post-thumbnail")
            ?.attr("src")
            ?.let { fixUrl(it) }
            ?: document.selectFirst("meta[property=og:image]")
                ?.attr("content")
                ?.let { fixUrl(it) }
        
        // Extract type from page
        val typeText = document.select("td:contains(Type), th:contains(Type) + td, span.type")
            .firstOrNull()
            ?.text()
            ?.trim()
            ?: ""
        
        val type = getType(typeText)
        
        // Extract year
        val year = document.select("td:contains(Released), th:contains(Released) + td, span.year, td:contains(Year) + td")
            .firstOrNull()
            ?.text()
            ?.trim()
            ?.let { 
                Regex("(\\d{4})").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }
        
        // Extract status
        val statusText = document.select("td:contains(Status), th:contains(Status) + td, span.status")
            .firstOrNull()
            ?.text()
            ?.trim()
            ?: ""
        
        val showStatus = getStatus(statusText)
        
        // Extract episode list
        val episodes = document.select("ul.episodelist li, div.episode-list li, li.episode-item")
            .mapNotNull { episodeElement ->
                val anchor = episodeElement.selectFirst("a") ?: return@mapNotNull null
                val episodeLink = fixUrl(anchor.attr("href"))
                val episodeText = anchor.text().trim()
                
                // Extract episode number
                val episodeNumber = when {
                    episodeText.contains("Episode") -> 
                        Regex("Episode\\s*(\\d+)").find(episodeText)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    episodeText.contains("EP") -> 
                        Regex("EP\\s*(\\d+)").find(episodeText)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    else -> 
                        Regex("(\\d+)").find(episodeText)?.groupValues?.getOrNull(1)?.toIntOrNull()
                }
                
                // Extract episode title
                val episodeName = episodeText
                    .replace("Episode $episodeNumber", "")
                    .replace("EP$episodeNumber", "")
                    .replace(Regex("^[\\s\\-:]+"), "")
                    .trim()
                    .ifEmpty { "Episode $episodeNumber" }
                
                newEpisode(episodeLink) {
                    this.name = episodeName
                    this.episode = episodeNumber
                    this.season = 1
                }
            }
            .reversed()
        
        // Extract plot/description
        val plot = document.select("div.desc, div.synopsis, div.entry-content > p, div.description")
            .firstOrNull()
            ?.text()
            ?.trim()
            ?.replace("\\s+".toRegex(), " ")
        
        // Extract genres/tags
        val tags = document.select("div.genres a, td:contains(Genre) + td a, span.genre a")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
        
        // Try to get tracker info (MAL/AniList)
        val tracker = APIHolder.getTracker(listOf(title), TrackerType.getTypes(type), year, true)
        
        return newAnimeLoadResponse(title, url, type) {
            this.posterUrl = tracker?.image ?: poster
            this.backgroundPosterUrl = tracker?.cover
            this.year = year
            this.showStatus = showStatus
            this.plot = plot
            this.tags = tags
            
            if (episodes.isNotEmpty()) {
                addEpisodes(DubStatus.Subbed, episodes)
            } else {
                // If no episode list, treat as movie or single episode
                addEpisodes(DubStatus.Subbed, listOf(newEpisode(url) {
                    this.name = "Play"
                    this.episode = 1
                }))
            }
            
            addMalId(tracker?.malId)
            addAniListId(tracker?.aniId?.toIntOrNull())
            
            // Additional metadata
            this.score = document.selectFirst("span.score, div.rating")
                ?.text()
                ?.trim()
                ?.toFloatOrNull()
            
            this.recommendations = document.select("div.related-anime a, div.recommendations a")
                .mapNotNull { 
                    val recTitle = it.text().trim()
                    val recUrl = fixUrl(it.attr("href"))
                    if (recTitle.isNotEmpty() && recUrl.isNotEmpty()) {
                        SearchResponse(
                            recTitle,
                            recUrl,
                            this.type,
                            null,
                            null,
                            null,
                            null,
                            null
                        )
                    } else null
                }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = request(data).document
        
        // Multiple player sources approach
        val playerSources = mutableListOf<String>()
        
        // Source 1: Direct iframe
        document.select("iframe[src]").forEach { iframe ->
            val src = iframe.attr("src").takeIf { it.isNotEmpty() }?.let { fixUrl(it) }
            src?.let { playerSources.add(it) }
        }
        
        // Source 2: Embedded video players
        document.select("video source[src], audio source[src]").forEach { source ->
            val src = source.attr("src").takeIf { it.isNotEmpty() }?.let { fixUrl(it) }
            src?.let { playerSources.add(it) }
        }
        
        // Source 3: Data attributes with encoded URLs
        document.select("[data-src], [data-em], [data-value]").forEach { element ->
            val dataSrc = element.attr("data-src").takeIf { it.isNotEmpty() }
                ?: element.attr("data-em").takeIf { it.isNotEmpty() }
                ?: element.attr("data-value").takeIf { it.isNotEmpty() }
            
            if (dataSrc != null) {
                try {
                    // Try to decode if it's base64
                    val decoded = base64Decode(dataSrc)
                    val iframeSrc = Regex("""src=["'](https?://[^"']+)["']""").find(decoded)
                        ?.groupValues?.getOrNull(1)
                    iframeSrc?.let { playerSources.add(fixUrl(it)) }
                } catch (e: Exception) {
                    // If not base64, check if it's a direct URL
                    if (dataSrc.startsWith("http")) {
                        playerSources.add(fixUrl(dataSrc))
                    }
                }
            }
        }
        
        // Source 4: Server selection options
        document.select("select.server-select option, .mirror option").forEach { option ->
            val value = option.attr("value").takeIf { it.isNotEmpty() }
            if (value != null && value.startsWith("http")) {
                playerSources.add(fixUrl(value))
            }
        }
        
        // Load all found sources
        playerSources.distinct().forEach { sourceUrl ->
            safeApiCall {
                loadKazefuriExtractor(sourceUrl, data, subtitleCallback, callback)
            }
        }
        
        return playerSources.isNotEmpty()
    }

    private suspend fun loadKazefuriExtractor(
        url: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        when {
            // Direct video files
            url.contains(".mp4") || url.contains(".m3u8") || url.contains(".mkv") -> {
                val quality = getQualityFromUrl(url)
                callback.invoke(
                    newExtractorLink(
                        name,
                        "Kazefuri Direct",
                        url,
                        ExtractorLinkType.DIRECT
                    ) {
                        this.referer = referer
                        this.quality = quality
                    }
                )
            }
            
            // Embedded players that need extraction
            url.contains("streamtape") -> loadExtractor(url, referer, subtitleCallback, callback)
            url.contains("dood") -> loadExtractor(url, referer, subtitleCallback, callback)
            url.contains("mixdrop") -> loadExtractor(url, referer, subtitleCallback, callback)
            url.contains("filesim") -> loadExtractor(url, referer, subtitleCallback, callback)
            url.contains("streamwish") -> loadExtractor(url, referer, subtitleCallback, callback)
            url.contains("vidcloud") -> loadExtractor(url, referer, subtitleCallback, callback)
            url.contains("upstream") -> loadExtractor(url, referer, subtitleCallback, callback)
            
            // Kazefuri's custom player
            url.contains(mainUrl) -> {
                val playerDoc = request(url, referer).document
                
                // Try to extract m3u8 or mp4 from player page
                val videoSource = playerDoc.select("video source[src], script:containsData(m3u8)")
                    .firstOrNull()
                    ?.let { 
                        if (it.tagName() == "source") {
                            it.attr("src")
                        } else {
                            // Extract from script
                            Regex("""(https?://[^\s"']+\.(?:m3u8|mp4)[^\s"']*)""")
                                .find(it.data())
                                ?.groupValues
                                ?.getOrNull(1)
                        }
                    }
                    ?.let { fixUrl(it) }
                
                if (videoSource != null) {
                    val quality = getQualityFromUrl(videoSource)
                    callback.invoke(
                        newExtractorLink(
                            name,
                            "Kazefuri Player",
                            videoSource,
                            if (videoSource.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.DIRECT
                        ) {
                            this.referer = url
                            this.quality = quality
                        }
                    )
                }
            }
            
            // Fallback to generic extractor
            else -> loadExtractor(url, referer, subtitleCallback, callback)
        }
    }

    private fun getQualityFromUrl(url: String): Int {
        return when {
            url.contains("1080") -> Qualities.P1080.value
            url.contains("720") -> Qualities.P720.value
            url.contains("480") -> Qualities.P480.value
            url.contains("360") -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
    }

    private suspend fun loadFixedExtractor(
        url: String,
        quality: Int,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        loadExtractor(url, referer, subtitleCallback) { link ->
            CoroutineScope(Dispatchers.IO).launch {
                callback.invoke(
                    newExtractorLink(
                        name,
                        name,
                        link.url,
                    ) {
                        this.referer = link.referer
                        this.type = link.type
                        this.extractorData = link.extractorData
                        this.headers = link.headers
                        this.quality = quality
                    }
                )
            }
        }
    }
}