package com.Javsek

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Javsek : MainAPI() {

    override var mainUrl = "https://javsek.net"
    override var name = "Javsek"
    override val hasMainPage = true
    override var lang = "id"
    override val hasQuickSearch = false
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

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
    val id = document.selectFirst("div#muvipro_player_content_id")?.attr("data-id")

    // 🎬 Ambil iframe player (streaming)
    if (id.isNullOrEmpty()) {
        document.select("ul.muvipro-player-tabs li a").amap { ele ->
            val iframe = app.get(fixUrl(ele.attr("href")))
                .document
                .selectFirst("div.gmr-embed-responsive iframe")
                ?.getIframeAttr()
                ?.let { httpsify(it) }
                ?: return@amap

            loadExtractor(iframe, "$directUrl/", subtitleCallback, callback)
        }
    } else {
        document.select("div.tab-content-ajax").amap { ele ->
            val server = app.post(
                "$directUrl/wp-admin/admin-ajax.php",
                data = mapOf(
                    "action" to "muvipro_player_content",
                    "tab" to ele.attr("id"),
                    "post_id" to "$id"
                )
            ).document
                .select("iframe")
                .attr("src")
                .let { httpsify(it) }

            loadExtractor(server, "$directUrl/", subtitleCallback, callback)
        }
    }

document.select("ul.gmr-download-list li a").forEach { linkEl ->
    val downloadUrl = linkEl.attr("href")
    if (downloadUrl.isNotBlank()) {
        loadExtractor(downloadUrl, data, subtitleCallback, callback)
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

    private fun Element?.getIframeAttr(): String? {
        return this?.attr("data-litespeed-src").takeIf { it?.isNotEmpty() == true }
                ?: this?.attr("src")
    }

    private fun String?.fixImageQuality(): String? {
        if (this == null) return null
        val regex = Regex("(-\\d*x\\d*)").find(this)?.groupValues?.get(0) ?: return this
        return this.replace(regex, "")
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }
}
