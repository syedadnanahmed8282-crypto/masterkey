package com.example.data.repository

import com.example.data.model.DecryptedCredential
import com.example.data.model.PendingCredential
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class SyncStatus(val label: String) {
    SYNCED("Synced"),
    SYNCING("Syncing..."),
    OFFLINE("Offline (Local Cache)"),
    ERROR("Sync Error")
}

/**
 * Universal repository interface for MasterKey credentials.
 * ViewModels, UI screens, and the Autofill service interact strictly with this interface.
 *
 * - Phase 1: Local storage via Room and Android Keystore AES-256-GCM.
 * - Phase 2: Autofill service matching and pending save queue management.
 * - Phase 3: Zero-knowledge remote synchronization via Supabase ([SupabaseCredentialRepository]),
 *   with Room as local offline cache and server-side last-write-wins conflict resolution.
 */
interface CredentialRepository {

    /**
     * Current synchronization state with the remote Supabase backend.
     */
    val syncStatus: StateFlow<SyncStatus>

    /**
     * Emits a reactive stream of all decrypted credentials, ordered by last updated time.
     */
    fun getDecryptedCredentialsStream(): Flow<List<DecryptedCredential>>

    /**
     * Retrieves a single decrypted credential by its unique ID, or null if not found.
     */
    suspend fun getDecryptedCredentialById(id: Long): DecryptedCredential?

    /**
     * Encrypts and saves a new credential with the active DEK.
     */
    suspend fun saveCredential(credential: DecryptedCredential): Long

    /**
     * Re-encrypts and updates an existing credential with a fresh nonce.
     */
    suspend fun updateCredential(credential: DecryptedCredential)

    /**
     * Marks credential as deleted and syncs soft-delete to Supabase.
     */
    suspend fun deleteCredential(id: Long)

    /**
     * Searches for saved credentials matching a package name or web domain.
     */
    suspend fun findMatchingCredentials(packageName: String, webDomain: String?): List<DecryptedCredential>

    /**
     * Triggers bidirectional synchronization with Supabase when online.
     */
    suspend fun triggerSync()

    /**
     * Clears local cached credentials and pending items (used upon log out).
     */
    suspend fun clearLocalData()

    // --- Pending Autofill Captures Queue ---

    fun getPendingCredentialsStream(): Flow<List<PendingCredential>>

    suspend fun savePendingCredential(title: String, appId: String?, username: String, password: String): Long

    suspend fun commitPendingCredential(pendingId: Long, customTitle: String? = null): Long

    suspend fun dismissPendingCredential(pendingId: Long)
}
