version = 8

cloudstream {
    description = "Ngefilm"
    language = "id"
    authors = listOf("Matane")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "AsianDrama",
        "TvSeries",
        "Movie",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=https://ngefilm21.pw&sz=%size%"
    
}
