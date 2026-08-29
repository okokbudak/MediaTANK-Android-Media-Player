package com.orhan.iptv.data.xtream

import com.google.gson.annotations.SerializedName

/**
 * Xtream Codes panel API modelleri.
 * Sunucular alanları bazen string bazen sayı döndürür; bu yüzden sayısal
 * alanlar String olarak alınıp gerektiğinde dönüştürülür.
 */

data class XtreamAuth(
    @SerializedName("user_info") val userInfo: UserInfo?,
    @SerializedName("server_info") val serverInfo: ServerInfo?
) {
    data class UserInfo(
        val username: String?,
        val status: String?,
        @SerializedName("exp_date") val expDate: String?,
        @SerializedName("max_connections") val maxConnections: String?
    )

    data class ServerInfo(
        val url: String?,
        val port: String?,
        @SerializedName("https_port") val httpsPort: String?,
        @SerializedName("server_protocol") val serverProtocol: String?
    )
}

data class XtreamCategory(
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("category_name") val categoryName: String?
)

data class XtreamLiveStream(
    @SerializedName("stream_id") val streamId: String?,
    val name: String?,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("epg_channel_id") val epgChannelId: String?
)

data class XtreamVodStream(
    @SerializedName("stream_id") val streamId: String?,
    val name: String?,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("container_extension") val containerExtension: String?,
    val plot: String?,
    val rating: String?
)

data class XtreamSeries(
    @SerializedName("series_id") val seriesId: String?,
    val name: String?,
    val cover: String?,
    @SerializedName("category_id") val categoryId: String?,
    val plot: String?
)

data class XtreamSeriesInfo(
    val episodes: Map<String, List<Episode>>?
) {
    data class Episode(
        val id: String?,
        val title: String?,
        @SerializedName("episode_num") val episodeNum: String?,
        @SerializedName("container_extension") val containerExtension: String?,
        val info: Info?
    ) {
        data class Info(
            val plot: String?,
            @SerializedName("movie_image") val movieImage: String?,
            val duration: String?
        )
    }
}
