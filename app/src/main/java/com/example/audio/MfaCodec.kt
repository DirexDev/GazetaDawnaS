package com.example.audio

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * MediaFileAudio (.mfa) — Autorski format dźwięku opracowany przez DirexSoft Corporations (Karol Kopeć).
 *
 * Struktura binarna kontenera MFA1:
 * - Magic: "MFA1" (4 bajty)
 * - FormatVersion: Short (1)
 * - CompressionMode: Short (1 = Delta-PCM16 + Deflate ZLIB)
 * - SampleRate: Int (np. 44100 lub 22050 Hz)
 * - Channels: Short (1 = Mono, 2 = Stereo)
 * - BitsPerSample: Short (16)
 * - TotalFrames: Int (liczba ramek audio)
 * - RawPcmBytes: Int (rozmiar nieskompresowanego PCM do statystyk kompresji)
 * - Title, Artist, Album, Creator: UTF-8 stringi
 * - CompressedPayloadLength: Int
 * - CompressedPayload: ByteArray (Delta-encoded PCM-16LE spakowany algorytmem Deflate)
 */
object MfaCodec {
    private const val MAGIC = "MFA1"
    private const val FORMAT_VERSION: Short = 1
    private const val COMPRESSION_DELTA_DEFLATE: Short = 1

    data class MfaMetadata(
        val title: String,
        val artist: String,
        val album: String,
        val creator: String,
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val totalFrames: Int,
        val rawPcmBytes: Int,
        val compressedBytes: Int
    ) {
        val durationMs: Long
            get() = if (sampleRate <= 0) 0L else (totalFrames.toLong() * 1000L) / sampleRate.toLong()

        val compressionRatioPercent: Int
            get() = if (rawPcmBytes <= 0) 0 else (((rawPcmBytes - compressedBytes).toFloat() / rawPcmBytes) * 100f).roundToInt().coerceIn(0, 99)
    }

    data class DecodedMfa(
        val metadata: MfaMetadata,
        val pcmSamples: ShortArray
    )

    fun encodePcm16(
        output: OutputStream,
        pcmSamples: ShortArray,
        sampleRate: Int = 22050,
        channels: Int = 1,
        title: String,
        artist: String,
        album: String = "DirexSoft Studio"
    ): MfaMetadata {
        val totalFrames = pcmSamples.size / channels.coerceAtLeast(1)
        val rawPcmBytes = pcmSamples.size * 2

        // Delta encoding per sample for high Deflate compressibility on audio waveforms
        val deltaBytes = ByteArray(rawPcmBytes)
        var prev = 0
        for (i in pcmSamples.indices) {
            val sample = pcmSamples[i].toInt()
            val delta = (sample - prev).toShort()
            prev = sample
            val pos = i * 2
            deltaBytes[pos] = (delta.toInt() and 0xFF).toByte()
            deltaBytes[pos + 1] = ((delta.toInt() ushr 8) and 0xFF).toByte()
        }

        val compressedStream = ByteArrayOutputStream()
        DeflaterOutputStream(compressedStream, Deflater(Deflater.BEST_COMPRESSION)).use { deflater ->
            deflater.write(deltaBytes)
        }
        val compressedPayload = compressedStream.toByteArray()

        val dos = DataOutputStream(output)
        dos.writeBytes(MAGIC)
        dos.writeShort(FORMAT_VERSION.toInt())
        dos.writeShort(COMPRESSION_DELTA_DEFLATE.toInt())
        dos.writeInt(sampleRate)
        dos.writeShort(channels)
        dos.writeShort(16)
        dos.writeInt(totalFrames)
        dos.writeInt(rawPcmBytes)
        dos.writeUTF(title.take(120))
        dos.writeUTF(artist.take(100))
        dos.writeUTF(album.take(100))
        dos.writeUTF("DirexSoft Corporations / Karol Kopeć")
        dos.writeInt(compressedPayload.size)
        dos.write(compressedPayload)
        dos.flush()

        return MfaMetadata(
            title = title,
            artist = artist,
            album = album,
            creator = "DirexSoft Corporations / Karol Kopeć",
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = 16,
            totalFrames = totalFrames,
            rawPcmBytes = rawPcmBytes,
            compressedBytes = compressedPayload.size
        )
    }

