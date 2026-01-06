package com.Javsek

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Javsek : MainAPI() {
    override var mainUrl = "https://javsek.net"
    override var name = "JavSek"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage = mainPageOf(
        "page/%d/" to "Terbaru",
        "category/indo-sub/page/%d/" to "Sub Indo",
        "category/english-sub/page/%d/" to "Sub English",
        "category/jav-reducing-mosaic-decensored-streaming-and-download/page/%d/" to "Reducing Mosaic",
        "category/amateur/page/%d/" to "Amateur",
        "category/chinese-porn-streaming/page/%d/" to "China",
        
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) "$mainUrl/${request.data.replace("page/%d/", "")}" 
                  else "$mainUrl/${request.data.format(page)}"
        
        val document = app.get(url).document
        // Menggunakan selektor article umum agar lebih stabil
        val home = document.select("article").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2.entry-title a")?.text() ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: "")
        // Perbaikan: Ambil data-src untuk menangani lazy load gambar
        val img = this.selectFirst("img")
        val posterUrl = img?.attr("data-src")?.ifBlank { img.attr("src") }

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: ""
        val poster = document.selectFirst("div.entry-content img")?.attr("src")
        
        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = document.selectFirst("div.entry-content p")?.text()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        // Mencari semua iframe player
        document.select("iframe").forEach { 
            val src = it.attr("src")
            if (src.isNotEmpty()) loadExtractor(src, data, subtitleCallback, callback)
        }
        return true
    }
}
