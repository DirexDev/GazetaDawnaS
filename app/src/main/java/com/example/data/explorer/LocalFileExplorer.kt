package com.example.data.explorer

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.example.audio.MfaCodec
import com.example.audio.SqPlaylistCodec
import com.example.data.model.PlayableTrack
import com.example.data.model.SourceType
import java.io.File

enum class ExplorerFilterMode(val label: String) {
    ALL_SUPPORTED(".MFA + .SQ"),
    MFA_ONLY("Tylko .MFA"),
    SQ_ONLY("Tylko .SQ")
}

sealed interface ExplorerEntry {
    val id: String
    val name: String
    val subtitle: String
    val sizeBytes: Long
    val lastModifiedMs: Long

    data class Folder(
        override val id: String,
        override val name: String,
        override val subtitle: String,
        override val sizeBytes: Long = 0L,
        override val lastModifiedMs: Long = 0L,
        val filePath: String? = null,
        val documentUri: String? = null,
        val childFilterHint: String = "Folder"
    ) : ExplorerEntry

    data class MfaFile(
        override val id: String,
        override val name: String,
        override val subtitle: String,
        override val sizeBytes: Long,
        override val lastModifiedMs: Long,
        val uriOrPath: String,
        val isContentUri: Boolean,
        val trackTitle: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val sampleRate: Int,
        val compressionRatioPercent: Int
    ) : ExplorerEntry {
        fun toPlayableTrack(): PlayableTrack = PlayableTrack(
            id = "exp_mfa_${id.hashCode()}",
            title = trackTitle,
            artist = artist,
            album = album,
            sourceType = SourceType.MFA,
            codecLabel = "MFA/PCM-Z (-$compressionRatioPercent%)",
            uriOrUrl = uriOrPath,
            durationMs = durationMs,
            sampleRate = sampleRate,
            compressionRatioPercent = compressionRatioPercent
        )
    }

    data class SqPlaylistFile(
        override val id: String,
        override val name: String,
        override val subtitle: String,
        override val sizeBytes: Long,
        override val lastModifiedMs: Long,
        val uriOrPath: String,
        val isContentUri: Boolean,
        val playlistName: String,
        val description: String,
        val trackCount: Int
    ) : ExplorerEntry
}

data class ExplorerBreadcrumb(
    val label: String,
    val filePath: String? = null,
    val treeUri: String? = null,
    val documentId: String? = null
)

object LocalFileExplorer {

    fun getAppRoots(context: Context): List<ExplorerBreadcrumb> {
        val roots = mutableListOf<ExplorerBreadcrumb>()
        val filesRoot = context.filesDir
        roots += ExplorerBreadcrumb(
            label = "Pamięć Song/Q",
            filePath = filesRoot.absolutePath
        )
        val extMusic = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        if (extMusic != null) {
            extMusic.mkdirs()
            roots += ExplorerBreadcrumb(
                label = "Muzyka Song/Q (Zewn.)",
                filePath = extMusic.absolutePath
            )
        }
        val extRoot = context.getExternalFilesDir(null)
        if (extRoot != null && extRoot.absolutePath != extMusic?.absolutePath) {
            extRoot.mkdirs()
            roots += ExplorerBreadcrumb(
                label = "Katalog Aplikacji",
                filePath = extRoot.absolutePath
            )
        }
        return roots
    }

    fun listLocalDirectory(
        directory: File,
        filterMode: ExplorerFilterMode,
        query: String = "",
        recursive: Boolean = false
    ): List<ExplorerEntry> {
        if (!directory.exists() || !directory.isDirectory) return emptyList()
        val results = mutableListOf<ExplorerEntry>()

        if (recursive) {
            scanFileTreeRecursive(directory, filterMode, query.trim(), results, depth = 0)
        } else {
            val children = directory.listFiles()?.toList().orEmpty()
            val folders = children.filter { it.isDirectory && !it.name.startsWith(".") }
                .sortedBy { it.name.lowercase() }
            for (dir in folders) {
                if (query.isNotBlank() && !dir.name.contains(query, ignoreCase = true)) continue
                val supportedInside = countSupportedInDir(dir)
                results += ExplorerEntry.Folder(
                    id = "dir_${dir.absolutePath}",
                    name = dir.name,
                    subtitle = if (supportedInside > 0) {
                        "$supportedInside plików .mfa / .sq w środku"
                    } else {
                        "Folder lokalny"
                    },
                    lastModifiedMs = dir.lastModified(),
                    filePath = dir.absolutePath
                )
            }

            val files = children.filter { it.isFile }
                .sortedBy { it.name.lowercase() }
            for (file in files) {
                parseLocalSupportedFile(file, filterMode, query.trim())?.let { results += it }
            }
        }
        return results
    }

