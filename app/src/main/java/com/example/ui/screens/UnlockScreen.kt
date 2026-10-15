package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.BackgroundWhite
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SkyBlueContainer
import com.example.ui.theme.SkyBluePrimary
import com.example.ui.theme.TextDarkGray
import com.example.ui.theme.TextSecondaryGray
import com.example.ui.viewmodel.MasterKeyViewModel

@Composable
fun UnlockScreen(
    viewModel: MasterKeyViewModel,
    onUseMasterPassword: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    val biometricStatus = remember { viewModel.biometricAuthManager.getStatusDescription() }
    val isBiometricAvailable = remember { viewModel.biometricAuthManager.isAuthenticationAvailable() }
    val userEmail by viewModel.currentUserEmail.collectAsStateWithLifecycle()

    fun triggerUnlock() {
        authErrorMessage = null
        if (activity != null) {
            viewModel.unlockWithBiometrics(
                activity = activity,
                onSuccess = {
                    // Handled inside ViewModel
                },
                onError = { err ->
                    authErrorMessage = err
                }
            )
        } else {
            viewModel.unlockVault()
        }
    }

    // Auto-prompt biometric unlock on open if available
    LaunchedEffect(Unit) {
        if (isBiometricAvailable) {
            triggerUnlock()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("unlock_screen"),
        color = BackgroundWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Lock Icon Emblem
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(SkyBlueContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Vault Locked",
                    tint = SkyBluePrimary,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "MasterKey",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextDarkGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (!userEmail.isNullOrBlank()) "Signed in as $userEmail" else "Hardware-backed AES-256 Vault",
                fontSize = 14.sp,
                color = TextSecondaryGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Unlock with Biometrics Button
            Button(
                onClick = { triggerUnlock() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("unlock_vault_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SkyBluePrimary,
                    contentColor = BackgroundWhite
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = "Unlock with Biometrics / PIN",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Unlock with Master Password Option
            OutlinedButton(
                onClick = onUseMasterPassword,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("unlock_with_master_pw_button"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = SkyBluePrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Enter Master Password",
                    fontSize = 14.sp,
                    color = SkyBluePrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Fallback unlock if device has no biometrics set up (e.g. testing in emulator)
            if (!isBiometricAvailable) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = {
                        val unsealed = viewModel.masterKeyManager.unsealDEKWithKeystore()
                        if (unsealed != null) {
                            viewModel.unlockVault()
                        } else {
                            onUseMasterPassword()
                        }
                    },
                    modifier = Modifier.testTag("unlock_fallback_button")
                ) {
                    Text(
                        text = "Open Vault (No Device Lock Enrolled)",
                        fontSize = 13.sp,
                        color = SkyBluePrimary
                    )
                }
            }

            if (authErrorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = authErrorMessage ?: "",
                    color = ErrorRed,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().testTag("unlock_error_text")
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = { viewModel.logout(clearLocalData = false) },
                modifier = Modifier.testTag("unlock_sign_out_button")
            ) {
                Text(
                    text = "Sign Out / Switch Account",
                    fontSize = 13.sp,
                    color = TextSecondaryGray
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Security badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = TextSecondaryGray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = biometricStatus,
                    fontSize = 12.sp,
                    color = TextSecondaryGray
                )
            }
        }
    }
}
