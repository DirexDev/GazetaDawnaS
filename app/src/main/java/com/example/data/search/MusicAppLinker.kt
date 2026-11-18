package com.example.data.search

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build

data class MusicAppTarget(
    val id: String,
    val label: String,
    val subtitle: String,
    val packageName: String?,
    val isInstalled: Boolean,
    val icon: Drawable? = null,
    val buildQueryUrl: (String) -> String
)

object MusicAppLinker {

    private data class KnownPlatformSpec(
        val id: String,
        val label: String,
        val packageName: String,
        val subtitle: String,
        val buildUrl: (String) -> String
    )

    private val knownPlatforms = listOf(
        KnownPlatformSpec(
            id = "ytm",
            label = "YouTube Music",
            packageName = "com.google.android.apps.youtube.music",
            subtitle = "Aplikacja / Serwis YT Music",
            buildUrl = { q -> "https://music.youtube.com/search?q=${Uri.encode(q)}" }
        ),
        KnownPlatformSpec(
            id = "yt",
            label = "YouTube",
            packageName = "com.google.android.youtube",
            subtitle = "Wideo & Teledyski YouTube",
            buildUrl = { q -> "https://www.youtube.com/results?search_query=${Uri.encode(q)}" }
        ),
        KnownPlatformSpec(
            id = "spotify",
            label = "Spotify",
            packageName = "com.spotify.music",
            subtitle = "Streaming Spotify",
            buildUrl = { q -> "https://open.spotify.com/search/${Uri.encode(q)}" }
        ),
        KnownPlatformSpec(
            id = "soundcloud",
            label = "SoundCloud",
            packageName = "com.soundcloud.android",
            subtitle = "Odkrycia & Remixy SoundCloud",
            buildUrl = { q -> "https://soundcloud.com/search?q=${Uri.encode(q)}" }
        ),
        KnownPlatformSpec(
            id = "apple_music",
            label = "Apple Music",
            packageName = "com.apple.android.music",
            subtitle = "Katalog Apple Music / iTunes",
            buildUrl = { q -> "https://music.apple.com/search?term=${Uri.encode(q)}" }
        ),
        KnownPlatformSpec(
            id = "deezer",
            label = "Deezer",
            packageName = "deezer.android.app",
            subtitle = "Deezer HiFi & Playlisty",
            buildUrl = { q -> "https://www.deezer.com/search/${Uri.encode(q)}" }
        ),
        KnownPlatformSpec(
            id = "tidal",
            label = "TIDAL",
            packageName = "com.aspiro.tidal",
            subtitle = "Bezstratny dźwięk TIDAL",
            buildUrl = { q -> "https://listen.tidal.com/search?q=${Uri.encode(q)}" }
        ),
        KnownPlatformSpec(
            id = "bandcamp",
            label = "Bandcamp",
            packageName = "com.bandcamp.android",
            subtitle = "Niezależni artyści Bandcamp",
            buildUrl = { q -> "https://bandcamp.com/search?q=${Uri.encode(q)}" }
        )
    )

    fun discoverMusicApps(context: Context): List<MusicAppTarget> {
        val pm = context.packageManager
        val discovered = mutableListOf<MusicAppTarget>()
        val seenPackages = mutableSetOf(context.packageName)

        // 1. Check major music platforms declared in <queries>
        for (spec in knownPlatforms) {
            val appInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(spec.packageName, PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(spec.packageName, 0)
                }
            }.getOrNull()

            val icon = appInfo?.let { runCatching { pm.getApplicationIcon(it) }.getOrNull() }
            if (appInfo != null) {
                seenPackages += spec.packageName
            }
            discovered += MusicAppTarget(
                id = spec.id,
                label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: spec.label,
                subtitle = if (appInfo != null) "Zainstalowana aplikacja • ${spec.subtitle}" else "Otwórz w przeglądarce • ${spec.subtitle}",
                packageName = if (appInfo != null) spec.packageName else null,
                isInstalled = appInfo != null,
                icon = icon,
                buildQueryUrl = spec.buildUrl
            )
        }

        // 2. Discover any other installed audio/* handler apps on the device
        val audioProbe = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://songq/probe.mp3"), "audio/*")
        }
        val audioHandlers = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(audioProbe, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(audioProbe, 0)
            }
        }.getOrDefault(emptyList())

        for (info in audioHandlers) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (!seenPackages.add(pkg)) continue
            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
            val icon = runCatching { info.loadIcon(pm) }.getOrNull()
            discovered += MusicAppTarget(
                id = "audio_$pkg",
                label = label,
                subtitle = "Zainstalowany odtwarzacz audio ($pkg)",
                packageName = pkg,
                isInstalled = true,
                icon = icon,
                buildQueryUrl = { q -> "https://www.youtube.com/results?search_query=${Uri.encode(q)}" }
            )
        }

        return discovered.sortedByDescending { it.isInstalled }
    }

    fun openInTargetApp(
        context: Context,
        target: MusicAppTarget,
        queryOrTitle: String,
        fallbackUrl: String? = null
    ): Boolean {
        val url = target.buildQueryUrl(queryOrTitle).ifBlank { fallbackUrl.orEmpty() }
        if (target.packageName != null) {
            val targeted = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage(target.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val launchedTargeted = runCatching {
                context.startActivity(targeted)
                true
            }.getOrDefault(false)
            if (launchedTargeted) return true

            val launchIntent = context.packageManager.getLaunchIntentForPackage(target.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (runCatching { context.startActivity(launchIntent); true }.getOrDefault(false)) {
                    return true
                }
            }
        }

        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(browserIntent)
            true
        }.getOrDefault(false)
    }

    fun openSystemMusicChooser(context: Context, titleOrQuery: String, targetUrl: String) {
        val uri = Uri.parse(targetUrl.ifBlank { "https://music.youtube.com/search?q=${Uri.encode(titleOrQuery)}" })
        val intent = Intent(Intent.ACTION_VIEW, uri)
        val chooser = Intent.createChooser(intent, "Otwórz \"$titleOrQuery\" w aplikacji muzycznej").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }
}