    private fun scanFileTreeRecursive(
        dir: File,
        filterMode: ExplorerFilterMode,
        query: String,
        out: MutableList<ExplorerEntry>,
        depth: Int
    ) {
        if (depth > 5 || out.size >= 250) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory && !child.name.startsWith(".")) {
                scanFileTreeRecursive(child, filterMode, query, out, depth + 1)
            } else if (child.isFile) {
                parseLocalSupportedFile(child, filterMode, query)?.let { out += it }
            }
        }
    }

    private fun parseLocalSupportedFile(
        file: File,
        filterMode: ExplorerFilterMode,
        query: String
    ): ExplorerEntry? {
        val lower = file.name.lowercase()
        val isMfa = lower.endsWith(".mfa")
        val isSq = lower.endsWith(".sq")
        if (!isMfa && !isSq) return null
        if (filterMode == ExplorerFilterMode.MFA_ONLY && !isMfa) return null
        if (filterMode == ExplorerFilterMode.SQ_ONLY && !isSq) return null

        if (isMfa) {
            val meta = runCatching { file.inputStream().use { MfaCodec.readMetadata(it) } }.getOrNull()
            val title = meta?.title ?: file.nameWithoutExtension
            val artist = meta?.artist ?: "Song/Q MediaFileAudio"
            if (query.isNotBlank() &&
                !file.name.contains(query, ignoreCase = true) &&
                !title.contains(query, ignoreCase = true) &&
                !artist.contains(query, ignoreCase = true)
            ) return null

            return ExplorerEntry.MfaFile(
                id = "mfa_${file.absolutePath}",
                name = file.name,
                subtitle = "$artist • ${formatFileSize(file.length())}" +
                    if (meta != null) " • -${meta.compressionRatioPercent}% PCM-Z" else "",
                sizeBytes = file.length(),
                lastModifiedMs = file.lastModified(),
                uriOrPath = file.absolutePath,
                isContentUri = false,
                trackTitle = title,
                artist = artist,
                album = meta?.album ?: "Song/Q Local",
                durationMs = meta?.durationMs ?: 0L,
                sampleRate = meta?.sampleRate ?: 22050,
                compressionRatioPercent = meta?.compressionRatioPercent ?: 0
            )
        } else {
            val doc = runCatching {
                file.inputStream().use { SqPlaylistCodec.deserialize(it, file.nameWithoutExtension) }
            }.getOrNull()
            val plName = doc?.name ?: file.nameWithoutExtension
            if (query.isNotBlank() &&
                !file.name.contains(query, ignoreCase = true) &&
                !plName.contains(query, ignoreCase = true)
            ) return null

            val count = doc?.tracks?.size ?: 0
            return ExplorerEntry.SqPlaylistFile(
                id = "sq_${file.absolutePath}",
                name = file.name,
                subtitle = "Playlista .SQ • $count utworów • ${formatFileSize(file.length())}",
                sizeBytes = file.length(),
                lastModifiedMs = file.lastModified(),
                uriOrPath = file.absolutePath,
                isContentUri = false,
                playlistName = plName,
                description = doc?.description ?: "",
                trackCount = count
            )
        }
    }

    private fun countSupportedInDir(dir: File): Int {
        val children = dir.listFiles() ?: return 0
        return children.count {
            it.isFile && (it.name.endsWith(".mfa", ignoreCase = true) || it.name.endsWith(".sq", ignoreCase = true))
        }
    }

    fun listSafTreeDirectory(
        context: Context,
        treeUri: Uri,
        documentId: String? = null,
        filterMode: ExplorerFilterMode,
        query: String = "",
        recursive: Boolean = false
    ): List<ExplorerEntry> {
        val rootDocId = documentId ?: DocumentsContract.getTreeDocumentId(treeUri)
        val out = mutableListOf<ExplorerEntry>()
        if (recursive) {
            scanSafRecursive(context, treeUri, rootDocId, filterMode, query.trim(), out, depth = 0)
        } else {
            querySafFolder(context, treeUri, rootDocId, filterMode, query.trim(), out)
        }
        return out.sortedWith(
            compareBy<ExplorerEntry> { it !is ExplorerEntry.Folder }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun scanSafRecursive(
        context: Context,
        treeUri: Uri,
        docId: String,
        filterMode: ExplorerFilterMode,
        query: String,
        out: MutableList<ExplorerEntry>,
        depth: Int
    ) {
        if (depth > 4 || out.size >= 250) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                val idIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (c.moveToNext()) {
                    val childId = c.getString(idIdx) ?: continue
                    val name = c.getString(nameIdx) ?: continue
                    val mime = c.getString(mimeIdx) ?: ""
                    val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
                    val mod = if (modIdx >= 0) c.getLong(modIdx) else 0L
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        scanSafRecursive(context, treeUri, childId, filterMode, query, out, depth + 1)
                    } else {
                        parseSafFile(context, treeUri, childId, name, size, mod, filterMode, query)?.let {
                            out += it
                        }
                    }
                }
            }
        }
    }

    private fun querySafFolder(
        context: Context,
        treeUri: Uri,
        docId: String,
        filterMode: ExplorerFilterMode,
        query: String,
        out: MutableList<ExplorerEntry>
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                val idIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (c.moveToNext()) {
                    val childId = c.getString(idIdx) ?: continue
                    val name = c.getString(nameIdx) ?: continue
                    val mime = c.getString(mimeIdx) ?: ""
                    val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
                    val mod = if (modIdx >= 0) c.getLong(modIdx) else 0L
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (query.isBlank() || name.contains(query, ignoreCase = true)) {
                            out += ExplorerEntry.Folder(
                                id = "saf_dir_$childId",
                                name = name,
                                subtitle = "Folder pamięci urządzenia (SAF)",
                                lastModifiedMs = mod,
                                documentUri = childId
                            )
                        }
                    } else {
                        parseSafFile(context, treeUri, childId, name, size, mod, filterMode, query)?.let {
                            out += it
                        }
                    }
                }
            }
        }
    }

    private fun parseSafFile(
        context: Context,
        treeUri: Uri,
        childId: String,
        name: String,
        size: Long,
        lastMod: Long,
        filterMode: ExplorerFilterMode,
        query: String
    ): ExplorerEntry? {
        val lower = name.lowercase()
        val isMfa = lower.endsWith(".mfa")
        val isSq = lower.endsWith(".sq")
        if (!isMfa && !isSq) return null
        if (filterMode == ExplorerFilterMode.MFA_ONLY && !isMfa) return null
        if (filterMode == ExplorerFilterMode.SQ_ONLY && !isSq) return null

        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
        if (isMfa) {
            val meta = runCatching {
                context.contentResolver.openInputStream(docUri)?.use { MfaCodec.readMetadata(it) }
            }.getOrNull()
            val title = meta?.title ?: name.substringBeforeLast('.')
            val artist = meta?.artist ?: "Song/Q .MFA"
            if (query.isNotBlank() &&
                !name.contains(query, ignoreCase = true) &&
                !title.contains(query, ignoreCase = true) &&
                !artist.contains(query, ignoreCase = true)
            ) return null

            return ExplorerEntry.MfaFile(
                id = "saf_mfa_$childId",
                name = name,
                subtitle = "$artist • ${formatFileSize(size)}" +
                    if (meta != null) " • -${meta.compressionRatioPercent}% PCM-Z" else "",
                sizeBytes = size,
                lastModifiedMs = lastMod,
                uriOrPath = docUri.toString(),
                isContentUri = true,
                trackTitle = title,
                artist = artist,
                album = meta?.album ?: "Pamięć urządzenia",
                durationMs = meta?.durationMs ?: 0L,
                sampleRate = meta?.sampleRate ?: 22050,
                compressionRatioPercent = meta?.compressionRatioPercent ?: 0
            )
        } else {
            val doc = runCatching {
                context.contentResolver.openInputStream(docUri)?.use {
                    SqPlaylistCodec.deserialize(it, name.substringBeforeLast('.'))
                }
            }.getOrNull()
            val plName = doc?.name ?: name.substringBeforeLast('.')
            if (query.isNotBlank() &&
                !name.contains(query, ignoreCase = true) &&
                !plName.contains(query, ignoreCase = true)
            ) return null

            val count = doc?.tracks?.size ?: 0
            return ExplorerEntry.SqPlaylistFile(
                id = "saf_sq_$childId",
                name = name,
                subtitle = "Playlista .SQ • $count utworów • ${formatFileSize(size)}",
                sizeBytes = size,
                lastModifiedMs = lastMod,
                uriOrPath = docUri.toString(),
                isContentUri = true,
                playlistName = plName,
                description = doc?.description ?: "",
                trackCount = count
            )
        }
    }

    fun readSqTracksFromEntry(context: Context, entry: ExplorerEntry.SqPlaylistFile): List<PlayableTrack> {
        return runCatching {
            val doc = if (entry.isContentUri) {
                context.contentResolver.openInputStream(Uri.parse(entry.uriOrPath))?.use {
                    SqPlaylistCodec.deserialize(it, entry.playlistName)
                }
            } else {
                File(entry.uriOrPath).inputStream().use {
                    SqPlaylistCodec.deserialize(it, entry.playlistName)
                }
            }
            doc?.tracks.orEmpty()
        }.getOrDefault(emptyList())
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
            else -> "%.1f MB".format(bytes / (1024f * 1024f))
        }
    }
}
