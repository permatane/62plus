package com.KazefuriStream

import com.fasterxml.jackson.annotation.JsonProperty

data class KazefuriVideoResponse(
    val success: Boolean,
    val data: VideoData? = null,
    val message: String? = null
)

data class VideoData(
    val title: String,
    val poster: String?,
    val sources: List<VideoSource>,
    val tracks: List<SubtitleTrack>? = null
)

data class VideoSource(
    val file: String,
    val label: String,
    val type: String,
    val default: Boolean = false
)

data class SubtitleTrack(
    val file: String,
    val label: String,
    val kind: String = "subtitles",
    val default: Boolean = false
)

data class ServerOption(
    val name: String,
    val value: String,
    val type: String
)

data class EpisodeInfo(
    val number: Int,
    val title: String,
    val url: String,
    val thumbnail: String?
)