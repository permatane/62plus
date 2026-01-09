package com.Podjav

import android.util.Base64
import com.lagradost.api.Log
//import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Podjav : MainAPI() {
    override var mainUrl              = "https://podjav.tv"
    override var name                 = "PodJAV"
    override val hasMainPage          = true
    override var lang                 = "id"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.NSFW)

    override val mainPage = mainPageOf(
        "/movies/" to "Update Terbaru",
        "/genre/orgasm/" to "Orgasme",
        "/genre/big-tits/" to "Tobrut",
        "/genre/creampie/" to "Krim Pejuh",
        "/genre/abuse/" to "Pemaksaan",
        "/genre/model/" to "Model Cantik",
        "/genre/mature-woman/" to "Wanita Dewasa",
        "/genre/step-mother/" to "Ibu Angkat",
        "/genre/nurse/" to "Perawat",
        "/genre/secretary/" to "Sekretaris",
        "/genre/female-teacher/" to "Guru",
        "/genre/swingers/" to "Tukar Pasangan",
        "/genre/solowork/" to "Solowork",
        "/genre/cuckold/" to "Istri Menyimpang"

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
            this.tags = document.select("a[rel=tag]").eachText()
        }
    }

override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    val doc = app.get(data).document

    // Coba ambil dari <video src=...>
    val mp4Url = doc.selectFirst("video.jw-video")?.attr("src")
        ?: doc.selectFirst("video")?.attr("src")

    if (!mp4Url.isNullOrEmpty()) {
        val fullUrl = fixUrlNull(mp4Url) ?: mp4Url
        callback(
            newExtractorLink(
                this.name,
                "Direct MP4 (from HTML)",
                fullUrl,
                ExtractorLinkType.VIDEO
            ) {
                this.referer = data
                this.quality = Qualities.P1080.value
            }
        )
        return true
    }

    // Jika gagal, fallback ke pola URL
    val javCodeMatch = Regex("/movies/([a-zA-Z0-9-]+)-sub-indo-").find(data)
    val javCode = javCodeMatch?.groupValues?.get(1)?.uppercase() ?: return false

    val fallbackUrl = "https://vod.podjav.tv/$javCode/$javCode.mp4"

    callback(
        newExtractorLink(
            this.name,
            "Direct MP4 (fallback pattern)",
            fallbackUrl,
            ExtractorLinkType.VIDEO
        ) {
            this.referer = data
            this.quality = Qualities.P1080.value
        }
    )

    return true
}
}




