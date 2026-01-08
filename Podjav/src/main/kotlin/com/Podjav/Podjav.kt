package com.Podjav

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink

class Podjav : MainAPI() {
    override var mainUrl              = "https://podjav.tv"
    override var name                 = "Podjav"
    override val hasMainPage          = true
    override var lang                 = "id"
    override val supportedTypes       = setOf(TvType.NSFW)


    // Mapping kategori sesuai struktur menu di Podjav
    override val mainPage = mainPageOf(
        "movies" to "Terbaru",
        "genre/big-tits" to "Tobrut",
        "genre/orgasm" to "Orgasme"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) {
            "$mainUrl/${request.data}/"
        } else {
            "$mainUrl/${request.data}/page/$page/"
        }
        
        val document = app.get(url).document
        val home = document.select("article.v-card").mapNotNull {
            it.toSearchResult()
        }
        
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
        val title     = this.selectFirst("h2.entry-title a")?.text()?.trim() ?: return null
        val href      = fixUrl(this.selectFirst("a")?.attr("href") ?: "")
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src") ?: this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..2) {
            val url = "$mainUrl/page/$i/?s=$query"
            val document = app.get(url).document
            val results = document.select("article.v-card").mapNotNull { it.toSearchResult() }

            if (results.isEmpty()) break
            searchResponse.addAll(results)
        }
        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title       = document.selectFirst("h1.entry-title")?.text()?.trim() 
                          ?: document.selectFirst("meta[property=og:title]")?.attr("content") ?: ""
        val poster      = document.selectFirst("meta[property=og:image]")?.attr("content")
        val description = document.selectFirst("div.entry-content p")?.text() 
                          ?: document.selectFirst("meta[property=og:description]")?.attr("content")

        val recommendations = document.select("div.related-posts article").mapNotNull {
            it.toSearchResult()
        }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot      = description
            this.recommendations = recommendations
        }
    }

override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // 1. Mencari direct link dari tag <video src="...">
        val directVideoUrl = document.selectFirst("video.jw-video")?.attr("src") 
            ?: document.selectFirst("video")?.attr("src")

        if (!directVideoUrl.isNullOrEmpty()) {
            callback(
                newExtractorLink(
                    this.name,
                    this.name,
                    fixUrl(directVideoUrl),
                    referer = "$mainUrl/",
                    quality = Qualities.Unknown.value,
                    isM3u8 = directVideoUrl.contains(".m3u8")
                )
            )
        }

        // 2. Fallback: Mencari di dalam iframe jika video tidak ditemukan di root document
        document.select("iframe").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotEmpty()) {
                loadExtractor(fixUrl(src), subtitleCallback, callback)
            }
        }

        return true
    }
}


