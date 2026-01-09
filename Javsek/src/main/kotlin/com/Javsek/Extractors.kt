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

class Earnvida : VidhideExtractor() {
    override var name = "Earnvida"
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

