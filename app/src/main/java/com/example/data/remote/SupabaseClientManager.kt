package com.example.data.remote

import android.content.Context
import com.example.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

sealed class AuthResult {
    data class Success(val userId: String, val email: String) : AuthResult()
    data class NeedsEmailConfirmation(val email: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/**
 * Robust Android SharedPreferences session manager for Supabase auth persistence.
 * Persists session tokens securely and reliably across app restarts.
 */
class AndroidSharedPreferencesSessionManager(context: Context) : SessionManager {
    private val prefs = context.getSharedPreferences("masterkey_supabase_auth_session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveSession(session: UserSession) {
        try {
            val jsonStr = json.encodeToString(UserSession.serializer(), session)
            prefs.edit().putString("auth_session_json", jsonStr).apply()
        } catch (_: Exception) {
            // Ignore serialization exceptions
        }
    }

    override suspend fun loadSession(): UserSession? {
        val jsonStr = prefs.getString("auth_session_json", null) ?: return null
        return try {
            json.decodeFromString(UserSession.serializer(), jsonStr)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun deleteSession() {
        prefs.edit().remove("auth_session_json").apply()
    }
}

class AndroidCodeVerifierCache : io.github.jan.supabase.auth.CodeVerifierCache {
    private var cachedVerifier: String? = null

    override suspend fun saveCodeVerifier(codeVerifier: String) {
        cachedVerifier = codeVerifier
    }

    override suspend fun loadCodeVerifier(): String? {
        return cachedVerifier
    }

    override suspend fun deleteCodeVerifier() {
        cachedVerifier = null
    }
}

class SupabaseClientManager(context: Context) {

    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY
    ) {
        httpEngine = OkHttp.create()
        install(Auth) {
            sessionManager = AndroidSharedPreferencesSessionManager(context.applicationContext)
            codeVerifierCache = AndroidCodeVerifierCache()
        }
        install(Postgrest)
    }

    val sessionStatus: StateFlow<SessionStatus> = client.auth.sessionStatus

    fun isUserLoggedIn(): Boolean {
        return client.auth.currentSessionOrNull() != null
    }

    fun getCurrentUserId(): String? {
        return client.auth.currentUserOrNull()?.id
    }

    fun getCurrentUserEmail(): String? {
        return client.auth.currentUserOrNull()?.email
    }

    suspend fun signUp(email: String, password: CharArray): AuthResult {
        return try {
            client.auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = String(password)
            }

            val session = client.auth.currentSessionOrNull()
            if (session != null) {
                AuthResult.Success(session.user?.id ?: "", email)
            } else {
                AuthResult.NeedsEmailConfirmation(email)
            }
        } catch (e: Exception) {
            val msg = e.message ?: "Sign up failed. Please check credentials or network."
            AuthResult.Error(cleanErrorMessage(msg))
        }
    }

    suspend fun signIn(email: String, password: CharArray): AuthResult {
        return try {
            client.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = String(password)
            }

            val session = client.auth.currentSessionOrNull()
            if (session != null) {
                AuthResult.Success(session.user?.id ?: "", email)
            } else {
                AuthResult.Error("Could not retrieve session after sign in.")
            }
        } catch (e: Exception) {
            val msg = e.message ?: "Login failed. Please check credentials or network."
            AuthResult.Error(cleanErrorMessage(msg))
        }
    }

    suspend fun signOut() {
        try {
            client.auth.signOut()
        } catch (_: Exception) {
            // Ignore signout network errors
        }
    }

    private fun cleanErrorMessage(raw: String): String {
        return when {
            raw.contains("User already registered", ignoreCase = true) ->
                "An account with this email already exists. Please log in instead."
            raw.contains("Invalid login credentials", ignoreCase = true) ->
                "Invalid email or password. Please try again."
            raw.contains("Email not confirmed", ignoreCase = true) ->
                "Please verify your email address before logging in."
            raw.contains("Network", ignoreCase = true) || raw.contains("Unable to resolve host", ignoreCase = true) ->
                "Network connection unavailable. Please check your internet connection."
            else -> raw
        }
    }
}
