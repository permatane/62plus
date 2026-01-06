version = 3

cloudstream {
    description = "Javsek"
    language = "id"
    authors = listOf("Matanya")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 3 // will be 3 if unspecified
    tvTypes = listOf("NSFW")

    iconUrl = "https://www.google.com/s2/favicons?domain=https://javsek.net&sz=%size%"
}
