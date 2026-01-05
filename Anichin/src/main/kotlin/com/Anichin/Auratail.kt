package com.Anichin

class Auratail : Anichin() {
    override var mainUrl              = "https://auratail.vip/"
    override var name                 = "Auratail"
    override val hasMainPage          = true
    override var lang                 = "id"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.Anime)

    override val mainPage = mainPageOf(
        "anime/?status=&type=&order=update" to "Update Terbaru",
    )
}


