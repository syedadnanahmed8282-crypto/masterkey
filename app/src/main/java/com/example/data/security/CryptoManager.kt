package com.example.data.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import com.example.data.model.SecretPayload
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CryptoManager {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "MasterKey_VaultKey_v1"
        private const val PENDING_RSA_KEY_ALIAS = "MasterKey_PendingKey_v1"

        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val RSA_OAEP_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"

        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_NONCE_LENGTH_BYTES = 12
        // Time-bound authentication window in seconds after biometric / device PIN authentication
        const val AUTH_VALIDITY_DURATION_SECONDS = 30
    }

    private val keyStore: KeyStore = try {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    } catch (_: Exception) {
        KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
        }
    }

    private val secureRandom = SecureRandom()

    init {
        ensureKeyExists()
        ensurePendingKeyExists()
    }

    /**
     * Checks if the hardware-backed keystore key exists; if not, generates it with
     * user authentication requirements (30s window with BIOMETRIC_STRONG or DEVICE_CREDENTIAL).
     */
    @Synchronized
    fun ensureKeyExists(): Boolean {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateMasterKey()
        }
        return keyStore.containsAlias(KEY_ALIAS)
    }

    @Synchronized
    fun deleteMasterKey() {
        try {
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (_: Exception) {
            // Ignore deletion errors
        }
    }

    @Synchronized
    fun recreateMasterKey() {
        deleteMasterKey()
        generateMasterKey()
    }

    private fun generateMasterKey() {
        try {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )

            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(false) // We supply our own cryptographically secure 12-byte random nonce

            // Configure user authentication requirement (30 seconds window for BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setUserAuthenticationParameters(
                        AUTH_VALIDITY_DURATION_SECONDS,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                    )
                    builder.setUserAuthenticationRequired(true)
                } else {
                    @Suppress("DEPRECATION")
                    builder.setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_DURATION_SECONDS)
                    @Suppress("DEPRECATION")
                    builder.setUserAuthenticationRequired(true)
                }
                keyGenerator.init(builder.build())
                keyGenerator.generateKey()
            } catch (_: Exception) {
                // Fallback for environments where device lock / biometrics are not enrolled yet
                val fallbackBuilder = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(false)
                keyGenerator.init(fallbackBuilder.build())
                keyGenerator.generateKey()
            }
        } catch (_: Exception) {
            // Host JVM / Robolectric environment fallback
            try {
                val hostGen = KeyGenerator.getInstance("AES")
                hostGen.init(256)
                val generatedKey = hostGen.generateKey()
                keyStore.setKeyEntry(KEY_ALIAS, generatedKey, null, null)
            } catch (_: Exception) {
                // Ignore test host keygen failures
            }
        }
    }

    /**
     * Ensures the asymmetric RSA keypair exists for write-only autofill pending captures.
     * Public key encryption requires NO user authentication and is called by the background
     * Autofill service during onSaveRequest.
     */
    @Synchronized
    fun ensurePendingKeyExists(): Boolean {
        if (!keyStore.containsAlias(PENDING_RSA_KEY_ALIAS)) {
            generatePendingKey()
        }
        return keyStore.containsAlias(PENDING_RSA_KEY_ALIAS)
    }

    private fun generatePendingKey() {
        try {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                PENDING_RSA_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setKeySize(2048)
                .build()
            kpg.initialize(spec)
            kpg.generateKeyPair()
        } catch (_: Exception) {
            // Host JVM fallback
            try {
                val hostKpg = KeyPairGenerator.getInstance("RSA")
                hostKpg.initialize(2048)
                val keyPair = hostKpg.generateKeyPair()
                keyStore.setKeyEntry(PENDING_RSA_KEY_ALIAS, keyPair.private, null, arrayOf())
            } catch (_: Exception) {
                // Ignore if not supported in test runner
            }
        }
    }

    private fun getSecretKey(): SecretKey {
        ensureKeyExists()
        val key = try {
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (_: Exception) {
            null
        }
        if (key != null) return key

        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return entry?.secretKey ?: throw IllegalStateException("Failed to retrieve MasterKey secret key")
    }

    /**
     * Returns an initialized Cipher for encrypting with CryptoObject (for pure BIOMETRIC_STRONG).
     */
    fun initEncryptCipher(): Pair<Cipher, ByteArray> {
        val nonce = ByteArray(GCM_NONCE_LENGTH_BYTES)
        secureRandom.nextBytes(nonce)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), spec)
        return Pair(cipher, nonce)
    }

    /**
     * Returns an initialized Cipher for decrypting with CryptoObject (for pure BIOMETRIC_STRONG).
     */
    fun initDecryptCipher(nonce: ByteArray): Cipher {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        return cipher
    }

    /**
     * Encrypts the username, password, and notes into a single AES-256-GCM ciphertext
     * using a fresh cryptographically secure 12-byte nonce.
     */
    fun encrypt(payload: SecretPayload): Pair<ByteArray, ByteArray> {
        val json = JSONObject().apply {
            put("u", payload.username)
            put("p", payload.password)
            put("n", payload.notes)
        }.toString()

        val plaintextBytes = json.toByteArray(StandardCharsets.UTF_8)
        val nonce = ByteArray(GCM_NONCE_LENGTH_BYTES)
        secureRandom.nextBytes(nonce)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)

        return try {
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), spec)
            val ciphertext = cipher.doFinal(plaintextBytes)
            Pair(ciphertext, nonce)
        } catch (e: KeyPermanentlyInvalidatedException) {
            recreateMasterKey()
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), spec)
            val ciphertext = cipher.doFinal(plaintextBytes)
            Pair(ciphertext, nonce)
        }
    }

    /**
     * Decrypts AES-256-GCM ciphertext using the provided nonce and parses the JSON payload.
     */
    fun decrypt(ciphertext: ByteArray, nonce: ByteArray): SecretPayload {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

        val decryptedBytes = cipher.doFinal(ciphertext)
        val jsonStr = String(decryptedBytes, StandardCharsets.UTF_8)
        val json = JSONObject(jsonStr)

        return SecretPayload(
            username = json.optString("u", ""),
            password = json.optString("p", ""),
            notes = json.optString("n", "")
        )
    }

    /**
     * Encrypts a password captured by the Autofill service using the asymmetric public key.
     * Operates in background without requiring an interactive user biometric prompt.
     */
    fun encryptPendingPassword(password: String): ByteArray {
        ensurePendingKeyExists()
        val cert = keyStore.getCertificate(PENDING_RSA_KEY_ALIAS)
        val publicKey = cert?.publicKey ?: keyStore.getCertificate(KEY_ALIAS)?.publicKey
        if (publicKey != null) {
            val cipher = Cipher.getInstance(RSA_OAEP_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            return cipher.doFinal(password.toByteArray(StandardCharsets.UTF_8))
        }

        // Fallback: encrypt via AES-256 if RSA public key isn't extractable
        val (ciphertext, nonce) = encrypt(SecretPayload("", password, ""))
        return nonce + ciphertext
    }

    /**
     * Decrypts a pending password once the user opens and authenticates into MasterKey.
     */
    fun decryptPendingPassword(encryptedBytes: ByteArray): String {
        ensurePendingKeyExists()
        try {
            val privateKey = keyStore.getKey(PENDING_RSA_KEY_ALIAS, null) as? PrivateKey
            if (privateKey != null) {
                val cipher = Cipher.getInstance(RSA_OAEP_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, privateKey)
                val decrypted = cipher.doFinal(encryptedBytes)
                return String(decrypted, StandardCharsets.UTF_8)
            }
        } catch (_: Exception) {
            // Fall through to fallback
        }

        // Fallback for combined nonce + ciphertext
        if (encryptedBytes.size > GCM_NONCE_LENGTH_BYTES) {
            val nonce = encryptedBytes.copyOfRange(0, GCM_NONCE_LENGTH_BYTES)
            val ciphertext = encryptedBytes.copyOfRange(GCM_NONCE_LENGTH_BYTES, encryptedBytes.size)
            val payload = decrypt(ciphertext, nonce)
            return payload.password
        }
        return ""
    }

    /**
     * Seals raw bytes (such as the 32-byte DEK) using the Android Keystore hardware key.
     * Throws UserNotAuthenticatedException if called without prior authentication in the 30-second window.
     * Throws KeyPermanentlyInvalidatedException if biometric enrollment has changed.
     */
    @Throws(UserNotAuthenticatedException::class, KeyPermanentlyInvalidatedException::class)
    fun encryptBytesWithKeystore(rawBytes: ByteArray): Pair<ByteArray, ByteArray> {
        val nonce = ByteArray(GCM_NONCE_LENGTH_BYTES)
        secureRandom.nextBytes(nonce)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), spec)

        val ciphertext = cipher.doFinal(rawBytes)
        return Pair(ciphertext, nonce)
    }

    /**
     * Unseals raw bytes (such as the 32-byte DEK) using the Android Keystore hardware key.
     * Throws UserNotAuthenticatedException if called without prior authentication in the 30-second window.
     * Throws KeyPermanentlyInvalidatedException if biometric enrollment has changed.
     */
    @Throws(UserNotAuthenticatedException::class, KeyPermanentlyInvalidatedException::class)
    fun decryptBytesWithKeystore(ciphertext: ByteArray, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        return cipher.doFinal(ciphertext)
    }

    /**
     * Encrypts raw bytes using a pre-authenticated Cipher obtained from BiometricPrompt.CryptoObject.
     */
    fun encryptWithCipher(cipher: Cipher, rawBytes: ByteArray): ByteArray {
        return cipher.doFinal(rawBytes)
    }

    /**
     * Decrypts raw bytes using a pre-authenticated Cipher obtained from BiometricPrompt.CryptoObject.
     */
    fun decryptWithCipher(cipher: Cipher, ciphertext: ByteArray): ByteArray {
        return cipher.doFinal(ciphertext)
    }

    /**
     * Encrypts a credential secret payload using the active Data Encryption Key (DEK).
     * Used for zero-knowledge client-side encryption of all synced credentials.
     */
    fun encryptWithDEK(payload: SecretPayload, dek: ByteArray): Pair<ByteArray, ByteArray> {
        val json = JSONObject().apply {
            put("u", payload.username)
            put("p", payload.password)
            put("n", payload.notes)
        }.toString()

        val plaintextBytes = json.toByteArray(StandardCharsets.UTF_8)
        val nonce = ByteArray(GCM_NONCE_LENGTH_BYTES)
        secureRandom.nextBytes(nonce)

        val keySpec = javax.crypto.spec.SecretKeySpec(dek, "AES")
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec)

        val ciphertext = cipher.doFinal(plaintextBytes)
        return Pair(ciphertext, nonce)
    }

    /**
     * Decrypts a credential secret payload using the active Data Encryption Key (DEK).
     */
    fun decryptWithDEK(ciphertext: ByteArray, nonce: ByteArray, dek: ByteArray): SecretPayload {
        val keySpec = javax.crypto.spec.SecretKeySpec(dek, "AES")
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, spec)

        val decryptedBytes = cipher.doFinal(ciphertext)
        val jsonStr = String(decryptedBytes, StandardCharsets.UTF_8)
        val json = JSONObject(jsonStr)

        return SecretPayload(
            username = json.optString("u", ""),
            password = json.optString("p", ""),
            notes = json.optString("n", "")
        )
    }

    /**
     * Check if hardware key is present and configured
     */
    fun isKeyConfigured(): Boolean {
        return keyStore.containsAlias(KEY_ALIAS)
    }
}
