package com.example.ui.viewmodel

import android.content.Context
import android.os.Build
import android.view.autofill.AutofillManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.DecryptedCredential
import com.example.data.model.PendingCredential
import com.example.data.remote.AuthResult
import com.example.data.remote.SupabaseClientManager
import com.example.data.remote.model.UserKeysDto
import com.example.data.repository.CredentialRepository
import com.example.data.repository.SyncStatus
import com.example.data.security.AppLockManager
import com.example.data.security.AutoLockTimeout
import com.example.data.security.BiometricAuthManager
import com.example.data.security.ClipboardHelper
import com.example.data.security.MasterKeyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class VaultState {
    NEEDS_AUTH,
    NEEDS_MASTER_PASSWORD_SETUP,
    NEEDS_MASTER_PASSWORD_ENTRY,
    LOCKED,
    UNLOCKED
}

class MasterKeyViewModel(
    private val repository: CredentialRepository,
    private val appLockManager: AppLockManager,
    private val clipboardHelper: ClipboardHelper,
    val biometricAuthManager: BiometricAuthManager,
    val supabaseManager: SupabaseClientManager,
    val masterKeyManager: MasterKeyManager
) : ViewModel() {

    private val _vaultState = MutableStateFlow(VaultState.NEEDS_AUTH)
    val vaultState: StateFlow<VaultState> = _vaultState.asStateFlow()

    val isUnlocked: StateFlow<Boolean> = appLockManager.isUnlocked
    val autoLockTimeout: StateFlow<AutoLockTimeout> = appLockManager.selectedTimeout
    val isFlagSecure: StateFlow<Boolean> = appLockManager.isFlagSecureEnabled
    val syncStatus: StateFlow<SyncStatus> = repository.syncStatus

    private val _lastSyncFormatted = MutableStateFlow(masterKeyManager.getFormattedLastSyncTime())
    val lastSyncFormatted: StateFlow<String> = _lastSyncFormatted.asStateFlow()

    private val _currentUserEmail = MutableStateFlow<String?>(supabaseManager.getCurrentUserEmail())
    val currentUserEmail: StateFlow<String?> = _currentUserEmail.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _emailConfirmationNotice = MutableStateFlow<String?>(null)
    val emailConfirmationNotice: StateFlow<String?> = _emailConfirmationNotice.asStateFlow()

    private val _masterPasswordError = MutableStateFlow<String?>(null)
    val masterPasswordError: StateFlow<String?> = _masterPasswordError.asStateFlow()

    private val _isMasterPasswordLoading = MutableStateFlow(false)
    val isMasterPasswordLoading: StateFlow<Boolean> = _isMasterPasswordLoading.asStateFlow()

    private var cachedRemoteKeys: UserKeysDto? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    private val _selectedCredentialId = MutableStateFlow<Long?>(null)
    val selectedCredentialId: StateFlow<Long?> = _selectedCredentialId.asStateFlow()

    private val _isAutofillEnabled = MutableStateFlow(false)
    val isAutofillEnabled: StateFlow<Boolean> = _isAutofillEnabled.asStateFlow()

    // Filtered credentials based on search query
    val credentials: StateFlow<List<DecryptedCredential>> = combine(
        repository.getDecryptedCredentialsStream(),
        _searchQuery
    ) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            val trimmed = query.trim().lowercase()
            list.filter { credential ->
                credential.title.lowercase().contains(trimmed) ||
                credential.username.lowercase().contains(trimmed) ||
                (credential.appId?.lowercase()?.contains(trimmed) == true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Pending autofill captures
    val pendingCredentials: StateFlow<List<PendingCredential>> = repository
        .getPendingCredentialsStream()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Observe lock manager changes
        viewModelScope.launch {
            appLockManager.isUnlocked.collect { unlocked ->
                if (!unlocked) {
                    masterKeyManager.lockActiveDEK()
                    if (_vaultState.value == VaultState.UNLOCKED) {
                        _vaultState.value = VaultState.LOCKED
                    }
                }
            }
        }

        // Initialize vault state on startup
        refreshVaultState()
    }

    fun refreshVaultState() {
        viewModelScope.launch {
            _currentUserEmail.value = supabaseManager.getCurrentUserEmail()

            if (!supabaseManager.isUserLoggedIn()) {
                _vaultState.value = VaultState.NEEDS_AUTH
                return@launch
            }

            val userId = supabaseManager.getCurrentUserId()
            if (userId == null) {
                _vaultState.value = VaultState.NEEDS_AUTH
                return@launch
            }

            // If active DEK is already in memory and vault marked unlocked
            if (masterKeyManager.isDEKUnlocked && appLockManager.isUnlocked.value) {
                _vaultState.value = VaultState.UNLOCKED
                return@launch
            }

            // Check if this device already has a sealed DEK in hardware Keystore
            if (masterKeyManager.hasSealedDEK()) {
                _vaultState.value = VaultState.LOCKED
                return@launch
            }

            // Otherwise, check remote user_keys table on Supabase
            _isMasterPasswordLoading.value = true
            val remoteKeys = masterKeyManager.fetchRemoteUserKeys(userId)
            cachedRemoteKeys = remoteKeys
            _isMasterPasswordLoading.value = false

            if (remoteKeys == null) {
                // First time user: needs to create master password
                _vaultState.value = VaultState.NEEDS_MASTER_PASSWORD_SETUP
            } else {
                // User has vault keys on cloud, but this device has not unwrapped it yet
                _vaultState.value = VaultState.NEEDS_MASTER_PASSWORD_ENTRY
            }
        }
    }

    // --- Authentication Flow ---

    fun login(email: String, passwordChars: CharArray, onSuccess: () -> Unit = {}) {
        if (email.isBlank() || passwordChars.isEmpty()) {
            _authError.value = "Please enter both email and password."
            return
        }

        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            _emailConfirmationNotice.value = null

            when (val result = supabaseManager.signIn(email, passwordChars)) {
                is AuthResult.Success -> {
                    _currentUserEmail.value = result.email
                    _authError.value = null
                    _isAuthLoading.value = false
                    refreshVaultState()
                    onSuccess()
                }
                is AuthResult.NeedsEmailConfirmation -> {
                    _isAuthLoading.value = false
                    _emailConfirmationNotice.value = "Please check your email (${result.email}) and click the verification link before logging in."
                }
                is AuthResult.Error -> {
                    _isAuthLoading.value = false
                    _authError.value = result.message
                }
            }
        }
    }

    fun signUp(email: String, passwordChars: CharArray, onSuccess: () -> Unit = {}) {
        if (email.isBlank() || passwordChars.isEmpty()) {
            _authError.value = "Please enter both email and password."
            return
        }

        if (passwordChars.size < 6) {
            _authError.value = "Account password must be at least 6 characters."
            return
        }

        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            _emailConfirmationNotice.value = null

            when (val result = supabaseManager.signUp(email, passwordChars)) {
                is AuthResult.Success -> {
                    _currentUserEmail.value = result.email
                    _authError.value = null
                    _isAuthLoading.value = false
                    _operationMessage.value = "Account created successfully"
                    refreshVaultState()
                    onSuccess()
                }
                is AuthResult.NeedsEmailConfirmation -> {
                    _isAuthLoading.value = false
                    _emailConfirmationNotice.value = "Confirmation email sent! Please check your inbox at ${result.email} to verify your account."
                }
                is AuthResult.Error -> {
                    _isAuthLoading.value = false
                    _authError.value = result.message
                }
            }
        }
    }

    fun logout(clearLocalData: Boolean, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            masterKeyManager.clearSealedDEK()
            appLockManager.lock()

            if (clearLocalData) {
                repository.clearLocalData()
            }

            supabaseManager.signOut()
            _currentUserEmail.value = null
            _vaultState.value = VaultState.NEEDS_AUTH
            _operationMessage.value = if (clearLocalData) {
                "Logged out and cleared local vault from this device."
            } else {
                "Logged out. Local vault data retained on device."
            }
            onComplete()
        }
    }

    fun clearAuthMessages() {
        _authError.value = null
        _emailConfirmationNotice.value = null
        _masterPasswordError.value = null
    }

    // --- Master Password & Vault Setup Flow ---

    fun setupMasterPassword(
        passwordChars: CharArray,
        confirmChars: CharArray,
        onSuccess: () -> Unit = {}
    ) {
        if (passwordChars.isEmpty()) {
            _masterPasswordError.value = "Please enter a master password."
            return
        }

        if (passwordChars.size < 8) {
            _masterPasswordError.value = "Master password must be at least 8 characters long."
            return
        }

        if (!passwordChars.contentEquals(confirmChars)) {
            _masterPasswordError.value = "Master passwords do not match. Please verify."
            return
        }

        val userId = supabaseManager.getCurrentUserId()
        if (userId == null) {
            _masterPasswordError.value = "User session expired. Please sign in again."
            return
        }

        viewModelScope.launch {
            _isMasterPasswordLoading.value = true
            _masterPasswordError.value = null

            val result = masterKeyManager.setupNewMasterPassword(userId, passwordChars)
            _isMasterPasswordLoading.value = false

            if (result.isSuccess) {
                appLockManager.unlock()
                _vaultState.value = VaultState.UNLOCKED
                _operationMessage.value = "Master password configured. Vault initialized."
                triggerManualSync()
                onSuccess()
            } else {
                _masterPasswordError.value = result.exceptionOrNull()?.message ?: "Failed to set up master password."
            }
        }
    }

    fun unlockWithMasterPassword(
        passwordChars: CharArray,
        onSuccess: () -> Unit = {}
    ) {
        if (passwordChars.isEmpty()) {
            _masterPasswordError.value = "Please enter your master password."
            return
        }

        val userId = supabaseManager.getCurrentUserId()
        if (userId == null) {
            _masterPasswordError.value = "No active user session. Please sign in again."
            return
        }

        viewModelScope.launch {
            _isMasterPasswordLoading.value = true
            _masterPasswordError.value = null

            var remoteKeys = cachedRemoteKeys
            if (remoteKeys == null) {
                remoteKeys = masterKeyManager.fetchRemoteUserKeys(userId)
                cachedRemoteKeys = remoteKeys
            }

            if (remoteKeys == null) {
                _isMasterPasswordLoading.value = false
                _masterPasswordError.value = "No encryption keys found for this account. Setup is required."
                _vaultState.value = VaultState.NEEDS_MASTER_PASSWORD_SETUP
                return@launch
            }

            val result = masterKeyManager.unlockWithMasterPassword(userId, passwordChars, remoteKeys)
            _isMasterPasswordLoading.value = false

            if (result.isSuccess) {
                appLockManager.unlock()
                _vaultState.value = VaultState.UNLOCKED
                _operationMessage.value = "Vault unlocked successfully"
                triggerManualSync()
                onSuccess()
            } else {
                _masterPasswordError.value = "Incorrect master password. Could not decrypt vault."
            }
        }
    }

    fun unlockWithBiometrics(
        activity: FragmentActivity,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        biometricAuthManager.promptBiometricUnlock(
            activity = activity,
            title = "MasterKey Vault",
            subtitle = "Authenticate to decrypt stored credentials",
            onSuccess = {
                val unsealedDek = masterKeyManager.unsealDEKWithKeystore()
                if (unsealedDek != null) {
                    appLockManager.unlock()
                    _vaultState.value = VaultState.UNLOCKED
                    triggerManualSync()
                    onSuccess()
                } else {
                    // Hardware unseal failed or requires master password
                    _vaultState.value = VaultState.NEEDS_MASTER_PASSWORD_ENTRY
                    onError("Device authentication succeeded, but master password is required to decrypt.")
                }
            },
            onError = { _, errString ->
                onError(errString.toString())
            },
            onFailed = {
                onError("Authentication failed. Please try again.")
            }
        )
    }

    fun changeMasterPassword(
        newPasswordChars: CharArray,
        confirmChars: CharArray,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (newPasswordChars.size < 8) {
            onError("New master password must be at least 8 characters long.")
            return
        }

        if (!newPasswordChars.contentEquals(confirmChars)) {
            onError("New master passwords do not match.")
            return
        }

        val userId = supabaseManager.getCurrentUserId()
        if (userId == null) {
            onError("User session expired. Please sign in again.")
            return
        }

        viewModelScope.launch {
            _isMasterPasswordLoading.value = true
            val result = masterKeyManager.changeMasterPassword(userId, newPasswordChars)
            _isMasterPasswordLoading.value = false

            if (result.isSuccess) {
                _operationMessage.value = "Master password changed successfully. Encryption keys updated on cloud."
                cachedRemoteKeys = null
                onSuccess()
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Failed to change master password."
                onError(msg)
            }
        }
    }

    // --- Sync Engine Operations ---

    fun triggerManualSync() {
        viewModelScope.launch {
            repository.triggerSync()
            _lastSyncFormatted.value = masterKeyManager.getFormattedLastSyncTime()
        }
    }

    // --- App Settings & Controls ---

    fun setFlagSecureEnabled(enabled: Boolean) {
        appLockManager.setFlagSecureEnabled(enabled)
        _operationMessage.value = if (enabled) {
            "Screenshot protection (FLAG_SECURE) activated"
        } else {
            "Screenshot protection disabled for emulator visibility"
        }
    }

    fun checkAutofillStatus(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val autofillManager = context.getSystemService(AutofillManager::class.java)
            _isAutofillEnabled.value = autofillManager?.hasEnabledAutofillServices() == true
        } else {
            _isAutofillEnabled.value = false
        }
    }

    fun commitPendingCredential(pendingId: Long, customTitle: String? = null, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.commitPendingCredential(pendingId, customTitle)
            _operationMessage.value = "Login successfully saved into your vault"
            onSuccess()
        }
    }

    fun dismissPendingCredential(pendingId: Long) {
        viewModelScope.launch {
            repository.dismissPendingCredential(pendingId)
            _operationMessage.value = "Pending login dismissed"
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCredential(id: Long?) {
        _selectedCredentialId.value = id
    }

    suspend fun getCredentialById(id: Long): DecryptedCredential? {
        return repository.getDecryptedCredentialById(id)
    }

    fun saveCredential(
        title: String,
        username: String,
        password: String,
        appId: String?,
        notes: String,
        onSuccess: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val credential = DecryptedCredential(
                id = 0,
                title = title.trim(),
                appId = appId?.trim()?.ifEmpty { null },
                username = username.trim(),
                password = password,
                notes = notes.trim()
            )
            val newId = repository.saveCredential(credential)
            _operationMessage.value = "Account saved and queued for sync"
            _lastSyncFormatted.value = masterKeyManager.getFormattedLastSyncTime()
            onSuccess(newId)
        }
    }

    fun updateCredential(
        id: Long,
        title: String,
        username: String,
        password: String,
        appId: String?,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val credential = DecryptedCredential(
                id = id,
                title = title.trim(),
                appId = appId?.trim()?.ifEmpty { null },
                username = username.trim(),
                password = password,
                notes = notes.trim()
            )
            repository.updateCredential(credential)
            _operationMessage.value = "Account updated and queued for sync"
            _lastSyncFormatted.value = masterKeyManager.getFormattedLastSyncTime()
            onSuccess()
        }
    }

    fun deleteCredential(id: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.deleteCredential(id)
            if (_selectedCredentialId.value == id) {
                _selectedCredentialId.value = null
            }
            _operationMessage.value = "Account deleted"
            _lastSyncFormatted.value = masterKeyManager.getFormattedLastSyncTime()
            onSuccess()
        }
    }

    fun copyToClipboard(label: String, text: String) {
        clipboardHelper.copySensitiveText(label, text)
        _operationMessage.value = "$label copied. Clipboard will auto-clear in 30 seconds."
    }

    fun clearOperationMessage() {
        _operationMessage.value = null
    }

    fun unlockVault() {
        appLockManager.unlock()
        _vaultState.value = VaultState.UNLOCKED
    }

    fun lockVault() {
        masterKeyManager.lockActiveDEK()
        appLockManager.lock()
        _selectedCredentialId.value = null
        if (supabaseManager.isUserLoggedIn()) {
            _vaultState.value = VaultState.LOCKED
        } else {
            _vaultState.value = VaultState.NEEDS_AUTH
        }
    }

    fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        appLockManager.setTimeoutPreference(timeout)
        _operationMessage.value = "Auto-lock set to ${timeout.label}"
    }
}

class MasterKeyViewModelFactory(
    private val repository: CredentialRepository,
    private val appLockManager: AppLockManager,
    private val clipboardHelper: ClipboardHelper,
    private val biometricAuthManager: BiometricAuthManager,
    private val supabaseManager: SupabaseClientManager,
    private val masterKeyManager: MasterKeyManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MasterKeyViewModel::class.java)) {
            return MasterKeyViewModel(
                repository = repository,
                appLockManager = appLockManager,
                clipboardHelper = clipboardHelper,
                biometricAuthManager = biometricAuthManager,
                supabaseManager = supabaseManager,
                masterKeyManager = masterKeyManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
