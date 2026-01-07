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
        "category/indo-sub" to "Sub Indo",
        "category/english-sub" to "Sub English",
        "category/jav-reducing-mosaic-decensored-streaming-and-download" to "Reducing Mosaic",
        "category/amateur" to "Amateur",
        "category/chinese-porn-streaming" to "China"
    )

    /* =========================
       HTTP HELPER
       ========================= */
    private suspend fun getDocument(url: String) =
        app.get(
            url,
            headers = mapOf(
                "User-Agent" to BROWSER_UA,
                "Referer" to mainUrl
            )
        ).document

    /* =========================
       MAIN PAGE
       ========================= */
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val url = if (page == 1)
            "$mainUrl/${request.data}"
        else
            "$mainUrl/${request.data}/page/$page"

        val document = getDocument(url)

        val items = document
            .select("article.post")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            HomePageList(
                request.name,
                items,
                isHorizontalImages = false
            ),
            hasNext = items.isNotEmpty()
        )
    }

    /* =========================
       SEARCH RESULT
       ========================= */
    private fun Element.toSearchResult(): SearchResponse? {
        val titleEl = selectFirst("h2.entry-title a") ?: return null
        val img = selectFirst("img")

        val poster = img?.attr("data-src")
            ?.ifBlank { img.attr("src") }

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
            .select("article.post")
            .mapNotNull { it.toSearchResult() }
    }

    /* =========================
       LOAD DETAIL
       ========================= */
    override suspend fun load(url: String): LoadResponse {
        val document = getDocument(url)

        val title = document.selectFirst("meta[property=og:title]")
            ?.attr("content")
            ?: "Javsek Video"

        val poster = document.selectFirst("meta[property=og:image]")
            ?.attr("content")

        val desc = document.selectFirst("meta[property=og:description]")
            ?.attr("content")

        // Ambil halaman PLAYER (?player=1,2,3...)
        val playerPages = document
            .select("a[href*=?player=]")
            .map { fixUrl(it.attr("href")) }
            .distinct()

        return newMovieLoadResponse(
            name = title,
            url = url,
            type = TvType.NSFW,
            data = playerPages.joinToString("||")
        ) {
            posterUrl = poster
            plot = desc
            this.tags = document.select("span.tags-links a").eachText()
        }
    }

    /* =========================
       LOAD LINKS (HLS ONLY)
       ========================= */
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val playerPages = data.split("||").distinct()
        var found = false

        // Domain HLS yang TERBUKTI dari Javsek
        val hlsRegex = Regex(
            """https?:\/\/(sdqm\.lavonadesign\.sbs|nomtre\.upns\.pro|iplayerhls\.com)[^\s"'<>]+"""
        )

        playerPages.forEach { playerUrl ->
            try {
                val html = app.get(
                    playerUrl,
                    headers = mapOf(
                        "User-Agent" to BROWSER_UA,
                        "Referer" to mainUrl
                    )
                ).text

    hlsRegex.findAll(html)
    .map { it.value }
    .distinct()
    .mapNotNull { url ->
        when {
            url.endsWith(".m3u8", true) -> url
            url.endsWith(".txt", true) -> url.replaceAfterLast('.', "m3u8")
            else -> null
        }
    }
    .forEach { hls ->
        found = true
        callback(
            newExtractorLink(
                source = name,
                name = "HLS",
                url = hls
            ) {
                referer = playerUrl
                quality = Qualities.Unknown.value
            }
        )
    }


            } catch (_: Exception) {
            }
        }

        return found
    }
}
