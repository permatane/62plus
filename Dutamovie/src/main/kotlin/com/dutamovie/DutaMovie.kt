package com.dutamovie

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Dutamovie : MainAPI() {
    override var mainUrl = "https://javsek.net"
    override var name = "JavSek"
    override val hasMainPage = true
    override var lang = "id" // Konten biasanya Jepang, tapi interface sering dicari user Indo
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage = mainPageOf(
        "" to "Terbaru",
        "category/uncensored/page/%d/" to "Uncensored",
        "category/censored/page/%d/" to "Censored",
        "category/jav-sub-indo/page/%d/" to "Sub Indo",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) "$mainUrl/${request.data.replace("/page/%d/", "")}" 
                  else "$mainUrl/${request.data.format(page)}"
        
        val document = app.get(url).document
        val home = document.select("article.item-list").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2.entry-title a")?.text() ?: return null
        val href = this.selectFirst("h2.entry-title a")?.attr("href") ?: return null
        val posterUrl = this.selectFirst("img")?.attr("src")

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article.item-list").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: ""
        val poster = document.selectFirst("div.entry-content img")?.attr("src")
        val description = document.selectFirst("div.entry-content p")?.text()
        
        // Mengambil tag/genre
        val tags = document.select("span.tags-links a").eachText()

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // Mencari semua iframe di dalam konten (biasanya tempat player berada)
        document.select("div.entry-content iframe").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotEmpty()) {
                // Menggunakan loadExtractor untuk memproses link otomatis (Doodstream, Vidguard, dll)
                loadExtractor(src, data, subtitleCallback, callback)
            }
        }

        // Mencari link download yang mungkin bisa diekstrak
        document.select("a[href*='dood'], a[href*='filelions'], a[href*='streamwish']").forEach { link ->
            loadExtractor(link.attr("href"), data, subtitleCallback, callback)
        }

        return true
    }
}
