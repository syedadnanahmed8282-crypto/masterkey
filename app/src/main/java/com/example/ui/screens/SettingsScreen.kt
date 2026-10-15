package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.SyncStatus
import com.example.data.security.AutoLockTimeout
import com.example.ui.theme.BackgroundWhite
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.LightGrayDivider
import com.example.ui.theme.OutlineBorder
import com.example.ui.theme.SkyBlueContainer
import com.example.ui.theme.SkyBluePrimary
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextDarkGray
import com.example.ui.theme.TextMutedGray
import com.example.ui.theme.TextSecondaryGray
import com.example.ui.viewmodel.MasterKeyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MasterKeyViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedTimeout by viewModel.autoLockTimeout.collectAsStateWithLifecycle()
    val isFlagSecure by viewModel.isFlagSecure.collectAsStateWithLifecycle()
    val isAutofillEnabled by viewModel.isAutofillEnabled.collectAsStateWithLifecycle()
    val userEmail by viewModel.currentUserEmail.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val lastSyncTime by viewModel.lastSyncFormatted.collectAsStateWithLifecycle()

    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showChangeMasterPasswordDialog by remember { mutableStateOf(false) }

    val biometricStatus = remember { viewModel.biometricAuthManager.getStatusDescription() }

    LaunchedEffect(Unit) {
        viewModel.checkAutofillStatus(context)
    }

    fun openAutofillSettings() {
        val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS)
            context.startActivity(fallback)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        containerColor = BackgroundWhite,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkGray
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextDarkGray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundWhite
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // --- Account & Cloud Sync Section ---
            Text(
                text = "ACCOUNT & CLOUD SYNC",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondaryGray,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = SkyBluePrimary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userEmail ?: "Signed In",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextDarkGray
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val statusColor = when (syncStatus) {
                                    SyncStatus.SYNCED -> Color(0xFF2E7D32)
                                    SyncStatus.SYNCING -> SkyBluePrimary
                                    SyncStatus.OFFLINE -> TextSecondaryGray
                                    SyncStatus.ERROR -> ErrorRed
                                }
                                Text(
                                    text = "${syncStatus.label} • Last synced $lastSyncTime",
                                    fontSize = 12.sp,
                                    color = statusColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.triggerManualSync() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SkyBluePrimary,
                                contentColor = BackgroundWhite
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f).height(40.dp).testTag("sync_now_button")
                        ) {
                            Icon(
                                imageVector = if (syncStatus == SyncStatus.SYNCING) Icons.Default.Refresh else Icons.Default.CloudQueue,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (syncStatus == SyncStatus.SYNCING) "Syncing..." else "Sync Now",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = { showChangeMasterPasswordDialog = true },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f).height(40.dp).testTag("change_master_pw_button")
                        ) {
                            Text(
                                text = "Change Key",
                                fontSize = 13.sp,
                                color = SkyBluePrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { showLogoutDialog = true },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp).testTag("logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Log Out",
                            fontSize = 13.sp,
                            color = ErrorRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Security Preferences Section ---
            Text(
                text = "SECURITY & LOCKING",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondaryGray,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            SettingsRow(
                icon = Icons.Default.LockClock,
                title = "Auto-Lock Timeout",
                subtitle = selectedTimeout.label,
                onClick = { showTimeoutDialog = true }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = SkyBluePrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Screenshot Protection",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDarkGray
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isFlagSecure) "Active (blocks screenshots & streaming)" else "Disabled (allows emulator stream)",
                            fontSize = 12.sp,
                            color = TextSecondaryGray
                        )
                    }
                    Switch(
                        checked = isFlagSecure,
                        onCheckedChange = { viewModel.setFlagSecureEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BackgroundWhite,
                            checkedTrackColor = SkyBluePrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            SettingsRow(
                icon = Icons.Default.Lock,
                title = "Lock Vault Now",
                subtitle = "Immediately lock and clear DEK from memory",
                onClick = { viewModel.lockVault() }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- System Autofill Service ---
            Text(
                text = "SYSTEM AUTOFILL SERVICE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondaryGray,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Password,
                                contentDescription = null,
                                tint = SkyBluePrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Autofill Service",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextDarkGray
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(
                                    if (isAutofillEnabled) Color(0xFFE8F5E9) else SkyBlueContainer,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isAutofillEnabled) "ACTIVE" else "NOT ENABLED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAutofillEnabled) Color(0xFF2E7D32) else SkyBluePrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isAutofillEnabled) {
                            "MasterKey is your system autofill provider. It fills credentials in apps and web browsers using the local Room cache even when offline."
                        } else {
                            "Allow MasterKey to fill logins across apps and browsers. Tap below to enable MasterKey in Android Autofill settings."
                        },
                        fontSize = 13.sp,
                        color = TextSecondaryGray,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { openAutofillSettings() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SkyBluePrimary,
                            contentColor = BackgroundWhite
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Text(
                            text = if (isAutofillEnabled) "Manage Autofill Settings" else "Enable Autofill in Settings",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Tip for Google Chrome: Go to Chrome Settings > Autofill services > 'Autofill using another service' and select MasterKey.",
                        fontSize = 11.sp,
                        color = TextMutedGray,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Encryption Architecture Card ---
            Text(
                text = "ENCRYPTION ARCHITECTURE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondaryGray,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SecuritySpecRow(
                        title = "Two-Layer Key Architecture",
                        value = "256-bit DEK encrypts credentials with AES-256-GCM. KEK derived from Master Password wraps DEK using PBKDF2-HMAC-SHA256 (600,000 rounds)."
                    )
                    HorizontalDivider(color = LightGrayDivider, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))
                    SecuritySpecRow(
                        title = "Zero-Knowledge Supabase Sync",
                        value = "Server only stores ciphertext, nonces, and wrapped DEK. Supabase RLS enforces own-row access; server updated_at drives last-write-wins sync."
                    )
                    HorizontalDivider(color = LightGrayDivider, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))
                    SecuritySpecRow(
                        title = "Device Hardware Key Protection",
                        value = "Android Keystore hardware key seals active DEK locally for instant biometric/PIN access without re-entering Master Password."
                    )
                    HorizontalDivider(color = LightGrayDivider, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))
                    SecuritySpecRow(
                        title = "Biometrics Status",
                        value = biometricStatus
                    )
                    HorizontalDivider(color = LightGrayDivider, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))
                    SecuritySpecRow(
                        title = "Sensitive Clipboard",
                        value = "EXTRA_IS_SENSITIVE with automated 30s background memory purge."
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Auto-Lock Timeout Dialog
    if (showTimeoutDialog) {
        AlertDialog(
            onDismissRequest = { showTimeoutDialog = false },
            title = {
                Text(
                    text = "Select Auto-Lock Timeout",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextDarkGray
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AutoLockTimeout.values().forEach { timeout ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setAutoLockTimeout(timeout)
                                    showTimeoutDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedTimeout == timeout),
                                onClick = {
                                    viewModel.setAutoLockTimeout(timeout)
                                    showTimeoutDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = SkyBluePrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = timeout.label,
                                fontSize = 14.sp,
                                color = TextDarkGray
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimeoutDialog = false }) {
                    Text("Close", color = SkyBluePrimary)
                }
            },
            containerColor = BackgroundWhite,
            shape = RoundedCornerShape(12.dp)
        )
    }

    // Log Out Prompt Dialog (Required: "Keep local data on this device or clear it?")
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Log Out of MasterKey",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextDarkGray
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Do you want to keep your encrypted vault cached locally on this device, or clear all local vault data?",
                        fontSize = 14.sp,
                        color = TextDarkGray,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your cloud vault on Supabase will remain safe and accessible when you log in again with your Master Password.",
                        fontSize = 12.sp,
                        color = TextSecondaryGray,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            showLogoutDialog = false
                            viewModel.logout(clearLocalData = false)
                        }
                    ) {
                        Text("Keep Local Vault", color = SkyBluePrimary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            showLogoutDialog = false
                            viewModel.logout(clearLocalData = true)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ErrorRed,
                            contentColor = BackgroundWhite
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Clear Local & Log Out")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = TextSecondaryGray)
                }
            },
            containerColor = BackgroundWhite,
            shape = RoundedCornerShape(12.dp)
        )
    }

    // Change Master Password Dialog
    if (showChangeMasterPasswordDialog) {
        ChangeMasterPasswordDialog(
            viewModel = viewModel,
            onDismiss = { showChangeMasterPasswordDialog = false }
        )
    }
}

