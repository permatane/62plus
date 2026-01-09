package com.Podjav

import android.util.Base64
import com.lagradost.api.Log
//import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Podjav : MainAPI() {
    override var mainUrl              = "https://podjav.tv"
    override var name                 = "POD JAV"
    override val hasMainPage          = true
    override var lang                 = "id"  // Focused on Indonesian subtitles
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "/movies/" to "All Movies",
        "/genre/abuse/" to "Abuse",
        "/genre/big-tits/" to "Big Tits",
        "/genre/bride/" to "Bride",
        "/genre/creampie/" to "Creampie",
        "/genre/cuckold/" to "Cuckold",
        "/genre/step-mother/" to "Step Mother"
        // Add more genres if needed
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) "$mainUrl${request.data}" else "$mainUrl${request.data}page/$page/"
        val document = app.get(url).document
        val responseList = document.select("article, div.item").mapNotNull { it.toSearchResult() }
        val hasNext = document.select(".pagination .next").isNotEmpty()
        return newHomePageResponse(HomePageList(request.name, responseList, isHorizontalImages = false), hasNext)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleEl = selectFirst("h2.entry-title a, h3 a") ?: return null
        val title = titleEl.text().trim()
        if (title.isEmpty()) return null
        val href = fixUrlNull(titleEl.attr("href")) ?: return null
        val posterUrl = selectFirst("img")?.attr("src")?.let { fixUrlNull(it) }
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val url = if (page == 1) "$mainUrl/?s=$query" else "$mainUrl/page/$page/?s=$query"
        val document = app.get(url).document
        val results = document.select("article, div.item").mapNotNull { it.toSearchResult() }
        val hasNext = document.select(".pagination .next").isNotEmpty()
        return if (results.isEmpty()) null else newSearchResponseList(results, hasNext)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("meta[property=og:title]")
            ?.attr("content")
            ?.trim()
            ?: "Podjav Video"
        val poster = document.selectFirst("meta[property=og:image]")
            ?.attr("content")
        val description = document.selectFirst("meta[property=og:description]")
            ?.attr("content")
        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.tags = document.select("span.tags-links a").eachText()
        }
    }

  override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    val doc = app.get(data).document

    // Mencari direct MP4 src dari tag <video>
    val mp4Url = doc.selectFirst("video.jw-video")?.attr("src")
        ?: doc.selectFirst("video")?.attr("src")
        ?: return false

    // Jika src relatif, jadikan absolute (meskipun dari contoh sudah absolute)
    val fullMp4Url = fixUrlNull(mp4Url) ?: return false

    callback(
        newExtractorLink(
            source = name,
            name = "$name Direct Stream",
            url = fullMp4Url
        )
    )
    return true
}
}

