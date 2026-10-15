package com.example.data.repository

import com.example.data.local.CredentialDao
import com.example.data.model.Credential
import com.example.data.model.DecryptedCredential
import com.example.data.model.PendingCredential
import com.example.data.model.SecretPayload
import com.example.data.security.CryptoManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Phase 1 & 2 implementation of [CredentialRepository] using Room database,
 * AES-256-GCM hardware encryption for vault entries, and asymmetric RSA-2048
 * encryption for background autofill captures.
 */
class RoomCredentialRepository(
    private val credentialDao: CredentialDao,
    private val cryptoManager: CryptoManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CredentialRepository {

    override val syncStatus: kotlinx.coroutines.flow.StateFlow<SyncStatus> =
        kotlinx.coroutines.flow.MutableStateFlow(SyncStatus.OFFLINE)

    override suspend fun triggerSync(): Unit = withContext(ioDispatcher) {
        // Local-only repository implementation: no remote sync
    }

    override suspend fun clearLocalData(): Unit = withContext(ioDispatcher) {
        credentialDao.deleteAllCredentials()
        credentialDao.deleteAllPendingCredentials()
    }

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
        val payload = SecretPayload(
            username = credential.username,
            password = credential.password,
            notes = credential.notes
        )

        // Encrypt with fresh random 12-byte nonce
        val (ciphertext, nonce) = cryptoManager.encrypt(payload)

        val now = System.currentTimeMillis()
        val entity = Credential(
            id = 0,
            title = credential.title.trim(),
            appId = credential.appId?.trim()?.ifEmpty { null },
            encryptedData = ciphertext,
            nonce = nonce,
            createdAt = now,
            updatedAt = now
        )

        credentialDao.insertCredential(entity)
    }

    override suspend fun updateCredential(credential: DecryptedCredential): Unit = withContext(ioDispatcher) {
        val payload = SecretPayload(
            username = credential.username,
            password = credential.password,
            notes = credential.notes
        )

        // Fresh random 12-byte nonce per encryption
        val (ciphertext, nonce) = cryptoManager.encrypt(payload)

        val existing = credentialDao.getCredentialById(credential.id)
        val createdAt = existing?.createdAt ?: credential.createdAt
        val now = System.currentTimeMillis()

        val entity = Credential(
            id = credential.id,
            title = credential.title.trim(),
            appId = credential.appId?.trim()?.ifEmpty { null },
            encryptedData = ciphertext,
            nonce = nonce,
            createdAt = createdAt,
            updatedAt = now
        )

        credentialDao.updateCredential(entity)
    }

    override suspend fun deleteCredential(id: Long): Unit = withContext(ioDispatcher) {
        credentialDao.deleteCredentialById(id)
    }

    override suspend fun findMatchingCredentials(
        packageName: String,
        webDomain: String?
    ): List<DecryptedCredential> = withContext(ioDispatcher) {
        val entities = credentialDao.getAllCredentialsList()
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
                // Exact package match (e.g. com.spotify.music)
                storedAppId != null && storedAppId == cleanPkg -> true

                // Stored App ID matches domain or domain contains stored App ID
                cleanDomain != null && storedAppId != null &&
                        (storedAppId.contains(cleanDomain) || cleanDomain.contains(storedAppId)) -> true

                // Stored title matches domain or package (e.g. title "Spotify" matches com.spotify.music or spotify.com)
                cleanDomain != null && (cleanDomain.contains(storedTitle) || storedTitle.contains(cleanDomain.substringBefore('.'))) -> true
                cleanPkg.contains(storedTitle) -> true

                else -> false
            }
        }

        matchedEntities.mapNotNull { decryptEntitySafely(it) }
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

        // Check if an entry with the same appId (or title) and username already exists
        val allDecrypted = credentialDao.getAllCredentialsList().mapNotNull { decryptEntitySafely(it) }
        val existingMatch = allDecrypted.find {
            val sameUsername = it.username.equals(pending.username, ignoreCase = true)
            val sameApp = (it.appId != null && pending.appId != null && it.appId.equals(pending.appId, ignoreCase = true)) ||
                    it.title.equals(finalTitle, ignoreCase = true)
            sameUsername && sameApp
        }

        val resultId = if (existingMatch != null) {
            // If password changed, update existing account
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
            // New username or new account: insert as distinct credential (multiple accounts supported)
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

        // Remove from pending queue
        credentialDao.deletePendingCredentialById(pendingId)
        resultId
    }

    override suspend fun dismissPendingCredential(pendingId: Long): Unit = withContext(ioDispatcher) {
        credentialDao.deletePendingCredentialById(pendingId)
    }

    private fun decryptEntitySafely(entity: Credential): DecryptedCredential? {
        return try {
            val payload = cryptoManager.decrypt(entity.encryptedData, entity.nonce)
            DecryptedCredential(
                id = entity.id,
                title = entity.title,
                appId = entity.appId,
                username = payload.username,
                password = payload.password,
                notes = payload.notes,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt
            )
        } catch (_: Exception) {
            DecryptedCredential(
                id = entity.id,
                title = entity.title,
                appId = entity.appId,
                username = "[Encrypted]",
                password = "",
                notes = "Encrypted entry. Requires biometric authentication.",
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt
            )
        }
    }
}
