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
        val titleElement = this.selectFirst("h2.entry-title a") ?: return null
        val title = titleElement.text().trim()
        val href = fixUrl(titleElement.attr("href"))      
        val posterUrl = this.selectFirst("image")?.getImageAttr()

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: ""
        
        // Mengambil gambar dari elemen utama konten
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
        
        // 1. Mencari link video di dalam iframe (Player)
        document.select("iframe").forEach { 
            val src = it.attr("src")
            if (src.isNotEmpty() && !src.contains("about:blank")) {
                loadExtractor(fixUrl(src), data, subtitleCallback, callback)
            }
        }

        // 2. Mencari link video dari tombol atau teks link (Doodstream, Vidguard, dll)
        document.select("div.entry-content a").forEach { 
            val href = it.attr("href")
            if (href.contains("dood") || href.contains("stream") || href.contains("earnvids") || href.contains("file") || href.contains("vidguard")) {
                loadExtractor(href, data, subtitleCallback, callback)
            }
        }
        return true
    }

    // Fungsi utilitas untuk menangani Lazy Load Gambar
    private fun Element.getImageAttr(): String? {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
            this.hasAttr("srcset") -> this.attr("abs:srcset").substringBefore(" ")
            else -> this.attr("abs:src")
        }
    }
}
