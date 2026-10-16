package com.example.data.security

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import com.example.data.remote.SupabaseClientManager
import com.example.data.remote.model.UserKeysDto
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class MasterKeyManager(
    context: Context,
    private val cryptoManager: CryptoManager,
    private val supabaseManager: SupabaseClientManager
) {

    companion object {
        const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val PBKDF2_ITERATIONS = 600_000
        const val KEY_LENGTH_BITS = 256
        const val SALT_LENGTH_BYTES = 16
        const val DEK_LENGTH_BYTES = 32
        const val GCM_NONCE_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128

        private const val PREFS_NAME = "masterkey_cloud_key_prefs"
        private const val PREF_SEALED_DEK = "sealed_dek_blob"
        private const val PREF_SEALED_DEK_NONCE = "sealed_dek_nonce"
        private const val PREF_SEALED_USER_ID = "sealed_user_id"
        private const val PREF_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
    }

    sealed class UnsealResult {
        data class Success(val dek: ByteArray) : UnsealResult() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
                other as Success
                return dek.contentEquals(other.dek)
            }
            override fun hashCode(): Int = dek.contentHashCode()
        }
        object NeedsAuthentication : UnsealResult()
        object KeyPermanentlyInvalidated : UnsealResult()
        object NoKeyFound : UnsealResult()
        data class Error(val error: Throwable) : UnsealResult()
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    // Transient in-memory copy of the unlocked DEK for the active session.
    @Volatile
    private var _activeDEK: ByteArray? = null

    val isDEKUnlocked: Boolean
        get() = _activeDEK != null

    fun getActiveDEK(): ByteArray? = _activeDEK

    fun setActiveDEK(dek: ByteArray) {
        _activeDEK?.let { wipe(it) }
        _activeDEK = dek.copyOf()
    }

    fun lockActiveDEK() {
        _activeDEK?.let { wipe(it) }
        _activeDEK = null
    }

    fun getLastSyncTimestamp(): Long {
        return prefs.getLong(PREF_LAST_SYNC_TIMESTAMP, 0L)
    }

    fun updateLastSyncTimestamp(timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(PREF_LAST_SYNC_TIMESTAMP, timestamp).apply()
    }

    fun getFormattedLastSyncTime(): String {
        val lastSync = getLastSyncTimestamp()
        if (lastSync == 0L) return "Never synced"
        val elapsedMillis = System.currentTimeMillis() - lastSync
        val seconds = elapsedMillis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return when {
            seconds < 60 -> "Just now"
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> "$hours hr ago"
            else -> "${hours / 24} days ago"
        }
    }

    /**
     * Generates a fresh random 16-byte salt for PBKDF2.
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Generates a fresh random 256-bit (32 bytes) Data Encryption Key (DEK).
     */
    fun generateDEK(): ByteArray {
        val dek = ByteArray(DEK_LENGTH_BYTES)
        secureRandom.nextBytes(dek)
        return dek
    }

    /**
     * Derives a 256-bit Key Encryption Key (KEK) from the user's Master Password using PBKDF2.
     */
    fun deriveKEK(masterPassword: CharArray, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): SecretKey {
        val spec = PBEKeySpec(masterPassword, salt, iterations, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        val kekBytes = factory.generateSecret(spec).encoded
        val key = SecretKeySpec(kekBytes, "AES")
        wipe(kekBytes)
        return key
    }

    /**
     * Encrypts (wraps) the DEK using the derived KEK with AES-256-GCM and a fresh 12-byte nonce.
     */
    fun wrapDEK(dek: ByteArray, kek: SecretKey): Pair<ByteArray, ByteArray> {
        val nonce = ByteArray(GCM_NONCE_LENGTH_BYTES)
        secureRandom.nextBytes(nonce)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, kek, spec)

        val wrapped = cipher.doFinal(dek)
        return Pair(wrapped, nonce)
    }

    /**
     * Decrypts (unwraps) the DEK using the derived KEK with AES-256-GCM.
     */
    fun unwrapDEK(wrappedDEK: ByteArray, nonce: ByteArray, kek: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
        cipher.init(Cipher.DECRYPT_MODE, kek, spec)
        return cipher.doFinal(wrappedDEK)
    }

    /**
     * Seals the DEK locally inside the device's Android Keystore hardware key.
     * Throws UserNotAuthenticatedException if called without prior authentication.
     * Catches and handles KeyPermanentlyInvalidatedException.
     */
    @Throws(UserNotAuthenticatedException::class)
    fun sealDEKWithKeystore(dek: ByteArray, userId: String? = null): Boolean {
        return try {
            val (ciphertext, nonce) = cryptoManager.encryptBytesWithKeystore(dek)
            val b64Cipher = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            val b64Nonce = Base64.encodeToString(nonce, Base64.NO_WRAP)
            val editor = prefs.edit()
                .putString(PREF_SEALED_DEK, b64Cipher)
                .putString(PREF_SEALED_DEK_NONCE, b64Nonce)
            if (userId != null) {
                editor.putString(PREF_SEALED_USER_ID, userId)
            }
            editor.apply()
            true
        } catch (e: KeyPermanentlyInvalidatedException) {
            cryptoManager.recreateMasterKey()
            clearSealedDEK()
            false
        } catch (e: UserNotAuthenticatedException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Attempts to seal the DEK silently, safely catching UserNotAuthenticatedException without crashing.
     */
    fun trySealDEKWithKeystore(dek: ByteArray, userId: String? = null): Boolean {
        return try {
            sealDEKWithKeystore(dek, userId)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Unseals the DEK from the local Keystore hardware key using the active biometric/PIN session.
     * Returns a rich UnsealResult handling UserNotAuthenticatedException and KeyPermanentlyInvalidatedException.
     */
    fun unsealDEKWithKeystoreResult(): UnsealResult {
        val b64Cipher = prefs.getString(PREF_SEALED_DEK, null) ?: return UnsealResult.NoKeyFound
        val b64Nonce = prefs.getString(PREF_SEALED_DEK_NONCE, null) ?: return UnsealResult.NoKeyFound

        val ciphertext = Base64.decode(b64Cipher, Base64.NO_WRAP)
        val nonce = Base64.decode(b64Nonce, Base64.NO_WRAP)

        return try {
            val dek = cryptoManager.decryptBytesWithKeystore(ciphertext, nonce)
            setActiveDEK(dek)
            UnsealResult.Success(dek)
        } catch (_: UserNotAuthenticatedException) {
            UnsealResult.NeedsAuthentication
        } catch (_: KeyPermanentlyInvalidatedException) {
            cryptoManager.recreateMasterKey()
            clearSealedDEK()
            UnsealResult.KeyPermanentlyInvalidated
        } catch (e: Exception) {
            UnsealResult.Error(e)
        }
    }

    fun unsealDEKWithKeystore(): ByteArray? {
        return when (val result = unsealDEKWithKeystoreResult()) {
            is UnsealResult.Success -> result.dek
            else -> null
        }
    }

    fun hasSealedDEK(): Boolean {
        return prefs.contains(PREF_SEALED_DEK) && prefs.contains(PREF_SEALED_DEK_NONCE)
    }

    fun getSealedUserId(): String? {
        return prefs.getString(PREF_SEALED_USER_ID, null)
    }

    fun clearSealedDEK() {
        lockActiveDEK()
        prefs.edit()
            .remove(PREF_SEALED_DEK)
            .remove(PREF_SEALED_DEK_NONCE)
            .remove(PREF_SEALED_USER_ID)
            .remove(PREF_LAST_SYNC_TIMESTAMP)
            .apply()
    }

    // --- Remote Supabase user_keys Operations ---

    /**
     * Fetches user_keys for the specified user from Supabase.
     */
    suspend fun fetchRemoteUserKeys(userId: String): UserKeysDto? = withContext(Dispatchers.IO) {
        try {
            supabaseManager.client.from("user_keys")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeSingleOrNull<UserKeysDto>()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Sets up a fresh master password for a first-time user:
     * 1. Generates random 256-bit DEK.
     * 2. Derives KEK using PBKDF2 (600,000 iterations) and random salt.
     * 3. Wraps DEK with KEK.
     * 4. Stores salt, wrapped_dek, dek_nonce, kdf_params in Supabase user_keys table.
     * 5. Activates DEK in session and returns it for immediate biometric authentication wrapping.
     */
    suspend fun setupNewMasterPassword(
        userId: String,
        masterPassword: CharArray
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val salt = generateSalt()
            val dek = generateDEK()
            val kek = deriveKEK(masterPassword, salt, PBKDF2_ITERATIONS)
            val (wrappedDek, dekNonce) = wrapDEK(dek, kek)

            val kdfParams = buildJsonObject {
                put("iterations", PBKDF2_ITERATIONS)
                put("key_length", KEY_LENGTH_BITS)
                put("algorithm", KDF_ALGORITHM)
            }

            val userKeysDto = UserKeysDto(
                userId = userId,
                salt = Base64.encodeToString(salt, Base64.NO_WRAP),
                kdf = KDF_ALGORITHM,
                kdfParams = kdfParams,
                wrappedDek = Base64.encodeToString(wrappedDek, Base64.NO_WRAP),
                dekNonce = Base64.encodeToString(dekNonce, Base64.NO_WRAP)
            )

            // Save to Supabase
            supabaseManager.client.from("user_keys").upsert(userKeysDto)

            // Store in active session
            setActiveDEK(dek)

            // Try sealing in Keystore silently if already authenticated
            trySealDEKWithKeystore(dek, userId)

            Result.success(dek.copyOf())
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            wipe(masterPassword)
        }
    }

    /**
     * Unwraps the remote DEK using the entered master password on a new device or subsequent login:
     * 1. Derives KEK from entered password and stored salt.
     * 2. Unwraps wrapped DEK using AES-256-GCM.
     * 3. Activates DEK in session and returns it for biometric sealing.
     */
    suspend fun unlockWithMasterPassword(
        userId: String,
        masterPassword: CharArray,
        remoteKeys: UserKeysDto
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val salt = Base64.decode(remoteKeys.salt, Base64.NO_WRAP)
            val wrappedDek = Base64.decode(remoteKeys.wrappedDek, Base64.NO_WRAP)
            val dekNonce = Base64.decode(remoteKeys.dekNonce, Base64.NO_WRAP)

            val iterations = try {
                remoteKeys.kdfParams.jsonObject["iterations"]?.jsonPrimitive?.intOrNull ?: PBKDF2_ITERATIONS
            } catch (_: Exception) {
                PBKDF2_ITERATIONS
            }

            val kek = deriveKEK(masterPassword, salt, iterations)
            val unwrappedDek = try {
                unwrapDEK(wrappedDek, dekNonce, kek)
            } catch (_: Exception) {
                return@withContext Result.failure(IllegalArgumentException("Incorrect master password. Authentication failed."))
            }

            // Set active in memory
            setActiveDEK(unwrappedDek)

            // Try sealing in Keystore silently if already authenticated
            trySealDEKWithKeystore(unwrappedDek, userId)

            Result.success(unwrappedDek.copyOf())
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            wipe(masterPassword)
        }
    }

    /**
     * Changes Master Password:
     * 1. Derives new KEK from new master password and a fresh random salt.
     * 2. Re-wraps the EXISTING active DEK with the new KEK.
     * 3. Updates Supabase user_keys.
     */
    suspend fun changeMasterPassword(
        userId: String,
        newPassword: CharArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val activeDek = getActiveDEK() ?: unsealDEKWithKeystore()
                ?: return@withContext Result.failure(IllegalStateException("Vault must be unlocked to change master password"))

            val newSalt = generateSalt()
            val newKek = deriveKEK(newPassword, newSalt, PBKDF2_ITERATIONS)
            val (newWrappedDek, newDekNonce) = wrapDEK(activeDek, newKek)

            val kdfParams = buildJsonObject {
                put("iterations", PBKDF2_ITERATIONS)
                put("key_length", KEY_LENGTH_BITS)
                put("algorithm", KDF_ALGORITHM)
            }

            val updatedKeys = UserKeysDto(
                userId = userId,
                salt = Base64.encodeToString(newSalt, Base64.NO_WRAP),
                kdf = KDF_ALGORITHM,
                kdfParams = kdfParams,
                wrappedDek = Base64.encodeToString(newWrappedDek, Base64.NO_WRAP),
                dekNonce = Base64.encodeToString(newDekNonce, Base64.NO_WRAP)
            )

            supabaseManager.client.from("user_keys").upsert(updatedKeys)
            trySealDEKWithKeystore(activeDek, userId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            wipe(newPassword)
        }
    }

    fun wipe(bytes: ByteArray) {
        Arrays.fill(bytes, 0.toByte())
    }

    fun wipe(chars: CharArray) {
        Arrays.fill(chars, '\u0000')
    }
}
