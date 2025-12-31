package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.text.RegexOption
import kotlinx.coroutines.withTimeoutOrNull

class Kazefuri : MainAPI() {
    override var mainUrl = "https://sv3.kazefuri.cloud"
    override var name = "Kazefuri"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)
    override var lang = "id"
    override val hasMainPage = true
    override val hasQuickSearch = true

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = withTimeoutOrNull(30000) {
            app.get(mainUrl, timeout = 30).document
        } ?: return newHomePageResponse(emptyList())

        val homePageLists = mutableListOf<HomePageList>()

        try {
            val headings = document.select("h2, h3")
            headings.forEach { heading ->
                val sectionName = heading.text().trim().takeIf { it.isNotEmpty() } ?: "Unknown Section"
                val list = heading.nextElementSibling()?.select("a[href]")
                    ?.mapNotNull { it.toSearchResult() } ?: emptyList()
                if (list.isNotEmpty()) {
                    homePageLists.add(HomePageList(sectionName, list))
                }
            }

            if (homePageLists.isEmpty()) {
                val allItems = document.select("a[href^=/][href$=/]")
                    .mapNotNull { it.toSearchResult() }
                if (allItems.isNotEmpty()) {
                    homePageLists.add(HomePageList("Available Anime", allItems))
                }
            }
        } catch (e: Exception) { }

        return newHomePageResponse(homePageLists)
    }

    private fun org.jsoup.nodes.Element.toSearchResult(): SearchResponse? {
        return try {
            val href = this.attr("href").takeIf { it.startsWith("/") && it.endsWith("/") } ?: return null
            val fullHref = fixUrl(href)
            val title = this.text().trim().takeIf { it.isNotEmpty() } ?: return null

            newAnimeSearchResponse(title, fullHref, TvType.Anime) {
                this.posterUrl = null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val formattedQuery = query.replace(" ", "-")
        val url = "$mainUrl/search/$formattedQuery"

        val document = withTimeoutOrNull(20000) {
            app.get(url, timeout = 20).document
        }

        return document?.select("ul li a[href*=-episode-]")
            ?.mapNotNull { it.toSearchResult() }
            ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        val document = withTimeoutOrNull(30000) {
            app.get(url, timeout = 30).document
        } ?: return newAnimeLoadResponse("Unknown", url, TvType.Anime) {
            addEpisodes(DubStatus.Subbed, emptyList())
        }

        val title = try {
            document.selectFirst("h1")?.text()?.trim() ?: "Unknown Title"
        } catch (e: Exception) { "Unknown Title" }

        val description = try {
            document.selectFirst("h2:contains(Synopsis) + p")?.text()?.trim()
                ?: document.selectFirst("p")?.text()?.trim()
                ?: ""
        } catch (e: Exception) { "" }

        val episodes = mutableListOf<Episode>()

        try {
            val seasonHeadings = document.select("h2:contains(Musim)")
            if (seasonHeadings.isNotEmpty()) {
                seasonHeadings.forEachIndexed { index, seasonHeading ->
                    val seasonNum = Regex("\\d+").find(seasonHeading.text())?.value?.toInt() ?: (index + 1)
                    val seasonList = seasonHeading.nextElementSibling()?.select("ul li a[href*=-episode-]")
                    seasonList?.forEach { epElement ->
                        val epHref = fixUrl(epElement.attr("href"))
                        val epText = epElement.text().trim()
                        val epNum = Regex("Episode (\\d+)", RegexOption.IGNORE_CASE)
                            .find(epText)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        if (epNum > 0) {
                            episodes.add(newEpisode(epHref) {
                                this.name = epText.ifEmpty { "Episode $epNum" }
                                this.season = seasonNum
                                this.episode = epNum
                            })
                        }
                    }
                }
            } else {
                document.select("ul li a[href*=-episode-]").forEach { epElement ->
                    val epHref = fixUrl(epElement.attr("href"))
                    val epText = epElement.text().trim()
                    val epNum = Regex("Episode (\\d+)", RegexOption.IGNORE_CASE)
                        .find(epText)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    if (epNum > 0) {
                        episodes.add(newEpisode(epHref) {
                            this.name = epText.ifEmpty { "Episode $epNum" }
                            this.season = 1
                            this.episode = epNum
                        })
                    }
                }
            }
        } catch (e: Exception) { }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = null
            this.plot = description
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var hasLinks = false

        val document = withTimeoutOrNull(30000) {
            app.get(data, timeout = 30).document
        } ?: return false

        try {
            document.select("h2:contains(P)").forEach { qualityHeading ->
                val qualityText = qualityHeading.text().trim()
                val qualityInt = getQualityFromName(qualityText)

                val links = qualityHeading.nextElementSibling()?.select("a[href]")
                links?.forEach { linkElement ->
                    val hostUrl = linkElement.attr("href").takeIf { it.isNotEmpty() } ?: return@forEach
                    val fullUrl = fixUrl(hostUrl)

                    // loadExtractor sudah suspend, jadi bisa dipanggil langsung di coroutine
                    loadExtractor(fullUrl, data, subtitleCallback, callback)
                    hasLinks = true
                }
            }
        } catch (e: Exception) { }

        return hasLinks
    }
}