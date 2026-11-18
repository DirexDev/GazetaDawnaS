package com.example.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File

data class ExtractedAudioInfo(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val codecLabel: String,
    val extension: String
)

object AudioMetadataHelper {

    fun detectCodecBadge(fileNameOrUrl: String, mimeType: String? = null): Pair<String, String> {
        val lower = fileNameOrUrl.lowercase().substringBefore('?')
        val mime = mimeType?.lowercase().orEmpty()
        return when {
            lower.endsWith(".mfa") -> "MFA/PCM-Z" to "mfa"
            lower.endsWith(".flac") || mime.contains("flac") -> "FLAC Hi-Res" to "flac"
            lower.endsWith(".mp3") || mime.contains("mpeg") -> "MP3 320k" to "mp3"
            lower.endsWith(".m4a") || lower.endsWith(".aac") || mime.contains("mp4") || mime.contains("aac") -> "AAC / M4A" to "m4a"
            lower.endsWith(".ogg") || mime.contains("ogg") -> "OGG Vorbis" to "ogg"
            lower.endsWith(".opus") || mime.contains("opus") -> "OPUS Audio" to "opus"
            lower.endsWith(".wav") || mime.contains("wav") -> "WAV PCM" to "wav"
            else -> "AUDIO" to "mp3"
        }
    }

    fun extractFromFile(file: File, fallbackTitle: String = file.nameWithoutExtension): ExtractedAudioInfo {
        val (badge, ext) = detectCodecBadge(file.name)
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(file.absolutePath)
            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.trim()?.takeIf { it.isNotBlank() } ?: fallbackTitle
            val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.trim()?.takeIf { it.isNotBlank() } ?: "Song/Q Lokalny"
            val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.trim()?.takeIf { it.isNotBlank() } ?: "Biblioteka Muzyki"
            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            ExtractedAudioInfo(
                title = title,
                artist = artist,
                album = album,
                durationMs = dur.coerceAtLeast(0L),
                codecLabel = badge,
                extension = ext
            )
        } catch (_: Exception) {
            ExtractedAudioInfo(
                title = fallbackTitle,
                artist = "Song/Q Lokalny",
                album = "Biblioteka Muzyki",
                durationMs = 0L,
                codecLabel = badge,
                extension = ext
            )
        } finally {
            runCatching { mmr.release() }
        }
    }

    fun extractFromUri(context: Context, uri: Uri, displayName: String): ExtractedAudioInfo {
        val (badge, ext) = detectCodecBadge(displayName, context.contentResolver.getType(uri))
        val fallbackTitle = displayName.substringBeforeLast('.').ifBlank { "Utwór Song/Q" }
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.trim()?.takeIf { it.isNotBlank() } ?: fallbackTitle
            val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.trim()?.takeIf { it.isNotBlank() } ?: "Pamięć urządzenia"
            val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.trim()?.takeIf { it.isNotBlank() } ?: "Song/Q Audio"
            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            ExtractedAudioInfo(
                title = title,
                artist = artist,
                album = album,
                durationMs = dur.coerceAtLeast(0L),
                codecLabel = badge,
                extension = ext
            )
        } catch (_: Exception) {
            ExtractedAudioInfo(
                title = fallbackTitle,
                artist = "Pamięć urządzenia",
                album = "Song/Q Audio",
                durationMs = 0L,
                codecLabel = badge,
                extension = ext
            )
        } finally {
            runCatching { mmr.release() }
        }
    }
}
