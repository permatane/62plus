package com.Podjav

import android.util.Base64
import com.lagradost.api.Log
//import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

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

        val title = document.selectFirst("meta[property=og:title]")?.text()?.trim() ?: "Untitled"
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]") ?.attr("content")
        val description = ddocument.selectFirst("meta[property=og:description]")?.attr("content")

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




