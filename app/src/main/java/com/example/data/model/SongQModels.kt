package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class SourceType {
    MFA,
    LOCAL,
    WEB_LINK,
    ITUNES_PREVIEW
}

enum class SearchEngineType(
    val displayName: String,
    val badge: String,
    val baseSearchUrl: String
) {
    ITUNES("iTunes / Web API", "API", "https://music.apple.com/search?term="),
    MUSICBRAINZ("MusicBrainz Artysta", "BIO", "https://musicbrainz.org/search?type=artist&query="),
    YOUTUBE("YouTube", "YT", "https://www.youtube.com/results?search_query="),
    YOUTUBE_MUSIC("YouTube Music", "YTM", "https://music.youtube.com/search?q="),
    SPOTIFY("Spotify", "SPT", "https://open.spotify.com/search/"),
    SOUNDCLOUD("SoundCloud", "SC", "https://soundcloud.com/search?q="),
    BANDCAMP("Bandcamp", "BC", "https://bandcamp.com/search?q=")
}

data class PlayableTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val sourceType: SourceType = SourceType.MFA,
    val codecLabel: String = "MFA/PCM-Z",
    val uriOrUrl: String = "",
    val artworkUrl: String = "",
    val durationMs: Long = 0L,
    val sampleRate: Int = 22050,
    val compressionRatioPercent: Int = 0
)

data class EqBandState(
    val index: Int,
    val centerFreqHz: Int,
    val label: String,
    val levelDb: Float
)

data class EqPreset(
    val name: String,
    val bandGainsDb: List<Float>,
    val bassDb: Float,
    val midsDb: Float,
    val trebleDb: Float,
    val bassBoostStrength: Int,
    val virtualizerStrength: Int,
    val preampDb: Float
)

data class WebTrackResult(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val previewUrl: String,
    val externalUrl: String,
    val artworkUrl: String,
    val durationMs: Long,
    val engine: SearchEngineType
)

data class ArtistInfoResult(
    val id: String,
    val name: String,
    val country: String,
    val type: String,
    val disambiguation: String,
    val tags: List<String>,
    val externalUrl: String
)

// --- Firestore Models (Strictly aligned with firestore.rules & firebase-blueprint.json) ---

@Keep
@IgnoreExtraProperties
data class UserProfileDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    val preferredPreset: String = "Studio Flat",
    val appVersion: String = "1.15",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SqPlaylistDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val formatVersion: String = "SQ1",
    val trackCount: Long = 0L,
    val totalDurationMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SqTrackDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val playlistId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val sourceType: String = SourceType.WEB_LINK.name,
    val codecLabel: String = "SQ-LINK",
    val uriOrUrl: String = "",
    val artworkUrl: String = "",
    val durationMs: Long = 0L,
    val orderIndex: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toPlayableTrack(): PlayableTrack {
        val resolvedSource = SourceType.entries.firstOrNull { it.name == sourceType } ?: SourceType.WEB_LINK
        return PlayableTrack(
            id = id,
            title = title,
            artist = artist,
            album = album,
            sourceType = resolvedSource,
            codecLabel = codecLabel,
            uriOrUrl = uriOrUrl,
            artworkUrl = artworkUrl,
            durationMs = durationMs
        )
    }
}

@Keep
@IgnoreExtraProperties
data class ActivityLogDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val queryOrTitle: String = "",
    val artistName: String = "",
    val engine: String = "MFA_LOCAL",
    val targetUrl: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