    fun readMetadata(input: InputStream): MfaMetadata {
        val dis = DataInputStream(input)
        val magicBuf = ByteArray(4)
        dis.readFully(magicBuf)
        require(String(magicBuf, Charsets.US_ASCII) == MAGIC) {
            "Nieprawidłowy nagłówek pliku .mfa (oczekiwano MFA1)"
        }
        val version = dis.readShort()
        require(version == FORMAT_VERSION) { "Nieobsługiwana wersja MFA: $version" }
        dis.readShort() // compression mode
        val sampleRate = dis.readInt()
        val channels = dis.readShort().toInt()
        val bitsPerSample = dis.readShort().toInt()
        val totalFrames = dis.readInt()
        val rawPcmBytes = dis.readInt()
        val title = dis.readUTF()
        val artist = dis.readUTF()
        val album = dis.readUTF()
        val creator = dis.readUTF()
        val compressedSize = dis.readInt()
        return MfaMetadata(
            title = title,
            artist = artist,
            album = album,
            creator = creator,
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            totalFrames = totalFrames,
            rawPcmBytes = rawPcmBytes,
            compressedBytes = compressedSize
        )
    }

    fun decode(input: InputStream): DecodedMfa {
        val dis = DataInputStream(input)
        val magicBuf = ByteArray(4)
        dis.readFully(magicBuf)
        require(String(magicBuf, Charsets.US_ASCII) == MAGIC) {
            "Plik nie jest poprawnym formatem MediaFileAudio (.mfa)"
        }
        val version = dis.readShort()
        require(version == FORMAT_VERSION) { "Nieobsługiwana wersja MFA: $version" }
        val compressionMode = dis.readShort()
        val sampleRate = dis.readInt()
        val channels = dis.readShort().toInt()
        val bitsPerSample = dis.readShort().toInt()
        val totalFrames = dis.readInt()
        val rawPcmBytes = dis.readInt()
        val title = dis.readUTF()
        val artist = dis.readUTF()
        val album = dis.readUTF()
        val creator = dis.readUTF()
        val compressedSize = dis.readInt()
        require(compressedSize in 1..(64 * 1024 * 1024)) { "Nieprawidłowy rozmiar strumienia MFA" }

        val compressedPayload = ByteArray(compressedSize)
        dis.readFully(compressedPayload)

        val rawBytes = if (compressionMode == COMPRESSION_DELTA_DEFLATE) {
            InflaterInputStream(ByteArrayInputStream(compressedPayload)).use { it.readBytes() }
        } else {
            compressedPayload
        }

        val sampleCount = rawBytes.size / 2
        val pcmSamples = ShortArray(sampleCount)
        var acc = 0
        for (i in 0 until sampleCount) {
            val pos = i * 2
            val lo = rawBytes[pos].toInt() and 0xFF
            val hi = rawBytes[pos + 1].toInt() shl 8
            val delta = (hi or lo).toShort().toInt()
            acc += delta
            pcmSamples[i] = acc.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        return DecodedMfa(
            metadata = MfaMetadata(
                title = title,
                artist = artist,
                album = album,
                creator = creator,
                sampleRate = sampleRate,
                channels = channels,
                bitsPerSample = bitsPerSample,
                totalFrames = totalFrames,
                rawPcmBytes = rawPcmBytes,
                compressedBytes = compressedSize
            ),
            pcmSamples = pcmSamples
        )
    }

    /**
     * Konwertuje standardowy plik WAV (PCM 16-bit LE) na skompresowany format .mfa!
     */
    fun convertWavToMfa(
        wavInput: InputStream,
        mfaOutput: OutputStream,
        title: String,
        artist: String
    ): MfaMetadata {
        val bytes = wavInput.readBytes()
        require(bytes.size > 44 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF") {
            "Obsługiwana jest konwersja nieskompresowanych plików WAV PCM"
        }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val channels = bb.getShort(22).toInt().coerceIn(1, 2)
        val sampleRate = bb.getInt(24).coerceIn(8000, 96000)
        // Find "data" chunk
        var offset = 12
        var dataStart = 44
        var dataLen = bytes.size - 44
        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = bb.getInt(offset + 4)
            if (chunkId == "data") {
                dataStart = offset + 8
                dataLen = chunkSize.coerceAtMost(bytes.size - dataStart)
                break
            }
            offset += 8 + chunkSize + (chunkSize and 1)
        }
        val sampleCount = (dataLen / 2).coerceAtLeast(1)
        val samples = ShortArray(sampleCount)
        for (i in 0 until sampleCount) {
            samples[i] = bb.getShort(dataStart + i * 2)
        }
        return encodePcm16(
            output = mfaOutput,
            pcmSamples = samples,
            sampleRate = sampleRate,
            channels = channels,
            title = title,
            artist = artist,
            album = "Konwersja WAV -> .MFA"
        )
    }

