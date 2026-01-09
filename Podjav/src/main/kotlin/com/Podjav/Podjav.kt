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
    // Bangun URL dengan pagination benar (/page/N/)
    val url = if (page == 1) {
        "$mainUrl${request.data}"
    } else {
        "$mainUrl${request.data}page/$page/"
    }

    val document = app.get(url, timeout = 20).document  // Timeout lebih kecil agar cepat

    // Selector item: disesuaikan struktur podjav.tv (heading link ke movie)
    val home = document.select("h3 a, h2 a, a[href*='/movies/']").mapNotNull { element ->
        val title = element.text().trim()
        if (title.isEmpty() || !title.contains("Sub Indo", ignoreCase = true)) return@mapNotNull null

        val href = fixUrlNull(element.attr("href")) ?: return@mapNotNull null

        // Poster: coba ambil dari parent jika ada img
        val poster = element.parents().selectFirst("img")?.attr("src")?.let { fixUrlNull(it) }

        newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = poster
        }
    }

    // Deteksi halaman berikutnya untuk infinite scroll
    val hasNext = document.select(".pagination .next, a.next, a[href*='page/${page + 1}']").isNotEmpty()

    return newHomePageResponse(
        HomePageList(
            name = request.name,
            list = home,
            isHorizontalImages = false
        ),
        hasNext = hasNext
    )
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
    // Hanya proses page 1 (tidak ada pagination di hasil search podjav.tv)
    if (page > 1) return null

    val searchUrl = "$mainUrl/?s=$query"
    
    val document = try {
        app.get(searchUrl, timeout = 30).document
    } catch (e: Exception) {
        return null  // Jika gagal fetch (blokir dll), return null
    }

    // Selector: ambil link ke halaman film
    val results = document.select("a[href*='/movies/']").mapNotNull { element ->
        val title = element.text().trim()
        if (title.isEmpty()) return@mapNotNull null

        // Filter relevansi (harus mengandung query)
        if (!title.contains(query, ignoreCase = true)) return@mapNotNull null

        val href = fixUrlNull(element.attr("href")) ?: return@mapNotNull null

        newMovieSearchResponse(
            name = title,
            url = href,
            type = TvType.NSFW
        ) {
            this.posterUrl = posterUrl
        }
    }

    // Jika tidak ada hasil → return null (CloudStream akan tampilkan "No results")
    if (results.isEmpty()) return null

    // Return dengan hasNext = false (tidak ada pagination di search)
    return newSearchResponseList(results, hasNext = false)
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
    val javCodeMatch = Regex("/movies/([a-zA-Z0-9-]+)-sub-indo-").find(data)
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







