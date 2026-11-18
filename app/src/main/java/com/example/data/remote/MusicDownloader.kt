package com.example.data.remote

import android.net.Uri
import com.example.audio.AudioMetadataHelper
import com.example.data.model.PlayableTrack
import com.example.data.model.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class MusicDownloadProgress(
    val title: String,
    val sourceLabel: String,
    val progressPercent: Int,
    val isDownloading: Boolean,
    val statusText: String
)

class MusicDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.tokhmi.xyz",
        "https://api.piped.projectsegfau.lt"
    )

    private val invidiousInstances = listOf(
        "https://inv.nadeko.net",
        "https://iv.ggtyler.dev"
    )

    data class DownloadOutcome(
        val track: PlayableTrack,
        val file: File,
        val sourceNote: String
    )

    /**
     * Pobiera muzykę z podanego linku (YouTube, YouTube Music, bezpośrednie linki MP3/M4A/FLAC/OGG/WAV
     * oraz inne strony muzyczne) lub wg tytułu/wykonawcy i zapisuje lokalnie w pamięci aplikacji.
     */
    suspend fun downloadTrack(
        titleHint: String,
        artistHint: String,
        albumHint: String = "Pobrane w Song/Q",
        targetUrl: String,
        fallbackPreviewUrl: String = "",
        artworkUrl: String = "",
        destDir: File,
        onProgress: (MusicDownloadProgress) -> Unit
    ): Result<DownloadOutcome> = withContext(Dispatchers.IO) {
        destDir.mkdirs()
        val safeTitle = titleHint.trim().ifBlank { "Utwór SongQ" }
        val safeArtist = artistHint.trim().ifBlank { "Song/Q Download" }

        try {
            onProgress(MusicDownloadProgress(safeTitle, "Analiza źródła...", 5, true, "Rozpoznawanie strumienia audio..."))

            val resolvedCandidates = mutableListOf<Pair<String, String>>() // url to sourceNote
            val ytVideoId = extractYouTubeVideoId(targetUrl)

            if (ytVideoId != null) {
                onProgress(MusicDownloadProgress(safeTitle, "YouTube", 12, true, "Pobieranie strumienia audio z YouTube..."))
                resolveYouTubeAudioStream(ytVideoId)?.let { streamUrl ->
                    resolvedCandidates += streamUrl to "Strumień audio YouTube"
                }
            }

            if (targetUrl.startsWith("http", ignoreCase = true) && ytVideoId == null) {
                val lowerUrl = targetUrl.lowercase().substringBefore('?')
                if (lowerUrl.endsWith(".mp3") || lowerUrl.endsWith(".m4a") ||
                    lowerUrl.endsWith(".aac") || lowerUrl.endsWith(".flac") ||
                    lowerUrl.endsWith(".ogg") || lowerUrl.endsWith(".opus") ||
                    lowerUrl.endsWith(".wav") || lowerUrl.endsWith(".mfa")
                ) {
                    resolvedCandidates += targetUrl to "Bezpośredni plik audio"
                } else {
                    onProgress(MusicDownloadProgress(safeTitle, "Strona muzyczna", 15, true, "Skanowanie strony pod kątem strumienia audio..."))
                    extractAudioUrlFromWebPage(targetUrl)?.let { found ->
                        resolvedCandidates += found to "Strumień ze strony muzycznej"
                    }
                }
            }

            // If YouTube / Web URL came from search or didn't yield direct audio, try full-track Audius API + iTunes M4A
            if (resolvedCandidates.isEmpty()) {
                val searchPhrase = "$safeArtist $safeTitle".trim()
                onProgress(MusicDownloadProgress(safeTitle, "Katalog Audio API", 20, true, "Wyszukiwanie strumienia do pobrania..."))
                resolveAudiusFullTrack(searchPhrase)?.let { audiusUrl ->
                    resolvedCandidates += audiusUrl to "Pełny utwór MP3 (Audius Audio)"
                }
                if (fallbackPreviewUrl.isNotBlank()) {
                    resolvedCandidates += fallbackPreviewUrl to "Strumień M4A (Apple/iTunes API)"
                }
                if (ytVideoId == null && resolvedCandidates.isEmpty()) {
                    // Try finding a YouTube audio stream via Piped search for query
                    searchYouTubeAudioByQuery(searchPhrase)?.let { ytStream ->
                        resolvedCandidates += ytStream to "Strumień audio YouTube"
                    }
                }
            } else if (fallbackPreviewUrl.isNotBlank()) {
                resolvedCandidates += fallbackPreviewUrl to "Strumień M4A (API)"
            }

            if (resolvedCandidates.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Nie znaleziono bezpośredniego strumienia audio dla \"$safeTitle\". Wklej bezpośredni link do pliku audio lub link YouTube.")
                )
            }

            var lastError: Throwable? = null
            for ((streamUrl, note) in resolvedCandidates) {
                val downloadRes = performHttpFileDownload(
                    streamUrl = streamUrl,
                    title = safeTitle,
                    artist = safeArtist,
                    album = albumHint,
                    artworkUrl = artworkUrl,
                    sourceNote = note,
                    destDir = destDir,
                    onProgress = onProgress
                )
                if (downloadRes.isSuccess) {
                    return@withContext downloadRes
                } else {
                    lastError = downloadRes.exceptionOrNull()
                }
            }

            Result.failure(lastError ?: IllegalStateException("Pobieranie audio nie powiodło się."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun performHttpFileDownload(
        streamUrl: String,
        title: String,
        artist: String,
        album: String,
        artworkUrl: String,
        sourceNote: String,
        destDir: File,
        onProgress: (MusicDownloadProgress) -> Unit
    ): Result<DownloadOutcome> {
        return try {
            val req = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile) SongQ/1.15")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return Result.failure(IllegalStateException("Serwer zwrócił kod ${resp.code}"))
                }
                val body = resp.body ?: return Result.failure(IllegalStateException("Pusta odpowiedź strumienia"))
                val contentType = resp.header("Content-Type")?.lowercase().orEmpty()
                if (contentType.contains("text/html")) {
                    return Result.failure(IllegalStateException("Adres zwrócił stronę HTML zamiast strumienia audio"))
                }

                val (_, ext) = AudioMetadataHelper.detectCodecBadge(streamUrl, contentType)
                val cleanBase = "${artist}_${title}"
                    .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
                    .trim()
                    .replace(Regex("\\s+"), "_")
                    .take(48)
                    .ifBlank { "songq_track" }
                val destFile = File(destDir, "${cleanBase}_${System.currentTimeMillis() % 100000}.$ext")

                val totalBytes = body.contentLength()
                var downloaded = 0L
                val buf = ByteArray(8192)
                var lastPct = -1

                body.byteStream().use { input ->
                    destFile.outputStream().use { output ->
                        while (true) {
                            val read = input.read(buf)
                            if (read == -1) break
                            output.write(buf, 0, read)
                            downloaded += read
                            val pct = if (totalBytes > 0L) {
                                ((downloaded * 100L) / totalBytes).toInt().coerceIn(5, 99)
                            } else {
                                (25 + ((downloaded / 32768L) % 70)).toInt()
                            }
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(
                                    MusicDownloadProgress(
                                        title = title,
                                        sourceLabel = sourceNote,
                                        progressPercent = pct,
                                        isDownloading = true,
                                        statusText = "Pobieranie ($pct%) • ${downloaded / 1024} KB"
                                    )
                                )
                            }
                        }
                    }
                }

                if (destFile.length() < 4096L) {
                    destFile.delete()
                    return Result.failure(IllegalStateException("Pobrany plik jest zbyt mały lub pusty"))
                }

                val meta = AudioMetadataHelper.extractFromFile(destFile, fallbackTitle = title)
                val track = PlayableTrack(
                    id = "dl_${destFile.name}",
                    title = meta.title.ifBlank { title },
                    artist = if (meta.artist == "Song/Q Lokalny") artist else meta.artist,
                    album = album.ifBlank { meta.album },
                    sourceType = SourceType.LOCAL,
                    codecLabel = meta.codecLabel,
                    uriOrUrl = destFile.absolutePath,
                    artworkUrl = artworkUrl,
                    durationMs = meta.durationMs
                )
                onProgress(
                    MusicDownloadProgress(
                        title = track.title,
                        sourceLabel = sourceNote,
                        progressPercent = 100,
                        isDownloading = false,
                        statusText = "Pobrano: ${destFile.name}"
                    )
                )
                Result.success(DownloadOutcome(track, destFile, sourceNote))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractYouTubeVideoId(url: String): String? {
        if (!url.contains("youtu", ignoreCase = true)) return null
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val vParam = uri.getQueryParameter("v")
        if (!vParam.isNullOrBlank()) return vParam
        val host = uri.host?.lowercase().orEmpty()
        if (host.contains("youtu.be")) {
            return uri.lastPathSegment?.takeIf { it.isNotBlank() }
        }
        val segments = uri.pathSegments
        val shortsIdx = segments.indexOf("shorts")
        if (shortsIdx >= 0 && shortsIdx + 1 < segments.size) {
            return segments[shortsIdx + 1]
        }
        return null
    }

    private fun resolveYouTubeAudioStream(videoId: String): String? {
        for (base in pipedInstances) {
            val stream = runCatching {
                val req = Request.Builder()
                    .url("$base/streams/$videoId")
                    .header("User-Agent", "SongQ/1.15")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val audioStreams = json.optJSONArray("audioStreams") ?: return@runCatching null
                    pickBestAudioUrlFromJsonArray(audioStreams)
                }
            }.getOrNull()
            if (!stream.isNullOrBlank()) return stream
        }

        for (base in invidiousInstances) {
            val stream = runCatching {
                val req = Request.Builder()
                    .url("$base/api/v1/videos/$videoId")
                    .header("User-Agent", "SongQ/1.15")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val formats = json.optJSONArray("adaptiveFormats") ?: return@runCatching null
                    var bestUrl: String? = null
                    var bestBitrate = -1
                    for (i in 0 until formats.length()) {
                        val obj = formats.optJSONObject(i) ?: continue
                        val type = obj.optString("type").lowercase()
                        if (type.startsWith("audio/")) {
                            val bitrate = obj.optString("bitrate").toIntOrNull() ?: 0
                            val url = obj.optString("url")
                            if (url.startsWith("http") && bitrate >= bestBitrate) {
                                bestBitrate = bitrate
                                bestUrl = url
                            }
                        }
                    }
                    bestUrl
                }
            }.getOrNull()
            if (!stream.isNullOrBlank()) return stream
        }
        return null
    }

    private fun searchYouTubeAudioByQuery(query: String): String? {
        val encoded = Uri.encode(query)
        for (base in pipedInstances) {
            val videoId = runCatching {
                val req = Request.Builder()
                    .url("$base/search?q=$encoded&filter=music_songs")
                    .header("User-Agent", "SongQ/1.15")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val items = json.optJSONArray("items") ?: return@runCatching null
                    val first = items.optJSONObject(0) ?: return@runCatching null
                    val itemUrl = first.optString("url")
                    itemUrl.substringAfter("v=").substringBefore('&').takeIf { it.isNotBlank() }
                }
            }.getOrNull()
            if (!videoId.isNullOrBlank()) {
                resolveYouTubeAudioStream(videoId)?.let { return it }
            }
        }
        return null
    }

    private fun pickBestAudioUrlFromJsonArray(arr: JSONArray): String? {
        var bestUrl: String? = null
        var bestScore = -1
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val url = obj.optString("url")
            if (!url.startsWith("http")) continue
            val mime = obj.optString("mimeType").lowercase()
            val bitrate = obj.optInt("bitrate", 0)
            // Prefer m4a / audio/mp4 for universal MediaPlayer compatibility on Android 8+
            val bonus = if (mime.contains("mp4") || mime.contains("m4a")) 500000 else 0
            val score = bitrate + bonus
            if (score > bestScore) {
                bestScore = score
                bestUrl = url
            }
        }
        return bestUrl
    }

    private fun resolveAudiusFullTrack(query: String): String? {
        return runCatching {
            val encoded = Uri.encode(query)
            val req = Request.Builder()
                .url("https://discoveryprovider.audius.co/v1/tracks/search?query=$encoded&app_name=SongQ")
                .header("User-Agent", "SongQ/1.15")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val json = JSONObject(resp.body?.string().orEmpty())
                val data = json.optJSONArray("data") ?: return@runCatching null
                val first = data.optJSONObject(0) ?: return@runCatching null
                val trackId = first.optString("id")
                if (trackId.isBlank()) null
                else "https://discoveryprovider.audius.co/v1/tracks/$trackId/stream?app_name=SongQ"
            }
        }.getOrNull()
    }

    private fun extractAudioUrlFromWebPage(pageUrl: String): String? {
        return runCatching {
            val req = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile) SongQ/1.15")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val ctype = resp.header("Content-Type")?.lowercase().orEmpty()
                if (ctype.startsWith("audio/")) return@runCatching pageUrl
                val html = resp.body?.string()?.take(200_000).orEmpty()
                val regex = Regex("""https?://[^\s"'<>]+?\.(?:mp3|m4a|flac|ogg|aac|wav)(?:\?[^\s"'<>]*)?""", RegexOption.IGNORE_CASE)
                regex.find(html)?.value
            }
        }.getOrNull()
    }
}
