package com.example.data.remote

import android.net.Uri
import com.example.data.model.ArtistInfoResult
import com.example.data.model.SearchEngineType
import com.example.data.model.WebTrackResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.text.Normalizer
import java.util.concurrent.TimeUnit

class WebSearchRepository {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val itunesApi: ItunesSearchApi = Retrofit.Builder()
        .baseUrl("https://itunes.apple.com/")
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(ItunesSearchApi::class.java)

    private val musicBrainzApi: MusicBrainzApi = Retrofit.Builder()
        .baseUrl("https://musicbrainz.org/ws/2/")
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(MusicBrainzApi::class.java)

    suspend fun searchTracks(query: String, engine: SearchEngineType): Result<List<WebTrackResult>> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return@withContext Result.success(emptyList())
            try {
                val response = itunesApi.searchTracks(term = trimmed, limit = 35)
                val encoded = Uri.encode(trimmed)
                val apiTracks = response.results.mapNotNull { dto ->
                    val title = dto.trackName?.trim() ?: return@mapNotNull null
                    val artist = dto.artistName?.trim() ?: "Nieznany artysta"
                    val engineSpecificUrl = when (engine) {
                        SearchEngineType.ITUNES -> dto.trackViewUrl ?: "${engine.baseSearchUrl}$encoded"
                        SearchEngineType.MUSICBRAINZ -> "https://musicbrainz.org/search?type=recording&query=${Uri.encode("$artist $title")}"
                        SearchEngineType.YOUTUBE -> "https://www.youtube.com/results?search_query=${Uri.encode("$artist - $title")}"
                        SearchEngineType.YOUTUBE_MUSIC -> "https://music.youtube.com/search?q=${Uri.encode("$artist $title")}"
                        SearchEngineType.SPOTIFY -> "https://open.spotify.com/search/${Uri.encode("$artist $title")}"
                        SearchEngineType.SOUNDCLOUD -> "https://soundcloud.com/search?q=${Uri.encode("$artist $title")}"
                        SearchEngineType.BANDCAMP -> "https://bandcamp.com/search?q=${Uri.encode("$artist $title")}"
                    }
                    WebTrackResult(
                        id = (dto.trackId ?: title.hashCode().toLong()).toString(),
                        title = title,
                        artist = artist,
                        album = dto.collectionName?.trim() ?: "",
                        genre = dto.primaryGenreName?.trim() ?: "Muzyka",
                        previewUrl = dto.previewUrl ?: "",
                        externalUrl = engineSpecificUrl,
                        artworkUrl = dto.artworkUrl100?.replace("100x100bb", "300x300bb") ?: "",
                        durationMs = dto.trackTimeMillis ?: 30000L,
                        engine = engine
                    )
                }

                val ranked = rankAndDeduplicateByRelevance(apiTracks, trimmed)
                Result.success(ranked)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun rankAndDeduplicateByRelevance(
        tracks: List<WebTrackResult>,
        rawQuery: String
    ): List<WebTrackResult> {
        val normQuery = normalizeForMatch(rawQuery)
        val tokens = normQuery.split(' ').filter { it.length >= 2 }.distinct()
        val wantsCoverOrKaraoke = normQuery.contains("karaoke") ||
            normQuery.contains("cover") ||
            normQuery.contains("instrumental") ||
            normQuery.contains("tribute")

        val scored = tracks.map { item ->
            val normTitle = normalizeForMatch(item.title)
            val normArtist = normalizeForMatch(item.artist)
            val normAlbum = normalizeForMatch(item.album)
            val combined = "$normArtist $normTitle"
            val combinedRev = "$normTitle $normArtist"

            var score = 0
            if (normTitle == normQuery) score += 1200
            if (combined == normQuery || combinedRev == normQuery) score += 1000
            if (normTitle.startsWith(normQuery)) score += 550
            if (normTitle.contains(normQuery)) score += 420
            if (normArtist == normQuery) score += 380
            if (normArtist.contains(normQuery)) score += 260
            if (normAlbum.contains(normQuery)) score += 120

            if (tokens.isNotEmpty()) {
                val titleHits = tokens.count { normTitle.contains(it) }
                val artistHits = tokens.count { normArtist.contains(it) }
                val allText = "$normTitle $normArtist $normAlbum"
                val allHits = tokens.count { allText.contains(it) }

                score += titleHits * 110
                score += artistHits * 75
                if (allHits == tokens.size) score += 300
                if (titleHits == tokens.size) score += 250
            }

            // Shorter clean title wins tie-breaks (prefer original single/album title over long remixes)
            score -= (normTitle.length - normQuery.length).coerceAtLeast(0).coerceAtMost(80)

            // Playable 30s preview bonus
            if (item.previewUrl.isNotBlank()) score += 65

            // Demote karaoke/tribute clones unless explicitly searched
            if (!wantsCoverOrKaraoke) {
                val lowerJoined = "$normTitle $normArtist $normAlbum"
                if (lowerJoined.contains("karaoke") ||
                    lowerJoined.contains("tribute") ||
                    lowerJoined.contains("ringtone") ||
                    lowerJoined.contains("made famous") ||
                    lowerJoined.contains("instrumental version")
                ) {
                    score -= 350
                }
            }
            item to score
        }

        // Deduplicate near-identical (title + artist), keeping the highest-scored result
        return scored
            .sortedByDescending { it.second }
            .distinctBy { (item, _) ->
                "${normalizeForMatch(item.title)}::${normalizeForMatch(item.artist)}"
            }
            .map { it.first }
    }

    private fun normalizeForMatch(input: String): String {
        val replacedPolish = input
            .replace('ł', 'l')
            .replace('Ł', 'L')
        val decomposed = Normalizer.normalize(replacedPolish, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    suspend fun searchArtists(query: String): Result<List<ArtistInfoResult>> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return@withContext Result.success(emptyList())
            try {
                val res = musicBrainzApi.searchArtists(query = trimmed)
                val list = res.artists.map { dto ->
                    ArtistInfoResult(
                        id = dto.id,
                        name = dto.name,
                        country = dto.country ?: "Świat",
                        type = dto.type ?: "Artysta",
                        disambiguation = dto.disambiguation ?: "",
                        tags = dto.tags?.sortedByDescending { it.count }?.take(5)?.map { it.name } ?: emptyList(),
                        externalUrl = "https://musicbrainz.org/artist/${dto.id}"
                    )
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun buildSearchUrl(query: String, engine: SearchEngineType): String {
        val trimmed = query.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        return "${engine.baseSearchUrl}${Uri.encode(trimmed)}"
    }
}

