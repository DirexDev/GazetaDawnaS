package com.example.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * System aktualizacji Song/Q oparty na plikach instalacyjnych o nazwie:
 * SongQ.<danawersja>.update.apk (np. SongQ.1.16.update.apk).
 * Po wybraniu pliku natychmiast weryfikuje nazwę/wersję i uruchamia instalator pakietów Androida.
 */
object AppUpdater {
    private val UPDATE_NAME_REGEX = Regex("^SongQ\\.([0-9]+(?:\\.[0-9]+)*)\\.update\\.apk$", RegexOption.IGNORE_CASE)

    data class UpdatePackageInfo(
        val fileName: String,
        val targetVersion: String,
        val stagedFile: File,
        val sizeBytes: Long
    )

    sealed interface UpdateLaunchResult {
        data class InstallerLaunched(val info: UpdatePackageInfo) : UpdateLaunchResult
        data class NeedsUnknownSourcesPermission(val info: UpdatePackageInfo) : UpdateLaunchResult
        data class InvalidFile(val reason: String) : UpdateLaunchResult
    }

    fun resolveDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "unknown.apk"
    }

    fun stageAndInstallUpdate(context: Context, uri: Uri): UpdateLaunchResult {
        val fileName = resolveDisplayName(context, uri)
        val match = UPDATE_NAME_REGEX.matchEntire(fileName)
            ?: return UpdateLaunchResult.InvalidFile(
                "Nieprawidłowa nazwa pliku \"$fileName\". Wymagany format: SongQ.<wersja>.update.apk (np. SongQ.1.16.update.apk)."
            )

        val targetVersion = match.groupValues[1]
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val stagedFile = File(updatesDir, fileName)

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                stagedFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return UpdateLaunchResult.InvalidFile("Nie udało się odczytać wybranego pliku APK.")
        } catch (e: Exception) {
            return UpdateLaunchResult.InvalidFile("Błąd kopiowania pakietu aktualizacji: ${e.localizedMessage}")
        }

        if (stagedFile.length() < 128L) {
            return UpdateLaunchResult.InvalidFile("Wybrany plik \"$fileName\" jest pusty lub uszkodzony.")
        }

        val info = UpdatePackageInfo(
            fileName = fileName,
            targetVersion = targetVersion,
            stagedFile = stagedFile,
            sizeBytes = stagedFile.length()
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val permIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(permIntent)
            return UpdateLaunchResult.NeedsUnknownSourcesPermission(info)
        }

        launchApkInstaller(context, stagedFile)
        return UpdateLaunchResult.InstallerLaunched(info)
    }

    fun launchApkInstaller(context: Context, apkFile: File) {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        context.startActivity(installIntent)
    }
}
