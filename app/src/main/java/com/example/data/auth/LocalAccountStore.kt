package com.example.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

private val Context.localAuthStore by preferencesDataStore(name = "songq_local_accounts")

data class LocalSongQAccount(
    val id: String,
    val login: String,
    val displayName: String,
    val passwordHash: String,
    val saltHex: String
)

class LocalAccountStore(private val context: Context) {
    private val keyAccountsJson = stringPreferencesKey("registered_accounts_json")
    private val keyActiveAccountId = stringPreferencesKey("active_account_id")

    val activeAccountFlow: Flow<LocalSongQAccount?> = context.localAuthStore.data.map { prefs ->
        val activeId = prefs[keyActiveAccountId] ?: return@map null
        val list = parseAccounts(prefs[keyAccountsJson])
        list.firstOrNull { it.id == activeId }
    }

    suspend fun register(
        login: String,
        displayName: String,
        password: String
    ): Result<LocalSongQAccount> {
        val cleanLogin = login.trim().lowercase()
        val cleanName = displayName.trim().ifBlank { cleanLogin.substringBefore('@') }
        if (cleanLogin.length < 3) {
            return Result.failure(IllegalArgumentException("Login lub e-mail musi mieć co najmniej 3 znaki."))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Hasło musi mieć co najmniej 6 znaków."))
        }

        val prefs = context.localAuthStore.data.first()
        val existing = parseAccounts(prefs[keyAccountsJson])
        if (existing.any { it.login.equals(cleanLogin, ignoreCase = true) }) {
            return Result.failure(IllegalStateException("Konto o takim loginie lub adresie e-mail już istnieje."))
        }

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        val hash = sha256Hex(salt, password)
        val newAccount = LocalSongQAccount(
            id = "sq_${UUID.randomUUID().toString().replace("-", "").take(16)}",
            login = cleanLogin,
            displayName = cleanName.take(80),
            passwordHash = hash,
            saltHex = salt
        )

        val updated = existing + newAccount
        context.localAuthStore.edit { editPrefs ->
            editPrefs[keyAccountsJson] = serializeAccounts(updated)
            editPrefs[keyActiveAccountId] = newAccount.id
        }
        return Result.success(newAccount)
    }

    suspend fun login(login: String, password: String): Result<LocalSongQAccount> {
        val cleanLogin = login.trim().lowercase()
        if (cleanLogin.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Podaj login i hasło."))
        }
        val prefs = context.localAuthStore.data.first()
        val accounts = parseAccounts(prefs[keyAccountsJson])
        val account = accounts.firstOrNull { it.login.equals(cleanLogin, ignoreCase = true) }
            ?: return Result.failure(IllegalArgumentException("Nie znaleziono konta \"$cleanLogin\". Zarejestruj się najpierw."))

        val candidateHash = sha256Hex(account.saltHex, password)
        if (candidateHash != account.passwordHash) {
            return Result.failure(IllegalArgumentException("Nieprawidłowe hasło."))
        }

        context.localAuthStore.edit { editPrefs ->
            editPrefs[keyActiveAccountId] = account.id
        }
        return Result.success(account)
    }

    suspend fun signOutLocal() {
        context.localAuthStore.edit { it.remove(keyActiveAccountId) }
    }

    private fun parseAccounts(raw: String?): List<LocalSongQAccount> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        LocalSongQAccount(
                            id = obj.optString("id"),
                            login = obj.optString("login"),
                            displayName = obj.optString("displayName"),
                            passwordHash = obj.optString("passwordHash"),
                            saltHex = obj.optString("saltHex")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeAccounts(accounts: List<LocalSongQAccount>): String {
        val arr = JSONArray()
        accounts.forEach { acc ->
            arr.put(
                JSONObject().apply {
                    put("id", acc.id)
                    put("login", acc.login)
                    put("displayName", acc.displayName)
                    put("passwordHash", acc.passwordHash)
                    put("saltHex", acc.saltHex)
                }
            )
        }
        return arr.toString()
    }

    private fun sha256Hex(saltHex: String, password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$saltHex:$password".toByteArray(Charsets.UTF_8))
        return bytes.toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
