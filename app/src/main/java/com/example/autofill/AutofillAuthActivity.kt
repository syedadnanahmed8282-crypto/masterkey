package com.example.autofill

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.R
import com.example.data.local.MasterKeyDatabase
import com.example.data.repository.RoomCredentialRepository
import com.example.data.security.BiometricAuthManager
import com.example.data.security.CryptoManager
import kotlinx.coroutines.launch

class AutofillAuthActivity : FragmentActivity() {

    companion object {
        const val EXTRA_CREDENTIAL_ID = "extra_credential_id"
        const val EXTRA_USERNAME_AUTOFILL_ID = "extra_username_autofill_id"
        const val EXTRA_PASSWORD_AUTOFILL_ID = "extra_password_autofill_id"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_SUBTITLE = "extra_subtitle"
    }

    private lateinit var biometricAuthManager: BiometricAuthManager
    private lateinit var repository: com.example.data.repository.CredentialRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val credentialId = intent.getLongExtra(EXTRA_CREDENTIAL_ID, -1L)
        val usernameId = getParcelableExtraCompat(intent, EXTRA_USERNAME_AUTOFILL_ID, AutofillId::class.java)
        val passwordId = getParcelableExtraCompat(intent, EXTRA_PASSWORD_AUTOFILL_ID, AutofillId::class.java)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "MasterKey"
        val subtitle = intent.getStringExtra(EXTRA_SUBTITLE) ?: "Account"

        if (credentialId == -1L || (usernameId == null && passwordId == null)) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val cryptoManager = CryptoManager()
        val database = MasterKeyDatabase.getDatabase(applicationContext)
        val supabaseManager = com.example.data.remote.SupabaseClientManager(applicationContext)
        val masterKeyManager = com.example.data.security.MasterKeyManager(applicationContext, cryptoManager, supabaseManager)
        repository = com.example.data.repository.SupabaseCredentialRepository(
            credentialDao = database.credentialDao(),
            cryptoManager = cryptoManager,
            masterKeyManager = masterKeyManager,
            supabaseManager = supabaseManager
        )
        biometricAuthManager = BiometricAuthManager(applicationContext)

        if (biometricAuthManager.isAuthenticationAvailable()) {
            biometricAuthManager.promptBiometricUnlock(
                activity = this,
                title = "Unlock to Autofill",
                subtitle = label,
                onSuccess = {
                    completeAutofill(credentialId, usernameId, passwordId, label, subtitle)
                },
                onError = { _, _ ->
                    setResult(RESULT_CANCELED)
                    finish()
                },
                onFailed = {
                    // BiometricPrompt handles retry UI internally
                }
            )
        } else {
            // Fallback for emulator / devices without enrolled biometrics
            completeAutofill(credentialId, usernameId, passwordId, label, subtitle)
        }
    }

    private fun completeAutofill(
        credentialId: Long,
        usernameId: AutofillId?,
        passwordId: AutofillId?,
        label: String,
        subtitle: String
    ) {
        lifecycleScope.launch {
            val credential = repository.getDecryptedCredentialById(credentialId)
            if (credential == null) {
                setResult(RESULT_CANCELED)
                finish()
                return@launch
            }

            val remoteViews = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
                setTextViewText(R.id.autofill_title, label)
                setTextViewText(R.id.autofill_subtitle, subtitle)
            }

            val datasetBuilder = Dataset.Builder(remoteViews)

            if (usernameId != null) {
                datasetBuilder.setValue(usernameId, AutofillValue.forText(credential.username))
            }
            if (passwordId != null) {
                datasetBuilder.setValue(passwordId, AutofillValue.forText(credential.password))
            }

            val authenticatedDataset = datasetBuilder.build()
            val replyIntent = Intent().apply {
                putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, authenticatedDataset)
            }

            setResult(RESULT_OK, replyIntent)
            finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun <T> getParcelableExtraCompat(intent: Intent, key: String, clazz: Class<T>): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, clazz)
        } else {
            intent.getParcelableExtra(key)
        }
    }
}
