package com.example.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillContext
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.widget.RemoteViews
import com.example.R
import com.example.data.local.MasterKeyDatabase
import com.example.data.repository.RoomCredentialRepository
import com.example.data.security.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MasterKeyAutofillService : AutofillService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: com.example.data.repository.CredentialRepository

    override fun onCreate() {
        super.onCreate()
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
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val fillContexts: List<FillContext> = request.fillContexts
        if (fillContexts.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val latestStructure = fillContexts.last().structure
        val parsed = AutofillParser.parse(latestStructure)

        // Only respond when a password field is present (or a username field in a two-step login)
        if (!parsed.isPasswordFieldPresent && !parsed.isUsernameFieldPresent) {
            callback.onSuccess(null)
            return
        }

        serviceScope.launch {
            if (cancellationSignal.isCanceled) return@launch

            val targetPackage = parsed.packageName
            val targetDomain = parsed.webDomain?.let { AutofillParser.extractRegistrableDomain(it) }

            val matchedCredentials = repository.findMatchingCredentials(
                packageName = targetPackage,
                webDomain = targetDomain
            )

            val responseBuilder = FillResponse.Builder()

            // Setup SaveInfo so new or updated logins can be saved
            if (parsed.passwordId != null) {
                val saveInfoBuilder = SaveInfo.Builder(
                    SaveInfo.SAVE_DATA_TYPE_PASSWORD or SaveInfo.SAVE_DATA_TYPE_USERNAME,
                    arrayOf(parsed.passwordId)
                )
                if (parsed.usernameId != null) {
                    saveInfoBuilder.setOptionalIds(arrayOf(parsed.usernameId))
                }
                saveInfoBuilder.setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
                responseBuilder.setSaveInfo(saveInfoBuilder.build())
            }

            if (matchedCredentials.isEmpty()) {
                // If no entry matches, show no suggestion UI (no intrusive dropdown)
                // but keep SaveInfo registered if a password field is present
                if (parsed.passwordId != null) {
                    callback.onSuccess(responseBuilder.build())
                } else {
                    callback.onSuccess(null)
                }
                return@launch
            }

            // Return one Dataset per saved account
            for (credential in matchedCredentials) {
                val maskedUser = maskUsername(credential.username)
                val displayLabel = "${credential.title} – $maskedUser"

                val remoteViews = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
                    setTextViewText(R.id.autofill_title, credential.title)
                    setTextViewText(R.id.autofill_subtitle, maskedUser)
                }

                // Protect every dataset with Biometric authentication
                val authIntent = Intent(this@MasterKeyAutofillService, AutofillAuthActivity::class.java).apply {
                    putExtra(AutofillAuthActivity.EXTRA_CREDENTIAL_ID, credential.id)
                    putExtra(AutofillAuthActivity.EXTRA_USERNAME_AUTOFILL_ID, parsed.usernameId)
                    putExtra(AutofillAuthActivity.EXTRA_PASSWORD_AUTOFILL_ID, parsed.passwordId)
                    putExtra(AutofillAuthActivity.EXTRA_LABEL, credential.title)
                    putExtra(AutofillAuthActivity.EXTRA_SUBTITLE, maskedUser)
                }

                val intentSender = PendingIntent.getActivity(
                    this@MasterKeyAutofillService,
                    credential.id.toInt(),
                    authIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ).intentSender

                val datasetBuilder = Dataset.Builder(remoteViews)
                if (parsed.usernameId != null) {
                    datasetBuilder.setValue(parsed.usernameId, null)
                }
                if (parsed.passwordId != null) {
                    datasetBuilder.setValue(parsed.passwordId, null)
                }
                datasetBuilder.setAuthentication(intentSender)

                responseBuilder.addDataset(datasetBuilder.build())
            }

            callback.onSuccess(responseBuilder.build())
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val fillContexts = request.fillContexts
        if (fillContexts.isEmpty()) {
            callback.onSuccess()
            return
        }

        val structure = fillContexts.last().structure
        val parsed = AutofillParser.parse(structure)

        serviceScope.launch {
            val (enteredUsername, enteredPassword) = extractValues(structure, parsed.usernameId, parsed.passwordId)

            if (enteredPassword.isBlank()) {
                callback.onSuccess()
                return@launch
            }

            val targetPackage = parsed.packageName
            val targetDomain = parsed.webDomain?.let { AutofillParser.extractRegistrableDomain(it) }

            val appId = targetDomain ?: targetPackage
            val defaultTitle = when {
                targetDomain != null -> targetDomain.substringBefore('.').replaceFirstChar { it.uppercase() }
                targetPackage.isNotBlank() -> targetPackage.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                else -> "New Account"
            }

            val usernameToSave = enteredUsername.ifBlank { "User" }

            // Hold in pending queue encrypted with asymmetric pending key
            repository.savePendingCredential(
                title = defaultTitle,
                appId = appId,
                username = usernameToSave,
                password = enteredPassword
            )

            // Notify user to finish saving into primary vault
            AutofillNotificationHelper.showPendingSaveNotification(
                context = applicationContext,
                title = defaultTitle,
                username = usernameToSave
            )

            callback.onSuccess()
        }
    }

    private fun extractValues(
        structure: android.app.assist.AssistStructure,
        usernameId: AutofillId?,
        passwordId: AutofillId?
    ): Pair<String, String> {
        var username = ""
        var password = ""

        fun traverse(node: android.app.assist.AssistStructure.ViewNode) {
            val autofillVal = node.autofillValue
            val text = autofillVal?.textValue?.toString() ?: node.text?.toString() ?: ""

            if (node.autofillId == usernameId && text.isNotBlank()) {
                username = text
            }
            if (node.autofillId == passwordId && text.isNotBlank()) {
                password = text
            }

            for (i in 0 until node.childCount) {
                val child = node.getChildAt(i) ?: continue
                traverse(child)
            }
        }

        for (i in 0 until structure.windowNodeCount) {
            val root = structure.getWindowNodeAt(i).rootViewNode ?: continue
            traverse(root)
        }

        return Pair(username, password)
    }

    private fun maskUsername(username: String): String {
        if (username.isBlank()) return "Account"
        if (username.contains("@")) {
            val parts = username.split("@", limit = 2)
            val name = parts[0]
            val domain = parts[1]
            val masked = if (name.length <= 2) "${name.first()}***" else "${name.first()}***${name.last()}"
            return "$masked@$domain"
        }
        return if (username.length <= 2) "${username.first()}***" else "${username.first()}***${username.last()}"
    }
}
