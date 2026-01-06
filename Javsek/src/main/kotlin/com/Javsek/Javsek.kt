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
        val home = document.select("article").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2.entry-title a")?.text() ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: "")
        val posterUrl = this.selectFirst("img")?.getImageAttr()

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: ""
        
        // Perbaikan selektor poster di halaman detail
        val poster = document.selectFirst("div.entry-content img")?.getImageAttr()
        val plot = document.selectFirst("div.entry-content p")?.text()

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = plot
            this.tags = document.select("span.tags-links a").eachText()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // 1. Ekstrak dari iframe (Player utama)
        document.select("div.entry-content iframe").forEach { 
            val src = it.attr("src")
            if (src.isNotEmpty()) loadExtractor(src, data, subtitleCallback, callback)
        }

        // 2. Ekstrak dari link teks/tombol (Alternative)
        document.select("div.entry-content a").forEach { 
            val href = it.attr("href")
            // Menambah filter provider yang sering digunakan JavSek
            if (href.contains("dood") || href.contains("streamwish") || href.contains("filelions") || href.contains("vidguard")) {
                loadExtractor(href, data, subtitleCallback, callback)
            }
        }
        return true
    }


    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
            this.hasAttr("srcset") -> this.attr("abs:srcset").substringBefore(" ")
            else -> this.attr("abs:src")
        }
    }
}
