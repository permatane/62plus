package com.Javsek

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Javsek : MainAPI() {

    override var mainUrl = "https://javsek.net"
    override var name = "Javsek"
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie)
    override val hasMainPage = true

    // ================= HOME =================

    override val mainPage = mainPageOf(
        "/category/indo-sub/" to "Sub Indo"
    )

    override fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val url = if (page == 1)
            mainUrl + request.data
        else
            "${mainUrl}${request.data}page/$page/"

        val doc = app.get(url).document

        val items = doc.select("article").mapNotNull { article ->
            val a = article.selectFirst("a") ?: return@mapNotNull null
            val title = article.selectFirst("h2")?.text() ?: return@mapNotNull null
            val poster = article.selectFirst("img")?.attr("src")

            newMovieSearchResponse(
                title,
                fixUrl(a.attr("href")),
                TvType.Movie
            ) {
                this.posterUrl = poster
            }
        }

        return newHomePageResponse(
            request.name,
            items,
            hasNextPage = true
        )
    }

    // ================= SEARCH =================

    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query.replace(" ", "+")}"
        val doc = app.get(url).document

        return doc.select("article").mapNotNull {
            val a = it.selectFirst("a") ?: return@mapNotNull null
            val title = it.selectFirst("h2")?.text() ?: return@mapNotNull null
            val poster = it.selectFirst("img")?.attr("src")

            newMovieSearchResponse(
                title,
                fixUrl(a.attr("href")),
                TvType.Movie
            ) {
                this.posterUrl = poster
            }
        }
    }

    // ================= DETAIL =================

    override fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1.entry-title")?.text() ?: "Javsek Video"
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val desc = doc.selectFirst("meta[property=og:description]")?.attr("content")

        return newMovieLoadResponse(
            title,
            url,
            TvType.Movie,
            url
        ) {
            this.posterUrl = poster
            this.plot = desc
        }
    }

    // ================= VIDEO =================

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val doc = app.get(data).document

        doc.select("iframe[src]").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotBlank()) {
                loadExtractor(
                    fixUrl(src),
                    data,
                    subtitleCallback,
                    callback
                )
            }
        }

        return true
    }
}