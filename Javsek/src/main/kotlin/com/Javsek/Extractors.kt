package com.Javsek 

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.VidhideExtractor  // Asumsi earnvid mirip Vidhide (dari update CloudStream terbaru)
import com.lagradost.cloudstream3.extractors.StreamWishExtractor  // Untuk luluvideo, asumsi mirip StreamWish (common untuk host M3U8 JAV)
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities

class Earnvid : VidhideExtractor() {
    override var name = "Earnvid"
    override var mainUrl = "https://earnvid.com"  // Domain utama dari search (bisa earnvid.xyz / earnvids.com jika varian)
    // Jika ada sub-domain lain, tambah concat seperti di Vidhide
}

// Jika earnvid punya varian domain, tambah class terpisah
class Earnvids : VidhideExtractor() {
    override var name = "Earnvids"
    override var mainUrl = "https://earnvids.com"
}

class Earnvids : VidhideExtractor() {
    override var name = "Earnvid2"
    override var mainUrl = "https://minochinos.com"
}

// ────────────────────────────────────────────────────────────────
// 2. Luluvideo / Lulustream Extractor (mirip StreamWish – common pattern M3U8 master playlist)
// ────────────────────────────────────────────────────────────────
class Luluvideo : StreamWishExtractor() {
    override var name = "Luluvideo"
    override var mainUrl = "https://luluvideo.com"  // atau https://lulustream.com jika itu domainnya
    // StreamWishExtractor sudah handle parsing script untuk sources/file= master.m3u8
}

// Jika domain lulustream
class Lulustream : StreamWishExtractor() {
    override var name = "Lulustream"
    override var mainUrl = "https://lulustream.com"
}

 override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    var linksAdded = false

    // Multi player (?player=1 sampai 7) → otomatis coba semua server
    val baseUrl = data.substringBefore("?")  // Hapus parameter lama jika ada
    for (i in 1..7) {
        val playerUrl = "$baseUrl?player=$i"
        loadExtractor(playerUrl, data, subtitleCallback, callback)
        linksAdded = true
    }

    // Tambahan: coba iframe atau direct dari halaman utama
    try {
        app.get(data).document.select("iframe[src*='http']").forEach { iframe ->
            val embedUrl = fixUrlNull(iframe.attr("src")) ?: return@forEach
            loadExtractor(embedUrl, data, subtitleCallback, callback)
            linksAdded = true
        }
    } catch (_: Exception) {}

    return linksAdded

}
