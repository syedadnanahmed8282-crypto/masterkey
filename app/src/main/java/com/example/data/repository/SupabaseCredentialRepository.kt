package com.example.data.repository

import android.util.Base64
import com.example.data.local.CredentialDao
import com.example.data.model.Credential
import com.example.data.model.DecryptedCredential
import com.example.data.model.PendingCredential
import com.example.data.model.SecretPayload
import com.example.data.remote.SupabaseClientManager
import com.example.data.remote.model.RemoteCredentialDto
import com.example.data.security.CryptoManager
import com.example.data.security.MasterKeyManager
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class SupabaseCredentialRepository(
    private val credentialDao: CredentialDao,
    private val cryptoManager: CryptoManager,
    private val masterKeyManager: MasterKeyManager,
    private val supabaseManager: SupabaseClientManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CredentialRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _syncStatus = MutableStateFlow(SyncStatus.SYNCED)
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    override fun getDecryptedCredentialsStream(): Flow<List<DecryptedCredential>> {
        return credentialDao.getAllCredentials()
            .map { entities ->
                entities.mapNotNull { entity ->
                    decryptEntitySafely(entity)
                }
            }
            .flowOn(ioDispatcher)
    }

    override suspend fun getDecryptedCredentialById(id: Long): DecryptedCredential? = withContext(ioDispatcher) {
        val entity = credentialDao.getCredentialById(id) ?: return@withContext null
        decryptEntitySafely(entity)
    }

    override suspend fun saveCredential(credential: DecryptedCredential): Long = withContext(ioDispatcher) {
        val dek = getOrUnsealDEK()
        val payload = SecretPayload(
            username = credential.username,
            password = credential.password,
            notes = credential.notes
        )

        // Encrypt with DEK (Phase 3 zero-knowledge)
        val (ciphertext, nonce) = if (dek != null) {
            cryptoManager.encryptWithDEK(payload, dek)
        } else {
            cryptoManager.encrypt(payload)
        }

        val now = System.currentTimeMillis()
        val syncId = if (credential.syncId.isNotBlank()) credential.syncId else UUID.randomUUID().toString()

        val entity = Credential(
            id = 0,
            syncId = syncId,
            userId = supabaseManager.getCurrentUserId(),
            title = credential.title.trim(),
            appId = credential.appId?.trim()?.ifEmpty { null },
            encryptedData = ciphertext,
            nonce = nonce,
            createdAt = now,
            updatedAt = now,
            serverUpdatedAt = null,
            isPendingSync = true,
            isDeleted = false,
            encryptionVersion = if (dek != null) 2 else 1
        )

        val insertedId = credentialDao.insertCredential(entity)
        triggerSyncAsync()
        insertedId
    }

    override suspend fun updateCredential(credential: DecryptedCredential): Unit = withContext(ioDispatcher) {
        val dek = getOrUnsealDEK()
        val payload = SecretPayload(
            username = credential.username,
            password = credential.password,
            notes = credential.notes
        )

        val (ciphertext, nonce) = if (dek != null) {
            cryptoManager.encryptWithDEK(payload, dek)
        } else {
            cryptoManager.encrypt(payload)
        }

        val existing = credentialDao.getCredentialById(credential.id)
        val createdAt = existing?.createdAt ?: credential.createdAt
        val syncId = existing?.syncId ?: if (credential.syncId.isNotBlank()) credential.syncId else UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val entity = Credential(
            id = credential.id,
            syncId = syncId,
            userId = supabaseManager.getCurrentUserId(),
            title = credential.title.trim(),
            appId = credential.appId?.trim()?.ifEmpty { null },
            encryptedData = ciphertext,
            nonce = nonce,
            createdAt = createdAt,
            updatedAt = now,
            serverUpdatedAt = existing?.serverUpdatedAt,
            isPendingSync = true,
            isDeleted = false,
            encryptionVersion = if (dek != null) 2 else 1
        )

        credentialDao.updateCredential(entity)
        triggerSyncAsync()
    }

    override suspend fun deleteCredential(id: Long): Unit = withContext(ioDispatcher) {
        val existing = credentialDao.getCredentialById(id)
        if (existing != null) {
            if (supabaseManager.isUserLoggedIn()) {
                // Soft delete locally and mark for sync deletion
                credentialDao.markDeleted(id)
                triggerSyncAsync()
            } else {
                // Hard delete locally if no remote sync is active
                credentialDao.deleteCredentialById(id)
            }
        }
    }

    override suspend fun findMatchingCredentials(
        packageName: String,
        webDomain: String?
    ): List<DecryptedCredential> = withContext(ioDispatcher) {
        val entities = credentialDao.getAllActiveCredentialsList()
        val cleanPkg = packageName.trim().lowercase()

        val cleanDomain = webDomain?.trim()?.lowercase()
            ?.removePrefix("https://")
            ?.removePrefix("http://")
            ?.removePrefix("www.")
            ?.substringBefore('/')
            ?.substringBefore(':')

        val matchedEntities = entities.filter { entity ->
            val storedAppId = entity.appId?.trim()?.lowercase()
            val storedTitle = entity.title.trim().lowercase()

            when {
                storedAppId != null && storedAppId == cleanPkg -> true
                cleanDomain != null && storedAppId != null &&
                        (storedAppId.contains(cleanDomain) || cleanDomain.contains(storedAppId)) -> true
                cleanDomain != null && (cleanDomain.contains(storedTitle) || storedTitle.contains(cleanDomain.substringBefore('.'))) -> true
                cleanPkg.contains(storedTitle) -> true
                else -> false
            }
        }

        matchedEntities.mapNotNull { decryptEntitySafely(it) }
    }

    override suspend fun triggerSync(): Unit = withContext(ioDispatcher) {
        if (!supabaseManager.isUserLoggedIn()) {
            _syncStatus.value = SyncStatus.OFFLINE
            return@withContext
        }

        val dek = getOrUnsealDEK()
        if (dek == null) {
            _syncStatus.value = SyncStatus.OFFLINE
            return@withContext
        }

        _syncStatus.value = SyncStatus.SYNCING

        try {
            val userId = supabaseManager.getCurrentUserId() ?: return@withContext

            // 1. Push pending local mutations to Supabase
            val pendingSyncList = credentialDao.getPendingSyncCredentials()
            for (local in pendingSyncList) {
                if (local.isDeleted) {
                    // Push soft delete to Supabase
                    supabaseManager.client.from("credentials").update(
                        mapOf("deleted_at" to java.time.Instant.now().toString())
                    ) {
                        filter {
                            eq("id", local.syncId)
                            eq("user_id", userId)
                        }
                    }
                    credentialDao.deleteCredentialById(local.id)
                } else {
                    // Upload / upsert active credential
                    val remoteDto = RemoteCredentialDto(
                        id = local.syncId,
                        userId = userId,
                        title = local.title,
                        appId = local.appId,
                        encryptedData = Base64.encodeToString(local.encryptedData, Base64.NO_WRAP),
                        nonce = Base64.encodeToString(local.nonce, Base64.NO_WRAP),
                        deletedAt = null
                    )

                    supabaseManager.client.from("credentials").upsert(remoteDto) {
                        select()
                    }

                    // Mark as synced locally
                    credentialDao.updateCredential(local.copy(isPendingSync = false))
                }
            }

            // 2. Pull remote credentials from Supabase
            val remoteCredentials = supabaseManager.client.from("credentials")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<RemoteCredentialDto>()

            for (remote in remoteCredentials) {
                val localMatch = credentialDao.getCredentialBySyncId(remote.id)
                if (remote.deletedAt != null) {
                    // Remote was soft-deleted, remove from local cache
                    if (localMatch != null) {
                        credentialDao.deleteCredentialById(localMatch.id)
                    }
                } else {
                    val rawCipher = Base64.decode(remote.encryptedData, Base64.NO_WRAP)
                    val rawNonce = Base64.decode(remote.nonce, Base64.NO_WRAP)

                    if (localMatch == null) {
                        // New credential from another device: insert locally
                        val newEntity = Credential(
                            id = 0,
                            syncId = remote.id,
                            userId = userId,
                            title = remote.title,
                            appId = remote.appId,
                            encryptedData = rawCipher,
                            nonce = rawNonce,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            serverUpdatedAt = remote.updatedAt,
                            isPendingSync = false,
                            isDeleted = false,
                            encryptionVersion = 2
                        )
                        credentialDao.insertCredential(newEntity)
                    } else if (!localMatch.isPendingSync) {
                        // Conflict resolution: last-write-wins using server updated_at
                        if (remote.updatedAt != null && remote.updatedAt != localMatch.serverUpdatedAt) {
                            credentialDao.updateCredential(
                                localMatch.copy(
                                    title = remote.title,
                                    appId = remote.appId,
                                    encryptedData = rawCipher,
                                    nonce = rawNonce,
                                    serverUpdatedAt = remote.updatedAt,
                                    isPendingSync = false
                                )
                            )
                        }
                    }
                }
            }

            masterKeyManager.updateLastSyncTimestamp()
            _syncStatus.value = SyncStatus.SYNCED
        } catch (_: Exception) {
            _syncStatus.value = SyncStatus.OFFLINE
        }
    }

    private fun triggerSyncAsync() {
        repositoryScope.launch {
            triggerSync()
        }
    }

    override suspend fun clearLocalData(): Unit = withContext(ioDispatcher) {
        credentialDao.deleteAllCredentials()
        credentialDao.deleteAllPendingCredentials()
        _syncStatus.value = SyncStatus.SYNCED
    }

    override fun getPendingCredentialsStream(): Flow<List<PendingCredential>> {
        return credentialDao.getPendingCredentialsStream().flowOn(ioDispatcher)
    }

    override suspend fun savePendingCredential(
        title: String,
        appId: String?,
        username: String,
        password: String
    ): Long = withContext(ioDispatcher) {
        val encryptedPassword = cryptoManager.encryptPendingPassword(password)
        val pending = PendingCredential(
            id = 0,
            title = title.trim(),
            appId = appId?.trim()?.ifEmpty { null },
            username = username.trim(),
            encryptedPassword = encryptedPassword,
            createdAt = System.currentTimeMillis()
        )
        credentialDao.insertPendingCredential(pending)
    }

    override suspend fun commitPendingCredential(
        pendingId: Long,
        customTitle: String?
    ): Long = withContext(ioDispatcher) {
        val pending = credentialDao.getPendingCredentialById(pendingId) ?: return@withContext -1L
        val decryptedPassword = cryptoManager.decryptPendingPassword(pending.encryptedPassword)
        val finalTitle = customTitle?.trim()?.ifEmpty { null } ?: pending.title

        val allDecrypted = credentialDao.getAllActiveCredentialsList().mapNotNull { decryptEntitySafely(it) }
        val existingMatch = allDecrypted.find {
            val sameUsername = it.username.equals(pending.username, ignoreCase = true)
            val sameApp = (it.appId != null && pending.appId != null && it.appId.equals(pending.appId, ignoreCase = true)) ||
                    it.title.equals(finalTitle, ignoreCase = true)
            sameUsername && sameApp
        }

        val resultId = if (existingMatch != null) {
            if (existingMatch.password != decryptedPassword) {
                updateCredential(
                    existingMatch.copy(
                        password = decryptedPassword,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            existingMatch.id
        } else {
            saveCredential(
                DecryptedCredential(
                    id = 0,
                    title = finalTitle,
                    appId = pending.appId,
                    username = pending.username,
                    password = decryptedPassword,
                    notes = "Saved via MasterKey Autofill"
                )
            )
        }

        credentialDao.deletePendingCredentialById(pendingId)
        resultId
    }

    override suspend fun dismissPendingCredential(pendingId: Long): Unit = withContext(ioDispatcher) {
        credentialDao.deletePendingCredentialById(pendingId)
    }

    /**
     * Decrypts an entity using the active DEK (for Phase 3) or falls back to local Keystore (Phase 1/2).
     * If an older Phase 1/2 credential is found, it is automatically re-encrypted with the DEK.
     */
    private fun decryptEntitySafely(entity: Credential): DecryptedCredential? {
        val dek = getOrUnsealDEK()

        return try {
            val payload = if (entity.encryptionVersion == 2 && dek != null) {
                cryptoManager.decryptWithDEK(entity.encryptedData, entity.nonce, dek)
            } else {
                // Phase 1/2 local Keystore fallback & automatic migration
                val oldPayload = cryptoManager.decrypt(entity.encryptedData, entity.nonce)
                if (dek != null && entity.encryptionVersion == 1) {
                    // Migrate to Phase 3 DEK encryption in background
                    repositoryScope.launch {
                        val (newCipher, newNonce) = cryptoManager.encryptWithDEK(oldPayload, dek)
                        credentialDao.updateCredential(
                            entity.copy(
                                encryptedData = newCipher,
                                nonce = newNonce,
                                encryptionVersion = 2,
                                isPendingSync = true
                            )
                        )
                        triggerSync()
                    }
                }
                oldPayload
            }

            DecryptedCredential(
                id = entity.id,
                syncId = entity.syncId,
                title = entity.title,
                appId = entity.appId,
                username = payload.username,
                password = payload.password,
                notes = payload.notes,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                isPendingSync = entity.isPendingSync
            )
        } catch (_: Exception) {
            DecryptedCredential(
                id = entity.id,
                syncId = entity.syncId,
                title = entity.title,
                appId = entity.appId,
                username = "[Encrypted]",
                password = "",
                notes = "Encrypted entry. Authenticate with Master Password or Biometrics.",
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                isPendingSync = entity.isPendingSync
            )
        }
    }

    private fun getOrUnsealDEK(): ByteArray? {
        val active = masterKeyManager.getActiveDEK()
        if (active != null) return active
        return masterKeyManager.unsealDEKWithKeystore()
    }
}
