package com.example.audio

import com.example.data.model.PlayableTrack
import com.example.data.model.SourceType
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Song/Q (.sq) — Autorski lekki format playlist firmy DirexSoft Corporations (Karol Kopeć).
 * Przechowuje metadane playlisty oraz odniesienia do lokalnych plików .mfa / audio i linków z sieci,
 * dzięki czemu zapisywanie utworów z wyszukiwarek internetowych nie marnuje pamięci urządzenia.
 */
object SqPlaylistCodec {
    private const val HEADER = "SQ1\n"

    data class SqDocument(
        val name: String,
        val description: String,
        val creator: String = "DirexSoft Song/Q 1.15",
        val tracks: List<PlayableTrack>
    )

    fun serialize(document: SqDocument, output: OutputStream) {
        val root = JSONObject().apply {
            put("format", "SQ1")
            put("generator", "Song/Q 1.15 - DirexSoft Corporations (Karol Kopeć)")
            put("name", document.name)
            put("description", document.description)
            put("creator", document.creator)
            val arr = JSONArray()
            document.tracks.forEachIndexed { index, track ->
                arr.put(
                    JSONObject().apply {
                        put("id", track.id)
                        put("title", track.title)
                        put("artist", track.artist)
                        put("album", track.album)
                        put("sourceType", track.sourceType.name)
                        put("codecLabel", track.codecLabel)
                        put("uriOrUrl", track.uriOrUrl)
                        put("artworkUrl", track.artworkUrl)
                        put("durationMs", track.durationMs)
                        put("order", index)
                    }
                )
            }
            put("tracks", arr)
        }
        output.write(HEADER.toByteArray(Charsets.UTF_8))
        output.write(root.toString(2).toByteArray(Charsets.UTF_8))
        output.flush()
    }

    fun deserialize(input: InputStream, fallbackName: String = "Zaimportowana Playlista"): SqDocument {
        val rawText = input.bufferedReader(Charsets.UTF_8).readText().trim()
        val jsonText = if (rawText.startsWith("SQ1")) {
            rawText.removePrefix("SQ1").trim()
        } else {
            rawText
        }
        val root = JSONObject(jsonText)
        val name = root.optString("name").ifBlank { fallbackName }
        val description = root.optString("description", "")
        val creator = root.optString("creator", "Song/Q .sq")
        val arr = root.optJSONArray("tracks") ?: JSONArray()
        val list = buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val sourceRaw = obj.optString("sourceType", SourceType.WEB_LINK.name)
                val sourceType = SourceType.entries.firstOrNull { it.name == sourceRaw } ?: SourceType.WEB_LINK
                add(
                    PlayableTrack(
                        id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                        title = obj.optString("title", "Bez tytułu"),
                        artist = obj.optString("artist", "Nieznany artysta"),
                        album = obj.optString("album", ""),
                        sourceType = sourceType,
                        codecLabel = obj.optString("codecLabel", if (sourceType == SourceType.MFA) "MFA/PCM-Z" else "SQ-LINK"),
                        uriOrUrl = obj.optString("uriOrUrl", ""),
                        artworkUrl = obj.optString("artworkUrl", ""),
                        durationMs = obj.optLong("durationMs", 0L)
                    )
                )
            }
        }
        return SqDocument(
            name = name,
            description = description,
            creator = creator,
            tracks = list
        )
    }
}