@Composable
fun ChangeMasterPasswordDialog(
    viewModel: MasterKeyViewModel,
    onDismiss: () -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var newVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = SkyBluePrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Change Master Password",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextDarkGray
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "This will derive a new Key Encryption Key (KEK) and re-wrap your active DEK. All your credentials stay intact without needing re-encryption.",
                    fontSize = 13.sp,
                    color = TextSecondaryGray,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Master Password") },
                    singleLine = true,
                    visualTransformation = if (newVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { newVisible = !newVisible }) {
                            Icon(
                                imageVector = if (newVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = TextSecondaryGray
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SkyBluePrimary,
                        unfocusedBorderColor = OutlineBorder
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("new_master_pw_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm New Password") },
                    singleLine = true,
                    visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { confirmVisible = !confirmVisible }) {
                            Icon(
                                imageVector = if (confirmVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = TextSecondaryGray
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SkyBluePrimary,
                        unfocusedBorderColor = OutlineBorder
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("confirm_new_master_pw_input")
                )

                if (!localError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = localError ?: "",
                        fontSize = 12.sp,
                        color = ErrorRed
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSubmitting = true
                    localError = null
                    viewModel.changeMasterPassword(
                        newPasswordChars = newPassword.toCharArray(),
                        confirmChars = confirmPassword.toCharArray(),
                        onSuccess = {
                            isSubmitting = false
                            onDismiss()
                        },
                        onError = { err ->
                            isSubmitting = false
                            localError = err
                        }
                    )
                },
                enabled = !isSubmitting && newPassword.length >= 8 && newPassword == confirmPassword,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SkyBluePrimary,
                    contentColor = BackgroundWhite
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = BackgroundWhite,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Update Password")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondaryGray)
            }
        },
        containerColor = BackgroundWhite,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SkyBluePrimary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDarkGray
                    )
                    if (badge != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(SkyBlueContainer, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SkyBluePrimary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextSecondaryGray
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = TextMutedGray,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun SecuritySpecRow(
    title: String,
    value: String
) {
    Column {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextDarkGray
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            color = TextSecondaryGray
        )
    }
}
