package com.Podjav

import android.util.Base64
import com.lagradost.api.Log
//import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Podjav : MainAPI() {
    override var mainUrl              = "https://podjav.tv"
    override var name                 = "PodJAV"
    override val hasMainPage          = true
    override var lang                 = "id"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.NSFW)

    /* =========================
       USER AGENT (MANUAL)
       ========================= */
    private val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"


    override val mainPage = mainPageOf(
        "/movies/" to "Update Terbaru",
        "/genre/orgasm/" to "Orgasme",
        "/genre/big-tits/" to "Tobrut",
        "/genre/creampie/" to "Krim Pejuh",
        "/genre/abuse/" to "Pemaksaan",
        "/genre/model/" to "Model Cantik",
        "/genre/mature-woman/" to "Wanita Dewasa",
        "/genre/step-mother/" to "Ibu Angkat",
        "/genre/nurse/" to "Perawat",
        "/genre/secretary/" to "Sekretaris",
        "/genre/female-teacher/" to "Guru",
        "/genre/swingers/" to "Tukar Pasangan",
        "/genre/solowork/" to "Solowork",
        "/genre/cuckold/" to "Istri Menyimpang"

    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) "$mainUrl${request.data}" else "$mainUrl${request.data}page/$page/"
        val document = app.get(url).document
        val responseList = document.select("article, div.item").mapNotNull { it.toSearchResult() }
        val hasNext = document.select(".pagination .next").isNotEmpty()
        return newHomePageResponse(HomePageList(request.name, responseList, isHorizontalImages = false), hasNext)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleEl = selectFirst("h2.entry-title a, h3 a") ?: return null
        val title = titleEl.text().trim()
        if (title.isEmpty()) return null
        val href = fixUrlNull(titleEl.attr("href")) ?: return null
        val posterUrl = selectFirst("img")?.attr("src")?.let { fixUrlNull(it) }
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val url = if (page == 1) "$mainUrl/?s=$query" else "$mainUrl/page/$page/?s=$query"
        val document = app.get(url).document
        val results = document.select("article, div.item").mapNotNull { it.toSearchResult() }
        val hasNext = document.select(".pagination .next").isNotEmpty()
        return if (results.isEmpty()) null else newSearchResponseList(results, hasNext)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("meta[property=og:title]")
            ?.attr("content")
            ?.trim()
            ?: "Podjav Video"
        val poster = document.selectFirst("meta[property=og:image]")
            ?.attr("content")
        val description = document.selectFirst("meta[property=og:description]")
            ?.attr("content")
        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.tags = document.select("a[rel=tag]").eachText()
        }
    }

override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    var linksAdded = false

    // 1. Prioritas tertinggi: Ambil langsung dari tag <video> di halaman
    try {
        val doc = app.get(data, timeout = 30).document
        val videoSrc = doc.selectFirst("video.jw-video")?.attr("src")
            ?: doc.selectFirst("video")?.attr("src")

        if (!videoSrc.isNullOrEmpty() && videoSrc.startsWith("http")) {
            callback(
                newExtractorLink(
                    source = this.name,
                    name = "Direct MP4 • 1080p (Player)",
                    url = videoSrc,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = data
                    this.quality = Qualities.P1080.value
                    this.headers = mapOf(
                        "Origin" to "https://podjav.tv",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
                    )
                }
            )
            linksAdded = true
        }
    } catch (e: Exception) {
        // Jika gagal fetch halaman (Cloudflare block dll), lanjut ke fallback
    }

    // 2. Fallback: Generate link berdasarkan kode JAV dari URL
    val javCodeMatch = Regex("/movies/([a-zA-Z0-9-]+)(-sub-indo-.*?)?/?$").find(data)
    val javCode = javCodeMatch?.groupValues?.get(1)?.uppercase() ?: return linksAdded

    // Link standar: KODE.mp4
    val standardUrl = "https://vod.podjav.tv/$javCode/$javCode.mp4"
    callback(
        newExtractorLink(
            source = this.name,
            name = "Direct MP4 • 1080p",
            url = standardUrl,
            type = ExtractorLinkType.VIDEO
        ) {
            this.referer = data
            this.quality = Qualities.P1080.value
            this.headers = mapOf(
                "Origin" to "https://podjav.tv",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
        }
    )
    linksAdded = true

    // Link versi Sub Indo: KODE-id.mp4 (contoh: START-440-id.mp4)
    val indoUrl = "https://vod.podjav.tv/$javCode/$javCode-id.mp4"
    if (indoUrl != standardUrl) {  // Hindari duplikat
        callback(
            newExtractorLink(
                source = this.name,
                name = "Direct MP4 • 1080p (Sub Indo)",
                url = indoUrl,
                type = ExtractorLinkType.VIDEO
            ) {
                this.referer = data
                this.quality = Qualities.P1080.value
                this.headers = mapOf(
                    "Origin" to "https://podjav.tv",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
            }
        )
    }

    return linksAdded
}
}
