package com.Podjav

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Podjav : MainAPI() {

    override var mainUrl = "https://podjav.tv/"
    override var name = "PodJav"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.NSFW)


    override val mainPage = mainPageOf(
 
        "" to "Terbaru",
        "/genre/big-tits" to "Tobrut",
        "/genre/orgasm" to "Orgasme"
    )

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

        val items = document.select("article, div.item")
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

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = this.selectFirst("a") ?: return null
        val title = anchor.attr("title").ifBlank {
            anchor.text()
        }.trim()

        val href = fixUrl(anchor.attr("href"))
        val poster = fixUrlNull(
            this.selectFirst("img")?.attr("data-src")
                ?: this.selectFirst("img")?.attr("src")
        )

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document

        return document.select("article, div.item")
            .mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("meta[property=og:title]")
            ?.attr("content")
            ?.trim()
            ?: "Javsek Video"

        val poster = document.selectFirst("meta[property=og:image]")
            ?.attr("content")

        val description = document.selectFirst("meta[property=og:description]")
            ?.attr("content")
            
        val recommendations = document.select("article.related, div.related item")
            .mapNotNull {
                val a = it.selectFirst("a") ?: return@mapNotNull null
                val t = a.attr("title").trim()
                val h = fixUrl(a.attr("href"))
                val p = fixUrlNull(it.selectFirst("img")?.attr("src"))

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

override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {

    val document = app.get(data).document
    var found = false

    // ================= DIRECT <video src> =================
    document.select("video[src]").forEach { video ->
        val videoUrl = fixUrlNull(video.attr("src")) ?: return@forEach

        callback(
            newExtractorLink(
                source = name,
                name = "Direct MP4",
                url = videoUrl,
                type = ExtractorLinkType.VIDEO
            ) {
                this.referer = data
                this.quality = Qualities.Unknown.value
              
            }
        )
        found = true
    }

    // ================= <source src> =================
    document.select("video source[src]").forEach { source ->
        val videoUrl = fixUrlNull(source.attr("src")) ?: return@forEach

        callback(
            newExtractorLink(
                source = name,
                name = "Direct MP4",
                url = videoUrl,
                type = ExtractorLinkType.VIDEO
            ) {
                this.referer = data
                this.quality = Qualities.Unknown.value

            }
        )
        found = true
    }

    // ================= IFRAME =================
    document.select("iframe[src]").forEach { iframe ->
        val src = fixUrlNull(iframe.attr("src")) ?: return@forEach
        loadExtractor(src, subtitleCallback, callback)
        found = true
    }

    // ================= DATA ATTR =================
    document.select("[data-src], [data-video], [data-embed]").forEach { el ->
        listOf(
            el.attr("data-src"),
            el.attr("data-video"),
            el.attr("data-embed")
        ).forEach { raw ->
            val url = fixUrlNull(raw)
            if (url != null) {
                loadExtractor(url, subtitleCallback, callback)
                found = true
            }
        }
    }

    return found
}

}





