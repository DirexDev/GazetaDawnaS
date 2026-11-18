package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.ActivityLogDoc
import com.example.data.model.PlayableTrack
import com.example.data.model.SqPlaylistDoc
import com.example.data.model.SqTrackDoc
import com.example.data.model.UserProfileDoc
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class SongQRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Użytkownik musi być zalogowany przez Google do Song/Q.")
    }

    suspend fun ensureUserProfile(
        displayName: String,
        email: String?,
        photoUrl: String?,
        preferredPreset: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Brak sesji Google"))
        val docPath = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            val safeName = displayName.trim().ifBlank { "Użytkownik Song/Q" }.take(80)
            val safePreset = preferredPreset.trim().ifBlank { "Studio Flat" }.take(40)
            if (!snap.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", safeName)
                    if (!email.isNullOrBlank()) put("email", email.take(160))
                    if (!photoUrl.isNullOrBlank()) put("photoUrl", photoUrl.take(500))
                    put("preferredPreset", safePreset)
                    put("appVersion", "1.15")
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            } else {
                val updateMap = buildMap<String, Any> {
                    put("displayName", safeName)
                    if (!email.isNullOrBlank()) put("email", email.take(160))
                    if (!photoUrl.isNullOrBlank()) put("photoUrl", photoUrl.take(500))
                    put("appVersion", "1.15")
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updatePreferredPreset(userId: String, presetName: String): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Niezgodność użytkownika sesji" }
            val safePreset = presetName.trim().ifBlank { "Studio Flat" }.take(40)
            db.collection("users").document(uid)
                .update(
                    mapOf(
                        "preferredPreset" to safePreset,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun observePlaylists(userId: String): Flow<List<SqPlaylistDoc>> = flow {
        val path = "users/$userId/playlists"
        emitAll(
            db.collection("users").document(userId).collection("playlists")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SqPlaylistDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createPlaylist(
        userId: String,
        name: String,
        description: String
    ): Result<String> {
        var docPath = "users/$userId/playlists"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Niezgodność użytkownika sesji" }
            val cleanName = name.trim().ifBlank { "Playlista Song/Q" }.take(100)
            val cleanDesc = description.trim().take(300)
            val docRef = db.collection("users").document(uid).collection("playlists").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "name" to cleanName,
                "description" to cleanDesc,
                "formatVersion" to "SQ1",
                "trackCount" to 0L,
                "totalDurationMs" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deletePlaylist(userId: String, playlistId: String): Result<Unit> {
        val docPath = "users/$userId/playlists/$playlistId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Niezgodność użytkownika sesji" }
            require(playlistId.isNotBlank())
            db.collection("users").document(uid)
                .collection("playlists").document(playlistId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun observePlaylistTracks(userId: String, playlistId: String): Flow<List<SqTrackDoc>> = flow {
        val path = "users/$userId/playlists/$playlistId/tracks"
        emitAll(
            db.collection("users").document(userId)
                .collection("playlists").document(playlistId)
                .collection("tracks")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SqTrackDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.orderIndex }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addTrackToPlaylist(
        userId: String,
        playlistId: String,
        track: PlayableTrack,
        orderIndex: Long
    ): Result<Unit> {
        var docPath = "users/$userId/playlists/$playlistId/tracks"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Niezgodność użytkownika sesji" }
            val playlistRef = db.collection("users").document(uid)
                .collection("playlists").document(playlistId)
            val trackRef = playlistRef.collection("tracks").document()
            docPath = trackRef.path

            val safeDuration = track.durationMs.coerceAtLeast(0L)
            val safeOrder = orderIndex.coerceIn(0L, 5000L)
            val payload = mapOf(
                "userId" to uid,
                "playlistId" to playlistId,
                "title" to track.title.trim().ifBlank { "Bez tytułu" }.take(160),
                "artist" to track.artist.trim().ifBlank { "Nieznany artysta" }.take(120),
                "album" to track.album.trim().take(120),
                "sourceType" to track.sourceType.name,
                "codecLabel" to track.codecLabel.trim().ifBlank { "SQ-LINK" }.take(32),
                "uriOrUrl" to track.uriOrUrl.trim().ifBlank { "songq://track/${track.id}" }.take(1000),
                "artworkUrl" to track.artworkUrl.trim().take(1000),
                "durationMs" to safeDuration,
                "orderIndex" to safeOrder,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            trackRef.set(payload).await()
            playlistRef.update(
                mapOf(
                    "trackCount" to FieldValue.increment(1L),
                    "totalDurationMs" to FieldValue.increment(safeDuration),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTrackOrder(
        userId: String,
        playlistId: String,
        trackId: String,
        newOrderIndex: Long
    ): Result<Unit> {
        val docPath = "users/$userId/playlists/$playlistId/tracks/$trackId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Niezgodność użytkownika sesji" }
            db.collection("users").document(uid)
                .collection("playlists").document(playlistId)
                .collection("tracks").document(trackId)
                .update(
                    mapOf(
                        "orderIndex" to newOrderIndex.coerceIn(0L, 5000L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTrackFromPlaylist(
        userId: String,
        playlistId: String,
        trackId: String,
        durationMs: Long
    ): Result<Unit> {
        val docPath = "users/$userId/playlists/$playlistId/tracks/$trackId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Niezgodność użytkownika sesji" }
            val playlistRef = db.collection("users").document(uid)
                .collection("playlists").document(playlistId)
            playlistRef.collection("tracks").document(trackId).delete().await()
            val snap = playlistRef.get().await()
            val curCount = (snap.getLong("trackCount") ?: 1L).coerceAtLeast(1L)
            val curDur = (snap.getLong("totalDurationMs") ?: durationMs).coerceAtLeast(durationMs)
            playlistRef.update(
                mapOf(
                    "trackCount" to (curCount - 1L).coerceAtLeast(0L),
                    "totalDurationMs" to (curDur - durationMs.coerceAtLeast(0L)).coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun observeActivity(userId: String): Flow<List<ActivityLogDoc>> = flow {
        val path = "users/$userId/activity"
        emitAll(
            db.collection("users").document(userId).collection("activity")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ActivityLogDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt }.take(40)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun logActivity(
        userId: String,
        queryOrTitle: String,
        artistName: String,
        engine: String,
        targetUrl: String
    ): Result<Unit> {
        var docPath = "users/$userId/activity"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Niezgodność użytkownika sesji" }
            val allowedEngines = setOf(
                "ITUNES", "MUSICBRAINZ", "YOUTUBE", "YOUTUBE_MUSIC",
                "SPOTIFY", "SOUNDCLOUD", "BANDCAMP", "MFA_LOCAL", "SQ_PLAYLIST"
            )
            val safeEngine = if (engine in allowedEngines) engine else "MFA_LOCAL"
            val docRef = db.collection("users").document(uid).collection("activity").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "queryOrTitle" to queryOrTitle.trim().ifBlank { "Aktywność Song/Q" }.take(160),
                "artistName" to artistName.trim().take(120),
                "engine" to safeEngine,
                "targetUrl" to targetUrl.trim().take(1000),
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }
}