    /**
     * Generuje wbudowane utwory studyjne .mfa przy pierwszym uruchomieniu, aby można było
     * od razu przetestować odtwarzacz, korektor EQ, wizualizer oraz kompresję PCM-Z.
     */
    fun ensureStarterMfaLibrary(libraryDir: File): List<File> {
        if (!libraryDir.exists()) libraryDir.mkdirs()
        val specs = listOf(
            StarterTrackSpec(
                fileName = "direxsoft_google_plus_theme.mfa",
                title = "Google+ 2014 Stream Pulse",
                artist = "Karol Kopeć • DirexSoft Lab",
                album = "Song/Q Anthology",
                baseFreqs = doubleArrayOf(220.0, 277.18, 329.63, 440.0, 329.63, 277.18),
                durationSec = 18,
                bpm = 118
            ),
            StarterTrackSpec(
                fileName = "songq_retro_synthwave.mfa",
                title = "Flat Square Synthwave",
                artist = "DirexSoft Soundworks",
                album = "MFA PCM-Z Master",
                baseFreqs = doubleArrayOf(146.83, 174.61, 220.0, 261.63, 293.66, 220.0),
                durationSec = 20,
                bpm = 124
            ),
            StarterTrackSpec(
                fileName = "mfa_lofi_analog_warmth.mfa",
                title = "Analog Tape Midnight",
                artist = "Karol Kopeć",
                album = "Late Night Sessions",
                baseFreqs = doubleArrayOf(196.0, 246.94, 293.66, 392.0, 329.63, 246.94),
                durationSec = 22,
                bpm = 92
            ),
            StarterTrackSpec(
                fileName = "eq_reference_sweep.mfa",
                title = "DirexSoft EQ Reference",
                artist = "Song/Q Calibration",
                album = "Acoustic Test Suite",
                baseFreqs = doubleArrayOf(110.0, 220.0, 440.0, 880.0, 1760.0, 440.0),
                durationSec = 16,
                bpm = 100
            )
        )

        return specs.map { spec ->
            val file = File(libraryDir, spec.fileName)
            if (!file.exists() || file.length() < 64) {
                val sampleRate = 22050
                val totalSamples = sampleRate * spec.durationSec
                val pcm = ShortArray(totalSamples)
                val samplesPerBeat = (sampleRate * 60.0 / spec.bpm).toInt().coerceAtLeast(1)
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val beatIdx = (i / samplesPerBeat) % spec.baseFreqs.size
                    val beatProgress = (i % samplesPerBeat).toDouble() / samplesPerBeat
                    val env = if (beatProgress < 0.04) beatProgress / 0.04 else kotlin.math.exp(-3.2 * (beatProgress - 0.04))
                    val freq = spec.baseFreqs[beatIdx]
                    val bassFreq = freq * 0.5
                    val lead = sin(2.0 * PI * freq * t) * 0.45 +
                        sin(2.0 * PI * freq * 2.0 * t) * 0.15 +
                        sin(2.0 * PI * freq * 3.0 * t) * 0.06
                    val bass = sin(2.0 * PI * bassFreq * t) * 0.34
                    val kick = if (beatProgress < 0.12) sin(2.0 * PI * (120.0 - 70.0 * beatProgress * 8) * beatProgress) * 0.25 else 0.0
                    val sample = ((lead * env + bass + kick) * 18000.0).roundToInt()
                    pcm[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                file.outputStream().use { out ->
                    encodePcm16(
                        output = out,
                        pcmSamples = pcm,
                        sampleRate = sampleRate,
                        channels = 1,
                        title = spec.title,
                        artist = spec.artist,
                        album = spec.album
                    )
                }
            }
            file
        }
    }

    private data class StarterTrackSpec(
        val fileName: String,
        val title: String,
        val artist: String,
        val album: String,
        val baseFreqs: DoubleArray,
        val durationSec: Int,
        val bpm: Int
    )
}
