package com.Javstory

import android.util.Log
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.WebViewResolver
import org.jsoup.nodes.Element

class JavStory : MainAPI() {
    override var name = "JavStory"
    override var mainUrl = "https://javstory1.com"
    override val supportedTypes = setOf(TvType.NSFW)
    override val hasDownloadSupport = true
    override val hasMainPage = true
    override val hasQuickSearch = false

    override val mainPage = mainPageOf(
        "category/indosub/" to "Sub Indonesia",
        "category/engsub/" to "Sub English",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.isEmpty()) {
            "$mainUrl/page/$page/"
        } else {
            "$mainUrl/${request.data}page/$page/"
        }
        val document = app.get(url).document
        val items = document.select("article, .post-item, .post").mapNotNull { it.toSearchResult() }
        val hasNext = items.isNotEmpty() && document.select("a.next.page-numbers").isNotEmpty()
        return newHomePageResponse(HomePageList(request.name, items, isHorizontalImages = true), hasNext)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleEl = this.selectFirst("h3 a, h2 a, .post-title a, a.title, a[href]") ?: return null
        val title = titleEl.text().trim()
        val href = fixUrl(titleEl.attr("href"))
        val poster = this.selectFirst("img")?.attr("data-src") ?: this.selectFirst("img")?.attr("src") ?: return null
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = poster
            posterHeaders = mapOf("Referer" to mainUrl)
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = "$mainUrl/page/$page/?s=$query"
        val document = app.get(url).document
        val items = document.select("article, .post-item, .post").mapNotNull { it.toSearchResult() }
        val hasNext = items.isNotEmpty() && document.select("a.next.page-numbers").isNotEmpty()
        return newSearchResponseList(items, hasNext)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim() ?: "No Title"
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            posterHeaders = mapOf("Referer" to mainUrl)
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = app.get(data)
        val doc = res.document
        val text = res.text

        val embedUrls = mutableSetOf<String>()

        // 1. Direct iframes
        doc.select("iframe[src*=streamtape], iframe[src*=sbembed], iframe[src*=streamsb], iframe.player-iframe").forEach {
            var src = fixUrl(it.attr("src"))
            if (src.contains("/e/") || src.contains(".html")) {
                src = src.trimEnd('/').removeSuffix(".html")
                embedUrls.add(src)
                if (src.contains("sbembed") || src.contains("streamsb")) {
                    embedUrls.add("$src.html")
                }
            }
        }

        // 2. Direct <video> tag (StreamTape get_video) - menggunakan newExtractorLink untuk menghindari deprecation
        doc.select("video source, video#mainvideo").forEach {
            val src = it.attr("src")
            if (src.isNotEmpty() && src.contains("get_video")) {
                callback(
                    newExtractorLink(
                        name = "$name Direct StreamTape",
                        url = src,
                        referer = mainUrl,
                        quality = Qualities.P1080.value,
                        isM3u8 = src.contains("m3u8")
                    )
                )
            }
        }

        // 3. loadStream('base/', 'code')
        val loadStreamRegex = Regex("""loadStream\s*\(\s*['"](https?://[^'"]*?/e/?)['"],\s*['"]([A-Za-z0-9]+)['"]\s*\)""")
        loadStreamRegex.findAll(text).forEach { match ->
            val base = match.groupValues[1].removeSuffix("/")
            val code = match.groupValues[2]
            val url = "$base/$code".trimEnd('/')
            embedUrls.add(url)
        }

        // 4. Obfuscated .rndmzr() → reverse string
        val rndmzrRegex = Regex("""["']([A-Za-z0-9]{10,20})["']\s*\.rndmzr\(\)""")
        rndmzrRegex.findAll(text).forEach { match ->
            val reversedCode = match.groupValues[1]
            val code = reversedCode.reversed()
            val bases = listOf(
                "https://streamtape.com/e/",
                "https://streamtape.to/e/",
                "https://streamtape.xyz/e/",
                "https://sbembed2.com/e/",
                "https://sbembed.com/e/",
                "https://streamsb.com/e/"
            )
            bases.forEach { base ->
                var url = base + code
                if (base.contains("sbembed") || base.contains("streamsb")) url += ".html"
                embedUrls.add(url.removeSuffix(".html"))
                embedUrls.add(url)
            }
        }

        // 5. General /e/code patterns
        val generalRegex = Regex("""(https?://[^'"\s>]{10,200}/e/[A-Za-z0-9]{6,20}[^'"\s>]*?)""")
        generalRegex.findAll(text).forEach { match ->
            var url = match.value.trimEnd('/').removeSuffix(".html")
            embedUrls.add(url)
            if (url.contains("sbembed") || url.contains("streamsb")) {
                embedUrls.add("$url.html")
            }
        }

        // Load semua embed URL
        embedUrls.forEach { url ->
            Log.d("JavStory1", "Loading extractor: $url")
            loadExtractor(url, mainUrl, subtitleCallback, callback)
        }

        return embedUrls.isNotEmpty() || doc.select("video source, video#mainvideo").isNotEmpty()
    }
}

