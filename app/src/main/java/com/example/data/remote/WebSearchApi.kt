package com.example.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class ItunesSearchResponse(
    val resultCount: Int = 0,
    val results: List<ItunesTrackDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ItunesTrackDto(
    val trackId: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val collectionName: String? = null,
    val primaryGenreName: String? = null,
    val previewUrl: String? = null,
    val trackViewUrl: String? = null,
    val artworkUrl100: String? = null,
    val trackTimeMillis: Long? = null
)

interface ItunesSearchApi {
    @GET("search")
    suspend fun searchTracks(
        @Query("term") term: String,
        @Query("media") media: String = "music",
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 25
    ): ItunesSearchResponse
}

@JsonClass(generateAdapter = true)
data class MusicBrainzArtistResponse(
    val artists: List<MusicBrainzArtistDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MusicBrainzArtistDto(
    val id: String = "",
    val name: String = "",
    val type: String? = null,
    val country: String? = null,
    val disambiguation: String? = null,
    val tags: List<MusicBrainzTagDto>? = null
)

@JsonClass(generateAdapter = true)
data class MusicBrainzTagDto(
    val name: String = "",
    val count: Int = 0
)

interface MusicBrainzApi {
    @GET("artist/")
    suspend fun searchArtists(
        @Query("query") query: String,
        @Query("fmt") format: String = "json",
        @Query("limit") limit: Int = 10,
        @Header("User-Agent") userAgent: String = "SongQ/1.15 (DirexSoft Corporations; kopeckarol463@gmail.com)"
    ): MusicBrainzArtistResponse
}
