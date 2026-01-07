package com.Podjav

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.kt.plus
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class PodJav : MainAPI() {

    override val mainUrl = "https://podjav.tv"
    override val name = "PodJav"
    override val lang = "all"
    override val supportedTypes = setOf(TvType.Movie, TvType.Series)
    override val hasMainPage = true
    override val usesWebView = false

    private fun fixUrl(url: String?): String? {
        return url?.let { if (it.startsWith("http")) it else mainUrl + it }
    }

    private fun fixUrlNull(url: String?): String? {
        return url?.takeIf { it.isNotBlank() }?.let { fixUrl(it) }
    }

    // MAIN PAGE
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val pageUrl = if (page == 1) {
            "$mainUrl/${request.data}"
        } else {
            "$mainUrl/${request.data}/page/$page"
        }

        val document = app.get(pageUrl).document

        val items = document.select("article, .post, .item")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = false
            ),
            hasNext = items.isNotEmpty()
        )
    }

    // SEARCH
    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document

        return document.select("article, .post, .item")
            .mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = selectFirst("a[href]") ?: return null

        val title = a.attr("title").ifBlank { text() }.trim()
        val href = fixUrl(a.attr("href")) ?: return null

        val img = a.selectFirst("img")
        val poster = fixUrlNull(
            img?.attr("data-src")
                ?: img?.attr("data-lazy-src")
                ?: img?.attr("src")
        )

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            posterUrl = poster
        }
    }

    // LOAD DETAIL
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("meta[property=og:title]")
            ?.attr("content")?.trim()
            ?: document.selectFirst("h1, .entry-title")?.text()?.trim()
            ?: "PodJav Video"

        val poster = fixUrlNull(
            document.selectFirst("meta[property=og:image]")
                ?.attr("content")
        )

        val description = document.selectFirst("meta[property=og:description]")
            ?.attr("content")

        val recommendations = document.select("article.related, .related-posts .post, .item")
            .mapNotNull {
                val a = it.selectFirst("a[href]") ?: return@mapNotNull null
                val t = a.attr("title").ifBlank { a.text() }.trim()
                val h = fixUrl(a.attr("href"))
                val p = it.selectFirst("img")?.let { img ->
                    fixUrlNull(
                        img.attr("data-src")
                            ?: img.attr("src")
                    )
                }

                newMovieSearchResponse(t, h ?: return@mapNotNull null, TvType.NSFW) {
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

    // GET LINKS
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // first try iframe
        val iframes = document.select("iframe[src]")
            .mapNotNull { fixUrl(it.attr("src")) }

        if (iframes.isNotEmpty()) {
            iframes.forEach {
                callback(
                    ExtractorLink(
                        name,
                        it,
                        it,
                        "HD",
                        null
                    )
                )
            }
            return true
        }

        // fallback: direct video tags
        document.select("video source[src]")
            .mapNotNull { fixUrl(it.attr("src")) }
            .forEach {
                callback(
                    ExtractorLink(
                        name,
                        it,
                        it,
                        "HD",
                        null
                    )
                )
            }

        return true
    }
}
