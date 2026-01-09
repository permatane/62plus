package com.Javsek

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.SubtitleFile

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
    data: String,                        // URL halaman film, contoh: https://javsek.net/sub-indo-ntrh-026-julia-toketmu-nyaris-sempurna/
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    var linksAdded = false

    // 1. Fetch halaman film untuk parsing
    val doc = try {
        app.get(data, timeout = 30).document
    } catch (e: Exception) {
        null
    }

    // 2. Coba ambil direct <video src> jika ada (jarang, tapi safety)
    doc?.selectFirst("video")?.attr("src")?.let { src ->
        val fullSrc = fixUrlNull(src) ?: return@let
        if (fullSrc.startsWith("http")) {
            callback(
                newExtractorLink(
                    source = this.name,
                    name = "Direct MP4 • 1080p",
                    url = fullSrc,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = data
                    this.quality = Qualities.P1080.value
                }
            )
            linksAdded = true
        }
    }

    // 3. Coba ambil semua iframe embed (banyak JAV site pakai iframe per server)
    doc?.select("iframe[src*='http']")?.forEach { iframe ->
        val iframeSrc = fixUrlNull(iframe.attr("src")) ?: return@forEach
        loadExtractor(iframeSrc, data, subtitleCallback, callback)
        linksAdded = true
    }

    // 4. Fallback utama: Multi server dengan parameter ?player=1 sampai ?player=7
    // Pola ini sesuai link yang kamu berikan — setiap ?player=N load player/server berbeda
    val baseUrl = data.substringBefore("?")  // Hapus parameter jika ada (misal ?player= lama)
    for (i in 1..7) {
        val playerUrl = "$baseUrl?player=$i"
        loadExtractor(playerUrl, data, subtitleCallback, callback)
        linksAdded = true
    }

    // Subtitle Indo biasanya hardcoded di video → tidak perlu external subtitle

    return linksAdded || doc != null  // Return true jika ada link ditambahkan atau halaman berhasil di-fetch
 }
}
