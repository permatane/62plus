package com.Podjav

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Podjav : MainAPI() {
    override var mainUrl = "https://podjav.tv"
    override var name = "Podjav"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.NSFW)

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

        val url = if (page == 1)
            "$mainUrl${request.data}/"
        else
            "$mainUrl${request.data}/page/$page/"

        val document = app.get(url).document

        val items = document.select("article, div.item")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            HomePageList(
                request.name,
                items,
                false
            ),
            hasNext = items.isNotEmpty()
        )
    }

    // ================= SEARCH RESULT =================

    private fun Element.toSearchResult(): SearchResponse? {

        val anchor = selectFirst("a[href]") ?: return null
        val title = anchor.text().trim()
        
        if (title.isBlank()) return null
        val href = fixUrl(anchor.attr("href"))
        val img = anchor.selectFirst("img")
            ?: selectFirst("img")
            ?: return null
        val poster = fixUrlNull(
            img.attr("data-src").takeIf { it.isNotBlank() }
                ?: img.attr("data-lazy-src").takeIf { it.isNotBlank() }
                ?: img.attr("srcset")
                    .split(",")
                    .firstOrNull()
                    ?.substringBefore(" ")
                ?: img.attr("src")
        ) ?: return null

        return newMovieSearchResponse(
            name = title,
            url = href,
            type = TvType.NSFW
        ) {
            posterUrl = poster
        }
    }

    // ================= SEARCH =================

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article, div.item")
            .mapNotNull { it.toSearchResult() }
    }

    // ================= LOAD DETAIL =================

    override suspend fun load(url: String): LoadResponse {

        val document = app.get(url).document
        val title = document.selectFirst("h1")
            ?.text()?.trim()
            ?: document.selectFirst("meta[property=og:title]")
                ?.attr("content")
            ?: "Podjav Video"

        val poster = fixUrlNull(
            document.selectFirst("meta[property=og:image]")
                ?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")
            ?.attr("content")
        return newMovieLoadResponse(
            title,
            url,
            TvType.NSFW,
            url
        ) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    // ================= VIDEO LINKS =================

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document

        // iframe langsung
        document.select("iframe[src]").forEach {
            fixUrlNull(it.attr("src"))?.let { iframe ->
                loadExtractor(iframe, subtitleCallback, callback)
            }
        }

        return true
    }

}
