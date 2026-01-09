package com.Podjav

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.VPNStatus
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Podjav : MainAPI() {
    override var mainUrl              = "https://podjav.tv"
    override var name                 = "POD JAV"
    override val hasMainPage          = true
    override var lang                 = "id"  // Focused on Indonesian subtitles
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "/movies/" to "All Movies",
        "/genre/abuse/" to "Abuse",
        "/genre/big-tits/" to "Big Tits",
        "/genre/bride/" to "Bride",
        "/genre/creampie/" to "Creampie",
        "/genre/cuckold/" to "Cuckold",
        "/genre/step-mother/" to "Step Mother"
        // Add more genres if needed
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) "$mainUrl${request.data}" else "$mainUrl${request.data}page/$page/"
        val document = app.get(url).document
        val responseList = document.select("article.item").mapNotNull { it.toSearchResult() }
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
        val results = document.select("article.item").mapNotNull { it.toSearchResult() }
        val hasNext = document.select(".pagination .next").isNotEmpty()
        return if (results.isEmpty()) null else newSearchResponseList(results, hasNext)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: "Untitled"
        val poster = fixUrlNull(document.selectFirst(".featured-image img, img.wp-post-image")?.attr("src"))
        val description = document.selectFirst(".entry-content p")?.text()?.trim()

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

     override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        val script=document.select("script:containsData(iframe_url)").html()
        val IFRAME_B64_REGEX = Regex(""""iframe_url":"([^"]+)"""")
         val iframeUrls = IFRAME_B64_REGEX.findAll(script)
             .map { it.groupValues[1] }
             .map { Base64.decode(it, Base64.DEFAULT).let(::String) }
             .toList()
         iframeUrls.forEach {
             Log.d("Phisher",it)
             val iframedoc=app.get(it, referer = it).document
             val olid=iframedoc.toString().substringAfter("var OLID = '").substringBefore("'")
             val newreq=iframedoc.toString().substringAfter("iframe").substringAfter("src=\"").substringBefore("'+OLID")
             val reverseid= olid.edoceD()
             val location= app.get("$newreq$reverseid", referer = it, allowRedirects = false)
             val link=location.headers["location"].toString()
             if (link.contains(".m3u"))
             {
                 callback.invoke(
                     newExtractorLink(
                         source = name,
                         name = name,
                         url = link,
                         INFER_TYPE
                     ) {
                         this.referer = ""
                         this.quality = getQualityFromName("")
                     }
                 )
             }
             else{
                 loadExtractor(link, referer = it,subtitleCallback,callback)
             }
         }
        return true
    }

    fun String.edoceD(): String {
        var x = this.length - 1
        var edoceD = ""
        while (x >= 0) {
            edoceD += this[x]
            x--
        }
        return edoceD
    }
}
