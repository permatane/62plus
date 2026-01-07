package com.Podjav

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Podjav : MainAPI() {

    override var mainUrl = "https://podjav.tv"
    override var name = "Podjav"
    override val hasMainPage = true
    override var lang = "id"
    override val hasQuickSearch = false
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "/movies" to "Latest",
        "/genre/big-tits" to "Tobrut",
        "/genre/orgasm" to "Orgame"
    )

    // ================= MAIN PAGE =================

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val pageUrl = if (page == 1) {
            "$mainUrl${request.data}"
        } else {
            "$mainUrl${request.data}/page/$page"
        }

        val document = app.get(pageUrl).document

        val items = document.select("article, div.item")
            .mapNotNull { it.toSearchResult() }
            .filter { it.posterUrl != null }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = false
            ),
            hasNext = items.isNotEmpty()
        )
    }

    // ================= SEARCH ITEM =================

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = selectFirst("a[href]") ?: return null

        val title = anchor.attr("title")
            .ifBlank { anchor.text() }
            .trim()

        val href = fixUrl(anchor.attr("href"))

        val img = anchor.selectFirst("img")

        val poster = fixUrlNull(
            img?.attr("data-src")
                ?.ifBlank { null }
                ?: img?.attr("data-lazy-src")
                    ?.ifBlank { null }
                ?: img?.attr("src")
                    ?.ifBlank { null }
        )

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            posterUrl = poster
        }
    }

    // ================= SEARCH =================

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document

        return document.select("article, div.item")
            .mapNotNull { it.toSearchResult() }
            .filter { it.posterUrl != null }
    }

    // ================= LOAD DETAIL =================

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("meta[property=og:title]")
            ?.attr("content")
            ?.trim()
            ?: document.selectFirst("h1")?.text()?.trim()
            ?: "Podjav Video"

        val poster = fixUrlNull(
            document.selectFirst("meta[property=og:image]")
                ?.attr("content")
        )

        val description = document.selectFirst("meta[property=og:description]")
            ?.attr("content")

        val recommendations = document.select("article.related, .related article, .related .item")
            .mapNotNull {
                val a = it.selectFirst("a[href]") ?: return@mapNotNull null
                val t = a.attr("title").ifBlank { a.text() }.trim()
                val h = fixUrl(a.attr("href"))
                val img = a.selectFirst("img")
                val p = fixUrlNull(
                    img?.attr("data-src")
                        ?: img?.attr("src")
                )

                newMovieSearchResponse(t, h, TvType.NSFW) {
                    posterUrl = p
                }
            }

        return newMovieLoadResponse(
            name = title,
            url = url,
            type = TvType.NSFW,
            data = url
        ) {
            this.posterUrl = poster
            this.plot = description
            this.recommendations = recommendations
        }
    }

    // ================= LOAD LINKS =================

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document

        val iframeUrls = document.select("iframe[src]")
            .mapNotNull {
                fixUrlNull(it.attr("src"))
            }

        iframeUrls.forEach { iframe ->
            loadExtractor(
                url = iframe,
                subtitleCallback = subtitleCallback,
                callback = callback
            )
        }

        return iframeUrls.isNotEmpty()
    }
}