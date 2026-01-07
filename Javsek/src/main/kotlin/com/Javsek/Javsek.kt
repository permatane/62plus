package com.Javsek

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URI

class Javsek : MainAPI() {

    override var mainUrl = "https://javsek.net"
    override var name = "Javsek"
    override val hasMainPage = true
    override var lang = "id"
    override val hasQuickSearch = false
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)


    /* =========================
       USER AGENT (MANUAL)
       ========================= */
    private val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    override val mainPage = mainPageOf(
        "" to "Latest",
        "category/indo-sub" to "Subtitle Indonesia"
    )

    /* =========================
       HTTP HELPER
       ========================= */
    private suspend fun getDocument(url: String) =
        app.get(
            url,
            headers = mapOf(
                "User-Agent" to BROWSER_UA,
                "Referer" to mainUrl,
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
        ).document

    /* =========================
       MAIN PAGE
       ========================= */
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val url = if (page == 1) {
            "$mainUrl/${request.data}"
        } else {
            "$mainUrl/${request.data}/page/$page"
        }

        val document = getDocument(url)

        val items = document
            .select("article.post, article.type-post, div.post")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = false
            ),
            hasNext = items.isNotEmpty()
        )
    }

    /* =========================
       SEARCH RESULT PARSER
       ========================= */
    private fun Element.toSearchResult(): SearchResponse? {
        val titleEl = selectFirst("h2.entry-title a, h3.entry-title a") ?: return null
        val img = selectFirst("img")

        val poster = img?.let {
            it.attr("data-src")
                .ifBlank { it.attr("data-lazy-src") }
                .ifBlank { it.attr("src") }
        }

        return newMovieSearchResponse(
            titleEl.text().trim(),
            fixUrl(titleEl.attr("href")),
            TvType.NSFW
        ) {
            posterUrl = fixUrlNull(poster)
        }
    }

    /* =========================
       SEARCH
       ========================= */
    override suspend fun search(query: String): List<SearchResponse> {
        val document = getDocument("$mainUrl/?s=$query")

        return document
            .select("article.post, article.type-post, div.post")
            .mapNotNull { it.toSearchResult() }
    }

    /* =========================
       LOAD DETAIL PAGE
       ========================= */
    override suspend fun load(url: String): LoadResponse {
        val document = getDocument(url)

        val title = document.selectFirst("meta[property=og:title]")
            ?.attr("content")
            ?.trim()
            ?: "Javsek Video"

        val poster = document.selectFirst("meta[property=og:image]")
            ?.attr("content")

        val description = document.selectFirst("meta[property=og:description]")
            ?.attr("content")

        val serverLinks = mutableSetOf<String>()

        // iframe servers
        document.select("iframe").forEach {
            val src = it.attr("src")
            if (src.startsWith("http")) {
                serverLinks.add(fixUrl(src))
            }
        }

        // anchor servers
        document.select("a").forEach {
            val href = it.attr("href")
            if (
                href.contains("player", true) ||
                href.contains("embed", true) ||
                href.contains("server", true)
            ) {
                if (href.startsWith("http")) {
                    serverLinks.add(fixUrl(href))
                }
            }
        }

        return newMovieLoadResponse(
            name = title,
            url = url,
            type = TvType.NSFW,
            data = serverLinks.joinToString("||")
        ) {
            this.posterUrl = poster
            this.plot = description
            this.tags = document.select("span.tags-links a").eachText()
        }
    }

    /* =========================
       LOAD LINKS (MULTI SERVER)
       ========================= */
  override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
val document = app.get(data).document
        
        // 1. Ambil semua opsi dari Dropdown Player (jika ada)
        // JavSek biasanya menggunakan <select id="select-player"> atau list <a>
        val playerOptions = document.select("select#select-player option, .muvipro-player-tabs li a, .player-option")
        
        if (playerOptions.isNotEmpty()) {
            playerOptions.forEach { option ->
                // Jika berupa dropdown <option>, ambil value-nya. Jika <a>, ambil href.
                val playerUrl = option.attr("value").ifBlank { option.attr("href") }
                if (playerUrl.isNotBlank() && playerUrl.startsWith("http")) {
                    fetchAndExtract(playerUrl, data, subtitleCallback, callback)
                }
            }
        }

        // 2. Scan Iframe yang ada di halaman utama (default player)
        document.select("iframe").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotEmpty() && !src.contains("about:blank")) {
                loadExtractor(fixUrl(src), data, subtitleCallback, callback)
            }
        }

        // 3. Scan link download/external di bawah konten
        document.select(".entry-content a[href*='earnvid'], .entry-content a[href*='dood'], .entry-content a[href*='vidguard']").forEach { link ->
            loadExtractor(fixUrl(link.attr("href")), data, subtitleCallback, callback)
        }

        return true
    }

    private suspend fun fetchAndExtract(url: String, referer: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        try {
            val doc = app.get(url, referer = referer).document
            doc.select("iframe").forEach { iframe ->
                val src = fixUrl(iframe.attr("src"))
                // Paksa loadExtractor mengenali earnvid sebagai vidguard jika perlu
                loadExtractor(src, url, subtitleCallback, callback)
            }
        } catch (e: Exception) {
            // Log error jika diperlukan
        }
    }

    private fun Element.getImageAttr(): String? {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
            this.hasAttr("srcset") -> this.attr("abs:srcset").substringBefore(" ")
            else -> this.attr("abs:src")
        }
    }
}
