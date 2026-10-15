package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MasterKeyDatabase
import com.example.data.model.DecryptedCredential
import com.example.data.repository.RoomCredentialRepository
import com.example.data.security.CryptoManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `verify app name resource is MasterKey`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("MasterKey", appName)
    }

    @Test
    fun `test repository saves and retrieves credential`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = MasterKeyDatabase.getDatabase(context)
        val cryptoManager = CryptoManager()
        val repository = RoomCredentialRepository(database.credentialDao(), cryptoManager)

        val id = repository.saveCredential(
            DecryptedCredential(
                title = "GitHub",
                appId = "github.com",
                username = "testuser",
                password = "SuperSecretPassword123!",
                notes = "Personal account"
            )
        )
        assertTrue(id > 0)

        val retrieved = repository.getDecryptedCredentialById(id)
        assertNotNull(retrieved)
        assertEquals("GitHub", retrieved?.title)
        assertEquals("testuser", retrieved?.username)
        assertEquals("SuperSecretPassword123!", retrieved?.password)
        assertEquals("Personal account", retrieved?.notes)

        // Multiple accounts with same appId/title
        val id2 = repository.saveCredential(
            DecryptedCredential(
                title = "GitHub",
                appId = "github.com",
                username = "workuser@corp.com",
                password = "WorkPassword987#",
                notes = "Corporate account"
            )
        )
        assertTrue(id2 > 0)
        assertTrue(id2 != id)

        val list = repository.getDecryptedCredentialsStream().first()
        assertTrue(list.size >= 2)

        // Matching test by domain
        val domainMatches = repository.findMatchingCredentials("com.android.chrome", "https://github.com/login")
        assertTrue(domainMatches.isNotEmpty())
        assertEquals(2, domainMatches.size)
    }

    @Test
    fun `test pending autofill queue capture and commit`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = MasterKeyDatabase.getDatabase(context)
        val cryptoManager = CryptoManager()
        val repository = RoomCredentialRepository(database.credentialDao(), cryptoManager)

        // Capture pending login from Autofill onSaveRequest
        val pendingId = repository.savePendingCredential(
            title = "Spotify",
            appId = "com.spotify.music",
            username = "musiclover@spotify.com",
            password = "StreamPassword2026!"
        )
        assertTrue(pendingId > 0)

        val pendingList = repository.getPendingCredentialsStream().first()
        assertTrue(pendingList.any { it.id == pendingId })

        // Commit to vault
        val vaultId = repository.commitPendingCredential(pendingId)
        assertTrue(vaultId > 0)

        // Verified in main vault
        val saved = repository.getDecryptedCredentialById(vaultId)
        assertNotNull(saved)
        assertEquals("Spotify", saved?.title)
        assertEquals("musiclover@spotify.com", saved?.username)
        assertEquals("StreamPassword2026!", saved?.password)

        // Verified removed from pending queue
        val pendingRemaining = repository.getPendingCredentialsStream().first()
        assertTrue(pendingRemaining.none { it.id == pendingId })
    }

    @Test
    fun `test zero knowledge key derivation and wrapping`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cryptoManager = CryptoManager()
        val supabaseManager = com.example.data.remote.SupabaseClientManager(context)
        val masterKeyManager = com.example.data.security.MasterKeyManager(context, cryptoManager, supabaseManager)

        val masterPassword = "MyStrongMasterPassphrase2026!".toCharArray()
        val salt = masterKeyManager.generateSalt()
        val kek = masterKeyManager.deriveKEK(masterPassword, salt, iterations = 1000) // Lower iteration in test for fast execution

        val dek = masterKeyManager.generateDEK()
        val (wrappedDek, nonce) = masterKeyManager.wrapDEK(dek, kek)

        val unwrappedDek = masterKeyManager.unwrapDEK(wrappedDek, nonce, kek)
        assertTrue(dek.contentEquals(unwrappedDek))

        // Incorrect password fails decryption
        val wrongPassword = "WrongPassword999!".toCharArray()
        val wrongKek = masterKeyManager.deriveKEK(wrongPassword, salt, iterations = 1000)
        var failed = false
        try {
            masterKeyManager.unwrapDEK(wrappedDek, nonce, wrongKek)
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(failed)
    }
}
